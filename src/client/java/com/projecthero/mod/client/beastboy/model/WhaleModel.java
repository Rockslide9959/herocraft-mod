package com.projecthero.mod.client.beastboy.model;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's whale, HQ round: a blocky humpback (about 7.5 blocks) in his greens, with a broad back the riders sit on, a flat
 * knobbly head (tubercles), twin blowholes behind a splash guard, a huge hinged lower jaw striped with pale throat grooves, baleen
 * plates that show when the mouth opens, long knobbly flippers with pale undersides, a dorsal hump and wide flukes with a ragged
 * trailing edge. Swims with slow up-and-down strokes through four tail segments (tied to speed), banks heavily into turns, blows a
 * spout when it surfaces; beached it rests, breathes, lifts its flukes now and then.
 * Moves: slam (tail heaves up, mouth gapes, flukes whip down with a splash).
 */
public final class WhaleModel {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/beastboy/art/whale.png");
	/** Drawn a little under its modelled size so riders sit on the back (seats at 0.86 x the 2.2 block height). */
	static final float SCALE = 0.92f;
	private static ModelPart root;
	private static Map<String, ModelPart> P;

	private WhaleModel() {
	}

	private static ModelPart p(String n) {
		return P.get(n);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, int tint, float flash, BeastPose s, SwimRig.State st) {
		if (root == null) {
			root = SeaShapes.whale();
			P = SeaShapes.whaleParts(root);
		}
		SeaArt.reset(root);
		ModelPart spray = p("spray");
		animate(s, st);
		spray.visible = false;
		pose.pushPose();
		pose.scale(SCALE, SCALE, SCALE);
		SeaArt.toModel(pose);
		SeaArt.draw(root, TEX, pose, buffers, light, tint, flash);
		fx(pose, buffers, light, tint, s, st, spray);
		pose.popPose();
	}

	private static float slamLift(BeastPose s) {
		if (!s.is("slam")) {
			return 0f;
		}
		float p = s.prog;
		if (p < 0.45f) {
			return SeaArt.smooth(p / 0.45f);
		}
		if (p < 0.58f) {
			return Mth.lerp(SeaArt.smooth((p - 0.45f) / 0.13f), 1f, -0.55f);
		}
		float k = SeaArt.span(p, 0.58f, 1f);
		return -0.55f * (1f - SeaArt.smooth(k)) + Mth.sin(k * Mth.PI * 2f) * 0.08f * (1f - k);
	}

	static void animate(BeastPose s, SwimRig.State st) {
		float t = st.time;
		float wet = st.wetness, dry = 1f - wet;
		float spd = st.speed01();
		ModelPart body = p("body"), head = p("head"), jaw = p("jaw");
		ModelPart t1 = p("tail1"), t2 = p("tail2"), t3 = p("tail3"), fk = p("fluke");

		float phase = t * 1.9f + st.dist * 1.1f;
		float amp = Mth.lerp(spd, 0.06f, 0.16f);
		// beached: slow breathing, the flukes lift and flop now and then
		float lift = 0f;
		if (dry > 0.01f) {
			float ft = (t % 5.5f) / 5.5f;
			lift = ft < 0.4f ? Mth.sin(ft / 0.4f * Mth.PI) : 0f;
			amp = Mth.lerp(dry, amp, 0.03f);
			phase = Mth.lerp(dry, phase, t * 1.2f);
		}
		float slam = slamLift(s);
		float s1 = Mth.sin(phase - 0.6f), s2 = Mth.sin(phase - 1.2f), s3 = Mth.sin(phase - 1.8f), s4 = Mth.sin(phase - 2.4f);
		float up = Math.max(slam, 0f), down = Math.min(slam, 0f);
		t1.xRot = amp * 0.35f * s1 + slam * 0.22f + lift * 0.08f;
		t2.xRot = amp * 0.6f * s2 + slam * 0.32f + lift * 0.12f;
		t3.xRot = amp * 0.85f * s3 + slam * 0.36f + lift * 0.18f;
		fk.xRot = amp * 1.15f * s4 + up * 0.3f + down * 0.5f + lift * 0.25f;

		body.xRot = st.pitch * 0.6f * wet - amp * 0.12f * Mth.sin(phase) + up * 0.12f + down * 0.05f;
		body.y -= amp * 4f * Mth.sin(phase + 0.4f) * wet;
		float breathe = Mth.sin(t * (dry > 0.5f ? 1.1f : 0.8f));
		body.xScale = 1f + breathe * 0.012f;
		body.yScale = 1f + breathe * (0.012f + dry * 0.01f);

		// turning: heavy bank, the tail swings round
		float bend = Mth.clamp(st.turn * 0.16f, -0.22f, 0.22f) * wet;
		body.zRot = Mth.clamp(st.turn * 0.14f, -0.35f, 0.35f) * wet;
		body.yRot = -s.yaw(0.5f) * 0.12f * wet;
		t1.yRot = bend * 0.4f;
		t2.yRot = bend * 0.5f;
		t3.yRot = bend * 0.5f;
		fk.yRot = bend * 0.3f;
		head.xRot = s.pitch(0.4f) * 0.1f * wet;
		head.yRot = -bend * 0.25f;

		// flippers: a slow glide-stroke; steer into turns; spread on the sand when beached
		float fl = Mth.sin(phase * 0.6f) * 0.12f * wet;
		p("flipperL").zRot = 0.18f + fl - bend * 0.8f + dry * 0.12f;
		p("flipperR").zRot = -0.18f - fl - bend * 0.8f - dry * 0.12f;
		p("flipperL").yRot = -0.1f * spd;
		p("flipperR").yRot = 0.1f * spd;
		p("flipperTipL").zRot = 0.1f + Mth.sin(phase * 0.6f - 0.7f) * 0.1f * wet + dry * 0.1f;
		p("flipperTipR").zRot = -0.1f - Mth.sin(phase * 0.6f - 0.7f) * 0.1f * wet - dry * 0.1f;
		if (s.is("slam")) {
			p("flipperL").zRot -= up * 0.35f;
			p("flipperR").zRot += up * 0.35f;
		}

		// the mouth: shut, a slow gape now and then, wide on the slam (showing the baleen)
		float gper = 9f, gt = (t % gper) / gper;
		float gape = gt < 0.12f ? Mth.sin(gt / 0.12f * Mth.PI) * 0.12f : 0f;
		jaw.xRot = 0.01f + gape + up * 0.32f;

		float blink = SwimRig.blink(t * 0.8f, 2);
		for (String sd : new String[] { "L", "R" }) {
			ModelPart lid = p("lid" + sd);
			lid.yScale = Math.max(0.01f, blink);
			lid.visible = blink > 0.02f;
		}
	}

	/** Spout from the blowholes (surfaced or beached) and the slam's splash. */
	private static void fx(PoseStack pose, MultiBufferSource buffers, int light, int tint, BeastPose s, SwimRig.State st, ModelPart spray) {
		float t = st.time;
		boolean blow = st.surfaced || (!st.wet && st.ground);
		float per = 7f, bt = (t % per);
		if (blow && bt < 1.8f) {
			float k = bt / 1.8f;
			pose.pushPose();
			p("body").translateAndRotate(pose);
			p("head").translateAndRotate(pose);
			for (int i = 0; i < 10; i++) {
				float age = k * 1.8f - i * 0.07f;
				if (age <= 0f || age > 1.1f) {
					continue;
				}
				float h = age * 26f;
				float spread = age * age * 7f;
				for (int sd = -1; sd <= 1; sd += 2) {
					float x = sd * (1.5f + spread * (0.6f + (i % 3) * 0.2f));
					SeaArt.puff(spray, WhaleModel.TEX, pose, buffers, light, x, -13f - h, -7f + Mth.sin(i * 1.7f) * spread * 0.5f,
							0.5f + age * 0.9f, i * 0.9f + t, (1f - age / 1.1f) * 0.75f, tint);
				}
			}
			pose.popPose();
		}
		if (s.is("slam") && s.prog > 0.52f) {
			float k = SeaArt.span(s.prog, 0.52f, 1f);
			for (int i = 0; i < 14; i++) {
				float a = i / 14f * Mth.TWO_PI;
				float r = 10f + k * 34f;
				float hop = Mth.sin(k * Mth.PI) * (6f + (i % 3) * 4f);
				SeaArt.puff(spray, WhaleModel.TEX, pose, buffers, light, Mth.sin(a) * r, 22f - hop, 66f + Mth.cos(a) * r * 0.7f, 1.2f + k * 1.4f, a + t,
						(1f - k) * 0.7f, tint);
			}
		}
	}
}
