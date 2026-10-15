package com.projecthero.mod.cyborg.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.16.5: the whole Cyborg power for one player in one attachment ({@code projecthero:cyborg_state}): persistent,
 * {@code copyOnDeath} and synced to every client (others see the plates, the cannon beam, the Defense Matrix and the
 * poses; the owner's HUD needs the Power Core, the cooldowns and the Scan). Only {@code Cyborg} and its move classes
 * change it, always through a {@link #copy()}.
 */
public final class CyborgState {
	public static final int ANIM_NONE = 0;
	/** The Mother Box fusing with him (the 10 s conversion). */
	public static final int ANIM_CONVERT = 1;
	public static final int ANIM_PLATES_ON = 2;
	public static final int ANIM_PLATES_OFF = 3;
	public static final int ANIM_CANNON = 4;
	public static final int ANIM_BURST = 5;
	public static final int ANIM_PUNCH = 6;
	public static final int ANIM_GRAPPLE = 7;
	public static final int ANIM_DASH = 8;
	public static final int ANIM_OVERRIDE = 9;
	public static final int ANIM_SCAN = 10;
	public static final int ANIM_BOOM_TUBE = 11;
	public static final int ANIM_WAYPOINT = 12;
	public static final int ANIM_REPAIR = 13;

	public boolean hasPower;
	/** Combat form: the plates show and the kit works (H). Civilian: a hologram hides them. */
	public boolean combat;
	/** Game time the last form change started (the client plays the plates spreading / folding away from it). */
	public long formChangeAt;
	/** 0..{@code CyborgConfig.CORE_MAX}. */
	public float core;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** The Sonic Cannon is charging since this game time (0 = not charging). */
	public long chargeStart;
	/** Hovering on the boot thrusters. */
	public boolean hovering;
	/** The Defense Matrix stands until this game time. */
	public long shieldUntil;
	/** Damage the Defense Matrix can still take. */
	public float shieldHp;
	/** The saved Boom Tube waypoint: x, y, z (empty = none). */
	public List<Double> waypoint;
	/** ... and its dimension. */
	public String waypointDim;
	/** The cannon beam shows until this game time. */
	public long beamUntil;
	/** Where the beam ended: x, y, z. */
	public List<Double> beamEnd;
	/** The Scan's HUD readout runs until this game time. */
	public long scanUntil;

	public CyborgState() {
		this(false, false, 0L, 0f, 0, 0L, new HashMap<>(), 0L, false, 0L, 0f, List.of(), "", 0L, List.of(), 0L);
	}

	public CyborgState(boolean hasPower, boolean combat, long formChangeAt, float core, int animId, long animStart,
			Map<String, Long> abilityReadyAt, long chargeStart, boolean hovering, long shieldUntil, float shieldHp, List<Double> waypoint,
			String waypointDim, long beamUntil, List<Double> beamEnd, long scanUntil) {
		this.hasPower = hasPower;
		this.combat = combat;
		this.formChangeAt = formChangeAt;
		this.core = core;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.chargeStart = chargeStart;
		this.hovering = hovering;
		this.shieldUntil = shieldUntil;
		this.shieldHp = shieldHp;
		this.waypoint = List.copyOf(waypoint);
		this.waypointDim = waypointDim;
		this.beamUntil = beamUntil;
		this.beamEnd = List.copyOf(beamEnd);
		this.scanUntil = scanUntil;
	}

	public CyborgState copy() {
		return new CyborgState(hasPower, combat, formChangeAt, core, animId, animStart, abilityReadyAt, chargeStart, hovering, shieldUntil,
				shieldHp, waypoint, waypointDim, beamUntil, beamEnd, scanUntil);
	}

	public static final Codec<CyborgState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("combat", false).forGetter(s -> s.combat),
			Codec.LONG.optionalFieldOf("form_change_at", 0L).forGetter(s -> s.formChangeAt),
			Codec.FLOAT.optionalFieldOf("core", 0f).forGetter(s -> s.core),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart),
			Codec.BOOL.optionalFieldOf("hovering", false).forGetter(s -> s.hovering),
			Codec.LONG.optionalFieldOf("shield_until", 0L).forGetter(s -> s.shieldUntil),
			Codec.FLOAT.optionalFieldOf("shield_hp", 0f).forGetter(s -> s.shieldHp),
			Codec.DOUBLE.listOf().optionalFieldOf("waypoint", List.of()).forGetter(s -> s.waypoint),
			Codec.STRING.optionalFieldOf("waypoint_dim", "").forGetter(s -> s.waypointDim),
			Codec.LONG.optionalFieldOf("beam_until", 0L).forGetter(s -> s.beamUntil),
			Codec.DOUBLE.listOf().optionalFieldOf("beam_end", List.of()).forGetter(s -> s.beamEnd),
			Codec.LONG.optionalFieldOf("scan_until", 0L).forGetter(s -> s.scanUntil)
	).apply(i, CyborgState::new));
}
