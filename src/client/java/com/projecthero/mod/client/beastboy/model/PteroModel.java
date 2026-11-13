package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.Blocky.bone;
import static com.projecthero.mod.client.beastboy.model.Blocky.box;
import static com.projecthero.mod.client.beastboy.model.Blocky.boxM;
import static com.projecthero.mod.client.beastboy.model.PteroUv.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's pterodactyl, HQ round: a blocky Pteranodon (about 4 blocks of wingspan) on {@code ptero.png} ({@link PteroUv}):
 * a small furred torso, a two-box neck, a skull with a long swept-back crest and a two-step beak with a hinged lower bill, wings of
 * box bones (humerus, forearm, wrist with three little claws, a three-joint wing finger) carrying a paneled membrane back to the
 * hips, and small hind legs.
 *
 * <p>In the air: full wing-beat cycles (the downstroke spread, the upstroke folding at the elbow and wrist, the outer wing lagging the
 * inner), beats tied to climb and speed via {@link FlightRig} (a glide when sinking), banking into turns. On the ground it walks on
 * its folded wing-hands. Take-off and landing blend between the two. Moves: dive (wings swept back, talons forward at the end),
 * screech / bite (bill open, head thrown up), grab (talons).
 */
public final class PteroModel {
	private static final float K = 0.85f;

	private PteroModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		BeastMesh.texture(TEX);
		MotionState st = MotionState.of(s, 2);
		float dive = s.env("dive", 0.2f, 0.15f);
		FlightRig.update(st, s, s.is("dive"), 0.36f, 0f);
		pose.pushPose();
		Blocky.scale(pose, K);
		float t = s.age;
		float air = st.air, gnd = 1f - air;
		float screech = Math.max(s.env("screech", 0.15f, 0.2f), s.env("bite", 0.15f, 0.3f));
		float grab = Math.max(s.env("grab", 0.25f, 0.25f), s.is("dive") ? Mth.clamp((s.prog - 0.7f) / 0.2f, 0f, 1f) * (1f - Mth.clamp((s.prog - 0.92f) / 0.08f, 0f, 1f)) : 0f);
		float amt = s.walkAmt * gnd;
		float ph = s.walkPos * 0.75f;

		float[] sk = FlightRig.stroke(st.flapPhase, st.flapAmp * (1f - dive));
		float[] skOut = FlightRig.stroke(st.flapPhase - 0.55f, st.flapAmp * (1f - dive));
		float glideLift = 0.1f * st.glide;
		float lift = (sk[0] + glideLift) * air, liftOut = (skOut[0] + glideLift) * air;
		float fold = Math.max(sk[1] * 0.8f, dive * 0.75f) * air;

		// body: hovering bob against the beats in the air, nose-up on the wing-hands on the ground
		float bodyY = Mth.lerp(air, 8.6f - Math.abs(Mth.sin(ph)) * 0.5f * amt, 11f - sk[0] * 1.2f);
		float bodyPitch = Mth.lerp(air, -0.32f + Mth.sin(ph * 2f) * 0.02f * amt, 0.08f + st.pitch + dive * 0.8f - grab * 0.35f);
		float bodyRoll = st.bank + gnd * Mth.sin(ph) * 0.05f * amt;
		Matrix4f bodyM = Rig.mat(0f, bodyY, 0f, 0f, bodyPitch, bodyRoll);

		// ---- hind legs
		for (int sd = -1; sd <= 1; sd += 2) {
			float[] hip = Rig.tp(bodyM, sd * 1.8f, -2.4f, -7.5f);
			float thighP, shinP;
			if (air > 0.5f) {
				float fwd = grab;
				thighP = Mth.lerp(fwd, 1.25f, -0.9f) - bodyPitch;
				shinP = Mth.lerp(fwd, 0.35f, -0.2f);
			} else {
				float[] stp = Rig.step(ph + (sd > 0 ? 0f : Mth.PI), 2.6f * amt, 1.6f * amt);
				float[] ik = Blocky.ik2(hip[1], hip[2], 1f + stp[1], -6.5f + stp[0], 4f, 4f, true);
				thighP = ik[0];
				shinP = ik[1];
			}
			bone(pose, hip[0], hip[1], hip[2], 0f, thighP, 0f);
			boxM(pose, THIGH, -1f, -4f, -1f, sd < 0);
			bone(pose, 0f, -3.6f, 0f, 0f, shinP, 0f);
			boxM(pose, SHIN, -0.5f, -4f, -0.5f, sd < 0);
			bone(pose, 0f, -3.8f, 0f, 0f, -(thighP + shinP) + (air > 0.5f ? 0.6f * (1f - grab) : 0f), 0f);
			box(pose, FOOT, -1f, -1f, -1f);
			float curl = grab > 0f ? (1f - grab) * 0.9f : 0f;
			for (int k = -1; k <= 1; k++) {
				bone(pose, k * 0.6f, -0.5f, 1.8f, k * 0.25f, 0.5f + curl, 0f);
				box(pose, TALON, -0.4f, -0.4f, 0f, 0.8f, 0.8f, 1.4f, false);
				pose.popPose();
			}
			pose.popPose();
			pose.popPose();
			pose.popPose();
		}

		// ---- body, neck, head
		Rig.push(pose, bodyM);
		box(pose, TORSO, -3f, -3f, -5f);
		box(pose, HIPS, -2.5f, -3f, -9.5f);
		box(pose, TAIL, -0.5f, -1f, -13f);
		Matrix4f inv = Blocky.inv(pose);

		// wings (bones in body space; joint positions read back for the membrane)
		for (int sd = -1; sd <= 1; sd += 2) {
			float wk = ground(ph, sd, amt);
			// air angles
			float aShYaw = 0.12f + fold * 0.5f + dive * 0.5f, aShRoll = lift * 0.95f, aShPitch = -0.08f + lift * 0.05f;
			float aElYaw = -0.32f - fold * 0.95f, aElRoll = (liftOut - lift) * 0.4f;
			float aWrYaw = 0.22f + fold * 1.45f + dive * 0.6f, aWrRoll = (liftOut - lift) * 0.5f;
			// ground angles (folded, the wrist a front foot)
			float gShYaw = 1.05f + wk * 0.25f, gShRoll = -0.85f, gShPitch = 0.35f;
			float gElYaw = -2.35f - wk * 0.15f, gElRoll = -0.4f;
			float gWrYaw = 2.9f, gWrRoll = 0.35f;
			float shYaw = Mth.lerp(air, gShYaw, aShYaw), shRoll = Mth.lerp(air, gShRoll, aShRoll), shPitch = Mth.lerp(air, gShPitch, aShPitch);
			float elYaw = Mth.lerp(air, gElYaw, aElYaw), elRoll = Mth.lerp(air, gElRoll, aElRoll);
			float wrYaw = Mth.lerp(air, gWrYaw, aWrYaw), wrRoll = Mth.lerp(air, gWrRoll, aWrRoll);
			float[][] lead = new float[6][];
			bone(pose, sd * 2.8f, 2f, 3f, sd * shYaw, shPitch, sd * shRoll);
			lead[0] = Blocky.at(pose, inv, 0f, 0f, -1.4f);
			box(pose, HUMERUS, sd > 0 ? 0f : -6f, -1f, -1.5f, 6f, 2f, 3f, sd < 0);
			bone(pose, sd * 5.8f, 0f, 0f, sd * elYaw, 0f, sd * elRoll);
			lead[1] = Blocky.at(pose, inv, 0f, 0f, 0f);
			box(pose, FOREARM, sd > 0 ? 0f : -8f, -1f, -1f, 8f, 2f, 2f, sd < 0);
			bone(pose, sd * 8f, 0f, 0f, sd * wrYaw, 0f, sd * wrRoll);
			lead[2] = Blocky.at(pose, inv, 0f, 0f, 0f);
			box(pose, WRIST, -1f, -1f, -1f);
			// the three little clawed fingers, pointing forward (down onto the ground when walking)
			for (int k = 0; k < 3; k++) {
				bone(pose, sd * (0.2f + k * 0.3f), -0.5f, 0.8f, sd * (-0.9f + k * 0.25f), 0.3f + gnd * 0.6f, 0f);
				box(pose, FINGERLET, -0.5f, -0.5f, 0f);
				pose.popPose();
			}
			float droop = 0.04f * air;
			bone(pose, 0f, 0f, 0f, sd * 0.1f, 0f, -sd * droop);
			box(pose, FING1, sd > 0 ? 0f : -10f, -0.5f, -0.5f, 10f, 1f, 1f, sd < 0);
			bone(pose, sd * 10f, 0f, 0f, sd * (0.08f + fold * 0.2f), 0f, -sd * droop);
			lead[3] = Blocky.at(pose, inv, 0f, 0f, 0f);
			box(pose, FING2, sd > 0 ? 0f : -8f, -0.5f, -0.5f, 8f, 0.9f, 0.9f, sd < 0);
			bone(pose, sd * 8f, 0f, 0f, sd * (0.1f + fold * 0.2f), 0f, -sd * droop);
			lead[4] = Blocky.at(pose, inv, 0f, 0f, 0f);
			box(pose, FING3, sd > 0 ? 0f : -6f, -0.4f, -0.4f, 6f, 0.8f, 0.8f, sd < 0);
			lead[5] = Blocky.at(pose, inv, sd * 6f, 0f, 0f);
			for (int k = 0; k < 3; k++) {
				pose.popPose(); // finger joints
			}
			pose.popPose(); // wrist
			pose.popPose(); // elbow
			pose.popPose(); // shoulder
			// membrane panels from the arm and finger back to the hip, the trailing edge scalloped toward the leading edge
			float[] root = { sd * 2.2f, -1.5f, -8.5f };
			float[] f = { 0f, 0.12f, 0.3f, 0.6f, 0.82f, 1f };
			float[][] trail = new float[6][];
			for (int i = 0; i < 6; i++) {
				float[] straight = BeastMesh.lerp(root, lead[5], f[i]);
				float sc = Mth.sin(f[i] * Mth.PI) * 0.28f;
				trail[i] = BeastMesh.lerp(straight, lead[i], sc + (i == 0 ? 0.6f : 0f));
			}
			for (int i = 0; i < 5; i++) {
				Blocky.panel(pose, MEMBRANE, trail[i], trail[i + 1], lead[i + 1], lead[i], f[i], f[i + 1]);
			}
		}

		// neck and head
		float hy = s.yaw(0.9f) * 0.8f, hp = s.pitch(0.8f);
		float nP = Mth.lerp(air, -1.05f, -0.45f + dive * 0.3f) - screech * 0.3f + Mth.sin(ph * 2f) * 0.05f * amt;
		bone(pose, 0f, 1.2f, 4.2f, hy * 0.35f, nP, 0f);
		box(pose, NECK1, -1.5f, -1.5f, -0.5f);
		float n2 = Mth.lerp(air, 0.55f, 0.3f);
		bone(pose, 0f, 0f, 5.2f, hy * 0.25f, n2, 0f);
		box(pose, NECK2, -1.5f, -1.5f, -0.5f);
		float headP = -(nP + n2) - bodyPitch + hp * 0.5f - screech * 0.45f + 0.12f;
		bone(pose, 0f, 0f, 4.4f, hy * 0.4f, headP, Mth.sin(t * 0.04f) * 0.04f * gnd);
		box(pose, SKULL, -2f, -1.5f, -1f);
		bone(pose, 0f, 2.2f, 0.8f, 0f, 0.42f, 0f);
		box(pose, CREST, -0.5f, -1.2f, -10f);
		pose.popPose();
		box(pose, BEAK1, -1.5f, -1.5f, 4f);
		box(pose, BEAK2, -1f, -1.5f, 10f);
		float open = Math.max(screech * (0.85f + Mth.sin(t * 1.6f) * 0.08f), 0.03f);
		bone(pose, 0f, -1.5f, 1.5f, 0f, open * 0.6f, 0f);
		box(pose, LJAW1, -1.5f, -1f, 2.5f);
		box(pose, LJAW2, -1f, -1f, 8.5f);
		pose.popPose();
		pose.popPose();
		pose.popPose();
		pose.popPose();

		pose.popPose(); // body
		pose.popPose(); // scale
	}

	/** Ground stepping of a wing-hand: a -1..1 swing for the shoulder. */
	private static float ground(float ph, int sd, float amt) {
		return Mth.sin(ph + (sd > 0 ? Mth.PI : 0f)) * amt;
	}
}
