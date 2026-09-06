package com.projecthero.mod.hero.power.p03;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.HeroFlight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Power 03 — Flight. Uses {@link HeroFlight} (independent of Thor flight). Flight itself never runs out. */
public final class FlightHandlers {
	private static final String KEY = "power_03_flight";

	private FlightHandlers() {
	}

	/** Stop Sonic Flight (manual or timed) and only now start its cooldown. */
	private static void endSonicFlight(AbilityContext ctx) {
		if (ctx.resource("sonic_ticks") <= 0.0f) {
			return;
		}
		ctx.setResource("sonic_ticks", 0, 25 * 20);
		AbilityHelpers.sound(ctx.player(), SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.9f);
		ctx.triggerCooldown();
	}

	public static void register() {
		// Air Dash: a burst forward that also drives a shoulder-check into anything you dash into.
		AbilityHandlers.register(KEY, "air_dash", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			double power = HeroFlight.isFlying(p) ? 1.9 : 1.1;
			AbilityHelpers.addImpulse(p, p.getLookAngle().scale(power));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 16, 0.3);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.8f, 1.4f);
			ctx.setResource("dash_ticks", 6, 6);
			ctx.triggerCooldown();
		}, ctx -> {
			int t = (int) ctx.resource("dash_ticks");
			if (t <= 0) {
				return;
			}
			ctx.setResource("dash_ticks", t - 1, 6);
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(p.getLookAngle().scale(1.0)), 1.8)) {
				if (e.invulnerableTime <= 0) {
					AbilityHelpers.hurt(p, e, 8.0f);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
				}
			}
		}));

		AbilityHandlers.register(KEY, "dive_bomb", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, -2.6, p.getDeltaMovement().z));
			ctx.setResource("diving", 1, 1);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.9f, 0.6f);
			ctx.triggerCooldown();
		}, ctx -> {
			if (ctx.resource("diving") < 0.5f) {
				return;
			}
			ServerPlayer p = ctx.player();
			if (p.onGround() || p.isInWater()) {
				double speed = Math.min(3.0, Math.abs(p.getDeltaMovement().y) + p.fallDistance / 10.0);
				double r = 2.5 + speed;
				float meteor = com.projecthero.mod.hero.power.PowerCombos.meteorSlamBonus(p, KEY);
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
					AbilityHelpers.hurt(p, e, Math.max(15.0f, (float) (6.0 + speed * 3.0)) + meteor);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.0 + speed * 0.3);
					AbilityHelpers.push(e, new Vec3(0, 0.5, 0));
				}
				ctx.level().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
				ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 40, r / 2, 0.1, r / 2, 0.05);
				AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.9f, 1.0f);
				ctx.setResource("diving", 0, 1);
			}
		}));

		AbilityHandlers.register(KEY, "flight_toggle", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				if (!HeroFlight.startFlying(ctx.player(), ctx.power())) {
					ctx.setToggled(false);
				}
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				HeroFlight.setFlying(ctx.player(), false);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer pl = ctx.player();
				if (!HeroFlight.isFlying(pl)) {
					ctx.setToggled(false);
					return;
				}
				if (pl.tickCount % 2 == 0 && pl.getDeltaMovement().lengthSqr() > 0.35
						&& com.projecthero.mod.hero.power.PowerCombos.flameFlightTrail(pl)) {
					ctx.level().sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
							pl.getX(), pl.getY() + 0.3, pl.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
				}
			}
		});

		// Sonic Flight: up to 25 seconds of max-speed flight. The sheer speed tears off shockwaves that
		// deal 9 damage to anything caught in them, and leaves a roaring vapour trail. Press again to
		// end it early; the cooldown only begins once the ability actually ends.
		AbilityHandlers.register(KEY, "sonic_flight", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (ctx.resource("sonic_ticks") > 0.5f) {
				endSonicFlight(ctx); // manual early stop
				return;
			}
			if (!HeroFlight.isFlying(p)) {
				HeroFlight.setFlying(p, true);
			}
			ctx.setResource("sonic_ticks", 25 * 20, 25 * 20);
			p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.4f);
		}, ctx -> {
			int t = (int) ctx.resource("sonic_ticks");
			if (t <= 0) {
				return;
			}
			ctx.setResource("sonic_ticks", t - 1, 25 * 20);
			ServerPlayer p = ctx.player();
			AbilityHelpers.addImpulse(p, p.getLookAngle().scale(0.35));
			// the vapour trail: a continuous streak of cloud pulled out behind the flight path
			Vec3 behind = p.position().subtract(p.getDeltaMovement().normalize().scale(0.8));
			ctx.level().sendParticles(ParticleTypes.CLOUD, behind.x, behind.y + 0.3, behind.z, 4, 0.15, 0.15, 0.15, 0.01);
			ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, behind.x, behind.y + 0.3, behind.z, 1, 0.0, 0.0, 0.0, 0.0);
			// a shockwave every half second
			if (t % 10 == 0 && p.getDeltaMovement().lengthSqr() > 0.2) {
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 4.5)) {
					AbilityHelpers.hurt(p, e, 9.0f);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.6);
				}
				ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 30, 2.5, 1.0, 2.5, 0.15);
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.8f);
			}
			if (t - 1 <= 0) {
				endSonicFlight(ctx); // ran its full course
			}
		}));

		// Carry (replaces Hover): pick up the creature/player you are looking at and fly them around.
		AbilityHandlers.register(KEY, "carry", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				LivingEntity t = AbilityHelpers.raycastEntity(p, 8.0);
				if (t == null || !AbilityHelpers.isValidGrabTarget(t, p)) {
					ctx.setToggled(false);
					ctx.actionBar("message.projecthero.ability.grabbed");
					return;
				}
				ctx.setResource("carry_id", t.getId(), 1.0e9f);
				AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				drop(ctx);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				Entity e = ctx.level().getEntity((int) ctx.resource("carry_id"));
				if (!(e instanceof LivingEntity le) || !le.isAlive() || p.distanceToSqr(e) > 400) {
					ctx.setToggled(false);
					drop(ctx);
					return;
				}
				double y = p.getY() - le.getBbHeight() - 0.15;
				le.setPos(p.getX(), y, p.getZ());
				le.setDeltaMovement(Vec3.ZERO);
				le.fallDistance = 0;
				le.hurtMarked = true;
				le.resetFallDistance();
			}

			private void drop(AbilityContext ctx) {
				Entity e = ctx.level().getEntity((int) ctx.resource("carry_id"));
				ctx.setResource("carry_id", 0, 1.0e9f);
				if (e instanceof LivingEntity le && le.isAlive() && !le.onGround()) {
					le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, false));
				}
			}
		});

		AbilityHandlers.register(KEY, "aerial_burst", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.addImpulse(p, new Vec3(0, 1.2, 0));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 24, 0.4);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.9f, 1.3f);
			ctx.triggerCooldown();
		}));
	}
}
