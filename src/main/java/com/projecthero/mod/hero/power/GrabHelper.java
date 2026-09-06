package com.projecthero.mod.hero.power;

import com.projecthero.mod.hero.AbilityContext;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shared grab / hold / throw logic used by Super Strength, Telekinesis, Elasticity, etc. State is
 * kept in the ability's own resource slots ({@code grabbed}, {@code grab_ticks}) so it is naturally
 * per-power and per-ability, cleared on power switch, and self-heals if the held entity vanishes.
 */
public final class GrabHelper {
	private GrabHelper() {
	}

	public static boolean isHolding(AbilityContext ctx) {
		return ctx.resource("grabbed") > 0.5f;
	}

	public static LivingEntity held(AbilityContext ctx) {
		int id = (int) ctx.resource("grabbed");
		return id != 0 && ctx.level().getEntity(id) instanceof LivingEntity le ? le : null;
	}

	/** Try to grab what the player is aiming at. Returns true on a successful grab. */
	public static boolean tryGrab(AbilityContext ctx, double range, int holdTicks) {
		ServerPlayer p = ctx.player();
		LivingEntity target = AbilityHelpers.raycastEntity(p, range);
		if (target == null || !AbilityHelpers.isValidGrabTarget(target, p)) {
			return false;
		}
		ctx.setResource("grabbed", target.getId(), 1_000_000);
		ctx.setResource("grab_ticks", holdTicks, 1_000_000);
		return true;
	}

	public static void clear(AbilityContext ctx) {
		ctx.setResource("grabbed", 0, 1_000_000);
		ctx.setResource("grab_ticks", 0, 1_000_000);
	}

	/** Throw the held entity in the aim direction. Returns true if something was thrown. */
	public static boolean throwHeld(AbilityContext ctx, double speed, float damage) {
		LivingEntity le = held(ctx);
		clear(ctx);
		if (le == null) {
			return false;
		}
		le.setDeltaMovement(ctx.player().getLookAngle().scale(speed).add(0, 0.3, 0));
		le.hurtMarked = true;
		le.hasImpulse = true;
		if (damage > 0) {
			AbilityHelpers.hurt(ctx.player(), le, damage);
		}
		return true;
	}

	/** Slam the held entity straight down. */
	public static boolean slamHeld(AbilityContext ctx, float damage) {
		LivingEntity le = held(ctx);
		clear(ctx);
		if (le == null) {
			return false;
		}
		le.setDeltaMovement(0, -2.0, 0);
		le.hurtMarked = true;
		if (damage > 0) {
			AbilityHelpers.hurt(ctx.player(), le, damage);
		}
		return true;
	}

	/** Per-tick upkeep: hold the entity in front of the player, expire on timeout / distance / death. */
	public static void tick(AbilityContext ctx, double holdDistance) {
		if (!isHolding(ctx)) {
			return;
		}
		ServerPlayer p = ctx.player();
		Entity e = ctx.level().getEntity((int) ctx.resource("grabbed"));
		int ticks = (int) ctx.resource("grab_ticks") - 1;
		if (!(e instanceof LivingEntity le) || !le.isAlive() || ticks <= 0 || p.distanceToSqr(e) > 100) {
			clear(ctx);
			return;
		}
		ctx.setResource("grab_ticks", ticks, 1_000_000);
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(holdDistance));
		le.setPos(hold.x, hold.y - le.getBbHeight() / 2, hold.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.fallDistance = 0;
		le.hurtMarked = true;
	}
}
