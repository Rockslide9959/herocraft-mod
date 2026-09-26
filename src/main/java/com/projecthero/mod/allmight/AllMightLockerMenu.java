package com.projecthero.mod.allmight;

import com.projecthero.mod.allmight.item.AllMightItems;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The All Might costume locker (v0.12.38): exactly two slots -- the costume (chest) and the trousers (legs) -- above the
 * player's inventory. Each slot only takes its own piece. The client half is {@code AllMightLockerScreen}.
 */
public class AllMightLockerMenu extends AbstractContainerMenu {
	public static final int SLOT_X_CHEST = 20;
	public static final int SLOT_X_LEGS = 100;
	public static final int SLOT_Y = 20;

	private final Container container;

	/** Client-side constructor (the server sends the two stacks through the normal slot sync). */
	public AllMightLockerMenu(int syncId, Inventory inventory, Integer ignored) {
		this(syncId, inventory, new SimpleContainer(AllMightSuit.SLOTS.length));
	}

	public AllMightLockerMenu(int syncId, Inventory inventory, Container container) {
		super(AllMightItems.LOCKER_MENU, syncId);
		this.container = container;
		container.startOpen(inventory.player);
		addSlot(new PieceSlot(container, 0, SLOT_X_CHEST, SLOT_Y, EquipmentSlot.CHEST));
		addSlot(new PieceSlot(container, 1, SLOT_X_LEGS, SLOT_Y, EquipmentSlot.LEGS));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 50 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, 8 + col * 18, 108));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			moved = stack.copy();
			int lockerSlots = AllMightSuit.SLOTS.length;
			if (index < lockerSlots) {
				if (!this.moveItemStackTo(stack, lockerSlots, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, lockerSlots, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return moved;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
		if (player instanceof net.minecraft.server.level.ServerPlayer sp && AllMight.isFullPower(sp)) {
			AllMightSuit.equip(sp); // closing the locker in the Power Form puts the costume straight on
		}
	}

	/** A locker slot that only accepts the matching costume piece, one at a time. */
	private static final class PieceSlot extends Slot {
		private final EquipmentSlot wanted;

		PieceSlot(Container container, int index, int x, int y, EquipmentSlot wanted) {
			super(container, index, x, y);
			this.wanted = wanted;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof com.projecthero.mod.allmight.item.AllMightArmorItem a && a.getType().getSlot() == wanted;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
