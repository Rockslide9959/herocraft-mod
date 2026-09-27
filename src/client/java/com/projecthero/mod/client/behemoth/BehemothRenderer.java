package com.projecthero.mod.client.behemoth;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders The Abyssal Behemoth through GeckoLib. The model is hand-built at a {@code MODEL_HEIGHT}
 * reference scale, then stretched to the entity's real bounding-box height here -- the same trick
 * {@code TitanFormRenderer} uses for a Titan Shifter's Titan body.
 */
public class BehemothRenderer extends GeoEntityRenderer<AbyssalBehemothEntity> {
	public BehemothRenderer(EntityRendererProvider.Context context) {
		super(context, new BehemothModel());
		this.shadowRadius = 4.0f;
	}

	@Override
	public void preRender(PoseStack poseStack, AbyssalBehemothEntity animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
			net.minecraft.client.renderer.MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		float scale = animatable.getBbHeight() / AbyssalBehemothEntity.MODEL_HEIGHT;
		if (Math.abs(scale - 1.0f) > 0.01f) {
			poseStack.scale(scale, scale, scale);
		}
	}
}
