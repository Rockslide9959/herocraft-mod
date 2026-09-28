package com.projecthero.mod.hulk.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3f;

/**
 * v0.13.12 (Hulk Phase 4): the Gamma Reactor -- the glowing centrepiece of a ruined Gamma Lab. Full light, a low
 * hum and a haze of green motes. A player with the Gamma power standing within a few blocks of one feels his rage
 * climb ({@code Hulk#tick}); nothing else happens to anyone else. Pickaxe-mined, drops itself.
 */
public class GammaReactorBlock extends Block {
	private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 1.0f);

	public GammaReactorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 3; i++) {
			level.addParticle(GLOW, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.6,
					pos.getY() + 0.5 + random.nextDouble() * 1.2, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.6,
					0.0, 0.02, 0.0);
		}
		if (random.nextInt(40) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BEACON_AMBIENT,
					SoundSource.BLOCKS, 0.6f, 0.6f, false);
		}
	}
}
