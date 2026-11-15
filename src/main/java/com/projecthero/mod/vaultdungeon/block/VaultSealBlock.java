package com.projecthero.mod.vaultdungeon.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.vaultdungeon.VaultClearing;

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
 * v0.17.5: a <b>sealed Dungeon Vault</b> -- a Tier 4 Hero Vault behind Warden Kross's mag-locks. Unbreakable. When Kross
 * falls ({@link VaultClearing#bossDefeated}) every seal near his arena turns into an open Tier 4 Hero Vault with the
 * same facing. Using a seal whose Warden has vanished without dying (Peaceful, a command) opens them anyway, so a
 * dungeon never gets stuck.
 */
public class VaultSealBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<VaultSealBlock> CODEC = simpleCodec(VaultSealBlock::new);

	public VaultSealBlock(BlockBehaviour.Properties props) {
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
			VaultClearing.pokeSeal(server, pos, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
