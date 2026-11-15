package com.projecthero.mod.powerarmour.block;

import com.projecthero.mod.powerarmour.PowerArmourMod;
import com.projecthero.mod.powerarmour.item.PowerCoreItem;
import com.projecthero.mod.powerarmour.menu.CoreChargerMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5 Power Armour: the Core Charger's insides -- slot 0 the core, slot 1 the fuel. Fuel burns only while a core that is not
 * full sits in it; every burning tick adds {@link #CHARGE_PER_TICK}. Furnace fuels burn for their furnace time; redstone dust
 * for {@link #REDSTONE_TICKS}, a redstone block nine times that.
 */
public class CoreChargerBlockEntity extends BlockEntity implements Container, MenuProvider {
	public static final float CHARGE_PER_TICK = 2f;
	public static final int REDSTONE_TICKS = 200;

	private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
	private int burn;
	private int burnMax;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			ItemStack core = items.get(0);
			return switch (index) {
				case 0 -> burn;
				case 1 -> burnMax;
				case 2 -> Math.round(PowerCoreItem.charge(core));
				case 3 -> core.getItem() instanceof PowerCoreItem c ? Math.round(c.type().capacity) : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			if (index == 0) {
				burn = value;
			} else if (index == 1) {
				burnMax = value;
			}
		}

		@Override
		public int getCount() {
			return 4;
		}
	};

	public CoreChargerBlockEntity(BlockPos pos, BlockState state) {
		super(PowerArmourMod.CHARGER_BE, pos, state);
	}

	public static int fuelTicks(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (stack.is(Items.REDSTONE)) {
			return REDSTONE_TICKS;
		}
		if (stack.is(Items.REDSTONE_BLOCK)) {
			return REDSTONE_TICKS * 9;
		}
		Integer t = AbstractFurnaceBlockEntity.getFuel().get(stack.getItem());
		return t == null ? 0 : t;
	}

	private boolean coreWantsCharge() {
		ItemStack core = items.get(0);
		return core.getItem() instanceof PowerCoreItem c && PowerCoreItem.charge(core) < c.type().capacity - 0.01f;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CoreChargerBlockEntity be) {
		boolean wasLit = be.burn > 0;
		boolean changed = false;
		boolean wants = be.coreWantsCharge();
		if (be.burn > 0) {
			be.burn--;
			if (wants) {
				ItemStack core = be.items.get(0);
				PowerCoreItem.setCharge(core, PowerCoreItem.charge(core) + CHARGE_PER_TICK);
			}
			changed = true;
		}
		if (be.burn <= 0 && wants) {
			ItemStack fuel = be.items.get(1);
			int t = fuelTicks(fuel);
			if (t > 0) {
				be.burn = be.burnMax = t;
				ItemStack remainder = fuel.getItem().getCraftingRemainingItem() == null ? ItemStack.EMPTY
						: new ItemStack(fuel.getItem().getCraftingRemainingItem());
				fuel.shrink(1);
				if (fuel.isEmpty() && !remainder.isEmpty()) {
					be.items.set(1, remainder);
				}
				changed = true;
			}
		}
		boolean lit = be.burn > 0;
		if (wasLit != lit) {
			level.setBlock(pos, state.setValue(CoreChargerBlock.LIT, lit), 3);
		}
		if (changed) {
			be.setChanged();
		}
	}

	// ------------------------------------------------------------------ container

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack s = ContainerHelper.removeItem(items, slot, amount);
		setChanged();
		return s;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == 0 ? stack.getItem() instanceof PowerCoreItem : fuelTicks(stack) > 0;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	// ------------------------------------------------------------------ menu + save

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.projecthero.core_charger");
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new CoreChargerMenu(id, inventory, this, data);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.clear();
		ContainerHelper.loadAllItems(tag, items, registries);
		burn = tag.getInt("Burn");
		burnMax = tag.getInt("BurnMax");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		tag.putInt("Burn", burn);
		tag.putInt("BurnMax", burnMax);
	}
}
