package com.projecthero.mod.client.omnitrix;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.OmnitrixPodEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "after the capsule is open, when the player gets within 3 blocks the bottom of the watch opens, and the Omnitrix
 * jumps out in a smooth arc onto the player's wrist ... the player then does an animation trying to shake the Omnitrix off:
 * grabbing it, yanking, shaking the arm ... smooth and look good in first person and in third person"): the whole latch on the
 * client, on one clock {@link #clock} -- ticks since the watch leapt, read off the pod while it flies ({@link
 * OmnitrixPodEntity#jumpTime}) and off the wearer's synced {@link Omnitrix#ANIM_LATCH} after it lands (which starts
 * {@link #JUMP} ticks in), so every viewer sees the same beats:
 * <ul>
 *   <li>0-3: the watch's cuff springs open in the cradle and it rises; the left arm starts up (a reflex);</li>
 *   <li>3-12: the leap -- an arc from the cradle to the left wrist, turning into the wrist's orientation, the jaws clamping
 *       shut as it lands on 12 (when the server grants the power and plays the activation sound);</li>
 *   <li>14-34: the right hand grabs it and yanks twice, the left arm pulling against it;</li>
 *   <li>34-50: the arm shaken hard; 50-58: they give up and stare at it; 58-62 back to normal.</li>
 * </ul>
 * The leap is drawn in the world ({@link #renderLeapInWorld}) for everyone but a first-person wearer, whose leap (and both
 * arms) are drawn in the hand pass ({@link #renderFirstPerson}) so it lands on the arm they see. Third person is
 * {@link #pose}. All of it runs off synced state: nothing static per player.
 */
public final class OmnitrixLatchClient {
	public static final float JUMP = OmnitrixConfig.POD_JUMP_TICKS;
	public static final float TOTAL = JUMP + OmnitrixConfig.LATCH_TICKS + 2f;
	/** The watch's scale resting in the cradle. */
	static final float SEAT_SCALE = 1.3f;

	private OmnitrixLatchClient() {
	}

	// ================================================================ the clock

	/** The pod whose watch is leaping at {@code p} now (null when none). */
	@Nullable
	public static OmnitrixPodEntity incoming(Player p) {
		List<OmnitrixPodEntity> pods = p.level().getEntitiesOfClass(OmnitrixPodEntity.class, p.getBoundingBox().inflate(12.0),
				pod -> !pod.empty() && pod.jumpTarget() == p);
		return pods.isEmpty() ? null : pods.get(0);
	}

	/** Ticks since the leap began, or -1 when no latch is playing on {@code p}. */
	public static float clock(Player p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if (s.hasPower && s.animId == Omnitrix.ANIM_LATCH) {
			float t = p.level().getGameTime() + partial - s.animStart + JUMP;
			return t >= 0 && t <= TOTAL ? t : -1f;
		}
		if (s.hasPower) {
			return -1f;
		}
		OmnitrixPodEntity pod = incoming(p);
		if (pod == null) {
			return -1f;
		}
		// the landing's grant can arrive a tick or two after the pod's clock says 12: hold there
		return Math.min(pod.jumpTime(partial), JUMP);
	}

	/** First person: how far the vanilla hands / items are pushed out of view (0..1). */
	public static float handHide(AbstractClientPlayer p, float partial) {
		float t = clock(p, partial);
		if (t < 0f) {
			return 0f;
		}
		return smooth(t / 3f) * (1f - smooth((t - (TOTAL - 6f)) / 6f));
	}

	// ================================================================ the leap

	/** A rigid frame of the watch: its centre, its rotation, its scale (blocks), drawn about the arm-space point {@code centre}. */
	record Frame(Vector3f c, Quaternionf q, float k) {
		Matrix4f matrix(Vector3f centre) {
			return new Matrix4f().translation(c).rotate(q).scale(k).translate(-centre.x, -centre.y, -centre.z);
		}
	}

	/** The arm-space point the watch turns about (its middle: across the cuff, at the band). */
	static Vector3f armCentre(float L) {
		return new Vector3f(L * 0.5f / 16f, OmnitrixModel.CY / 16f, 0f);
	}

	static float armL(Player p) {
		return p instanceof AbstractClientPlayer cp && cp.getSkin().model() == PlayerSkin.Model.SLIM ? 2f : 3f;
	}

	/** The watch in its cradle, in world space ({@code rise}: lifted out of it, blocks). */
	static Frame seat(OmnitrixPodEntity pod, float partial, float rise) {
		Vec3 at = pod.getPosition(partial);
		float bob = Mth.sin(pod.age(partial) * 0.12f) * 0.012f;
		Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(-pod.getYRot())).rotateX((float) Math.toRadians(25f))
				.rotateZ((float) Math.toRadians(90f));
		return new Frame(new Vector3f((float) at.x, (float) (at.y + OmnitrixPodModel.SEAT_Y + bob + rise), (float) at.z), q, SEAT_SCALE);
	}

	/** The jaws: open while it leaps, clamping shut as it lands. */
	static float jaw(float t) {
		return smooth(t / 2.5f) * (1f - smooth((t - (JUMP - 1.8f)) / 1.6f));
	}

	/** How far the watch has lifted out of the cradle before it leaps. */
	static float rise(float t) {
		return 0.14f * smooth(t / 3f);
	}

	/** Between the cradle and the wrist at {@code t}: an arc that rises well over the straight line, turning as it goes. */
	static Frame between(Frame a, Frame b, float t) {
		float f = Mth.clamp((t - 3f) / (JUMP - 3.5f), 0f, 1f);
		float e = f * 0.55f + smooth(f) * 0.45f;
		Vector3f mid = new Vector3f(a.c()).lerp(b.c(), 0.5f);
		float dist = a.c().distance(b.c());
		mid.y += 0.45f + 0.18f * dist;
		// a quadratic Bezier through the raised middle
		Vector3f p0 = new Vector3f(a.c()).lerp(mid, e), p1 = new Vector3f(mid).lerp(b.c(), e);
		Vector3f c = p0.lerp(p1, e);
		Quaternionf q = new Quaternionf(a.q()).slerp(b.q(), smooth(f));
		// a quarter twist through the air, settling back by the landing
		q.rotateX(Mth.sin(f * Mth.PI) * 0.6f);
		return new Frame(c, q, Mth.lerp(smooth(f), a.k(), b.k()));
	}

	/** The left arm's watch frame in world space for a third-person player model posed at the latch's landing (tick 12). */
	static Frame wristThirdPerson(Player p, float partial) {
		float[] arm = sample3p(JUMP);
		boolean slim = armL(p) < 3f;
		Vec3 at = p.getPosition(partial);
		float body = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		Matrix4f m = new Matrix4f().translation((float) at.x, (float) at.y, (float) at.z);
		float s = p.getScale();
		m.scale(s);
		m.rotateY((float) Math.toRadians(180f - body));
		m.scale(-1f, -1f, 1f);
		m.scale(0.9375f);
		m.translate(0f, -1.501f, 0f);
		m.translate(5f / 16f, (slim ? 2.5f : 2f) / 16f, 0f);
		m.rotate(new Quaternionf().rotationZYX(arm[L_Z], arm[L_Y], arm[L_X]));
		return frameOf(m, armL(p));
	}

	private static Frame frameOf(Matrix4f m, float L) {
		Vector3f c = m.transformPosition(armCentre(L), new Vector3f());
		Quaternionf q = m.getNormalizedRotation(new Quaternionf());
		float k = new Vector3f(m.m00(), m.m01(), m.m02()).length();
		return new Frame(c, q, k);
	}

	/**
	 * The pod's renderer: the watch in its cradle, springing open and leaping. {@code pose} is the pod's (world-aligned, at the
	 * pod). Returns without drawing once the wearer has it (their layer draws it) or while a first-person wearer draws it.
	 */
	public static void renderLeapInWorld(OmnitrixPodEntity pod, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = pod.jumpTime(partial);
		Player target = pod.jumpTarget();
		Vec3 origin = pod.getPosition(partial);
		Frame f;
		float jaws = 0f;
		float L = 3f;
		if (t < 0f || target == null) {
			f = seat(pod, partial, 0f);
		} else {
			if (Omnitrix.hasPower(target) || t > JUMP + 4f || firstPersonSelf(target)) {
				return;
			}
			L = armL(target);
			Frame a = seat(pod, partial, rise(t));
			f = t < 3f ? a : between(a, wristThirdPerson(target, partial), Math.min(t, JUMP));
			jaws = jaw(Math.min(t, JUMP));
		}
		Vector3f rel = new Vector3f(f.c()).sub((float) origin.x, (float) origin.y, (float) origin.z);
		Frame local = new Frame(rel, f.q(), f.k());
		pose.pushPose();
		pose.mulPose(local.matrix(armCentre(L)));
		OmnitrixModel.draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(OmnitrixWatch.TEX)), light, L, 0f, 0f, false, true, jaws);
		pose.popPose();
		// the green glow riding with it
		pose.pushPose();
		pose.translate(rel.x, rel.y + 0.05f, rel.z);
		float pulse = 0.42f + Mth.sin(pod.age(partial) * 0.15f) * 0.05f;
		OmnitrixRenderers.softGlow(pose, buffers, pulse * (t > 0 ? 0.8f : 1f), 0x7CFF6A, t > 0 ? 120 : 90);
		pose.popPose();
	}

	static boolean firstPersonSelf(Player target) {
		Minecraft mc = Minecraft.getInstance();
		return target == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	// ================================================================ first person

	/** FP left arm keys: {tick, x, y, z, roll, yaw, pitch} (see {@link OmnitrixFirstPerson#leftBase}). */
	private static final float[][] FP_LEFT = {
			{ 0, -0.5f, -1.25f, -0.9f, 14, 18, -18 },
			{ 8, -0.42f, -0.44f, -0.9f, 20, 12, -10 },
			{ 12, -0.4f, -0.34f, -0.88f, 17, 16, -14 },
			{ 14, -0.4f, -0.41f, -0.88f, 22, 16, -9 },
			{ 19, -0.42f, -0.28f, -0.9f, 14, 18, -18 },
			{ 23, -0.62f, -0.31f, -1.0f, 5, 30, -8 },
			{ 26, -0.44f, -0.28f, -0.9f, 14, 18, -18 },
			{ 30, -0.67f, -0.33f, -1.03f, 3, 33, -6 },
			{ 34, -0.5f, -0.36f, -0.95f, 16, 16, -12 },
			{ 48, -0.5f, -0.36f, -0.95f, 16, 16, -12 },
			{ 53, -0.45f, -0.25f, -0.92f, 14, 18, -18 },
			{ 58, -0.45f, -0.25f, -0.92f, 14, 18, -18 },
			{ 64, -0.5f, -1.25f, -0.9f, 14, 18, -18 } };

	/** The shake: how hard (0..1) at {@code t}, and its phase. */
	static float shakeEnv(float t) {
		return smooth((t - 34f) / 2f) * (1f - smooth((t - 47f) / 3f));
	}

	static float shakeWave(float t, float phase) {
		return Mth.sin((t - 34f) / 4.5f * Mth.TWO_PI + phase);
	}

	/** How firmly the right hand holds the watch (0 off .. 1 gripping) and how far it has pulled it down the arm (px). */
	static float grip(float t) {
		return smooth((t - 14.5f) / 4f) * (1f - smooth((t - 33f) / 3f));
	}

	static float yank(float t) {
		return 3.4f * bump(t, 20f, 23f, 26f) + 4.6f * bump(t, 27f, 30f, 33f);
	}

	/** 0 before {@code a}, 1 at {@code m}, 0 again from {@code b} (smooth both ways). */
	static float bump(float t, float a, float m, float b) {
		return t < m ? smooth((t - a) / (m - a)) : 1f - smooth((t - m) / (b - m));
	}

	private static Matrix4f fpLeftBase(float t) {
		float[] k = sample(FP_LEFT, t);
		float sh = shakeEnv(t);
		if (sh > 0f) {
			float w = shakeWave(t, 0f);
			k[2] += 0.075f * w * sh;
			k[1] += 0.03f * shakeWave(t, 0.6f) * sh;
			k[4] += 15f * w * sh;
			k[6] += 9f * shakeWave(t, 1.1f) * sh;
		}
		// a tremble while the two hands fight over it
		float g = grip(t);
		if (g > 0f) {
			k[2] += 0.006f * Mth.sin(t * 2.7f) * g;
			k[4] += 1.2f * Mth.sin(t * 3.3f) * g;
		}
		return OmnitrixFirstPerson.leftBase(k[1], k[2], k[3], k[4], k[5], k[6]);
	}

	/** The right hand on the watch: its palm over the face, fingers wrapped across the cuff (watch space, px). */
	static float[] gripPose(float L, float yankPx) {
		float top = OmnitrixModel.coreTop(L, 0f);
		float cy = OmnitrixModel.CY;
		return new float[] { top + 1.0f, cy - 0.2f + yankPx, 2.4f, -0.5f, -0.25f, 0.85f, -1f, 0f, 0.1f };
	}

	/**
	 * The hand pass (after the off hand): both arms through the latch, and the leap itself onto the arm the wearer sees. Called
	 * for every first-person frame; does nothing when no latch is playing.
	 */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float partial) {
		float t = clock(p, partial);
		if (t < 0f || p.isInvisible()) {
			return;
		}
		PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
		Matrix4f left = fpLeftBase(t);
		pose.pushPose();
		pose.mulPose(left);
		renderer.renderLeftHand(pose, buffers, light, p);
		pose.popPose();
		float L = armL(p);
		Matrix4f watch = OmnitrixFirstPerson.watchSpace(renderer, left);
		// the right hand: up from below-right, onto the watch, yanking, then away
		float g = grip(t);
		if (g > 0.01f) {
			float[] off = OmnitrixHands.pose(OmnitrixHands.OFF, L, 0f, 0f);
			float[] on = gripPose(L, yank(t));
			float[] h = new float[9];
			for (int i = 0; i < 9; i++) {
				h[i] = off[i] + (on[i] - off[i]) * g;
			}
			OmnitrixFirstPerson.renderRightHandAt(pose, buffers, light, p, renderer, watch, h);
		}
		// the leap, drawn here so it lands on this arm
		if (!Omnitrix.hasPower(p) && t <= JUMP + 4f) {
			OmnitrixPodEntity pod = incoming(p);
			if (pod == null) {
				return;
			}
			Frame a = toCamera(seat(pod, partial, rise(t)));
			Frame b = frameOf(OmnitrixFirstPerson.watchSpace(renderer, fpLeftBase(JUMP)), L);
			Frame f = t < 3f ? a : between(a, b, Math.min(t, JUMP));
			pose.pushPose();
			pose.mulPose(f.matrix(armCentre(L)));
			OmnitrixModel.draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(OmnitrixWatch.TEX)), light, L, 0f, 0f, false, true,
					jaw(Math.min(t, JUMP)));
			pose.popPose();
		}
	}

	/**
	 * A world frame into the hand pass's camera space -- with its position re-projected from the world's field of view to the
	 * hand pass's fixed 70 degrees, so it shows where it is in the world.
	 */
	static Frame toCamera(Frame w) {
		Minecraft mc = Minecraft.getInstance();
		Camera cam = mc.gameRenderer.getMainCamera();
		Quaternionf inv = new Quaternionf(cam.rotation()).conjugate();
		Vec3 cp = cam.getPosition();
		Vector3f c = new Vector3f(w.c()).sub((float) cp.x, (float) cp.y, (float) cp.z);
		inv.transform(c);
		float fov = mc.options.fov().get();
		float fix = (float) (Math.tan(Math.toRadians(35.0)) / Math.tan(Math.toRadians(fov * 0.5)));
		c.x *= fix;
		c.y *= fix;
		return new Frame(c, new Quaternionf(inv).mul(w.q()), w.k());
	}

	// ================================================================ third person

	/** 3P keys: {tick, rArm x y z, lArm x y z, left shoulder drop (px), head pitch}. */
	private static final int R_X = 1, R_Y = 2, R_Z = 3, L_X = 4, L_Y = 5, L_Z = 6, DROP = 7, HEAD = 8;
	private static final float[][] TP = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, -0.25f, 0, 0.1f, -0.9f, 0.15f, -0.15f, 0, 0.15f },
			{ 12, -0.3f, 0, 0.12f, -1.35f, 0.35f, -0.3f, 0, 0.32f },
			{ 14, -0.3f, 0, 0.12f, -1.55f, 0.4f, -0.36f, 0, 0.36f },
			// the grab: the left forearm across the chest (OmnitrixPose's raised wrist), the right palm down on the watch
			{ 19, -2.445f, -0.111f, 1.416f, -2.169f, 0.04f, -1.809f, 6, 0.42f },
			{ 23, -1.0f, -0.35f, 0.55f, -1.55f, 0.55f, -0.95f, 3, 0.25f },
			{ 26, -2.3f, -0.1f, 1.3f, -2.1f, 0.05f, -1.7f, 6, 0.4f },
			{ 30, -0.85f, -0.45f, 0.65f, -1.45f, 0.6f, -1.05f, 2, 0.2f },
			{ 34, -0.4f, 0, 0.35f, -1.3f, 0.25f, -0.5f, 0, 0.25f },
			{ 48, -0.4f, 0, 0.35f, -1.3f, 0.25f, -0.5f, 0, 0.25f },
			// giving up: a long look at it
			{ 53, -0.25f, 0, 0.1f, -2.169f, 0.04f, -1.809f, 6, 0.42f },
			{ 58, -0.25f, 0, 0.1f, -2.169f, 0.04f, -1.809f, 6, 0.42f },
			{ 64, 0, 0, 0, 0, 0, 0, 0, 0 } };

	static float[] sample3p(float t) {
		float[] k = sample(TP, t);
		float sh = shakeEnv(t);
		if (sh > 0f) {
			float w = shakeWave(t, 0f);
			k[L_X] += 0.6f * w * sh;
			k[L_Z] -= 0.28f * shakeWave(t, 1.0f) * sh;
			k[L_Y] += 0.12f * shakeWave(t, 2.0f) * sh;
			k[R_X] += 0.2f * shakeWave(t, Mth.PI) * sh;
			k[HEAD] += 0.06f * w * sh;
		}
		float g = grip(t);
		if (g > 0f) {
			k[L_X] += 0.05f * Mth.sin(t * 2.7f) * g;
			k[R_X] += 0.05f * Mth.sin(t * 2.7f + 1f) * g;
		}
		return k;
	}

	/** The third-person pose (from {@code OmnitrixPose}): the arms and head through the latch; nothing when none plays. */
	public static void pose(AbstractClientPlayer p, HumanoidModel<?> m, float partial) {
		float t = clock(p, partial);
		if (t < 0f) {
			return;
		}
		float[] k = sample3p(t);
		// eased in from the walk cycle and back out to it
		float w = smooth(t / 2f) * (1f - smooth((t - (TOTAL - 4f)) / 4f));
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, k[R_X]);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, k[R_Y]);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, k[R_Z]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, k[L_X]);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, k[L_Y]);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, k[L_Z]);
		m.leftArm.y += k[DROP] * w;
		m.head.xRot = Mth.lerp(w, m.head.xRot, k[HEAD]);
		m.head.yRot = Mth.lerp(w * 0.8f, m.head.yRot, 0.18f);
		m.hat.copyFrom(m.head);
	}

	// ================================================================ helpers

	/** Smoothstepped keyframes {tick, values...} at {@code t} (clamped to the ends). */
	static float[] sample(float[][] keys, float t) {
		if (t <= keys[0][0]) {
			return keys[0].clone();
		}
		for (int i = 1; i < keys.length; i++) {
			if (t <= keys[i][0]) {
				float[] a = keys[i - 1], b = keys[i];
				float f = smooth((t - a[0]) / Math.max(1e-4f, b[0] - a[0]));
				float[] o = new float[a.length];
				o[0] = t;
				for (int j = 1; j < a.length; j++) {
					o[j] = Mth.lerp(f, a[j], b[j]);
				}
				return o;
			}
		}
		return keys[keys.length - 1].clone();
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}
}
