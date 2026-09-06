package com.projecthero.mod.hero.power;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;

import net.minecraft.server.level.ServerPlayer;

/**
 * A "stance meter" shared by every mode toggle that the player is supposed to be able to hold only so
 * long: crystal / earth armor strain, the charged-mode energy reserve, the tailwind wind bank, the
 * repulsion-field bank, and so on. It drains while the mode is on and recharges while it is off,
 * exactly like {@link HeroFlight} stamina — force the toggle off when it empties, and refuse to
 * re-enter until it has recovered a little.
 *
 * <p>The meter seeds itself full on first use so a brand-new mutation starts with a full bar.
 */
public final class ModeMeter {
	private ModeMeter() {
	}

	/** Seed the meter to full the very first time it is referenced (never on a later empty re-toggle). */
	public static void ensureSeeded(AbilityContext ctx, String name, float max) {
		if (!ExperimentalPowers.state(ctx.player()).resources.containsKey(ctx.power().key() + "/" + name)) {
			ctx.setResource(name, max, max);
		}
	}

	public static boolean hasCharge(AbilityContext ctx, String name, float min) {
		return ctx.resource(name) >= min;
	}

	/**
	 * Drain one tick of the meter.
	 *
	 * @return {@code true} while charge remains, {@code false} the tick it hits zero — the caller
	 *         should then turn the mode off and show its "ran out" message.
	 */
	public static boolean drain(AbilityContext ctx, String name, float max, float perTick) {
		float v = ctx.resource(name);
		if (v <= 0.0f) {
			return false;
		}
		ctx.setResource(name, Math.max(0.0f, v - perTick), max);
		return ctx.resource(name) > 0.0f;
	}

	/**
	 * Inverse of {@link #regen}: a <em>build-up</em> gauge that climbs while its mode is on (the
	 * handler adds to it each channel tick) and bleeds back toward zero here while the mode is off.
	 * Used by the overheat-style channels — Flamethrower heat, Freeze Beam cold — where a <em>full</em>
	 * bar is the fail state ("cut out, cool down") rather than an empty one.
	 *
	 * <p>Unlike {@link #regen} it never seeds: an untouched build-up gauge is correctly zero already.
	 */
	public static void cool(ServerPlayer player, Power power, String name, float max, float perTick, boolean modeActive) {
		if (power == null || modeActive) {
			return;
		}
		float v = ExperimentalPowers.getResource(player, power, name);
		if (v > 0.0f) {
			ExperimentalPowers.addResource(player, power, name, -perTick, max);
		}
	}

	/** Call every server tick from the power's {@code PowerPassives.registerTick}; regenerates only while the mode is off. */
	public static void regen(ServerPlayer player, Power power, String name, float max, float perTick, boolean modeActive) {
		if (power == null) {
			return;
		}
		// An untouched meter is treated as full, not empty -- a fresh mutation starts with a full bar.
		if (!ExperimentalPowers.state(player).resources.containsKey(power.key() + "/" + name)) {
			ExperimentalPowers.setResource(player, power, name, max, max);
			return;
		}
		if (modeActive) {
			return;
		}
		float v = ExperimentalPowers.getResource(player, power, name);
		if (v < max) {
			ExperimentalPowers.addResource(player, power, name, perTick, max);
		}
	}
}
