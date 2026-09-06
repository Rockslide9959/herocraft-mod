package com.projecthero.mod.symbiote;

import com.projecthero.mod.armor.PowerEquipmentLock;
import com.projecthero.mod.spider.item.SpiderItems;
import com.projecthero.mod.spider.item.SymbioteArmorItem;
import com.projecthero.mod.symbiote.item.SymbioteHostArmorItem;
import com.projecthero.mod.symbiote.item.SymbioteHostItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The four synthesised Symbiote armour pieces -- one of two variants, chosen live off
 * {@link SymbioteHostType#of}: {@link SymbioteArmorItem} (Black Suit Spider-Man, GeckoLib) or
 * {@link SymbioteHostArmorItem} (Normal host, plain vanilla armour). Putting them on, capturing what
 * they displaced, handing that back, and keeping the suit honest tick to tick. Modelled on
 * {@link com.projecthero.mod.maxsteel.MaxSteelSuitArmor}, with two additions:
 *
 * <ul>
 *   <li>every synthesised piece is {@link PowerEquipmentLock#bind bound} with Curse of Binding, so it
 *       cannot be pulled out of its slot by the player -- see that class for why;</li>
 *   <li>{@link #equipAndCapture} hands back whatever real armour it displaced, so
 *       {@link Symbiote#toggle} can stow it and give it back on retract, rather
 *       than the "pushed to inventory, re-equip manually" simplification Max Steel uses.</li>
 * </ul>
 */
public final class SymbioteSuit {
	static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private SymbioteSuit() {
	}

	/** True if {@code item} is either variant of the Symbiote's own synthesised armour. */
	private static boolean isSymbiotePiece(Item item) {
		return item instanceof SymbioteArmorItem || item instanceof SymbioteHostArmorItem;
	}

	/**
	 * Put the suit on. Returns the real armour each slot held, in {@link #SLOTS} order (an empty stack
	 * for a slot that already held a Symbiote piece, or held nothing) -- the caller is responsible for
	 * remembering it.
	 */
	public static ItemStack[] equipAndCapture(ServerPlayer player) {
		ItemStack[] displaced = new ItemStack[SLOTS.length];
		for (int i = 0; i < SLOTS.length; i++) {
			EquipmentSlot slot = SLOTS[i];
			ItemStack current = player.getItemBySlot(slot);
			if (isSymbiotePiece(current.getItem())) {
				displaced[i] = ItemStack.EMPTY;
				continue;
			}
			displaced[i] = current.copy();
			player.setItemSlot(slot, freshPiece(player, slot));
		}
		return displaced;
	}

	/**
	 * True if every worn Symbiote piece matches the player's CURRENT host type -- false if, e.g., a
	 * bonded Spider-Man wearing the black GeckoLib suit lost the Spider-Man power and is now supposed
	 * to be wearing the plain Normal-host armour instead. {@link Symbiote#tick} uses this to hot-swap
	 * the suit the instant the host type changes while it is worn.
	 */
	public static boolean wearingCorrectVariant(Player player) {
		SymbioteHostType type = SymbioteHostType.of(player);
		for (EquipmentSlot slot : SLOTS) {
			Item item = player.getItemBySlot(slot).getItem();
			if (!isSymbiotePiece(item)) {
				continue;
			}
			boolean matches = type == SymbioteHostType.SPIDER_MAN
					? item instanceof SymbioteArmorItem
					: item instanceof SymbioteHostArmorItem;
			if (!matches) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Fill any of the four slots that is <em>empty</em> with the matching piece. Never overwrites a
	 * slot that holds something else -- a foreign item reaching an armour slot while suited should
	 * never happen (the existing pieces are curse-locked), but if it somehow does, this leaves it
	 * alone rather than silently eating it. Called every tick while the Symbiote is active as a cheap
	 * defence-in-depth backstop.
	 */
	public static void reequipMissing(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, freshPiece(player, slot));
			}
		}
	}

	/**
	 * Give {@code stowed} (in {@link #SLOTS} order) back to the player. Called only after
	 * {@link #strip}, so every slot is normally empty; a slot that unexpectedly already holds something
	 * else gets the stowed item pushed to the inventory instead of clobbering it.
	 */
	public static void restoreCaptured(ServerPlayer player, ItemStack[] stowed) {
		if (stowed == null) {
			return;
		}
		for (int i = 0; i < SLOTS.length && i < stowed.length; i++) {
			ItemStack item = stowed[i];
			if (item == null || item.isEmpty()) {
				continue;
			}
			EquipmentSlot slot = SLOTS[i];
			if (player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, item);
			} else if (!player.getInventory().add(item)) {
				player.drop(item, false);
			}
		}
	}

	/** Remove any Symbiote piece (either variant) from the four armour slots. */
	public static void strip(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (isSymbiotePiece(player.getItemBySlot(slot).getItem())) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * Remove every Symbiote piece (either variant) from anywhere on the player -- armour slots,
	 * hotbar, main inventory, offhand. Used when the player is no longer eligible (Symbiote off, no
	 * bond, an outsider who somehow received one). Curse of Binding should make this a no-op in
	 * practice; kept as the backstop it always was.
	 */
	public static void stripEverywhere(Player player) {
		strip(player);
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isSymbiotePiece(inv.getItem(i).getItem())) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * Delete any Symbiote piece (either variant) that is on the player but NOT in one of the four
	 * armour slots. Curse of Binding should make this unreachable in ordinary play now; kept as a
	 * cheap backstop for anything that bypasses normal slot interaction.
	 */
	public static boolean deleteLoose(Player player) {
		var inv = player.getInventory();
		boolean removed = false;
		for (int i = 0; i < inv.items.size(); i++) {
			if (isSymbiotePiece(inv.items.get(i).getItem())) {
				inv.items.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (isSymbiotePiece(inv.offhand.get(i).getItem())) {
				inv.offhand.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		return removed;
	}

	public static boolean wearing(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (isSymbiotePiece(player.getItemBySlot(slot).getItem())) {
				return true;
			}
		}
		return false;
	}

	public static boolean wearingFullSet(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (!isSymbiotePiece(player.getItemBySlot(slot).getItem())) {
				return false;
			}
		}
		return true;
	}

	private static ItemStack freshPiece(ServerPlayer player, EquipmentSlot slot) {
		ItemStack piece = new ItemStack(pieceFor(player, slot));
		PowerEquipmentLock.bind(player, piece);
		return piece;
	}

	private static ArmorItem pieceFor(ServerPlayer player, EquipmentSlot slot) {
		if (SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN) {
			return switch (slot) {
				case HEAD -> SpiderItems.SYMBIOTE_HELMET;
				case CHEST -> SpiderItems.SYMBIOTE_CHESTPLATE;
				case LEGS -> SpiderItems.SYMBIOTE_LEGGINGS;
				case FEET -> SpiderItems.SYMBIOTE_BOOTS;
				default -> SpiderItems.SYMBIOTE_CHESTPLATE;
			};
		}
		return switch (slot) {
			case HEAD -> SymbioteHostItems.HELMET;
			case CHEST -> SymbioteHostItems.CHESTPLATE;
			case LEGS -> SymbioteHostItems.LEGGINGS;
			case FEET -> SymbioteHostItems.BOOTS;
			default -> SymbioteHostItems.CHESTPLATE;
		};
	}
}
