package com.projecthero.mod.hero;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.6 (user: "give all the elemental powers extra moves that are triggered by holding down the ability key"): tap vs
 * hold on a mutation key. A key whose ability is {@link #register registered} here no longer fires on key-down:
 * <ul>
 *   <li>released before {@link #HOLD_TICKS} -- a TAP: the ability's normal path runs (INSTANT with its cooldown check,
 *       TOGGLE flips, ...), exactly as before, just on the release;</li>
 *   <li>still held at {@link #HOLD_TICKS} -- the HOLD move: {@link AbilityHandler#onHoldMove} fires once (the handler does
 *       its own cost / cooldown checks); the release that follows does nothing.</li>
 * </ul>
 * Sneak held at key-down = the Shift move, untouched: it fires on key-down as before (Shift+hold does nothing new).
 *
 * <p>The registry is static and filled from common init, so the client can ask {@link #hasHold} too (the HUD fills the
 * key's box while it is held and lists the hold move). Lang: the hold move's name is
 * {@code projecthero.power.<power>.ability.<ability>.hold} and its description {@code ....hold.desc}.
 */
public final class HoldTap {
	/** How long a key must be held for the hold move (0.6 s). */
	public static final int HOLD_TICKS = 12;

	private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

	/** A key that went down and has not been resolved yet. */
	private record Pending(String power, String ability, long downAt) {
	}

	/** player -> slot ordinal -> pending press. */
	private static final Map<UUID, Map<Integer, Pending>> PENDING = new ConcurrentHashMap<>();

	private HoldTap() {
	}

	/** Gives {@code (powerKey, abilityId)} a hold move (call from the power's common init). */
	public static void register(String powerKey, String abilityId) {
		REGISTERED.add(powerKey + ":" + abilityId);
	}

	/** Client-safe: whether this key has a hold move. */
	public static boolean hasHold(String powerKey, String abilityId) {
		return REGISTERED.contains(powerKey + ":" + abilityId);
	}

	public static void clearSessionState() {
		PENDING.clear();
	}

	public static void forget(UUID id) {
		PENDING.remove(id);
	}

	/**
	 * From {@link AbilityRouter#dispatchExperimental}: returns true if the edge was taken over here (the caller then does
	 * nothing); {@code tap} runs the ability's normal key-down path.
	 */
	static boolean onEdge(ServerPlayer player, Power power, Ability ability, AbilitySlot slot, boolean pressed, Runnable tap) {
		if (!hasHold(power.key(), ability.id())) {
			return false;
		}
		Map<Integer, Pending> mine = PENDING.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
		if (pressed) {
			if (player.isShiftKeyDown()) {
				mine.remove(slot.ordinal());
				return false; // the Shift move, as before
			}
			mine.put(slot.ordinal(), new Pending(power.key(), ability.id(), player.level().getGameTime()));
			return true;
		}
		Pending p = mine.remove(slot.ordinal());
		if (p == null) {
			// a release after the hold move already fired, or of a Shift press: a HOLD-type Shift move still needs its key-up
			return false;
		}
		if (p.power().equals(power.key()) && p.ability().equals(ability.id())) {
			tap.run();
		}
		return true;
	}

	/** Every server tick (from AbilityRouter.serverTick): fires the hold move of a key held long enough. */
	static void tick(ServerPlayer player) {
		Map<Integer, Pending> mine = PENDING.get(player.getUUID());
		if (mine == null || mine.isEmpty()) {
			return;
		}
		Power active = ExperimentalPowers.getActive(player);
		long now = player.level().getGameTime();
		for (Map.Entry<Integer, Pending> e : mine.entrySet()) {
			Pending p = e.getValue();
			if (active == null || !active.key().equals(p.power())) {
				mine.remove(e.getKey());
				continue;
			}
			if (now - p.downAt() < HOLD_TICKS) {
				continue;
			}
			mine.remove(e.getKey());
			Ability ability = active.ability(AbilitySlot.values()[e.getKey()]);
			AbilityHandler handler = ability == null ? null : AbilityHandlers.get(active, ability);
			if (handler != null && ability.id().equals(p.ability())) {
				handler.onHoldMove(new AbilityContext(player, active, ability, true));
			}
		}
	}
}
