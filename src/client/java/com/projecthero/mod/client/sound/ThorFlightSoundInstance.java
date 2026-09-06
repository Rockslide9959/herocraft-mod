package com.projecthero.mod.client.sound;

import com.projecthero.mod.power.ThorPowers;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Looping wind/thunder ambience while Thor flight is active, distinct from the one-shot ability
 * sounds. Mirrors vanilla's {@code ElytraOnPlayerSoundInstance} -- a looping sound instance that
 * follows the (local) player each tick and stops itself once the condition it represents ends.
 * Reuses {@link SoundEvents#ELYTRA_FLYING} (a wind whoosh loop) rather than shipping a custom
 * sound asset, since it isn't used anywhere else in this mod.
 */
public class ThorFlightSoundInstance extends AbstractTickableSoundInstance {
	private final LocalPlayer player;

	public ThorFlightSoundInstance(LocalPlayer player) {
		super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.6f;
		this.pitch = 1.0f;
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}

	@Override
	public void tick() {
		if (player.isRemoved() || !ThorPowers.isFlying(player)) {
			this.stop();
			return;
		}
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}
}
