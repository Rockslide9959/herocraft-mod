package com.projecthero.mod.client.spin.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 the Spin -- Ball Breaker, <b>Candidate B</b> (PREVIEW ONLY, not wired into the Ball Breaker move): the PlanetMinecraft skin
 * "Ball Breaker" by Kiryutojo (https://www.planetminecraft.com/skin/ball-breaker-6348233/, Alex/slim arms) on a normal slim player model,
 * in the same floating crouch as {@link BallBreakerModel}. The skin carries no stated licence: ask the author before a public release.
 *
 * <p>Same space as {@link BallBreakerModel}: +Y up, +Z front, origin at the soles of a standing figure, 1 unit = 1 block (a player-sized
 * 2-block figure; scale it to taste).
 */
public final class BallBreakerSkinFigure {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/spin/power/ball_breaker_pmc.png");
	private static PlayerModel<AbstractClientPlayer> model;

	private BallBreakerSkinFigure() {
	}

	/** Same parameters as {@link BallBreakerModel#render}. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, float alpha, float glow, float strike, float time) {
		if (alpha <= 0.003f) {
			return;
		}
		if (model == null) {
			model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
		}
		pose(strike, time);
		float bob = Mth.sin(time * 0.12f) * 0.6f / 16f;
		pose.pushPose();
		// PlayerModel space is y-down with the origin 24 px above the soles; flip into ours (y-up, front +Z)
		// (the player model's face is on its -Z side, as in LivingEntityRenderer's 180 - yaw turn)
		pose.translate(0f, bob, 0f);
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		RenderType rt = alpha >= 0.999f ? RenderType.entityCutoutNoCull(TEX) : RenderType.entityTranslucent(TEX);
		model.renderToBuffer(pose, buffers.getBuffer(rt), light, OverlayTexture.NO_OVERLAY, (a << 24) | 0xFFFFFF);
		if (glow > 0.003f) {
			float k = glow * alpha;
			int c = (0xFF << 24) | ((int) (255 * 0.5f * k) << 16) | ((int) (205 * 0.5f * k) << 8) | (int) (70 * 0.5f * k);
			model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(TEX)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c);
		}
		pose.popPose();
	}

	/** The floating crouch: leaning in, arms spread forward with bent elbows (no elbows on a player model: straighter), legs drawn up. */
	private static void pose(float strike, float time) {
		PlayerModel<AbstractClientPlayer> f = model;
		f.head.setRotation(0, 0, 0);
		f.body.setRotation(0, 0, 0);
		f.rightArm.setRotation(0, 0, 0);
		f.leftArm.setRotation(0, 0, 0);
		f.rightLeg.setRotation(0, 0, 0);
		f.leftLeg.setRotation(0, 0, 0);
		f.body.xRot = 0.3f;
		f.head.xRot = -0.05f;
		f.head.z = -1.6f;
		f.head.y = 0.6f;
		f.rightArm.z = -1.2f;
		f.leftArm.z = -1.2f;
		f.rightArm.y = 2.4f;
		f.leftArm.y = 2.4f;
		// arms (player-model x is mirrored: rightArm is at -x, a negative xRot raises it forward)
		f.leftArm.xRot = -0.8f;
		f.leftArm.zRot = -1.25f;
		f.rightArm.xRot = Mth.lerp(strike, -0.9f, -1.6f);
		f.rightArm.zRot = Mth.lerp(strike, 1.15f, 0.1f);
		// legs drawn up under the lean, right more than left
		f.rightLeg.xRot = -1.2f;
		f.rightLeg.zRot = 0.12f;
		f.rightLeg.z = 1.6f;
		f.leftLeg.xRot = -0.2f;
		f.leftLeg.zRot = -0.08f;
		f.leftLeg.z = 1.4f;
		f.hat.copyFrom(f.head);
		f.jacket.copyFrom(f.body);
		f.rightSleeve.copyFrom(f.rightArm);
		f.leftSleeve.copyFrom(f.leftArm);
		f.rightPants.copyFrom(f.rightLeg);
		f.leftPants.copyFrom(f.leftLeg);
	}
}
