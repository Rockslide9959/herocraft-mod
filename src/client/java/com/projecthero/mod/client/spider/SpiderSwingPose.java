package com.projecthero.mod.client.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.data.SpiderManState;
import com.projecthero.mod.spider.data.SpiderMoveState;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.client.render.JointedLimbs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the living web swing -- a pose driven every frame by the swing itself (rope vector, velocity, time on the
 * line), not by keyframes. Everything is derived from synced state (the anchor, the hand) and the player's own motion,
 * so every viewer sees the same swing.
 *
 * <ul>
 * <li>The whole body hangs along the rope (a pitch / roll about the shoulders, {@link #applyBodyRotation}) and rolls
 * into turns.</li>
 * <li>The web arm is aimed at the anchor every frame, in the tilted body's own frame, so it stays on the line.</li>
 * <li>Phase: through the bottom of a fast arc he is stretched out, legs trailing; on the upswing the knees come up and
 * he pitches back; near the top (slow, rope well off vertical) he tucks. Legs scissor with the arc speed, the free arm
 * counter-swings, the head keeps looking where he is going.</li>
 * <li>Styles per swing (v0.16.2: picked at random per line by {@code SpiderSwingStyles.pick} from the synced anchor
 * point + rope length, so every viewer agrees): the one-arm classic, a two-hand grip, a cannonball tuck through the
 * bottom of fast arcs, a corkscrew spin when a long, fast swing is let go, and the two-hand HANG_PULL (pull-up, knees
 * tucked on the upswing) and CLIMBER (hands stacked, leaning back, legs out front) grips, solved with jointed limbs.</li>
 * <li>Everything eases (no snapping): the swing weight in and out, every angle, and the aim from one anchor to the
 * next when swings are chained.</li>
 * </ul>
 * Torso bends go through {@link HipBend}, so the torso never leaves the legs.
 */
public final class SpiderSwingPose {
	private static final int CLASSIC = com.projecthero.mod.spider.SpiderSwingStyles.CLASSIC;
	private static final int TWO_HAND = com.projecthero.mod.spider.SpiderSwingStyles.TWO_HAND;
	private static final int CANNONBALL = com.projecthero.mod.spider.SpiderSwingStyles.CANNONBALL;
	private static final int SPIN = com.projecthero.mod.spider.SpiderSwingStyles.SPIN;
	/** v0.16.2: both hands high on the line, hanging straight, a pull-up with knees tucked on the upswing. */
	private static final int HANG_PULL = com.projecthero.mod.spider.SpiderSwingStyles.HANG_PULL;
	/** v0.16.2: hands stacked on the line, leaning back, legs out front like a rope swing. */
	private static final int CLIMBER = com.projecthero.mod.spider.SpiderSwingStyles.CLIMBER;

	/** Per player, the eased rig. Client only; stale entries are dropped. */
	private static final Map<UUID, Rig> RIGS = new ConcurrentHashMap<>();

	/** The eased swing state of one player. */
	public static final class Rig {
		public float w; // 0..1 swing weight
		public float lean; // body pitch toward the anchor (+ = toward his front), radians
		public float roll; // + = top toward his right, radians
		public float legR;
		public float legL;
		public float legSpread;
		public float curl; // HipBend target
		public float freeX;
		public float freeZ;
		public float cannon;
		public float twoHand;
		public float headComp;
		public float up; // -1 falling .. 1 rising
		public float fast; // 0..1
		public float peek; // first person: how far the free hand comes into view
		public float phase; // the arc phase (radians), for bobs / kicks
		public float fpLift; // first person: how far the web hand is raised (view units), for the line origin
		public float grip; // v0.16.2: 0..1 weight of the two-hand IK grip (HANG_PULL / CLIMBER)
		public float pull; // v0.16.2: 0..1 how far the arms are pulled in / the knees tucked (HANG_PULL)
		Vec3 aim = null; // smoothed world direction shoulder -> anchor
		int style = CLASSIC;
		boolean swinging;
		boolean right;
		long swingStart;
		float spinStart = Float.NaN;
		double lastKey = Double.NaN;
		long lastNanos;
		long seenNanos;

		/** The smoothed world direction from his shoulder to the anchor (null before the first swinging frame). */
		public Vec3 aimDir() {
			return aim;
		}

		/** The web hand of the current / last swing. */
		public boolean rightHand() {
			return right;
		}
	}

	private SpiderSwingPose() {
	}

	private static float approach(float cur, float target, float rate, float dt) {
		return cur + (target - cur) * (1f - (float) Math.exp(-rate * dt));
	}

	/** The eased rig for {@code player} this frame (updated at most once per frame), or null when idle. */
	public static Rig rig(Player player, float partial) {
		SpiderManState st = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		boolean hasPower = st != null && st.hasPower;
		Rig r = RIGS.get(player.getUUID());
		SpiderMoveState ms = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		boolean hanging = ms != null && ms.has(SpiderMoveState.FLAG_HANGING);
		boolean swinging = hasPower && SpiderSwing.isSwinging(player) && !hanging;
		if (r == null) {
			if (!swinging) {
				return null;
			}
			r = new Rig();
			r.lastNanos = System.nanoTime();
			RIGS.put(player.getUUID(), r);
		}
		long nanos = System.nanoTime();
		r.seenNanos = nanos;
		double key = player.level().getGameTime() + partial;
		if (key == r.lastKey) {
			return r;
		}
		r.lastKey = key;
		float dt = (float) Math.min(0.1, Math.max(0.0, (nanos - r.lastNanos) / 1.0e9));
		r.lastNanos = nanos;
		long gameTime = player.level().getGameTime();
		float t = (float) key;

		Vec3 pos = player.getPosition(partial);
		Vec3 vel = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
		double speed = vel.length();
		if (swinging && !r.swinging) {
			// a new line: v0.16.2 a random style per line, seeded from the synced anchor point + rope length so every
			// viewer picks the same one
			r.style = com.projecthero.mod.spider.SpiderSwingStyles.pick(st.anchorX, st.anchorY, st.anchorZ, st.ropeLength);
			r.swingStart = gameTime;
			r.right = st.swingHandRight;
		}
		if (!swinging && r.swinging && hasPower && !player.onGround()) {
			// let go: a long, fast swing of the spin style ends in a corkscrew
			if (r.style == SPIN && gameTime - r.swingStart > 24 && speed > 0.9) {
				r.spinStart = t;
			}
		}
		r.swinging = swinging;
		if (st != null) {
			r.right = swinging ? st.swingHandRight : r.right;
		}

		r.w = approach(r.w, swinging ? 1f : 0f, swinging ? 9f : 4.5f, dt);
		if (!swinging && r.w < 0.01f && (Float.isNaN(r.spinStart) || t - r.spinStart > 16f)) {
			RIGS.remove(player.getUUID());
			return null;
		}

		float leanT = 0f, rollT = 0f, legR = 0f, legL = 0f, spread = 0f, curl = 0f, freeX = 0f, freeZ = 0f;
		float cannonT = 0f, twoT = 0f, upT = 0f, fastT = 0f, peekT = 0f, gripT = 0f, pullT = 0f;
		if (swinging) {
			Vec3 anchor = new Vec3(st.anchorX, st.anchorY, st.anchorZ);
			Vec3 shoulder = pos.add(0, 1.35, 0);
			Vec3 rope = anchor.subtract(shoulder);
			double len = Math.max(1.0e-3, rope.length());
			Vec3 dir = rope.scale(1.0 / len);
			r.aim = r.aim == null ? dir : lerpDir(r.aim, dir, 1f - (float) Math.exp(-14f * dt));
			float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
			Vec3 fwd = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
			Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0, -Mth.sin(bodyYaw));
			double f = r.aim.dot(fwd);
			double rr = r.aim.dot(right);
			double u = Math.max(0.25, r.aim.y);
			float off = (float) Math.atan2(Math.sqrt(f * f + rr * rr), Math.max(0.0, r.aim.y)); // rope angle off vertical
			upT = (float) Mth.clamp(vel.y / 0.55, -1.0, 1.0);
			// scales with the (faster) swing: full "fast" at about 2.8 b/t
			fastT = (float) Mth.clamp((speed - 0.35) / 2.4, 0.0, 1.0);
			float bottom = Mth.clamp(1f - off / 0.55f, 0f, 1f);
			float top = Mth.clamp(1f - (float) speed / 0.55f, 0f, 1f) * Mth.clamp(off / 0.6f, 0f, 1f);
			float rising = Math.max(upT, 0f);
			float falling = Math.max(-upT, 0f);
			// hang along the rope, then the phase on top: stretched forward through a fast bottom, back on the upswing
			leanT = (float) Mth.clamp(Math.atan2(f, u) * 0.85, -1.15, 1.15) + 0.18f * fastT * bottom - 0.22f * rising;
			float turn = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO) * Mth.DEG_TO_RAD;
			rollT = (float) Mth.clamp(Math.atan2(rr, u) * 0.7 + Mth.clamp(turn * 2.2f, -0.45f, 0.45f), -0.75, 0.75);
			// legs: trail behind through the bottom, knees up on the upswing, tucked at the top; scissor with the arc
			float rate = 0.32f + 0.5f * fastT;
			r.phase += rate * dt * 20f;
			float scissor = Mth.sin(r.phase) * (0.16f + 0.38f * fastT) * (1f - top);
			float base = 0.55f * fastT * bottom - 1.0f * rising + 0.35f * falling - 1.15f * top;
			cannonT = (r.style == CANNONBALL || fastT > 0.85f) ? bottom * Mth.clamp(fastT * 1.6f, 0f, 1f) : 0f;
			legR = base + scissor;
			legL = base - scissor * 0.85f + 0.12f;
			spread = 0.06f + 0.1f * fastT;
			curl = -0.22f * fastT * bottom + 0.32f * top + 0.15f * rising;
			// free arm: out to the side for balance, counter-swinging the legs
			freeX = -0.35f + 0.85f * rising - 0.75f * falling + 0.25f * Mth.sin(r.phase + 1.4f) * fastT;
			freeZ = 0.85f + 0.45f * fastT;
			twoT = r.style == TWO_HAND ? Mth.clamp(fastT * 2f, 0.5f, 1f) : 0f;
			peekT = Mth.clamp(rising * 1.3f, 0f, 1f) * 0.8f + twoT;
			if (r.style == HANG_PULL) {
				// v0.16.2: hanging straight down the line (no stretch / lay-back), pulling up and tucking on the upswing
				leanT = (float) Mth.clamp(Math.atan2(f, u) * 0.95, -1.2, 1.2);
				gripT = 1f;
				pullT = Mth.clamp(rising * 1.6f + top * 0.6f, 0f, 1f);
				cannonT = 0f;
				peekT = 1f;
			} else if (r.style == CLIMBER) {
				// v0.16.2: leaning back from the line, legs out front, swinging on it like a rope swing
				leanT = (float) Mth.clamp(Math.atan2(f, u) * 0.85, -1.15, 1.15) - 0.32f;
				gripT = 1f;
				pullT = Mth.clamp(rising, 0f, 1f);
				cannonT = 0f;
				peekT = 1f;
			}
		}
		float rate = 7f;
		r.lean = approach(r.lean, leanT, rate, dt);
		r.roll = approach(r.roll, rollT, rate, dt);
		r.legR = approach(r.legR, legR, 10f, dt);
		r.legL = approach(r.legL, legL, 10f, dt);
		r.legSpread = approach(r.legSpread, spread, rate, dt);
		r.curl = approach(r.curl, curl, rate, dt);
		r.freeX = approach(r.freeX, freeX, 8f, dt);
		r.freeZ = approach(r.freeZ, freeZ, 8f, dt);
		r.cannon = approach(r.cannon, cannonT, 6f, dt);
		r.twoHand = approach(r.twoHand, twoT, 6f, dt);
		if (swinging) {
			r.grip = approach(r.grip, gripT, 8f, dt); // (kept while blending out after a release)
		}
		r.pull = approach(r.pull, pullT, 6f, dt);
		r.up = approach(r.up, upT, rate, dt);
		r.fast = approach(r.fast, fastT, rate, dt);
		r.peek = approach(r.peek, Math.min(1f, peekT), 5f, dt);
		r.headComp = r.lean;
		return r;
	}

	private static Vec3 lerpDir(Vec3 a, Vec3 b, float k) {
		Vec3 v = a.lerp(b, k);
		double l = v.length();
		return l < 1.0e-4 ? b : v.scale(1.0 / l);
	}

	/** The whole-body tilt along the rope plus the corkscrew on a stylish release (from {@code PlayerRendererMixin}). */
	public static void applyBodyRotation(Player player, PoseStack pose, float partial) {
		// v0.16.2: the wall run (sideways on the wall face) and the swing tricks (flips / rolls / spins)
		SpiderWallRunPose.applyBodyRotation(player, pose, partial);
		boolean trick = SpiderTrickPose.applyBodyRotation(player, pose, partial);
		Rig r = rig(player, partial);
		if (r == null) {
			return;
		}
		if (trick) {
			r.spinStart = Float.NaN; // a trick replaces the corkscrew release
		}
		float lean = r.lean * r.w;
		float roll = r.roll * r.w;
		if (Math.abs(lean) > 1.0e-3 || Math.abs(roll) > 1.0e-3) {
			pose.translate(0.0f, 1.35f, 0.0f);
			pose.mulPose(Axis.XP.rotation(-lean));
			pose.mulPose(Axis.ZP.rotation(-roll));
			pose.translate(0.0f, -1.35f, 0.0f);
		}
		if (!Float.isNaN(r.spinStart)) {
			float t = (float) (player.level().getGameTime() + partial) - r.spinStart;
			if (t >= 0f && t < 14f) {
				float k = t / 14f;
				k = k * k * (3f - 2f * k);
				pose.translate(0.0f, 0.9f, 0.0f);
				pose.mulPose(Axis.YP.rotationDegrees((r.right ? -360f : 360f) * k));
				pose.translate(0.0f, -0.9f, 0.0f);
			} else if (t >= 14f) {
				r.spinStart = Float.NaN;
			}
		}
	}

	/** True while the swing rig owns the limbs (so the old fixed swing pose stays out of the way). */
	public static boolean active(Player player) {
		Rig r = RIGS.get(player.getUUID());
		return r != null && r.w > 0.01f;
	}

	/** The limbs (from {@code SpiderPose.apply}, after vanilla). */
	public static void applyLimbs(Player player, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		applySwingLimbs(player, m, partial);
		// v0.16.2: the wall run's running legs and the swing tricks blend over whatever the swing left
		SpiderWallRunPose.applyLimbs(player, m, partial);
		SpiderTrickPose.applyLimbs(player, m, partial);
	}

	private static void applySwingLimbs(Player player, HumanoidModel<?> m, float partial) {
		Rig r = rig(player, partial);
		if (r == null || r.w <= 0.001f) {
			return;
		}
		float w = r.w;
		float t = (float) (player.level().getGameTime() + partial);
		ModelPart web = r.right ? m.rightArm : m.leftArm;
		ModelPart free = r.right ? m.leftArm : m.rightArm;
		float side = r.right ? 1f : -1f; // model x of "outward" for the web arm is -side

		// legs: trail / tuck / scissor; the cannonball pulls both right up
		float legR = Mth.lerp(r.cannon, r.legR, -1.55f);
		float legL = Mth.lerp(r.cannon, r.legL, -1.45f);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, legR);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, legL);
		m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, 0f);
		m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, 0f);
		m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, r.legSpread);
		m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, -r.legSpread);
		// torso: stretched / curled about the hips (never detaches); the cannonball curls right over
		HipBend.pitch(m, w, Mth.lerp(r.cannon, r.curl, 0.5f));

		// the web arm, aimed at the anchor in the tilted body's own frame, every frame
		if (r.aim != null && r.swinging) {
			float[] a = aimAngles(player, r, partial, r.aim, true);
			float pull = 0.04f * Mth.sin(r.phase * 0.5f) * r.fast;
			web.xRot = Mth.lerp(w, web.xRot, a[0] + pull);
			web.yRot = Mth.lerp(w, web.yRot, a[1]);
			// v0.16.2: a slight outward splay on top of aimAngles' keep-clear-of-the-head lean (right arm outward =
			// +zRot, left = -zRot); the hand tracker keeps the line on the hand either way
			web.zRot = Mth.lerp(w, web.zRot, side * 0.08f);
		}
		// the free arm: balance out to the side, counter-swinging; two-hand grip reaches the line too; hugs the knees in a
		// cannonball
		float fx = r.freeX;
		float fy = 0f;
		float fz = -side * r.freeZ; // the left arm (right swing) goes out with -z, the right arm with +z
		if (r.twoHand > 0.01f && r.aim != null) {
			float[] a = aimAngles(player, r, partial, r.aim, false);
			fx = Mth.lerp(r.twoHand, fx, a[0] + 0.12f);
			fy = Mth.lerp(r.twoHand, fy, a[1] + side * 0.22f);
			fz = Mth.lerp(r.twoHand, fz, 0f);
		}
		fx = Mth.lerp(r.cannon, fx, -1.05f);
		fy = Mth.lerp(r.cannon, fy, side * 0.35f);
		fz = Mth.lerp(r.cannon, fz, side * 0.1f);
		free.xRot = Mth.lerp(w, free.xRot, fx);
		free.yRot = Mth.lerp(w, free.yRot, fy);
		free.zRot = Mth.lerp(w, free.zRot, fz);
		// the head keeps looking where he is going: take the body tilt back out of it
		m.head.xRot = Mth.clamp(m.head.xRot - r.lean * w * 0.75f, -1.4f, 1.2f);
		m.head.zRot = Mth.lerp(w, m.head.zRot, r.roll * 0.5f);
		m.hat.copyFrom(m.head);
		// v0.16.2: the two new two-hand styles -- both hands solved onto the line (jointed elbows / knees)
		if (r.grip > 0.01f && r.aim != null) {
			float k = w * r.grip;
			Vector3f d = modelDir(player, r, partial, r.aim);
			if (r.style == CLIMBER) {
				climberGrip(m, r, d, k);
			} else {
				hangPullGrip(m, r, d, k);
			}
		}
		// the corkscrew release: arms spread wide
		if (!Float.isNaN(r.spinStart)) {
			float st = t - r.spinStart;
			if (st >= 0f && st < 14f) {
				float k = Math.min(1f, Math.min(st / 2f, (14f - st) / 3f));
				m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -0.3f);
				m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 1.35f);
				m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -0.3f);
				m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -1.35f);
				m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, -0.5f);
				m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 0.35f);
			}
		}
	}

	// ------------------------------------------------------------------ v0.16.2: the two-hand grips

	/** Vanilla arm / leg pivots (model px). */
	private static final float SHOULDER_Y = 2.0f;
	private static final float HIP_X = 1.9f;
	private static final float HIP_Y = 12.0f;

	/**
	 * HANG_PULL: both hands side by side high on the line, the body hanging straight under them. On the upswing (and over
	 * the top) he pulls up -- the grip comes in toward his head, the elbows bend out -- and tucks the knees up.
	 */
	private static void hangPullGrip(HumanoidModel<?> m, Rig r, Vector3f d, float k) {
		float pull = r.pull;
		HipBend.pitch(m, k, 0.06f + 0.22f * pull); // first: it moves the shoulders the arms are solved from
		// the grip point: up the line from between the shoulders; nearer his head the harder he pulls
		float reach = 10.4f - 4.0f * pull;
		// shoulders shrugged up 2 px at full stretch (so the straight arms reach above his head), the grip a little in front
		float shrug = 2.0f * (1f - pull) * k;
		Vector3f grip = new Vector3f(0f, SHOULDER_Y - shrug, -1.0f - 2.2f * pull).add(new Vector3f(d).mul(reach));
		Vector3f lat = new Vector3f(1f, 0f, 0f).sub(new Vector3f(d).mul(d.x));
		if (lat.lengthSquared() < 1.0e-4f) {
			lat.set(1f, 0f, 0f);
		}
		lat.normalize();
		for (int side = -1; side <= 1; side += 2) {
			boolean rightArm = side < 0;
			ModelPart arm = rightArm ? m.rightArm : m.leftArm;
			Vector3f shoulder = new Vector3f(arm.x, arm.y - shrug, arm.z); // (after the hip bend moved it; shrugged)
			// hands 2.2 px either side of the line; the pivot line 1 px inboard of the arm box's centre line
			Vector3f hand = new Vector3f(grip).add(new Vector3f(lat).mul(side * 2.2f)).add(-side * 1.0f, 0f, 0f);
			// elbows out to the sides and a little forward
			Vector3f pole = new Vector3f(side * 1.0f, 0.15f, -0.45f).normalize();
			SpiderPerch.Chain c = SpiderPerch.solve(shoulder, hand, 4f, 6f, pole, null, true);
			placeShortest(arm, k, shoulder, c.upper());
			JointedLimbs.setJoint(rightArm ? JointedLimbs.RIGHT_ARM : JointedLimbs.LEFT_ARM, c.joint(k));
		}
		// legs: hanging straight (a slight trail through a fast bottom), knees tucked up as he pulls
		float trail = 0.18f * r.fast * (1f - pull);
		float thigh = trail - 1.45f * pull + 0.05f * Mth.sin(r.phase);
		float knee = 0.12f + 1.95f * pull;
		legs(m, k, thigh, thigh - 0.12f * pull + 0.06f, knee, knee * 0.92f, 0.05f);
	}

	/**
	 * CLIMBER: hands stacked on the line in front of his face -- the web hand on top, the other a hand's width below --
	 * the body leaning back from the line and both legs out in front, like riding a rope swing.
	 */
	private static void climberGrip(HumanoidModel<?> m, Rig r, Vector3f d, float k) {
		// the line runs down in front of his chest; the hands grip it there, one above the other
		Vector3f onLine = new Vector3f(0f, 0.5f, -5.0f);
		HipBend.pitch(m, k, -0.08f); // first: it moves the shoulders the arms are solved from
		boolean webRight = r.right;
		for (int side = -1; side <= 1; side += 2) {
			boolean rightArm = side < 0;
			boolean top = rightArm == webRight;
			ModelPart arm = rightArm ? m.rightArm : m.leftArm;
			Vector3f shoulder = new Vector3f(arm.x, arm.y, arm.z); // (after the hip bend moved it)
			Vector3f hand = new Vector3f(onLine).add(new Vector3f(d).mul(top ? 9.4f : 4.6f)).add(-side * 1.0f, 0f, 0f);
			// elbows out and down
			Vector3f pole = new Vector3f(side * 1.0f, 0.55f, 0.1f).normalize();
			SpiderPerch.Chain c = SpiderPerch.solve(shoulder, hand, 4f, 6f, pole, null, true);
			placeShortest(arm, k, shoulder, c.upper());
			JointedLimbs.setJoint(rightArm ? JointedLimbs.RIGHT_ARM : JointedLimbs.LEFT_ARM, c.joint(k));
		}
		// legs out front, together, knees a little bent -- further forward on the upswing, swinging back on the way down
		float swing = 0.32f * r.up;
		float thigh = -1.05f - swing + 0.06f * Mth.sin(r.phase);
		float knee = 0.35f + 0.25f * r.pull;
		legs(m, k, thigh, thigh + 0.05f, knee, knee, 0.03f);
	}

	/** {@link SpiderPerch#place}, but each angle blended the short way round (an arm overhead sits at the +-pi seam). */
	private static void placeShortest(ModelPart part, float k, Vector3f pivot, org.joml.Matrix3f rot) {
		float x0 = part.xRot, y0 = part.yRot, z0 = part.zRot, px = part.x, py = part.y, pz = part.z;
		SpiderPerch.place(part, 1f, pivot, rot);
		part.xRot = x0 + Mth.wrapDegrees((part.xRot - x0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * k;
		part.yRot = y0 + Mth.wrapDegrees((part.yRot - y0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * k;
		part.zRot = z0 + Mth.wrapDegrees((part.zRot - z0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * k;
		part.x = Mth.lerp(k, px, part.x);
		part.y = Mth.lerp(k, py, part.y);
		part.z = Mth.lerp(k, pz, part.z);
	}

	/** Both legs: thigh pitch, knee bend (jointed), a little splay. */
	private static void legs(HumanoidModel<?> m, float k, float thighR, float thighL, float kneeR, float kneeL, float splay) {
		SpiderPerch.set(m.rightLeg, k, new Vector3f(-HIP_X, HIP_Y, m.rightLeg.z), thighR, 0f, splay);
		SpiderPerch.set(m.leftLeg, k, new Vector3f(HIP_X, HIP_Y, m.leftLeg.z), thighL, 0f, -splay);
		JointedLimbs.setJoint(JointedLimbs.RIGHT_LEG, new Quaternionf().rotationX(kneeR * k));
		JointedLimbs.setJoint(JointedLimbs.LEFT_LEG, new Quaternionf().rotationX(kneeL * k));
	}

	/** The world direction {@code dir} in model space (+X his left, +Y down, -Z forward) of the tilted body, unit. */
	private static Vector3f modelDir(Player player, Rig r, float partial, Vec3 dir) {
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0, -Mth.sin(bodyYaw));
		double x = dir.dot(right);
		double y = dir.y;
		double z = -dir.dot(fwd);
		double a = -r.lean * r.w;
		double b = -r.roll * r.w;
		double ca = Math.cos(-a), sa = Math.sin(-a);
		double y1 = y * ca - z * sa;
		double z1 = y * sa + z * ca;
		double cb = Math.cos(-b), sb = Math.sin(-b);
		double x2 = x * cb - y1 * sb;
		double y2 = x * sb + y1 * cb;
		Vector3f v = new Vector3f((float) -x2, (float) -y2, (float) z1);
		return v.lengthSquared() < 1.0e-8f ? new Vector3f(0f, -1f, 0f) : v.normalize();
	}

	/**
	 * {xRot, yRot} that point an arm (hanging along model +Y) along the world direction {@code dir}, in the body frame
	 * left after {@link #applyBodyRotation}. Model space: +X = his left, +Y = down, -Z = forward.
	 */
	private static float[] aimAngles(Player player, Rig r, float partial, Vec3 dir, boolean keepClear) {
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0, -Mth.sin(bodyYaw));
		// setupRotations frame: +X right, +Y up, +Z back
		double x = dir.dot(right);
		double y = dir.y;
		double z = -dir.dot(fwd);
		// undo the body tilt: the pose applies Rx(a) then Rz(b) to model vectors (frame = Rx(a) Rz(b) model), so
		// model = Rz(-b) Rx(-a) frame
		double a = -r.lean * r.w;
		double b = -r.roll * r.w;
		double ca = Math.cos(-a), sa = Math.sin(-a);
		double y1 = y * ca - z * sa;
		double z1 = y * sa + z * ca;
		double cb = Math.cos(-b), sb = Math.sin(-b);
		double x2 = x * cb - y1 * sb;
		double y2 = x * sb + y1 * cb;
		// model space
		double mx = -x2;
		double my = -y2;
		double mz = z1;
		double l = Math.sqrt(mx * mx + my * my + mz * mz);
		if (l < 1.0e-4) {
			return new float[] {-2.6f, 0f};
		}
		mx /= l;
		my /= l;
		mz /= l;
		// v0.16.2: an anchor overhead (or over the far shoulder) would aim the arm straight through his head -- keep the
		// arm leaning out to its own side by at least ~20 degrees the higher it points (model +X = his left)
		double up = keepClear ? Mth.clamp(-my, 0.0, 1.0) : 0.0;
		double out = r.right ? -1.0 : 1.0;
		double minOut = 0.36 * up;
		if (mx * out < minOut) {
			mx = out * minOut;
			double rest = Math.sqrt(Math.max(0.0, 1.0 - mx * mx) / Math.max(1.0e-6, my * my + mz * mz));
			my *= rest;
			mz *= rest;
		}
		float xRot = (float) -Math.acos(Mth.clamp(my, -1.0, 1.0));
		float yRot = (float) Math.atan2(-mx, -mz);
		return new float[] {xRot, yRot};
	}

	/** Drops rigs for players not drawn for a while (left the area / the world). */
	public static void prune() {
		long now = System.nanoTime();
		RIGS.values().removeIf(r -> now - r.seenNanos > 5_000_000_000L);
	}

	public static void clear() {
		RIGS.clear();
	}
}
