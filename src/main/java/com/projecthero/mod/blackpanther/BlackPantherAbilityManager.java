package com.projecthero.mod.blackpanther;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to Black Panther. {@link com.projecthero.mod.hero.AbilityRouter} hands him the slots when
 * {@link #hasContext} is true. Shift is read server-side.
 *
 * <p>Two movesets. With the full crafted suit on (v0.17.7) the keys run the suited kit; out of it, the unsuited one
 * ({@link BlackPantherUnsuited}). The switch is the worn suit itself: nothing to select.
 *
 * <pre>
 *            SUITED                                          UNSUITED (T'Challa)
 *   R (1)  Vibranium Combo   / Shift Kinetic Combo           Warrior's Combo   / Shift Leg Sweep
 *   G (2)  Pounce            / Shift Kinetic Pounce          Takedown          / Shift Ground Pound
 *   X (3)  Panther Leap      / Shift Kinetic Dash            Vault             / Shift Flying Kick
 *   Z (4)  Vibranium Dagger  / Shift Kinetic Overload (*)    Throwing Knife    / Shift Kimoyo Scan
 *   V (5)  Stalk             / Shift Kinetic Shroud          Rising Uppercut   / Shift Hunter's Tracking
 *   C (6)  Absorb Stance (hold) / Shift Kinetic Return       Block (hold)      / Shift Wakanda Forever
 *   N (8)  -- / Shift Mask (toggle, suited; no HUD box, listed in the Left-Alt move list)
 *   (*) the ultimate: only on a full Kinetic bar, which it empties. Shift moves spend Kinetic (refused, nothing spent, when short)
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
		if (!BlackPanther.suitedKit(player)) {
			handleUnsuited(player, slot, pressed, shift);
			return;
		}
		// v0.17.7 suited kit (user-approved): plain keys are free and fill a little Kinetic when they land; Shift keys spend it
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.kineticCombo(player);
					} else {
						BlackPantherAbilities.vibraniumCombo(player);
					}
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.kineticPounce(player);
					} else {
						BlackPantherAbilities.pounce(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.kineticDash(player);
					} else {
						BlackPantherAbilities.leap(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.overload(player); // the ultimate: a full bar only
					} else {
						BlackPantherAbilities.dagger(player);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						BlackPantherAbilities.shroud(player);
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
			case SLOT_8 -> { // N (AbilityRouter's utility branch hands it here): Shift+N the mask (the claws are always out now)
				if (pressed && shift) {
					BlackPanther.toggleMask(player);
				}
			}
			default -> {
			}
		}
	}

	/** T'Challa out of the suit. */
	private static void handleUnsuited(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (shift) {
						BlackPantherUnsuited.sweep(player);
					} else {
						BlackPantherUnsuited.combo(player);
					}
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						BlackPantherUnsuited.groundPound(player);
					} else {
						BlackPantherUnsuited.takedown(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						BlackPantherUnsuited.flyingKick(player);
					} else {
						BlackPantherUnsuited.vault(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						BlackPantherUnsuited.scan(player);
					} else {
						BlackPantherUnsuited.knife(player);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						BlackPantherUnsuited.toggleTracking(player); // v0.17.7 (user): Hunter's Tracking moved to Shift+V
					} else {
						BlackPantherUnsuited.uppercut(player);
					}
				}
			}
			case SLOT_6 -> {
				if (!pressed) {
					BlackPantherUnsuited.lowerBlock(player);
				} else if (shift) {
					BlackPantherUnsuited.resolve(player);
				} else {
					BlackPantherUnsuited.raiseBlock(player);
				}
			}
			case SLOT_8 -> { // the mask is the suit's: say so
				if (pressed && shift) {
					BlackPanther.toggleMask(player);
				}
			}
			default -> {
			}
		}
	}

	/** The suited kit's ids for a slot (plain, Shift). */
	public static String[] idsOf(AbilitySlot slot) {
		return idsOf(slot, true);
	}

	/** v0.17.4: a slot's ids (plain, Shift) for the suited or the unsuited kit. */
	public static String[] idsOf(AbilitySlot slot, boolean suited) {
		if (!suited) {
			return switch (slot) {
				case SLOT_1 -> new String[] { BlackPantherUnsuited.COMBO, BlackPantherUnsuited.SWEEP };
				case SLOT_2 -> new String[] { BlackPantherUnsuited.TAKEDOWN, BlackPantherUnsuited.POUND };
				case SLOT_3 -> new String[] { BlackPantherUnsuited.VAULT, BlackPantherUnsuited.KICK };
				case SLOT_4 -> new String[] { BlackPantherUnsuited.KNIFE, BlackPantherUnsuited.SCAN };
				case SLOT_5 -> new String[] { BlackPantherUnsuited.UPPERCUT, BlackPantherUnsuited.TRACKING };
				case SLOT_6 -> new String[] { BlackPantherUnsuited.BLOCK, BlackPantherUnsuited.RESOLVE };
				default -> new String[0];
			};
		}
		return switch (slot) {
			case SLOT_1 -> new String[] { BlackPantherAbilities.VIBRANIUM_COMBO, BlackPantherAbilities.KINETIC_COMBO };
			case SLOT_2 -> new String[] { BlackPantherAbilities.POUNCE, BlackPantherAbilities.KINETIC_POUNCE };
			case SLOT_3 -> new String[] { BlackPantherAbilities.LEAP, BlackPantherAbilities.DASH };
			case SLOT_4 -> new String[] { BlackPantherAbilities.DAGGER, BlackPantherAbilities.OVERLOAD };
			case SLOT_5 -> new String[] { BlackPantherAbilities.STALK, BlackPantherAbilities.SHROUD };
			case SLOT_6 -> new String[] { BlackPantherAbilities.ABSORB, BlackPantherAbilities.RETURN };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case BlackPantherAbilities.VIBRANIUM_COMBO -> BlackPantherConfig.VIBRANIUM_COMBO_COOLDOWN;
			case BlackPantherAbilities.KINETIC_COMBO -> BlackPantherConfig.KINETIC_COMBO_COOLDOWN;
			case BlackPantherAbilities.POUNCE -> BlackPantherConfig.POUNCE_COOLDOWN;
			case BlackPantherAbilities.KINETIC_POUNCE -> BlackPantherConfig.KINETIC_POUNCE_COOLDOWN;
			case BlackPantherAbilities.LEAP -> BlackPantherConfig.LEAP_COOLDOWN;
			case BlackPantherAbilities.DASH -> BlackPantherConfig.DASH_COOLDOWN;
			case BlackPantherAbilities.DAGGER -> BlackPantherConfig.DAGGER_COOLDOWN;
			case BlackPantherAbilities.OVERLOAD -> BlackPantherConfig.OVERLOAD_COOLDOWN;
			case BlackPantherAbilities.STALK -> BlackPantherConfig.STALK_COOLDOWN;
			case BlackPantherAbilities.SHROUD -> BlackPantherConfig.SHROUD_COOLDOWN;
			case BlackPantherAbilities.ABSORB -> BlackPantherConfig.ABSORB_COOLDOWN;
			case BlackPantherAbilities.RETURN -> BlackPantherConfig.RETURN_COOLDOWN;
			case BlackPantherUnsuited.COMBO -> BlackPantherConfig.COMBO_COOLDOWN;
			case BlackPantherUnsuited.SWEEP -> BlackPantherConfig.SWEEP_COOLDOWN;
			case BlackPantherUnsuited.TAKEDOWN -> BlackPantherConfig.TAKEDOWN_COOLDOWN;
			case BlackPantherUnsuited.POUND -> BlackPantherConfig.POUND_COOLDOWN;
			case BlackPantherUnsuited.VAULT -> BlackPantherConfig.VAULT_COOLDOWN;
			case BlackPantherUnsuited.KICK -> BlackPantherConfig.KICK_COOLDOWN;
			case BlackPantherUnsuited.KNIFE -> BlackPantherConfig.KNIFE_COOLDOWN;
			case BlackPantherUnsuited.SCAN -> BlackPantherConfig.SCAN_COOLDOWN;
			case BlackPantherUnsuited.UPPERCUT -> BlackPantherConfig.UPPERCUT_COOLDOWN;
			case BlackPantherUnsuited.BLOCK -> BlackPantherConfig.BLOCK_COOLDOWN;
			case BlackPantherUnsuited.RESOLVE -> BlackPantherConfig.RESOLVE_COOLDOWN;
			default -> 0;
		};
	}
}
