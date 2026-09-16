package com.projecthero.mod.greenlantern.block;

import com.projecthero.mod.greenlantern.GreenLanternTrial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Fallen Lantern Site's pedestal, holding the Dormant Power Ring. Right-clicking an eligible
 * player begins the Will Trial (see {@link GreenLanternTrial}); on success the {@link #CLAIMED}
 * blockstate flips permanently (persisted with the chunk, like any blockstate) so the site's single
 * Lantern Core reward cannot be farmed by reopening it.
 */
public class FallenLanternPedestalBlock extends Block {
	public static final BooleanProperty CLAIMED = BooleanProperty.create("claimed");

	public FallenLanternPedestalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(CLAIMED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CLAIMED);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		GreenLanternTrial.attemptStart(sp, pos, state.getValue(CLAIMED));
		return InteractionResult.CONSUME;
	}
}
