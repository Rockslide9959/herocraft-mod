package com.herocraft.mod.hero;

/**
 * The runtime behaviour of one ability. Registered per {@code (power, ability)} in
 * {@link AbilityHandlers}; powers get their handlers added batch by batch, so an ability with no
 * handler yet simply reports "not implemented" through {@link AbilityRouter} without breaking
 * anything.
 *
 * <p>Which methods fire depends on the ability's {@link AbilityActivation}:
 * <ul>
 *   <li>{@code INSTANT} / {@code CHARGE} -- {@link #onActivate} on key-down (already cooldown-checked
 *       by the router for INSTANT); {@link #onRelease} on key-up.</li>
 *   <li>{@code HOLD} -- {@link #onActivate} on key-down, {@link #onRelease} on key-up. No cooldown
 *       gate; drain a resource in {@link #onActivate}/a tick hook instead.</li>
 *   <li>{@code TOGGLE} -- {@link #onToggleOn}/{@link #onToggleOff} as the state flips, plus
 *       {@link #onToggleTick} every server tick while on.</li>
 *   <li>{@code CYCLE} -- {@link #onCycle} on key-down after the mode index has advanced.</li>
 * </ul>
 */
public interface AbilityHandler {

	default void onActivate(AbilityContext ctx) {
	}

	default void onRelease(AbilityContext ctx) {
	}

	default void onToggleOn(AbilityContext ctx) {
	}

	default void onToggleOff(AbilityContext ctx) {
	}

	default void onToggleTick(AbilityContext ctx) {
	}

	default void onCycle(AbilityContext ctx) {
	}

	/**
	 * Runs every server tick for every ability of the player's active power (not just toggles). Use
	 * for grab/charge/marker upkeep. Must be cheap and idempotent. {@code ctx.pressed()} is always
	 * true here.
	 */
	default void onServerTick(AbilityContext ctx) {
	}

	// ---- power-wide passive lifecycle (delegates to PowerPassives) ----

	static void activatePassives(net.minecraft.server.level.ServerPlayer player, Power power) {
		PowerPassives.activate(player, power);
	}

	static void deactivatePassives(net.minecraft.server.level.ServerPlayer player, Power power) {
		PowerPassives.deactivate(player, power);
	}

	static void tickPassives(net.minecraft.server.level.ServerPlayer player, Power power) {
		PowerPassives.tick(player, power);
	}
}
