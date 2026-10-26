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
 *   V (5)  Rescue Run                 Shift+V  Speed Heal
 *   C (6)  Speed Force Surge (mode)   Shift+C  Rewind
 * </pre>
 * H and N do nothing here (Shift+N is kept free for both speedsters).
 */
public final class SpeedForceAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private SpeedForceAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
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
					SpeedForceAbilities.speedHeal(player);
				} else {
					SpeedForceAbilities.rescueRun(player);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					SpeedForceAbilities.rewind(player);
				} else {
					SpeedForceAbilities.surge(player);
				}
			}
			default -> {
			}
		}
	}

	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { SpeedForceAbilities.PUNCHES, SpeedForceAbilities.MASS };
			case SLOT_2 -> new String[] { SpeedForceAbilities.THROW, SpeedForceAbilities.TORNADO };
			case SLOT_3 -> new String[] { SpeedForceAbilities.STEP, SpeedForceAbilities.PHASE };
			case SLOT_4 -> new String[] { SpeedForceAbilities.FLASH, SpeedForceAbilities.REMNANT };
			case SLOT_5 -> new String[] { SpeedForceAbilities.RESCUE, SpeedForceAbilities.HEAL };
			case SLOT_6 -> new String[] { SpeedForceAbilities.SURGE, SpeedForceAbilities.REWIND };
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
			case SpeedForceAbilities.HEAL -> SpeedForceConfig.HEAL_COOLDOWN;
			case SpeedForceAbilities.SURGE -> SpeedForceConfig.SURGE_COOLDOWN;
			case SpeedForceAbilities.REWIND -> SpeedForceConfig.REWIND_COOLDOWN;
			default -> 0;
		};
	}
}
