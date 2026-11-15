package com.projecthero.mod.sentry.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.sentry.SentryConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.5: a burnt shadow -- the dark outline of a creature the Void erased (and, above 90 Void, of everything Sentry hits) lying on
 * the ground. Flat, no collision, breaks at a touch, drops nothing, only ever placed into air on a solid top, and fades on its own
 * after {@link SentryConfig#SHADOW_TICKS} (a scheduled tick). Facing turns the silhouette.
 */
public class BurntShadowBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<BurntShadowBlock> CODEC = simpleCodec(BurntShadowBlock::new);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.5, 16);

	public BurntShadowBlock(BlockBehaviour.Properties props) {
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
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
		return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, SentryConfig.SHADOW_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.removeBlock(pos, false);
	}

	/** Lays a shadow at {@code feet} (or the first air above solid ground up to 2 below it). True if one went down. */
	public static boolean lay(ServerLevel level, BlockPos feet, Direction facing) {
		for (int dy = 0; dy >= -2; dy--) {
			BlockPos p = feet.offset(0, dy, 0);
			if (!level.isLoaded(p)) {
				return false;
			}
			BlockState here = level.getBlockState(p);
			if (here.isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
				level.setBlock(p, SentryBlocks.BURNT_SHADOW.defaultBlockState().setValue(FACING,
						facing.getAxis().isHorizontal() ? facing : Direction.NORTH), Block.UPDATE_ALL);
				return true;
			}
		}
		return false;
	}
}
