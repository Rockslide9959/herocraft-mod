package com.projecthero.mod.loot.sites;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.alarm.AlarmDirector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * v0.17.7 Tier 2 Sites: a <b>ward stone</b> -- the old-world sites' (crypt, shrine, ice hut) answer to a security camera.
 * A tripwire hook hangs on it and a string crosses a doorway; when the string is touched (the hook powers the stone) it
 * raises the site's alarm as a sensor ({@link AlarmDirector.Cause#SENSOR}: nothing happens once the Alarm Panel it is
 * wired to is sabotaged). Shears cut the string quietly, as ever.
 */
public class SiteWardBlock extends Block {
	public static final MapCodec<SiteWardBlock> CODEC = simpleCodec(SiteWardBlock::new);
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");

	public SiteWardBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(POWERED, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean moving) {
		super.neighborChanged(state, level, pos, neighbor, fromPos, moving);
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		boolean on = level.hasNeighborSignal(pos);
		if (on == state.getValue(POWERED)) {
			return;
		}
		level.setBlock(pos, state.setValue(POWERED, on), 3);
		if (on) {
			trip(server, pos);
		}
	}

	/** The string was touched: chime, runes flare, the alarm goes up (if the wired panel still lives). */
	public static boolean trip(ServerLevel level, BlockPos pos) {
		ServerPlayer culprit = null;
		double best = 16 * 16;
		for (ServerPlayer p : level.players()) {
			if (p.isCreative() || p.isSpectator()) {
				continue;
			}
			double d = p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (d < best) {
				best = d;
				culprit = p;
			}
		}
		level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.6f, 0.7f);
		level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.5, 0.6, 0.5, 0.6);
		return AlarmDirector.raise(level, pos, AlarmDirector.Cause.SENSOR, culprit);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.ENCHANT, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0.05, 0);
		}
	}
}
