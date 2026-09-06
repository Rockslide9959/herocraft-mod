package com.projecthero.mod.ironman.fabricator;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Stark Fabricator's state: 9 component-input slots, 1 output slot, 1 blueprint slot, an internal
 * Stark-energy buffer, and a fabrication timer (spec sections 8-11).
 *
 * <p>Operating restriction (spec section 6): the Fabricator only makes progress while a player with
 * the Tony Stark power is within {@value #OPERATOR_RADIUS} blocks. That is what stops a player without
 * the power from driving it with hoppers or other automation.
 */
public class StarkFabricatorBlockEntity extends BlockEntity implements Container, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SIZE = 11;
	public static final int OUTPUT_SLOT = 9;
	public static final int BLUEPRINT_SLOT = 10;
	private static final double OPERATOR_RADIUS = 6.0;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int energy;
	private int progress;
	private int maxProgress;
	private String activeRecipeId = "";
	/** "changes 18": which suit piece the operator picked (0 = none, 1 = helmet, 2 = chest, 3 = legs, 4 = boots). */
	private int selectedPiece;

	/**
	 * Vanilla {@link AbstractContainerMenu} data slots are synchronised as <b>signed 16-bit</b>, so a
	 * raw 50 000 energy value wraps to a negative number on the client. Energy is therefore sent
	 * pre-divided by {@link #ENERGY_SCALE} (fits comfortably in a short) and multiplied back in the
	 * menu / screen. Progress values are small and sent as-is.
	 */
	public static final int ENERGY_SCALE = 8;

	public final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> energy / ENERGY_SCALE;
				case 1 -> FabricatorRecipes.MAX_ENERGY / ENERGY_SCALE;
				case 2 -> progress;
				case 3 -> maxProgress;
				case 4 -> selectedPiece;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> energy = value * ENERGY_SCALE;
				case 2 -> progress = value;
				case 3 -> maxProgress = value;
				case 4 -> selectedPiece = value;
				default -> { }
			}
		}

		@Override
		public int getCount() {
			return 5;
		}
	};

	public StarkFabricatorBlockEntity(BlockPos pos, BlockState state) {
		super(IronManBlocks.STARK_FABRICATOR_BE, pos, state);
	}

	// ---------------- energy ----------------

	public int energy() {
		return energy;
	}

	/** Add Stark energy (from a Reactor Core / Arc Reactor inserted into the block). Clamped. Returns amount actually stored. */
	public int addEnergy(int amount) {
		int before = energy;
		energy = Math.min(FabricatorRecipes.MAX_ENERGY, energy + amount);
		setChanged();
		return energy - before;
	}

	public boolean isFull() {
		return energy >= FabricatorRecipes.MAX_ENERGY;
	}

	// ---------------- selected suit piece ("changes 18") ----------------

	public int selectedPiece() {
		return selectedPiece;
	}

	public void setSelectedPiece(int piece) {
		int clamped = Math.max(0, Math.min(4, piece));
		if (clamped == selectedPiece) {
			return;
		}
		selectedPiece = clamped;
		progress = 0;
		activeRecipeId = "";
		maxProgress = 0;
		setChanged();
	}

	// ---------------- tick ----------------

	private static final int ARC_REACTOR_ENERGY = 25_000;
	private static final int REACTOR_CORE_ENERGY = 8_000;

	/**
	 * "changes 22": the Fabricator has its own reactor and refills itself, no fuel required. It takes
	 * {@value #SELF_RECHARGE_SECONDS} seconds to go from empty to a full {@link FabricatorRecipes#MAX_ENERGY}
	 * buffer, which is exactly the cooldown between two armour pieces -- an armour recipe now costs the
	 * entire buffer (see {@code FabricatorRecipes.armor}), so building a piece drains the machine flat
	 * and the next one cannot start until it has charged all the way back up.
	 *
	 * <p>Reactor Cores and Arc Reactors dropped into an input slot still work; they are now a way to
	 * <em>skip</em> part of that wait rather than the only power source.
	 */
	public static final int SELF_RECHARGE_SECONDS = 300;
	private static final int SELF_RECHARGE_PER_TICK =
			Math.max(1, FabricatorRecipes.MAX_ENERGY / (SELF_RECHARGE_SECONDS * 20));

	/** Trickle the internal reactor's own output into the buffer. Runs whether or not anyone is nearby. */
	private void selfRecharge() {
		if (energy >= FabricatorRecipes.MAX_ENERGY) {
			return;
		}
		energy = Math.min(FabricatorRecipes.MAX_ENERGY, energy + SELF_RECHARGE_PER_TICK);
		setChanged();
	}

	/** Consume any Reactor Core / Arc Reactor sitting in an input slot to top up the energy buffer. */
	private void consumeFuelSlots() {
		if (energy >= FabricatorRecipes.MAX_ENERGY) {
			return;
		}
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = items.get(slot);
			int per = stack.is(com.projecthero.mod.ironman.item.IronManItems.ARC_REACTOR) ? ARC_REACTOR_ENERGY
					: stack.is(com.projecthero.mod.ironman.item.IronManItems.REACTOR_CORE) ? REACTOR_CORE_ENERGY : 0;
			if (per > 0) {
				energy = Math.min(FabricatorRecipes.MAX_ENERGY, energy + per);
				stack.shrink(1);
				setChanged();
				return;
			}
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, StarkFabricatorBlockEntity be) {
		be.selfRecharge();
		be.consumeFuelSlots();
		ServerPlayer operator = be.nearbyTonyStark(level, pos);
		FabricationRecipe recipe = operator == null ? null
				: FabricatorRecipes.find(be, TonyStark.techLevel(operator), be.selectedPiece);

		if (recipe == null || !be.canOutput(recipe) || be.energy < recipe.energyCost()) {
			if (be.progress != 0 || !be.activeRecipeId.isEmpty()) {
				be.progress = 0;
				be.activeRecipeId = "";
				be.maxProgress = 0;
				be.setChanged();
			}
			return;
		}

		if (!recipe.id().equals(be.activeRecipeId)) {
			be.activeRecipeId = recipe.id();
			be.progress = 0;
			be.maxProgress = recipe.timeTicks();
		}
		be.progress++;
		if (be.progress >= recipe.timeTicks()) {
			recipe.consume(be);
			be.energy -= recipe.energyCost();
			ItemStack out = be.items.get(OUTPUT_SLOT);
			if (out.isEmpty()) {
				be.items.set(OUTPUT_SLOT, recipe.result().copy());
			} else {
				out.grow(recipe.result().getCount());
			}
			// "changes 21": record every armour piece the Fabricator turns out. When the fourth piece of a
			// mark lands, TonyStark.recordSuitPiece promotes the mark into builtSuits -- which is what
			// unlocks the next mark's blueprint in the Blank Blueprint picker (linear progression).
			if (operator != null
					&& recipe.result().getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem armour) {
				TonyStark.recordSuitPiece(operator, armour.suitId(), armour.getType());
			}
			be.progress = 0;
			be.activeRecipeId = "";
			be.maxProgress = 0;
		}
		be.setChanged();
	}

	private boolean canOutput(FabricationRecipe recipe) {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			return true;
		}
		return out.is(recipe.result().getItem())
				&& out.getCount() + recipe.result().getCount() <= out.getMaxStackSize();
	}

	private ServerPlayer nearbyTonyStark(Level level, BlockPos pos) {
		AABB box = new AABB(pos).inflate(OPERATOR_RADIUS);
		for (Player player : level.getEntitiesOfClass(Player.class, box)) {
			if (player instanceof ServerPlayer sp && TonyStark.hasPower(sp)) {
				return sp;
			}
		}
		return null;
	}

	// ---------------- Container ----------------

	@Override
	public int getContainerSize() {
		return SIZE;
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
		ItemStack result = ContainerHelper.removeItem(items, slot, amount);
		setChanged();
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > stack.getMaxStackSize()) {
			stack.setCount(stack.getMaxStackSize());
		}
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player) && TonyStark.hasPower(player);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == OUTPUT_SLOT) {
			return false;
		}
		if (slot == BLUEPRINT_SLOT) {
			return stack.getItem() instanceof com.projecthero.mod.ironman.item.BlueprintItem;
		}
		return !(stack.getItem() instanceof com.projecthero.mod.ironman.item.BlueprintItem);
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	// ---------------- menu ----------------

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.projecthero.stark_fabricator");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
		return new StarkFabricatorMenu(syncId, inv, this, data);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	// ---------------- nbt ----------------

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.clear();
		ContainerHelper.loadAllItems(tag, items, registries);
		energy = tag.getInt("Energy");
		progress = tag.getInt("Progress");
		maxProgress = tag.getInt("MaxProgress");
		activeRecipeId = tag.getString("ActiveRecipe");
		selectedPiece = tag.getInt("SelectedPiece");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		tag.putInt("Energy", energy);
		tag.putInt("Progress", progress);
		tag.putInt("MaxProgress", maxProgress);
		tag.putString("ActiveRecipe", activeRecipeId);
		tag.putInt("SelectedPiece", selectedPiece);
	}
}
