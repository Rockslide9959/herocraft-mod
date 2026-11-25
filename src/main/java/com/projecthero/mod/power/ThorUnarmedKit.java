package com.projecthero.mod.power;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.power.ThorKit.Kind;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Cosmic revamp, <b>Unarmed: Warrior of Asgard</b> -- no weapon in either hand (bound to Mjolnir or a Stormbreaker).
 * Blue-white arcs crackle over his fists and body; every cooldown is 25% shorter ({@link ThorState#UNARMED}); no flight, so
 * X is an electric sprint and Shift+X a huge leap. Numbers in {@link ThorKit}.
 */
public final class ThorUnarmedKit {
	private ThorUnarmedKit() {
	}

	// ---------------------------------------------------------------- Shift+R Thunder Fist (hold)

	public static void startFist(ServerPlayer p) {
		if (!ThorState.hasKit(p) || ThorVisuals.fx(p).has(ThorFx.CH_FIST) || !ThorKit.ready(p, ThorAbility.U_THUNDER_FIST)) {
			return;
		}
		if (!StormEnergy.has(p, ThorState.UNARMED.cost(ThorKit.FIST_COST))) {
			p.displayClientMessage(Component.translatable("message.projecthero.no_storm_energy"), true);
			return;
		}
		ThorVisuals.channel(p, ThorFx.CH_FIST, true);
		ThorKit.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_ACTIVATE, 0.5f, 1.8f);
		ThorSequences.start(p, new ThorSequences.Seq() {
			@Override
			public boolean tick(ServerPlayer pl, int t) {
				if (!ThorVisuals.fx(pl).has(ThorFx.CH_FIST)) {
					return false;
				}
				if (ThorState.of(pl) != ThorState.UNARMED || !ThorState.hasKit(pl)) {
					ThorVisuals.channel(pl, ThorFx.CH_FIST, false);
					return false;
				}
				ServerLevel level = pl.serverLevel();
				Vec3 fist = fistPos(pl);
				float c = Math.min(1f, t / (float) ThorKit.FIST_CHARGE_TICKS);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, fist.x, fist.y, fist.z, 2 + (int) (c * 5), 0.12, 0.12, 0.12,
						0.04 + c * 0.08);
				if (t % 6 == 0) {
					ThorKit.sound(level, pl.position(), SoundEvents.BEACON_AMBIENT, 0.4f, 0.8f + c);
				}
				if (t == ThorKit.FIST_CHARGE_TICKS) {
					ThorKit.sound(level, pl.position(), SoundEvents.TRIDENT_THUNDER, 0.6f, 1.9f);
					level.sendParticles(ParticleTypes.FLASH, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
				}
				if (t >= 100) { // held too long: it goes off anyway
					releaseFist(pl);
					return false;
				}
				return true;
			}
		});
	}

	/** R released: a charging fist goes off, 24 at a tap to 30 fully charged (1.5 s). */
	public static void releaseFist(ServerPlayer p) {
		ThorFx fx = ThorVisuals.fx(p);
		if (!fx.has(ThorFx.CH_FIST)) {
			return;
		}
		ThorVisuals.channel(p, ThorFx.CH_FIST, false);
		float charge = Math.min(1f, (p.level().getGameTime() - fx.chargeStart()) / (float) ThorKit.FIST_CHARGE_TICKS);
		if (!ThorKit.begin(p, ThorAbility.U_THUNDER_FIST, ThorKit.FIST_CD, ThorKit.FIST_COST)) {
			return;
		}
		thunderFist(p, charge);
	}

	/** The punch itself: a lunge onto the aimed foe within reach, damage by charge. Public for tests. */
	public static void thunderFist(ServerPlayer p, float charge) {
		ServerLevel level = p.serverLevel();
		LivingEntity target = ThorKit.aimTarget(p, 6.0, 25.0);
		Vec3 look = ThorKit.flatLook(p);
		if (target != null) {
			Vec3 to = target.position().subtract(p.position()).multiply(1, 0, 1);
			double d = Math.max(0.0, to.length() - 1.2);
			Vec3 lunge = to.lengthSqr() < 1e-6 ? Vec3.ZERO : to.normalize().scale(Math.min(1.3, d * 0.45));
			p.setDeltaMovement(lunge.x, p.getDeltaMovement().y, lunge.z);
		} else {
			p.setDeltaMovement(look.x * 0.9, p.getDeltaMovement().y, look.z * 0.9);
		}
		p.hurtMarked = true;
		float dmg = ThorKit.FIST_MIN + (ThorKit.FIST_MAX - ThorKit.FIST_MIN) * charge;
		Vec3 at = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : fistPos(p).add(look.scale(2.0));
		ThorVisuals.anim(p, ThorFx.ANIM_THUNDER_FIST, at);
		if (target != null && ThorKit.hit(p, target, dmg, Kind.PHYSICAL)) {
			ThorKit.knock(target, p.position(), 1.0 + charge * 1.4, 0.25 + charge * 0.25);
			ThorTwinThunder.shake(level, at, 0.4f + charge * 0.6f, 8, 12.0);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30 + (int) (charge * 30), 0.3, 0.3, 0.3, 0.35);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		if (charge >= 0.99f) {
			level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		ThorKit.sound(level, at, SoundEvents.WIND_CHARGE_BURST, 0.9f, 0.7f);
		ThorKit.sound(level, at, SoundEvents.TRIDENT_THUNDER, 0.5f + charge * 0.5f, 1.3f);
		ThorKit.sound(level, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
	}

	/** Roughly where his main fist is (for sparks). */
	static Vec3 fistPos(ServerPlayer p) {
		float yaw = p.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-net.minecraft.util.Mth.cos(yaw), 0.0, -net.minecraft.util.Mth.sin(yaw));
		double side = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 0.38 : -0.38;
		return p.position().add(0, 1.05, 0).add(right.scale(side)).add(ThorKit.flatLook(p).scale(0.35));
	}

	// ---------------------------------------------------------------- G Lightning Palm

	public static void lightningPalm(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.U_LIGHTNING_PALM, ThorKit.PALM_CD, ThorKit.PALM_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		LivingEntity target = ThorKit.aimTarget(p, ThorKit.PALM_RANGE, 12.0);
		Vec3 end = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : ThorKit.aimPoint(p, ThorKit.PALM_RANGE);
		ThorVisuals.anim(p, ThorFx.ANIM_LIGHTNING_PALM, end);
		ThorPowers.sendLightningArc(p, 70, null, target, end, 4, 9);
		if (target != null && ThorKit.hit(p, target, ThorKit.PALM_DAMAGE, Kind.LIGHTNING)) {
			ThorKit.knock(target, p.position(), 0.9, 0.15);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 24, 0.25, 0.25, 0.25, 0.3);
		ThorKit.sound(level, p.position(), SoundEvents.TRIDENT_THUNDER, 0.7f, 1.7f);
		ThorKit.sound(level, end, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
	}

	// ---------------------------------------------------------------- Shift+G Asgardian Grapple

	public static void grapple(ServerPlayer p) {
		if (!ThorState.hasKit(p) || !ThorKit.ready(p, ThorAbility.U_GRAPPLE)) {
			return;
		}
		LivingEntity target = ThorKit.aimTarget(p, 5.5, 25.0);
		if (target == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.thor.nothing_to_grab"), true);
			return;
		}
		if (!ThorKit.begin(p, ThorAbility.U_GRAPPLE, ThorKit.GRAPPLE_CD, ThorKit.GRAPPLE_COST)) {
			return;
		}
		ThorVisuals.anim(p, ThorFx.ANIM_GRAPPLE, target.position());
		int id = target.getId();
		boolean boss = TitanCombat.isBoss(target);
		ThorKit.sound(p.serverLevel(), target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
		ThorSequences.start(p, (pl, t) -> {
			ServerLevel level = pl.serverLevel();
			Entity e = level.getEntity(id);
			if (!(e instanceof LivingEntity victim) || !victim.isAlive()) {
				return false;
			}
			Vec3 look = ThorKit.flatLook(pl);
			if (t <= 9 && !boss) {
				// lifted overhead in one hand, crackling
				double lift = Math.min(1.0, t / 5.0);
				Vec3 hold = pl.position().add(look.scale(1.1 - 0.6 * lift)).add(0, 1.0 + lift * 1.3, 0);
				victim.teleportTo(hold.x, hold.y, hold.z);
				victim.setDeltaMovement(Vec3.ZERO);
				victim.hurtMarked = true;
				victim.fallDistance = 0;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hold.x, hold.y + victim.getBbHeight() * 0.5, hold.z, 4, 0.3, 0.3,
						0.3, 0.08);
				if (t == 4) {
					ThorKit.sound(level, hold, SoundEvents.BEACON_POWER_SELECT, 0.4f, 1.8f);
				}
			}
			if (t == 11) {
				Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
				ThorKit.hit(pl, victim, ThorKit.GRAPPLE_DAMAGE, Kind.PHYSICAL);
				if (!boss) {
					Vec3 fling = pl.getLookAngle().scale(2.3).add(0, 0.45, 0);
					victim.setDeltaMovement(fling);
					victim.hurtMarked = true;
				}
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 0.4, 0.4, 0.4, 0.35);
				level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				ThorKit.sound(level, at, SoundEvents.WIND_CHARGE_BURST, 1.0f, 0.6f);
				ThorKit.sound(level, at, SoundEvents.TRIDENT_THUNDER, 0.6f, 1.4f);
				return false;
			}
			return true;
		});
	}

	// ---------------------------------------------------------------- C Storm Rush

	public static void stormRush(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.U_STORM_RUSH, ThorKit.RUSH_CD, ThorKit.RUSH_COST)) {
			return;
		}
		ThorVisuals.anim(p, ThorFx.ANIM_STORM_RUSH);
		ThorKit.sound(p.serverLevel(), p.position(), SoundEvents.BREEZE_JUMP, 0.6f, 1.4f);
		ThorSequences.start(p, (pl, t) -> {
			int blow = t == 2 ? 1 : t == 5 ? 2 : t == 8 ? 3 : t == 12 ? 4 : 0;
			if (blow > 0) {
				rushBlow(pl, blow);
			}
			return t < 12;
		});
	}

	private static void rushBlow(ServerPlayer p, int blow) {
		ServerLevel level = p.serverLevel();
		Vec3 look = ThorKit.flatLook(p);
		p.setDeltaMovement(look.x * 0.5, p.getDeltaMovement().y, look.z * 0.5);
		p.hurtMarked = true;
		Vec3 origin = p.position();
		Vec3 at = origin.add(look.scale(1.6)).add(0, 1.1, 0);
		for (LivingEntity e : ThorTargets.inRadius(level, p, origin.add(look.scale(1.4)).add(0, 1, 0), 2.6)) {
			Vec3 to = e.position().subtract(origin).multiply(1, 0, 1);
			if (to.length() > 0.5 && to.normalize().dot(look) < 0.2) {
				continue;
			}
			if (ThorKit.hit(p, e, ThorKit.RUSH_DAMAGE, Kind.PHYSICAL)) {
				ThorKit.knock(e, origin, blow == 4 ? 1.4 : 0.3, blow == 4 ? 0.35 : 0.05);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14 + blow * 4, 0.3, 0.3, 0.3, 0.25);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.2);
		ThorKit.sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 0.8f + blow * 0.1f);
		ThorKit.sound(level, at, SoundEvents.TRIDENT_THUNDER, 0.25f, 1.9f);
		if (blow == 4) {
			level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			ThorKit.sound(level, at, SoundEvents.WIND_CHARGE_BURST, 0.8f, 0.8f);
			ThorTwinThunder.shake(level, at, 0.5f, 6, 10.0);
		}
	}

	// ---------------------------------------------------------------- Shift+C Static Burst

	public static void staticBurst(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.U_STATIC_BURST, ThorKit.BURST_CD, ThorKit.BURST_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		ThorVisuals.anim(p, ThorFx.ANIM_STATIC_BURST, c);
		int slot = 0;
		for (LivingEntity e : ThorTargets.inRadius(level, p, c.add(0, 1, 0), ThorKit.BURST_RADIUS)) {
			if (ThorKit.hit(p, e, ThorKit.BURST_DAMAGE, Kind.LIGHTNING)) {
				ThorKit.knock(e, c, 1.0, 0.3);
				ThorPowers.sendLightningArc(p, 72 + (slot++ % 8), null, e, e.position(), 3, 8);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1.0, c.z, 90, 1.2, 1.0, 1.2, 0.45);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
		ThorKit.sparkRing(level, c, ThorKit.BURST_RADIUS * 0.8, 36);
		ThorKit.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 1.5f);
		ThorKit.sound(level, c, SoundEvents.WIND_CHARGE_BURST, 0.7f, 1.2f);
		ThorKit.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 0.5f, 2.0f);
	}

	// ---------------------------------------------------------------- Z Ground Pound

	public static void groundPound(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.U_GROUND_POUND, ThorKit.POUND_CD, ThorKit.POUND_COST)) {
			return;
		}
		ThorVisuals.anim(p, ThorFx.ANIM_GROUND_POUND, p.position());
		ThorKit.sound(p.serverLevel(), p.position(), SoundEvents.BREEZE_CHARGE, 0.7f, 0.8f);
		ThorSequences.start(p, (pl, t) -> {
			if (t == 7) {
				slam(pl, pl.position(), ThorKit.POUND_DAMAGE, ThorKit.POUND_RADIUS, 0.9f);
				return false;
			}
			return true;
		});
	}

	/** A fist / landing slam into the ground: damage within {@code r}, a knock-up, debris rings, a crack and a shake. */
	static void slam(ServerPlayer p, Vec3 c, float dmg, double r, float shake) {
		ServerLevel level = p.serverLevel();
		for (LivingEntity e : ThorTargets.inRadius(level, p, c.add(0, 0.5, 0), r)) {
			if (ThorKit.hit(p, e, dmg, Kind.PHYSICAL)) {
				ThorKit.knock(e, c, 1.0, 0.65);
			}
		}
		debris(level, c, r);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 2, 0.5, 0.1, 0.5, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.3, c.z, 70, r * 0.35, 0.2, r * 0.35, 0.35);
		ThorKit.sparkRing(level, c, r * 0.9, 40);
		ThorKit.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2f, 0.75f);
		ThorKit.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);
		ThorKit.sound(level, c, SoundEvents.GENERIC_EXPLODE, 0.45f, 1.3f);
		ThorTwinThunder.shake(level, c, shake, 12, 20.0);
	}

	/** Rings of block debris thrown up from the ground at {@code c}. */
	static void debris(ServerLevel level, Vec3 c, double r) {
		BlockPos below = BlockPos.containing(c.x, c.y - 0.2, c.z);
		BlockState ground = level.getBlockState(below);
		if (ground.isAir()) {
			ground = Blocks.STONE.defaultBlockState();
		}
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int ring = 1; ring <= (int) r; ring++) {
			int points = 6 + ring * 4;
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points;
				level.sendParticles(dust, c.x + Math.cos(a) * ring, c.y + 0.1, c.z + Math.sin(a) * ring, 2, 0.15, 0.05, 0.15, 0.15);
			}
		}
	}

	// ---------------------------------------------------------------- X Storm Sprint

	public static void stormSprint(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.U_STORM_SPRINT, ThorKit.SPRINT_CD, ThorKit.SPRINT_COST)) {
			return;
		}
		ThorKit.startTimer(p, ThorAbility.SPRINT_ACTIVE, ThorKit.SPRINT_TICKS);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
		ThorKit.sound(level, p.position(), SoundEvents.BREEZE_JUMP, 0.8f, 1.3f);
		ThorKit.sound(level, p.position(), SoundEvents.TRIDENT_THUNDER, 0.4f, 1.9f);
	}

	// ---------------------------------------------------------------- Shift+X Thunder Leap

	public static void thunderLeap(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.U_THUNDER_LEAP, ThorKit.LEAP_CD, ThorKit.LEAP_COST)) {
			return;
		}
		leapThen(p, ThorFx.ANIM_LEAP, 1.7, 1.25, "leap", (pl, at) -> {
			ThorVisuals.anim(pl, ThorFx.ANIM_LEAP_SLAM, at);
			ThorTwinThunder.visualBolt(pl.serverLevel(), pl, at);
			slam(pl, at, ThorKit.LEAP_DAMAGE, ThorKit.LEAP_RADIUS, 1.2f);
		});
	}

	/** What a leap does when it comes down. */
	interface Landing {
		void land(ServerPlayer p, Vec3 at);
	}

	/**
	 * Launches {@code p} forward ({@code fwd}) and up ({@code up}) with the {@code anim} take-off, then calls {@code landing}
	 * when he is back on the ground (or after 3 s). One leap at a time per {@code tag}.
	 */
	static void leapThen(ServerPlayer p, int anim, double fwd, double up, String tag, Landing landing) {
		ThorSequences.cancel(p, tag);
		Vec3 look = ThorKit.flatLook(p);
		ThorVisuals.anim(p, anim, p.position());
		if (ThorPowers.isFlying(p)) {
			ThorPowers.landFlight(p);
		}
		p.setDeltaMovement(look.x * fwd, up, look.z * fwd);
		p.hurtMarked = true;
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 30, 0.6, 0.1, 0.6, 0.3);
		ThorKit.sound(level, p.position(), SoundEvents.WIND_CHARGE_BURST, 0.9f, 0.7f);
		ThorKit.sound(level, p.position(), SoundEvents.BREEZE_JUMP, 1.0f, 0.8f);
		ThorSequences.start(p, new ThorSequences.Seq() {
			@Override
			public boolean tick(ServerPlayer pl, int t) {
				if (t % 2 == 0) {
					pl.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, pl.getX(), pl.getY() + 1, pl.getZ(), 3, 0.3, 0.5, 0.3,
							0.05);
				}
				pl.fallDistance = 0;
				if ((t > 4 && pl.onGround()) || t >= 60) {
					landing.land(pl, pl.position());
					return false;
				}
				return true;
			}

			@Override
			public String tag() {
				return tag;
			}
		});
	}

	// ---------------------------------------------------------------- Shift+Z Lightning Incarnate

	static void lightningIncarnate(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		ThorKit.startTimer(p, ThorAbility.INCARNATE_ACTIVE, ThorKit.INCARNATE_TICKS + 2);
		ThorVisuals.channel(p, ThorFx.CH_INCARNATE, true);
		ThorTwinThunder.visualBolt(level, p, p.position());
		level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 2, 0, 0, 0, 0);
		ThorKit.sound(level, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4f, 0.7f);
		ThorKit.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
		ThorTwinThunder.shake(level, p.position(), 0.8f, 12, 20.0);
		ThorSequences.start(p, new ThorSequences.Seq() {
			@Override
			public boolean tick(ServerPlayer pl, int t) {
				ServerLevel lv = pl.serverLevel();
				Vec3 c = pl.position();
				lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1.0, c.z, 6, 0.45, 0.9, 0.45, 0.15);
				if (t % 3 == 0) {
					lv.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 1, 0.3, 0.6, 0.3, 0.02);
				}
				if (t > 0 && t % ThorKit.INCARNATE_PULSE == 0 && t < ThorKit.INCARNATE_TICKS) {
					int slot = 0;
					for (LivingEntity e : ThorTargets.hostilesInRadius(lv, pl, c.add(0, 1, 0), ThorKit.INCARNATE_AURA_RADIUS)) {
						if (ThorKit.hit(pl, e, ThorKit.INCARNATE_AURA, Kind.LIGHTNING)) {
							ThorPowers.sendLightningArc(pl, 80 + (slot++ % 8), null, e, e.position(), 3, 6);
						}
					}
					ThorKit.sound(lv, c, SoundEvents.TRIDENT_THUNDER, 0.5f, 1.5f + lv.random.nextFloat() * 0.3f);
				}
				if (t == ThorKit.INCARNATE_TICKS - 30) {
					ThorKit.sound(lv, c, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.5f); // it builds
				}
				if (t >= ThorKit.INCARNATE_TICKS) {
					incarnateBurst(pl);
					return false;
				}
				return true;
			}

			@Override
			public String tag() {
				return "incarnate";
			}
		});
	}

	private static void incarnateBurst(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		ThorKit.endTimer(p, ThorAbility.INCARNATE_ACTIVE);
		ThorVisuals.channel(p, ThorFx.CH_INCARNATE, false);
		ThorVisuals.anim(p, ThorFx.ANIM_INCARNATE_BURST, c);
		Set<LivingEntity> hit = new HashSet<>(ThorTargets.inRadius(level, p, c.add(0, 1, 0), ThorKit.INCARNATE_BURST_RADIUS));
		for (LivingEntity e : hit) {
			if (ThorKit.hit(p, e, ThorKit.INCARNATE_BURST, Kind.LIGHTNING)) {
				ThorKit.knock(e, c, 1.8, 0.7);
			}
		}
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			ThorTwinThunder.visualBolt(level, p, ThorTwinThunder.groundAt(level, c.add(Math.cos(a) * 6, 0, Math.sin(a) * 6)));
		}
		ThorTwinThunder.visualBolt(level, p, c);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 200, 4, 2, 4, 0.6);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 3, 0.2, 0.2, 0.2, 0);
		debris(level, c, 8);
		ThorKit.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 3.0f, 0.6f);
		ThorKit.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0f, 0.55f);
		ThorKit.sound(level, c, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.7f);
		ThorKit.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.4f);
		ThorTwinThunder.shake(level, c, 2.2f, 24, 40.0);
	}

	static void tickIncarnate(ServerPlayer p) {
		// (the timeline is a ThorSequences entry; the channel is mirrored from the timer in ThorKit.tick)
	}
}
