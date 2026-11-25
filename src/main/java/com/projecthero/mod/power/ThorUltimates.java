package com.projecthero.mod.power;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.power.ThorKit.Kind;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Cosmic revamp: the four ultimates (Shift+Z), one per state, on one shared cooldown ({@link ThorKit#ULT_CD}). Each is
 * a fixed timeline the client draws from the same constants ({@code ThorFxRenderer}, {@code ThorKitPose}):
 * <ul>
 *   <li>Unarmed -- Lightning Incarnate ({@link ThorUnarmedKit}): 10 s as living lightning, then a 60 explosion within 10.</li>
 *   <li>Mjolnir -- Wrath of the Storm: he rises, hammer raised; bolts rain on every enemy near him (45 each), then one giant
 *   bolt (60) on the aimed spot.</li>
 *   <li>Stormbreaker -- Stormbreaker's Judgement (id bifrost_judgement): Stormbreaker hurled and impaling the target (60) in a
 *   lightning strike, pinned while the storm gathers on the axe, then a storm column crashes down on it (45 within 7).</li>
 *   <li>Both -- Ragnarok: he rises between the two weapons, bolts rain (45 each), then Stormbreaker is hurled down onto the
 *   centre (60) in a burst of lightning.</li>
 * </ul>
 */
public final class ThorUltimates {
	// Wrath of the Storm
	public static final int WRATH_RAIN_START = 12, WRATH_RAIN_END = 62, WRATH_FINAL = 72, WRATH_END = 90;
	public static final double WRATH_RAIN_RADIUS = 18.0, WRATH_FINAL_RADIUS = 7.0;
	// Stormbreaker's Judgement
	public static final int JUDGE_THROW = 8, JUDGE_IMPALE = 16, JUDGE_BURST = 38, JUDGE_END = 54;
	public static final double JUDGE_BURST_RADIUS = 7.0;
	// Ragnarok
	public static final int RAG_RISE_END = 18, RAG_RAIN_START = 20, RAG_RAIN_END = 60, RAG_HURL = 62, RAG_IMPACT = 70, RAG_END = 96;
	public static final double RAG_RAIN_RADIUS = 14.0, RAG_FINAL_RADIUS = 8.0;
	/** A target is struck by the rain at most once per this many ticks. */
	private static final int RAIN_GAP = 10;

	private ThorUltimates() {
	}

	public static void cast(ServerPlayer p, ThorState st) {
		if (ThorSequences.running(p, "ultimate") || ThorSequences.running(p, "incarnate")) {
			return;
		}
		if (!ThorKit.begin(p, ThorAbility.ULTIMATE, ThorKit.ULT_CD, ThorKit.ULT_COST)) {
			return;
		}
		switch (st) {
			case UNARMED -> ThorUnarmedKit.lightningIncarnate(p);
			case MJOLNIR -> wrathOfTheStorm(p);
			case STORMBREAKER -> bifrostJudgement(p);
			case TWIN -> ragnarok(p);
		}
	}

	private abstract static class Ult implements ThorSequences.Seq {
		@Override
		public String tag() {
			return "ultimate";
		}
	}

	/** Holds {@code p} up near {@code y} (a hover drawn from velocity, so it is smooth on his own client). */
	private static void hover(ServerPlayer p, double y) {
		double dy = (y - p.getY()) * 0.25;
		p.setDeltaMovement(p.getDeltaMovement().x * 0.6, Math.max(-0.3, Math.min(0.6, dy)), p.getDeltaMovement().z * 0.6);
		p.hurtMarked = true;
		p.fallDistance = 0;
	}

	/** One bolt of the rain: on a random enemy near {@code c} not struck in the last {@link #RAIN_GAP} ticks, else on the ground. */
	private static void rainBolt(ServerPlayer p, Vec3 c, double radius, float dmg, Map<Integer, Integer> last, int t) {
		ServerLevel level = p.serverLevel();
		List<LivingEntity> foes = ThorTargets.hostilesInRadius(level, p, c, radius);
		foes.removeIf(e -> WeaponCombo.friendly(p, e) || t - last.getOrDefault(e.getId(), -100) < RAIN_GAP);
		Vec3 at;
		if (!foes.isEmpty()) {
			LivingEntity e = foes.get(level.random.nextInt(foes.size()));
			at = e.position();
			last.put(e.getId(), t);
			ThorKit.hit(p, e, dmg, Kind.LIGHTNING);
		} else {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(level.random.nextDouble()) * radius;
			at = ThorTwinThunder.groundAt(level, c.add(Math.cos(a) * r, 0, Math.sin(a) * r));
		}
		ThorTwinThunder.visualBolt(level, p, at);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 20, 0.5, 0.2, 0.5, 0.3);
	}

	// ---------------------------------------------------------------- Mjolnir: Wrath of the Storm

	private static void wrathOfTheStorm(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		LivingEntity aimed = ThorKit.aimTarget(p, 60.0, 8.0);
		Vec3 target = aimed != null ? aimed.position() : ThorTwinThunder.groundAt(level, ThorKit.aimPoint(p, 60.0));
		ThorVisuals.anim(p, ThorFx.ANIM_WRATH_STORM, target);
		if (ThorPowers.isFlying(p)) {
			ThorPowers.landFlight(p);
		}
		double hoverY = p.getY() + 3.5;
		ThorKit.sound(level, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
		ThorKit.sound(level, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);
		int aimedId = aimed == null ? -1 : aimed.getId();
		Map<Integer, Integer> last = new HashMap<>();
		ThorSequences.start(p, new Ult() {
			@Override
			public boolean tick(ServerPlayer pl, int t) {
				ServerLevel lv = pl.serverLevel();
				if (t < WRATH_FINAL + 6) {
					hover(pl, hoverY);
				}
				Vec3 c = pl.position();
				if (t < WRATH_RAIN_START) {
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 2.6, c.z, 6, 0.3, 0.3, 0.3, 0.2);
					if (t == 6) {
						ThorTwinThunder.visualBolt(lv, pl, c.add(0, 2.4, 0)); // the sky answers the raised hammer
					}
				} else if (t <= WRATH_RAIN_END) {
					if (t % 3 == 0) {
						rainBolt(pl, new Vec3(c.x, c.y - 3.0, c.z), WRATH_RAIN_RADIUS, ThorKit.WRATH_RAIN, last, t);
					}
					if (t % 10 == 0) {
						ThorKit.sound(lv, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.7f + lv.random.nextFloat() * 0.4f);
					}
				}
				if (t == WRATH_FINAL - 8) {
					ThorKit.sound(lv, c, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.5f);
				}
				if (t == WRATH_FINAL) {
					Entity e = aimedId < 0 ? null : lv.getEntity(aimedId);
					Vec3 at = e != null && e.isAlive() ? e.position() : target;
					for (LivingEntity v : ThorPowers.wrathTargets(lv, pl, at)) {
						if (ThorKit.hit(pl, v, ThorKit.WRATH_FINAL, Kind.LIGHTNING)) {
							ThorKit.knock(v, at, 1.6, 0.8);
						}
					}
					for (int i = 0; i < 6; i++) {
						double a = Math.PI * 2 * i / 6;
						ThorTwinThunder.visualBolt(lv, pl, ThorTwinThunder.groundAt(lv, at.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5)));
					}
					ThorTwinThunder.visualBolt(lv, pl, at);
					lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, 220, WRATH_FINAL_RADIUS * 0.6, 2, WRATH_FINAL_RADIUS * 0.6, 0.6);
					lv.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 4, 0.2, 0.2, 0.2, 0);
					ThorUnarmedKit.debris(lv, at, WRATH_FINAL_RADIUS);
					ThorKit.sound(lv, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 4.0f, 0.5f);
					ThorKit.sound(lv, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0f, 0.5f);
					ThorKit.sound(lv, at, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.6f);
					ThorTwinThunder.shake(lv, at, 2.4f, 26, 48.0);
				}
				return t < WRATH_END;
			}
		});
	}

	// ---------------------------------------------------------------- Stormbreaker: Stormbreaker's Judgement

	private static void bifrostJudgement(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		LivingEntity aimed = ThorKit.aimTarget(p, 40.0, 10.0);
		Vec3 point = aimed != null ? aimed.position() : ThorTwinThunder.groundAt(level, ThorKit.aimPoint(p, 40.0));
		ThorVisuals.anim(p, ThorFx.ANIM_JUDGEMENT, point);
		int aimedId = aimed == null ? -1 : aimed.getId();
		boolean boss = aimed != null && TitanCombat.isBoss(aimed);
		ThorKit.sound(level, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.4f);
		ThorKit.sound(level, p.position(), SoundEvents.TRIDENT_THUNDER, 1.0f, 0.6f);
		Vec3[] pin = { point };
		ThorSequences.start(p, new Ult() {
			@Override
			public boolean tick(ServerPlayer pl, int t) {
				ServerLevel lv = pl.serverLevel();
				Entity e = aimedId < 0 ? null : lv.getEntity(aimedId);
				if (t < JUDGE_THROW) {
					ThorKit.storm(lv, pl.position().add(0, 2.4, 0), 4, 0.4, 0.4);
				}
				if (t == JUDGE_THROW) {
					ThorKit.sound(lv, pl.position(), SoundEvents.TRIDENT_THROW, 1.2f, 0.6f);
					ThorKit.sound(lv, pl.position(), SoundEvents.WIND_CHARGE_BURST, 1.0f, 0.5f);
				}
				if (t == JUDGE_IMPALE) {
					if (e instanceof LivingEntity v && v.isAlive()) {
						pin[0] = v.position();
						ThorKit.hit(pl, v, ThorKit.JUDGEMENT_IMPALE, Kind.PHYSICAL);
					}
					Vec3 c = pin[0];
					ThorTwinThunder.visualBolt(lv, pl, c);
					ThorKit.storm(lv, c.add(0, 1, 0), 60, 0.8, 1.0);
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 50, 0.5, 0.8, 0.5, 0.4);
					ThorKit.sound(lv, c, SoundEvents.TRIDENT_HIT, 1.5f, 0.5f);
					ThorKit.sound(lv, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2f, 0.6f);
					ThorTwinThunder.shake(lv, c, 1.2f, 12, 30.0);
				}
				if (t > JUDGE_IMPALE && t < JUDGE_BURST) {
					// pinned in place while the storm gathers on the axe
					if (e instanceof LivingEntity v && v.isAlive() && !boss) {
						v.teleportTo(pin[0].x, pin[0].y, pin[0].z);
						v.setDeltaMovement(Vec3.ZERO);
						v.hurtMarked = true;
						v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 9, false, false, false));
					}
					ThorKit.storm(lv, pin[0].add(0, 1 + (t - JUDGE_IMPALE) * 0.3, 0), 6, 0.6 + (t - JUDGE_IMPALE) * 0.08, 0.4);
					if (t == JUDGE_BURST - 6) {
						ThorStormbreakerKit.stormColumn(lv, pl, pin[0]);
						ThorKit.sound(lv, pin[0], SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);
					}
				}
				if (t == JUDGE_BURST) {
					Vec3 c = pin[0];
					for (LivingEntity v : ThorTargets.inRadius(lv, pl, c.add(0, 1, 0), JUDGE_BURST_RADIUS)) {
						if (ThorKit.hit(pl, v, ThorKit.JUDGEMENT_BURST, Kind.PHYSICAL)) {
							ThorKit.knock(v, c, 1.5, 0.9);
						}
					}
					ThorKit.storm(lv, c.add(0, 1, 0), 240, JUDGE_BURST_RADIUS * 0.6, 2.0);
					lv.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 3, 0.2, 0.2, 0.2, 0);
					lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
					ThorUnarmedKit.debris(lv, c, JUDGE_BURST_RADIUS);
					ThorKit.sound(lv, c, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.7f);
					ThorKit.sound(lv, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0f, 0.5f);
					ThorKit.sound(lv, c, SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 1.2f);
					ThorTwinThunder.shake(lv, c, 2.2f, 24, 44.0);
				}
				return t < JUDGE_END;
			}
		});
	}

	// ---------------------------------------------------------------- Both: Ragnarok

	private static void ragnarok(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 centre = ThorTwinThunder.groundAt(level, ThorKit.aimPoint(p, 30.0));
		if (centre.distanceTo(p.position()) > 30.0) {
			centre = p.position();
		}
		Vec3 c0 = centre;
		ThorVisuals.anim(p, ThorFx.ANIM_RAGNAROK, centre);
		if (ThorPowers.isFlying(p)) {
			ThorPowers.landFlight(p);
		}
		double hoverY = p.getY() + 6.0;
		ThorKit.sound(level, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0f, 0.5f);
		ThorKit.sound(level, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.6f);
		Map<Integer, Integer> last = new HashMap<>();
		ThorSequences.start(p, new Ult() {
			@Override
			public boolean tick(ServerPlayer pl, int t) {
				ServerLevel lv = pl.serverLevel();
				if (t < RAG_IMPACT + 4) {
					hover(pl, hoverY);
				}
				Vec3 c = pl.position();
				if (t < RAG_RISE_END) {
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 8, 0.5, 0.8, 0.5, 0.2);
					ThorKit.storm(lv, c.add(0, 1, 0), 4, 0.6, 0.8);
					if (t == 10) {
						ThorTwinThunder.visualBolt(lv, pl, c.add(0, 2.0, 0));
					}
				} else if (t >= RAG_RAIN_START && t <= RAG_RAIN_END) {
					if (t % 3 == 0) {
						rainBolt(pl, c0, RAG_RAIN_RADIUS, ThorKit.RAGNAROK_RAIN, last, t);
					}
					if (t % 6 == 0) {
						ThorKit.stormLine(lv, c, c0.add((lv.random.nextDouble() - 0.5) * 6, 0.5, (lv.random.nextDouble() - 0.5) * 6), 0.8);
					}
					if (t % 10 == 0) {
						ThorKit.sound(lv, c0, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4f, 0.6f + lv.random.nextFloat() * 0.3f);
					}
				}
				if (t == RAG_HURL) {
					ThorKit.sound(lv, c, SoundEvents.TRIDENT_THROW, 1.4f, 0.5f);
					ThorKit.sound(lv, c, SoundEvents.WIND_CHARGE_BURST, 1.2f, 0.5f);
				}
				if (t == RAG_IMPACT) {
					for (LivingEntity v : ThorTargets.inRadius(lv, pl, c0.add(0, 1, 0), RAG_FINAL_RADIUS)) {
						if (ThorKit.hit(pl, v, ThorKit.RAGNAROK_FINAL, Kind.LIGHTNING)) {
							ThorKit.knock(v, c0, 2.0, 1.0);
						}
					}
					ThorTwinThunder.visualBolt(lv, pl, c0);
					for (int i = 0; i < 10; i++) {
						double a = Math.PI * 2 * i / 10;
						ThorTwinThunder.visualBolt(lv, pl, ThorTwinThunder.groundAt(lv, c0.add(Math.cos(a) * 5, 0, Math.sin(a) * 5)));
					}
					ThorStormbreakerKit.stormColumn(lv, pl, c0);
					ThorKit.storm(lv, c0.add(0, 1, 0), 300, RAG_FINAL_RADIUS * 0.6, 2.0);
					lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c0.x, c0.y + 0.5, c0.z, 2, 1, 0.2, 1, 0);
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c0.x, c0.y + 1, c0.z, 260, 5, 2, 5, 0.7);
					lv.sendParticles(ParticleTypes.FLASH, c0.x, c0.y + 1, c0.z, 4, 0.3, 0.3, 0.3, 0);
					ThorUnarmedKit.debris(lv, c0, RAG_FINAL_RADIUS);
					ThorKit.sound(lv, c0, SoundEvents.LIGHTNING_BOLT_IMPACT, 4.0f, 0.5f);
					ThorKit.sound(lv, c0, SoundEvents.GENERIC_EXPLODE, 2.0f, 0.55f);
					ThorKit.sound(lv, c0, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.9f);
					ThorKit.sound(lv, c0, SoundEvents.TRIDENT_THUNDER, 2.0f, 0.5f);
					ThorTwinThunder.shake(lv, c0, 2.8f, 30, 56.0);
				}
				return t < RAG_END;
			}
		});
	}
}
