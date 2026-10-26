package com.projecthero.mod.speedforce;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the Speed Force Speedster (v0.17.1). {@link com.projecthero.mod.hero.AbilityRouter}
 * hands them the slots when {@link #hasContext} is true -- has the power and no mutation selected. Shift is read server-side.
 *
 * <pre>
 *   R (1)  Speed Punches              Shift+R  Infinite Mass Punch
 *   G (2)  Lightning Throw            Shift+G  Speed Tornado
 *   X (3)  Speed Force Step           Shift+X  Phase (hold)
 *   Z (4)  Flash Time                 Shift+Z  Time Remnant
 *   V (5)  Speed Force Barrier (tap)  hold V  Speed Heal     Shift+V  Rewind (v0.17.1, user: from Shift+C)
 *   N (8)  Rescue Run (v0.17.1, user: moved from V)
 *   C (6)  Speed Mode on / off        hold Shift+C  the 3 s charge, let go to launch at full speed (v0.17.1, user)
 * </pre>
 * H does nothing here; Shift+N is Ground-Stick (client-side, both speedsters).
 */
public final class SpeedForceAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();
	/** V held (no Shift): the game time it went down; Long.MIN_VALUE once the hold has fired Speed Heal. */
	private static final Map<UUID, Long> V_DOWN = new ConcurrentHashMap<>();

	private SpeedForceAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
		V_DOWN.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
		V_DOWN.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!SpeedForceSpeedster.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	private static boolean freshPress(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		long[] last = LAST_PRESS.computeIfAbsent(player.getUUID(), k -> {
			long[] a = new long[AbilitySlot.values().length];
			java.util.Arrays.fill(a, -1L);
			return a;
		});
		int i = slot.ordinal();
		if (!pressed) {
			last[i] = -1L;
			return true;
		}
		long now = player.level().getGameTime();
		boolean repeat = last[i] >= 0L && now - last[i] <= REPEAT_WINDOW && now >= last[i];
		last[i] = now;
		return !repeat;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		// R refires as fast as its cooldown allows
		if (slot != AbilitySlot.SLOT_1 && !freshPress(player, slot, pressed)) {
			return;
		}
		// while phasing every key is swallowed; letting go of X ends it
		if (SpeedForceSpeedster.phasing(player)) {
			if (slot == AbilitySlot.SLOT_3 && !pressed) {
				SpeedForceAbilities.endPhase(player);
			}
			return;
		}
		if (!pressed) {
			// v0.17.1 (user): releases matter for V (tap = Barrier) and Shift+C (let go = launch)
			if (slot == AbilitySlot.SLOT_5) {
				Long down = V_DOWN.remove(player.getUUID());
				if (down != null && down != Long.MIN_VALUE) {
					SpeedForceAbilities.speedForceBarrier(player);
				}
			} else if (slot == AbilitySlot.SLOT_6) {
				SpeedForceAbilities.releaseCharge(player);
			}
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					SpeedForceAbilities.infiniteMassPunch(player);
				} else {
					SpeedForceAbilities.speedPunches(player);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					SpeedForceAbilities.speedTornado(player);
				} else {
					SpeedForceAbilities.lightningThrow(player);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					SpeedForceAbilities.startPhase(player);
				} else {
					SpeedForceAbilities.speedForceStep(player);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					SpeedForceAbilities.timeRemnant(player);
				} else {
					SpeedForceAbilities.flashTime(player);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					SpeedForceAbilities.rewind(player);
				} else {
					V_DOWN.put(player.getUUID(), player.level().getGameTime()); // tap or hold: decided on release / in tick
				}
			}
			case SLOT_8 -> {
				if (!shift) {
					SpeedForceAbilities.rescueRun(player);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					SpeedForceAbilities.startCharge(player);
				} else {
					SpeedForceAbilities.toggleSpeedMode(player);
				}
			}
			default -> {
			}
		}
	}

	/** Hold V past {@link SpeedForceConfig#HEAL_HOLD_TICKS}: Speed Heal fires (and the release no longer raises the Barrier). */
	static void tick(ServerPlayer player, long now) {
		Long down = V_DOWN.get(player.getUUID());
		if (down != null && down != Long.MIN_VALUE && now - down >= SpeedForceConfig.HEAL_HOLD_TICKS) {
			V_DOWN.put(player.getUUID(), Long.MIN_VALUE);
			SpeedForceAbilities.speedHeal(player);
		}
	}

	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { SpeedForceAbilities.PUNCHES, SpeedForceAbilities.MASS };
			case SLOT_2 -> new String[] { SpeedForceAbilities.THROW, SpeedForceAbilities.TORNADO };
			case SLOT_3 -> new String[] { SpeedForceAbilities.STEP, SpeedForceAbilities.PHASE };
			case SLOT_4 -> new String[] { SpeedForceAbilities.FLASH, SpeedForceAbilities.REMNANT };
			case SLOT_5 -> new String[] { SpeedForceAbilities.BARRIER, SpeedForceAbilities.REWIND, SpeedForceAbilities.HEAL };
			case SLOT_8 -> new String[] { SpeedForceAbilities.RESCUE };
			case SLOT_6 -> new String[] { SpeedForceAbilities.SPEED_MODE, SpeedForceAbilities.LAUNCH };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case SpeedForceAbilities.PUNCHES -> SpeedForceConfig.PUNCH_COOLDOWN;
			case SpeedForceAbilities.MASS -> SpeedForceConfig.MASS_COOLDOWN;
			case SpeedForceAbilities.THROW -> SpeedForceConfig.THROW_COOLDOWN;
			case SpeedForceAbilities.TORNADO -> SpeedForceConfig.TORNADO_COOLDOWN;
			case SpeedForceAbilities.STEP -> SpeedForceConfig.STEP_COOLDOWN;
			case SpeedForceAbilities.PHASE -> SpeedForceConfig.PHASE_COOLDOWN;
			case SpeedForceAbilities.FLASH -> SpeedForceConfig.FLASH_TIME_COOLDOWN;
			case SpeedForceAbilities.REMNANT -> SpeedForceConfig.REMNANT_COOLDOWN;
			case SpeedForceAbilities.RESCUE -> SpeedForceConfig.RESCUE_COOLDOWN;
			case SpeedForceAbilities.BARRIER -> SpeedForceConfig.BARRIER_COOLDOWN;
			case SpeedForceAbilities.LAUNCH -> SpeedForceConfig.LAUNCH_COOLDOWN;
			case SpeedForceAbilities.HEAL -> SpeedForceConfig.HEAL_COOLDOWN;
			case SpeedForceAbilities.SURGE -> SpeedForceConfig.SURGE_COOLDOWN;
			case SpeedForceAbilities.REWIND -> SpeedForceConfig.REWIND_COOLDOWN;
			default -> 0;
		};
	}
}
