package com.projecthero.mod.grave;

import java.util.Map;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.14.4: a boss trophy head mounted on a wall -- the wall twin of {@link TrophyHeadBlock}, mirroring vanilla's
 * {@code WallSkullBlock}: {@code FACING} is the way the face looks, the wall is behind it, and the head sits halfway
 * up the block with its back against the wall.
 */
public class TrophyHeadWallBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<TrophyHeadWallBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			TrophyHeads.Kind.CODEC.fieldOf("kind").forGetter(TrophyHeadWallBlock::kind),
			propertiesCodec()).apply(i, TrophyHeadWallBlock::new));

	/** Vanilla wall-skull shapes: the 8x8x8 head against the wall behind its face. */
	private static final Map<Direction, VoxelShape> SHAPES = Maps.newEnumMap(Map.of(
			Direction.NORTH, Block.box(4.0, 4.0, 8.0, 12.0, 12.0, 16.0),
			Direction.SOUTH, Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 8.0),
			Direction.EAST, Block.box(0.0, 4.0, 4.0, 8.0, 12.0, 12.0),
			Direction.WEST, Block.box(8.0, 4.0, 4.0, 16.0, 12.0, 12.0)));

	private final TrophyHeads.Kind kind;

	public TrophyHeadWallBlock(TrophyHeads.Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public TrophyHeads.Kind kind() {
		return kind;
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	/** Same search as vanilla's wall skull: the first horizontal look direction that has something solid to hang on. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		BlockGetter level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		for (Direction direction : context.getNearestLookingDirections()) {
			if (direction.getAxis().isHorizontal()) {
				state = state.setValue(FACING, direction.getOpposite());
				if (!level.getBlockState(pos.relative(direction)).canBeReplaced(context)) {
					return state;
				}
			}
		}
		return null;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
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
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TrophyHeadBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return TrophyHeadBlock.readPlaque(kind, level, pos, player);
	}
}
