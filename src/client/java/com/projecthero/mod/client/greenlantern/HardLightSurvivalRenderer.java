package com.projecthero.mod.client.greenlantern;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.greenlantern.GreenLanternSurvival;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;

import net.minecraft.util.Mth;

/**
 * v0.17.1: the models of the survival constructs' moving parts ({@code GreenLanternSurvival}) -- the Item Net's sweep and
 * the Tunneller's drill-headed pickaxe -- in the same hard-light boxes as the rest. (The chest and the scaffold tower are
 * blocks with their own models.) The pose arrives at the entity's position.
 */
final class HardLightSurvivalRenderer {
	private HardLightSurvivalRenderer() {
	}

	static void render(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		switch (e.shape()) {
			case ITEM_NET -> net(e, vc, pose, age, alpha);
			case TUNNELLER -> tunneller(e, vc, pose, age, alpha, partial);
			default -> {
			}
		}
	}

	private static void p(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, float a) {
		HardLightDraw.part(vc, pose, x, y, z, hx, hy, hz, a);
	}

	// ---- Item Net: a shallow dome of netting flung out round the Lantern, then hauled back in

	private static void net(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		float r = (float) GreenLanternSurvival.netReach(e.scale(), age, e.life());
		if (r < 0.3f) {
			return;
		}
		float spin = age * 1.5f;
		pose.mulPose(Axis.YP.rotationDegrees(spin));
		float rise = 1.4f;
		// the rings: the rim brightest, the inner ones fainter
		for (int k = 1; k <= 3; k++) {
			float rk = r * k / 3f;
			float yk = 0.15f + rise * (1f - k / 3f);
			int segs = 12 + k * 8;
			float a = k == 3 ? alpha : alpha * 0.6f;
			for (int i = 0; i < segs; i++) {
				pose.pushPose();
				pose.mulPose(Axis.YP.rotationDegrees(i * 360f / segs));
				p(vc, pose, rk, yk, 0f, 0.035f, 0.035f, (float) (Math.PI * rk / segs) + 0.02f, a);
				pose.popPose();
			}
		}
		// the spokes, from a knot at the top down to the rim
		for (int k = 0; k < 12; k++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(k * 30f));
			float dx = r;
			float dy = -rise;
			float len = Mth.sqrt(dx * dx + dy * dy);
			pose.translate(r / 2f, 0.15f + rise / 2f, 0f);
			pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
			p(vc, pose, 0f, 0f, 0f, len / 2f, 0.03f, 0.03f, alpha * 0.75f);
			pose.popPose();
		}
		HardLightDraw.glow(vc, pose, 0f, 0.15f + rise, 0f, 0.12f, 0.12f, 0.12f, alpha);
		// weights round the rim
		for (int i = 0; i < 8; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 45f + 22.5f));
			HardLightDraw.glow(vc, pose, r, 0.15f, 0f, 0.07f, 0.07f, 0.07f, alpha);
			pose.popPose();
		}
	}

	// ---- Tunneller: a giant pickaxe whose head spins into a drill, boring forward

	private static void tunneller(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getViewYRot(partial)));
		pose.mulPose(Axis.XP.rotationDegrees(e.getViewXRot(partial)));
		float grow = Math.min(1f, age / 4f);
		pose.scale(grow, grow, grow);
		// the haft trails back toward the Lantern
		p(vc, pose, 0f, 0f, -2.2f, 0.11f, 0.11f, 1.5f, alpha);
		p(vc, pose, 0f, 0f, -3.75f, 0.16f, 0.16f, 0.12f, alpha);
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(age * 32f));
		// the pick head: a crossbar with two forward-hooked points -- spun, it carves the full 3x3
		p(vc, pose, 0f, 0f, -0.55f, 1.35f, 0.16f, 0.16f, alpha);
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(s * 1.3f, 0f, -0.3f);
			pose.mulPose(Axis.YP.rotationDegrees(s * -35f));
			p(vc, pose, 0f, 0f, 0.25f, 0.1f, 0.12f, 0.38f, alpha);
			pose.popPose();
		}
		// the drill cone: stacked, twisting plates narrowing to a point
		for (int i = 0; i < 6; i++) {
			float h = 1.25f * (1f - i / 6.5f);
			pose.pushPose();
			pose.translate(0f, 0f, -0.25f + i * 0.2f);
			pose.mulPose(Axis.ZP.rotationDegrees(i * 15f));
			p(vc, pose, 0f, 0f, 0f, h, h * 0.22f, 0.08f, alpha * 0.85f);
			p(vc, pose, 0f, 0f, 0f, h * 0.22f, h, 0.08f, alpha * 0.85f);
			pose.popPose();
		}
		pose.popPose();
		HardLightDraw.glow(vc, pose, 0f, 0f, 1.05f, 0.1f, 0.1f, 0.14f, alpha);
		// a flash of light at the face each time a slice gives way
		float k = (age - e.actionTick()) / 3f;
		if (k >= 0f && k < 1f) {
			HardLightDraw.glow(vc, pose, 0f, 0f, 0.6f, 1.5f * (0.6f + k * 0.4f), 1.5f * (0.6f + k * 0.4f), 0.03f, (1f - k) * alpha * 0.6f);
		}
	}
}
