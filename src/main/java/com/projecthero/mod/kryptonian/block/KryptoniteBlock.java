package com.projecthero.mod.kryptonian.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3f;

/**
 * v0.14.8: kryptonite ore and the kryptonite block. Plain glowing blocks that give off a faint green haze; what makes
 * them dangerous lives in {@code Kryptonite} (a Kryptonian within 5 blocks is weakened).
 */
public class KryptoniteBlock extends Block {
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.3f), 0.9f);

	public KryptoniteBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) != 0) {
			return;
		}
		double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
		double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
		double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
		level.addParticle(GREEN, x, y, z, 0.0, 0.02, 0.0);
	}
}
