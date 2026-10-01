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
 * button id {@value #BUTTON_SORT}, sent with vanilla's container-button packet). Four data slots sync the job:
 * running, stacks filed, stacks in the job, containers in range.
 */
public class SortingStationMenu extends AbstractContainerMenu {
	public static final int BUTTON_SORT = 0;
	public static final int ROWS = 6;
	/** Y of the first player-inventory row (the screen leaves a status band between the store and it). */
	public static final int INV_Y = 168;

	private final Container container;
	private final ContainerData data;
	private final ContainerLevelAccess access;

	/** Server side. */
	public SortingStationMenu(int syncId, Inventory playerInv, SortingStationBlockEntity be, ContainerData data) {
		this(syncId, playerInv, be, data, ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()));
	}

	/** Client side (extended screen handler, given the block pos). */
	public SortingStationMenu(int syncId, Inventory playerInv, BlockPos pos) {
		this(syncId, playerInv, resolve(playerInv, pos), new SimpleContainerData(4),
				ContainerLevelAccess.create(playerInv.player.level(), pos));
	}

	private SortingStationMenu(int syncId, Inventory playerInv, Container container, ContainerData data,
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

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == BUTTON_SORT && container instanceof SortingStationBlockEntity be && player instanceof ServerPlayer sp) {
			var status = be.startSort(sp);
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
		if (index < store) {
			if (!moveItemStackTo(stack, store, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 0, store, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}
}
