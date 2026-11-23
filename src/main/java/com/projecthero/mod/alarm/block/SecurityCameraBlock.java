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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
 * v0.17.7: a <b>Security Camera</b> -- a wall-mounted sensor whose head sweeps from side to side
 * ({@link SecurityCameraBlockEntity}); a player in its view for a second raises the zone's alarm. {@link #FACING} is the
 * way it looks out from the wall. Dead ({@link SensorBlockEntity#LIVE} false) once its wired panel is sabotaged.
 * Breaking a live camera trips it.
 */
public class SecurityCameraBlock extends BaseEntityBlock {
	public static final MapCodec<SecurityCameraBlock> CODEC = simpleCodec(SecurityCameraBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIVE = SensorBlockEntity.LIVE;

	private static final VoxelShape NORTH = box(4, 5, 6, 12, 13, 16);
	private static final VoxelShape SOUTH = box(4, 5, 0, 12, 13, 10);
	private static final VoxelShape WEST = box(6, 5, 4, 16, 13, 12);
	private static final VoxelShape EAST = box(0, 5, 4, 10, 13, 12);

	public SecurityCameraBlock(Properties props) {
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
		Direction f = ctx.getClickedFace();
		return defaultBlockState().setValue(FACING, f.getAxis().isHorizontal() ? f : ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return switch (state.getValue(FACING)) {
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			default -> NORTH;
		};
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
		return new SecurityCameraBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return createTickerHelper(type, AlarmBlocks.CAMERA_BE,
				(lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl, pos, st));
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
