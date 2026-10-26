package com.projecthero.mod.greenlantern.block;

import com.projecthero.mod.greenlantern.GreenLanternSurvival;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.1: the Hard-Light Scaffold Tower's blocks ({@link GreenLanternSurvival}). A column cell behaves like vanilla
 * scaffolding: you stand on top of it, walk into it from the side, hold Space to climb (it is in
 * {@code #minecraft:climbable}) and sneak to sink down through it ({@code HardLightScaffoldClimbMixin} lets a sneaking
 * player slide instead of clinging, the way the vanilla block does). {@link #PLATFORM} = the solid 3x3 deck round the
 * top of the column.
 *
 * <p>Like every hard-light block it is unobtainable, unbreakable, immovable, drops nothing, and removes itself if no live
 * tower owns its cell any more (a crash, a restart, a chunk unloaded when the tower ended).
 */
public class HardLightScaffoldBlock extends Block {
	public static final com.mojang.serialization.MapCodec<HardLightScaffoldBlock> CODEC = simpleCodec(HardLightScaffoldBlock::new);
	public static final BooleanProperty PLATFORM = BooleanProperty.create("platform");
	private static final VoxelShape TOP = Block.box(0, 14, 0, 16, 16, 16);
	private static final VoxelShape FRAME = Shapes.or(TOP, Block.box(0, 0, 0, 2, 14, 2), Block.box(14, 0, 0, 16, 14, 2),
			Block.box(0, 0, 14, 2, 14, 16), Block.box(14, 0, 14, 16, 14, 16));

	public HardLightScaffoldBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PLATFORM, false));
	}

	@Override
	protected com.mojang.serialization.MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PLATFORM);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(PLATFORM) ? Shapes.block() : FRAME;
	}

	/** The column: solid underfoot from above (unless sneaking), open from inside and from the sides. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(PLATFORM)) {
			return Shapes.block();
		}
		return context.isAbove(Shapes.block(), pos, true) && !context.isDescending() ? TOP : Shapes.empty();
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
	protected boolean skipRendering(BlockState state, BlockState adjacent, net.minecraft.core.Direction direction) {
		return state.getValue(PLATFORM) && adjacent.is(this) && adjacent.getValue(PLATFORM) || super.skipRendering(state, adjacent, direction);
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
		if (random.nextInt(40) == 0) {
			level.addParticle(HardLightBlock.MOTE, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
					pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}
}
