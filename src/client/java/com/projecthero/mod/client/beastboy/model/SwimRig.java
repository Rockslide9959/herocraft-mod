package com.projecthero.mod.client.beastboy.model;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: per-player swim smoothing for the sea animals. Read off the player once per frame ({@link #track}) and
 * handed to the animator ({@link #take}): eased speed, distance swum (so the stroke phase never jumps when the speed changes), turn
 * rate (banking, body bend), travel pitch, how wet he is (eases land and water poses into each other) and a short history of the
 * turn / speed signals, so trailing parts (octopus arms) can follow the body with a lag. Previews and harnesses without a player get
 * a {@link #synthetic} state from the pose, or {@link #bind} their own. Client render thread only; cleared on leaving a world.
 */
public final class SwimRig {
	/** Everything an animator reads. Public so previews can drive it. */
	public static final class State {
		/** Seconds (from the pose's age, so it pauses with the game). */
		public float time;
		/** Blocks swum, integrated from the eased speed: stroke phase = time * idle rate + dist * rate per block. */
		public float dist;
		/** Eased speed, blocks per tick. */
		public float speed;
		/** Eased turn rate, radians per second, + = turning left (toward +X). */
		public float turn;
		/** Eased travel pitch, radians, + = nose down. */
		public float pitch;
		/** In water / at the surface (eyes out of the water) / standing on the ground. */
		public boolean wet, surfaced, ground;
		/** 0 = on land .. 1 = in water, eased over about half a second. */
		public float wetness;
		/** Eased vertical speed, blocks per tick. */
		public float vy;

		private static final int H = 64;
		private final float[] hT = new float[H], hTurn = new float[H], hSpeed = new float[H];
		private int head, count;
		private float lastYaw, lastAge, lastWetAge, lastRec = -1f;
		/** Eased horizontal speed, blocks per tick. */
		public float hspeed;

		/** Speed 0..1 against a fast swim (0.3 blocks / tick). */
		public float speed01() {
			return Mth.clamp(speed / 0.3f, 0f, 1f);
		}

		/** The turn rate {@code delay} seconds ago (interpolated; the oldest sample when the history is shorter). */
		public float lagTurn(float delay) {
			return lag(hTurn, delay, turn);
		}

		public float lagSpeed(float delay) {
			return lag(hSpeed, delay, speed);
		}

		private float lag(float[] arr, float delay, float now) {
			if (count == 0 || delay <= 0f) {
				return now;
			}
			float want = hT[(head - 1 + H) % H] - delay;
			for (int k = 1; k <= count; k++) {
				int i = (head - k + H) % H;
				if (hT[i] <= want) {
					if (k == 1) {
						return arr[i];
					}
					int j = (head - k + 1 + H) % H;
					float span = hT[j] - hT[i];
					float f = span < 1.0e-5f ? 0f : (want - hT[i]) / span;
					return Mth.lerp(f, arr[i], arr[j]);
				}
			}
			return arr[(head - count + H) % H];
		}

		/** Pushes the current turn / speed into the lag history (previews building a state by hand). */
		public void recordForPreview() {
			record();
		}

		void record() {
			if (lastRec >= 0f && time - lastRec < 0.025f && time >= lastRec) {
				return;
			}
			lastRec = time;
			hT[head] = time;
			hTurn[head] = turn;
			hSpeed[head] = speed;
			head = (head + 1) % H;
			count = Math.min(H, count + 1);
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	private static State bound;

	private SwimRig() {
	}

	/**
	 * Updates {@code p}'s swim state for this frame and binds it for the next {@link #take}.
	 *
	 * <p>v0.17.4 (user: "the swimming animations for the sea creatures shake sometimes and they just tilt in general"): rebuilt on the
	 * game clock. The old version took the speed and travel pitch from frame-to-frame changes of the interpolated position over
	 * wall-clock time, which is noisy (frame pacing never lines up with the 20 Hz ticks), and turned that into a pitch with
	 * {@code atan2(-vertical, horizontal)}: hovering or crawling along at the surface, the player's little up-and-down bob over almost
	 * no horizontal speed came out as a nose swinging up to +-70 degrees and back -- the shake, and the constant tilt. Now the speed
	 * comes from the ticks' own steps (x - xo), every value eases on {@code tickCount + partialTick} (frame-rate independent, frozen
	 * while paused, inert across repeated passes of one frame), the pitch only follows the swim once he's really moving (and is held
	 * shallow when the motion is mostly vertical or he's at the surface), and the turn rate reads the body yaw, which now eases toward
	 * the look in the water ({@code BeastBoySwimTurnMixin}) instead of snapping to catch up with it.
	 */
	public static void track(AbstractClientPlayer p, float pt) {
		if (STATES.size() > 128) {
			STATES.clear();
		}
		float age = p.tickCount + pt;
		float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
		boolean inWater = p.isInWater() || p.isUnderWater();
		State st = STATES.get(p.getId());
		if (st == null || age < st.lastAge || age - st.lastAge > 40f) {
			st = new State();
			st.lastYaw = yaw;
			st.lastAge = age;
			st.lastWetAge = inWater ? age : -1000f;
			st.wetness = inWater ? 1f : 0f;
			STATES.put(p.getId(), st);
		}
		st.time = age / 20f;
		st.ground = p.onGround();
		if (inWater) {
			st.lastWetAge = age;
		}
		// a leap out of the water keeps the swim pose until he lands or has been out for over a second
		st.wet = inWater || (!st.ground && age - st.lastWetAge < 25f);
		st.surfaced = inWater && !p.isUnderWater();
		float dt = Math.min(age - st.lastAge, 3f);
		if (dt > 1.0e-4f) {
			st.lastAge = age;
			// the last tick's step (blocks per tick); a teleport is not a swim
			double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
			float h = (float) Math.sqrt(dx * dx + dz * dz);
			float v = (float) dy;
			if (h > 3f || Math.abs(v) > 3f) {
				h = 0f;
				v = 0f;
			}
			float kMove = k(0.3f, dt);
			st.hspeed += (h - st.hspeed) * kMove;
			st.vy += (v - st.vy) * kMove;
			st.speed = Mth.sqrt(st.hspeed * st.hspeed + st.vy * st.vy);
			st.dist += st.speed * dt;
			float dyaw = Mth.wrapDegrees(yaw - st.lastYaw);
			st.lastYaw = yaw;
			float rate = Mth.clamp(-dyaw * Mth.DEG_TO_RAD / (dt / 20f), -4f, 4f);
			st.turn += (rate - st.turn) * k(0.25f, dt);
			float target = 0f;
			if (st.wet && !(st.ground && inWater)) {
				float raw = (float) Math.atan2(-st.vy, Math.max(0.001f, st.hspeed));
				// mostly-vertical motion (Space / Sneak on the spot) only tips him a little; a real dive or climb follows the swim
				float limit = Mth.lerp(smoothstep(0.02f, 0.1f, st.hspeed), 0.45f, 1.2f);
				target = Mth.clamp(raw, -limit, limit) * smoothstep(0.025f, 0.09f, st.speed);
				if (st.surfaced && target < 0f) {
					target *= 0.4f; // cruising at the surface stays level
				}
			}
			st.pitch += (target - st.pitch) * k(0.18f, dt);
			st.wetness += ((st.wet ? 1f : 0f) - st.wetness) * k(0.2f, dt);
		}
		st.record();
		bound = st;
	}

	/** The share of the way an exponential ease at {@code rate} per tick covers in {@code dt} ticks. */
	private static float k(float rate, float dt) {
		return 1f - (float) Math.exp(-rate * dt);
	}

	private static float smoothstep(float a, float b, float x) {
		float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** The state bound by {@link #track} / {@link #bind} (consumed), or a synthetic one from the pose. */
	public static State take(BeastPose s) {
		State b = bound;
		bound = null;
		return b != null ? b : synthetic(s);
	}

	/** Binds a hand-made state for the next draw (previews, harnesses). */
	public static void bind(State st) {
		bound = st;
	}

	/** A state from a bare pose: walk amount as the speed, its walk position as the distance. */
	public static State synthetic(BeastPose s) {
		State st = new State();
		st.time = s.age / 20f;
		st.wet = s.inWater;
		st.wetness = s.inWater ? 1f : 0f;
		st.ground = !s.inWater && !s.airborne;
		st.speed = s.walkAmt * 0.3f;
		st.dist = s.walkPos * 0.25f;
		st.vy = s.vy;
		return st;
	}

	/** Drops every player's state (leaving a world). */
	public static void clear() {
		STATES.clear();
		bound = null;
	}

	/** 0..1 blink amount at time {@code t} seconds, a quick blink every few seconds (staggered by {@code seed}). */
	public static float blink(float t, int seed) {
		float period = 3.7f + (seed % 5) * 0.45f;
		float ph = ((t + seed * 1.37f) % period + period) % period;
		float d = 0.16f;
		if (ph > d) {
			return 0f;
		}
		return Mth.sin(ph / d * Mth.PI);
	}
}
