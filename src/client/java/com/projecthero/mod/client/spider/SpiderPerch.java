package com.projecthero.mod.client.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.2: Spider-Man's ledge perch, rebuilt with jointed limbs -- the Insomniac crouch on top of a post: a deep frog
 * squat, hips almost down on the heels, feet close together on the top of the perch with the toes turned out, knees
 * spread wide out to the sides at about hip height (thighs running out and a little forward, shins dropping back in to
 * the feet -- the knees bent ~140 degrees), the torso leaning ~38 degrees forward with the shoulders hunched, both arms
 * hanging down between the knees, elbows a little bent and out, the hands planted flat on the perch in front of the
 * toes, and the head up, looking straight ahead.
 *
 * <p>The arms and legs bend at the elbows and knees through {@link JointedLimbs}: every limb is solved here as a two-bone
 * chain (shoulder / hip, elbow / knee, hand / foot) in model space, the upper bone becomes the vanilla part's rotation
 * (so the sleeves, the suit, armour and the GeckoLib bones all copy it as usual), and the lower bone's rotation relative
 * to it is handed to {@link JointedLimbs#setJoint}. Everything is blended from vanilla's values by the eased perch amount
 * ({@link #amount}), so the crouch eases in and out over a quarter second. Positions are for the model as drawn: a
 * sneaking player is drawn 2 px lower ({@code PlayerRenderer#getRenderOffset}), so the perch's top surface is model y 22
 * while sneaking, 24 otherwise.
 *
 * <p>Model space: px, +y down, the front is -z, the model's right is -x.
 */
public final class SpiderPerch {
	/** First person: how far the eye sinks (blocks) and tips down (degrees) at full perch. */
	public static final double CAMERA_DROP = 0.36;
	public static final float CAMERA_TILT = 6f;

	/** The hip pivots sit this far above the perch top (px) -- butt just above the surface. */
	private static final float HIP_ABOVE = 4.5f;
	/** The hips sit this far back of the model origin (px), over the heels. */
	private static final float HIP_Z = 2.0f;
	/** ... and this far either side of the centre line (vanilla: 1.9). */
	private static final float HIP_X = 2.5f;
	/** Torso lean forward (degrees). */
	private static final float LEAN = 42f;
	/** Feet: sole centres this far either side of the centre line (px), and this far forward (-z). */
	private static final float FOOT_X = 2.0f;
	private static final float FOOT_Z = -1.5f;
	/** Toes turned out this far (degrees). */
	private static final float TOE_OUT = 38f;
	/** Hands: palm centres this far either side of the centre line and this far forward (-z) (px). */
	private static final float HAND_X = 1.9f;
	private static final float HAND_Z = -8.0f;
	/** Shoulders hunched: down the torso / forward of it (px, in the torso's frame). */
	private static final float SHOULDER_DOWN = 3.5f;
	private static final float SHOULDER_FWD = -1.0f;
	/** ... and rolled in toward the chest (vanilla's shoulder pivots are 5 px out). */
	private static final float SHOULDER_X = 3.9f;
	/** Where the knees push (out, up = -y, forward = -z) and the elbows point (out, back). */
	private static final float LEG_POLE_X = 1.0f;
	private static final float LEG_POLE_Y = -0.6f;
	private static final float LEG_POLE_Z = -0.15f;
	private static final float ARM_POLE_X = 0.5f;
	private static final float ARM_POLE_Y = 0.0f;
	private static final float ARM_POLE_Z = 0.85f;

	/** Per player: the eased perch amount (0..1) and the last time it was advanced (ns). */
	private static final Map<UUID, double[]> EASE = new ConcurrentHashMap<>();

	private SpiderPerch() {
	}

	/** The ledge perch, eased in / out over about five ticks (0 = standing). */
	public static float amount(Player player) {
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		boolean on = s != null && s.has(SpiderMoveState.FLAG_PERCH) && SpiderMan.hasPower(player);
		double[] e = EASE.get(player.getUUID());
		if (e == null) {
			if (!on) {
				return 0f;
			}
			e = new double[] {0.0, System.nanoTime()};
			EASE.put(player.getUUID(), e);
		}
		long now = System.nanoTime();
		double dt = Math.min(0.2, (now - e[1]) / 1.0e9);
		e[1] = now;
		// a quarter second (5 ticks) from one to the other
		e[0] = Mth.clamp(e[0] + (on ? dt : -dt) * 4.0, 0.0, 1.0);
		if (!on && e[0] <= 0.0) {
			EASE.remove(player.getUUID());
			return 0f;
		}
		float f = (float) e[0];
		return f * f * (3f - 2f * f);
	}

	/** Pose {@code m} for {@code player} (from {@code SpiderPose.apply}, after vanilla's animation). */
	public static void apply(Player player, HumanoidModel<?> m) {
		float k = amount(player);
		if (k > 0f) {
			pose(m, k);
		}
	}

	/** The perch at strength {@code k} (also used by the screenshot harness). */
	public static void pose(HumanoidModel<?> m, float k) {
		final float ground = m.crouching ? 22.0f : 24.0f;
		final float hipY = ground - HIP_ABOVE;
		final float lean = LEAN * Mth.DEG_TO_RAD;

		// ---- torso: leaning forward about the hips; the neck (its pivot) up and forward of them
		Vector3f hip = new Vector3f(0f, hipY, HIP_Z);
		Vector3f neck = new Vector3f(0f, hipY - 12f * Mth.cos(lean), HIP_Z - 12f * Mth.sin(lean));
		set(m.body, k, neck, lean, 0f, 0f);
		// ---- head: on the neck, up and level, looking where he looks (gently limited)
		float look = Mth.clamp(m.head.xRot, -0.5f, 0.35f);
		float headYaw = m.head.yRot;
		set(m.head, k, new Vector3f(neck.x, neck.y + 0.4f, neck.z - 0.6f), look, headYaw, 0f);
		m.hat.copyFrom(m.head);

		// ---- legs: hip -> knee (6 px) -> sole (6 px); the knees pushed out wide, a little forward and up
		for (int side = -1; side <= 1; side += 2) {
			ModelPart leg = side < 0 ? m.rightLeg : m.leftLeg;
			// the hips spread a little (HumanoidModelLimbRegistryMixin puts the x pivots back every frame)
			Vector3f p = new Vector3f(side * HIP_X, hipY, HIP_Z);
			Vector3f pole = new Vector3f(side * LEG_POLE_X, LEG_POLE_Y, LEG_POLE_Z).normalize();
			Vector3f toe = new Vector3f(side * Mth.sin(TOE_OUT * Mth.DEG_TO_RAD), 0f, -Mth.cos(TOE_OUT * Mth.DEG_TO_RAD));
			// the sole: solve once, then lift it by however far the tilted box's lowest corner sinks below the surface
			Vector3f foot = new Vector3f(side * FOOT_X, ground, FOOT_Z);
			Chain c = solve(p, foot, 6f, 6f, pole, toe, false);
			float sink = c.lowestCorner(foot, 2f) - ground;
			foot.y -= sink;
			c = solve(p, foot, 6f, 6f, pole, toe, false);
			place(leg, k, p, c.upper);
			JointedLimbs.setJoint(side < 0 ? JointedLimbs.RIGHT_LEG : JointedLimbs.LEFT_LEG, c.joint(k));
		}

		// ---- arms: shoulder -> elbow (4 px; the pivot sits 2 px down the 12 px arm) -> hand end (6 px)
		Matrix3f torso = new Matrix3f().rotationX(lean);
		for (int side = -1; side <= 1; side += 2) {
			ModelPart arm = side < 0 ? m.rightArm : m.leftArm;
			Vector3f s = torso.transform(new Vector3f(side * SHOULDER_X, 2f + SHOULDER_DOWN, SHOULDER_FWD)).add(neck);
			// the arm box's centre line is 1 px outboard of its pivot (wide arms): aim the pivot line 1 px inboard
			Vector3f hand = new Vector3f(side * (HAND_X - 1f), ground, HAND_Z);
			Vector3f pole = new Vector3f(side * ARM_POLE_X, ARM_POLE_Y, ARM_POLE_Z).normalize();
			Chain c = solve(s, hand, 4f, 6f, pole, null, true);
			float sink = c.lowestCorner(hand, 2f) - ground;
			hand.y -= sink;
			c = solve(s, hand, 4f, 6f, pole, null, true);
			place(arm, k, s, c.upper);
			JointedLimbs.setJoint(side < 0 ? JointedLimbs.RIGHT_ARM : JointedLimbs.LEFT_ARM, c.joint(k));
		}
	}

	/** Blend a part to a pivot + rotation. */
	static void set(ModelPart part, float k, Vector3f pivot, float xRot, float yRot, float zRot) {
		part.x = Mth.lerp(k, part.x, pivot.x);
		part.y = Mth.lerp(k, part.y, pivot.y);
		part.z = Mth.lerp(k, part.z, pivot.z);
		part.xRot = Mth.lerp(k, part.xRot, xRot);
		part.yRot = Mth.lerp(k, part.yRot, yRot);
		part.zRot = Mth.lerp(k, part.zRot, zRot);
	}

	/** Blend a part to a pivot + a rotation matrix (as the part's ZYX euler angles). */
	static void place(ModelPart part, float k, Vector3f pivot, Matrix3f r) {
		// ModelPart: R = Rz(zRot) * Ry(yRot) * Rx(xRot); JOML m{col}{row}
		float yRot = (float) Math.asin(Mth.clamp(-r.m02, -1f, 1f));
		float xRot = (float) Mth.atan2(r.m12, r.m22);
		float zRot = (float) Mth.atan2(r.m01, r.m00);
		part.y = Mth.lerp(k, part.y, pivot.y);
		part.z = Mth.lerp(k, part.z, pivot.z);
		part.x = Mth.lerp(k, part.x, pivot.x);
		part.xRot = Mth.lerp(k, part.xRot, xRot);
		part.yRot = Mth.lerp(k, part.yRot, yRot);
		part.zRot = Mth.lerp(k, part.zRot, zRot);
	}

	/** A solved two-bone limb: the upper and lower bones' frames (columns: local x, y = along the bone, z = back). */
	record Chain(Matrix3f upper, Matrix3f lower, Vector3f end, Vector3f lowerDir) {
		/** The lower bone's rotation relative to the upper, scaled by {@code k}. */
		Quaternionf joint(float k) {
			Matrix3f rel = new Matrix3f(upper).transpose().mul(lower);
			Quaternionf q = new Quaternionf().setFromNormalized(rel);
			return new Quaternionf().slerp(q, k);
		}

		/** The y (down) of the lowest corner of the lower bone's end face, a {@code half}-px-wide square. */
		float lowestCorner(Vector3f endCentre, float half) {
			Vector3f ex = lower.getColumn(0, new Vector3f());
			Vector3f ez = lower.getColumn(2, new Vector3f());
			return end.y + half * (Math.abs(ex.y) + Math.abs(ez.y));
		}
	}

	/**
	 * Two-bone IK from {@code root} to {@code target}: the middle joint bent toward {@code pole}. Knees fold back (the
	 * shin swings to the back of the thigh), elbows fold forward. {@code endFront} (optional) is where the front of the
	 * lower bone should face (the toes).
	 */
	static Chain solve(Vector3f root, Vector3f target, float l1, float l2, Vector3f pole, Vector3f endFront,
			boolean elbow) {
		Vector3f d = new Vector3f(target).sub(root);
		float dist = Mth.clamp(d.length(), Math.abs(l1 - l2) + 0.05f, l1 + l2 - 0.02f);
		Vector3f u = d.lengthSquared() < 1.0e-6f ? new Vector3f(0f, 1f, 0f) : new Vector3f(d).normalize();
		float a = (l1 * l1 - l2 * l2 + dist * dist) / (2f * dist);
		float h = (float) Math.sqrt(Math.max(0f, l1 * l1 - a * a));
		Vector3f v = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
		if (v.lengthSquared() < 1.0e-6f) {
			v.set(0f, 0f, elbow ? 1f : -1f);
		}
		v.normalize();
		Vector3f mid = new Vector3f(root).add(new Vector3f(u).mul(a)).add(new Vector3f(v).mul(h));
		Vector3f end = new Vector3f(root).add(new Vector3f(u).mul(dist));
		Vector3f up = new Vector3f(mid).sub(root).normalize();
		Vector3f lo = new Vector3f(end).sub(mid).normalize();
		// the upper bone's front (-z): a knee folds the shin toward the back of the thigh, an elbow the forearm toward
		// the front of the upper arm
		Vector3f perp = new Vector3f(lo).sub(new Vector3f(up).mul(lo.dot(up)));
		Vector3f front;
		if (perp.lengthSquared() < 1.0e-5f) {
			front = new Vector3f(0f, 0f, -1f);
		} else {
			front = perp.normalize();
			if (!elbow) {
				front.negate();
			}
		}
		Matrix3f upper = frame(up, front);
		// the lower bone: no twist from the upper (its front carried over the joint), unless asked to face somewhere
		Vector3f lowFront = endFront != null ? new Vector3f(endFront) : upper.getColumn(2, new Vector3f()).negate();
		if (endFront == null && elbow) {
			// carried over the bend: the upper's front turned by the same bend
			Vector3f axis = new Vector3f(up).cross(lo);
			if (axis.lengthSquared() > 1.0e-8f) {
				float ang = (float) Math.acos(Mth.clamp(up.dot(lo), -1f, 1f));
				new Quaternionf().rotationAxis(ang, axis.normalize()).transform(lowFront);
			}
		}
		Matrix3f lower = frame(lo, lowFront);
		return new Chain(upper, lower, end, lo);
	}

	/** A bone frame: local +y along {@code along}, local -z toward {@code front} (made perpendicular). */
	private static Matrix3f frame(Vector3f along, Vector3f front) {
		Vector3f y = new Vector3f(along).normalize();
		Vector3f f = new Vector3f(front).sub(new Vector3f(y).mul(front.dot(y)));
		if (f.lengthSquared() < 1.0e-6f) {
			f.set(0f, 0f, -1f).sub(new Vector3f(y).mul(-y.z));
		}
		f.normalize();
		Vector3f z = new Vector3f(f).negate();
		Vector3f x = new Vector3f(y).cross(z);
		return new Matrix3f(x, y, z);
	}
}
