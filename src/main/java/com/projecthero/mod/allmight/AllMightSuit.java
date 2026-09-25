package com.projecthero.mod.allmight;

import com.projecthero.mod.allmight.item.AllMightArmorItem;
import com.projecthero.mod.allmight.item.AllMightItems;
import com.projecthero.mod.armor.PowerEquipmentLock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Puts the four synthesised {@link AllMightArmorItem} pieces (hair-and-face, costume, trousers, boots) on an All Might
 * and takes them off on revoke/death (v0.12.33). Cloned from {@code GreenLanternSuitArmor}: real armour is pushed into
 * the inventory (dropped only if it is full) rather than stowed, and every piece is
 * {@link PowerEquipmentLock Curse-of-Binding-locked}. The costume is the power's body -- the contained and the full-power
 * forms are the same four pieces, drawn differently by the wearer's form (see {@link AllMightArmorItem#armorSetId}).
 */
public final class AllMightSuit {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private AllMightSuit() {
	}

	public static void equip(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack current = player.getItemBySlot(slot);
			if (current.getItem() instanceof AllMightArmorItem) {
				continue;
			}
			if (!current.isEmpty()) {
				ItemStack displaced = current.copy();
				player.setItemSlot(slot, ItemStack.EMPTY);
				if (!player.getInventory().add(displaced)) {
					player.drop(displaced, false);
				}
				player.displayClientMessage(Component.translatable("message.projecthero.all_might.armour_stowed", displaced.getHoverName()), true);
			}
			player.setItemSlot(slot, freshPiece(player, slot));
		}
	}

	/** Fills any empty slot (after a respawn or a swap) without stowing anything. */
	public static void reequipMissing(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, freshPiece(player, slot));
			}
		}
	}

	public static void strip(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof AllMightArmorItem) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	/** Deletes any stray piece in the inventory (a suit piece must never become a real, storable item). */
	public static boolean deleteLoose(Player player) {
		var inv = player.getInventory();
		boolean removed = false;
		for (int i = 0; i < inv.items.size(); i++) {
			if (inv.items.get(i).getItem() instanceof AllMightArmorItem) {
				inv.items.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (inv.offhand.get(i).getItem() instanceof AllMightArmorItem) {
				inv.offhand.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		return removed;
	}

	public static boolean wearing(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof AllMightArmorItem) {
				return true;
			}
		}
		return false;
	}

	private static ItemStack freshPiece(ServerPlayer player, EquipmentSlot slot) {
		ItemStack piece = new ItemStack(switch (slot) {
			case HEAD -> AllMightItems.HELMET;
			case CHEST -> AllMightItems.CHESTPLATE;
			case LEGS -> AllMightItems.LEGGINGS;
			default -> AllMightItems.BOOTS;
		});
		PowerEquipmentLock.bind(player, piece);
		return piece;
	}
}
