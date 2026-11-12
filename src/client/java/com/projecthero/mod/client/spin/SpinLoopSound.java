package com.projecthero.mod.client.spin;

import java.util.function.BooleanSupplier;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 the Spin: a looping sound that follows an entity while {@code keep} holds -- a ball's whirr in flight
 * ({@code SpinSounds.BALL_SPIN_LOOP}) and the Golden Rectangle's charge ({@code SpinSounds.GOLDEN_CHARGE_LOOP}). Quiet, as the user
 * asked for the Spin's sounds.
 */
public final class SpinLoopSound extends AbstractTickableSoundInstance {
	private final Entity entity;
	private final BooleanSupplier keep;

	public SpinLoopSound(SoundEvent sound, Entity entity, float volume, BooleanSupplier keep) {
		super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.keep = keep;
		this.looping = true;
		this.delay = 0;
		this.volume = volume;
		this.x = entity.getX();
		this.y = entity.getY();
		this.z = entity.getZ();
	}

	@Override
	public void tick() {
		if (entity.isRemoved() || !keep.getAsBoolean()) {
			stop();
			return;
		}
		x = entity.getX();
		y = entity.getY() + entity.getBbHeight() * 0.5;
		z = entity.getZ();
	}

	public void end() {
		stop();
	}
}
