package com.projecthero.mod.vaultdungeon.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.vaultdungeon.VaultSecurity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: the <b>cell-block breaker</b> in the Warden's Control Room. While {@link #POWERED} it hums and sparks;
 * right-click shuts the cell-block power down for good ({@link VaultSecurity#shutDown}): every blue security field
 * within reach drops (the way down to the Warden's Level opens -- and so do the Maximum Security pods), and the
 * Maximum Security wing's cameras and lasers go dark.
 */
public class VaultBreakerBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<VaultBreakerBlock> CODEC = simpleCodec(VaultBreakerBlock::new);
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");

	public VaultBreakerBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, true));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			if (!state.getValue(POWERED)) {
				player.displayClientMessage(Component.translatable("block.projecthero.vault_breaker.off").withStyle(ChatFormatting.GRAY), true);
			} else {
				VaultSecurity.shutDown(server, pos, player);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(POWERED) && random.nextInt(4) == 0) {
			Direction f = state.getValue(FACING);
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + f.getStepX() * 0.55, pos.getY() + 0.3 + random.nextDouble() * 0.5,
					pos.getZ() + 0.5 + f.getStepZ() * 0.55, 0, 0.02, 0);
		}
	}
}
