package com.projecthero.mod.skyfortress.block;

import com.projecthero.mod.skyfortress.SkyFling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: the Sky Fortress <b>Launch Pad</b>. Step on it holding a Fortress Pass (either hand) and it flings you
 * straight up through the hangar's lift hatch overhead ({@link SkyFling}). Without a pass it only hums and says so.
 */
public class LaunchPadBlock extends Block {
	public LaunchPadBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
			SkyFling.tryLaunch(server, pos, player, false);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			SkyFling.explain(server, pos, sp);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
					0, 0.04 + random.nextDouble() * 0.05, 0);
		}
	}
}
