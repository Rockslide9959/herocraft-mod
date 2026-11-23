package com.projecthero.mod.alarm.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: a <b>Laser Tripwire</b> emitter -- a small box on a wall projecting a red beam along {@link #FACING}
 * ({@link LaserTripwireBlockEntity}); a player crossing it raises the zone's alarm. Dead once its wired panel is
 * sabotaged (no beam). Breaking a live emitter trips it.
 */
public class LaserTripwireBlock extends BaseEntityBlock {
	public static final MapCodec<LaserTripwireBlock> CODEC = simpleCodec(LaserTripwireBlock::new);
	public static final DirectionProperty FACING = DirectionalBlock.FACING;
	public static final BooleanProperty LIVE = SensorBlockEntity.LIVE;

	private static final VoxelShape[] SHAPES = new VoxelShape[6];
	static {
		// the emitter sits against the face opposite to the beam
		SHAPES[Direction.DOWN.ordinal()] = box(5, 11, 5, 11, 16, 11);
		SHAPES[Direction.UP.ordinal()] = box(5, 0, 5, 11, 5, 11);
		SHAPES[Direction.NORTH.ordinal()] = box(5, 5, 11, 11, 11, 16);
		SHAPES[Direction.SOUTH.ordinal()] = box(5, 5, 0, 11, 11, 5);
		SHAPES[Direction.WEST.ordinal()] = box(11, 5, 5, 16, 11, 11);
		SHAPES[Direction.EAST.ordinal()] = box(0, 5, 5, 5, 11, 11);
	}

	public LaserTripwireBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIVE, true));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIVE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPES[state.getValue(FACING).ordinal()];
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LaserTripwireBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return createTickerHelper(type, AlarmBlocks.LASER_BE, (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl, pos, st));
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && state.getValue(LIVE) && !player.isCreative()
				&& server.getBlockEntity(pos) instanceof SensorBlockEntity s) {
			s.trip(server, pos, player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
