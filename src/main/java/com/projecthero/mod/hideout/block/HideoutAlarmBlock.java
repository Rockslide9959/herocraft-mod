package com.projecthero.mod.hideout.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.hideout.HideoutAlarms;
import com.projecthero.mod.hideout.HideoutFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * v0.17.5: a hideout <b>alarm</b> -- a siren box (Syndicate, Ultron), a Boom Tube beacon (Apokolips), an egg pod
 * (Symbiote) or a war horn (Frost Giants). A tripwire hook is hung on it: the first time anything powers it (the tripwire
 * snaps) it goes off once ({@link #SPENT}) and calls a squad of guards in ({@link HideoutAlarms#trigger}). Break it first
 * and the trap is disarmed.
 */
public class HideoutAlarmBlock extends Block {
	public static final MapCodec<HideoutAlarmBlock> CODEC = simpleCodec(HideoutAlarmBlock::new);
	public static final EnumProperty<HideoutFaction> FACTION = HideoutBlocks.FACTION;
	public static final BooleanProperty SPENT = BooleanProperty.create("spent");

	public HideoutAlarmBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACTION, HideoutFaction.KINGPIN).setValue(SPENT, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACTION, SPENT);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean moving) {
		super.neighborChanged(state, level, pos, neighbor, fromPos, moving);
		if (level instanceof ServerLevel server && !state.getValue(SPENT) && level.hasNeighborSignal(pos)) {
			level.setBlock(pos, state.setValue(SPENT, true), 3);
			HideoutAlarms.trigger(server, pos, state.getValue(FACTION));
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(SPENT) && random.nextInt(4) == 0) {
			level.addParticle(state.getValue(FACTION) == HideoutFaction.SYMBIOTE ? ParticleTypes.SQUID_INK : ParticleTypes.SMALL_FLAME,
					pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.02, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01, 0);
		}
	}
}
