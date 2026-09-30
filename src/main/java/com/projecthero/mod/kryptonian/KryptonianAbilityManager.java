package com.projecthero.mod.kryptonian;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the Kryptonian (v0.14.8). {@link com.projecthero.mod.hero.AbilityRouter} hands
 * him the slots when {@link #hasContext} is true -- has the power and no mutation selected on the wheel (a Primary power
 * rules the mutations out anyway). Shift is read server-side off the player's sneak state.
 *
 * <pre>
 *   R (1)  Kryptonian Punch      Shift+R  Heat Vision (hold)
 *   G (2)  Freeze Breath         Shift+G  Thunderclap
 *   X (3)  Super Dash            Shift+X  Sky Launch
 *   Z (4)  Ground Slam           Shift+Z  SOLAR FLARE
 *   V (5)  X-Ray Vision          Shift+V  Super Grab / Throw
 *   C (6)  --
 * </pre>
 * H stays the power wheel and N does nothing: neither is ever routed here.
 */
public final class KryptonianAbilityManager {
	private KryptonianAbilityManager() {
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Kryptonian.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (!pressed) {
					KryptonianAbilities.stopHeatVision(player);
				} else if (shift) {
					KryptonianAbilities.startHeatVision(player);
				} else {
					KryptonianAbilities.punch(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						KryptonianAbilities.thunderclap(player);
					} else {
						KryptonianAbilities.freezeBreath(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						KryptonianAbilities.skyLaunch(player);
					} else {
						KryptonianAbilities.superDash(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						KryptonianAbilities.solarFlare(player);
					} else {
						KryptonianAbilities.groundSlam(player);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift || KryptonianAbilities.holding(player)) {
						KryptonianAbilities.superGrab(player); // grab, or (anything held) throw
					} else {
						KryptonianAbilities.xray(player);
					}
				}
			}
			default -> {
			}
		}
	}

	/** The two moves on one key: [plain, shift]. */
	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { KryptonianAbilities.PUNCH, KryptonianAbilities.HEAT_VISION };
			case SLOT_2 -> new String[] { KryptonianAbilities.FREEZE_BREATH, KryptonianAbilities.THUNDERCLAP };
			case SLOT_3 -> new String[] { KryptonianAbilities.SUPER_DASH, KryptonianAbilities.SKY_LAUNCH };
			case SLOT_4 -> new String[] { KryptonianAbilities.GROUND_SLAM, KryptonianAbilities.SOLAR_FLARE };
			case SLOT_5 -> new String[] { KryptonianAbilities.XRAY, KryptonianAbilities.SUPER_GRAB };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case KryptonianAbilities.PUNCH -> KryptonianConfig.PUNCH_COOLDOWN;
			case KryptonianAbilities.HEAT_VISION -> KryptonianConfig.HEAT_COOLDOWN;
			case KryptonianAbilities.FREEZE_BREATH -> KryptonianConfig.BREATH_COOLDOWN;
			case KryptonianAbilities.THUNDERCLAP -> KryptonianConfig.CLAP_COOLDOWN;
			case KryptonianAbilities.GROUND_SLAM -> KryptonianConfig.SLAM_COOLDOWN;
			case KryptonianAbilities.SOLAR_FLARE -> KryptonianConfig.FLARE_COOLDOWN;
			case KryptonianAbilities.SUPER_DASH -> KryptonianConfig.DASH_COOLDOWN;
			case KryptonianAbilities.SKY_LAUNCH -> KryptonianConfig.LAUNCH_COOLDOWN;
			case KryptonianAbilities.XRAY -> KryptonianConfig.XRAY_COOLDOWN;
			case KryptonianAbilities.SUPER_GRAB -> KryptonianConfig.GRAB_COOLDOWN;
			default -> 0;
		};
	}
}
