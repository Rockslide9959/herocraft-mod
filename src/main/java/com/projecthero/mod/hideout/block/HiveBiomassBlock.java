package com.projecthero.mod.hideout.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5: the Symbiote Hive's living walls -- black, glossy, sticky (walking on it is slow) and always dripping: a bead
 * of black goo falls from the underside of any biomass with air below it. The pod variant ({@code hive_pod}) is a pale,
 * faintly glowing egg sac grown into the walls.
 */
public class HiveBiomassBlock extends Block {
	public static final MapCodec<HiveBiomassBlock> CODEC = simpleCodec(HiveBiomassBlock::new);

	public HiveBiomassBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(5) != 0) {
			return;
		}
		BlockPos below = pos.below();
		if (level.getBlockState(below).isAir()) {
			level.addParticle(ParticleTypes.SQUID_INK, pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() - 0.05,
					pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0, -0.08, 0);
		}
	}
}
