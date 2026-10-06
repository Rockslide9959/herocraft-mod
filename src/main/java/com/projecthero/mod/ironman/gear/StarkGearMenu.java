package com.projecthero.mod.ironman.gear;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.1: the Stark Gear screen's menu -- one Stark Gear slot (the glasses) plus the player's inventory and hotbar.
 *
 * <p>On the server the gear slot reads and writes the {@link com.projecthero.mod.attachment.ModAttachments#STARK_GEAR}
 * attachment directly ({@link GearContainer}), so the item only ever lives in one place: the slot <em>is</em> the
 * attachment. The client mirrors it in a plain one-slot container that the vanilla menu sync fills.
 */
public class StarkGearMenu extends AbstractContainerMenu {
	public static final int GEAR_SLOT = 0;
	private static final int INV_START = 1;
	private static final int INV_END = INV_START + 36;

	private final Player player;

	/** Client (and the menu-type factory). */
	public StarkGearMenu(int syncId, Inventory playerInv) {
		this(syncId, playerInv, playerInv.player.level().isClientSide ? new SimpleContainer(1) : new GearContainer(playerInv.player));
	}

	private StarkGearMenu(int syncId, Inventory playerInv, Container gear) {
		super(StarkGear.MENU, syncId);
		this.player = playerInv.player;
		addSlot(new Slot(gear, 0, StarkGearLayout.SLOT_X, StarkGearLayout.SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return StarkGear.isGear(stack); // v0.15.4: the glasses or the Colantotte Bracelets
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, StarkGearLayout.INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, StarkGearLayout.HOTBAR_Y));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player who, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index == GEAR_SLOT) {
			ItemStack moving = stack.copy(); // never shrink the attachment's own stack in place
			if (!moveItemStackTo(moving, INV_START, INV_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.setByPlayer(moving.isEmpty() ? ItemStack.EMPTY : moving);
		} else {
			if (!StarkGear.isGear(stack) || slots.get(GEAR_SLOT).hasItem()
					|| !moveItemStackTo(stack, GEAR_SLOT, GEAR_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player who) {
		return who.isAlive() && who == player;
	}

	/** The server-side gear slot: a one-item view of the player's Stark Gear attachment. */
	static final class GearContainer implements Container {
		private final Player player;

		GearContainer(Player player) {
			this.player = player;
		}

		@Override
		public int getContainerSize() {
			return 1;
		}

		@Override
		public boolean isEmpty() {
			return StarkGear.glasses(player).isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return slot == 0 ? StarkGear.glasses(player) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			if (slot != 0 || amount <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack out = StarkGear.glasses(player).copy();
			StarkGear.setGlasses(player, ItemStack.EMPTY);
			return out;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return removeItem(slot, 1);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (slot == 0) {
				StarkGear.setGlasses(player, stack);
			}
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player who) {
			return who == player;
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return StarkGear.isGear(stack);
		}

		@Override
		public void clearContent() {
			StarkGear.setGlasses(player, ItemStack.EMPTY);
		}
	}
}
