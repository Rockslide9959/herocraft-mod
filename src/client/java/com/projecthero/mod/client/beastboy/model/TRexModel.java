package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.AnimKit.key;
import static com.projecthero.mod.client.beastboy.model.Blocky.bone;
import static com.projecthero.mod.client.beastboy.model.Blocky.box;
import static com.projecthero.mod.client.beastboy.model.Blocky.boxM;
import static com.projecthero.mod.client.beastboy.model.TRexUv.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's T-Rex, HQ round: a blocky Minecraft-style tyrannosaur (about 3.4 blocks tall, 5 long) on its own atlas
 * ({@code trex.png}, {@link TRexUv}): layered torso, belly and chest, a two-box neck, a big skull with brow ridges, cheeks, nose ridge,
 * a hinged jaw with a tongue and rows of teeth, eyelids that blink; tiny two-fingered arms; huge striped thighs on IK legs (thigh,
 * shin, metatarsus, foot, three clawed toes and a dewclaw) that stay planted; an eight-box tail with scutes down the back.
 *
 * <p>Animation: a heavy stomping walk (each footfall drops the body and jolts the head, the hips roll and shift over the standing leg,
 * the tail swings against the hips and trails turns), a damped head that follows the look, breathing, blinking, a slack jaw. Moves:
 * bite (rear back, lunge, snap), roar (inhale, thrust, a shaking hold), swipe / sweep / tail (wind up, whip the tail through),
 * stomp, charge.
 */
public final class TRexModel {
	/** Model units -> pixels. */
	private static final float K = 1.25f;
	private static final float[] TAIL_LEN = { 9, 9, 9, 8, 8, 7, 7, 6 };
	private static final int[][] TAILS = { TAIL0, TAIL1, TAIL2, TAIL3, TAIL4, TAIL5, TAIL6, TAIL7 };

	private TRexModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		BeastMesh.texture(TEX);
		MotionState st = MotionState.of(s, 1);
		pose.pushPose();
		Blocky.scale(pose, K);
		float t = s.age;
		float amt = s.walkAmt;
		float charge = s.env("charge", 0.15f, 0.2f);
		float ph = s.walkPos * 0.42f * (1f + charge * 0.15f);

		// ---- moves
		float[] BT = { 0f, 0.3f, 0.48f, 0.56f, 1f };
		float biteJaw = key(s, "bite", BT, new float[] { 0.08f, 1.0f, 0.9f, 0f, 0.06f });
		float biteHead = key(s, "bite", BT, new float[] { 0f, -0.35f, 0.3f, 0.32f, 0f });
		float biteNeck = key(s, "bite", BT, new float[] { 0f, -0.15f, 0.4f, 0.35f, 0f });
		float biteBody = key(s, "bite", BT, new float[] { 0f, -0.04f, 0.12f, 0.12f, 0f });
		float[] RT = { 0f, 0.18f, 0.3f, 0.8f, 1f };
		float roarJaw = key(s, "roar", RT, new float[] { 0.08f, 0.4f, 1.0f, 0.95f, 0.08f });
		float roarHead = key(s, "roar", RT, new float[] { 0f, -0.6f, -0.22f, -0.2f, 0f });
		float roarNeck = key(s, "roar", RT, new float[] { 0f, -0.35f, 0.14f, 0.14f, 0f });
		float roarBody = key(s, "roar", RT, new float[] { 0f, -0.14f, 0.06f, 0.06f, 0f });
		float roarHold = key(s, "roar", RT, new float[] { 0f, 0f, 1f, 1f, 0f });
		float roarIn = key(s, "roar", RT, new float[] { 0f, 1f, 0.6f, 0.6f, 0f });
		boolean sweeping = s.isAny("swipe", "sweep", "tail");
		float[] ST = { 0f, 0.3f, 0.62f, 1f };
		float sweepTwist = sweeping ? key(s.prog, ST, new float[] { 0f, 0.4f, -0.55f, 0f }) : 0f;
		float stomp = s.is("stomp") ? s.prog : -1f;
		float stompLift = stomp < 0f ? 0f : key(stomp, new float[] { 0f, 0.45f, 0.58f, 1f }, new float[] { 0f, 1f, 0f, 0f });
		float stompHit = stomp < 0.56f ? 0f : (float) Math.exp(-(stomp - 0.56f) * 14f);

		// ---- walk: footfall impacts, weight shift and counter-rotation
		float impact = (AnimKit.impact(ph - Mth.HALF_PI) + AnimKit.impact(ph + Mth.HALF_PI)) * amt;
		st.stomp = st.ease(st.stomp, impact + stompHit * 1.4f, 0.9f);
		float breathe = Mth.sin(t * 0.085f);
		float bob = -st.stomp * 2.2f + Math.abs(Mth.sin(ph)) * 0.6f * amt + breathe * 0.3f - roarIn * 0.4f + roarBody * -6f;
		float shiftX = -Mth.cos(ph) * 1.1f * amt;
		float roll = -Mth.cos(ph) * 0.05f * amt;
		float twist = Mth.sin(ph) * 0.07f * amt + sweepTwist;
		float bodyPitch = 0.02f + 0.05f * amt + charge * 0.14f + biteBody + roarBody + stompHit * 0.05f;
		Matrix4f body = Rig.mat(shiftX, 23f + bob, -2f, twist, bodyPitch, roll);

		// head follow, damped
		st.headYaw = st.easeAngle(st.headYaw, s.yaw(0.85f), 0.22f);
		st.headPitch = st.ease(st.headPitch, s.pitch(0.7f), 0.22f);

		// ---- legs (root space, planted)
		for (int sd = -1; sd <= 1; sd += 2) {
			float lph = ph + (sd > 0 ? 0f : Mth.PI);
			float[] stp = Rig.step(lph, 7.5f * amt + 2f * charge, 4.5f * amt);
			float lift = stp[1], fz = 2f + stp[0];
			if (sd < 0 && stomp >= 0f) {
				lift = stompLift * 9f;
				fz = 3f + stompLift * 1.5f;
			}
			float[] hip = Rig.tp(body, sd * 7.5f, -1f, 0f);
			float metaA = -0.45f - lift * 0.03f; // metatarsus tilt (forward), steeper while lifted
			float ballY = lift + 2f, ballZ = fz;
			float ankY = ballY + 7f * Mth.cos(metaA), ankZ = ballZ + 7f * Mth.sin(metaA);
			float[] ik = Blocky.ik2(hip[1], hip[2], ankY, ankZ, 11f, 11f, true);
			float footPitch = lift * 0.045f; // toes droop in the swing
			bone(pose, hip[0], hip[1], hip[2], twist * 0.3f, ik[0], 0f);
			boxM(pose, THIGH, -3.5f, -12.5f, -5f, sd < 0);
			bone(pose, 0f, -11f, 0f, 0f, ik[1], 0f);
			boxM(pose, SHIN, -2.5f, -12f, -2.5f, sd < 0);
			bone(pose, 0f, -11f, 0f, 0f, metaA - ik[0] - ik[1], 0f);
			boxM(pose, META, -2f, -7.5f, -2f, sd < 0);
			// dewclaw on the inner back of the metatarsus
			box(pose, CLAW_S, -sd * 1.6f - 0.5f, -5f, -2.6f, 1f, 1f, 1.6f, false);
			bone(pose, 0f, -7f, 0f, 0f, -metaA + footPitch, 0f);
			boxM(pose, FOOT, -3f, -2.5f, -2f, sd < 0);
			for (int k = -1; k <= 1; k++) {
				bone(pose, k * 2f, -1.5f, 4.5f, k * 0.3f + sd * 0.06f, footPitch * 0.5f, 0f);
				float tl = k == 0 ? 5f : 4f;
				box(pose, TOE, -1f, -1f, -0.5f, 2f, 2f, tl, sd < 0);
				bone(pose, 0f, -0.2f, tl - 0.8f, 0f, 0.45f, 0f);
				box(pose, CLAW, -0.8f, -0.8f, 0f, 1.6f, 1.6f, 2.6f, false);
				pose.popPose();
				pose.popPose();
			}
			pose.popPose();
			pose.popPose();
			pose.popPose();
			pose.popPose();
		}

		// ---- body
		Rig.push(pose, body);
		box(pose, TORSO, -7f, -6f, -9f);
		box(pose, BELLY, -6f, -9f, -5f);
		for (int i = 0; i < 4; i++) {
			float z = -7f + i * 5f;
			box(pose, SCUTE, -1f, 7.5f, z);
			box(pose, SCUTE_S, 2.5f, 7.6f, z + 0.5f);
			boxM(pose, SCUTE_S, -4.5f, 7.6f, z + 0.5f, true);
		}
		// chest (breathes; swells on the roar's inhale)
		float br = 1f + breathe * 0.02f + roarIn * 0.06f;
		bone(pose, 0f, 1f, 12f, 0f, -0.35f, 0f);
		pose.scale(br, br, br);
		box(pose, CHEST, -6f, -6f, -3f);
		pose.popPose();

		// tiny arms: idle fidget, flared on the roar, tucked on the bite
		for (int sd = -1; sd <= 1; sd += 2) {
			float fid = Mth.sin(t * 0.11f + sd * 1.3f) * 0.12f + Mth.sin(ph + (sd > 0 ? 0 : Mth.PI)) * 0.1f * amt;
			float flare = roarHold;
			bone(pose, sd * 6.2f, -1f, 15f, sd * flare * 0.25f, -0.55f + fid - flare * 0.5f + biteBody * 2f, -sd * (0.15f + flare * 0.35f));
			boxM(pose, UPPER_ARM, -1.5f, -5f, -1.5f, sd < 0);
			bone(pose, 0f, -4.5f, 0f, 0f, -1.0f + fid * 0.6f - flare * 0.4f, 0f);
			boxM(pose, FOREARM, -1f, -4f, -1f, sd < 0);
			bone(pose, 0f, -3.8f, 0f, 0f, -0.35f - flare * 0.3f, 0f);
			for (int f = 0; f < 2; f++) {
				float fx = (f == 0 ? -1.1f : 0.1f);
				box(pose, FINGER, fx, -2.6f, -0.5f, 1f, 2.8f, 1f, false);
				box(pose, CLAW_S, fx + 0.1f, -3.9f, -0.4f, 0.8f, 1.5f, 0.8f, false);
			}
			pose.popPose();
			pose.popPose();
			pose.popPose();
		}

		// tail: swings against the hips, trails turns, lifts on the roar, whips on the sweep
		bone(pose, 0f, 1f, -8.5f, 0f, 0f, 0f);
		for (int k = 0; k < 8; k++) {
			float lag = k * 0.45f;
			float yaw = -Mth.sin(ph - lag) * 0.05f * amt + Mth.sin(t * 0.06f - lag) * 0.035f + s.tailLag * 0.16f;
			if (sweeping) {
				float pk = Mth.clamp(s.prog - k * 0.035f, 0f, 1f);
				yaw += key(pk, ST, new float[] { 0f, -0.18f, 0.24f, 0f }) * (1f + k * 0.12f);
			}
			float pitch = (k == 0 ? -0.07f : 0.025f) + roarHold * 0.03f - biteBody * 0.2f + Mth.sin(ph * 2f - lag) * 0.012f * amt;
			if (k > 0) {
				bone(pose, 0f, 0f, -(TAIL_LEN[k - 1] - 1f), yaw, pitch, 0f);
			} else {
				bone(pose, 0f, 0f, 0f, yaw - twist * 0.5f, pitch, 0f);
			}
			int[] p = TAILS[k];
			box(pose, p, -p[2] * 0.5f, -p[3] * 0.5f, -TAIL_LEN[k]);
			if (k < 5) {
				box(pose, SCUTE, -1f, p[3] * 0.5f - 0.6f, -TAIL_LEN[k] * 0.65f, 2f, 2f - k * 0.25f, 3f, false);
			}
		}
		for (int k = 0; k < 8; k++) {
			pose.popPose();
		}
		pose.popPose();

		// neck and head
		float hy = st.headYaw, hp = st.headPitch;
		float shake = roarHold * Mth.sin(t * 1.9f) * 0.12f;
		float neckYaw = hy * 0.4f - twist * 0.5f;
		float neckPitch = -0.78f + hp * 0.25f + biteNeck + roarNeck - charge * 0.1f + st.stomp * 0.05f;
		bone(pose, 0f, 4f, 14.5f, neckYaw, neckPitch, 0f);
		box(pose, NECK1, -5f, -5f, -2f);
		box(pose, SCUTE, -1f, 4.4f, 1f);
		bone(pose, 0f, 0f, 5.5f, hy * 0.2f, -0.12f + biteNeck * 0.3f, 0f);
		box(pose, NECK2, -4.5f, -4.5f, -1.5f);
		box(pose, SCUTE, -1f, 3.9f, 0.5f, 2f, 1.6f, 2.4f, false);
		float headPitch = -(neckPitch - 0.12f + biteNeck * 0.3f) - bodyPitch + hp * 0.55f + biteHead + roarHead - charge * 0.05f
				+ Mth.sin(ph * 2f) * 0.02f * amt + st.stomp * 0.06f;
		bone(pose, 0f, 0.5f, 4.5f, hy * 0.4f + shake, headPitch, shake * 0.3f);
		float jaw = Math.max(0.05f + Math.max(0f, Mth.sin(t * 0.045f)) * 0.06f, Math.max(biteJaw, roarJaw));
		if (roarHold > 0f) {
			jaw += Mth.sin(t * 2.7f) * 0.04f * roarHold;
		}
		float blink = Math.max(AnimKit.blink(t, 95f, 0.3f), roarHold * 0.45f);
		head(pose, jaw, blink);
		pose.popPose();
		pose.popPose();
		pose.popPose();

		pose.popPose(); // body
		pose.popPose(); // scale
	}

	/** The head (pivot at the neck joint, +Z forward, model units), jaw 0..1+ open, blink 0..1 shut. */
	static void head(PoseStack pose, float jaw, float blink) {
		box(pose, SKULL, -6.5f, -3f, -3f);
		box(pose, SNOUT, -5f, -3f, 8f);
		box(pose, NOSE_RIDGE, -3f, 4f, 9.5f);
		box(pose, BROW, 3.5f, 7.5f, 1.5f);
		boxM(pose, BROW, -6.5f, 7.5f, 1.5f, true);
		box(pose, CHEEK, 6.1f, -2.5f, -1f);
		boxM(pose, CHEEK, -8.1f, -2.5f, -1f, true);
		if (blink > 0.04f) {
			float lh = 5.2f * blink;
			box(pose, LID, 6.5f, 6.2f - lh, 2.4f, 0.4f, lh, 5.2f, false);
			box(pose, LID, -6.9f, 6.2f - lh, 2.4f, 0.4f, lh, 5.2f, true);
		}
		// upper teeth: a row along each side of the snout and across the front, an overbite past the jaw
		for (int sd = -1; sd <= 1; sd += 2) {
			float x = sd > 0 ? 3.9f : -4.9f;
			for (int i = 0; i < 5; i++) {
				float z = 9.2f + i * 2.1f;
				box(pose, i == 1 || i == 2 ? TOOTH_BIG : TOOTH, x, i == 1 || i == 2 ? -6f : -5f, z);
			}
			box(pose, TOOTH, sd > 0 ? 4.9f : -5.9f, -5f, 4.5f, 1f, 2f, 1f, false);
		}
		for (int i = 0; i < 4; i++) {
			box(pose, TOOTH, -3.5f + i * 2.05f, -4.6f, 18f, 1f, 1.6f, 0.8f, false);
		}
		// jaw
		bone(pose, 0f, -3f, -1f, 0f, Mth.clamp(jaw, 0f, 1.25f) * 0.8f, 0f);
		box(pose, JAW, -4.5f, -4f, 0f);
		box(pose, TONGUE, -2.5f, -0.6f, 3f);
		box(pose, THROAT, -4f, -7f, 0.5f);
		for (int sd = -1; sd <= 1; sd += 2) {
			float x = sd > 0 ? 3.2f : -4.2f;
			for (int i = 0; i < 5; i++) {
				box(pose, TOOTH, x, -0.2f, 6.5f + i * 2.3f, 1f, 1.8f, 1f, false);
			}
		}
		pose.popPose();
	}

	/** T-Rex Roar quick morph: a neck stump and the head over the player's head (feet-origin pixels, +Z front); jaw 0..1. */
	public static void quickHead(PoseStack pose, float jaw, float t) {
		BeastMesh.texture(TEX);
		pose.pushPose();
		Blocky.scale(pose, 0.8f);
		bone(pose, 0f, -6f, -4f, 0f, 0f, 0f);
		box(pose, NECK2, -4.5f, -4.5f, -3f);
		pose.popPose();
		head(pose, jaw, jaw * 0.4f);
		pose.popPose();
	}
}
