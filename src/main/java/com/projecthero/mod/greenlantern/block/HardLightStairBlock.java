package com.projecthero.mod.greenlantern.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.13.21: the Stair/Ramp construct's steps. The ramp used to be a staircase of full blocks, which a player cannot
 * walk up without jumping every single step; real stair shapes are walkable at a normal stride. Same hard-light look,
 * rules and orphan cleanup as {@link HardLightBlock}.
 */
public class HardLightStairBlock extends StairBlock {
	public HardLightStairBlock(BlockState baseState, Properties properties) {
		super(baseState, properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		HardLightBlock.scheduleOrphanCheck(level, pos, this);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		HardLightBlock.orphanCheck(level, pos, this);
	}

	/** Side-by-side steps of the same ramp share a face -- hide it so the translucent ramp reads as one surface. */
	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
		if (adjacent.is(this) && adjacent.getValue(FACING) == state.getValue(FACING)
				&& adjacent.getValue(HALF) == state.getValue(HALF)
				&& direction.getAxis() != state.getValue(FACING).getAxis()) {
			return true;
		}
		return super.skipRendering(state, adjacent, direction);
	}

	@Override
	protected float getShadeBrightness(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return 1.0f;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(28) == 0) {
			level.addParticle(HardLightBlock.MOTE, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
					pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}
}
