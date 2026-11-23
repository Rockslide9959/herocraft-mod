package com.projecthero.mod.alarm.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.alarm.AlarmZones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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

import org.joml.Vector3f;

/**
 * v0.17.7: an <b>Alarm Lamp</b> -- dark until its zone goes Alert, then {@link #LIT} red (tech) or a burning ward
 * brazier (ancient), light 13. Driven only by the zone ({@code AlarmDirector.syncBlocks}).
 */
public class AlarmLampBlock extends Block {
	public static final MapCodec<AlarmLampBlock> CODEC = simpleCodec(AlarmLampBlock::new);
	public static final EnumProperty<AlarmStyle> STYLE = AlarmBlocks.STYLE;
	public static final BooleanProperty LIT = BooleanProperty.create("lit");

	public AlarmLampBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(STYLE, AlarmStyle.TECH).setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STYLE, LIT);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
		super.onPlace(state, level, pos, old, moving);
		if (level instanceof ServerLevel server && !old.is(this)) {
			AlarmZones.noteBlock(server, pos, state);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		if (state.getValue(STYLE) == AlarmStyle.ANCIENT) {
			if (random.nextInt(2) == 0) {
				level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
						pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
			}
		} else if (random.nextInt(3) == 0) {
			level.addParticle(new DustParticleOptions(new Vector3f(1f, 0.1f, 0.05f), 1.2f), pos.getX() + random.nextDouble(),
					pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0, 0);
		}
	}
}
