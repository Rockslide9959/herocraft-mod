package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.wolverine.Wolverine;

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
	private static WolverineClawsModel model;

	private WolverineClawsRenderer() {
	}

	private static WolverineClawsModel model() {
		if (model == null) {
			model = new WolverineClawsModel(Minecraft.getInstance().getEntityModels().bakeLayer(WolverineClawsModel.LAYER));
		}
		return model;
	}

	/** Called with the pose stack at the base of the first-person arm render; {@code arm} supplies the bone transform. */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, boolean rightArm, ModelPart arm) {
		float ext = WolverineClawsModel.extension(player, 1.0f);
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		int color = Wolverine.raging(player) ? 0xFFFFC4C4 : 0xFFFFFFFF;
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(WolverineClawsModel.TEXTURE));
		pose.pushPose();
		arm.translateAndRotate(pose);
		model().render(pose, buffer, light, OverlayTexture.NO_OVERLAY, rightArm, slim, ext, color);
		pose.popPose();
	}
}
