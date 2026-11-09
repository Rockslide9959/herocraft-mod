package com.projecthero.mod.wolverine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * v0.17.3: the healing factor's own sounds (synthesised for the mod by {@code scratchpad/synth_wolverine_heal.js}; no
 * film audio). Played at the player through {@code level.playSound(null, ...)}, so he and everyone nearby hear them.
 * <ul>
 * <li><b>Crackle</b> ({@link WolverineSounds#HEAL_CRACKLE}): flesh knitting -- while the healing factor is actually
 * restoring health, one short burst every {@value #CRACKLE_GAP_TICKS}-{@value #CRACKLE_GAP_MAX_TICKS} ticks, quiet.</li>
 * <li><b>Sizzle</b> ({@link WolverineSounds#HEAL_SIZZLE}): the first heal after a burn / explosion or a single hit of
 * {@value #BIG_WOUND_HP}+ HP; at most every {@value #SIZZLE_GAP_TICKS} ticks (a long burn sizzles now and then, not
 * constantly). It replaces that pulse's crackle.</li>
 * <li><b>Surge</b> ({@link WolverineSounds#HEAL_SURGE}): the Death Surge and surviving a lethal fall.</li>
 * </ul>
 * The per-player throttle lives in a static map (not persisted); it is dropped on disconnect and by
 * {@code ServerStateReset}.
 */
public final class WolverineHealSounds {
	public static final int CRACKLE_GAP_TICKS = 24;
	public static final int CRACKLE_GAP_MAX_TICKS = 40;
	public static final int SIZZLE_GAP_TICKS = 50;
	/** A single hit at least this big (after his reductions) counts as a big wound: its first heal sizzles. */
	public static final float BIG_WOUND_HP = 6.0f;
	public static final float CRACKLE_VOLUME = 0.16f; // v0.17.3 (user: "dont make the sound effects too loud")
	public static final float SIZZLE_VOLUME = 0.26f;
	public static final float SURGE_VOLUME = 0.45f;

	private static final class Tracker {
		long nextCrackleAt;
		long nextSizzleAt;
		boolean bigWound;
		SoundEvent lastPlayed;
		long lastPlayedAt = -1L;
		int plays;
	}

	private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

	private WolverineHealSounds() {
	}

	public static void initialize() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRACKERS.remove(handler.getPlayer().getUUID()));
	}

	/** Static scratch for {@code ServerStateReset}. */
	public static void clearSessionState() {
		TRACKERS.clear();
	}

	private static Tracker tracker(ServerPlayer player) {
		return TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
	}

	/** He just took damage: burns, explosions and big single hits make the next heal sizzle. */
	public static void onHurt(ServerPlayer player, DamageSource source, float dealt) {
		if (dealt <= 0.0f) {
			return;
		}
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION) || dealt >= BIG_WOUND_HP) {
			tracker(player).bigWound = true;
		}
	}

	/** The healing factor restored {@code healed} HP this pulse (called from {@code WolverinePassives}). */
	public static void onHealed(ServerPlayer player, float healed) {
		if (healed <= 0.0f) {
			return;
		}
		Tracker t = tracker(player);
		long now = player.level().getGameTime();
		if (t.bigWound && now >= t.nextSizzleAt) {
			t.bigWound = false;
			t.nextSizzleAt = now + SIZZLE_GAP_TICKS;
			t.nextCrackleAt = now + CRACKLE_GAP_TICKS; // let the sizzle breathe before the next crackle
			play(player, t, WolverineSounds.HEAL_SIZZLE, SIZZLE_VOLUME, 0.95f + player.getRandom().nextFloat() * 0.1f, now);
			return;
		}
		if (now >= t.nextCrackleAt) {
			t.nextCrackleAt = now + CRACKLE_GAP_TICKS + player.getRandom().nextInt(CRACKLE_GAP_MAX_TICKS - CRACKLE_GAP_TICKS + 1);
			play(player, t, WolverineSounds.HEAL_CRACKLE, CRACKLE_VOLUME, 0.9f + player.getRandom().nextFloat() * 0.25f, now);
		}
	}

	/** A big regeneration moment: the Death Surge, or surviving a lethal fall. */
	public static void surge(ServerPlayer player) {
		Tracker t = tracker(player);
		long now = player.level().getGameTime();
		t.bigWound = false;
		t.nextCrackleAt = now + 50; // the surge covers the first few heal pulses
		t.nextSizzleAt = now + 50;
		play(player, t, WolverineSounds.HEAL_SURGE, SURGE_VOLUME, 1.0f, now);
	}

	private static void play(ServerPlayer player, Tracker t, SoundEvent sound, float volume, float pitch, long now) {
		t.lastPlayed = sound;
		t.lastPlayedAt = now;
		t.plays++;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** For the gametests: the last heal sound played for this player (null if none yet). */
	public static SoundEvent lastPlayed(ServerPlayer player) {
		Tracker t = TRACKERS.get(player.getUUID());
		return t == null ? null : t.lastPlayed;
	}

	/** For the gametests: how many heal sounds have played for this player. */
	public static int plays(ServerPlayer player) {
		Tracker t = TRACKERS.get(player.getUUID());
		return t == null ? 0 : t.plays;
	}

	/** For the gametests: game time of the last heal sound (-1 if none yet). */
	public static long lastPlayedAt(ServerPlayer player) {
		Tracker t = TRACKERS.get(player.getUUID());
		return t == null ? -1L : t.lastPlayedAt;
	}
}
