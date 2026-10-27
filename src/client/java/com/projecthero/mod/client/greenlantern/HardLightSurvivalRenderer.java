package com.projecthero.mod.client.greenlantern;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.greenlantern.GreenLanternSurvival;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;

import net.minecraft.util.Mth;

/**
 * v0.17.1: the models of the survival constructs' moving parts ({@code GreenLanternSurvival}) -- the Item Net's sweep and
 * the Tunneller's drill car ({@link HardLightTunnellerModel}) -- in the same hard-light boxes as the rest. (The chest and the scaffold tower are
 * blocks with their own models.) The pose arrives at the entity's position.
 */
final class HardLightSurvivalRenderer {
	private HardLightSurvivalRenderer() {
	}

	static void render(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		switch (e.shape()) {
			case ITEM_NET -> net(e, vc, pose, age, alpha);
			case TUNNELLER -> HardLightTunnellerModel.render(e, vc, pose, age, alpha, partial); // the drill car
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
}
