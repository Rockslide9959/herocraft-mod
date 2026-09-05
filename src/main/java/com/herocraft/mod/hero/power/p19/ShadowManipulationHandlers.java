package com.herocraft.mod.hero.power.p19;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.SafeTeleport;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Power 19 — Shadow Manipulation. Stronger in darkness. */
public final class ShadowManipulationHandlers {
	private static final String KEY = "power_19_shadow_manipulation";

	/** entityId -> game time the shadow-bind visual expires (drawn from the passive tick). */
	private static final Map<Integer, Long> BOUND = new ConcurrentHashMap<>();

	private static final int TOTAL_DARKNESS_TICKS = 25 * 20;

	/**
	 * Expire stale shadow-bind markers. The passive tick that normally prunes {@link #BOUND} only runs
	 * while some player has Shadow Manipulation selected, so an entry left behind by a player who
	 * switched power or logged out would otherwise stay forever -- see {@code ServerStateReset}.
	 */
	public static void pruneExpired(long now) {
		if (!BOUND.isEmpty()) {
			BOUND.values().removeIf(expiry -> expiry <= now);
		}
	}

	public static void clearSessionState() {
		BOUND.clear();
	}

	private ShadowManipulationHandlers() {
	}

	private static boolean isDark(ServerPlayer p) {
		return ((ServerLevel) p.level()).getMaxLocalRawBrightness(p.blockPosition()) <= 5;
	}

	/** Halved cooldowns while standing in darkness (the "stronger ability regen in darkness" passive). */
	private static void cd(AbilityContext ctx, int base) {
		ctx.triggerCooldown(isDark(ctx.player()) ? base / 2 : base);
	}

	/** End Total Darkness (manual or timed) and only now start its 50 s cooldown. */
	private static void endTotalDarkness(AbilityContext ctx) {
		if (ctx.resource("total_darkness") <= 0.0f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("total_darkness", 0, TOTAL_DARKNESS_TICKS);
		p.removeEffect(MobEffects.MOVEMENT_SPEED);
		p.removeEffect(MobEffects.DAMAGE_BOOST);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.8f, 0.9f);
		ctx.triggerCooldown(1000); // 50 s (halved to 25 s in darkness by cd(), but this one is fixed on end)
	}

	public static void register() {
		AbilityHandlers.register(KEY, "shadow_bolt", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 24.0), ParticleTypes.SQUID_INK, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 8.0f);
				AbilityHelpers.applyControl(t, MobEffects.BLINDNESS, 140, 0); // blind for 7 s
			}
			AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.0f, 0.6f);
			cd(ctx, 40);
		}));

		AbilityHandlers.register(KEY, "shadow_tendrils", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(4)), 4.0)) {
				AbilityHelpers.hurt(p, e, 10.0f);
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 140, 0); // blind for 7 s
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 80, 5);
				Vec3 pull = p.position().subtract(e.position()).normalize().scale(0.8);
				AbilityHelpers.push(e, pull);
			}
			AbilityHelpers.burst(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(4)), ParticleTypes.SQUID_INK, 30, 0.8);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 0.7f);
			cd(ctx, 160);
		}));

		// Shadow Step: a plain 25-block blink in the direction you are looking.
		AbilityHandlers.register(KEY, "shadow_step", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			Vec3 from = p.position();
			if (SafeTeleport.blink(p, p.getLookAngle(), 25.0)) {
				level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y + 1, from.z, 20, 0.3, 0.5, 0.3, 0.1);
				level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
				// blink straight into an enemy -> a 5-damage shadow strike as you rematerialise inside them
				for (LivingEntity hit : AbilityHelpers.living(level, p.position().add(0, p.getBbHeight() * 0.5, 0), 1.6,
						e -> e != p)) {
					AbilityHelpers.hurt(p, hit, 5.0f);
					AbilityHelpers.knockbackFrom(hit, p.position(), 0.6);
					AbilityHelpers.applyControl(hit, MobEffects.BLINDNESS, 60, 0);
				}
				cd(ctx, 80); // 4 s (halved in darkness)
			}
		}));

		// Total Darkness: 25 s of a personal darkness field. The "total_darkness" bar shows the time left;
		// press again to end it early. The 50 s cooldown only starts once the ability actually ends.
		AbilityHandlers.register(KEY, "total_darkness", Handlers.instantTicking(ctx -> {
			if (ctx.resource("total_darkness") > 0.5f) {
				endTotalDarkness(ctx); // manual early stop
				return;
			}
			ctx.setResource("total_darkness", TOTAL_DARKNESS_TICKS, TOTAL_DARKNESS_TICKS);
			ctx.player().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, TOTAL_DARKNESS_TICKS, 1, false, true, true));
			ctx.player().addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, TOTAL_DARKNESS_TICKS, 1, false, true, true));
			AbilityHelpers.sound(ctx.player(), SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.5f);
		}, ctx -> {
			int t = (int) ctx.resource("total_darkness");
			if (t <= 0) {
				return;
			}
			ctx.setResource("total_darkness", t - 1, TOTAL_DARKNESS_TICKS);
			ServerPlayer p = ctx.player();
			if (t % 20 == 0) {
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 8.0)) {
					e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
				}
			}
			if (t % 3 == 0) {
				ctx.level().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 3, 4, 1, 4, 0.0);
			}
			if (t - 1 <= 0) {
				endTotalDarkness(ctx);
			}
		}));

		// Shadow Bind (replaces Shadow Clone): pin the target in place and blind them for 7 s, wrapped in
		// a shroud of shadow particles so the effect is unmistakable.
		AbilityHandlers.register(KEY, "shadow_clone", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 22.0);
			if (t == null) {
				return;
			}
			AbilityHelpers.applyControl(t, MobEffects.BLINDNESS, 140, 0);
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 140, 9); // Slowness X
			AbilityHelpers.applyControl(t, MobEffects.JUMP, 140, -10); // cannot jump
			AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, 140, 2);
			BOUND.put(t.getId(), ctx.player().level().getGameTime() + 140);
			ctx.level().sendParticles(ParticleTypes.SQUID_INK, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
			ctx.level().sendParticles(ParticleTypes.SMOKE, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.9f, 0.5f);
			cd(ctx, 240);
		}));

		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player -> {
			if (!(player.level() instanceof ServerLevel level) || BOUND.isEmpty() || player.tickCount % 4 != 0) {
				return;
			}
			long now = level.getGameTime();
			BOUND.entrySet().removeIf(e -> {
				if (e.getValue() <= now) {
					return true;
				}
				if (level.getEntity(e.getKey()) instanceof LivingEntity le && le.isAlive()) {
					level.sendParticles(ParticleTypes.SQUID_INK,
							le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 5, 0.4, 0.6, 0.4, 0.02);
					level.sendParticles(ParticleTypes.SMOKE,
							le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 3, 0.4, 0.6, 0.4, 0.02);
					return false;
				}
				return true;
			});
		});

		// (see pruneExpired below -- the tick above only runs while somebody has this power selected)

		AbilityHandlers.register(KEY, "shadow_form", Handlers.toggle(Handlers.noop(), Handlers.noop(), ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.modeAura(p, net.minecraft.core.particles.ParticleTypes.SMOKE, 4);
			if (isDark(p)) {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, false, false, false));
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false, false));
				if (p.tickCount % 40 == 0) {
					for (Mob m : AbilityHelpers.living(ctx.level(), p.position(), 12.0, x -> x instanceof Mob).stream()
							.map(x -> (Mob) x).toList()) {
						if (m.getTarget() == p) {
							m.setTarget(null);
						}
					}
				}
			}
		}));
	}
}
