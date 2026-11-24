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
	private final java.util.function.Predicate<LocalPlayer> flying;

	public ThorFlightSoundInstance(LocalPlayer player) {
		this(player, ThorPowers::isFlying, 0.6f, 1.0f);
	}

	/** v0.17.7: the same loop for another power's flight (Kryptonian): it stops the tick {@code flying} turns false. */
	public ThorFlightSoundInstance(LocalPlayer player, java.util.function.Predicate<LocalPlayer> flying, float volume, float pitch) {
		super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		this.flying = flying;
		this.looping = true;
		this.delay = 0;
		this.volume = volume;
		this.pitch = pitch;
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}

	@Override
	public void tick() {
		if (player.isRemoved() || !flying.test(player)) {
			this.stop();
			return;
		}
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}
}
