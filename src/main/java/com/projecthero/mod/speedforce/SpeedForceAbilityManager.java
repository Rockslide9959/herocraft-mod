package com.projecthero.mod.speedforce;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.p04.SuperSpeedMoves;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the Speed Force Speedster (v0.17.1). {@link com.projecthero.mod.hero.AbilityRouter}
 * hands them the slots when {@link #hasContext} is true -- has the power and no mutation selected. Shift is read server-side.
 *
 * <p>v0.17.1 (user, approved): every key is TAP / SHIFT / HOLD. The tap and Shift moves are the plain Speedster's (borrowed);
 * the hold moves are the Speed Force's own. A tap fires on the RELEASE (as the mutations' {@code HoldTap}); a key still held
 * {@link SpeedForceConfig#HOLD_TICKS} after it went down starts its hold move, and letting go finishes it. A Shift move fires
 * on the press.
 *
 * <pre>
 *   R (1)  Rapid Assault       Shift+R  Mach Punch          hold R  Infinite Mass Punch (wind up, let go: it locks on)
 *   G (2)  Blitz               Shift+G  Speed Vortex        hold G  Lightning Throw (charge, let go: throw)
 *   X (3)  Momentum Dash       Shift+X  Speed Sweep         hold X  Tornado Arms
 *   Z (4)  --                  Shift+Z  Time Remnant        hold Z  below 100% momentum: Flash Time (from the press);
 *                                                                   pressed AT 100%: the Speed Force portal ONLY (5 s held)
 *   V (5)  Afterimage Decoys   Shift+V  Speed Heal          hold V  Rewind
 *   C (6)  Speed Mode on / off Shift+C  Phase (held)        hold C  the 3 s charge, let go to launch at full speed
 *   N (8)  Rescue Run (Shift+N is the Speedster menu, client-side)
 * </pre>
 * H does nothing here.
 */
public final class SpeedForceAbilityManager {
	/** A key that is down: whether Shift was, the tick count it went down at, and what has happened since. */
	private static final class Key {
		final boolean shift;
		final int downAt;
		/** The hold move has started (the release finishes it). */
		boolean held;
		/** The press already did everything (a Shift move, Z, N, ending a sweep): the release only ends what is held. */
		boolean done;

		Key(boolean shift, int downAt) {
			this.shift = shift;
			this.downAt = downAt;
		}
	}

	/** player -> slot ordinal -> the key while it is down. The OS key repeat (more "pressed" packets) is ignored. */
	private static final Map<UUID, Map<Integer, Key>> DOWN = new ConcurrentHashMap<>();

	private SpeedForceAbilityManager() {
	}

	static void clearSessionState() {
		DOWN.clear();
	}

	static void forget(UUID id) {
		DOWN.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!SpeedForceSpeedster.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/** Whether {@code slot}'s key is down for {@code player} right now (the HUD's hold fill reads the client side). */
	public static boolean isDown(ServerPlayer player, AbilitySlot slot) {
		Map<Integer, Key> mine = DOWN.get(player.getUUID());
		return mine != null && mine.containsKey(slot.ordinal());
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		Map<Integer, Key> mine = DOWN.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
		int i = slot.ordinal();
		if (pressed) {
			if (mine.containsKey(i)) {
				return; // the key repeat
			}
			boolean shift = player.isShiftKeyDown();
			Key k = new Key(shift, player.tickCount);
			mine.put(i, k);
			// while phasing every key is swallowed (letting go of C ends it)
			if (SpeedForceSpeedster.phasing(player)) {
				k.done = true;
				return;
			}
			if (shift) {
				k.done = true;
				shiftMove(player, slot);
				return;
			}
			switch (slot) {
				case SLOT_4 -> {
					k.done = true;
					SpeedForceAbilities.pressZ(player); // Z is all hold: at 100% the portal charge, else Flash Time, from the press
				}
				case SLOT_8 -> {
					k.done = true;
					SpeedForceAbilities.rescueRun(player);
				}
				case SLOT_3 -> {
					if (SuperSpeedMoves.sweeping(player)) {
						k.done = true;
						SuperSpeedMoves.endSweep(player); // a fresh X press calls a running sweep off
					}
				}
				default -> {
				}
			}
			return;
		}
		Key k = mine.remove(i);
		if (k == null) {
			// a release with no press seen (a relog, a screen): end whatever such a key could be holding
			endHolds(player, slot);
			return;
		}
		if (slot == AbilitySlot.SLOT_6 && SpeedForceSpeedster.phasing(player)) {
			SpeedForceAbilities.endPhase(player);
			return;
		}
		if (slot == AbilitySlot.SLOT_4) {
			SpeedForceAbilities.endFlashTime(player);
			return;
		}
		if (k.done) {
			return;
		}
		if (k.held) {
			releaseHold(player, slot);
		} else {
			tap(player, slot);
		}
	}

	private static void shiftMove(ServerPlayer player, AbilitySlot slot) {
		switch (slot) {
			case SLOT_1 -> SpeedForceAbilities.machPunch(player);
			case SLOT_2 -> SpeedForceAbilities.speedTornado(player);
			case SLOT_3 -> SpeedForceAbilities.speedSweep(player);
			case SLOT_4 -> SpeedForceAbilities.timeRemnant(player);
			case SLOT_5 -> SpeedForceAbilities.speedHeal(player);
			case SLOT_6 -> SpeedForceAbilities.startPhase(player);
			default -> {
			}
		}
	}

	private static void tap(ServerPlayer player, AbilitySlot slot) {
		switch (slot) {
			case SLOT_1 -> SpeedForceAbilities.rapidAssault(player);
			case SLOT_2 -> SpeedForceAbilities.blitz(player);
			case SLOT_3 -> SpeedForceAbilities.momentumDash(player);
			case SLOT_5 -> SpeedForceAbilities.decoys(player);
			case SLOT_6 -> SpeedForceAbilities.toggleSpeedMode(player);
			default -> {
			}
		}
	}

	private static void startHold(ServerPlayer player, AbilitySlot slot) {
		switch (slot) {
			case SLOT_1 -> SpeedForceAbilities.startMassWindup(player);
			case SLOT_2 -> SpeedForceAbilities.startThrowCharge(player);
			case SLOT_3 -> SpeedForceAbilities.startTornadoArms(player);
			case SLOT_5 -> SpeedForceAbilities.rewind(player);
			case SLOT_6 -> SpeedForceAbilities.startCharge(player);
			default -> {
			}
		}
	}

	private static void releaseHold(ServerPlayer player, AbilitySlot slot) {
		switch (slot) {
			case SLOT_1 -> SpeedForceAbilities.releaseMass(player);
			case SLOT_2 -> SpeedForceAbilities.releaseThrow(player);
			case SLOT_3 -> SpeedForceAbilities.endTornadoArms(player);
			case SLOT_6 -> SpeedForceAbilities.releaseCharge(player);
			default -> {
			}
		}
	}

	/** A stray release: finish anything the key could be holding (never fires a tap). */
	private static void endHolds(ServerPlayer player, AbilitySlot slot) {
		switch (slot) {
			case SLOT_4 -> SpeedForceAbilities.endFlashTime(player);
			case SLOT_6 -> {
				SpeedForceAbilities.endPhase(player);
				SpeedForceAbilities.releaseCharge(player);
			}
			default -> releaseHold(player, slot);
		}
	}

	/** Every tick: a key held {@link SpeedForceConfig#HOLD_TICKS} (on the runner's own clock) starts its hold move. */
	static void tick(ServerPlayer player, long now) {
		Map<Integer, Key> mine = DOWN.get(player.getUUID());
		if (mine == null || mine.isEmpty()) {
			return;
		}
		for (Map.Entry<Integer, Key> e : mine.entrySet()) {
			Key k = e.getValue();
			if (k.done || k.held || k.shift) {
				continue;
			}
			int age = player.tickCount - k.downAt;
			if (age < 0) {
				k.done = true; // the tick count reset (a respawn): drop it
				continue;
			}
			if (age >= SpeedForceConfig.HOLD_TICKS) {
				k.held = true;
				startHold(player, AbilitySlot.values()[e.getKey()]);
			}
		}
	}

	/** The key's three moves, {tap, Shift, hold} (Z: Flash Time on the press and the hold, its portal as the third). */
	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { SpeedForceAbilities.RAPID, SpeedForceAbilities.MACH, SpeedForceAbilities.MASS };
			case SLOT_2 -> new String[] { SpeedForceAbilities.BLITZ, SpeedForceAbilities.TORNADO, SpeedForceAbilities.THROW };
			case SLOT_3 -> new String[] { SpeedForceAbilities.DASH, SpeedForceAbilities.SWEEP, SpeedForceAbilities.ARMS };
			case SLOT_4 -> new String[] { SpeedForceAbilities.FLASH, SpeedForceAbilities.REMNANT, SpeedForceAbilities.PORTAL };
			case SLOT_5 -> new String[] { SpeedForceAbilities.DECOYS, SpeedForceAbilities.HEAL, SpeedForceAbilities.REWIND };
			case SLOT_6 -> new String[] { SpeedForceAbilities.SPEED_MODE, SpeedForceAbilities.PHASE, SpeedForceAbilities.LAUNCH };
			case SLOT_8 -> new String[] { SpeedForceAbilities.RESCUE };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case SpeedForceAbilities.RAPID -> SpeedForceConfig.RAPID_COOLDOWN;
			case SpeedForceAbilities.MACH -> SpeedForceConfig.MACH_COOLDOWN;
			case SpeedForceAbilities.MASS -> SpeedForceConfig.MASS_COOLDOWN;
			case SpeedForceAbilities.BLITZ -> SpeedForceConfig.BLITZ_COOLDOWN;
			case SpeedForceAbilities.TORNADO -> SpeedForceConfig.TORNADO_COOLDOWN;
			case SpeedForceAbilities.THROW -> SpeedForceConfig.THROW_COOLDOWN;
			case SpeedForceAbilities.DASH -> SpeedForceConfig.DASH_COOLDOWN;
			case SpeedForceAbilities.SWEEP -> SpeedForceConfig.SWEEP_COOLDOWN;
			case SpeedForceAbilities.ARMS -> SpeedForceConfig.ARMS_COOLDOWN;
			case SpeedForceAbilities.FLASH -> SpeedForceConfig.FLASH_TIME_COOLDOWN;
			case SpeedForceAbilities.REMNANT -> SpeedForceConfig.REMNANT_COOLDOWN;
			case SpeedForceAbilities.PORTAL -> SpeedForceConfig.PORTAL_COOLDOWN;
			case SpeedForceAbilities.DECOYS -> SpeedForceConfig.DECOY_COOLDOWN;
			case SpeedForceAbilities.HEAL -> SpeedForceConfig.HEAL_COOLDOWN;
			case SpeedForceAbilities.REWIND -> SpeedForceConfig.REWIND_COOLDOWN;
			case SpeedForceAbilities.PHASE -> SpeedForceConfig.PHASE_COOLDOWN;
			case SpeedForceAbilities.LAUNCH -> SpeedForceConfig.LAUNCH_COOLDOWN;
			case SpeedForceAbilities.RESCUE -> SpeedForceConfig.RESCUE_COOLDOWN;
			default -> 0;
		};
	}
}
