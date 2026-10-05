package com.projecthero.mod.ironman.furnace;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.14.26: the Stark cookers' block entity -- a vanilla furnace that takes its recipe type and screen from its
 * {@link StarkFurnaces.Kind}, and cooks every item in {@link #cookTicks} ticks instead of the recipe's own time.
 */
public class StarkFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
	private final StarkFurnaces.Kind kind;

	public StarkFurnaceBlockEntity(BlockPos pos, BlockState state) {
		this(pos, state, state.getBlock() instanceof StarkFurnaceBlock b ? b.kind() : StarkFurnaces.Kind.FURNACE);
	}

	private StarkFurnaceBlockEntity(BlockPos pos, BlockState state, StarkFurnaces.Kind kind) {
		super(StarkFurnaces.BLOCK_ENTITY, pos, state, kind.recipeType);
		this.kind = kind;
	}

	public StarkFurnaces.Kind kind() {
		return kind;
	}

	/** Ticks to cook one item: 50 for the furnace (2.5 s), 20 for the smelter and smoker (1 s). */
	public int cookTicks() {
		return kind.cookTicks;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.projecthero." + kind.id);
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return switch (kind) {
			case SMELTER -> new BlastFurnaceMenu(id, inventory, this, this.dataAccess);
			case SMOKER -> new SmokerMenu(id, inventory, this, this.dataAccess);
			default -> new FurnaceMenu(id, inventory, this, this.dataAccess);
		};
	}
}
