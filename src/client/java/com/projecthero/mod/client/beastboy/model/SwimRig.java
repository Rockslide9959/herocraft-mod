package com.projecthero.mod.client.beastboy.model;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.Util;
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
		private float lastYaw, lastNow = -1f, lastRec = -1f;
		private double lx, ly, lz;

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
	private static final long T0 = Util.getNanos();
	private static State bound;

	private SwimRig() {
	}

	/** Updates {@code p}'s swim state for this frame and binds it for the next {@link #take}. */
	public static void track(AbstractClientPlayer p, float pt) {
		if (STATES.size() > 128) {
			STATES.clear();
		}
		float now = (Util.getNanos() - T0) / 1.0e9f;
		double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
		float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
		State st = STATES.get(p.getId());
		if (st == null) {
			st = new State();
			st.lastYaw = yaw;
			st.lx = x;
			st.ly = y;
			st.lz = z;
			st.wetness = p.isInWater() ? 1f : 0f;
			STATES.put(p.getId(), st);
		}
		st.time = (p.tickCount + pt) / 20f;
		st.wet = p.isInWater() || p.isUnderWater();
		st.surfaced = st.wet && !p.isUnderWater();
		st.ground = p.onGround();
		float dt = st.lastNow < 0f ? 0f : now - st.lastNow;
		if (dt > 1.0e-4f) {
			st.lastNow = now;
			dt = Math.min(dt, 0.1f);
			double dx = x - st.lx, dy = y - st.ly, dz = z - st.lz;
			st.lx = x;
			st.ly = y;
			st.lz = z;
			float perTick = dt * 20f;
			float h = (float) Math.sqrt(dx * dx + dz * dz) / perTick;
			float v = (float) dy / perTick;
			if (h > 3f || Math.abs(v) > 3f) { // a teleport, not a swim
				h = 0f;
				v = 0f;
			}
			float sp = Mth.sqrt(h * h + v * v);
			float k = 1f - (float) Math.exp(-dt * 6f);
			st.speed += (sp - st.speed) * k;
			st.vy += (v - st.vy) * k;
			st.dist += st.speed * perTick;
			float dyaw = Mth.wrapDegrees(yaw - st.lastYaw);
			st.lastYaw = yaw;
			float rate = Mth.clamp(-dyaw * Mth.DEG_TO_RAD / dt, -4f, 4f);
			st.turn += (rate - st.turn) * (1f - (float) Math.exp(-dt * 5f));
			float target = 0f;
			if (!st.ground && sp > 0.03f) {
				target = Mth.clamp((float) Math.atan2(-v, Math.max(0.001f, h)), -1.25f, 1.25f);
			}
			st.pitch += (target - st.pitch) * (1f - (float) Math.exp(-dt * 4f));
			st.wetness += ((st.wet ? 1f : 0f) - st.wetness) * (1f - (float) Math.exp(-dt * 4f));
		} else if (st.lastNow < 0f) {
			st.lastNow = now;
		}
		st.record();
		bound = st;
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
