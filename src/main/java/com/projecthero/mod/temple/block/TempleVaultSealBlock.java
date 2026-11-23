package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.temple.TempleClearing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: a <b>sealed Treasury Vault</b> behind Ankhatep's throne. Unbreakable; when he falls
 * ({@link TempleClearing#bossDefeated}) each one opens into a Tier 4 Hero Vault (per-player loot) with the same facing.
 * Used while no Ankhatep is within reach (Peaceful, a command), it opens anyway, so the temple can never get stuck.
 */
public class TempleVaultSealBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<TempleVaultSealBlock> CODEC = simpleCodec(TempleVaultSealBlock::new);

	public TempleVaultSealBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			TempleClearing.pokeSeal(server, pos, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
