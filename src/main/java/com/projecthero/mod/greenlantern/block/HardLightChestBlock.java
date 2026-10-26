package com.projecthero.mod.greenlantern.block;

import com.projecthero.mod.greenlantern.GreenLanternSurvival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.1: the Hard-Light Chest ({@link GreenLanternSurvival}): a chest-shaped block of hard light, its latch facing the
 * Lantern who shaped it. The 27 slots live in the construct, not in a block entity -- right-click opens them for the
 * owner and their squad, and every way the chest ends (N, an empty ring, being punched apart, its owner leaving) spills
 * them onto the ground. Unobtainable, unbreakable by mining, immovable, self-removing if orphaned.
 */
public class HardLightChestBlock extends HorizontalDirectionalBlock {
	public static final com.mojang.serialization.MapCodec<HardLightChestBlock> CODEC = simpleCodec(HardLightChestBlock::new);
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

	public HardLightChestBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0f;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, HardLightBlock.ORPHAN_CHECK_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (GreenLanternSurvival.isTrackedCell(level, pos)) {
			level.scheduleTick(pos, this, HardLightBlock.ORPHAN_CHECK_TICKS);
		} else {
			level.removeBlock(pos, false);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(14) == 0) {
			level.addParticle(HardLightBlock.MOTE, pos.getX() + 0.1 + random.nextDouble() * 0.8, pos.getY() + 0.9,
					pos.getZ() + 0.1 + random.nextDouble() * 0.8, 0.0, 0.02, 0.0);
		}
	}
}
