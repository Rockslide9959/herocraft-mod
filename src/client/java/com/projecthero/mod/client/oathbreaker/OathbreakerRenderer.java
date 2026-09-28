package com.projecthero.mod.client.oathbreaker;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.oathbreaker.entity.OathbreakerEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the Oathbreaker through GeckoLib. The model is hand-built at a {@code MODEL_HEIGHT}
 * reference scale (vanilla-player proportions), then stretched to the entity's real bounding-box
 * height here -- the same trick {@code TitanFormRenderer}/{@code BehemothRenderer} use.
 */
public class OathbreakerRenderer extends GeoEntityRenderer<OathbreakerEntity> {
	public OathbreakerRenderer(EntityRendererProvider.Context context) {
		super(context, new OathbreakerModel());
		this.shadowRadius = 0.9f;
	}

	@Override
	public void preRender(PoseStack poseStack, OathbreakerEntity animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
			net.minecraft.client.renderer.MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		float scale = animatable.getBbHeight() / OathbreakerEntity.MODEL_HEIGHT;
		if (Math.abs(scale - 1.0f) > 0.01f) {
			poseStack.scale(scale, scale, scale);
		}
	}
}
