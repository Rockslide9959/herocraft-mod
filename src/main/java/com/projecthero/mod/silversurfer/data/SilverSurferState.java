package com.projecthero.mod.silversurfer.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.1: the whole Silver Surfer power for one player in one attachment ({@code projecthero:silver_surfer_state}):
 * persistent, kept through death and synced to every client (others see the skin, the board under his feet, the beam, the
 * barrier, the phase; the owner's HUD needs the Power Cosmic and the cooldowns). Only {@code SilverSurfer} and its move
 * classes change it, always through a {@link #copy()}.
 */
public final class SilverSurferState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_BLAST = 1;
	public static final int ANIM_BARRAGE = 2;
	public static final int ANIM_THROW = 3;
	public static final int ANIM_TRANSMUTE = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_HEAL = 6;
	public static final int ANIM_NOVA = 7;
	public static final int ANIM_METEOR = 8;
	public static final int ANIM_BOND = 9;

	public boolean hasPower;
	/** 0..{@code SilverSurferConfig.COSMIC_MAX}. */
	public float cosmic;
	/** Riding the board (double-tap jump). */
	public boolean surfing;
	/** Game time the board last flew up under him / dropped away. */
	public long surfChange;
	/** Shift+X Light-Speed runs until this game time. */
	public long lightSpeedUntil;
	/** Z: the Cosmic Barrier stands until this game time (held: refreshed while Z is down, up to its maximum). */
	public long barrierUntil;
	public float barrierHp;
	/** Shift+Z: Molecular Phase runs until this game time. */
	public long phaseUntil;
	/** V: Cosmic Awareness is on. */
	public boolean awareness;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** R is held since this game time (0 = not held); past the hold threshold it is the beam. */
	public long beamStart;
	/** Where the beam ended last tick (x, y, z; empty = no beam). */
	public List<Double> beamEnd;
	/** G in flight: the ram charge runs until this game time. */
	public long ramUntil;

	public SilverSurferState() {
		this(false, 0f, false, 0L, 0L, 0L, 0f, 0L, false, 0, 0L, new HashMap<>(), 0L, List.of(), 0L);
	}

	public SilverSurferState(boolean hasPower, float cosmic, boolean surfing, long surfChange, long lightSpeedUntil, long barrierUntil,
			float barrierHp, long phaseUntil, boolean awareness, int animId, long animStart, Map<String, Long> abilityReadyAt, long beamStart,
			List<Double> beamEnd, long ramUntil) {
		this.hasPower = hasPower;
		this.cosmic = cosmic;
		this.surfing = surfing;
		this.surfChange = surfChange;
		this.lightSpeedUntil = lightSpeedUntil;
		this.barrierUntil = barrierUntil;
		this.barrierHp = barrierHp;
		this.phaseUntil = phaseUntil;
		this.awareness = awareness;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.beamStart = beamStart;
		this.beamEnd = List.copyOf(beamEnd);
		this.ramUntil = ramUntil;
	}

	public SilverSurferState copy() {
		return new SilverSurferState(hasPower, cosmic, surfing, surfChange, lightSpeedUntil, barrierUntil, barrierHp, phaseUntil, awareness,
				animId, animStart, abilityReadyAt, beamStart, beamEnd, ramUntil);
	}

	public static final Codec<SilverSurferState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("cosmic", 0f).forGetter(s -> s.cosmic),
			Codec.BOOL.optionalFieldOf("surfing", false).forGetter(s -> s.surfing),
			Codec.LONG.optionalFieldOf("surf_change", 0L).forGetter(s -> s.surfChange),
			Codec.LONG.optionalFieldOf("light_speed_until", 0L).forGetter(s -> s.lightSpeedUntil),
			Codec.LONG.optionalFieldOf("barrier_until", 0L).forGetter(s -> s.barrierUntil),
			Codec.FLOAT.optionalFieldOf("barrier_hp", 0f).forGetter(s -> s.barrierHp),
			Codec.LONG.optionalFieldOf("phase_until", 0L).forGetter(s -> s.phaseUntil),
			Codec.BOOL.optionalFieldOf("awareness", false).forGetter(s -> s.awareness),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("beam_start", 0L).forGetter(s -> s.beamStart),
			Codec.DOUBLE.listOf().optionalFieldOf("beam_end", List.of()).forGetter(s -> s.beamEnd),
			Codec.LONG.optionalFieldOf("ram_until", 0L).forGetter(s -> s.ramUntil)
	).apply(i, SilverSurferState::new));
}
