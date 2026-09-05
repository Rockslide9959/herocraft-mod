package com.herocraft.mod.hero.device;

import com.herocraft.mod.hero.MutationTrigger;
import com.herocraft.mod.hero.mutation.MutationManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A reusable "laboratory device" block: the reproducible way to trigger a specific mutation exposure
 * event (spec section 11). Fires {@link MutationManager#triggerExposure} for every nearby player who
 * currently has the matching unstable serum active — a <em>small local</em> entity query run only
 * when the device activates, never a per-tick scan.
 *
 * <p>Redstone-activated devices fire on the rising edge of a redstone signal; interaction devices
 * fire on right-click.
 */
public class LabDeviceBlock extends Block {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private static final double RADIUS = 4.0;

	private final MutationTrigger.Kind[] kinds;
	private final boolean redstoneActivated;

	public LabDeviceBlock(Properties properties, boolean redstoneActivated, MutationTrigger.Kind... kinds) {
		super(properties);
		this.kinds = kinds;
		this.redstoneActivated = redstoneActivated;
		this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
		if (!redstoneActivated || level.isClientSide()) {
			return;
		}
		boolean signal = level.hasNeighborSignal(pos);
		if (signal != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
			if (signal && level instanceof ServerLevel serverLevel) {
				activate(serverLevel, pos);
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (redstoneActivated) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			activate(serverLevel, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	private void activate(ServerLevel level, BlockPos pos) {
		AABB box = new AABB(pos).inflate(RADIUS);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
			for (MutationTrigger.Kind kind : kinds) {
				MutationManager.triggerExposure(player, kind);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
				30, 0.4, 0.4, 0.4, 0.15);
		level.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
		level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 0.6f);
		level.playSound(null, pos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
	}
}
