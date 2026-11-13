package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.Util;
import net.minecraft.util.Mth;

/**
 * v0.17.3 hq round: rigging for the land mammals (wolf, cheetah, tiger, bear, mouse): quadruped gaits (walk / trot / rotary gallop with
 * duty factors and leg phase offsets), digitigrade leg targets with folding paws (solved on the box bones by {@link BoxBeast#aimLeg}),
 * and the little life signs (blinks, ear flicks, idle glances). Also a per-player smoothing of the gait so the walk -> run
 * blend never snaps. Render thread only.
 */
public final class LandRig {
	private LandRig() {
	}

	/** Leg indices. */
	static final int LF = 0, RF = 1, LH = 2, RH = 3;

	// walk (lateral sequence), trot (diagonal pairs), rotary gallop -- phase offsets of LF, RF, LH, RH
	private static final float[] WALK = { 0.25f, 0.75f, 0f, 0.5f }, TROT = { 0.5f, 0f, 0f, 0.5f }, GALLOP = { 0.6f, 0.48f, 0f, 0.1f };

	/** Per-player gait smoothing: id -> {run, last time s}. */
	private static final Map<Integer, float[]> STATE = new HashMap<>();
	private static final long T0 = Util.getNanos();

	/** Drops the smoothing (leaving a world). */
	public static void clear() {
		STATE.clear();
	}

	/**
	 * 0..1 how far from a walk into a full run, from the horizontal speed between {@code walkSpd} and {@code runSpd} (blocks a tick),
	 * scaled by how much he moves at all, and eased per player so it never jumps.
	 */
	static float run(BeastPose s, float walkSpd, float runSpd) {
		float raw = Mth.clamp((s.speed - walkSpd) / Math.max(0.01f, runSpd - walkSpd), 0f, 1f) * Mth.clamp(s.walkAmt * 1.4f, 0f, 1f);
		if (s.entityId < 0) {
			return raw;
		}
		if (STATE.size() > 256) {
			STATE.clear();
		}
		float now = (Util.getNanos() - T0) / 1.0e9f;
		float[] st = STATE.computeIfAbsent(s.entityId, k -> new float[] { raw, now });
		float dt = Mth.clamp(now - st[1], 0f, 0.1f);
		st[1] = now;
		// faster to speed up than to settle back
		float rate = raw > st[0] ? 5f : 3f;
		st[0] += (raw - st[0]) * (1f - (float) Math.exp(-dt * rate));
		return st[0];
	}

	/** The phase offset of leg {@code leg} for a gait blend: run 0 = walk, 0.5 = trot, 1 = gallop (or trot when {@code gallop} is false). */
	static float offset(int leg, float run, boolean gallop) {
		if (!gallop) {
			return Mth.lerp(BeastPose.smooth(run), WALK[leg], TROT[leg]);
		}
		if (run < 0.5f) {
			return Mth.lerp(BeastPose.smooth(run * 2f), WALK[leg], TROT[leg]);
		}
		return Mth.lerp(BeastPose.smooth((run - 0.5f) * 2f), TROT[leg], GALLOP[leg]);
	}

	/**
	 * All four feet for a gait cycle {@code phase} (cycles): {forward, lift, swing} per leg (LF, RF, LH, RH). Duty factor, stride and
	 * lift blend from the walk to the run values with {@code run}; everything scales with {@code amt} (0 = standing).
	 */
	static float[][] feet(float phase, float run, float amt, float strideWalk, float strideRun, float liftWalk, float liftRun, boolean gallop) {
		float duty = Mth.lerp(run, 0.64f, gallop ? 0.36f : 0.45f);
		float stride = Mth.lerp(run, strideWalk, strideRun) * amt;
		float lift = Mth.lerp(run, liftWalk, liftRun) * Math.min(1f, amt * 1.5f);
		float[][] out = new float[4][];
		for (int leg = 0; leg < 4; leg++) {
			out[leg] = foot(phase + offset(leg, run, gallop), duty, stride, lift);
			if (amt < 0.02f) {
				out[leg][2] = -1f;
			}
		}
		return out;
	}

	/**
	 * A foot's path through one gait cycle: {forward offset, lift, swing 0..1 (or -1 on the ground)}. Stance slides the foot back from
	 * +stride/2 to -stride/2 along the ground; swing lifts it in an arc (peaking early) and sets it down ahead.
	 */
	static float[] foot(float phase, float duty, float stride, float lift) {
		float f = phase - Mth.floor(phase);
		if (f < duty) {
			float k = f / duty;
			return new float[] { stride * (0.5f - k), 0f, -1f };
		}
		float k = (f - duty) / (1f - duty);
		float e = k * k * (3f - 2f * k);
		float arc = Mth.sin(Mth.PI * (float) Math.pow(k, 0.75f));
		return new float[] { stride * (-0.5f + e), lift * arc, k };
	}

	/** Hind leg (digitigrade): {hip, knee, hock, foot, toe tip} from the hip, the foot target and the bone lengths; the paw flexes in swing. */
	static float[][] hind(float[] hip, float[] foot, float thigh, float shin, float meta, float metaLean, float swing) {
		float flex = swing >= 0f ? Mth.sin(swing * Mth.PI) : 0f;
		// the cannon bone leans back from the paw; it folds further back while the leg swings
		float a = metaLean + flex * 0.55f;
		float[] hock = add(foot, v(0f, meta * Mth.cos(a), -meta * Mth.sin(a)));
		float[] knee = ik(hip, hock, thigh, shin, v(0f, -0.15f, 1f));
		float[] toe = add(foot, v(0f, -flex * 0.25f, 1.0f - flex * 0.4f));
		return new float[][] { hip, knee, hock, foot, toe };
	}

	/** Fore leg: {shoulder, elbow, wrist, foot, toe tip}; the wrist folds the paw back and up during swing. */
	static float[][] fore(float[] sh, float[] foot, float upper, float fore, float pastern, float swing) {
		float flex = swing >= 0f ? Mth.sin(Math.min(1f, swing * 1.15f) * Mth.PI) : 0f;
		float[] wrist = add(foot, v(0f, pastern * 0.98f, -pastern * 0.18f));
		// in swing the forearm lifts the wrist and the paw hangs folded behind it
		wrist = add(wrist, v(0f, flex * pastern * 0.35f, flex * pastern * 0.3f));
		float[] elbow = ik(sh, wrist, upper, fore, v(0f, 0.1f, -1f));
		float fa = flex * 1.45f;
		float[] pdir = v(0f, -Mth.cos(fa), -Mth.sin(fa));
		float[] paw = add(wrist, mul(pdir, pastern));
		float[] toe = add(paw, v(0f, -0.05f + flex * 0.3f, Mth.cos(fa) * 1.0f));
		return new float[][] { sh, elbow, wrist, paw, toe };
	}

	private static float hash(int a, int b) {
		int n = a * 374761393 + b * 668265263;
		n = (n ^ (n >>> 13)) * 1274126177;
		return ((n ^ (n >>> 16)) >>> 0 & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	/** 0..1 eyelid closure: a quick blink every few seconds (sometimes a double blink). */
	static float blink(float t, int seed) {
		int cyc = Mth.floor(t / 90f);
		float local = t - cyc * 90f;
		float at = 10f + hash(cyc, seed) * 70f;
		float b = Math.max(0f, 1f - Math.abs(local - at) / 2.2f);
		if (hash(cyc, seed + 7) > 0.7f) {
			b = Math.max(b, Math.max(0f, 1f - Math.abs(local - at - 6f) / 2.2f));
		}
		return BeastPose.smooth(b);
	}

	/** 0..1 a quick ear flick now and then (one ear at a time). */
	static float earFlick(float t, int seed, int side) {
		int cyc = Mth.floor(t / 55f);
		float local = t - cyc * 55f;
		if (hash(cyc, seed + side * 31) < 0.55f) {
			return 0f;
		}
		float at = 8f + hash(cyc, seed + 3 + side) * 40f;
		float k = (local - at) / 5f;
		return k < 0f || k > 1f ? 0f : Mth.sin(k * Mth.PI) * (1f - k * 0.3f);
	}

	/** An idle glance {yaw, pitch} (radians): the head drifts to look about now and then while standing still. */
	static float[] glance(float t, int seed) {
		int cyc = Mth.floor(t / 70f);
		float local = (t - cyc * 70f) / 70f;
		float y0 = (hash(cyc, seed) - 0.5f) * 0.9f, y1 = (hash(cyc + 1, seed) - 0.5f) * 0.9f;
		float p0 = (hash(cyc, seed + 9) - 0.6f) * 0.3f, p1 = (hash(cyc + 1, seed + 9) - 0.6f) * 0.3f;
		float k = BeastPose.smooth((local - 0.7f) / 0.3f);
		return new float[] { Mth.lerp(k, y0, y1), Mth.lerp(k, p0, p1) };
	}

	/** Ease in-out between a and b over the move window [p0, p1] of {@code p}. */
	static float win(float p, float p0, float p1) {
		return BeastPose.smooth((p - p0) / (p1 - p0));
	}
}
