package com.projecthero.mod.supersoldier;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the Super Soldier kit and runs the power's per-player server tick (v0.14.8).
 * {@link com.projecthero.mod.hero.AbilityRouter} hands him the slots when {@link #hasContext} is true (he has the power
 * and no experimental mutation is selected). Ability 3 = X, 4 = Z, 5 = V, 6 = C. Shift is read on the server. H is not his:
 * H stays the power selector. N (v0.17.5, user-assigned) only in the Battle Suit top: the shield slot ({@link #ownsN}).
 *
 * <pre>
 *   R  Combo Strike      / Shift+R  Uppercut Launcher
 *   G  Flying Kick       / Shift+G  Judo Takedown            (v0.14.9; were the shield moves)
 *   Z  Leaping Slam      / Shift+Z  ULTIMATE: Peak Human Barrage, or Shield Storm with a shield (v0.17.7)
 *   X  Tactical Roll     / Shift+X  High Leap
 *   V  Counter Stance    / Shift+V  Assemble                  (v0.17.7; were Battle Cry / Tactical Focus)
 *   C  Shield Throw (v0.14.9: the shield in his hand -- Shift does nothing different)
 * </pre>
 */
public final class SuperSoldierAbilityManager {
	private SuperSoldierAbilityManager() {
	}

	public static void clearSessionState() {
		SuperSoldier.clearSessionState();
		SuperSoldierAbilities.clearSessionState();
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!SuperSoldier.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/**
	 * v0.17.5 (user-assigned N): a Super Soldier in the Captain America Battle Suit top owns N -- it stores / retrieves the shield in the
	 * suit's shield slot ({@link CapShieldSlot#toggle}). Routed from the H / N branch of {@code AbilityRouter}.
	 */
	public static boolean ownsN(ServerPlayer player) {
		return hasContext(player) && CapShieldSlot.hasSlot(player);
	}

	public static void handleN(ServerPlayer player, boolean pressed) {
		if (pressed) {
			CapShieldSlot.toggle(player);
		}
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!pressed) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					SuperSoldierAbilities.uppercut(player);
				} else {
					SuperSoldierAbilities.comboStrike(player);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					SuperSoldierAbilities.takedown(player);
				} else {
					SuperSoldierAbilities.flyingKick(player);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					SuperSoldierAbilities.highLeap(player);
				} else {
					SuperSoldierAbilities.tacticalRoll(player);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					SuperSoldierAbilities.ultimate(player); // v0.17.7: Barrage bare-handed, Shield Storm with a shield
				} else {
					SuperSoldierAbilities.leapingSlam(player);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					SuperSoldierAbilities.assemble(player);
				} else {
					SuperSoldierAbilities.counterStance(player);
				}
			}
			case SLOT_6 -> SuperSoldierAbilities.shieldThrow(player);
			default -> {
				// H / N are never routed to a Hero-Tier power
			}
		}
	}

	/** Runs for every player every server tick, whichever power currently holds the slots. Cheap when he has no power. */
	public static void serverTick(ServerPlayer player) {
		SuperSoldierArmorGate.enforce(player); // v0.14.9: only a Super Soldier may wear the suit -- checked for everyone
		CapShieldSlot.tick(player); // v0.17.5: the Battle Suit's shield slot (guard upkeep; a slotted shield off a top without a slot)
		if (!SuperSoldier.hasPower(player)) {
			return;
		}
		SuperSoldier.tick(player);
		SuperSoldierAbilities.tick(player);
	}

	/** The ability ids that share a key (plain, Shift), for the HUD's combined cooldown. */
	public static String[] idsOf(AbilitySlot slot) {
		return idsOf(slot, false);
	}

	/** v0.17.7: as {@link #idsOf(AbilitySlot)}, with Shift+Z naming the ultimate a shield ({@code shield}) would fire. */
	public static String[] idsOf(AbilitySlot slot, boolean shield) {
		return switch (slot) {
			case SLOT_1 -> new String[] { SuperSoldierAbilities.COMBO, SuperSoldierAbilities.UPPERCUT };
			case SLOT_2 -> new String[] { SuperSoldierAbilities.FLYING_KICK, SuperSoldierAbilities.TAKEDOWN };
			case SLOT_3 -> new String[] { SuperSoldierAbilities.ROLL, SuperSoldierAbilities.HIGH_LEAP };
			case SLOT_4 -> new String[] { SuperSoldierAbilities.SLAM, shield ? SuperSoldierAbilities.SHIELD_STORM : SuperSoldierAbilities.BARRAGE };
			case SLOT_5 -> new String[] { SuperSoldierAbilities.COUNTER, SuperSoldierAbilities.ASSEMBLE };
			case SLOT_6 -> new String[] { SuperSoldierAbilities.SHIELD_THROW };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case SuperSoldierAbilities.COMBO -> SuperSoldierConfig.COMBO_COOLDOWN;
			case SuperSoldierAbilities.UPPERCUT -> SuperSoldierConfig.UPPERCUT_COOLDOWN;
			case SuperSoldierAbilities.FLYING_KICK -> SuperSoldierConfig.KICK_COOLDOWN;
			case SuperSoldierAbilities.TAKEDOWN -> SuperSoldierConfig.TAKEDOWN_COOLDOWN;
			// the longer of the two shields' (an ordinary shield's)
			case SuperSoldierAbilities.SHIELD_THROW -> Math.max(SuperSoldierConfig.SHIELD_THROW_COOLDOWN,
					SuperSoldierConfig.NORMAL_SHIELD_THROW_COOLDOWN);
			case SuperSoldierAbilities.SLAM -> SuperSoldierConfig.SLAM_COOLDOWN;
			case SuperSoldierAbilities.BARRAGE, SuperSoldierAbilities.SHIELD_STORM, SuperSoldierAbilities.ULTIMATE ->
					SuperSoldierConfig.ULTIMATE_COOLDOWN;
			case SuperSoldierAbilities.ROLL -> SuperSoldierConfig.ROLL_COOLDOWN;
			case SuperSoldierAbilities.HIGH_LEAP -> SuperSoldierConfig.HIGH_LEAP_COOLDOWN;
			case SuperSoldierAbilities.COUNTER -> SuperSoldierConfig.COUNTER_COOLDOWN;
			case SuperSoldierAbilities.ASSEMBLE -> SuperSoldierConfig.ASSEMBLE_COOLDOWN;
			default -> 0;
		};
	}
}
