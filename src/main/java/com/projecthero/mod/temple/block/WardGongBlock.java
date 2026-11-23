package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.alarm.AlarmDirector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: a <b>Ward Gong</b> -- the Ancient Temple's sensor (Temple Wards = the alarm framework, themed). A bronze gong
 * set into the wall; a tripwire hooked to it, a redstone signal, or anyone striking it booms it and "wakes the guardians":
 * {@link AlarmDirector#raise} with {@link AlarmDirector.Cause#SENSOR}, so it is wired to the zone's nearest ward stone
 * (Alarm Panel) and goes quiet once that panel is sabotaged. {@code facing} = the side the gong face looks out of.
 */
public class WardGongBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<WardGongBlock> CODEC = simpleCodec(WardGongBlock::new);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public WardGongBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		boolean powered = level.hasNeighborSignal(pos);
		if (powered != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, powered), 3);
			if (powered) {
				strike(server, pos, state, server.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16, false));
			}
		}
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel server && !player.isCreative()) {
			strike(server, pos, state, player);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			if (player.isCreative()) {
				player.displayClientMessage(Component.translatable("block.projecthero.ward_gong.hint"), true);
			} else {
				strike(server, pos, state, player);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/** The gong booms: deep bell + shockwave rings, and the zone's alarm is raised (if the gong's ward stone is live). */
	public static boolean strike(ServerLevel level, BlockPos pos, BlockState state, Player culprit) {
		Direction f = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
		double x = pos.getX() + 0.5 + f.getStepX() * 0.6;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5 + f.getStepZ() * 0.6;
		level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 0.5f);
		level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.0f, 0.6f);
		level.sendParticles(ParticleTypes.NOTE, x, y + 0.5, z, 3, 0.3, 0.2, 0.3, 0.6);
		level.sendParticles(ParticleTypes.SONIC_BOOM, x, y, z, 1, 0, 0, 0, 0);
		return AlarmDirector.raise(level, pos, AlarmDirector.Cause.SENSOR, culprit);
	}
}
