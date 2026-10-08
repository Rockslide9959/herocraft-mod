package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.maxsteel.TurboDraw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;

/**
 * v0.15.15 playtest (user: "the glow is supposed to warp around the players body like an outer layer of the players
 * body, make it really transparent"): while in Ring Flight, a Green Lantern wears a thin skin of green light -- the
 * player's own head, body, arms and legs redrawn a little inflated, following the live pose (flight lean, limb swing,
 * every GL move pose), in additive green at a very low alpha with a slow pulse, plus a second, fainter shell a little
 * further out. Drawn from {@code PowerRingLayer} (a player render layer, so everyone sees it); not drawn for yourself
 * in first person. Replaces the old ellipsoid aura.
 */
public final class GreenLanternFlightGlow {
	/** Inflation of the inner / outer shells, px per side (outside the GL suit's 0.55 px outer layer). */
	private static final float INNER = 0.8f;
	private static final float OUTER = 1.7f;

	private GreenLanternFlightGlow() {
	}

	/** {@code strength} 0..1 (eased in / out by {@link GreenLanternFlightFx}). */
	public static void render(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
			float ageInTicks) {
		if (player.isInvisible()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		float k = GreenLanternFlightFx.auraStrength(player, mc.getTimer().getGameTimeDeltaPartialTick(false));
		if (k <= 0.01f) {
			return;
		}
		float pulse = 0.5f + 0.5f * Mth.sin(ageInTicks * 0.15f);
		float boost = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BOOSTING, false) || player.isSprinting() ? 1f : 0f;
		float a1 = k * (0.1f + 0.04f * pulse) * (1f + 0.25f * boost);
		float a2 = k * (0.045f + 0.025f * pulse);
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		VertexConsumer add = HardLightRibbon.additive(buffers);
		for (int shell = 0; shell < 2; shell++) {
			float g = shell == 0 ? INNER : OUTER + 0.25f * pulse;
			float a = shell == 0 ? a1 : a2;
			int rgb = shell == 0 ? 0x5CFF8E : 0x35F075;
			part(add, pose, model.head, -4, -8, -4, 4, 0, 4, g, rgb, a);
			part(add, pose, model.body, -4, 0, -2, 4, 12, 2, g, rgb, a);
			part(add, pose, model.rightArm, slim ? -2 : -3, -2, -2, 1, 10, 2, g, rgb, a);
			part(add, pose, model.leftArm, -1, -2, -2, slim ? 2 : 3, 10, 2, g, rgb, a);
			part(add, pose, model.rightLeg, -2, 0, -2, 2, 12, 2, g, rgb, a);
			part(add, pose, model.leftLeg, -2, 0, -2, 2, 12, 2, g, rgb, a);
		}
	}

	/** One inflated box (px extents in the part's space) round a posed model part. */
	private static void part(VertexConsumer vc, PoseStack pose, ModelPart p, float x0, float y0, float z0, float x1, float y1, float z1,
			float grow, int rgb, float alpha) {
		if (!p.visible) {
			return;
		}
		pose.pushPose();
		p.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		pose.translate((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f);
		TurboDraw.box(vc, pose.last(), (x1 - x0) / 2f + grow, (y1 - y0) / 2f + grow, (z1 - z0) / 2f + grow, rgb, alpha);
		pose.popPose();
	}
}
