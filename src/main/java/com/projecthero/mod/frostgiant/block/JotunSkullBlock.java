package com.projecthero.mod.frostgiant.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.1: the <b>Jotun Skull</b> -- the Frost Giant Assault's trophy. A horned frost-giant skull with glowing red eyes
 * (light 6) that breathes a little frost. Faces the player who placed it.
 */
public class JotunSkullBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<JotunSkullBlock> CODEC = simpleCodec(JotunSkullBlock::new);
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

	public JotunSkullBlock(BlockBehaviour.Properties properties) {
		super(properties);
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			Direction f = state.getValue(FACING);
			double x = pos.getX() + 0.5 + f.getStepX() * 0.45;
			double z = pos.getZ() + 0.5 + f.getStepZ() * 0.45;
			level.addParticle(ParticleTypes.SNOWFLAKE, x, pos.getY() + 0.35, z, f.getStepX() * 0.02, 0.01, f.getStepZ() * 0.02);
		}
	}
}
