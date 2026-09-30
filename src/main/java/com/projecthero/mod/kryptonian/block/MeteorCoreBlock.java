package com.projecthero.mod.kryptonian.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3f;

/**
 * v0.14.8: the Meteor Core -- the still-glowing heart of a Kryptonite Meteor, one per crater. Mining it (iron pickaxe or
 * better) drops the Kryptonian Crystal; the loot table never drops the block itself, so there is exactly one crystal
 * per meteor.
 */
public class MeteorCoreBlock extends Block {
	private static final DustParticleOptions SUN = new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.3f), 1.0f);

	public MeteorCoreBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.1;
		double y = pos.getY() + 0.6 + random.nextDouble() * 0.6;
		double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.1;
		level.addParticle(SUN, x, y, z, 0.0, 0.03, 0.0);
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.04, 0.0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.05, z, 0.0, 0.03, 0.0);
		}
	}
}
