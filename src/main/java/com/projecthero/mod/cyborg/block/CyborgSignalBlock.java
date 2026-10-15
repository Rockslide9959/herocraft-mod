package com.projecthero.mod.cyborg.block;

import com.projecthero.mod.cyborg.CyborgCircuit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.5: the power Cyborg's Circuit Link (V) feeds into redstone. Placed for a moment in the air beside the block he aims
 * at, it is an invisible, intangible, replaceable signal source -- full power to everything round it, strong power into
 * a solid block next to it (so a powered block behaves like a block of redstone) -- and it removes itself the tick
 * {@link CyborgCircuit} stops holding it (it re-checks every 2 ticks, so a restart or an unloaded chunk can never strand
 * one). Never an item, never dropped.
 */
public final class CyborgSignalBlock extends Block {
	public CyborgSignalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return 15;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return 15;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		if (!old.is(this)) {
			level.scheduleTick(pos, this, 2);
			updateAround(level, pos);
		}
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
		super.onRemove(state, level, pos, next, moved);
		if (!next.is(this)) {
			updateAround(level, pos);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (CyborgCircuit.powered(level, pos)) {
			level.scheduleTick(pos, this, 2);
		} else {
			level.removeBlock(pos, false);
		}
	}

	/** Like a redstone torch: its neighbours, and theirs (strong power runs through a solid block). */
	private void updateAround(Level level, BlockPos pos) {
		level.updateNeighborsAt(pos, this);
		for (Direction d : Direction.values()) {
			level.updateNeighborsAt(pos.relative(d), this);
		}
	}
}
