package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.Blocky.bone;
import static com.projecthero.mod.client.beastboy.model.Blocky.box;
import static com.projecthero.mod.client.beastboy.model.SnakeUv.*;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's snake, HQ round (moved out of SmallModels): a chain of small scaled boxes on {@code snake.png} ({@link SnakeUv})
 * placed at even spacing along a smooth path, so the body bends smoothly while staying blocky: saddle diamonds down the back, pale
 * belly scutes, tapering to a pointed tail. A blocky head with brow scales over slit-pupil eyes, nostrils, a dropping jaw, fangs that
 * fold back when the mouth shuts and swing out when it opens, and a forked tongue that flicks.
 *
 * <p>At rest it lies coiled with the neck raised in an S, swaying and tasting the air; moving, it slithers in a lateral wave that stays
 * put on the ground as the body passes through it. Moves: strike / bite (the S draws back, then the head shoots out with the jaw
 * wide). Drawn alone (the SNAKE form) and by the Snake Strike quick morph.
 */
public final class SnakeModel {
	private static final float K = 0.5f;
	private static final int SEGS = 19;
	private static final float SPACING = 2.4f;

	private SnakeModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		BeastMesh.texture(TEX);
		MotionState st = MotionState.of(s, 5);
		pose.pushPose();
		Blocky.scale(pose, K);
		float t = s.age;
		boolean striking = s.isAny("strike", "bite");
		float[] T = { 0f, 0.25f, 0.45f, 0.7f, 1f };
		float lunge = striking ? AnimKit.key(s.prog, T, new float[] { 0f, -0.35f, 1f, 0.85f, 0f }) : 0f;
		float jaw = striking ? AnimKit.key(s.prog, T, new float[] { 0f, 0.55f, 1f, 0.5f, 0f }) : 0f;
		st.speed = st.ease(st.speed, Mth.clamp(s.walkAmt * 1.6f, 0f, 1f), 0.12f);
		float slither = st.speed;
		float wave = s.walkPos * 0.85f;

		// the path: tail tip -> neck base on the ground (coil blended into a trailing wave), then the raised neck
		List<float[]> path = new ArrayList<>();
		for (int i = 0; i <= 48; i++) {
			float k = i / 48f;
			float ang = (1f - k) * 9.2f + 0.5f, rad = 2.4f + k * 5.4f;
			float[] coil = { Mth.sin(ang) * rad, 0f, -3.4f + Mth.cos(ang) * rad };
			float zb = -3f - (1f - k) * 34f;
			float amp = 3.4f * (0.3f + (1f - k) * 0.7f);
			float[] sl = { Mth.sin(zb * 0.32f + wave) * amp, 0f, zb };
			path.add(BeastMesh.lerp(coil, sl, slither));
		}
		float rise = 1f - slither * 0.5f;
		float sway = Mth.sin(t * 0.07f) * 1.1f * (1f - Math.abs(lunge)) + Mth.sin(t * 0.031f) * 0.5f;
		float back = Math.min(0f, lunge), out = Math.max(0f, lunge);
		float[][] neck = { path.get(path.size() - 1), { sway * 0.4f, 0.2f, 1.2f + out * 4f + back * 1f },
				{ sway * 0.9f, 3.4f * rise + back * -1.5f - out * 1.2f, 3.2f + out * 9f + back * 2f },
				{ sway * 1.3f, 7.4f * rise - back * 2.5f - out * 2.6f, 4.4f + out * 15f + back * 3.5f },
				{ sway * 1.6f, 8.8f * rise - back * 2.6f - out * 3.4f, 7.2f + out * 21f + back * 3.8f } };
		for (int i = 0; i < neck.length - 1; i++) {
			float[] p0 = neck[Math.max(0, i - 1)], p1 = neck[i], p2 = neck[i + 1], p3 = neck[Math.min(neck.length - 1, i + 2)];
			for (int j = 1; j <= 8; j++) {
				path.add(cr(p0, p1, p2, p3, j / 8f));
			}
		}
		// segments at even spacing back from the head
		float[] cum = new float[path.size()];
		for (int i = 1; i < path.size(); i++) {
			cum[i] = cum[i - 1] + BeastMesh.len(BeastMesh.sub(path.get(i), path.get(i - 1)));
		}
		float total = cum[cum.length - 1];
		float[] head = path.get(path.size() - 1);
		float[] hdir = BeastMesh.norm(BeastMesh.sub(head, at(path, cum, total - 1.5f)), new float[] { 0f, 0f, 1f });
		for (int n = SEGS; n >= 0; n--) {
			float d = total - 2.2f - n * SPACING;
			if (d < 0f) {
				continue;
			}
			float[] c = at(path, cum, d);
			float[] a = at(path, cum, Math.min(total, d + 1f)), b = at(path, cum, Math.max(0f, d - 1f));
			float[] dir = BeastMesh.sub(a, b);
			float k = n / (float) SEGS; // 0 at the neck, 1 at the tail
			float sc = k < 0.15f ? Mth.lerp(k / 0.15f, 0.82f, 1f) : Mth.lerp((k - 0.15f) / 0.85f, 1f, 0.32f);
			float breathe = n > 2 && n < 9 ? 1f + Mth.sin(t * 0.12f) * 0.03f : 1f;
			float yaw = (float) Math.atan2(dir[0], dir[2]);
			float pitch = (float) -Math.atan2(dir[1], Mth.sqrt(dir[0] * dir[0] + dir[2] * dir[2]));
			bone(pose, c[0], c[1] + 1.5f * sc, c[2], yaw, pitch, 0f);
			int[] part = n < 2 ? NECK : (n % 2 == 0 ? SEG_A : SEG_B);
			float len = Math.max(4f * sc, SPACING + 0.9f);
			box(pose, part, -2f * sc * breathe, -1.5f * sc, -len * 0.5f, 4f * sc * breathe, 3f * sc, len, false);
			pose.popPose();
		}
		// pointed tail tip
		{
			float d = Math.max(0f, total - 2.2f - SEGS * SPACING - 2f);
			float[] c = at(path, cum, d), a = at(path, cum, d + 1f);
			float[] dir = BeastMesh.sub(a, c);
			bone(pose, c[0], c[1] + 0.5f, c[2], (float) Math.atan2(dir[0], dir[2]), 0f, 0f);
			box(pose, TAIL_TIP, -0.6f, -0.5f, -2.8f, 1.2f, 1f, 3.6f, false);
			pose.popPose();
		}

		// head: follows the neck, then the look
		float hy = (float) Math.atan2(hdir[0], hdir[2]) + s.yaw(0.7f) * 0.5f;
		float hp = 0.08f + out * 0.12f + s.pitch(0.6f) * 0.35f;
		bone(pose, head[0], head[1] + 1.4f, head[2], hy, hp, sway * 0.03f);
		pose.scale(0.82f, 0.82f, 0.82f);
		head(pose, t, jaw);
		pose.popPose();

		pose.popPose(); // scale
	}

	/** The head (pivot at the back-centre of the skull, +Z forward, model units), jaw 0..1 open. */
	static void head(PoseStack pose, float t, float jaw) {
		box(pose, HEAD, -2.5f, -1.5f, -2f);
		box(pose, SNOUT, -1.5f, -1.5f, 3f);
		box(pose, BROW, 1.7f, 1.3f, -0.2f);
		box(pose, BROW, -2.7f, 1.3f, -0.2f, 1f, 1f, 3f, true);
		// fangs: folded back along the roof when shut, swung down and forward as the jaw opens
		for (int sd = -1; sd <= 1; sd += 2) {
			bone(pose, sd * 1.0f, -1.5f, 4.8f, 0f, Mth.lerp(Mth.clamp(jaw * 1.6f, 0f, 1f), -1.35f, 0.15f), 0f);
			box(pose, FANG, -0.4f, -1.8f, -0.4f, 0.8f, 1.8f, 0.8f, false);
			pose.popPose();
		}
		bone(pose, 0f, -1.5f, -1.5f, 0f, jaw * 0.95f, 0f);
		box(pose, JAW, -2f, -1f, 0f, 4f, 1f, 7.2f, false);
		pose.popPose();
		// forked tongue: flicks out, wags, pulls back (always out while striking)
		float cyc = (t * 0.12f) % 1f;
		float flick = jaw > 0.3f ? 1f : (cyc < 0.35f ? Mth.sin(cyc / 0.35f * Mth.PI) : 0f);
		if (flick > 0.05f) {
			float wag = Mth.sin(t * 2.4f) * 0.35f;
			bone(pose, 0f, -1.4f + jaw * -0.8f, 4.6f, 0f, wag, 0f);
			Blocky.plane(pose, TONGUE, -0.9f, 0f, 0f, 1.8f, 5f * flick, false);
			pose.popPose();
		}
	}

	private static float[] at(List<float[]> path, float[] cum, float d) {
		int lo = 0, hi = cum.length - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (cum[mid] < d) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		float seg = cum[hi] - cum[lo];
		return BeastMesh.lerp(path.get(lo), path.get(hi), seg < 1.0e-5f ? 0f : Mth.clamp((d - cum[lo]) / seg, 0f, 1f));
	}

	private static float[] cr(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
		float t2 = t * t, t3 = t2 * t;
		float[] o = new float[3];
		for (int i = 0; i < 3; i++) {
			o[i] = 0.5f * (2f * p1[i] + (-p0[i] + p2[i]) * t + (2f * p0[i] - 5f * p1[i] + 4f * p2[i] - p3[i]) * t2 + (-p0[i] + 3f * p1[i] - 3f * p2[i] + p3[i]) * t3);
		}
		return o;
	}
}
