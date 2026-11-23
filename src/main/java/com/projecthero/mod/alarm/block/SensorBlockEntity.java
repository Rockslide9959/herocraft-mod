package com.projecthero.mod.alarm.block;

import com.projecthero.mod.alarm.AlarmDirector;
import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * v0.17.7: shared server logic of the sensor blocks (Security Camera, Laser Tripwire): every second it checks whether the
 * panel it is wired to is still live and shows that in the block's {@code live} property; a trip raises the zone's alarm
 * ({@link AlarmDirector#raise} with {@link AlarmDirector.Cause#SENSOR}), then waits {@link #COOLDOWN} ticks. A sensor
 * outside any zone just sounds off. Nothing is saved: the state is in the block.
 */
public abstract class SensorBlockEntity extends BlockEntity {
	public static final int COOLDOWN = 60;
	public static final BooleanProperty LIVE = BooleanProperty.create("live");

	protected int cooldown;
	private int clock;

	protected SensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Server tick (from the block's ticker). */
	public final void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (++clock % 20 == 1) {
			AlarmZone z = AlarmZones.get(level).zoneAt(pos);
			boolean live = z == null || z.sensorLive(level, pos);
			if (state.getValue(LIVE) != live) {
				state = state.setValue(LIVE, live);
				level.setBlock(pos, state, 3);
			}
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (state.getValue(LIVE) && clock % 2 == 0) {
			sense(level, pos, state);
		}
	}

	/** Look for intruders; call {@link #trip} when one is caught. */
	protected abstract void sense(ServerLevel level, BlockPos pos, BlockState state);

	/** Fires the sensor: raises the zone's alarm (or just beeps outside zones). */
	public void trip(ServerLevel level, BlockPos pos, Player culprit) {
		cooldown = COOLDOWN;
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.5f, 0.6f);
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.5f, 0.5f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
		AlarmDirector.raise(level, pos, AlarmDirector.Cause.SENSOR, culprit);
	}
}
