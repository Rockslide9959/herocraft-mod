package com.projecthero.mod.skyfortress.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.skyfortress.SkyFortressClearing;

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
 * v0.17.7: the sealed <b>Fortress Vault</b> in Admiral Kestrel's cabin. Unbreakable. Kestrel dies -> every seal near her
 * flight deck opens into a Tier 4 Hero Vault with the same facing ({@link SkyFortressClearing}). Using a seal whose
 * Admiral is gone without dying (Peaceful, a command) opens it anyway, so the fortress never gets stuck.
 */
public class SkyVaultSealBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<SkyVaultSealBlock> CODEC = simpleCodec(SkyVaultSealBlock::new);

	public SkyVaultSealBlock(BlockBehaviour.Properties props) {
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
			SkyFortressClearing.pokeSeal(server, pos, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
