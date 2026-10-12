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
 *   R (1)  Claw Slash          Shift+R  Rising Uppercut
 *   G (2)  Cross Slash         Shift+G  Whirlwind
 *   X (3)  Claw Dash           Shift+X  Pounce
 *   Z (4)  Berserker Plunge    Shift+Z  Adamantium Execution (hold 5 s, release)
 *   V (5)  Frenzy              Shift+V  Bleed Flurry
 *   C (6)  Berserker Rage      Shift+C  Feral Roar          N  Sniff
 * </pre>
 * v0.16.1: Shift = sneaking when the key goes down (as for the Punisher); every Shift move has its own cooldown.
 * Berserker Plunge took over plain Z and the Adamantium Execution moved to Shift+Z.
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
		boolean shift = player.isShiftKeyDown();
		if (slot == AbilitySlot.SLOT_4) {
			// Shift+Z: Adamantium Execution is hold-to-charge (press starts it, the release fires it -- a release always
			// goes through, whatever Shift does meanwhile); plain Z: Berserker Plunge
			if (pressed) {
				if (shift) {
					WolverineAbilities.beginExecutionCharge(player);
				} else {
					WolverineShiftAbilities.berserkerPlunge(player);
				}
			} else {
				WolverineAbilities.releaseExecutionCharge(player);
			}
			return;
		}
		if (!pressed) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					WolverineShiftAbilities.risingUppercut(player);
				} else {
					WolverineAbilities.clawSlash(player);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					WolverineShiftAbilities.whirlwind(player);
				} else {
					WolverineAbilities.crossSlash(player);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					WolverineShiftAbilities.pounce(player);
				} else {
					WolverineAbilities.clawDash(player);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					WolverineShiftAbilities.bleedFlurry(player);
				} else {
					WolverineAbilities.frenzy(player);
				}
			}
			case SLOT_6, SLOT_7, SLOT_8 -> {
				if (shift) {
					WolverineShiftAbilities.feralRoar(player);
				} else {
					WolverineAbilities.berserkerRage(player);
				}
			} // H / N are mutation-only (v0.13.22); never routed here
			default -> {
			}
		}
	}

	/** Runs for every Wolverine every server tick, whichever power currently holds the slots. */
	public static void serverTick(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		WolverineSense.tick(player);
		WolverineBlock.tick(player);
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
			case SLOT_4 -> WolverineShiftAbilities.PLUNGE;
			case SLOT_5 -> WolverineAbilities.FRENZY;
			case SLOT_6, SLOT_7, SLOT_8 -> WolverineAbilities.RAGE; // H / N are mutation-only (v0.13.22); never routed here
		};
	}

	/** Max cooldown of a slot, for the HUD's cooldown fill. */
	public static int maxCooldown(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> WolverineConfig.SLASH_COOLDOWN;
			case SLOT_2 -> WolverineConfig.CROSS_COOLDOWN;
			case SLOT_3 -> WolverineConfig.DASH_COOLDOWN;
			case SLOT_4 -> WolverineConfig.PLUNGE_COOLDOWN;
			case SLOT_5 -> WolverineConfig.FRENZY_COOLDOWN;
			case SLOT_6, SLOT_7, SLOT_8 -> 0; // H / N are mutation-only (v0.13.22); never routed here // the rage bar gates it, not a cooldown
		};
	}

	/** v0.16.1: the Shift move's cooldown id for a slot (the HUD's thin Shift bar). */
	public static String shiftAbilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> WolverineShiftAbilities.UPPERCUT;
			case SLOT_2 -> WolverineShiftAbilities.WHIRLWIND;
			case SLOT_3 -> WolverineShiftAbilities.POUNCE;
			case SLOT_4 -> WolverineAbilities.EXECUTION;
			case SLOT_5 -> WolverineShiftAbilities.FLURRY;
			case SLOT_6, SLOT_7, SLOT_8 -> WolverineShiftAbilities.ROAR;
		};
	}

	/** v0.16.1: max cooldown of a slot's Shift move. */
	public static int shiftMaxCooldown(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> WolverineConfig.UPPERCUT_COOLDOWN;
			case SLOT_2 -> WolverineConfig.WHIRLWIND_COOLDOWN;
			case SLOT_3 -> WolverineConfig.POUNCE_COOLDOWN;
			case SLOT_4 -> WolverineConfig.EXECUTION_COOLDOWN;
			case SLOT_5 -> WolverineConfig.FLURRY_COOLDOWN;
			case SLOT_6, SLOT_7, SLOT_8 -> WolverineConfig.ROAR_COOLDOWN;
		};
	}
}
