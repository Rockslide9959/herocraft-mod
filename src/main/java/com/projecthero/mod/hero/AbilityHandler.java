package com.projecthero.mod.hero;

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
	 * v0.16.6: the key's HOLD move (only for abilities registered with {@link HoldTap#register}): fires once when the key
	 * has been held {@link HoldTap#HOLD_TICKS}. Do the cost / cooldown checks here (the router does none for it).
	 */
	default void onHoldMove(AbilityContext ctx) {
	}

	/**
	 * v0.17.5: true when this key's Shift move keeps its own cooldown (and does its own check), so the router must not gate
	 * a Shift press of an INSTANT key on the plain move's cooldown -- the plain move's wait never blocks the Shift move.
	 */
	default boolean shiftHasOwnCooldown() {
		return false;
	}

	/**
	 * v0.17.5: true when this press must not be gated on the plain move's cooldown at all (e.g. throwing something a Shift move
	 * is holding -- that ends the Shift move, it isn't a new plain move). The handler does its own checks.
	 */
	default boolean bypassesRouterCooldown(AbilityContext ctx) {
		return false;
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
