package com.projecthero.mod.greenlantern.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Vector3f;

/**
 * v0.13.21: the Lantern Light construct -- a small floating orb of hard light (light level 15, no collision) instead
 * of the full Sea Lantern block it used to drop into the world. Same orphan cleanup as {@link HardLightBlock}.
 */
public class HardLightLampBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 11, 11);
	private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.65f), 0.6f);

	public HardLightLampBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
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
		HardLightBlock.scheduleOrphanCheck(level, pos, this);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		HardLightBlock.orphanCheck(level, pos, this);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			level.addParticle(GLOW, pos.getX() + 0.5 + Math.cos(a) * 0.3, pos.getY() + 0.35 + random.nextDouble() * 0.3,
					pos.getZ() + 0.5 + Math.sin(a) * 0.3, 0.0, 0.015, 0.0);
		}
	}
}
