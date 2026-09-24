package com.projecthero.mod.wolverine;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Wolverine kit and runs the power's per-player server
 * tick. {@link com.projecthero.mod.hero.AbilityRouter} hands Wolverine the slots when {@link #hasContext}
 * is true (has the power, no experimental mutation selected) -- the same terms as every other Hero-Tier
 * power, so R/G/Z/X/C/V (Ability 1-6) keep their own meaning for everyone else:
 *
 * <pre>
 *   R (1)  Claw Slash        G (2)  Cross Slash        Z (3)  Claw Dash
 *   X (4)  Berserker Rage    C (5)  Frenzy             V (6)  Adamantium Execution
 * </pre>
 *
 * <p>H (the claw toggle) is not an ability slot -- see {@code WolverineActionPayload}.
 */
public final class WolverineAbilityManager {
	private WolverineAbilityManager() {
	}

	public static void clearSessionState() {
		WolverineScheduler.clearSessionState();
		WolverineAbilities.clearSessionState();
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
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
			case SLOT_1 -> WolverineAbilities.clawSlash(player);
			case SLOT_2 -> WolverineAbilities.crossSlash(player);
			case SLOT_3 -> WolverineAbilities.clawDash(player);
			case SLOT_4 -> WolverineAbilities.berserkerRage(player);
			case SLOT_5 -> WolverineAbilities.frenzy(player);
			case SLOT_6 -> WolverineAbilities.execution(player);
		}
	}

	/** Runs for every Wolverine every server tick, whichever power currently holds the slots. */
	public static void serverTick(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		WolverinePassives.tick(player);
		WolverineScheduler.tick(player);
		WolverineAbilities.tick(player);
	}

	/** Cooldown-map id for a slot -- the HUD reads it client-side from the synced state. */
	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> WolverineAbilities.SLASH;
			case SLOT_2 -> WolverineAbilities.CROSS;
			case SLOT_3 -> WolverineAbilities.DASH;
			case SLOT_4 -> WolverineAbilities.RAGE;
			case SLOT_5 -> WolverineAbilities.FRENZY;
			case SLOT_6 -> WolverineAbilities.EXECUTION;
		};
	}

	/** Max cooldown of a slot, for the HUD's cooldown fill. */
	public static int maxCooldown(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> WolverineConfig.SLASH_COOLDOWN;
			case SLOT_2 -> WolverineConfig.CROSS_COOLDOWN;
			case SLOT_3 -> WolverineConfig.DASH_COOLDOWN;
			case SLOT_4 -> WolverineConfig.RAGE_COOLDOWN;
			case SLOT_5 -> WolverineConfig.FRENZY_COOLDOWN;
			case SLOT_6 -> WolverineConfig.EXECUTION_COOLDOWN;
		};
	}
}
