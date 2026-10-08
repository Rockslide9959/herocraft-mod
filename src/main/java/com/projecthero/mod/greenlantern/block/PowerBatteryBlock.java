package com.projecthero.mod.greenlantern.block;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternBattery;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Personal Power Battery. Right-click by its bonded owner begins the Oath recitation that fully
 * recharges the ring on completion (v0.11.4); any other Green Lantern may also use it (per the build
 * brief's fallback -- ownership on placed batteries isn't tracked, team-sharing is implicit). See
 * {@link GreenLanternBattery} for the oath state machine and cancel conditions.
 */
public class PowerBatteryBlock extends Block {
	/** v0.13.21: the lantern's real footprint (base plate + barrel + cap) rather than a full cube. */
	private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
			Block.box(2, 0, 2, 14, 2, 14), Block.box(3, 2, 3, 13, 13, 13), Block.box(4, 13, 4, 12, 16, 12));
	private static final net.minecraft.core.particles.DustParticleOptions GLOW =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.36f, 1.0f, 0.55f), 0.8f);

	public PowerBatteryBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
		return SHAPE;
	}

	/** v0.13.21: a slow drift of green light rising out of the lantern's core. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(GLOW, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.35 + random.nextDouble() * 0.5,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (!GreenLantern.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.green_lantern.not_a_lantern"), true);
			return InteractionResult.CONSUME;
		}
		// v0.15.15: charging moved to the battery held in the off hand (Sneak + right-click) -- a placed one just says so
		sp.displayClientMessage(Component.translatable("message.projecthero.green_lantern.battery_how"), true);
		return InteractionResult.CONSUME;
	}
}
