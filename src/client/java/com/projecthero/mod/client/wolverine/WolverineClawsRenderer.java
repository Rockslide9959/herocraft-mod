package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.wolverine.Wolverine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;

/** First-person entry point for the claws (v0.16.1: the smooth mesh blades + their slash trail). */
public final class WolverineClawsRenderer {
	private WolverineClawsRenderer() {
	}

	/** Called with the pose stack at the base of the first-person arm render; {@code arm} supplies the bone transform. */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, boolean rightArm, ModelPart arm) {
		float ext = WolverineClawsModel.extension(player, 1.0f);
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		int color = Wolverine.raging(player) ? WolverineClawsLayer.RAGE_TINT : WolverineClawsLayer.SILVER;
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(WolverineClawsModel.TEXTURE));
		pose.pushPose();
		arm.translateAndRotate(pose);
		WolverineClawsModel.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, rightArm, slim, ext,
				WolverineClawsModel.FIRST_PERSON_LENGTH, color);
		org.joml.Matrix4f m = new org.joml.Matrix4f(pose.last().pose());
		pose.popPose();
		// the trail is drawn last: asking for its (additive) buffer closes the claws' one
		WolverineClawFx.firstPerson(player, rightArm, m, slim, ext, buffers);
	}
}
