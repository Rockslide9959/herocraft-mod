package com.projecthero.mod.omnitrix.alien.diamondhead;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * v0.17.2 Diamondhead revamp: the Petrosapien crystal, with a {@link #RISING} state -- solid but drawn as nothing (its model is
 * empty) while {@link DiamondheadRise} slides a display of it up out of the ground; and when it goes (its time is up) a display
 * of it sinks back into the ground instead of it simply vanishing.
 */
public class PetrosapienCrystalBlock extends TransparentBlock {
	public static final BooleanProperty RISING = BooleanProperty.create("rising");
	/** Set while a construct is broken up on purpose (the Crystal Prison's shatter): no sinking then. */
	static boolean shattering;

	public PetrosapienCrystalBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(RISING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(RISING);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (level instanceof ServerLevel sl && !newState.is(this) && !shattering) {
			DiamondheadRise.sink(sl, pos);
		}
		super.onRemove(state, level, pos, newState, moved);
	}
}
