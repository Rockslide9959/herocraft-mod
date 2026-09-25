package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.data.ClawTier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;

/** First-person entry point for the claws; owns one lazily-baked model instance. */
public final class WolverineClawsRenderer {
	private static WolverineClawsModel adamantium;
	private static WolverineClawsModel bone;

	private WolverineClawsRenderer() {
	}

	private static WolverineClawsModel model(ClawTier tier) {
		if (tier == ClawTier.BONE) {
			if (bone == null) {
				bone = new WolverineClawsModel(Minecraft.getInstance().getEntityModels().bakeLayer(WolverineClawsModel.BONE_LAYER));
			}
			return bone;
		}
		if (adamantium == null) {
			adamantium = new WolverineClawsModel(Minecraft.getInstance().getEntityModels().bakeLayer(WolverineClawsModel.LAYER));
		}
		return adamantium;
	}

	/** Called with the pose stack at the base of the first-person arm render; {@code arm} supplies the bone transform. */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, boolean rightArm, ModelPart arm) {
		float ext = WolverineClawsModel.extension(player, 1.0f);
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		int color = Wolverine.raging(player) ? 0xFFFFC4C4 : 0xFFFFFFFF;
		ClawTier tier = Wolverine.clawTier(player);
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(WolverineClawsModel.textureFor(tier)));
		pose.pushPose();
		arm.translateAndRotate(pose);
		model(tier).render(pose, buffer, light, OverlayTexture.NO_OVERLAY, rightArm, slim, ext, color);
		pose.popPose();
	}
}
