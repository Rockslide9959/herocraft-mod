package com.projecthero.mod.supersoldier;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the Super Soldier kit and runs the power's per-player server tick (v0.14.8).
 * {@link com.projecthero.mod.hero.AbilityRouter} hands him the slots when {@link #hasContext} is true (he has the power
 * and no experimental mutation is selected). Ability 3 = X, 4 = Z, 5 = V, 6 = C. Shift is read on the server. H and N
 * are not his: H stays the power selector.
 *
 * <pre>
 *   R  Combo Strike      / Shift+R  Uppercut Launcher
 *   G  Flying Kick       / Shift+G  Judo Takedown            (v0.14.9; were the shield moves)
 *   Z  Leaping Slam      / Shift+Z  Super Soldier Onslaught (ultimate)
 *   X  Tactical Roll     / Shift+X  High Leap
 *   V  Battle Cry        / Shift+V  Tactical Focus
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
					SuperSoldierAbilities.onslaught(player);
				} else {
					SuperSoldierAbilities.leapingSlam(player);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					SuperSoldierAbilities.tacticalFocus(player);
				} else {
					SuperSoldierAbilities.battleCry(player);
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
		if (!SuperSoldier.hasPower(player)) {
			return;
		}
		SuperSoldier.tick(player);
		SuperSoldierAbilities.tick(player);
	}

	/** The ability ids that share a key (plain, Shift), for the HUD's combined cooldown. */
	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { SuperSoldierAbilities.COMBO, SuperSoldierAbilities.UPPERCUT };
			case SLOT_2 -> new String[] { SuperSoldierAbilities.FLYING_KICK, SuperSoldierAbilities.TAKEDOWN };
			case SLOT_3 -> new String[] { SuperSoldierAbilities.ROLL, SuperSoldierAbilities.HIGH_LEAP };
			case SLOT_4 -> new String[] { SuperSoldierAbilities.SLAM, SuperSoldierAbilities.ONSLAUGHT };
			case SLOT_5 -> new String[] { SuperSoldierAbilities.BATTLE_CRY, SuperSoldierAbilities.FOCUS };
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
			case SuperSoldierAbilities.ONSLAUGHT -> SuperSoldierConfig.ONSLAUGHT_COOLDOWN;
			case SuperSoldierAbilities.ROLL -> SuperSoldierConfig.ROLL_COOLDOWN;
			case SuperSoldierAbilities.HIGH_LEAP -> SuperSoldierConfig.HIGH_LEAP_COOLDOWN;
			case SuperSoldierAbilities.BATTLE_CRY -> SuperSoldierConfig.BATTLE_CRY_COOLDOWN;
			case SuperSoldierAbilities.FOCUS -> SuperSoldierConfig.FOCUS_COOLDOWN;
			default -> 0;
		};
	}
}
