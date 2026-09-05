package com.herocraft.mod.hero.power.p16;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.spider.SpiderClimb;
import com.herocraft.mod.spider.SpiderClimbActions;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Power 16 -- Spider Climbing / Adhesion. The early arachnid power: real surface adhesion, but the
 * plain version of it. {@link com.herocraft.mod.spider.SpiderMan Spider-Man} is what this evolves
 * into, and the difference is deliberate -- Adhesion climbs more slowly, holds on less stubbornly and
 * has none of the web kit.
 *
 * <p>The adhesion itself is no longer implemented here. It moved to {@link SpiderClimb}, which both
 * powers share, so a player who has had Spider Adhesion since before the overhaul simply gets the
 * better climbing on their existing save with nothing to migrate: the same two toggles, read from the
 * same attachment, now drive a real engine instead of a {@code onClimbable} override.
 */
public final class SpiderClimbingHandlers {
	private static final String KEY = "power_16_spider_climbing_adhesion";

	private SpiderClimbingHandlers() {
	}

	/**
	 * True when the player should be sticking to surfaces right now. Kept as the power's own public
	 * entry point (it was here before the overhaul), but the actual answer comes from the shared
	 * engine so the two powers can never disagree about it.
	 */
	public static boolean wallClinging(Player p) {
		return SpiderClimb.profile(p) == SpiderClimb.Profile.ADHESION;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "adhesive_strike", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 4.5);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 7.0f);
				// pinned in place for 7 seconds
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 140, 200);
				AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, 140, 2);
				t.setDeltaMovement(Vec3.ZERO);
				t.hurtMarked = true;
			}
			AbilityHelpers.burst(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(2)), ParticleTypes.POOF, 8, 0.2);
			AbilityHelpers.sound(p, SoundEvents.SPIDER_HURT, 0.8f, 1.4f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "pounce", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 dir = p.getLookAngle();
			AbilityHelpers.launchSelf(p, new Vec3(dir.x * 1.9, Math.max(0.45, dir.y * 1.6 + 0.35), dir.z * 1.9));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CRIT, 12, 0.3);
			AbilityHelpers.sound(p, SoundEvents.SPIDER_AMBIENT, 0.7f, 1.5f);
			ctx.triggerCooldown();
		}));

		// Wall Leap is now the ability form of the same push-off the jump key performs while adhered,
		// so it launches along the real surface normal instead of simply backwards from the camera --
		// which used to send you into the wall whenever you were looking away from it.
		AbilityHandlers.register(KEY, "wall_leap", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (SpiderClimbActions.leap(p)) {
				AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 10, 0.3);
				ctx.triggerCooldown();
				return;
			}
			if (!p.onGround()) {
				Vec3 away = p.getLookAngle().reverse().scale(0.8).add(0, 0.9, 0);
				AbilityHelpers.launchSelf(p, away);
				AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 10, 0.3);
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "predator_rush", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 700, 1, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 700, 1, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.JUMP, 700, 2, false, true, true));
			ctx.setResource("rush_until", p.level().getGameTime() + 700, 1e12f);
			AbilityHelpers.sound(p, SoundEvents.SPIDER_AMBIENT, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		// Both toggles simply switch adhesion on; SpiderClimb does the rest. The tick is only feedback
		// plus the fall-distance reset, which has to happen on the server whichever side is moving.
		AbilityHandlers.register(KEY, "wall_grip", Handlers.toggle(
				ctx -> setGripping(ctx, true), ctx -> setGripping(ctx, false), SpiderClimbingHandlers::clingTick));

		AbilityHandlers.register(KEY, "adhesion_mode", Handlers.toggle(Handlers.noop(), Handlers.noop(),
				SpiderClimbingHandlers::clingTick));
	}

	private static void setGripping(AbilityContext ctx, boolean on) {
		ctx.setResource("gripping", on ? 1 : 0, 1);
	}

	private static void clingTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!SpiderClimb.attached(p)) {
			return;
		}
		p.resetFallDistance();
		if (p.tickCount % 8 == 0) {
			ctx.level().sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 1, 0.2, 0.4, 0.2, 0.0);
		}
	}
}
