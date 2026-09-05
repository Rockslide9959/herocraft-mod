package com.herocraft.mod.hero.power.p21;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandler;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.ModeMeter;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Power 21 — Shockwave Manipulation. C builds charge that empowers the next R/G/Z. */
public final class ShockwaveHandlers {
	private static final String KEY = "power_21_shockwave_manipulation";
	private static final float MAX_CHARGE = 100.0f;
	private static final float MAX_REPULSION = 500.0f;
	private static final float REPULSION_DRAIN = MAX_REPULSION / (13 * 20); // hold for ~13 s
	private static final float REPULSION_REGEN = MAX_REPULSION / (22 * 20);

	private ShockwaveHandlers() {
	}

	private static boolean repellingActive(net.minecraft.server.level.ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.getResource(p, power, "repelling") > 0.5f;
	}

	private static float consumeCharge(AbilityContext ctx) {
		float c = ctx.resource("charge");
		ctx.setResource("charge", 0, MAX_CHARGE);
		return c / MAX_CHARGE; // 0..1 bonus factor
	}

	public static void register() {
		AbilityHandlers.register(KEY, "shockwave_punch", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float bonus = consumeCharge(ctx);
			double range = 4.0 + bonus * 4.0;
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(range * 0.5));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, range * 0.5 + 1.0)) {
				AbilityHelpers.hurt(p, e, 8.0f + bonus * 6.0f);
				AbilityHelpers.knockbackFrom(e, p.getEyePosition(), 1.3 + bonus * 1.5);
			}
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(range)),
					ParticleTypes.SONIC_BOOM, 1.5);
			AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST, 1.0f, 1.2f - bonus * 0.4f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "ground_wave", Handlers.instantTicking(ctx -> {
			ctx.setResource("wave", 20, 20);
			ctx.setResource("wave_bonus", consumeCharge(ctx), 1);
			AbilityHelpers.sound(ctx.player(), SoundEvents.WIND_CHARGE_BURST, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}, ctx -> {
			int t = (int) ctx.resource("wave");
			if (t <= 0) {
				return;
			}
			ctx.setResource("wave", t - 1, 20);
			ServerPlayer p = ctx.player();
			double dist = (20 - t) * 1.0;
			Vec3 ring = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(dist));
			float bonus = ctx.resource("wave_bonus");
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, ring, 2.0)) {
				AbilityHelpers.hurt(p, e, 12.0f + bonus * 4.0f);
				AbilityHelpers.push(e, new Vec3(0, 0.6, 0));
				AbilityHelpers.knockbackFrom(e, p.position(), 0.8);
			}
			ctx.level().sendParticles(ParticleTypes.CLOUD, ring.x, ring.y + 0.2, ring.z, 12, 1.0, 0.1, 1.0, 0.02);
		}));

		// Recoil Jump: blast yourself up AND forward (like Super Strength's leap). Consumes any built
		// charge to launch further, and you take no fall damage from the landing.
		AbilityHandlers.register(KEY, "recoil_jump", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float bonus = consumeCharge(ctx);
			Vec3 look = p.getLookAngle();
			Vec3 launch = new Vec3(look.x, 0, look.z).normalize().scale(0.9 + bonus * 1.2)
					.add(p.getDeltaMovement().x * 0.3, 1.15 + bonus * 0.6, p.getDeltaMovement().z * 0.3);
			AbilityHelpers.launchSelf(p, launch);
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			ctx.level().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST, 0.9f, 1.5f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "kinetic_detonation", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float bonus = consumeCharge(ctx);
			double r = 6.0 + bonus * 4.0;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				AbilityHelpers.hurt(p, e, 35.0f + bonus * 8.0f);
				AbilityHelpers.knockbackFrom(e, p.position(), 2.5 + bonus * 1.5);
				AbilityHelpers.push(e, new Vec3(0, 0.8, 0));
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			}
			ctx.level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
			ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 8, r / 3, 0.5, r / 3, 0.0);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.6f);
			ctx.triggerCooldown();
		}));

		// Repulsion Field: HOLD to keep a bubble up that constantly shoves entities and projectiles away.
		// Drains a repulsion bank (~13 s), which refills on its own while it is not held.
		AbilityHandlers.register(KEY, "repulsion_field", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ModeMeter.ensureSeeded(ctx, "repulsion_field", MAX_REPULSION);
				if (!ModeMeter.hasCharge(ctx, "repulsion_field", 30.0f)) {
					ctx.actionBar("message.herocraft.shockwave.repulsion_low");
					return;
				}
				ctx.setResource("repelling", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("repelling", 0, 1);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("repelling") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 5.0)) {
					AbilityHelpers.knockbackFrom(e, p.position(), 0.9);
				}
				for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(5.0))) {
					proj.setDeltaMovement(proj.position().subtract(p.position()).normalize().scale(1.4));
				}
				if (p.tickCount % 4 == 0) {
					ctx.level().sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 4, 1.6, 0.5, 1.6, 0.0);
				}
				if (!ModeMeter.drain(ctx, "repulsion_field", MAX_REPULSION, REPULSION_DRAIN)) {
					ctx.setResource("repelling", 0, 1);
					ctx.actionBar("message.herocraft.shockwave.repulsion_out");
				}
			}
		});

		AbilityHandlers.register(KEY, "charge", Handlers.charge(
				ctx -> {
					ctx.setResource("charging", 1, 1);
					// a rising kinetic-charge whine as the player braces
					AbilityHelpers.sound(ctx.player(), SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 1.2f);
				},
				ctx -> {
					if (ctx.resource("charging") > 0.5f) {
						ctx.addResource("charge", 3.0f, MAX_CHARGE);
						ServerPlayer p = ctx.player();
						// a modest swirl of kinetic energy drawing inward while charging
						if (p.tickCount % 2 == 0) {
							ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK,
									p.getX(), p.getY() + 1, p.getZ(), 4, 0.35, 0.55, 0.35, 0.0);
							ctx.level().sendParticles(ParticleTypes.CRIT,
									p.getX(), p.getY() + 1, p.getZ(), 3, 0.45, 0.5, 0.45, 0.03);
						}
						if (p.tickCount % 8 == 0) {
							ctx.level().sendParticles(ParticleTypes.SONIC_BOOM,
									p.getX(), p.getY() + 0.2, p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
						}
					}
				},
				ctx -> ctx.setResource("charging", 0, 1)));

		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player ->
				ModeMeter.regen(player, Powers.byKey(KEY), "repulsion_field", MAX_REPULSION, REPULSION_REGEN,
						repellingActive(player)));

		com.herocraft.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				com.herocraft.mod.hero.power.PowerToggles.modifier(player,
						net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
						com.herocraft.mod.HeroCraftMod.id("shockwave_kb"), 0.2,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
			} else {
				com.herocraft.mod.hero.power.PowerToggles.clearModifier(player,
						net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
						com.herocraft.mod.HeroCraftMod.id("shockwave_kb"));
			}
		});
	}
}
