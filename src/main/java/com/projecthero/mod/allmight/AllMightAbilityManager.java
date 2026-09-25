package com.projecthero.mod.allmight;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the All Might kit and runs the power's per-player server tick.
 * {@link com.projecthero.mod.hero.AbilityRouter} hands All Might the slots when {@link #hasContext} is true (has the
 * power, no experimental mutation selected) -- the same terms as every other Hero-Tier power. The slots are numbered by
 * the mod's key layout (Ability 3 = X, Ability 4 = Z, Ability 5 = V, Ability 6 = C):
 *
 * <pre>
 *   R (1)  Detroit Smash      G (2)  Texas Smash
 *   X (3)  New Hampshire Smash        Z (4)  Carolina Smash
 *   V (5)  United States of Smash     C (6)  Full Cowl
 *   H      Transform (full-power form)   N  All Might Leap (utility)
 * </pre>
 *
 * H and N are not ability slots -- see {@code AllMightActionPayload}.
 */
public final class AllMightAbilityManager {
	private AllMightAbilityManager() {
	}

	public static void clearSessionState() {
		AllMight.clearSessionState();
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!AllMight.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!pressed) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> AllMightAbilities.detroit(player);
			case SLOT_2 -> AllMightAbilities.texas(player);
			case SLOT_3 -> AllMightAbilities.newHampshire(player);
			case SLOT_4 -> AllMightAbilities.carolina(player);
			case SLOT_5 -> AllMightAbilities.unitedStates(player);
			case SLOT_6 -> AllMightAbilities.fullCowl(player);
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
			case SLOT_3 -> AllMightAbilities.NEW_HAMPSHIRE;
			case SLOT_4 -> AllMightAbilities.CAROLINA;
			case SLOT_5 -> AllMightAbilities.UNITED_STATES;
			case SLOT_6 -> AllMightAbilities.COWL;
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case AllMightAbilities.DETROIT -> AllMightConfig.DETROIT_COOLDOWN;
			case AllMightAbilities.TEXAS -> AllMightConfig.TEXAS_COOLDOWN;
			case AllMightAbilities.NEW_HAMPSHIRE -> AllMightConfig.NEW_HAMPSHIRE_COOLDOWN;
			case AllMightAbilities.CAROLINA -> AllMightConfig.CAROLINA_COOLDOWN;
			case AllMightAbilities.UNITED_STATES -> AllMightConfig.UNITED_STATES_COOLDOWN;
			case AllMightAbilities.COWL -> AllMightConfig.COWL_COOLDOWN_TICKS;
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
			case AllMightAbilities.COWL -> AllMightConfig.COWL_OFA_COST;
			case AllMightAbilities.LEAP -> AllMightConfig.LEAP_COST;
			default -> 0;
		};
	}
}
