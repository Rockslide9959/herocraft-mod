package com.projecthero.mod.skyfortress.block;

import com.projecthero.mod.skyfortress.SkyTurbines;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: the energy <b>Bridge Shield</b> across the Command Bridge stairway. Unbreakable, solid, see-through
 * (translucent layer). It drops -- every shield block in the fortress at once -- when the last engine turbine is
 * sabotaged ({@link SkyTurbines}). Using it says how many turbines still run.
 */
public class BridgeShieldBlock extends Block {
	public BridgeShieldBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			int running = SkyTurbines.countRunning(server, pos);
			player.displayClientMessage(Component.translatable("block.projecthero.bridge_shield.up", running).withStyle(ChatFormatting.AQUA), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
		return adjacent.is(this) || super.skipRendering(state, adjacent, side);
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return Shapes.empty();
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
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.GLOW, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
					0, 0.01, 0);
		}
	}
}
