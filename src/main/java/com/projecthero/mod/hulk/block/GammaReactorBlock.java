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
 *
 * <p>v0.13.21: right-clicking it with the Gamma Serum in your blood overloads it -- a huge explosion you survive
 * because it makes you the Hulk ({@link com.projecthero.mod.hulk.GammaOverload}).
 */
public class GammaReactorBlock extends Block {
	private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 1.0f);

	public GammaReactorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
			com.projecthero.mod.hulk.GammaOverload.onReactorUsed(sp, pos);
		}
		return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
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
