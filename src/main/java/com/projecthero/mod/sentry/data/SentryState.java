package com.projecthero.mod.sentry.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;

/**
 * v0.17.5 Sentry &amp; the Void: the whole power for one player in one attachment ({@code projecthero:sentry_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the suit layer, the HUD, the takeover view and the Void's dark sky read it). Only
 * {@code Sentry} / {@code VoidTakeover} change it, through a {@link #copy()} saved at once; the two meters tick in place and are re-sent
 * every few ticks. All times are game times (0 = never / none). Saved as a plain compound so fields can be added freely.
 */
public final class SentryState {
	public boolean hasPower;
	/** The Sun meter (fuels the Sentry moves), 0..100. */
	public float sun;
	/** The Void meter (the danger), 0..100. */
	public float voidLevel;
	/** Void Form (Shift+V, or forced by the takeover). */
	public boolean voidForm;
	public boolean flying;
	/** Holding V (Calm). */
	public boolean calming;
	/** Move id -> game time it is ready again. */
	public Map<String, Long> abilityReadyAt = new HashMap<>();
	/** Shift+V (Let it out) is locked until this time (5 minutes after the Void is thrown out). */
	public long letOutLockedUntil;
	/** v0.17.5 rework: the Let It Out trial (the "Hold the Light" minigame) started at (0 = none running). */
	public long letOutTrialStart;
	/** When Void Form last went on or off (the suit fades between the Sentry and the Void suit from here). */
	public long formChangedAt;

	// ---------------------------------------------------------------- running moves (for the look)
	/** Solar Gaze (moveset 2; was the Sunbeam) held since (0 = off). Save key kept. */
	public long beamStart;
	/** A Million Suns charging since (0 = off). */
	public long sunsStart;
	/** Molecular Shield (moveset 2; was the Sentinel Shield) up until. */
	public long shieldUntil;
	/** Shadow Eclipse (moveset 2; was Nightfall) up until. Save key kept. */
	public long nightfallUntil;
	/** Disassemble (moveset 2; was the Molecular Grip): the held creature's entity id (-1 none) and since when. */
	public int gripTarget = -1;
	public long gripStart;
	/** Moveset 2: a short body pose a move plays (one of the POSE_ ids, 0 none), since when and for how long (every viewer's model reads it). */
	public int pose;
	public long poseAt;
	public int poseTicks;
	/** The golden kneel after the Void is thrown out, until. */
	public long kneelUntil;

	// ---------------------------------------------------------------- the takeover
	/** The Void has the body since (0 = no takeover). */
	public long takeoverStart;
	/** Shame Rooms cleared so far, 0..3. */
	public int room;
	/** The current room started at (server time, for the plausibility check). */
	public long roomStartedAt;
	/** The mind screen is shut until (the first 30 s, and +20 s after a failed room). */
	public long mindLockedUntil;
	/** The Void's grip, 100 .. 0: outside help drains it; at 0 a room breaks open on its own. */
	public float grip = 100f;
	/** The entity the Void is after right now (-1 none), so the owner's client faces it. */
	public int takeoverTarget = -1;

	public static final int POSE_NONE = 0;
	/** Sentry: Skyward Hurl (both arms thrown up), Sunfall leap / dive, Golden Guardian (arms spread, face up), Molecular Shield (arms crossed, then open), the Haymaker wind-up. */
	public static final int POSE_HURL = 1, POSE_LEAP = 2, POSE_DIVE = 3, POSE_GUARDIAN = 4, POSE_SHIELD = 5, POSE_HAYMAKER = 6;
	/** Void: Erase (hand out, fingers spread), Shadow Pool (pointing down), Swallow (fist closing), Nightmare (arms wide, head back), Eclipse (arms raised), Blot Out (pulling in), Shadow Clones (palms out low), Impale (a thrust). */
	public static final int POSE_ERASE = 10, POSE_POOL = 11, POSE_SPHERE = 12, POSE_NIGHTMARE = 13, POSE_ECLIPSE = 14, POSE_BLOT = 15, POSE_CLONES = 16, POSE_IMPALE = 17;

	public SentryState() {
	}

	public boolean inTakeover() {
		return takeoverStart > 0L;
	}

	public SentryState copy() {
		SentryState n = new SentryState();
		n.hasPower = hasPower;
		n.sun = sun;
		n.voidLevel = voidLevel;
		n.voidForm = voidForm;
		n.flying = flying;
		n.calming = calming;
		n.abilityReadyAt = new HashMap<>(abilityReadyAt);
		n.letOutLockedUntil = letOutLockedUntil;
		n.letOutTrialStart = letOutTrialStart;
		n.formChangedAt = formChangedAt;
		n.beamStart = beamStart;
		n.sunsStart = sunsStart;
		n.shieldUntil = shieldUntil;
		n.nightfallUntil = nightfallUntil;
		n.gripTarget = gripTarget;
		n.gripStart = gripStart;
		n.pose = pose;
		n.poseAt = poseAt;
		n.poseTicks = poseTicks;
		n.kneelUntil = kneelUntil;
		n.takeoverStart = takeoverStart;
		n.room = room;
		n.roomStartedAt = roomStartedAt;
		n.mindLockedUntil = mindLockedUntil;
		n.grip = grip;
		n.takeoverTarget = takeoverTarget;
		return n;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putBoolean("has_power", hasPower);
		t.putFloat("sun", sun);
		t.putFloat("void", voidLevel);
		t.putBoolean("void_form", voidForm);
		t.putBoolean("flying", flying);
		t.putBoolean("calming", calming);
		CompoundTag cds = new CompoundTag();
		abilityReadyAt.forEach(cds::putLong);
		t.put("ready_at", cds);
		t.putLong("let_out_locked", letOutLockedUntil);
		t.putLong("let_out_trial", letOutTrialStart);
		t.putLong("form_changed", formChangedAt);
		t.putLong("beam_start", beamStart);
		t.putLong("suns_start", sunsStart);
		t.putLong("shield_until", shieldUntil);
		t.putLong("nightfall_until", nightfallUntil);
		t.putInt("grip_target", gripTarget);
		t.putLong("grip_start", gripStart);
		t.putInt("pose", pose);
		t.putLong("pose_at", poseAt);
		t.putInt("pose_ticks", poseTicks);
		t.putLong("kneel_until", kneelUntil);
		t.putLong("takeover_start", takeoverStart);
		t.putInt("room", room);
		t.putLong("room_started", roomStartedAt);
		t.putLong("mind_locked", mindLockedUntil);
		t.putFloat("grip", grip);
		t.putInt("takeover_target", takeoverTarget);
		return t;
	}

	public static SentryState load(CompoundTag t) {
		SentryState s = new SentryState();
		s.hasPower = t.getBoolean("has_power");
		s.sun = t.getFloat("sun");
		s.voidLevel = t.getFloat("void");
		s.voidForm = t.getBoolean("void_form");
		s.flying = t.getBoolean("flying");
		s.calming = t.getBoolean("calming");
		CompoundTag cds = t.getCompound("ready_at");
		for (String k : cds.getAllKeys()) {
			s.abilityReadyAt.put(k, cds.getLong(k));
		}
		s.letOutLockedUntil = t.getLong("let_out_locked");
		s.letOutTrialStart = t.getLong("let_out_trial");
		s.formChangedAt = t.getLong("form_changed");
		s.beamStart = t.getLong("beam_start");
		s.sunsStart = t.getLong("suns_start");
		s.shieldUntil = t.getLong("shield_until");
		s.nightfallUntil = t.getLong("nightfall_until");
		s.gripTarget = t.contains("grip_target") ? t.getInt("grip_target") : -1;
		s.gripStart = t.getLong("grip_start");
		s.pose = t.getInt("pose");
		s.poseAt = t.getLong("pose_at");
		s.poseTicks = t.getInt("pose_ticks");
		s.kneelUntil = t.getLong("kneel_until");
		s.takeoverStart = t.getLong("takeover_start");
		s.room = t.getInt("room");
		s.roomStartedAt = t.getLong("room_started");
		s.mindLockedUntil = t.getLong("mind_locked");
		s.grip = t.contains("grip") ? t.getFloat("grip") : 100f;
		s.takeoverTarget = t.contains("takeover_target") ? t.getInt("takeover_target") : -1;
		return s;
	}

	public static final Codec<SentryState> CODEC = CompoundTag.CODEC.xmap(SentryState::load, SentryState::save);
}
