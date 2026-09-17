package com.projecthero.mod.greenlantern.item;

import com.projecthero.mod.armor.PowerEquipmentLock;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Puts the four synthesised {@link GreenLanternArmorItem} pieces on the player when they Suit Up, and
 * takes them off on suit-down/death/logout/power loss. Cloned from {@code MaxSteelSuitArmor}: real
 * armour is pushed into the inventory (dropped only if full) rather than stowed, and every piece is
 * {@link PowerEquipmentLock#bind Curse-of-Binding-locked} so it cannot be pulled out of its slot.
 *
 * <p>v0.11.4: also grants a flat {@link GreenLanternConfig#SUIT_MELEE_BONUS} {@code ATTACK_DAMAGE}
 * modifier while the suit is on, applied/cleared the same {@link PowerToggles} way as every other
 * suit-bound attribute buff in the mod (e.g. the construct Energy Blade's damage modifier).
 */
public final class GreenLanternSuitArmor {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ResourceLocation SUIT_MELEE_BONUS =
			com.projecthero.mod.ProjectHeroMod.id("green_lantern_suit_melee");

	private GreenLanternSuitArmor() {
	}

	public static void equip(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack current = player.getItemBySlot(slot);
			if (current.getItem() instanceof GreenLanternArmorItem) {
				continue;
			}
			if (!current.isEmpty()) {
				ItemStack displaced = current.copy();
				player.setItemSlot(slot, ItemStack.EMPTY);
				if (!player.getInventory().add(displaced)) {
					player.drop(displaced, false);
				}
				player.displayClientMessage(Component.translatable(
						"message.projecthero.green_lantern.armour_stowed", displaced.getHoverName()), true);
			}
			player.setItemSlot(slot, freshPiece(player, slot));
		}
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, SUIT_MELEE_BONUS,
				GreenLanternConfig.SUIT_MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
	}

	public static void reequipMissing(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, freshPiece(player, slot));
			}
		}
		// PowerToggles.modifier() is idempotent -- cheap insurance against any path that clears
		// transient attribute modifiers while the player stays suited.
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, SUIT_MELEE_BONUS,
				GreenLanternConfig.SUIT_MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
	}

	public static void strip(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof GreenLanternArmorItem) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		if (player instanceof ServerPlayer sp) {
			PowerToggles.clearModifier(sp, Attributes.ATTACK_DAMAGE, SUIT_MELEE_BONUS);
		}
	}

	public static boolean deleteLoose(Player player) {
		var inv = player.getInventory();
		boolean removed = false;
		for (int i = 0; i < inv.items.size(); i++) {
			if (inv.items.get(i).getItem() instanceof GreenLanternArmorItem) {
				inv.items.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (inv.offhand.get(i).getItem() instanceof GreenLanternArmorItem) {
				inv.offhand.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		return removed;
	}

	public static boolean wearing(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof GreenLanternArmorItem) {
				return true;
			}
		}
		return false;
	}

	private static ItemStack freshPiece(ServerPlayer player, EquipmentSlot slot) {
		ItemStack piece = new ItemStack(pieceFor(slot));
		PowerEquipmentLock.bind(player, piece);
		return piece;
	}

	private static GreenLanternArmorItem pieceFor(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> GreenLanternItems.SUIT_HELMET;
			case CHEST -> GreenLanternItems.SUIT_CHESTPLATE;
			case LEGS -> GreenLanternItems.SUIT_LEGGINGS;
			case FEET -> GreenLanternItems.SUIT_BOOTS;
			default -> GreenLanternItems.SUIT_CHESTPLATE;
		};
	}
}
