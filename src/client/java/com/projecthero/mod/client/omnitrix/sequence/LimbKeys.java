package com.projecthero.mod.client.omnitrix.sequence;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.HipBend;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;

/**
 * v0.17.3 (the v1b touch-ups of Wildmutt, Diamondhead and Ripjaws; user: "make sure their limbs don't go weird or detach from the
 * body"): a keyframed whole-body pose track for a humanoid show body, the same idea as {@code FourArmsShow.POSE} without Four Arms'
 * extra pair. One row per key, {tick, channels...}; every key starts as a copy of the one before, so a key only names what moves.
 * Keys flow through each other (Catmull-Rom tangents); a {@link #hold()} key eases in and out (the curve stops on it); a key under
 * 0.05 ticks after the one before is a cut (snaps). Because the whole body is always sampled from one track, nothing ever snaps back
 * to the vanilla pose between two beats of the same shot.
 *
 * <p>{@link #apply} puts the body together from its rest place every frame: the torso leans about the hips ({@link HipBend}, so it
 * never leaves the legs), the head and shoulders follow it, {@code drop} lowers the whole body (for bent knees), and the elbows and
 * knees bend through {@link BentLimbs} (true mitred joints: no gap, no inside-out faces).
 */
final class LimbKeys {
	/** Channels (radians unless noted). Arms: x, y, z rotation, elbow flex, twist; legs the same with the knee. */
	static final int HX = 1, HY = 2, HZ = 3, LEAN = 4, DROP = 5, SHRUG = 6, YAW = 7, ROLL = 8, RA = 9, LA = 14, RL = 19, LL = 24, N = 29;

	private final List<float[]> rows = new ArrayList<>();
	private final List<Boolean> holds = new ArrayList<>();

	LimbKeys at(float tick) {
		float[] r = rows.isEmpty() ? new float[N] : rows.get(rows.size() - 1).clone();
		r[0] = tick;
		rows.add(r);
		holds.add(false);
		return this;
	}

	private float[] cur() {
		return rows.get(rows.size() - 1);
	}

	LimbKeys hold() {
		holds.set(holds.size() - 1, true);
		return this;
	}

	LimbKeys set(int ch, float... v) {
		System.arraycopy(v, 0, cur(), ch, v.length);
		return this;
	}

	LimbKeys head(float x, float y, float z) {
		return set(HX, x, y, z);
	}

	/** The torso's pitch about the hips (+ forward). */
	LimbKeys lean(float v) {
		return set(LEAN, v);
	}

	/** The torso's turn (yaw) and sideways tilt (roll), radians. */
	LimbKeys twist(float yaw, float roll) {
		return set(YAW, yaw, roll);
	}

	/** The whole body lowered (px), for bent knees. */
	LimbKeys drop(float px) {
		return set(DROP, px);
	}

	/** The shoulders raised (- px) or dropped. */
	LimbKeys shrug(float px) {
		return set(SHRUG, px);
	}

	LimbKeys rArm(float x, float y, float z, float flex, float twist) {
		return set(RA, x, y, z, flex, twist);
	}

	LimbKeys lArm(float x, float y, float z, float flex, float twist) {
		return set(LA, x, y, z, flex, twist);
	}

	/** Both arms, the left mirroring the right. */
	LimbKeys arms(float x, float y, float z, float flex, float twist) {
		return rArm(x, y, z, flex, twist).lArm(x, -y, -z, flex, -twist);
	}

	LimbKeys rLeg(float x, float y, float z, float knee, float twist) {
		return set(RL, x, y, z, knee, twist);
	}

	LimbKeys lLeg(float x, float y, float z, float knee, float twist) {
		return set(LL, x, y, z, knee, twist);
	}

	LimbKeys legs(float x, float y, float z, float knee, float twist) {
		return rLeg(x, y, z, knee, twist).lLeg(x, -y, -z, knee, -twist);
	}

	private boolean cutBefore(int i) {
		return i > 0 && i < rows.size() && rows.get(i)[0] - rows.get(i - 1)[0] < 0.05f;
	}

	private float tangent(int i, int c) {
		int last = rows.size() - 1;
		if (i <= 0 || i >= last || holds.get(i) || cutBefore(i) || cutBefore(i + 1)) {
			return 0f;
		}
		float[] a = rows.get(i - 1), b = rows.get(i + 1);
		return (b[c] - a[c]) / (b[0] - a[0]);
	}

	/** Every channel at {@code t}. */
	float[] sample(float t) {
		int i = 0;
		while (i < rows.size() - 2 && t >= rows.get(i + 1)[0]) {
			i++;
		}
		float[] a = rows.get(i), b = rows.get(Math.min(i + 1, rows.size() - 1));
		float dt = b[0] - a[0];
		if (dt <= 0.05f || t <= a[0]) {
			return (t >= b[0] ? b : a).clone();
		}
		float s = Mth.clamp((t - a[0]) / dt, 0f, 1f);
		float s2 = s * s, s3 = s2 * s;
		float h00 = 2 * s3 - 3 * s2 + 1, h10 = s3 - 2 * s2 + s, h01 = -2 * s3 + 3 * s2, h11 = s3 - s2;
		float[] out = new float[N];
		out[0] = t;
		for (int c = 1; c < N; c++) {
			out[c] = h00 * a[c] + h10 * dt * tangent(i, c) + h01 * b[c] + h11 * dt * tangent(i + 1, c);
		}
		return out;
	}

	/**
	 * The body from {@code k} (a {@link #sample}): every part from its rest place, the torso leaning about its hips, the elbows and
	 * knees bent. Call {@link #copyLayers} after any extra tremble.
	 */
	static void apply(HumanoidModel<?> m, float[] k) {
		float drop = k[DROP];
		m.body.xRot = 0f;
		m.body.yRot = k[YAW];
		m.body.zRot = k[ROLL];
		m.body.x = 0f;
		m.body.y = drop;
		m.body.z = 0f;
		m.head.x = 0f;
		m.head.y = drop;
		m.head.z = 0f;
		// the shoulders turn with the torso's yaw (round the spine)
		float cy = Mth.cos(k[YAW]), sy = Mth.sin(k[YAW]);
		m.rightArm.x = -5f * cy;
		m.rightArm.z = 5f * sy;
		m.leftArm.x = 5f * cy;
		m.leftArm.z = -5f * sy;
		m.rightArm.y = m.leftArm.y = 2f + drop + k[SHRUG];
		// the roll tips one shoulder up and the other down
		m.rightArm.y += 5f * Mth.sin(k[ROLL]);
		m.leftArm.y -= 5f * Mth.sin(k[ROLL]);
		m.rightLeg.x = -1.9f;
		m.leftLeg.x = 1.9f;
		m.rightLeg.y = m.leftLeg.y = 12f + drop;
		m.rightLeg.z = m.leftLeg.z = 0f;
		HipBend.pitch(m, 1f, k[LEAN]);
		m.head.xRot = k[HX];
		m.head.yRot = k[HY] + k[YAW];
		m.head.zRot = k[HZ];
		m.rightArm.xRot = k[RA];
		m.rightArm.yRot = k[RA + 1] + k[YAW];
		m.rightArm.zRot = k[RA + 2];
		m.leftArm.xRot = k[LA];
		m.leftArm.yRot = k[LA + 1] + k[YAW];
		m.leftArm.zRot = k[LA + 2];
		m.rightLeg.xRot = k[RL];
		m.rightLeg.yRot = k[RL + 1];
		m.rightLeg.zRot = k[RL + 2];
		m.leftLeg.xRot = k[LL];
		m.leftLeg.yRot = k[LL + 1];
		m.leftLeg.zRot = k[LL + 2];
		BentLimbs.bend(BentLimbs.RIGHT_ARM, k[RA + 3], k[RA + 4]);
		BentLimbs.bend(BentLimbs.LEFT_ARM, k[LA + 3], k[LA + 4]);
		BentLimbs.bend(BentLimbs.RIGHT_LEG, k[RL + 3], k[RL + 4]);
		BentLimbs.bend(BentLimbs.LEFT_LEG, k[LL + 3], k[LL + 4]);
	}

	/** Sleeves, pants, jacket and hat follow their parts. */
	static void copyLayers(HumanoidModel<?> m) {
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.rightPants.copyFrom(m.rightLeg);
			pm.leftPants.copyFrom(m.leftLeg);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}
}
