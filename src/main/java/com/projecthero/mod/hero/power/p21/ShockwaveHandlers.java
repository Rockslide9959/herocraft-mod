package com.projecthero.mod.hero.power.p21;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Power 21 — Shockwave Manipulation. C builds a shared 0-100 Charge bar spent by the other abilities. */
public final class ShockwaveHandlers {
	private static final String KEY = "power_21_shockwave_manipulation";
	private static final float MAX_CHARGE = 100.0f;

	private ShockwaveHandlers() {
	}

	public static void register() {
		// R -- Shockwave Punch. Shift+R is Shockwave Pulse, a traveling ground wave with step assist.
		AbilityHandlers.register(KEY, "shockwave_punch", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				if (!ctx.spendResource("charge", 10.0f)) {
					ctx.actionBar("message.projecthero.shockwave.charge_low");
					return;
				}
				ctx.setResource("pulse_ticks", 16, 16);
				ctx.setResource("pulse_pos_x", (float) p.getX(), 1.0e9f);
				ctx.setResource("pulse_pos_z", (float) p.getZ(), 1.0e9f);
				ctx.setResource("pulse_dir_x", (float) p.getLookAngle().x, 2);
				ctx.setResource("pulse_dir_z", (float) p.getLookAngle().z, 2);
				AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST, 1.0f, 0.9f);
				ctx.triggerCooldown(8 * 20);
				return;
			}
			if (!ctx.spendResource("charge", 5.0f)) {
				ctx.actionBar("message.projecthero.shockwave.charge_low");
				return;
			}
			double range = 7.0;
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(range * 0.5));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, range * 0.5 + 1.0)) {
				AbilityHelpers.hurt(p, e, 10.0f);
				AbilityHelpers.knockbackFrom(e, p.getEyePosition(), 1.3);
			}
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(range)),
					ParticleTypes.SONIC_BOOM, 1.5);
			AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST, 1.0f, 1.2f);
			ctx.triggerCooldown(3 * 20);
		}));

		AbilityHandlers.register(KEY, "ground_wave", Handlers.instantTicking(ctx -> {
			ctx.setResource("wave", 20, 20);
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
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, ring, 2.0)) {
				AbilityHelpers.hurt(p, e, 15.0f);
				AbilityHelpers.push(e, new Vec3(0, 0.6, 0));
				AbilityHelpers.knockbackFrom(e, p.position(), 0.8);
			}
			ctx.level().sendParticles(ParticleTypes.CLOUD, ring.x, ring.y + 0.2, ring.z, 12, 1.0, 0.1, 1.0, 0.02);
		}));

		// X -- Recoil Jump (unchanged).
		AbilityHandlers.register(KEY, "recoil_jump", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 look = p.getLookAngle();
			Vec3 launch = new Vec3(look.x, 0, look.z).normalize().scale(0.9)
					.add(p.getDeltaMovement().x * 0.3, 1.15, p.getDeltaMovement().z * 0.3);
			AbilityHelpers.launchSelf(p, launch);
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			ctx.level().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST, 0.9f, 1.5f);
			ctx.triggerCooldown();
		}));

		// Z -- hold for 5 seconds to charge, costs the full Charge bar, 100s cooldown.
		AbilityHandlers.register(KEY, "kinetic_detonation", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("kd_charging") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				if (ctx.resource("charge") < MAX_CHARGE - 0.5f) {
					ctx.actionBar("message.projecthero.shockwave.charge_low");
					return;
				}
				ctx.setResource("kd_charging", 1, 1);
				ctx.setResource("kd_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("kd_charging") > 0.5f) {
					ctx.setResource("kd_charging", 0, 1);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("kd_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("kd_charge_start");
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
				}
				if (held >= 5 * 20) {
					ctx.setResource("kd_charging", 0, 1);
					if (!ctx.spendResource("charge", MAX_CHARGE)) {
						return;
					}
					double r = 20.0;
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
						AbilityHelpers.hurt(p, e, 60.0f);
						AbilityHelpers.knockbackFrom(e, p.position(), 3.0);
						AbilityHelpers.push(e, new Vec3(0, 0.8, 0));
						AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
					}
					ctx.level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
					ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 20, r / 2, 1.0, r / 2, 0.0);
					AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.4f);
					ctx.triggerCooldown(100 * 20);
				}
			}
		});

		// V -- Repulsion Field, now drawing from the shared Charge bar.
		AbilityHandlers.register(KEY, "repulsion_field", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("charge") < 7.0f) {
					ctx.actionBar("message.projecthero.shockwave.charge_low");
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
				if (!ctx.spendResource("charge", 0.35f)) { // 7/s
					ctx.setResource("repelling", 0, 1);
					ctx.actionBar("message.projecthero.shockwave.charge_low");
					return;
				}
				ServerPlayer p = ctx.player();
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 2.0)) {
					AbilityHelpers.knockbackFrom(e, p.position(), 0.9);
				}
				for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(2.0))) {
					proj.setDeltaMovement(proj.position().subtract(p.position()).normalize().scale(1.4));
				}
				if (p.tickCount % 4 == 0) {
					ctx.level().sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 4, 1.6, 0.5, 1.6, 0.0);
				}
			}
		});

		// C -- Charge: 3/s while held, 5s cooldown once released.
		AbilityHandlers.register(KEY, "charge", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("charging", 1, 1);
				AbilityHelpers.sound(ctx.player(), SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 1.2f);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("charging") < 0.5f) {
					return;
				}
				ctx.addResource("charge", 0.15f, MAX_CHARGE); // 3/s
				ServerPlayer p = ctx.player();
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

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("charging") > 0.5f) {
					ctx.setResource("charging", 0, 1);
					ctx.triggerCooldown(5 * 20);
				}
			}
		});

		// Traveling Shockwave Pulse (Shift+R): tick independent of the R key so a tap-and-release still
		// carries the wave along the ground with 2-block step assist.
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			var power = Powers.byKey(KEY);
			if (power == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
				return;
			}
			int t = (int) ExperimentalPowers.getResource(player, power, "pulse_ticks");
			if (t <= 0) {
				return;
			}
			ExperimentalPowers.setResource(player, power, "pulse_ticks", t - 1, 16);
			float x = ExperimentalPowers.getResource(player, power, "pulse_pos_x");
			float z = ExperimentalPowers.getResource(player, power, "pulse_pos_z");
			float dx = ExperimentalPowers.getResource(player, power, "pulse_dir_x");
			float dz = ExperimentalPowers.getResource(player, power, "pulse_dir_z");
			x += dx * 1.3f;
			z += dz * 1.3f;
			ExperimentalPowers.setResource(player, power, "pulse_pos_x", x, 1.0e9f);
			ExperimentalPowers.setResource(player, power, "pulse_pos_z", z, 1.0e9f);
			BlockPos ground = BlockPos.containing(x, player.getY(), z);
			int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
					ground.getX(), ground.getZ());
			double y = Math.max(player.getY() - 2.0, Math.min(player.getY() + 2.0, surface)); // 2-block step assist
			Vec3 pt = new Vec3(x, y, z);
			for (LivingEntity e : AbilityHelpers.enemiesAround(player, pt, 2.0)) {
				AbilityHelpers.hurt(player, e, 13.0f);
				AbilityHelpers.knockbackFrom(e, pt, 1.4);
			}
			level.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y + 0.3, pt.z, 2, 0.4, 0.2, 0.4, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, pt.x, pt.y + 0.1, pt.z, 6, 0.6, 0.1, 0.6, 0.01);
		});

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
			}
		});
	}
}
