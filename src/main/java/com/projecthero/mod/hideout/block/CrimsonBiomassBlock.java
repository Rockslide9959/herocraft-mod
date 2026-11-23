package com.projecthero.mod.hideout.block;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: the Carnage Nest's living walls -- Carnage's red biomass ({@code crimson_biomass}: sticky, slow to walk on,
 * dripping red) and the pulsing egg walls ({@code crimson_egg}: glowing red egg sacs with an animated pulse texture).
 */
public class CrimsonBiomassBlock extends Block {
	public static final MapCodec<CrimsonBiomassBlock> CODEC = simpleCodec(CrimsonBiomassBlock::new);
	private static final DustParticleOptions DRIP = new DustParticleOptions(new Vector3f(0.7f, 0.03f, 0.05f), 0.7f);

	public CrimsonBiomassBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(6) != 0) {
			return;
		}
		if (level.getBlockState(pos.below()).isAir()) {
			level.addParticle(DRIP, pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() - 0.05, pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0, -0.1, 0);
		}
	}
}
