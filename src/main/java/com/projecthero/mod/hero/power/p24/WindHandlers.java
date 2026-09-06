package com.projecthero.mod.hero.power.p24;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.ModeMeter;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Power 24 — Wind Manipulation. */
public final class WindHandlers {
	private static final String KEY = "power_24_wind_manipulation";
	private static final float MAX_WIND = 500.0f;
	private static final float WIND_DRAIN = MAX_WIND / (20 * 20); // tailwind holds ~20 s
	private static final float WIND_REGEN = MAX_WIND / (30 * 20);

	private WindHandlers() {
	}

	private static boolean tailwindActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));
	}

	public static void register() {
		AbilityHandlers.register(KEY, "wind_blade", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 26.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 26.0), ParticleTypes.SWEEP_ATTACK, 2.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 8.0f);
				AbilityHelpers.knockbackFrom(t, p.position(), 0.7);
			}
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}));

		// Wind Burst (replaces Tornado): 12 damage in a 6-block cone ahead of the player, with far
		// pushback. Same cooldown Tornado had.
		AbilityHandlers.register(KEY, "tornado", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 eye = p.getEyePosition();
			Vec3 look = p.getLookAngle();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(3.0)), 6.5)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
				if (to.length() > 6.0 || to.normalize().dot(look) < 0.6) {
					continue; // outside the ~53° forward cone
				}
				AbilityHelpers.hurt(p, e, 12.0f);
				AbilityHelpers.knockbackFrom(e, p.position(), 3.2);
				AbilityHelpers.push(e, look.scale(1.6).add(0, 0.35, 0));
			}
			for (var proj : ctx.level().getEntitiesOfClass(Projectile.class,
					p.getBoundingBox().inflate(6.0))) {
				proj.setDeltaMovement(look.scale(2.5));
			}
			for (int i = 1; i <= 6; i++) {
				Vec3 pt = eye.add(look.scale(i));
				ctx.level().sendParticles(ParticleTypes.CLOUD, pt.x, pt.y, pt.z, 10, 0.3 * i, 0.3 * i, 0.3 * i, 0.05);
			}
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.3f, 0.7f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "wind_flight", new AbilityHandler() {
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
				if (!HeroFlight.isFlying(ctx.player())) {
					ctx.setToggled(false);
				} else if (ctx.player().tickCount % 4 == 0) {
					ctx.level().sendParticles(ParticleTypes.CLOUD, ctx.player().getX(), ctx.player().getY(),
							ctx.player().getZ(), 2, 0.3, 0.1, 0.3, 0.0);
				}
			}
		});

		// Hurricane: 5 dmg/s, 20 s, 15-block radius.
		AbilityHandlers.register(KEY, "hurricane", Handlers.instantTicking(ctx -> {
			ctx.setResource("hurr", 400, 400);
			ctx.triggerCooldown();
			AbilityHelpers.sound(ctx.player(), SoundEvents.BREEZE_IDLE_GROUND, 1.4f, 0.35f);
		}, ctx -> {
			int t = (int) ctx.resource("hurr");
			if (t <= 0) {
				return;
			}
			ctx.setResource("hurr", t - 1, 400);
			ServerPlayer p = ctx.player();
			if (t % 20 == 0) {
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 15.0)) {
					Vec3 tangent = new Vec3(-(e.getZ() - p.getZ()), 0.35, e.getX() - p.getX()).normalize().scale(0.7);
					AbilityHelpers.push(e, tangent);
					AbilityHelpers.hurt(p, e, 5.0f);
				}
			}
			if (t % 5 == 0) {
				for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(15.0))) {
					proj.setDeltaMovement(proj.getDeltaMovement().reverse());
				}
			}
			if (t % 2 == 0) {
				double a = t * 0.5;
				for (double rr = 4; rr <= 14; rr += 5) {
					ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * rr, p.getY() + 1,
							p.getZ() + Math.sin(a) * rr, 2, 0.2, 0.6, 0.2, 0.0);
				}
			}
		}));

		AbilityHandlers.register(KEY, "wind_push", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(4)), 4.5)) {
				AbilityHelpers.knockbackFrom(e, p.position(), 4.5);
				AbilityHelpers.push(e, p.getLookAngle().scale(2.2).add(0, 0.3, 0));
			}
			for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6.0))) {
				proj.setDeltaMovement(p.getLookAngle().scale(2.0));
			}
			ctx.level().sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 6, 1.5, 0.5, 1.5, 0.0);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.2f, 0.9f);
			ctx.triggerCooldown();
		}));

		// Tailwind: jump ~3 blocks high, drift under Slow Falling II, and a constant outward gust shoves
		// anything that comes within 2 blocks back by ~1 block. Drains a wind bank that refills when off.
		AbilityHandlers.register(KEY, "tailwind", Handlers.toggle(
				ctx -> {
					ModeMeter.ensureSeeded(ctx, "tailwind", MAX_WIND);
					if (!ModeMeter.hasCharge(ctx, "tailwind", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.wind.wind_low");
					}
				},
				Handlers.noop(),
				ctx -> {
					ServerPlayer p = ctx.player();
					p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, false, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 3, false, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20, 1, false, false, true));
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 2.0)) {
						Vec3 away = e.position().subtract(p.position());
						double d = away.horizontalDistance();
						if (d > 0.05) {
							away = new Vec3(away.x / d, 0.15, away.z / d).scale(1.0); // ~1 block of shove
							AbilityHelpers.push(e, away);
						}
					}
					if (p.tickCount % 6 == 0) {
						ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0.0);
					}
					p.resetFallDistance();
					if (!ModeMeter.drain(ctx, "tailwind", MAX_WIND, WIND_DRAIN)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.wind.wind_out");
					}
				}));

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player ->
				ModeMeter.regen(player, Powers.byKey(KEY), "tailwind", MAX_WIND, WIND_REGEN, tailwindActive(player)));
	}
}
