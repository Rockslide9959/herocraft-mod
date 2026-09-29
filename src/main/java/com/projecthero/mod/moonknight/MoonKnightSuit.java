package com.projecthero.mod.moonknight;

import java.util.List;

import com.projecthero.mod.armor.PowerEquipmentLock;
import com.projecthero.mod.moonknight.data.MoonKnightState;
import com.projecthero.mod.moonknight.item.MoonKnightArmorItem;
import com.projecthero.mod.moonknight.item.MoonKnightItems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The four synthesised Moon Knight suit pieces: putting them on while stowing whatever real armour they displaced
 * (in {@link MoonKnightState#storedArmor}), handing that back exactly as it was on un-transforming, and keeping the
 * suit honest. The pieces are Curse-of-Binding locked and unbreakable, so they cannot be taken out of their slots; any
 * copy that turns up anywhere else (the inventory, a dropped item entity) is deleted on sight. The player's own gear is
 * never deleted or dropped by any of this.
 */
public final class MoonKnightSuit {
	static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private MoonKnightSuit() {
	}

	public static void initialize() {
		// a suit piece can never exist as a loose item in the world
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && isPiece(item.getItem().getItem())) {
				item.discard();
			}
		});
	}

	public static boolean isPiece(Item item) {
		return item instanceof MoonKnightArmorItem;
	}

	/** Equip the suit and return the armour it displaced (SLOTS order; empty where a slot was empty or already suit). */
	static ItemStack[] equipAndCapture(ServerPlayer player) {
		ItemStack[] displaced = new ItemStack[SLOTS.length];
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack current = player.getItemBySlot(SLOTS[i]);
			if (isPiece(current.getItem())) {
				displaced[i] = ItemStack.EMPTY;
				continue;
			}
			displaced[i] = current.copy();
			player.setItemSlot(SLOTS[i], freshPiece(player, SLOTS[i]));
		}
		return displaced;
	}

	/** Suit up: equip, and remember what the player was wearing. */
	static void suitUp(ServerPlayer player) {
		ItemStack[] displaced = equipAndCapture(player);
		MoonKnightState c = MoonKnight.state(player).copy();
		// never overwrite armour already stowed (a suit-up can't happen twice without a suit-down, but be safe)
		if (c.storedArmor.nonEmptyStream().findAny().isEmpty()) {
			c.storedArmor = ItemContainerContents.fromItems(List.of(displaced));
		}
		MoonKnight.save(player, c);
	}

	/** Suit down: strip every piece and hand back the stowed armour. */
	static void suitDown(ServerPlayer player) {
		strip(player);
		MoonKnightState s = MoonKnight.state(player);
		NonNullList<ItemStack> stowed = NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
		s.storedArmor.copyInto(stowed);
		MoonKnightState c = s.copy();
		c.storedArmor = ItemContainerContents.EMPTY;
		MoonKnight.save(player, c);
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack item = stowed.get(i);
			if (item.isEmpty()) {
				continue;
			}
			if (player.getItemBySlot(SLOTS[i]).isEmpty()) {
				player.setItemSlot(SLOTS[i], item);
			} else if (!player.getInventory().add(item)) {
				player.drop(item, false);
			}
		}
	}

	static void strip(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (isPiece(player.getItemBySlot(slot).getItem())) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	/** Refill an empty suit slot (while transformed). Never overwrites anything. */
	static void reequipMissing(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, freshPiece(player, slot));
			}
		}
	}

	/** Delete any piece that is not in an armour slot (inventory, offhand, cursor). */
	static boolean deleteLoose(Player player) {
		boolean removed = false;
		var inv = player.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			if (isPiece(inv.items.get(i).getItem())) {
				inv.items.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (isPiece(inv.offhand.get(i).getItem())) {
				inv.offhand.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		if (isPiece(player.containerMenu.getCarried().getItem())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			removed = true;
		}
		return removed;
	}

	public static boolean wearing(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (isPiece(player.getItemBySlot(slot).getItem())) {
				return true;
			}
		}
		return false;
	}

	/** Once a second: a transformed Moon Knight keeps a full, honest suit; anyone else keeps none of it. */
	static void audit(ServerPlayer player, boolean transformed) {
		if (transformed) {
			reequipMissing(player);
			deleteLoose(player);
		} else if (wearing(player) || deleteLoose(player)) {
			strip(player);
		}
	}

	private static ItemStack freshPiece(ServerPlayer player, EquipmentSlot slot) {
		ItemStack piece = new ItemStack(switch (slot) {
			case HEAD -> MoonKnightItems.HELMET;
			case LEGS -> MoonKnightItems.LEGGINGS;
			case FEET -> MoonKnightItems.BOOTS;
			default -> MoonKnightItems.CHESTPLATE;
		});
		piece.set(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
		PowerEquipmentLock.bind(player, piece);
		piece.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
		return piece;
	}
}
