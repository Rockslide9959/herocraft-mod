package com.projecthero.mod.blackpanther;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to Black Panther (v0.17.3). {@link com.projecthero.mod.hero.AbilityRouter} hands him
 * the slots when {@link #hasContext} is true. Shift is read server-side.
 *
 * <pre>
 *   R (1)  Kinetic Pulse       Shift+R  Kinetic Overload
 *   G (2)  Claw Combo          Shift+G  Pounce
 *   X (3)  Panther Leap        Shift+X  Drop Strike
 *   Z (4)  Vibranium Dagger    Shift+Z  Kimoyo Flash
 *   V (5)  Stalk (toggle)      Shift+V  Panther Senses
 *   C (6)  Absorb Stance (hold) Shift+C Kinetic Return
 *   N (8)  Claws (toggle)      Shift+N  Mask (toggle)       -- no HUD box; listed in the Left-Alt move list
 * </pre>
 */
public final class BlackPantherAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private BlackPantherAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!BlackPanther.hasPower(player)) {
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
		if (!freshPress(player, slot, pressed)) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.overload(player);
					} else {
						BlackPantherAbilities.pulse(player);
					}
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.pounce(player);
					} else {
						BlackPantherAbilities.claws(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.dropStrike(player);
					} else {
						BlackPantherAbilities.leap(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.kimoyo(player);
					} else {
						BlackPantherAbilities.dagger(player);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.senses(player);
					} else {
						BlackPantherAbilities.toggleStalk(player);
					}
				}
			}
			case SLOT_6 -> {
				if (!pressed) {
					BlackPantherAbilities.lowerStance(player);
				} else if (shift) {
					BlackPantherAbilities.kineticReturn(player);
				} else {
					BlackPantherAbilities.raiseStance(player);
				}
			}
			case SLOT_8 -> { // N (AbilityRouter's utility branch hands it here): the claws; Shift+N the mask
				if (pressed) {
					if (shift) {
						BlackPanther.toggleMask(player);
					} else {
						BlackPanther.toggleClaws(player);
					}
				}
			}
			default -> {
			}
		}
	}

	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { BlackPantherAbilities.PULSE, BlackPantherAbilities.OVERLOAD };
			case SLOT_2 -> new String[] { BlackPantherAbilities.CLAWS, BlackPantherAbilities.POUNCE };
			case SLOT_3 -> new String[] { BlackPantherAbilities.LEAP, BlackPantherAbilities.DROP };
			case SLOT_4 -> new String[] { BlackPantherAbilities.DAGGER, BlackPantherAbilities.KIMOYO };
			case SLOT_5 -> new String[] { BlackPantherAbilities.STALK, BlackPantherAbilities.SENSES };
			case SLOT_6 -> new String[] { BlackPantherAbilities.ABSORB, BlackPantherAbilities.RETURN };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case BlackPantherAbilities.PULSE -> BlackPantherConfig.PULSE_COOLDOWN;
			case BlackPantherAbilities.OVERLOAD -> BlackPantherConfig.OVERLOAD_COOLDOWN;
			case BlackPantherAbilities.CLAWS -> BlackPantherConfig.CLAW_COOLDOWN;
			case BlackPantherAbilities.POUNCE -> BlackPantherConfig.POUNCE_COOLDOWN;
			case BlackPantherAbilities.LEAP -> BlackPantherConfig.LEAP_COOLDOWN;
			case BlackPantherAbilities.DROP -> BlackPantherConfig.DROP_COOLDOWN;
			case BlackPantherAbilities.DAGGER -> BlackPantherConfig.DAGGER_COOLDOWN;
			case BlackPantherAbilities.KIMOYO -> BlackPantherConfig.FLASH_COOLDOWN;
			case BlackPantherAbilities.STALK -> BlackPantherConfig.STALK_COOLDOWN;
			case BlackPantherAbilities.SENSES -> BlackPantherConfig.SENSES_COOLDOWN;
			case BlackPantherAbilities.ABSORB -> BlackPantherConfig.ABSORB_COOLDOWN;
			case BlackPantherAbilities.RETURN -> BlackPantherConfig.RETURN_COOLDOWN;
			default -> 0;
		};
	}
}
