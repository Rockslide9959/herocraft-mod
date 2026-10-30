package com.projecthero.mod.omnitrix;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.1: the six ability keys for an Omnitrix wearer ({@code AbilityRouter} hands them over when {@link #hasContext}).
 *
 * <pre>
 *   R (1)        human: raise the watch (the core pops up, the face shows the selected alien) / lower it again;
 *                during the recharge the face flashes red with the time left instead (v0.17.1 user: R, was V).
 *   N (8)        alien: revert early (10 s recharge; v0.17.1 user: "make the detransform keybind N", was V). Shift+N is the
 *                same key. Human: nothing.
 *   (watch up)   scroll wheel = turn the dial, left / right click = slap the core down and transform (client side, sent as
 *                OmnitrixActionPayload CYCLE / SLAP)
 *   R G X Z V C  alien: the kit's moves (tap / Shift / declared holds; v0.17.1 user: "add abilities to the V key for every
 *                alien" -- V is each kit's utility / mode move). Human: nothing.
 *   H            the power selector (never sent here).
 * </pre>
 */
public final class OmnitrixAbilityManager {
	private static final class Key {
		final boolean shift;
		final long downAt;
		boolean held;
		boolean done;

		Key(boolean shift, long downAt) {
			this.shift = shift;
			this.downAt = downAt;
		}
	}

	private static final Map<UUID, Map<Integer, Key>> DOWN = new ConcurrentHashMap<>();

	private OmnitrixAbilityManager() {
	}

	public static boolean hasContext(ServerPlayer p) {
		if (!Omnitrix.hasPower(p)) {
			return false;
		}
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/** v0.17.1 Master Control: when Shift+N went down with the watch up (cleared on release). */
	private static final Map<UUID, Long> MASTER_HOLD = new ConcurrentHashMap<>();

	static void forget(UUID id) {
		DOWN.remove(id);
		MASTER_HOLD.remove(id);
	}

	static void clearSessionState() {
		DOWN.clear();
		MASTER_HOLD.clear();
	}

	/** R as a human: the watch up / down. */
	public static void pressR(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if (s.transformed()) {
			return;
		} else if (s.selecting) {
			Omnitrix.deactivate(p);
		} else {
			Omnitrix.activate(p);
		}
	}

	/** v0.17.1 (user): N as an alien -- turn back early (the power-down beeps, then the short recharge). Human: nothing. */
	public static void pressN(ServerPlayer p) {
		if (Omnitrix.state(p).transformed()) {
			Omnitrix.revertEarly(p);
		}
	}

	public static void handle(ServerPlayer p, AbilitySlot slot, boolean pressed) {
		if (slot == AbilitySlot.SLOT_7) {
			return; // H is the power selector
		}
		if (slot == AbilitySlot.SLOT_8) {
			// v0.17.1 (user): N turns an alien back (Shift+N too); the release has nothing to do
			if (pressed) {
				pressN(p);
				// v0.17.1 Master Control: Shift+N held with the watch up (no bar, no hint: see tick)
				if (p.isShiftKeyDown() && Omnitrix.state(p).selecting && !Omnitrix.state(p).transformed()) {
					MASTER_HOLD.putIfAbsent(p.getUUID(), p.level().getGameTime()); // a key repeat never restarts the hold
				}
			} else {
				MASTER_HOLD.remove(p.getUUID());
			}
			return;
		}
		Map<Integer, Key> mine = DOWN.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
		int i = slot.ordinal();
		long now = p.level().getGameTime();
		OmnitrixState s = Omnitrix.state(p);
		if (pressed) {
			if (mine.containsKey(i)) {
				return; // the OS key repeat
			}
			Key k = new Key(p.isShiftKeyDown(), now);
			mine.put(i, k);
			// v0.17.1 (user: "make the R button activate the omnitrix instead of V"): R raises / lowers the watch as a human; V
			// only turned an alien back (as an alien R is the kit's move again); v0.17.1: N turns back now, V is a kit key
			if (!s.transformed()) {
				k.done = true;
				if (slot == AbilitySlot.SLOT_1) {
					pressR(p);
				}
				return;
			}
			AlienKit kit = Aliens.kit(s.active);
			if (kit == null) {
				k.done = true;
				return;
			}
			if (k.shift || !kit.holdable(slot)) {
				k.done = true;
				kit.press(p, slot, k.shift);
			}
			return;
		}
		Key k = mine.remove(i);
		if (k == null || !s.transformed()) {
			return;
		}
		AlienKit kit = Aliens.kit(s.active);
		if (kit == null) {
			return;
		}
		int held = (int) (now - k.downAt);
		if (!k.done) {
			if (k.held) {
				kit.holdEnd(p, slot, held);
			} else {
				kit.press(p, slot, false); // a tap on a holdable key fires on the release
			}
		}
		kit.release(p, slot, k.shift, held);
	}

	/** A holdable key still down after {@code KIT_HOLD_TICKS} starts its hold move. */
	static void tick(ServerPlayer p) {
		Long held = MASTER_HOLD.get(p.getUUID());
		if (held != null) {
			OmnitrixState ms = Omnitrix.state(p);
			if (!p.isShiftKeyDown() || !ms.selecting || ms.transformed()) {
				MASTER_HOLD.remove(p.getUUID());
			} else if (p.level().getGameTime() - held >= OmnitrixConfig.MASTER_HOLD_TICKS) {
				MASTER_HOLD.remove(p.getUUID());
				Omnitrix.startMasterSequence(p);
			}
		}
		Map<Integer, Key> mine = DOWN.get(p.getUUID());
		if (mine == null || mine.isEmpty()) {
			return;
		}
		AlienKit kit = Aliens.kit(Omnitrix.state(p).active);
		if (kit == null) {
			return;
		}
		long now = p.level().getGameTime();
		for (Map.Entry<Integer, Key> e : mine.entrySet()) {
			Key k = e.getValue();
			AbilitySlot slot = AbilitySlot.values()[e.getKey()];
			if (!k.done && !k.held && kit.holdable(slot) && now - k.downAt >= OmnitrixConfig.KIT_HOLD_TICKS) {
				k.held = true;
				kit.holdStart(p, slot);
			}
		}
	}
}
