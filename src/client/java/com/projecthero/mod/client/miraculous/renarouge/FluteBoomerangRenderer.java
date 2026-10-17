package com.projecthero.mod.client.miraculous.renarouge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.miraculous.renarouge.entity.FluteBoomerangEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** v0.16.6: the thrown Flute, lying flat and spinning end over end round its middle like a boomerang. */
public class FluteBoomerangRenderer extends EntityRenderer<FluteBoomerangEntity> {
	public FluteBoomerangRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.15f;
	}

	@Override
	public void render(FluteBoomerangEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.translate(0f, 0.2f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees((e.tickCount + partialTick) * 48f));
		pose.mulPose(Axis.XP.rotationDegrees(8f));
		pose.mulPose(Axis.ZP.rotationDegrees(90f));
		pose.translate(0f, -1f / 16f, 0f);
		RenaRougeRender.renderFluteModel(pose, buffers, light, 1f);
		pose.popPose();
		super.render(e, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(FluteBoomerangEntity e) {
		return RenaRougeRender.FLUTE;
	}
}
