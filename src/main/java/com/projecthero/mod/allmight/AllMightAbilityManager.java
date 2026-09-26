package com.projecthero.mod.allmight;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the All Might kit and runs the power's per-player server tick.
 * {@link com.projecthero.mod.hero.AbilityRouter} hands All Might the slots when {@link #hasContext} is true (Power Form, no
 * experimental mutation selected). The Base Form is a plain player and owns no keys. Ability 3 = X, 4 = Z, 5 = V, 6 = C:
 *
 * <pre>
 *   R (1)  Detroit Smash   (Shift+R = New Hampshire Smash)     G (2)  Texas Smash
 *   X (3)  Leap            Z (4)  United States of Smash (hold 5 s)
 *   V (5)  Carolina Smash  C (6)  Plus Ultra (toggle)
 *   H      Base Form / Power Form
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
		if (slot == AbilitySlot.SLOT_4) {
			if (pressed) {
				AllMightAbilities.unitedStatesPress(player);
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
				if (player.isShiftKeyDown()) {
					AllMightAbilities.newHampshire(player);
				} else {
					AllMightAbilities.detroit(player);
				}
			}
			case SLOT_2 -> AllMightAbilities.texas(player);
			case SLOT_3 -> AllMightAbilities.leap(player);
			case SLOT_5 -> AllMightAbilities.carolina(player);
			case SLOT_6 -> AllMightAbilities.plusUltra(player);
			default -> {
			}
		}
	}

	/** Runs for every All Might every server tick, whichever power currently holds the slots. */
	public static void serverTick(ServerPlayer player) {
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
			case SLOT_6 -> AllMightAbilities.PLUS_ULTRA;
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
			default -> 0;
		};
	}

	public static int cost(String id) {
		return switch (id) {
			case AllMightAbilities.DETROIT -> AllMightConfig.DETROIT_COST;
			case AllMightAbilities.TEXAS -> AllMightConfig.TEXAS_COST;
			case AllMightAbilities.NEW_HAMPSHIRE -> AllMightConfig.NEW_HAMPSHIRE_COST;
			case AllMightAbilities.CAROLINA -> AllMightConfig.CAROLINA_COST;
			case AllMightAbilities.UNITED_STATES -> AllMightConfig.UNITED_STATES_COST;
			case AllMightAbilities.LEAP -> AllMightConfig.LEAP_COST;
			default -> 0;
		};
	}
}
