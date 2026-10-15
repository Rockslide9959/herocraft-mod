package com.projecthero.mod.client.hulk;

import com.projecthero.mod.hulk.HulkMind;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.16.4 (user): "make it so that the place has the creepy cave sounds playing all the time so that the player feels eerie
 * and put off". Plays while the local player is in the Gamma-origin mind ({@link HulkMind#DIMENSION}) or shut in the mind
 * hall of a death rampage ({@link HulkMindScreen}):
 * <ul>
 *   <li>a low drone that never stops -- two vanilla ambient loops pitched right down (the soul sand valley wind and the
 *       basalt deltas rumble), faded in and out so arriving and leaving never click;</li>
 *   <li>the cave sounds every few seconds (3-8 s), from a random direction around him (listener-relative, so they work the
 *       same in the real mind dimension and in the hall drawn over the overworld), now and then a deep rumble or a distant
 *       moan;</li>
 *   <li>a slow, heavy heartbeat now and then (two thumps).</li>
 * </ul>
 * The music is swapped for a sparse eerie mood track ({@link #MUSIC}, through {@code MinecraftHulkMindMusicMixin}). The
 * dimension's own biome ({@code projecthero:hulk_mind}) adds the dark fog, falling ash, the vanilla cave mood and rumbles on
 * top. Everything is category AMBIENT, so the Ambient/Environment volume slider rules it. Leaving stops it all (the loops
 * fade out and stop themselves).
 */
public final class HulkMindAmbience {
	/** The music while in the mind: the soul sand valley's mood -- long, low, unsettling -- every 15-45 s. */
	public static final Music MUSIC = new Music(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 300, 900, true);

	private static Drone wind;
	private static Drone rumble;
	private static int nextCave;
	private static int nextHeart;
	private static int heartSecond = -1;

	private HulkMindAmbience() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(HulkMindAmbience::tick);
	}

	/** The local player is somewhere the eerie ambience belongs. */
	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return false;
		}
		return mc.level.dimension() == HulkMind.DIMENSION || HulkMindScreen.showing();
	}

	private static void tick(Minecraft mc) {
		boolean on = active();
		if (!on) {
			// the loops fade themselves out; nothing new starts
			nextCave = 0;
			nextHeart = 0;
			heartSecond = -1;
			if (wind != null && wind.isStopped()) {
				wind = null;
			}
			if (rumble != null && rumble.isStopped()) {
				rumble = null;
			}
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		RandomSource r = mc.level.getRandom();
		if (wind == null || !mc.getSoundManager().isActive(wind)) {
			wind = new Drone(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP.value(), 0.85f, 0.55f);
			mc.getSoundManager().play(wind);
		}
		if (rumble == null || !mc.getSoundManager().isActive(rumble)) {
			rumble = new Drone(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value(), 0.45f, 0.5f);
			mc.getSoundManager().play(rumble);
		}
		// the cave sounds: every 3-8 s, from somewhere around him
		if (nextCave <= 0) {
			nextCave = 60 + r.nextInt(100);
		} else if (--nextCave == 0) {
			float roll = r.nextFloat();
			Holder<SoundEvent> ev = roll < 0.72f ? SoundEvents.AMBIENT_CAVE
					: roll < 0.88f ? SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS : SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS;
			around(mc, ev.value(), 0.7f + 0.3f * r.nextFloat(), 0.6f + 0.35f * r.nextFloat(), r);
		}
		// the heartbeat: a slow pair of thumps every 9-20 s
		if (nextHeart <= 0) {
			nextHeart = 180 + r.nextInt(220);
		} else if (--nextHeart == 0) {
			heart(mc, 1.0f);
			heartSecond = 9;
		}
		if (heartSecond > 0 && --heartSecond == 0) {
			heart(mc, 0.7f);
			heartSecond = -1;
		}
	}

	private static void heart(Minecraft mc, float volume) {
		mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.WARDEN_HEARTBEAT.getLocation(), SoundSource.AMBIENT, volume, 0.55f,
				SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
	}

	/** A one-off sound from a random direction around the listener, 4-10 blocks out. */
	private static void around(Minecraft mc, SoundEvent ev, float volume, float pitch, RandomSource r) {
		double a = r.nextDouble() * Math.PI * 2.0;
		double d = 4.0 + r.nextDouble() * 6.0;
		mc.getSoundManager().play(new SimpleSoundInstance(ev.getLocation(), SoundSource.AMBIENT, volume, pitch,
				SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, Math.cos(a) * d, (r.nextDouble() - 0.3) * 3.0,
				Math.sin(a) * d, true));
	}

	/** A looping drone at the listener that fades in, and fades out and stops itself once the ambience is no longer wanted. */
	private static final class Drone extends AbstractTickableSoundInstance {
		private final float target;

		Drone(SoundEvent ev, float target, float pitch) {
			super(ev, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.target = target;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.pitch = pitch;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.x = 0.0;
			this.y = 0.0;
			this.z = 0.0;
		}

		@Override
		public void tick() {
			if (active()) {
				this.volume = Math.min(target, this.volume + target / 40.0f); // 2 s in
			} else {
				this.volume = Math.max(0.0f, this.volume - target / 20.0f); // 1 s out
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
