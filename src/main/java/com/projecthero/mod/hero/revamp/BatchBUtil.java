package com.projecthero.mod.hero.revamp;

import java.util.Locale;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 batch B shared plumbing: change-only resource writes (every {@code setResource} re-syncs the whole
 * experimental state, so per-tick meters only write when the value actually moves), the hold-to-charge ultimate
 * pattern every batch-B Z uses, and retiring resource names an older kit left behind.
 */
public final class BatchBUtil {
	public static final int ULT_CHARGE_COLOR = 0xFFFFC24A;

	private BatchBUtil() {
	}

	// ---------------- resources ----------------

	/** Writes {@code value} only if it differs from the stored value by more than {@code eps}. */
	public static void set(ServerPlayer p, Power power, String name, float value, float max, float eps) {
		float old = ExperimentalPowers.getResource(p, power, name);
		boolean present = ExperimentalPowers.state(p).resources.containsKey(power.key() + "/" + name);
		float clamped = Math.max(0.0f, Math.min(max, value));
		if (!present || Math.abs(old - clamped) > eps || (clamped == 0.0f && old != 0.0f) || (clamped == max && old != max)) {
			ExperimentalPowers.setResource(p, power, name, clamped, max);
		}
	}

	public static float get(ServerPlayer p, Power power, String name) {
		return ExperimentalPowers.getResource(p, power, name);
	}

	/** Seeds a reserve to {@code max} the first time it is referenced. */
	public static void seed(ServerPlayer p, Power power, String name, float max) {
		if (!ExperimentalPowers.state(p).resources.containsKey(power.key() + "/" + name)) {
			ExperimentalPowers.setResource(p, power, name, max, max);
		}
	}

	/** Zeroes resources an older version of the kit used, so no stale bar lingers on the HUD. */
	public static void retire(ServerPlayer p, Power power, String... names) {
		for (String n : names) {
			if (ExperimentalPowers.getResource(p, power, n) != 0.0f) {
				ExperimentalPowers.setResource(p, power, n, 0.0f, 1.0f);
			}
		}
	}

	/** Registers a never-visible meter for a retired resource name (overrides the HUD's legacy name list). */
	public static void hideMeter(String powerKey, String resource) {
		MutationMeters.register(new MutationMeters.Spec(powerKey, resource, MutationMeters.Kind.TIMER,
				MutationMeters.Style.HAIRLINE, "", 1.0e9f, 0, false, false));
	}

	/** The standard "Ultimate -- charging" hairline every hold-to-charge Z shows. */
	public static void ultMeter(String powerKey) {
		MutationMeters.register(new MutationMeters.Spec(powerKey, "ult_charge", MutationMeters.Kind.BUILD,
				MutationMeters.Style.HAIRLINE, "Ultimate — charging", 100.0f, ULT_CHARGE_COLOR, false, false));
	}

	// ---------------- cooldown feedback ----------------

	/** If the ability is cooling down, says so on the action bar and returns true. */
	public static boolean onCooldown(AbilityContext ctx) {
		if (ctx.cooldownReady()) {
			return false;
		}
		ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
				String.format(Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
		return true;
	}

	// ---------------- hold-to-charge ultimates ----------------

	/** Starts a charge stored under {@code key}. False (with feedback) when on cooldown or already charging. */
	public static boolean chargeStart(AbilityContext ctx, String key) {
		if (ctx.resource(key) > 0.5f || onCooldown(ctx)) {
			return false;
		}
		ctx.setResource(key, ctx.player().level().getGameTime(), 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		return true;
	}

	/** Ticks held so far, or -1 when not charging. */
	public static long chargeHeld(AbilityContext ctx, String key) {
		float start = ctx.resource(key);
		if (start <= 0.5f) {
			return -1;
		}
		return ctx.player().level().getGameTime() - (long) start;
	}

	/** Updates the shared ult meter for a charge of {@code full} ticks. */
	public static void chargeMeter(AbilityContext ctx, long held, int full) {
		set(ctx.player(), ctx.power(), "ult_charge", Math.min(100.0f, held * 100.0f / full), 100.0f, 2.0f);
	}

	public static void chargeClear(AbilityContext ctx, String key) {
		ctx.setResource(key, 0, 1e12f);
		ctx.setResource("ult_charge", 0, 100);
	}

	// ---------------- geometry ----------------

	/** The horizontal part of the look vector, normalised (never zero). */
	public static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
	}
}
