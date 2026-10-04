package com.projecthero.mod.ironman.sorter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * v0.14.16: the Stark Sorting Station menu -- the 6x9 store, the player inventory, and the Sort button (menu
 * button id {@value #BUTTON_SORT}, sent with vanilla's container-button packet) and, since v0.14.20, the Tidy
 * button ({@value #BUTTON_TIDY}). v0.14.21 adds the supply column (3 sign slots, 3 chest slots) to the right of
 * the store. Data slots sync the job (running, stacks filed, stacks in the job, containers in range, whether it
 * is a Tidy) and what the room needs (see {@link SortingStationBlockEntity#DATA_COUNT}).
 */
public class SortingStationMenu extends AbstractContainerMenu {
	public static final int BUTTON_SORT = 0;
	/** v0.14.20: re-sort the chests themselves (see {@link SortingStationBlockEntity#startTidy}). */
	public static final int BUTTON_TIDY = 1;
	public static final int ROWS = 6;
	/** Y of the first player-inventory row (the screen leaves a status band between the store and it). */
	public static final int INV_Y = 180;
	/** v0.14.21: the supply column -- x of its first slot, and the y of the sign row and the chest row. */
	public static final int SUPPLY_X = 184;
	public static final int SIGN_Y = 30;
	public static final int CHEST_Y = 66;
	/** Menu slot index of the first supply slot (after the store). */
	public static final int SUPPLY_START = SortingStationBlockEntity.SIZE;
	private static final int PLAYER_START = SUPPLY_START + SortingStationBlockEntity.SUPPLY_SLOTS;

	private final Container container;
	private final ContainerData data;
	private final ContainerLevelAccess access;

	/** Server side. */
	public SortingStationMenu(int syncId, Inventory playerInv, SortingStationBlockEntity be, ContainerData data) {
		this(syncId, playerInv, be, be.supply(), data, ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()));
	}

	/** Client side (extended screen handler, given the block pos). */
	public SortingStationMenu(int syncId, Inventory playerInv, BlockPos pos) {
		this(syncId, playerInv, resolve(playerInv, pos), new SimpleContainer(SortingStationBlockEntity.SUPPLY_SLOTS),
				new SimpleContainerData(SortingStationBlockEntity.DATA_COUNT),
				ContainerLevelAccess.create(playerInv.player.level(), pos));
	}

	private SortingStationMenu(int syncId, Inventory playerInv, Container container, Container supply, ContainerData data,
			ContainerLevelAccess access) {
		super(StarkSorter.STATION_MENU, syncId);
		this.container = container;
		this.data = data;
		this.access = access;
		checkContainerSize(container, SortingStationBlockEntity.SIZE);
		container.startOpen(playerInv.player);

		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
			}
		}
		for (int i = 0; i < SortingStationBlockEntity.SUPPLY_SLOTS; i++) {
			boolean sign = i < SortingStationBlockEntity.SIGN_SLOTS;
			int col = i % SortingStationBlockEntity.SIGN_SLOTS;
			addSlot(new SupplySlot(supply, i, SUPPLY_X + col * 18, sign ? SIGN_Y : CHEST_Y, sign));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, INV_Y + 58));
		}
		addDataSlots(data);
	}

	private static Container resolve(Inventory playerInv, BlockPos pos) {
		BlockEntity be = playerInv.player.level().getBlockEntity(pos);
		return be instanceof SortingStationBlockEntity s ? s : new SimpleContainer(SortingStationBlockEntity.SIZE);
	}

	public boolean running() {
		return data.get(0) != 0;
	}

	public int done() {
		return data.get(1);
	}

	public int total() {
		return data.get(2);
	}

	public int containers() {
		return data.get(3);
	}

	/** The current (or last) job was a Tidy. */
	public boolean tidyMode() {
		return data.get(4) != 0;
	}

	/** v0.14.21: chests the player still has to add to the supply. */
	public int chestsShort() {
		return data.get(5);
	}

	/** v0.14.21: signs the player still has to add to the supply. */
	public int signsShort() {
		return data.get(6);
	}

	/** v0.14.21: unlabelled chests (plus chests still to be placed) that need a sign. */
	public int signsNeeded() {
		return data.get(7);
	}

	/** v0.14.21: new chests the room needs for overflow. */
	public int chestsNeeded() {
		return data.get(8);
	}

	/** v0.14.21: unlabelled chests with no free face for a sign. */
	public int noSignFace() {
		return data.get(9);
	}

	/** v0.14.21: a supply slot -- signs only, or chests only. */
	public static class SupplySlot extends Slot {
		private final boolean sign;

		public SupplySlot(Container container, int index, int x, int y, boolean sign) {
			super(container, index, x, y);
			this.sign = sign;
		}

		public boolean isSignSlot() {
			return sign;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return sign ? SorterSupply.isSign(stack) : SorterSupply.isChest(stack);
		}
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if ((id == BUTTON_SORT || id == BUTTON_TIDY) && container instanceof SortingStationBlockEntity be
				&& player instanceof ServerPlayer sp) {
			var status = id == BUTTON_TIDY ? be.startTidy(sp) : be.startSort(sp);
			if (!status.getString().isEmpty()) {
				sp.sendSystemMessage(status);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, StarkSorter.STATION);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack result = stack.copy();
		int store = SortingStationBlockEntity.SIZE;
		if (index < PLAYER_START) {
			// the store or the supply: into the player's inventory
			if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// signs and chests go to the supply first, then (whatever is left) into the store
			if (SorterSupply.isSign(stack)) {
				moveItemStackTo(stack, SUPPLY_START, SUPPLY_START + SortingStationBlockEntity.SIGN_SLOTS, false);
			} else if (SorterSupply.isChest(stack)) {
				moveItemStackTo(stack, SUPPLY_START + SortingStationBlockEntity.SIGN_SLOTS, PLAYER_START, false);
			}
			if (!stack.isEmpty() && !moveItemStackTo(stack, 0, store, false) && stack.getCount() == result.getCount()) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}
}
