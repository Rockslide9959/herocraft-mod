package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BatUv.*;
import static com.projecthero.mod.client.beastboy.model.Blocky.bone;
import static com.projecthero.mod.client.beastboy.model.Blocky.box;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's bat, HQ round (was a tinted vanilla bat): a blocky fruit-bat-cute bat on {@code bat.png} ({@link BatUv}). Furry
 * body and chest, a round head with big tall ears (ridged insides), a short snout with a nose, tiny fangs and a dropping jaw, big dark
 * glossy eyes; wings of box bones (arm, forearm, a clawed thumb and three long fingers) with membrane panels scalloped between the
 * finger tips and back to the legs, a tail membrane, little clawed feet.
 *
 * <p>Flying: fast deep beats with the fingers folding on the upstroke, a fluttery jitter, banking. On the ground it crouches on its
 * folded wing-wrists and crawls. Ears twitch, eyes blink. Move: screech (the echolocation pulse: ears perk forward, head pushes out,
 * mouth wide, a quick shudder).
 */
public final class BatModel {
	private static final float K = 0.85f;

	private BatModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		BeastMesh.texture(TEX);
		MotionState st = MotionState.of(s, 4);
		FlightRig.update(st, s, false, 0.8f, 0f);
		pose.pushPose();
		Blocky.scale(pose, K);
		float t = s.age;
		float air = st.air, gnd = 1f - air;
		float screech = s.env("screech", 0.1f, 0.3f);
		float amt = s.walkAmt * gnd;
		float ph = s.walkPos * 1.2f;

		float[] sk = FlightRig.stroke(st.flapPhase, Math.max(st.flapAmp, 0.45f * air));
		float[] skOut = FlightRig.stroke(st.flapPhase - 0.7f, Math.max(st.flapAmp, 0.45f * air));
		float lift = sk[0] * 1.15f * air, liftOut = skOut[0] * 1.15f * air;
		float fold = sk[1] * air;

		float jx = AnimKit.wobble(t * 0.9f, 1.3f) * 0.6f * air, jy = AnimKit.wobble(t * 1.1f, 4.1f) * 0.6f * air;
		float bodyY = Mth.lerp(air, 3.6f + Math.abs(Mth.sin(ph)) * 0.4f * amt, 9f - sk[0] * 1.6f + jy);
		float bodyPitch = Mth.lerp(air, -0.15f, st.pitch + 0.1f) - screech * 0.1f;
		Matrix4f bodyM = Rig.mat(jx, bodyY, 0f, 0f, bodyPitch, st.bank + jx * 0.05f);

		Rig.push(pose, bodyM);
		box(pose, BODY, -2.5f, -2.5f, -3.5f, 5f, 5f, 6.5f, false);
		box(pose, CHEST, -2f, -2.6f, 2.6f);
		Matrix4f inv = Blocky.inv(pose);

		// legs (trail behind in the air, tuck under on the ground)
		float[][] ankle = new float[2][];
		for (int sd = -1; sd <= 1; sd += 2) {
			float step = Mth.sin(ph + (sd > 0 ? 0f : Mth.PI)) * 0.4f * amt;
			bone(pose, sd * 1.6f, -1.8f, -3f, sd * 0.2f * air, Mth.lerp(air, 0.5f, 1.35f) + step, -sd * 0.3f);
			box(pose, LEG, -0.5f, -3f, -0.5f, 1f, 3f, 1f, sd < 0);
			ankle[sd < 0 ? 0 : 1] = Blocky.at(pose, inv, 0f, -3f, 0f);
			bone(pose, 0f, -3f, 0f, 0f, Mth.lerp(air, -0.4f, 0.3f), 0f);
			box(pose, FOOT, -1f, -0.5f, -0.2f, 2f, 0.8f, 1.6f, sd < 0);
			pose.popPose();
			pose.popPose();
		}
		// tail membrane between the ankles and the tail tip
		float[] tailTip = { 0f, -1.5f, -7.5f };
		Blocky.panel(pose, MEMBRANE, ankle[0], tailTip, tailTip, ankle[1], 0f, 0.4f);

		// wings
		for (int sd = -1; sd <= 1; sd += 2) {
			pose.pushPose();
			pose.scale(sd, 1f, 1f);
			Matrix4f winv = Blocky.inv(pose);
			float wk = Mth.sin(ph + (sd > 0 ? Mth.PI : 0f)) * amt;
			float shYaw = Mth.lerp(air, 1.15f + wk * 0.3f, 0.05f + fold * 0.45f);
			float shRoll = Mth.lerp(air, -0.75f, lift);
			float shPitch = Mth.lerp(air, 0.3f, -0.05f);
			float elYaw = Mth.lerp(air, -2.45f, -0.35f - fold * 0.7f);
			float elRoll = Mth.lerp(air, -0.3f, (liftOut - lift) * 0.5f);
			float spread = Mth.lerp(air, 0.06f, 0.5f - fold * 0.32f);
			float handYaw = Mth.lerp(air, 2.7f, 0.15f + fold * 0.9f);
			bone(pose, 2.2f, 1.2f, 1.5f, shYaw, shPitch, shRoll);
			float[] sh = Blocky.at(pose, winv, 0f, 0f, 0f);
			box(pose, ARM, 0f, -0.5f, -0.5f);
			bone(pose, 5.8f, 0f, 0f, elYaw, 0f, elRoll);
			float[] el = Blocky.at(pose, winv, 0f, 0f, 0f);
			box(pose, FORE, 0f, -0.45f, -0.45f, 7f, 0.9f, 0.9f, false);
			bone(pose, 7f, 0f, 0f, handYaw, 0f, (liftOut - lift) * 0.3f);
			float[] wr = Blocky.at(pose, winv, 0f, 0f, 0f);
			// thumb claw pointing forward
			bone(pose, 0f, 0.2f, 0.3f, -1.3f + gnd * 0.6f, 0.2f + gnd * 0.7f, 0f);
			box(pose, THUMB, -0.4f, -0.4f, 0f, 0.8f, 0.8f, 1.6f, false);
			pose.popPose();
			float[][] tips = new float[3][];
			for (int k = 0; k < 3; k++) {
				float len = 7.5f - k * 0.8f;
				bone(pose, 0f, 0f, 0f, -0.05f + k * spread, 0f, -0.05f * air);
				box(pose, FINGER, 0f, -0.35f, -0.35f, len * 0.6f, 0.7f, 0.7f, false);
				bone(pose, len * 0.6f, 0f, 0f, 0.12f + fold * 0.35f, 0f, -0.06f * air);
				box(pose, FINGER, 0f, -0.3f, -0.3f, len * 0.45f, 0.6f, 0.6f, false);
				tips[k] = Blocky.at(pose, winv, len * 0.45f, 0f, 0f);
				pose.popPose();
				pose.popPose();
			}
			pose.popPose();
			pose.popPose();
			pose.popPose();
			// membranes: body side to the last finger, scalloped panels between the finger tips, the arm in front
			float[] hip = { 2f, -1.5f, -3.5f };
			Blocky.panel(pose, MEMBRANE, hip, tips[2], wr, sh, 0f, 0.35f);
			Blocky.panel(pose, MEMBRANE, sh, wr, el, sh, 0.35f, 0.5f);
			for (int k = 2; k > 0; k--) {
				float[] mid = BeastMesh.lerp(BeastMesh.lerp(tips[k], tips[k - 1], 0.5f), wr, 0.28f * air);
				float u0 = 0.5f + (2 - k) * 0.25f;
				Blocky.panel(pose, MEMBRANE, tips[k], mid, wr, wr, u0, u0 + 0.12f);
				Blocky.panel(pose, MEMBRANE, mid, tips[k - 1], wr, wr, u0 + 0.12f, u0 + 0.25f);
			}
			pose.popPose();
		}

		// head
		float hy = s.yaw(1.0f) * 0.85f, hp = s.pitch(0.8f);
		float shudder = screech * Mth.sin(t * 3.1f) * 0.06f;
		bone(pose, 0f, 1.2f, 3.4f + screech * 0.8f, hy + shudder, -bodyPitch + hp * 0.6f - screech * 0.25f, Mth.sin(t * 0.05f) * 0.05f * gnd);
		box(pose, HEAD, -2.5f, -2f, -1f);
		box(pose, SNOUT, -1.5f, -2f, 3f);
		float blink = AnimKit.blink(t, 60f, 0.1f);
		if (blink > 0.05f) {
			box(pose, EAR, -2.5f, 2f - 2f * blink, 2.98f, 2f, 2f * blink, 0.15f, false);
			box(pose, EAR, 0.5f, 2f - 2f * blink, 2.98f, 2f, 2f * blink, 0.15f, true);
		}
		box(pose, FANG, -1.1f, -2.7f, 4.2f, 0.6f, 0.9f, 0.6f, false);
		box(pose, FANG, 0.5f, -2.7f, 4.2f, 0.6f, 0.9f, 0.6f, false);
		float open = Math.max(screech * (0.8f + Mth.sin(t * 2.6f) * 0.15f), 0f);
		bone(pose, 0f, -2f, 3f, 0f, open * 0.75f, 0f);
		box(pose, JAW, -1.5f, -1f, 0f);
		pose.popPose();
		// ears: twitch now and then, perk forward on the screech
		for (int sd = -1; sd <= 1; sd += 2) {
			float tw = Math.max(0f, Mth.sin(t * 0.21f + sd)) * Math.max(0f, Mth.sin(t * 0.047f + sd * 2f)) * 0.3f;
			bone(pose, sd * 1.6f, 1.6f, 0.4f, sd * (0.45f - screech * 0.35f), -0.2f - screech * 0.45f + tw, sd * (-0.6f - tw * 0.5f));
			box(pose, EAR, -1f, 0f, -0.5f, 2.2f, 3f, 0.8f, sd < 0);
			pose.popPose();
		}
		pose.popPose();

		pose.popPose(); // body
		pose.popPose(); // scale
	}
}
