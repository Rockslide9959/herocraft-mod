package com.projecthero.mod.client.beastboy.model;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's dolphin, HQ round (was the tinted vanilla dolphin): a blocky bottlenose in his greens -- darker cape, light flanks,
 * pale belly -- with the rounded melon, a short beak over a hinged lower jaw with little teeth, a blowhole, a hooked dorsal fin, flippers
 * and broad flukes. Swims with up-and-down fluke strokes running down the tail (tied to swim speed), banks into turns and follows its
 * climb / dive; chatters now and then; out of water it rests on its belly and flicks its tail.
 */
public final class DolphinModel {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/beastboy/art/dolphin.png");
	private static ModelPart root;
	private static Map<String, ModelPart> P;

	private DolphinModel() {
	}

	private static ModelPart p(String n) {
		return P.get(n);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, int tint, float flash, BeastPose s, SwimRig.State st) {
		if (root == null) {
			root = SeaShapes.dolphin();
			P = SeaShapes.dolphinParts(root);
		}
		SeaArt.reset(root);
		animate(s, st);
		pose.pushPose();
		SeaArt.toModel(pose);
		SeaArt.draw(root, TEX, pose, buffers, light, tint, flash);
		pose.popPose();
	}

	static void animate(BeastPose s, SwimRig.State st) {
		float t = st.time;
		float wet = st.wetness, dry = 1f - wet;
		float spd = st.speed01();
		boolean air = !st.wet && !st.ground;
		ModelPart body = p("body"), head = p("head"), jaw = p("jaw"), t1 = p("tail1"), t2 = p("tail2"), fl = p("flukes");

		// ---- vertical fluke strokes
		float phase = t * 4.2f + st.dist * 2.6f;
		float amp = Mth.lerp(spd, 0.1f, 0.3f);
		// on land: rests, flicks its tail every couple of seconds
		float flick = 0f;
		if (dry > 0.01f && !air) {
			float ft = (t % 2.2f) / 2.2f;
			flick = ft < 0.35f ? Mth.sin(ft / 0.35f * Mth.PI) : 0f;
			amp = Mth.lerp(dry, amp, 0.05f + flick * 0.4f);
			phase = Mth.lerp(dry, phase, t * 11f);
		}
		float s1 = Mth.sin(phase - 0.8f), s2 = Mth.sin(phase - 1.6f), s3 = Mth.sin(phase - 2.4f);
		// +xRot lifts a tail part's far end
		t1.xRot = amp * 0.5f * s1;
		t2.xRot = amp * 0.8f * s2;
		fl.xRot = amp * 1.1f * s3;
		float pitchSwim = (air ? st.pitch : st.pitch * wet);
		body.xRot = pitchSwim * 0.9f - amp * 0.18f * Mth.sin(phase) + s.pitch(0.5f) * 0.2f * wet;
		body.y -= amp * 1.2f * Mth.sin(phase + 0.5f) * wet;
		body.y += dry * (air ? 0f : 0.5f);

		// turning: bank and bend
		float bend = Mth.clamp(st.turn * 0.2f, -0.3f, 0.3f) * wet;
		body.zRot = Mth.clamp(st.turn * 0.22f, -0.6f, 0.6f) * wet + dry * (air ? 0f : 0.12f);
		t1.yRot = bend * 0.5f;
		t2.yRot = bend * 0.6f;
		fl.yRot = bend * 0.3f;
		head.yRot = -bend * 0.6f - s.yaw(0.5f) * 0.35f;
		head.xRot = s.pitch(0.4f) * 0.25f * wet - dry * flick * 0.2f;

		// flippers trim and steer
		ModelPart fL = p("finL"), fR = p("finR");
		float trim = Mth.sin(phase * 0.5f) * 0.08f;
		fL.zRot = 0.15f + trim - bend * 0.7f - dry * 0.1f;
		fR.zRot = -0.15f - trim - bend * 0.7f + dry * 0.1f;
		fL.yRot = -0.15f * spd;
		fR.yRot = 0.15f * spd;
		p("dorsal").zRot = -bend * 0.4f;

		// chatter: quick jaw clicks every few seconds (more often out of water), a slow breath otherwise
		float cper = dry > 0.5f ? 3.2f : 6.5f;
		float ct = (t % cper) / cper;
		float chatter = ct < 0.14f ? Math.abs(Mth.sin(ct / 0.14f * Mth.PI * 3f)) : 0f;
		jaw.xRot = 0.02f + chatter * 0.42f;

		float blink = SwimRig.blink(t, 1);
		for (String sd : new String[] { "L", "R" }) {
			ModelPart lid = p("lid" + sd);
			lid.yScale = Math.max(0.01f, blink);
			lid.visible = blink > 0.02f;
		}
	}
}
