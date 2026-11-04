package com.projecthero.mod.galactus.orbit;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * v0.17.2 Galactus raid v2: the Devourer's code-driven skeleton, shared by the server (where his fists land, where his
 * head / arm / hand / chest hit-boxes go) and the client renderer (the bone rotations that draw it), so the two can never
 * disagree. Everything is solved from a handful of synced numbers -- his height, yaw, waist lean, a head look target and
 * one target point + weight per arm -- with a two-bone IK per arm (upper arm, then forearm + fist as one rigid piece).
 *
 * <p>Spaces: <b>file</b> space is the geo.json (units, Galactus faces -Z, his right arm at -X); <b>render</b> space is what
 * GeckoLib draws in (X mirrored: {@code (-x, y, z)}), where a bone's rotation matrix is {@code Rz * Ry * Rx}; <b>world</b>
 * space is blocks. A bone at rest points straight down; {@link #aim} finds the (rotX, rotZ) that points it along a
 * render-space direction in its parent's frame.
 */
public final class GalactusRig {
	/** Model height (file units, head top to toes, prongs left out -- the same number {@code GalactusModelFit} measures). */
	public static final float MODEL_TOP = 126.045f;
	static final Vector3f WAIST = new Vector3f(0f, 62.5f, 0f);
	static final Vector3f HEAD = new Vector3f(0f, 101.5f, 0f);
	static final Vector3f FACE = new Vector3f(0f, 108.5f, -9.0f);
	static final Vector3f CHEST = new Vector3f(0f, 84.0f, -6.0f);
	/** Shoulder (the arm bone's pivot) in render space: right arm at +X once mirrored. */
	static final Vector3f SHOULDER_R = new Vector3f(22f, 90.9f, 0f);
	static final Vector3f SHOULDER_L = new Vector3f(-22f, 90.9f, 0f);
	public static final float UPPER = 21.0f;
	/** Elbow to the middle of the closed fist (forearm 18 + hand 9). */
	public static final float LOWER = 27.0f;
	/** The resting arms (radians, render space): hanging, a little out from the body. */
	static final float REST_X = 0.08f;
	static final float REST_Z = 0.10f;

	private GalactusRig() {
	}

	/** The pose inputs (world space for the points). */
	public static final class Input {
		public Vec3 feet = Vec3.ZERO;
		public float yawDeg;
		public float height = 100f;
		/** Waist lean forward (radians). */
		public float lean;
		public Vec3 look;
		public Vec3 targetR;
		public float weightR;
		public Vec3 targetL;
		public float weightL;
	}

	/** The solved pose: bone rotations (render space, radians) and the world points the server needs. */
	public static final class Pose {
		public float waistX;
		public float headX, headY;
		public float armRX, armRZ, foreRX, foreRZ;
		public float armLX, armLZ, foreLX, foreLZ;
		public Vec3 head = Vec3.ZERO, face = Vec3.ZERO, chest = Vec3.ZERO;
		public Vec3 shoulderR = Vec3.ZERO, elbowR = Vec3.ZERO, fistR = Vec3.ZERO;
		public Vec3 shoulderL = Vec3.ZERO, elbowL = Vec3.ZERO, fistL = Vec3.ZERO;
		/** Blocks per file unit. */
		public float k;
	}

	public static float scale(float height) {
		return height / MODEL_TOP;
	}

	// ---------------------------------------------------------------- world <-> render space

	/** Render-space (units) -> world, for a body at {@code feet} facing {@code yawDeg}. */
	public static Vec3 toWorld(Vector3f r, Vec3 feet, float yawDeg, float k) {
		// render space faces -Z with his right at +X; world forward = (-sin yaw, cos yaw), world right = (-cos yaw, -sin yaw)
		float yr = yawDeg * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yr), fz = Mth.cos(yr);
		double rx = -Mth.cos(yr), rz = -Mth.sin(yr);
		double right = r.x * k, up = r.y * k, fwd = -r.z * k;
		return new Vec3(feet.x + rx * right + fx * fwd, feet.y + up, feet.z + rz * right + fz * fwd);
	}

	/** World -> render space (units). */
	public static Vector3f toRender(Vec3 w, Vec3 feet, float yawDeg, float k) {
		float yr = yawDeg * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yr), fz = Mth.cos(yr);
		double rx = -Mth.cos(yr), rz = -Mth.sin(yr);
		double dx = w.x - feet.x, dy = w.y - feet.y, dz = w.z - feet.z;
		double right = dx * rx + dz * rz;
		double fwd = dx * fx + dz * fz;
		return new Vector3f((float) (right / k), (float) (dy / k), (float) (-fwd / k));
	}

	// ---------------------------------------------------------------- bone matrices

	/** {@code Rz * Ry * Rx}: GeckoLib's bone rotation. */
	public static Matrix3f rot(float x, float y, float z) {
		return new Matrix3f().rotateZ(z).rotateY(y).rotateX(x);
	}

	/** (rotX, rotZ) that point a bone (resting straight down) along {@code d} (unit, the parent's frame). */
	public static float[] aim(Vector3f d) {
		float a = (float) Math.asin(Mth.clamp(-d.z, -1f, 1f));
		float c = (float) Mth.atan2(d.x, -d.y);
		return new float[] { a, c };
	}

	// ---------------------------------------------------------------- solve

	public static Pose solve(Input in) {
		Pose p = new Pose();
		float k = scale(in.height);
		p.k = k;
		// a positive rotX tips the top back (+Z), so leaning forward is negative
		p.waistX = -in.lean;
		Matrix3f waist = rot(-in.lean, 0f, 0f);
		Matrix3f waistInv = new Matrix3f(waist).transpose();

		Vector3f headPivot = about(HEAD, WAIST, waist);
		p.head = toWorld(headPivot, in.feet, in.yawDeg, k);
		p.chest = toWorld(about(CHEST, WAIST, waist), in.feet, in.yawDeg, k);
		// the head turns to its look target (in the leaned chest's frame), limited like a neck
		if (in.look != null) {
			Vector3f d = toRender(in.look, in.feet, in.yawDeg, k).sub(headPivot);
			if (d.lengthSquared() > 1.0e-4f) {
				d.normalize();
				waistInv.transform(d);
				// rest look is -Z; yaw about Y (toward +X is his right), pitch about X
				float yaw = (float) Mth.atan2(-d.x, -d.z);
				float pitch = (float) Math.asin(Mth.clamp(d.y, -1f, 1f));
				p.headY = Mth.clamp(yaw, -1.0f, 1.0f);
				p.headX = Mth.clamp(pitch, -0.9f, 0.7f);
			}
		}
		Matrix3f headM = new Matrix3f(waist).mul(rot(p.headX, p.headY, 0f));
		Vector3f face = new Vector3f(FACE).sub(HEAD);
		headM.transform(face);
		face.add(headPivot);
		p.face = toWorld(face, in.feet, in.yawDeg, k);

		float[] r = arm(in, waist, waistInv, SHOULDER_R, in.targetR, in.weightR, 1f, k, p, true);
		p.armRX = r[0];
		p.armRZ = r[1];
		p.foreRX = r[2];
		p.foreRZ = r[3];
		float[] l = arm(in, waist, waistInv, SHOULDER_L, in.targetL, in.weightL, -1f, k, p, false);
		p.armLX = l[0];
		p.armLZ = l[1];
		p.foreLX = l[2];
		p.foreLZ = l[3];
		return p;
	}

	/** {@code point} rotated by {@code m} about {@code pivot}. */
	static Vector3f about(Vector3f point, Vector3f pivot, Matrix3f m) {
		Vector3f v = new Vector3f(point).sub(pivot);
		m.transform(v);
		return v.add(pivot);
	}

	/** One arm: {upperX, upperZ, foreX, foreZ}, and its world points written to {@code p}. */
	private static float[] arm(Input in, Matrix3f waist, Matrix3f waistInv, Vector3f shoulderRest, Vec3 target, float weight, float side,
			float k, Pose p, boolean right) {
		Vector3f shoulder = about(shoulderRest, WAIST, waist);
		// the resting angles
		float ux = REST_X, uz = REST_Z * side, fx = 0.12f, fz = 0f;
		float w = Mth.clamp(weight, 0f, 1f);
		if (target != null && w > 0.001f) {
			Vector3f t = toRender(target, in.feet, in.yawDeg, k);
			Vector3f to = new Vector3f(t).sub(shoulder);
			float dist = to.length();
			if (dist > 1.0e-3f) {
				Vector3f u = new Vector3f(to).div(dist);
				float reach = Mth.clamp(dist, Math.abs(UPPER - LOWER) + 0.5f, UPPER + LOWER - 0.05f);
				float cosA = (UPPER * UPPER + reach * reach - LOWER * LOWER) / (2f * UPPER * reach);
				float alpha = (float) Math.acos(Mth.clamp(cosA, -1f, 1f));
				// the elbow bends out to the side, back and down (render space: his side is +X for the right arm, back is +Z)
				Vector3f pole = new Vector3f(side * 0.8f, -0.5f, 0.6f);
				waist.transform(pole);
				Vector3f perp = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
				if (perp.lengthSquared() < 1.0e-5f) {
					perp.set(side, 0f, 0f);
				}
				perp.normalize();
				Vector3f du = new Vector3f(u).mul(Mth.cos(alpha)).add(new Vector3f(perp).mul(Mth.sin(alpha)));
				Vector3f elbow = new Vector3f(shoulder).add(new Vector3f(du).mul(UPPER));
				Vector3f end = new Vector3f(shoulder).add(new Vector3f(u).mul(reach));
				Vector3f df = new Vector3f(end).sub(elbow).normalize();
				// upper arm in the waist frame
				Vector3f localU = new Vector3f(du);
				waistInv.transform(localU);
				float[] ua = aim(localU);
				// forearm in the upper arm's frame
				Matrix3f upperM = new Matrix3f(waist).mul(rot(ua[0], 0f, ua[1]));
				Vector3f localF = new Vector3f(df);
				new Matrix3f(upperM).transpose().transform(localF);
				float[] fa = aim(localF);
				ux = Mth.lerp(w, ux, ua[0]);
				uz = Mth.lerp(w, uz, ua[1]);
				fx = Mth.lerp(w, fx, fa[0]);
				fz = Mth.lerp(w, fz, fa[1]);
			}
		}
		// forward kinematics from the final angles (so a blended pose's points match what is drawn)
		Matrix3f upperM = new Matrix3f(waist).mul(rot(ux, 0f, uz));
		Vector3f down = new Vector3f(0f, -1f, 0f);
		Vector3f du = upperM.transform(new Vector3f(down));
		Vector3f elbow = new Vector3f(shoulder).add(du.mul(UPPER));
		Matrix3f foreM = new Matrix3f(upperM).mul(rot(fx, 0f, fz));
		Vector3f df = foreM.transform(new Vector3f(down));
		Vector3f fist = new Vector3f(elbow).add(df.mul(LOWER));
		Vec3 ws = toWorld(shoulder, in.feet, in.yawDeg, k);
		Vec3 we = toWorld(elbow, in.feet, in.yawDeg, k);
		Vec3 wf = toWorld(fist, in.feet, in.yawDeg, k);
		if (right) {
			p.shoulderR = ws;
			p.elbowR = we;
			p.fistR = wf;
		} else {
			p.shoulderL = ws;
			p.elbowL = we;
			p.fistL = wf;
		}
		return new float[] { ux, uz, fx, fz };
	}

	/** How far (blocks) a fist can reach from its shoulder at this height. */
	public static double reach(float height) {
		return (UPPER + LOWER - 0.1f) * scale(height);
	}

	/** The waist lean (radians) that brings his shoulders closest over a point {@code horizontalFromFeet} blocks away. */
	public static float leanToward(float height, double horizontalFromFeet, float maxLean) {
		float k = scale(height);
		double shoulderAbove = (SHOULDER_R.y - WAIST.y) * k;
		double reach = reach(height) * 0.92;
		// lean just enough that the target falls inside the reach
		for (float lean = 0f; lean <= maxLean; lean += 0.05f) {
			double fwd = Math.sin(lean) * shoulderAbove;
			if (horizontalFromFeet - fwd <= reach * 0.85) {
				return lean;
			}
		}
		return maxLean;
	}
}
