package com.projecthero.mod.hero.power.p21;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.revamp.BatchCFx;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Power 21 -- Shockwave Manipulation (v0.13.22 revamp).
 *
 * <h2>Signature: kinetic storage</h2>
 * This power stores <b>kinetic</b> energy only -- no fire, no lightning, no magic (that is Energy Absorption) and no
 * raw muscle (that is Super Strength). Melee hits, falls and explosions you take are partly dampened and the blocked
 * force is banked in the <b>Kinetic</b> gauge ({@value #MAX_CHARGE}); holding C braces to bank more (and doubles what
 * incoming hits store). Waves spend it to hit harder, and every wave leaves visible distortion rings.
 */
public final class ShockwaveHandlers {
	public static final String KEY = "power_21_shockwave_manipulation";
	/** +15% over the pre-revamp 100. */
	public static final float MAX_CHARGE = 115.0f;

	public static final float PUNCH_DAMAGE = 12.0f;
	public static final float WAVE_DAMAGE = 16.0f;
	private static final float BURST_DAMAGE = 18.0f;
	public static final float DETONATION_DAMAGE = 72.0f;
	public static final float AFTERSHOCK_DAMAGE = 14.0f;
	private static final int DETONATION_CHARGE = 85;
	private static final int PARRY_TICKS = 14;
	private static final int AFTERSHOCK_DELAY = 16;

	/** Warm amber for stored kinetic energy; pale grey-white for the distortion itself. */
	static final int KINETIC = 0xFFB347;
	static final int DISTORT = 0xE8ECF2;

	/** A travelling ground wave. */
	private static final class Wave {
		final ServerLevel level;
		double x;
		double z;
		double y;
		final double dx;
		final double dz;
		int ticksLeft;
		final float damage;
		final Set<Integer> hit = new HashSet<>();

		Wave(ServerLevel level, double x, double y, double z, double dx, double dz, int ticks, float damage) {
			this.level = level;
			this.x = x;
			this.y = y;
			this.z = z;
			this.dx = dx;
			this.dz = dz;
			this.ticksLeft = ticks;
			this.damage = damage;
		}
	}

	/** Where a player's last wave landed (for Aftershock). */
	public record Impact(ServerLevel level, Vec3 pos, long at) {
	}

	private record Pending(ServerLevel level, Vec3 pos, long fireAt, float damage) {
	}

	private static final Map<UUID, Wave> WAVES = new ConcurrentHashMap<>();
	private static final Map<UUID, Impact> LAST_IMPACT = new ConcurrentHashMap<>();
	private static final Map<UUID, Pending> AFTERSHOCKS = new ConcurrentHashMap<>();

	private ShockwaveHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	private static ParticleOptions distortion(float size) {
		return BatchCFx.dust(DISTORT, size);
	}

	private static ParticleOptions kineticDust(float size) {
		return BatchCFx.dust(KINETIC, size);
	}

	/** The distortion rings every wave leaves: a pale ring plus a POOF ring expanding outward. */
	private static void distortionRing(ServerLevel level, Vec3 c, double r) {
		BatchCFx.flatRing(level, c, r, (int) (12 + r * 5), distortion(1.1f), 0.12);
		BatchCFx.flatRing(level, c.add(0, 0.2, 0), r * 0.7, (int) (8 + r * 3), ParticleTypes.POOF, 0.08);
	}

	// ---- kinetic storage -------------------------------------------------------------------

	/** Banks {@code amount} kinetic energy (capped). Public for HeroDamageRules and tests. */
	public static void storeKinetic(ServerPlayer p, float amount) {
		Power power = power();
		if (power == null || amount <= 0) {
			return;
		}
		ExperimentalPowers.addResource(p, power, "charge", amount, MAX_CHARGE);
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(kineticDust(1.0f), p.getX(), p.getY() + 1.0, p.getZ(), 6 + (int) Math.min(12, amount), 0.35, 0.5, 0.35, 0.02);
		}
	}

	public static boolean bracing(ServerPlayer p) {
		return BatchCFx.resourceQuick(p, KEY, "charging") > 0.5f;
	}

	public static boolean repelling(ServerPlayer p) {
		return BatchCFx.resourceQuick(p, KEY, "repelling") > 0.5f;
	}

	/** True while Kinetic Parry's window is open. */
	public static boolean parryOpen(ServerPlayer p) {
		return BatchCFx.resourceQuick(p, KEY, "parry_until") > p.level().getGameTime();
	}

	private static boolean isMelee(DamageSource source) {
		return source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
				&& !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION);
	}

	/**
	 * The passive rule, from {@code HeroDamageRules}: returns the damage factor that still lands, or a negative value when
	 * the hit is negated outright (a successful Kinetic Parry). Stores the dampened force.
	 */
	public static float onIncoming(ServerPlayer p, DamageSource source, float amount) {
		if (isMelee(source) && parryOpen(p)) {
			parry(p, (LivingEntity) source.getEntity(), amount);
			return -1.0f;
		}
		float mult = bracing(p) ? 2.0f : 1.0f;
		float factor = 1.0f;
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			storeKinetic(p, amount * 1.5f * mult);
			factor = 0.5f;
		} else if (source.is(DamageTypeTags.IS_FALL)) {
			storeKinetic(p, amount * 1.2f * mult);
			factor = 0.6f;
		} else if (isMelee(source)) {
			storeKinetic(p, amount * mult);
			factor = bracing(p) ? 0.6f : 0.85f;
		}
		if (repelling(p)) {
			factor *= 0.7f;
		}
		return factor;
	}

	/** Kinetic Parry landed: the blow is negated, stored twice over, and the attacker is thrown back. */
	public static void parry(ServerPlayer p, LivingEntity attacker, float amount) {
		Power power = power();
		if (power == null) {
			return;
		}
		ExperimentalPowers.setResource(p, power, "parry_until", 0, 1.0e12f);
		storeKinetic(p, amount * 2.0f);
		if (attacker != null && attacker != p) {
			AbilityHelpers.hurt(p, attacker, 5.0f);
			AbilityHelpers.knockbackFrom(attacker, p.position(), 2.5);
			AbilityHelpers.push(attacker, new Vec3(0, 0.3, 0));
			AbilityHelpers.applyControl(attacker, MobEffects.MOVEMENT_SLOWDOWN, 30, 3);
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		BatchCFx.ring(level, c.add(p.getLookAngle().scale(0.7)), p.getLookAngle(), 0.8, 20, kineticDust(1.4f), 0.35);
		level.sendParticles(ParticleTypes.GUST, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, p.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0f, 1.3f);
		MutationVisuals.play(p, "double_punch");
		// a successful parry refunds most of the cooldown
		var parry = power.ability(AbilitySlot.SLOT_8);
		ExperimentalPowers.triggerCooldown(p, power, parry, HeroConfig.get().scaledCooldown(40));
	}

	/** Spends up to {@code maxSpend} kinetic for a damage multiplier of up to 1 + {@code maxBonus}. */
	private static float boost(AbilityContext ctx, float maxSpend, float maxBonus) {
		float have = ctx.resource("charge");
		float spend = Math.min(have, maxSpend);
		if (spend < 1.0f) {
			return 1.0f;
		}
		ctx.addResource("charge", -spend, MAX_CHARGE);
		return 1.0f + maxBonus * (spend / maxSpend);
	}

	private static void recordImpact(ServerPlayer p, Vec3 pos) {
		LAST_IMPACT.put(p.getUUID(), new Impact((ServerLevel) p.level(), pos, p.level().getGameTime()));
	}

	public static Impact lastImpact(ServerPlayer p) {
		return LAST_IMPACT.get(p.getUUID());
	}

	// ---- registration -----------------------------------------------------------------------

	public static void register() {
		// R -- Shockwave Punch: a 7-block pressure cone. Spends up to 10 Kinetic for up to x1.5 damage.
		AbilityHandlers.register(KEY, "shockwave_punch", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			float mult = boost(ctx, 10.0f, 0.5f);
			double range = 7.0;
			Vec3 eye = p.getEyePosition();
			Vec3 look = p.getLookAngle();
			Vec3 lastHit = null;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(range * 0.5)), range * 0.5 + 1.0)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
				if (to.normalize().dot(look) < 0.55) {
					continue;
				}
				AbilityHelpers.hurt(p, e, PUNCH_DAMAGE * mult);
				AbilityHelpers.knockbackFrom(e, eye, 1.3 * mult);
				lastHit = e.position();
			}
			BatchCFx.ringTrail(level, AbilityHelpers.handPosition(p), look, range, 1.4, 0.25, 0.22, distortion(1.0f));
			level.sendParticles(ParticleTypes.GUST, eye.x + look.x * 2, eye.y + look.y * 2 - 0.3, eye.z + look.z * 2, 1, 0, 0, 0, 0);
			recordImpact(p, lastHit != null ? lastHit : eye.add(look.scale(range)).add(0, -1.2, 0));
			MutationVisuals.play(p, "punch_right");
			AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 1.0f, 1.2f);
			ctx.triggerCooldown();
		}));

		// G -- Ground Wave: a shockwave that travels along the ground. Shift + G: a burst all round you instead.
		AbilityHandlers.register(KEY, "ground_wave", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				float mult = boost(ctx, 15.0f, 0.5f);
				if (p.isShiftKeyDown()) {
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 8.0)) {
						AbilityHelpers.hurt(p, e, BURST_DAMAGE * mult);
						AbilityHelpers.push(e, new Vec3(0, 0.6, 0));
						AbilityHelpers.knockbackFrom(e, p.position(), 0.8);
					}
					for (int i = 1; i <= 4; i++) {
						distortionRing(level, p.position().add(0, 0.2, 0), i * 1.8);
					}
					groundDebris(level, p.position(), 3.0);
					recordImpact(p, p.position());
					MutationVisuals.play(p, "stomp");
					AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 1.0f, 0.7f);
					ctx.triggerCooldown(6 * 20);
					return;
				}
				Vec3 look = p.getLookAngle();
				Vec3 dir = new Vec3(look.x, 0, look.z);
				dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
				WAVES.put(p.getUUID(), new Wave(level, p.getX(), p.getY(), p.getZ(), dir.x, dir.z, 16, WAVE_DAMAGE * mult));
				MutationVisuals.play(p, "ground_pound");
				AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 1.0f, 0.9f);
				ctx.triggerCooldown();
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				Wave w = WAVES.get(ctx.player().getUUID());
				if (w != null) {
					waveTick(ctx.player(), w);
				}
			}
		});

		// X -- Recoil Jump: blast yourself up and forward. 10 Kinetic makes it bigger and blasts the ground you left.
		AbilityHandlers.register(KEY, "recoil_jump", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			boolean big = ctx.resource("charge") >= 10.0f;
			if (big) {
				ctx.addResource("charge", -10.0f, MAX_CHARGE);
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 3.0)) {
					AbilityHelpers.hurt(p, e, 6.0f);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
				}
			}
			double s = big ? 1.3 : 1.0;
			Vec3 look = p.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
			Vec3 launch = flat.scale(0.9 * s).add(p.getDeltaMovement().x * 0.3, 1.15 * s, p.getDeltaMovement().z * 0.3);
			AbilityHelpers.launchSelf(p, launch);
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			distortionRing(level, p.position().add(0, 0.1, 0), 1.2);
			if (big) {
				distortionRing(level, p.position().add(0, 0.1, 0), 2.6);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			MutationVisuals.play(p, "leap");
			AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.9f, 1.5f);
			ctx.triggerCooldown();
		}));

		// Z -- Kinetic Detonation: hold ~4.25 s with a full gauge, release it all in a 20-block wave.
		AbilityHandlers.register(KEY, "kinetic_detonation", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("kd_charging") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				if (ctx.resource("charge") < MAX_CHARGE - 0.5f) {
					ctx.actionBar("message.projecthero.shockwave.charge_full_needed");
					return;
				}
				ctx.setResource("kd_charging", 1, 1);
				ctx.setResource("kd_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("kd_charging") > 0.5f) {
					ctx.setResource("kd_charging", 0, 1);
					ctx.setResource("kd_progress", 0, 100);
					MutationVisuals.stopIf(ctx.player(), "crouch_charge");
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("kd_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				long held = p.level().getGameTime() - (long) ctx.resource("kd_charge_start");
				MutationVisuals.ensure(p, "crouch_charge");
				double frac = Math.min(1.0, held / (double) DETONATION_CHARGE);
				if (held % 4 == 0) {
					ctx.setResource("kd_progress", (float) (frac * 100), 100);
					BatchCFx.flatRing(level, p.position().add(0, 0.1, 0), 3.5 - frac * 2.8, 16, kineticDust(1.2f), 0);
				}
				if (held % 10 == 0) {
					AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.4f, 0.6f + (float) frac);
				}
				if (held >= DETONATION_CHARGE) {
					ctx.setResource("kd_charging", 0, 1);
					ctx.setResource("kd_progress", 0, 100);
					if (!ctx.spendResource("charge", MAX_CHARGE - 0.5f)) {
						MutationVisuals.stopIf(p, "crouch_charge");
						return;
					}
					ctx.setResource("charge", 0, MAX_CHARGE);
					double r = 20.0;
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
						AbilityHelpers.hurtBurst(p, e, DETONATION_DAMAGE);
						AbilityHelpers.knockbackFrom(e, p.position(), 3.0);
						AbilityHelpers.push(e, new Vec3(0, 0.8, 0));
						AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
					}
					for (int i = 1; i <= 5; i++) {
						distortionRing(level, p.position().add(0, 0.3, 0), i * 3.8);
						BatchCFx.flatRing(level, p.position().add(0, 1.2, 0), i * 0.6, 20 + i * 4, kineticDust(1.6f), 0.5 + i * 0.3);
					}
					groundDebris(level, p.position(), 5.0);
					level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
					recordImpact(p, p.position());
					MutationVisuals.play(p, "slam_two_hand");
					AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.4f);
					ctx.triggerCooldown();
				}
			}
		});

		// V -- Repulsion Field (hold): a 3-block field that shoves enemies and throws projectiles back. 6 Kinetic/s.
		AbilityHandlers.register(KEY, "repulsion_field", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("charge") < 6.0f) {
					ctx.actionBar("message.projecthero.shockwave.charge_low");
					return;
				}
				ctx.setResource("repelling", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("repelling") > 0.5f) {
					ctx.setResource("repelling", 0, 1);
					MutationVisuals.stopIf(ctx.player(), "shield_brace");
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("repelling") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.tickCount % 5 == 0 && !ctx.spendResource("charge", 1.5f)) { // 6/s
					ctx.setResource("repelling", 0, 1);
					MutationVisuals.stopIf(p, "shield_brace");
					ctx.actionBar("message.projecthero.shockwave.charge_low");
					return;
				}
				MutationVisuals.ensure(p, "shield_brace");
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 3.0)) {
					AbilityHelpers.knockbackFrom(e, p.position(), 0.9);
				}
				for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.0))) {
					if (proj.getOwner() != p) {
						proj.setDeltaMovement(proj.position().subtract(p.position()).normalize().scale(1.4));
						proj.hurtMarked = true;
					}
				}
				if (p.tickCount % 4 == 0) {
					Vec3 c = p.position().add(0, 1.0, 0);
					double r = 2.2 + 0.4 * ((p.tickCount / 4) % 3);
					BatchCFx.flatRing(ctx.level(), c, r, 22, distortion(0.9f), 0);
					BatchCFx.ring(ctx.level(), c, p.getLookAngle(), r * 0.8, 16, distortion(0.7f), 0);
				}
			}
		});

		// C -- Charge (hold): bank 5 Kinetic/s and brace -- hits taken while bracing store double and hurt less.
		AbilityHandlers.register(KEY, "charge", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("charging", 1, 1);
				AbilityHelpers.sound(ctx.player(), SoundEvents.WARDEN_SONIC_CHARGE, 0.35f, 1.2f);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				MutationVisuals.ensure(p, "crouch_charge");
				if (p.tickCount % 4 == 0) {
					ctx.addResource("charge", 1.0f, MAX_CHARGE); // 5/s
					ctx.level().sendParticles(kineticDust(1.0f), p.getX(), p.getY() + 1, p.getZ(), 5, 0.4, 0.55, 0.4, 0.0);
					BatchCFx.flatRing(ctx.level(), p.position().add(0, 0.1, 0), 1.6 - 0.3 * ((p.tickCount / 4) % 4), 14,
							distortion(0.8f), 0);
				}
				if (p.tickCount % 25 == 0) {
					AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.2f, 1.4f);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("charging") > 0.5f) {
					ctx.setResource("charging", 0, 1);
					MutationVisuals.stopIf(ctx.player(), "crouch_charge");
					ctx.triggerCooldown(85);
				}
			}
		});

		// H -- Aftershock: a delayed second wave where your last wave hit.
		AbilityHandlers.register(KEY, "aftershock", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				Impact last = LAST_IMPACT.get(p.getUUID());
				long now = p.level().getGameTime();
				if (last == null || last.level() != p.level() || now - last.at() > 12 * 20
						|| last.pos().distanceToSqr(p.position()) > 48 * 48) {
					ctx.actionBar("message.projecthero.shockwave.no_wave");
					return;
				}
				float mult = boost(ctx, 15.0f, 0.5f);
				AFTERSHOCKS.put(p.getUUID(), new Pending(last.level(), last.pos(), now + AFTERSHOCK_DELAY, AFTERSHOCK_DAMAGE * mult));
				MutationVisuals.play(p, "stomp");
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 0.8f);
				ctx.triggerCooldown();
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				Pending a = AFTERSHOCKS.get(ctx.player().getUUID());
				if (a != null) {
					aftershockTick(ctx.player(), a);
				}
			}
		});

		// N -- Kinetic Parry: a 0.7 s window. The next melee hit is negated, stored x2 and its attacker thrown back.
		AbilityHandlers.register(KEY, "kinetic_parry", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ctx.setResource("parry_until", p.level().getGameTime() + PARRY_TICKS, 1.0e12f);
			BatchCFx.ring(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(0.7)), p.getLookAngle(), 0.6, 14,
					distortion(0.9f), 0);
			MutationVisuals.play(p, "p21.parry");
			AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.6f);
			ctx.triggerCooldown();
		}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				com.projecthero.mod.hero.power.PowerToggles.modifier(player,
						net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
						com.projecthero.mod.ProjectHeroMod.id("shockwave_kb"), 0.7,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
			} else {
				com.projecthero.mod.hero.power.PowerToggles.clearModifier(player,
						net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
						com.projecthero.mod.ProjectHeroMod.id("shockwave_kb"));
				WAVES.remove(player.getUUID());
				AFTERSHOCKS.remove(player.getUUID());
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, ShockwaveHandlers::passiveTick);
	}

	private static final Set<String> STALE = Set.of("pulse_ticks", "pulse_pos_x", "pulse_pos_z", "pulse_dir_x", "pulse_dir_z");

	private static void passiveTick(ServerPlayer player) {
		if (player.tickCount % 100 == 0) {
			BatchCFx.purge(player, KEY, STALE, Set.of());
		}
		if (player.tickCount % 5 == 0 && !ExperimentalPowers.state(player).resources.containsKey(KEY + "/charge")) {
			Power power = power();
			if (power != null) {
				ExperimentalPowers.setResource(player, power, "charge", 0, MAX_CHARGE);
			}
		}
	}

	// ---- routines -----------------------------------------------------------------------------

	private static void waveTick(ServerPlayer p, Wave w) {
		if (p.level() != w.level || w.ticksLeft-- <= 0) {
			WAVES.remove(p.getUUID());
			return;
		}
		ServerLevel level = w.level;
		w.x += w.dx * 1.3;
		w.z += w.dz * 1.3;
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(w.x), (int) Math.floor(w.z));
		w.y = Math.max(w.y - 2.0, Math.min(w.y + 2.0, surface)); // 2-block step assist
		Vec3 pt = new Vec3(w.x, w.y, w.z);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, pt.add(0, 0.8, 0), 2.2)) {
			if (w.hit.add(e.getId())) {
				AbilityHelpers.hurt(p, e, w.damage);
				AbilityHelpers.knockbackFrom(e, pt, 1.4);
				AbilityHelpers.push(e, new Vec3(0, 0.35, 0));
				recordImpact(p, e.position());
			}
		}
		distortionRing(level, pt.add(0, 0.15, 0), 1.3);
		groundDebris(level, pt, 0.8);
		if (w.ticksLeft <= 0) {
			if (w.hit.isEmpty()) {
				recordImpact(p, pt);
			}
			WAVES.remove(p.getUUID());
		}
	}

	private static void aftershockTick(ServerPlayer p, Pending a) {
		long now = a.level().getGameTime();
		if (p.level() != a.level()) {
			AFTERSHOCKS.remove(p.getUUID());
			return;
		}
		if (now < a.fireAt()) {
			// telegraph: rings tightening on the spot
			if (now % 2 == 0) {
				double frac = (a.fireAt() - now) / (double) AFTERSHOCK_DELAY;
				BatchCFx.flatRing(a.level(), a.pos().add(0, 0.15, 0), 0.6 + 4.0 * frac, 18, kineticDust(1.2f), 0);
			}
			return;
		}
		AFTERSHOCKS.remove(p.getUUID());
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, a.pos().add(0, 0.8, 0), 5.0)) {
			AbilityHelpers.hurtBurst(p, e, a.damage());
			AbilityHelpers.push(e, new Vec3(0, 0.75, 0));
			AbilityHelpers.knockbackFrom(e, a.pos(), 0.8);
		}
		for (int i = 1; i <= 3; i++) {
			distortionRing(a.level(), a.pos().add(0, 0.2, 0), i * 1.6);
		}
		groundDebris(a.level(), a.pos(), 2.5);
		a.level().sendParticles(ParticleTypes.GUST_EMITTER_SMALL, a.pos().x, a.pos().y + 0.5, a.pos().z, 1, 0, 0, 0, 0);
		a.level().playSound(null, BlockPos.containing(a.pos()), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.4f, 0.6f);
	}

	/** Chunks of the ground block kicked up by a wave. */
	private static void groundDebris(ServerLevel level, Vec3 at, double spread) {
		BlockPos below = BlockPos.containing(at).below();
		var st = level.getBlockState(below);
		if (st.isAir()) {
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y + 0.1, at.z,
				(int) (6 + spread * 6), spread * 0.5, 0.1, spread * 0.5, 0.15);
	}

	/** Server tick hook (from RevampBatchC) -- nothing global to do today; kept for symmetry with the other powers. */
	public static void serverTick(MinecraftServer server) {
	}

	public static void clearSessionState() {
		WAVES.clear();
		LAST_IMPACT.clear();
		AFTERSHOCKS.clear();
	}
}
