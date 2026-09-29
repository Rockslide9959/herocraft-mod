package com.projecthero.mod.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Hulk (v0.13.14 kit). {@link com.projecthero.mod.hero.AbilityRouter}
 * hands him the slots when {@link #hasContext} is true -- has the Gamma power and no mutation selected on the wheel --
 * and, while he is out, ahead of every other power.
 *
 * <pre>
 *   R (1)  Power Punch              G (2)  Ground Smash
 *   X (3)  Super Leap (hold)        Z (4)  Thunderclap   /  Shift+Z (hold 5 s) HULK SMASH
 *   V (5)  Grab / Throw, Shift+V Crush (holding) or Earth Chunk (empty-handed)
 *   C (6)  Charge
 * </pre>
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
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					HulkAbilities.powerPunch(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					HulkAbilities.groundSmash(player);
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
					if (player.isShiftKeyDown()) {
						HulkAbilities.beginHulkSmash(player);
					} else {
						HulkAbilities.thunderclap(player);
					}
				} else {
					HulkAbilities.releaseHulkSmash(player);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					HulkGrab.press(player, player.isShiftKeyDown());
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					HulkAbilities.charge(player);
				}
			}
		}
	}

	/** The ability id shown in each HUD box. */
	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> HulkAbilities.POWER_PUNCH;
			case SLOT_2 -> HulkAbilities.GROUND_SMASH;
			case SLOT_3 -> HulkAbilities.SUPER_LEAP;
			case SLOT_4 -> HulkAbilities.THUNDERCLAP;
			case SLOT_5 -> HulkAbilities.GRAB;
			case SLOT_6, SLOT_7, SLOT_8 -> HulkAbilities.CHARGE; // H / N are mutation-only (v0.13.22); never routed here
		};
	}

	public static int maxCooldown(String id) {
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		return switch (id) {
			case HulkAbilities.POWER_PUNCH -> cfg.powerPunchCooldownTicks;
			case HulkAbilities.GROUND_SMASH -> cfg.groundSmashCooldownTicks;
			case HulkAbilities.SUPER_LEAP -> cfg.leapCooldownTicks;
			case HulkAbilities.THUNDERCLAP -> cfg.thunderclapCooldownTicks;
			case HulkAbilities.HULK_SMASH -> cfg.hulkSmashCooldownTicks;
			case HulkAbilities.GRAB -> cfg.grabCooldownTicks;
			case HulkAbilities.CHARGE -> cfg.chargeCooldownTicks;
			default -> 0;
		};
	}
}
