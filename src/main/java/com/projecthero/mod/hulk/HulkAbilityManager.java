package com.projecthero.mod.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Hulk. {@link com.projecthero.mod.hero.AbilityRouter} hands him the
 * slots when {@link #hasContext} is true -- has the Gamma power and no mutation selected on the wheel -- and, while he is
 * out, ahead of every other power. v0.16.4 (user) kit (tap / Shift+key):
 *
 * <pre>
 *   R (1)  Super Punch          / Ground Smash
 *   G (2)  Thunderclap          / Fault-line Stomp
 *   X (3)  Super Leap (hold up to 0.5 s)
 *   Z (4)  Bulldozer Charge     / Launch Uppercut
 *   V (5)  Grab, V again throws / Debris Throw, or the Loki Slam on a target within 10 blocks
 *   C (6)  Gamma Burst          / Earth Wall
 * </pre>
 *
 * The Gladiator Hulk keeps his twelve weapon moves on these keys instead ({@code GladiatorAbilities}).
 */
public final class HulkAbilityManager {
	private HulkAbilityManager() {
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Hulk.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		// v0.15.3: the Hulk in the full gladiator gear fights with his axe and hammer instead (tap / Shift+key)
		if (slot.ordinal() < 6 && com.projecthero.mod.hulk.gladiator.GladiatorAbilities.active(player)) {
			com.projecthero.mod.hulk.gladiator.GladiatorAbilities.handle(player, slot, pressed);
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (shift) {
						HulkAbilities.groundSmash(player);
					} else {
						HulkAbilities.superPunch(player);
					}
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						HulkAbilities.faultStomp(player);
					} else {
						HulkAbilities.thunderclap(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					HulkAbilities.beginLeap(player);
				} else {
					HulkAbilities.releaseLeap(player);
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						HulkAbilities.uppercut(player);
					} else {
						HulkAbilities.bulldoze(player);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					HulkGrab.press(player, shift);
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						HulkAbilities.earthWall(player);
					} else {
						HulkAbilities.gammaBurst(player);
					}
				}
			}
			default -> {
			}
		}
	}

	/** The tap ability id shown in each HUD box. */
	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> HulkAbilities.SUPER_PUNCH;
			case SLOT_2 -> HulkAbilities.THUNDERCLAP;
			case SLOT_3 -> HulkAbilities.SUPER_LEAP;
			case SLOT_4 -> HulkAbilities.BULLDOZE;
			case SLOT_5 -> HulkAbilities.GRAB;
			case SLOT_6, SLOT_7, SLOT_8 -> HulkAbilities.GAMMA_BURST; // H / N are mutation-only (v0.13.22); never routed here
		};
	}

	/** v0.16.4: the Shift+key ability id on each key (X has only the leap). */
	public static String shiftIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> HulkAbilities.GROUND_SMASH;
			case SLOT_2 -> HulkAbilities.FAULT_STOMP;
			case SLOT_3 -> HulkAbilities.SUPER_LEAP;
			case SLOT_4 -> HulkAbilities.UPPERCUT;
			case SLOT_5 -> HulkAbilities.LOKI_SLAM;
			case SLOT_6, SLOT_7, SLOT_8 -> HulkAbilities.EARTH_WALL;
		};
	}

	public static int maxCooldown(String id) {
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		return switch (id) {
			case HulkAbilities.SUPER_PUNCH -> cfg.superPunchCooldownTicks;
			case HulkAbilities.GROUND_SMASH -> cfg.groundSmashCooldownTicks;
			case HulkAbilities.THUNDERCLAP -> cfg.clapCooldownTicks;
			case HulkAbilities.FAULT_STOMP -> cfg.faultCooldownTicks;
			case HulkAbilities.BULLDOZE -> cfg.bulldozeCooldownTicks;
			case HulkAbilities.UPPERCUT -> cfg.uppercutCooldownTicks;
			case HulkAbilities.SUPER_LEAP -> cfg.leapCooldownTicks;
			case HulkAbilities.GAMMA_BURST -> cfg.gammaBurstCooldownTicks;
			case HulkAbilities.EARTH_WALL -> cfg.earthWallCooldownTicks;
			case HulkAbilities.GRAB -> cfg.grabCooldownTicks;
			case HulkAbilities.LOKI_SLAM -> cfg.lokiCooldownTicks + HulkGrab.LOKI_TOTAL_TICKS;
			default -> 0;
		};
	}
}
