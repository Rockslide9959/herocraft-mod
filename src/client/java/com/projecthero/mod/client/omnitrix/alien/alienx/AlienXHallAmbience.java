package com.projecthero.mod.client.omnitrix.alien.alienx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.17.2 round 4: the sound of the Celestial Council's hall ({@link AlienXHall}): a low hum that fades in and out (the beacon's loop
 * pitched right down) and now and then a far chime. Category AMBIENT, so the Ambient/Environment slider rules it; the End's music
 * plays over it ({@code MinecraftHulkMindMusicMixin}). The world's own sounds go on, heard from the camera on the body.
 */
public final class AlienXHallAmbience {
	private static Hum hum;
	private static int nextChime;

	private AlienXHallAmbience() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(AlienXHallAmbience::tick);
	}

	private static void tick(Minecraft mc) {
		if (!AlienXHall.showing() || mc.level == null) {
			nextChime = 0;
			if (hum != null && hum.isStopped()) {
				hum = null; // it fades itself out
			}
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		if (hum == null || !mc.getSoundManager().isActive(hum)) {
			hum = new Hum();
			mc.getSoundManager().play(hum);
		}
		RandomSource r = mc.level.getRandom();
		if (nextChime <= 0) {
			nextChime = 80 + r.nextInt(120);
		} else if (--nextChime == 0) {
			mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.AMETHYST_BLOCK_CHIME.getLocation(), SoundSource.AMBIENT, 0.5f, 0.5f + r.nextFloat() * 0.5f,
					SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
		}
	}

	private static final class Hum extends AbstractTickableSoundInstance {
		private static final float TARGET = 0.55f;

		Hum() {
			super(SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.pitch = 0.5f;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public void tick() {
			if (AlienXHall.showing()) {
				this.volume = Math.min(TARGET, this.volume + TARGET / 40.0f);
			} else {
				this.volume = Math.max(0.0f, this.volume - TARGET / 20.0f);
				if (this.volume <= 0.001f) {
					stop();
				}
			}
			this.volume = Mth.clamp(this.volume, 0.0f, 1.0f);
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
