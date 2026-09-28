package com.projecthero.mod.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Hulk (v0.13.12). {@link com.projecthero.mod.hero.AbilityRouter}
 * hands him the slots when {@link #hasContext} is true -- has the Gamma power and no mutation selected on the
 * wheel -- on the same terms as every other Hero-Tier power.
 *
 * <pre>
 *   R (1)  Thunderclap        G (2)  Ground Smash
 *   X (3)  Super Leap (hold)  C (6)  Sprint Smash on / off
 *   Z (4), V (5)  -- unused
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
					HulkAbilities.thunderclap(player);
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
			case SLOT_6 -> {
				if (pressed) {
					HulkAbilities.toggleSprintSmash(player);
				}
			}
			default -> {
				if (pressed) {
					Hulk.say(player, "message.projecthero.hulk.unbound_key", ChatFormatting.DARK_GRAY);
				}
			}
		}
	}

	/** The ability id shown in each HUD box, or null for an unused slot. */
	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> HulkAbilities.THUNDERCLAP;
			case SLOT_2 -> HulkAbilities.GROUND_SMASH;
			case SLOT_3 -> HulkAbilities.SUPER_LEAP;
			default -> null;
		};
	}

	public static int maxCooldown(String id) {
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		return switch (id) {
			case HulkAbilities.THUNDERCLAP -> cfg.thunderclapCooldownTicks;
			case HulkAbilities.GROUND_SMASH -> cfg.groundSmashCooldownTicks;
			case HulkAbilities.SUPER_LEAP -> cfg.leapCooldownTicks;
			default -> 0;
		};
	}
}
