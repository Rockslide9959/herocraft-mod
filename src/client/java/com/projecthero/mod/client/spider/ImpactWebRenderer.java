package com.projecthero.mod.client.spider;

import com.projecthero.mod.spider.entity.ImpactWebEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws {@link ImpactWebModel}, tumbling as it flies. */
public class ImpactWebRenderer extends EntityRenderer<ImpactWebEntity> {
	private static final float SCALE = 0.75f;

	private final ImpactWebModel model;

	public ImpactWebRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new ImpactWebModel(context.bakeLayer(ImpactWebModel.LAYER));
	}

	@Override
	public void render(ImpactWebEntity entity, float yaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int light) {
		float age = entity.tickCount + partialTicks;
		pose.pushPose();
		pose.translate(0.0, entity.getBbHeight() * 0.5, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(age * 28.0f));
		pose.mulPose(Axis.XP.rotationDegrees(age * 17.0f));
		pose.scale(SCALE, SCALE, SCALE);
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(ImpactWebModel.TEXTURE));
		model.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		super.render(entity, yaw, partialTicks, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ImpactWebEntity entity) {
		return ImpactWebModel.TEXTURE;
	}
}
