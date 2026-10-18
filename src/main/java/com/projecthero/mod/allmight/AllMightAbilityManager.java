package com.projecthero.mod.allmight;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the All Might kit and runs the power's per-player server tick.
 * {@link com.projecthero.mod.hero.AbilityRouter} hands All Might the slots when {@link #hasContext} is true (Power Form, no
 * experimental mutation selected). The Base Form is a plain player and owns no keys. Ability 3 = X, 4 = Z, 5 = V, 6 = C.
 * v0.16.6: every key has a Shift move:
 *
 * <pre>
 *   R (1)  Detroit Smash            Shift: New Hampshire Smash
 *   G (2)  Texas Smash              Shift: Delaware Smash
 *   X (3)  Leap                     Shift: Manchester Smash
 *   Z (4)  United States of Smash (hold 5 s)   Shift: Oklahoma Smash
 *   V (5)  Carolina Smash           Shift: Missouri Smash
 *   C (6)  Plus Ultra               Shift: St. Louis Smash
 *   H      Base Form / Power Form   N  the costume locker
 * </pre>
 */
public final class AllMightAbilityManager {
	private AllMightAbilityManager() {
	}

	public static void clearSessionState() {
		AllMight.clearSessionState();
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!AllMight.hasPower(player) || !AllMight.isFullPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		boolean shift = player.isShiftKeyDown();
		if (slot == AbilitySlot.SLOT_4) {
			if (pressed) {
				if (shift) {
					AllMightAbilities.oklahoma(player);
				} else {
					AllMightAbilities.unitedStatesPress(player);
				}
			} else {
				AllMightAbilities.unitedStatesRelease(player);
			}
			return;
		}
		if (!pressed) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					AllMightAbilities.newHampshire(player);
				} else {
					AllMightAbilities.detroit(player);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					AllMightAbilities.delaware(player);
				} else {
					AllMightAbilities.texas(player);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					AllMightAbilities.manchester(player);
				} else {
					AllMightAbilities.leap(player);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					AllMightAbilities.missouri(player);
				} else {
					AllMightAbilities.carolina(player);
				}
			}
			case SLOT_6, SLOT_7, SLOT_8 -> { // H / N are mutation-only (v0.13.22); never routed here
				if (shift) {
					AllMightAbilities.stLouis(player);
				} else {
					AllMightAbilities.plusUltra(player);
				}
			}
			default -> {
			}
		}
	}

	/** Runs for every player every server tick, whichever power currently holds the slots (v0.16.6: lenders and offers too). */
	public static void serverTick(ServerPlayer player) {
		AllMightPassOn.tick(player);
		if (!AllMight.hasPower(player)) {
			return;
		}
		AllMight.tick(player);
	}

	/** Cooldown-map id for a slot -- the HUD reads it client-side from the synced state. */
	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> AllMightAbilities.DETROIT;
			case SLOT_2 -> AllMightAbilities.TEXAS;
			case SLOT_3 -> AllMightAbilities.LEAP;
			case SLOT_4 -> AllMightAbilities.UNITED_STATES;
			case SLOT_5 -> AllMightAbilities.CAROLINA;
			case SLOT_6, SLOT_7, SLOT_8 -> AllMightAbilities.PLUS_ULTRA; // H / N are mutation-only (v0.13.22); never routed here
		};
	}

	/** v0.16.6: the Shift move's cooldown-map id for a slot. */
	public static String shiftAbilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> AllMightAbilities.NEW_HAMPSHIRE;
			case SLOT_2 -> AllMightAbilities.DELAWARE;
			case SLOT_3 -> AllMightAbilities.MANCHESTER;
			case SLOT_4 -> AllMightAbilities.OKLAHOMA;
			case SLOT_5 -> AllMightAbilities.MISSOURI;
			case SLOT_6, SLOT_7, SLOT_8 -> AllMightAbilities.ST_LOUIS;
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case AllMightAbilities.DETROIT -> AllMightConfig.DETROIT_COOLDOWN;
			case AllMightAbilities.TEXAS -> AllMightConfig.TEXAS_COOLDOWN;
			case AllMightAbilities.NEW_HAMPSHIRE -> AllMightConfig.NEW_HAMPSHIRE_COOLDOWN;
			case AllMightAbilities.CAROLINA -> AllMightConfig.CAROLINA_COOLDOWN;
			case AllMightAbilities.UNITED_STATES -> AllMightConfig.UNITED_STATES_COOLDOWN;
			case AllMightAbilities.PLUS_ULTRA -> AllMightConfig.PLUS_ULTRA_COOLDOWN_TICKS;
			case AllMightAbilities.LEAP -> AllMightConfig.LEAP_COOLDOWN;
			case AllMightAbilities.DELAWARE -> AllMightConfig.DELAWARE_COOLDOWN;
			case AllMightAbilities.MANCHESTER -> AllMightConfig.MANCHESTER_COOLDOWN;
			case AllMightAbilities.OKLAHOMA -> AllMightConfig.OKLAHOMA_COOLDOWN;
			case AllMightAbilities.MISSOURI -> AllMightConfig.MISSOURI_COOLDOWN;
			case AllMightAbilities.ST_LOUIS -> AllMightConfig.ST_LOUIS_COOLDOWN;
			default -> 0;
		};
	}

	/** v0.16.6: nothing costs anything any more (Output and Strain replaced the OFA timer). Kept for callers; always 0. */
	public static int cost(String id) {
		return 0;
	}
}
