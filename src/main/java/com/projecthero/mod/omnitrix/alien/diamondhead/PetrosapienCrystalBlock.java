package com.projecthero.mod.omnitrix.alien.diamondhead;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * v0.17.2 Diamondhead revamp: the Petrosapien crystal, with a {@link #RISING} state -- solid but drawn as nothing (its model is
 * empty) while {@link DiamondheadRise} slides a display of it up out of the ground; and when it goes (its time is up) a display
 * of it sinks back into the ground instead of it simply vanishing. v0.17.2 (user): Diamondhead breaks his own crystal as fast as
 * dirt, and breaking any block of a construct brings the whole thing down ({@link DiamondheadRise#collapse}).
 */
public class PetrosapienCrystalBlock extends TransparentBlock {
	public static final BooleanProperty RISING = BooleanProperty.create("rising");
	/** Dirt's hardness: how fast Diamondhead breaks his crystal. */
	public static final float DIAMONDHEAD_HARDNESS = 0.5f;
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
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (com.projecthero.mod.omnitrix.alien.AlienKit.activeKit(player) instanceof DiamondheadKit) {
			return player.getDestroySpeed(state) / DIAMONDHEAD_HARDNESS / 30f;
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel sl) {
			DiamondheadRise.collapse(sl, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (level instanceof ServerLevel sl && !newState.is(this) && !DiamondheadRise.collapsing(pos) && !shattering) {
			DiamondheadRise.sink(sl, pos);
		}
		super.onRemove(state, level, pos, newState, moved);
	}
}
