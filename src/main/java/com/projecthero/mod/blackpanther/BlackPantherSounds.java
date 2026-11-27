package com.projecthero.mod.blackpanther;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user: "add a nanotech sound effect for the building please"): the suit's nanotech sound, layered from vanilla recordings
 * pitched up (events {@code black_panther_nanotech_*} in {@code sounds.json}, no new audio files):
 * <ul>
 *   <li><b>start</b> -- the beacon's power-up, pitched high and short: the whir as the necklace wakes;</li>
 *   <li><b>skitter</b> -- sculk spreading, pitched up: thousands of tiny plates crawling over him (a burst every
 *       {@link #SKITTER_EVERY} ticks, rising in pitch as the suit spreads);</li>
 *   <li><b>shimmer</b> -- amethyst resonance / shimmer, pitched up: the vibranium ringing as it settles (every {@link #SHIMMER_EVERY});</li>
 *   <li><b>seal</b> -- the beacon's power hum, high: the suit locking shut at the end (with a light netherite clasp);</li>
 *   <li><b>retract</b> -- the beacon's power-down: the suit pulled back in. The retract is the build backwards and softer: the
 *       skitter falls in pitch and plays less often, and the necklace gives a small click at the end.</li>
 * </ul>
 * Played into the world from the necklace (everyone near hears it). Step 0 is played by {@link BlackPanther#suitUp} /
 * {@link BlackPanther#suitDown} themselves; the rest from {@link BlackPanther#tick} off the synced suit clock. Holds no state.
 */
public final class BlackPantherSounds {
	public static final SoundEvent NANOTECH_START = register("black_panther_nanotech_start");
	public static final SoundEvent NANOTECH_SKITTER = register("black_panther_nanotech_skitter");
	public static final SoundEvent NANOTECH_SHIMMER = register("black_panther_nanotech_shimmer");
	public static final SoundEvent NANOTECH_SEAL = register("black_panther_nanotech_seal");
	public static final SoundEvent NANOTECH_RETRACT = register("black_panther_nanotech_retract");

	/** Ticks between skitter bursts while the suit builds (the retract plays them half as often). */
	public static final int SKITTER_EVERY = 2;
	/** Ticks between shimmer rings while the suit builds (the retract: a little less often). */
	public static final int SHIMMER_EVERY = 6;

	private BlackPantherSounds() {
	}

	static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static void play(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		Vec3 at = BlackPanther.necklacePos(p);
		((ServerLevel) p.level()).playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	/** Step {@code age} (ticks since H) of the build ({@code up}) or the retract. */
	static void step(ServerPlayer p, int age, boolean up) {
		int total = up ? BlackPantherConfig.SUIT_UP_TICKS : BlackPantherConfig.SUIT_DOWN_TICKS;
		if (age < 0 || age >= total) {
			return;
		}
		float t = age / (float) total;
		if (up) {
			if (age == 0) {
				play(p, NANOTECH_START, 1.0f, 1.0f);
				play(p, NANOTECH_SHIMMER, 0.9f, 0.9f);
			} else if (age == total - 2) {
				play(p, NANOTECH_SEAL, 1.0f, 1.0f);
				play(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.5f, 1.7f);
			} else if (age < total - 3) {
				if (age % SKITTER_EVERY == 0) {
					play(p, NANOTECH_SKITTER, 0.75f, 0.9f + 0.25f * t);
				}
				if (age % SHIMMER_EVERY == 0) {
					play(p, NANOTECH_SHIMMER, 0.7f, 0.92f + 0.2f * t);
				}
			}
			return;
		}
		if (age == 0) {
			play(p, NANOTECH_RETRACT, 0.9f, 1.0f);
			play(p, NANOTECH_SHIMMER, 0.5f, 1.0f);
		} else if (age == total - 2) {
			play(p, SoundEvents.AMETHYST_BLOCK_HIT, 0.45f, 0.85f); // the necklace takes the last of it
		} else if (age < total - 3) {
			if (age % (SKITTER_EVERY * 2) == 0) {
				play(p, NANOTECH_SKITTER, 0.4f, 1.12f - 0.25f * t);
			}
			if (age % (SHIMMER_EVERY + 2) == 0) {
				play(p, NANOTECH_SHIMMER, 0.4f, 1.0f - 0.2f * t);
			}
		}
	}
}
