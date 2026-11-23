package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * v0.17.7: a <b>Temple Mirror</b> -- a bronze-framed polished mirror on a stand, set at 45 degrees. One-sided: its face
 * looks between {@code facing} and {@code facing.getClockWise()}, so a sun beam arriving from either of those two sides
 * leaves through the other; from the back it is blocked ({@link #reflect}). Right-click turns it a quarter clockwise.
 * Unbreakable (the light puzzle can always be solved).
 */
public class TempleMirrorBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<TempleMirrorBlock> CODEC = simpleCodec(TempleMirrorBlock::new);

	public TempleMirrorBlock(BlockBehaviour.Properties props) {
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
			turn(server, pos, state);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/** Turns the mirror a quarter clockwise. */
	public static void turn(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getClockWise()), 3);
		level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7f, 1.4f);
	}

	/**
	 * Where a beam travelling {@code travel} leaves a mirror facing {@code facing}, or null when it hits the back.
	 * The beam arrives from side {@code travel.getOpposite()}.
	 */
	public static Direction reflect(Direction facing, Direction travel) {
		Direction from = travel.getOpposite();
		if (from == facing) {
			return facing.getClockWise();
		}
		if (from == facing.getClockWise()) {
			return facing;
		}
		return null;
	}
}
