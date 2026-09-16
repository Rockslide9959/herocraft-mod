package com.projecthero.mod.greenlantern.item;

import com.projecthero.mod.armor.PowerEquipmentLock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Puts the four synthesised {@link GreenLanternArmorItem} pieces on the player when they Suit Up, and
 * takes them off on suit-down/death/logout/power loss. Cloned from {@code MaxSteelSuitArmor}: real
 * armour is pushed into the inventory (dropped only if full) rather than stowed, and every piece is
 * {@link PowerEquipmentLock#bind Curse-of-Binding-locked} so it cannot be pulled out of its slot.
 */
public final class GreenLanternSuitArmor {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

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
	}

	public static void reequipMissing(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, freshPiece(player, slot));
			}
		}
	}

	public static void strip(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof GreenLanternArmorItem) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
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
