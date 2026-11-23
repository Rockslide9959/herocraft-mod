package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * v0.17.7: a <b>Dart Trap</b> -- a carved face in a temple wall with a dark mouth. It watches the line straight out of
 * its mouth ({@code facing}) at its own height, up to {@link DartTrapBlockEntity#RANGE} blocks: a survival player whose
 * body crosses that line hears a click and, a quarter second later, takes a poisoned dart. Set one block above the
 * floor it is knee high (jump it); two above, head high (crouch under it). Mine it to disarm it.
 */
public class DartTrapBlock extends BaseEntityBlock {
	public static final MapCodec<DartTrapBlock> CODEC = simpleCodec(DartTrapBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	/** True: a head-high trap (one block above the feet; crouch under it). False: knee high (in the feet block; jump it). */
	public static final net.minecraft.world.level.block.state.properties.BooleanProperty HIGH =
			net.minecraft.world.level.block.state.properties.BooleanProperty.create("high");

	public DartTrapBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HIGH, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HIGH);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DartTrapBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, TempleBlocks.DART_TRAP_BE, DartTrapBlockEntity::serverTick);
	}
}
