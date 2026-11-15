package com.projecthero.mod.powerarmour.menu;

import com.projecthero.mod.powerarmour.PowerArmourMod;
import com.projecthero.mod.powerarmour.block.CoreChargerBlockEntity;
import com.projecthero.mod.powerarmour.item.PowerCoreItem;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** v0.17.5 Power Armour: the Core Charger's menu (core above, fuel below, a charge bar and a burn flame between). */
public class CoreChargerMenu extends AbstractContainerMenu {
	public static final int CORE_X = 80, CORE_Y = 17, FUEL_X = 80, FUEL_Y = 53;

	private final Container container;
	private final ContainerData data;

	public CoreChargerMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(2), new SimpleContainerData(4));
	}

	public CoreChargerMenu(int id, Inventory inventory, Container container, ContainerData data) {
		super(PowerArmourMod.CHARGER_MENU, id);
		this.container = container;
		this.data = data;
		addSlot(new Slot(container, 0, CORE_X, CORE_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof PowerCoreItem;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(container, 1, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return CoreChargerBlockEntity.fuelTicks(stack) > 0;
			}
		});
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
			}
		}
		for (int c = 0; c < 9; c++) {
			addSlot(new Slot(inventory, c, 8 + c * 18, 142));
		}
		addDataSlots(data);
	}

	public float burnFraction() {
		int max = data.get(1);
		return max <= 0 ? 0f : data.get(0) / (float) max;
	}

	public int charge() {
		return data.get(2);
	}

	public int capacity() {
		return data.get(3);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < 2) {
			if (!moveItemStackTo(stack, 2, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof PowerCoreItem) {
			if (!moveItemStackTo(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (CoreChargerBlockEntity.fuelTicks(stack) > 0) {
			if (!moveItemStackTo(stack, 1, 2, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}
}
