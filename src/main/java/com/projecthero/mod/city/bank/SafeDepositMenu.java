package com.projecthero.mod.city.bank;

import com.projecthero.mod.city.CityBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.2 City part B: the Safe Deposit screen -- a 3-row vault that only takes emeralds and emerald blocks, over the
 * player's own account ({@link BankAccounts}). Data slots: 0 = the vault's emerald value, 1 = interest earned so far,
 * 2 = the interest cap.
 */
public class SafeDepositMenu extends AbstractContainerMenu {
	public static final int ROWS = 3;
	private final Container vault;
	private final ContainerData data;
	private final BlockPos pos;

	/** Client side. */
	public SafeDepositMenu(int id, Inventory inv) {
		this(id, inv, new SimpleContainer(BankAccounts.SLOTS), new SimpleContainerData(3), null);
	}

	public SafeDepositMenu(int id, Inventory inv, Container vault, ContainerData data, BlockPos pos) {
		super(CityBlocks.SAFE_DEPOSIT_MENU, id);
		this.vault = vault;
		this.data = data;
		this.pos = pos;
		vault.startOpen(inv.player);
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < 9; c++) {
				addSlot(new Slot(vault, c + r * 9, 8 + c * 18, 18 + r * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return BankAccounts.accepts(stack);
					}
				});
			}
		}
		int offset = (ROWS - 4) * 18;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 103 + r * 18 + offset));
			}
		}
		for (int c = 0; c < 9; c++) {
			addSlot(new Slot(inv, c, 8 + c * 18, 161 + offset));
		}
		addDataSlots(data);
	}

	public int value() {
		return data.get(0);
	}

	public int interestPaid() {
		return data.get(1);
	}

	public int interestCap() {
		return data.get(2);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack out = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			out = stack.copy();
			int vaultSlots = ROWS * 9;
			if (index < vaultSlots) {
				if (!moveItemStackTo(stack, vaultSlots, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!BankAccounts.accepts(stack) || !moveItemStackTo(stack, 0, vaultSlots, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return out;
	}

	@Override
	public boolean stillValid(Player player) {
		return pos == null || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		vault.stopOpen(player);
	}
}
