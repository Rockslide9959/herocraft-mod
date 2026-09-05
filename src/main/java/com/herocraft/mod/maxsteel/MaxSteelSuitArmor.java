package com.herocraft.mod.maxsteel;

import com.herocraft.mod.armor.PowerEquipmentLock;
import com.herocraft.mod.maxsteel.item.MaxSteelArmorItem;
import com.herocraft.mod.maxsteel.item.MaxSteelItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Puts the four synthesised {@link MaxSteelArmorItem} pieces on the player when they Go Turbo, and
 * takes them off again on suit-down / death / logout / power loss. The pieces are synthetic, so
 * removing them is a plain delete -- nothing to lose. Any <em>real</em> armour the player was wearing
 * is pushed into their inventory (dropped only if the pack is completely full) with a message, rather
 * than stowed in a persisted structure: no restore bookkeeping, no way to lose an item across a
 * restart. The player re-equips it themselves after suiting down.
 *
 * <p>Every synthesised piece is {@link PowerEquipmentLock#bind bound} with Curse of Binding so the
 * player cannot pull it out of its slot -- see that class for why. {@link #strip} (and death, which
 * strips before vanilla's own equipment-drop code runs) is still the only way it leaves the slot; a
 * Creative-mode player can still remove it normally.
 *
 * <p>Idempotent and safe on a fresh player entity -- {@link #strip} just walks the four armour slots
 * and clears any that hold a Max Steel piece.
 */
public final class MaxSteelSuitArmor {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private MaxSteelSuitArmor() {
	}

	public static void equip(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack current = player.getItemBySlot(slot);
			if (current.getItem() instanceof MaxSteelArmorItem) {
				continue; // already wearing this piece
			}
			if (!current.isEmpty()) {
				ItemStack displaced = current.copy();
				player.setItemSlot(slot, ItemStack.EMPTY);
				if (!player.getInventory().add(displaced)) {
					player.drop(displaced, false);
				}
				player.displayClientMessage(Component.translatable(
						"message.herocraft.max_steel.armour_stowed", displaced.getHoverName()), true);
			}
			player.setItemSlot(slot, freshPiece(player, slot));
		}
	}

	/**
	 * Fill any of the four slots that is <em>empty</em> with the matching piece, without touching a
	 * slot that holds something else. Called every tick while transformed as a cheap backstop -- Curse
	 * of Binding should make a slot going empty unreachable in ordinary play now.
	 */
	public static void reequipMissing(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, freshPiece(player, slot));
			}
		}
	}

	public static void strip(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof MaxSteelArmorItem) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * Delete any Max Steel piece that is on the player but NOT in one of the four armour slots. Curse
	 * of Binding should make this unreachable in ordinary play; kept as a cheap backstop, the same
	 * discipline the Symbiote suit uses.
	 */
	public static boolean deleteLoose(Player player) {
		var inv = player.getInventory();
		boolean removed = false;
		for (int i = 0; i < inv.items.size(); i++) {
			if (inv.items.get(i).getItem() instanceof MaxSteelArmorItem) {
				inv.items.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (inv.offhand.get(i).getItem() instanceof MaxSteelArmorItem) {
				inv.offhand.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		return removed;
	}

	public static boolean wearing(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof MaxSteelArmorItem) {
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

	private static MaxSteelArmorItem pieceFor(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> MaxSteelItems.SUIT_HELMET;
			case CHEST -> MaxSteelItems.SUIT_CHESTPLATE;
			case LEGS -> MaxSteelItems.SUIT_LEGGINGS;
			case FEET -> MaxSteelItems.SUIT_BOOTS;
			default -> MaxSteelItems.SUIT_CHESTPLATE;
		};
	}
}
