package com.projecthero.mod.grave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Everything the Zombie Raid progression stores per player, in one isolated namespaced attachment
 * ({@code projecthero:gravebound_state}). Completely separate from Thor's attachments, from
 * {@code ExperimentalState} and from {@code TonyStarkState}.
 *
 * <p>The curse lives here rather than as a {@link net.minecraft.world.effect.MobEffect} on purpose.
 * The design requires it to survive logging out, dying, changing dimension, restarting the server,
 * <em>and drinking milk</em> (spec section 11). A status effect fails four of those five: milk clears
 * it outright, and a fresh player entity after death/relog starts with no effects. Persistent,
 * copy-on-death attachment data satisfies all five by construction -- there is simply no vanilla
 * mechanic that can reach it. The timer is displayed on the HUD instead of via an effect icon
 * (section 13 explicitly allows either).
 *
 * <p>{@link #curseTicksLeft} counts down only while the player is online and ticking, which is what
 * makes it "20 minutes of active gameplay" rather than 20 minutes of wall clock: logging out simply
 * stops decrementing it (section 11).
 */
public final class GraveboundState {
	/** Zero means no curse. Any positive value means the curse is active with that long to run. */
	public int curseTicksLeft;
	/** {@link CurseSource#name()} of whatever applied the current (or most recent) curse. */
	public String curseSource;
	/** True once the dramatic "YOU HAVE BEEN CURSED" intro has been shown for the current curse. */
	public boolean curseAnnounced;
	/** Ticks until the next ambience beat, so the atmosphere never spams (spec section 13). */
	public int nextAmbientTicks;

	/** Completed Zombie Raids -- drives the Gravewalker advancement and repeat-raid access. */
	public int raidsCompleted;
	/** True once the first-clear Heart of the Grave has been handed over, ever. */
	public boolean heartOfTheGraveGranted;
	/** True while an eaten Heart of the Grave is holding a one-shot totem-of-undying revive in reserve. */
	public boolean heartTotemArmed;

	public GraveboundState() {
		this(0, CurseSource.COMMAND.name(), false, 0, 0, false, false);
	}

	public GraveboundState(int curseTicksLeft, String curseSource, boolean curseAnnounced, int nextAmbientTicks,
			int raidsCompleted, boolean heartOfTheGraveGranted, boolean heartTotemArmed) {
		this.curseTicksLeft = curseTicksLeft;
		this.curseSource = curseSource == null ? CurseSource.COMMAND.name() : curseSource;
		this.curseAnnounced = curseAnnounced;
		this.nextAmbientTicks = nextAmbientTicks;
		this.raidsCompleted = raidsCompleted;
		this.heartOfTheGraveGranted = heartOfTheGraveGranted;
		this.heartTotemArmed = heartTotemArmed;
	}

	public GraveboundState copy() {
		return new GraveboundState(curseTicksLeft, curseSource, curseAnnounced, nextAmbientTicks,
				raidsCompleted, heartOfTheGraveGranted, heartTotemArmed);
	}

	public boolean cursed() {
		return curseTicksLeft > 0;
	}

	public CurseSource source() {
		return CurseSource.byName(curseSource);
	}

	public static final Codec<GraveboundState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("curse_ticks", 0).forGetter(s -> s.curseTicksLeft),
			Codec.STRING.optionalFieldOf("curse_source", CurseSource.COMMAND.name()).forGetter(s -> s.curseSource),
			Codec.BOOL.optionalFieldOf("curse_announced", false).forGetter(s -> s.curseAnnounced),
			Codec.INT.optionalFieldOf("next_ambient", 0).forGetter(s -> s.nextAmbientTicks),
			Codec.INT.optionalFieldOf("raids_completed", 0).forGetter(s -> s.raidsCompleted),
			Codec.BOOL.optionalFieldOf("heart_granted", false).forGetter(s -> s.heartOfTheGraveGranted),
			Codec.BOOL.optionalFieldOf("heart_totem_armed", false).forGetter(s -> s.heartTotemArmed)
	).apply(instance, GraveboundState::new));
}
