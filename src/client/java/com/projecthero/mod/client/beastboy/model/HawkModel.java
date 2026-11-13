package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.Blocky.bone;
import static com.projecthero.mod.client.beastboy.model.Blocky.box;
import static com.projecthero.mod.client.beastboy.model.Blocky.plane;
import static com.projecthero.mod.client.beastboy.model.HawkUv.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's hawk, HQ round (was a tinted vanilla parrot): a blocky raptor on {@code hawk.png} ({@link HawkUv}). Plumaged body
 * with a streaked pale chest, a head with a heavy brow over yellow eyes, a hooked beak with a cere and a lower bill, wings of two covert
 * panels carrying four secondaries and five primaries as alpha-cut feathers, a five-feather tail fan, feathered thighs, yellow scaled
 * legs with three forward toes, a hind toe and black talons.
 *
 * <p>Perched: upright, breathing, bird-like head snaps toward the look, tail flicks, blinks, wings folded with the primaries crossed over
 * the tail. Flying: beat cycles that fold the hand on the upstroke and spread the "fingers" in a glide, flap-and-glide bursts when
 * cruising, banking, the tail fanning to brake and steer, legs tucked. Moves: strike (wings flared back, talons thrown forward and
 * clenched), screech (head forward, beak wide, wings mantled).
 */
public final class HawkModel {
	private static final float K = 0.8f;

	private HawkModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		BeastMesh.texture(TEX);
		MotionState st = MotionState.of(s, 3);
		FlightRig.update(st, s, false, 0.55f, 0.7f);
		pose.pushPose();
		Blocky.scale(pose, K);
		float t = s.age;
		float air = st.air, gnd = 1f - air;
		float strike = s.env("strike", 0.15f, 0.35f);
		float screech = s.env("screech", 0.12f, 0.25f);
		float amt = s.walkAmt * gnd;
		float ph = s.walkPos * 1.1f;
		float breathe = Mth.sin(t * 0.16f);

		float[] sk = FlightRig.stroke(st.flapPhase, st.flapAmp * (1f - strike * 0.6f));
		float[] skOut = FlightRig.stroke(st.flapPhase - 0.6f, st.flapAmp * (1f - strike * 0.6f));
		float lift = (sk[0] + 0.12f * st.glide) * air + strike * 0.7f * air + screech * 0.5f * gnd;
		float liftOut = (skOut[0] + 0.12f * st.glide) * air + strike * 0.5f * air + screech * 0.3f * gnd;
		float fold = sk[1] * 0.85f * air;
		float spreadOpen = air * (1f - fold * 0.3f) + screech * 0.6f * gnd; // 0 folded on the back .. 1 spread

		// bird hop on the ground
		float hop = gnd * Math.abs(Mth.sin(ph)) * 1.2f * amt;
		float bodyY = Mth.lerp(air, 7.6f + hop + breathe * 0.15f, 8f - sk[0] * 0.8f);
		float bodyPitch = Mth.lerp(air, -0.6f + amt * 0.25f - screech * 0.25f, st.pitch + 0.05f - strike * 0.55f);
		Matrix4f bodyM = Rig.mat(0f, bodyY, 0f, 0f, bodyPitch, st.bank);

		// ---- legs
		for (int sd = -1; sd <= 1; sd += 2) {
			float[] hip = Rig.tp(bodyM, sd * 1.5f, -2.4f, -0.5f);
			float tuck = air * (1f - strike);
			float thighP, tarsP;
			if (air > 0.01f && strike < 0.01f) {
				thighP = Mth.lerp(air, 0f, 1.0f) - bodyPitch * gnd;
				tarsP = Mth.lerp(air, 0f, 0.9f);
			} else {
				thighP = 0f;
				tarsP = 0f;
			}
			if (strike > 0f) {
				thighP = Mth.lerp(strike, thighP, -1.0f - bodyPitch);
				tarsP = Mth.lerp(strike, tarsP, -0.3f);
			}
			float step = gnd * Mth.sin(ph + (sd > 0 ? 0f : Mth.PI)) * 0.35f * amt;
			// perched legs stand straight down from the hips to the ground
			float legLen = hip[1];
			bone(pose, hip[0], hip[1], hip[2], 0f, thighP * air + step, 0f);
			box(pose, THIGH, -1.5f, -2.2f, -1.5f, 3f, 3f, 3f, sd < 0);
			float tl = Mth.lerp(air, Math.max(2.5f, legLen - 2.2f - 0.6f), 4f);
			bone(pose, 0f, -2f, 0.2f, 0f, tarsP, 0f);
			box(pose, TARSUS, -0.5f, -tl, -0.5f, 1f, tl, 1f, sd < 0);
			// foot: level on the ground when perched, curled when tucked, open then clenched on the strike
			float clench = strike > 0f ? Mth.clamp((s.prog - 0.5f) / 0.15f, 0f, 1f) : tuck;
			float open = strike * (1f - clench);
			bone(pose, 0f, -tl, 0f, 0f, -(thighP * air + step + tarsP), 0f);
			for (int k = -1; k <= 1; k++) {
				bone(pose, k * 0.55f, 0f, 0.2f, k * (0.35f + open * 0.3f), 0.2f + clench * 1.1f - open * 0.5f, 0f);
				box(pose, TOE, -0.5f, -0.5f, 0f, 1f, 1f, 2.6f, false);
				bone(pose, 0f, 0f, 2.4f, 0f, 0.7f + clench * 0.6f, 0f);
				box(pose, TALON, -0.4f, -0.45f, 0f, 0.8f, 0.8f, 1.5f, false);
				pose.popPose();
				pose.popPose();
			}
			bone(pose, 0f, 0f, 0f, 0f, -0.2f - clench * 1.1f + open * 0.4f, 0f);
			box(pose, TOE, -0.5f, -0.5f, -2.2f, 1f, 1f, 2.2f, false);
			box(pose, TALON, -0.4f, -0.9f, -3.2f, 0.8f, 0.8f, 1.3f, false);
			pose.popPose();
			pose.popPose();
			pose.popPose();
			pose.popPose();
		}

		// ---- body
		Rig.push(pose, bodyM);
		float br = 1f + breathe * 0.025f;
		pose.pushPose();
		pose.scale(br, br, 1f);
		box(pose, BODY, -3f, -3f, -5f);
		box(pose, CHEST, -2.5f, -3.4f, 2.2f);
		pose.popPose();
		box(pose, RUMP, -2f, -2f, -7.5f);

		// tail fan: closed when cruising, fanned to brake / steer / land, flicks when perched
		float fan = Mth.clamp(gnd * 0.15f + air * (0.25f + st.glide * 0.2f + Math.abs(st.bank) * 0.8f + Math.max(0f, -st.climb) * 0.3f) + strike * 0.6f, 0f, 1f);
		float flick = gnd * Math.max(0f, Mth.sin(t * 0.09f)) * Math.max(0f, Mth.sin(t * 0.9f)) * 0.25f;
		bone(pose, 0f, -0.2f, -7f, -st.bank * 0.4f, Mth.lerp(air, 0.35f, 0.05f) - st.climb * 0.15f + flick, st.bank * 0.6f);
		for (int k = -2; k <= 2; k++) {
			bone(pose, k * 0.5f, 0.05f * (2 - Math.abs(k)), 0f, Mth.HALF_PI + k * 0.17f * fan, 0f, 0f);
			plane(pose, TAILF, 0f, 0f, -1f, 9f, 2.2f, false);
			pose.popPose();
		}
		pose.popPose();

		// wings
		for (int sd = -1; sd <= 1; sd += 2) {
			pose.pushPose();
			pose.scale(sd, 1f, 1f); // build the left wing; the right is its mirror
			float shYaw = Mth.lerp(spreadOpen, 1.45f, -0.05f + fold * 0.55f) + strike * 0.35f * air;
			float shRoll = Mth.lerp(spreadOpen, -0.25f, lift) + (gnd * screech * 0.6f);
			float shPitch = Mth.lerp(spreadOpen, 0.05f, -0.06f);
			float wrYaw = Mth.lerp(spreadOpen, -2.85f, -0.08f + fold * 1.05f);
			float wrRoll = Mth.lerp(spreadOpen, 0.0f, (liftOut - lift) * 0.6f);
			float fingers = Mth.lerp(spreadOpen, 0.02f, 0.11f + st.glide * 0.06f);
			bone(pose, 2.7f, sd > 0 ? 1.5f : 1.56f, 2f, shYaw, shPitch, shRoll);
			box(pose, ARM_IN, 0f, -0.5f, -4f);
			// secondaries along the inner wing, pointing back
			for (int k = 0; k < 4; k++) {
				bone(pose, 0.6f + k * 1.2f, -0.2f - k * 0.02f, -2.5f, Mth.lerp(spreadOpen, 0.12f + k * 0.02f, Mth.HALF_PI - 0.06f + k * 0.03f), 0f, 0f);
				plane(pose, SECONDARY, 0f, 0f, -1f, 7f, 2.2f, false);
				pose.popPose();
			}
			bone(pose, 5f, 0f, 0f, wrYaw, 0f, wrRoll);
			box(pose, ARM_OUT, 0f, -0.45f, -2f);
			// primaries fanned from the hand: the outermost leading
			for (int k = 0; k < 5; k++) {
				float yaw = 0.12f + k * fingers + Mth.lerp(spreadOpen, 2.85f, 0f);
				bone(pose, 2.2f + k * 0.65f, -0.15f - k * 0.03f, -0.6f, yaw, 0f, (4 - k) * 0.025f * spreadOpen);
				plane(pose, PRIMARY, 0f, 0f, -1f, 8.5f, 2.3f, false);
				pose.popPose();
			}
			pose.popPose();
			pose.popPose();
			pose.popPose();
		}

		// head: quick bird snaps toward the look, held between them
		float tgtYaw = s.yaw(1.2f), tgtPitch = s.pitch(0.8f);
		if (st.fresh || Math.abs(tgtYaw - st.f[0]) > 0.22f || Math.abs(tgtPitch - st.f[1]) > 0.2f) {
			st.f[0] = tgtYaw;
			st.f[1] = tgtPitch;
		}
		st.headYaw = st.easeAngle(st.headYaw, st.f[0], 0.7f);
		st.headPitch = st.ease(st.headPitch, st.f[1], 0.7f);
		float tilt = gnd * Mth.sin(t * 0.031f) * Math.max(0f, Mth.sin(t * 0.013f)) * 0.35f;
		bone(pose, 0f, 1.6f, 3.6f + screech * 1.2f, st.headYaw, -bodyPitch + st.headPitch * 0.7f - screech * 0.25f + strike * 0.2f, tilt);
		box(pose, HEAD, -2.5f, -1.5f, -2f);
		box(pose, BROW, -2.5f, 2.6f, 1.6f);
		float blink = AnimKit.blink(t, 70f, 0.6f);
		if (blink > 0.05f) {
			// a pale nictitating lid sweeping across the eye
			box(pose, CHEST, 2.45f, 0.4f, 0.6f, 0.15f, 1.8f, 2.4f * blink, false);
			box(pose, CHEST, -2.6f, 0.4f, 0.6f, 0.15f, 1.8f, 2.4f * blink, true);
		}
		box(pose, BEAK, -1f, -0.4f, 3f);
		box(pose, HOOK, -0.5f, -1.4f, 4.1f);
		float open = Math.max(screech * (0.7f + Mth.sin(t * 2.1f) * 0.12f), strike * 0.3f);
		bone(pose, 0f, -0.4f, 3f, 0f, open * 0.7f, 0f);
		box(pose, BEAK_LOW, -1f, -1f, 0f, 2f, 1f, 1.8f, false);
		pose.popPose();
		pose.popPose();

		pose.popPose(); // body
		pose.popPose(); // scale
	}
}
