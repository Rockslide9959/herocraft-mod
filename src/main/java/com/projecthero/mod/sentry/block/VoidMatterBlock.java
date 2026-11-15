package com.projecthero.mod.sentry.block;

import com.projecthero.mod.sentry.SentryConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5: void matter -- the black, gold-flecked stuff the Void's ring turns the world into for a moment before it is nothing. Every
 * one dissolves into air {@link SentryConfig#MATTER_DISSOLVE_TICKS} after it appears (a scheduled tick, so it also goes on a reload);
 * the original block was recorded by {@code VoidMatter} first (unless {@code projectheroVoidGriefing} is on). Unbreakable, drops
 * nothing. The air is set without neighbour shape updates, so torches and flowers on it don't pop off as items.
 */
public class VoidMatterBlock extends Block {
	public VoidMatterBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, SentryConfig.MATTER_DISSOLVE_TICKS + level.getRandom().nextInt(10));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		if (random.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
		}
	}
}
