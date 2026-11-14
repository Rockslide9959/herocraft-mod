package com.projecthero.mod.omnitrix;

import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * v0.17.4 (user: "right now its way too easy ... i want master control to be something that can be activated at any point if the
 * player knows how to do it, make it kinda complicated"; they picked a gesture ritual): the secret way to wake Master Control.
 * With the watch raised (human form, either watch), in order:
 * <ol>
 *   <li>look straight up ({@link OmnitrixConfig#RITUAL_LOOK_PITCH}) and hold it {@link OmnitrixConfig#RITUAL_LOOK_TICKS};</li>
 *   <li>still looking up, crouch {@link OmnitrixConfig#RITUAL_CROUCHES} times (the sneak key down, each press counts);</li>
 *   <li>turn the dial one full round LEFT (scroll up, {@code dir -1}): one step per alien on the dial, back where it started;</li>
 *   <li>then one full round RIGHT (scroll down, {@code dir +1});</li>
 *   <li>the core beeps three times, a second apart (the third higher) -- slap the core on the third beep, within
 *       {@link OmnitrixConfig#RITUAL_WINDOW_TICKS} either side of it.</li>
 * </ol>
 * The slap on time plays the Master Control dial sequence ({@link Omnitrix#startMasterSequence}); a slap off the beat is a
 * mistransform ({@link Omnitrix#FORCE_GLITCH}). Any step more than {@link OmnitrixConfig#RITUAL_STEP_TICKS} after the one before,
 * a dial step the wrong way (or before the crouches), the look dropping before the crouches are done, the watch going down or a
 * transformation quietly start it over -- no text ever says what comes next. Each finished step ticks faintly and the core
 * glows a little brighter ({@link #glow}).
 *
 * <p>Tracked server-side in the wearer's persisted, synced {@link OmnitrixState#timers} (so no static map: the client reads
 * the stage for the glow, and the keys go when the watch is raised or lowered, {@link #clear}).
 */
public final class OmnitrixRitual {
	/** The stage reached: absent = none, 1 looked up, 2 crouched, 3 turned left, 4 turned right (the beeps). */
	public static final String STAGE = "omnitrix.ritual";
	/** The game time the last stage was reached (stage 4: the beeps count from it). */
	public static final String AT = "omnitrix.ritual_at";
	/** Crouches / dial steps made in this stage, or beeps played in stage 4. */
	public static final String COUNT = "omnitrix.ritual_n";
	/** 1 while the sneak key was down at the last tick (the crouch edge). */
	public static final String SNEAK = "omnitrix.ritual_sneak";
	/** The game time the look straight up began (before stage 1). */
	public static final String LOOK = "omnitrix.ritual_look";

	public static final int LOOKED = 1, CROUCHED = 2, LEFT = 3, BEEPS = 4;

	/** What a slap during the beeps did. */
	public enum Slap {
		/** Not in the beeps: an ordinary slap. */
		NONE,
		/** On the third beep: Master Control. */
		WAKE,
		/** Off the beat: a mistransform. */
		MISS
	}

	private OmnitrixRitual() {
	}

	public static int stage(OmnitrixState s) {
		return (int) s.readyAt(STAGE);
	}

	/** Drops every ritual key from {@code n} (a copy about to be saved). True if there was any. */
	public static boolean clear(OmnitrixState n) {
		boolean any = false;
		for (String k : new String[] { STAGE, AT, COUNT, SNEAK, LOOK }) {
			any |= n.timers.remove(k) != null;
		}
		return any;
	}

	private static void reset(ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		if (clear(n)) {
			Omnitrix.save(p, n);
		}
	}

	/** Whether the ritual can run now: the watch up and its core up, human, Master Control off, no sequence or slap under way. */
	private static boolean open(ServerPlayer p, OmnitrixState s, long now) {
		return s.hasPower && s.selecting && !s.transformed() && !Omnitrix.masterControl(p) && !Omnitrix.masterSequence(p)
				&& Omnitrix.coreUp(s, now);
	}

	private static void advance(ServerPlayer p, int stage, long now, boolean sneak) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(LOOK);
		n.timers.put(STAGE, (long) stage);
		n.timers.put(AT, now);
		n.timers.put(COUNT, 0L);
		n.timers.put(SNEAK, sneak ? 1L : 0L);
		Omnitrix.save(p, n);
		// a faint tick for the wearer alone: something answered
		OmnitrixSfx.notify(p, OmnitrixSounds.SELECT, 0.45f, 1.6f + 0.1f * stage);
	}

	/** Every server tick ({@link Omnitrix#tick}): the look, the crouches, the timeouts and the beeps. */
	public static void tick(ServerPlayer p, OmnitrixState s, long now) {
		int stage = stage(s);
		boolean tracking = stage > 0 || s.timers.containsKey(LOOK);
		if (!open(p, s, now)) {
			if (tracking) {
				reset(p);
			}
			return;
		}
		float pitch = p.getXRot();
		boolean sneak = p.isShiftKeyDown();
		long at = s.readyAt(AT);
		switch (stage) {
			case 0 -> {
				if (pitch <= OmnitrixConfig.RITUAL_LOOK_PITCH) {
					if (!s.timers.containsKey(LOOK)) {
						OmnitrixState n = s.copy();
						n.timers.put(LOOK, now);
						Omnitrix.save(p, n);
					} else if (now - s.readyAt(LOOK) >= OmnitrixConfig.RITUAL_LOOK_TICKS) {
						advance(p, LOOKED, now, sneak);
					}
				} else if (s.timers.containsKey(LOOK) && pitch > OmnitrixConfig.RITUAL_LOOK_KEEP_PITCH) {
					reset(p);
				}
			}
			case LOOKED -> {
				if (pitch > OmnitrixConfig.RITUAL_LOOK_KEEP_PITCH || now - at > OmnitrixConfig.RITUAL_STEP_TICKS) {
					reset(p);
					return;
				}
				boolean was = s.readyAt(SNEAK) == 1L;
				if (sneak != was) {
					long count = s.readyAt(COUNT) + (sneak ? 1 : 0);
					if (count >= OmnitrixConfig.RITUAL_CROUCHES) {
						advance(p, CROUCHED, now, sneak);
					} else {
						OmnitrixState n = s.copy();
						n.timers.put(SNEAK, sneak ? 1L : 0L);
						n.timers.put(COUNT, count);
						Omnitrix.save(p, n);
					}
				}
			}
			case CROUCHED, LEFT -> {
				if (now - at > OmnitrixConfig.RITUAL_STEP_TICKS) {
					reset(p);
				}
			}
			case BEEPS -> {
				if (now > beepAt(at, OmnitrixConfig.RITUAL_BEEPS) + OmnitrixConfig.RITUAL_LATE_TICKS) {
					reset(p); // never slapped: the moment passed
					return;
				}
				long played = s.readyAt(COUNT);
				if (played < OmnitrixConfig.RITUAL_BEEPS && now >= beepAt(at, (int) played + 1)) {
					OmnitrixState n = s.copy();
					n.timers.put(COUNT, played + 1);
					Omnitrix.save(p, n);
					boolean last = played + 1 == OmnitrixConfig.RITUAL_BEEPS;
					// the watch's own warning blip; the third rings higher and louder -- that is the one
					OmnitrixSfx.notify(p, OmnitrixSounds.WARNING, last ? 1.4f : 1.0f, last ? 2.0f : 1.0f);
				}
			}
			default -> reset(p);
		}
	}

	/** The game time of beep {@code k} (1-based) for a ritual that reached the beeps at {@code at}. */
	public static long beepAt(long at, int k) {
		return at + (long) k * OmnitrixConfig.RITUAL_BEEP_TICKS;
	}

	/**
	 * A dial step that went through ({@link Omnitrix#cycle}, already saved): counts toward the left round, then the right round,
	 * and anything out of order starts over.
	 */
	public static void onDial(ServerPlayer p, int dir) {
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		int stage = stage(s);
		if (stage != CROUCHED && stage != LEFT) {
			if (stage > 0 || s.timers.containsKey(LOOK)) {
				reset(p); // a turn before the crouches, or during the beeps
			}
			return;
		}
		if (now - s.readyAt(AT) > OmnitrixConfig.RITUAL_STEP_TICKS) {
			reset(p);
			return;
		}
		int want = stage == CROUCHED ? -1 : 1;
		if (Integer.signum(dir) != want) {
			reset(p); // the wrong way
			return;
		}
		long count = s.readyAt(COUNT) + 1;
		if (count >= Math.max(1, Aliens.playable(p).size())) {
			advance(p, stage == CROUCHED ? LEFT : BEEPS, now, p.isShiftKeyDown());
		} else {
			OmnitrixState n = s.copy();
			n.timers.put(COUNT, count);
			Omnitrix.save(p, n);
		}
	}

	/**
	 * A slap of the raised core ({@link Omnitrix#slap}): during the beeps, on the third beep (within the window) wakes Master
	 * Control, anywhere else misses. The ritual is over either way. {@link Slap#NONE} outside the beeps.
	 */
	public static Slap onSlap(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if (stage(s) != BEEPS) {
			return Slap.NONE;
		}
		long now = p.level().getGameTime();
		long third = beepAt(s.readyAt(AT), OmnitrixConfig.RITUAL_BEEPS);
		reset(p);
		return Math.abs(now - third) <= OmnitrixConfig.RITUAL_WINDOW_TICKS ? Slap.WAKE : Slap.MISS;
	}

	/**
	 * Client-safe (the synced state): how much brighter the raised core glows for the ritual -- a little more per stage, and a
	 * pulse on each beep (the third the strongest). 0 with no ritual under way.
	 */
	public static float glow(OmnitrixState s, long now, float partial) {
		int stage = stage(s);
		if (stage <= 0 || !s.selecting) {
			return 0f;
		}
		float k = 0.12f * Math.min(stage, BEEPS);
		if (stage == BEEPS) {
			long at = s.readyAt(AT);
			for (int b = 1; b <= OmnitrixConfig.RITUAL_BEEPS; b++) {
				float age = now + partial - beepAt(at, b);
				if (age >= 0f && age < 8f) {
					k += (b == OmnitrixConfig.RITUAL_BEEPS ? 0.9f : 0.45f) * (1f - age / 8f);
				}
			}
		}
		return Mth.clamp(k, 0f, 1.4f);
	}
}
