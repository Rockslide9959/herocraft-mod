package com.projecthero.mod.client.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.data.SpiderManState;
import com.projecthero.mod.spider.data.SpiderMoveState;

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
 * <li>Styles per swing (chosen from the anchor, so every viewer agrees): the one-arm classic, a two-hand grip on fast
 * lines, a cannonball tuck through the bottom of fast arcs, and a corkscrew spin when a long, fast swing is let go.</li>
 * <li>Everything eases (no snapping): the swing weight in and out, every angle, and the aim from one anchor to the
 * next when swings are chained.</li>
 * </ul>
 * Torso bends go through {@link HipBend}, so the torso never leaves the legs.
 */
public final class SpiderSwingPose {
	private static final int CLASSIC = 0;
	private static final int TWO_HAND = 1;
	private static final int CANNONBALL = 2;
	private static final int SPIN = 3;

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
			// a new line: pick the style from the anchor so everyone agrees
			int h = (int) (Math.floor(st.anchorX) * 73856093) ^ (int) (Math.floor(st.anchorY) * 19349663)
					^ (int) (Math.floor(st.anchorZ) * 83492791);
			h = (h >>> 3) & 0xFF;
			r.style = speed > 1.3 && h % 3 == 0 ? TWO_HAND : h % 4 == 1 ? CANNONBALL : h % 4 == 3 ? SPIN : CLASSIC;
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
		float cannonT = 0f, twoT = 0f, upT = 0f, fastT = 0f, peekT = 0f;
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
		Rig r = rig(player, partial);
		if (r == null) {
			return;
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
