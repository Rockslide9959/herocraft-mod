package com.projecthero.mod.client.behemoth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renders The Abyssal Behemoth through GeckoLib. The model is hand-built at a {@code MODEL_HEIGHT} reference scale (tentacle
 * tips to horn tips), then stretched to the entity's real bounding-box height here -- the same trick {@code TitanFormRenderer}
 * uses. v0.17.2: the rebuilt model glows (eyes, maw, vents, magma cracks, tentacle tips) through its glowmask, and there is
 * no vanilla tip-over while dying -- the death clip is the collapse.
 */
public class BehemothRenderer extends GeoEntityRenderer<AbyssalBehemothEntity> {
	public BehemothRenderer(EntityRendererProvider.Context context) {
		super(context, new BehemothModel());
		this.shadowRadius = 4.0f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	@Override
	public void preRender(PoseStack poseStack, AbyssalBehemothEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (!isReRender) { // the glow layer re-renders inside the already-scaled stack
			float scale = animatable.getBbHeight() / AbyssalBehemothEntity.MODEL_HEIGHT;
			poseStack.scale(scale, scale, scale);
		}
	}

	@Override
	protected float getDeathMaxRotation(AbyssalBehemothEntity animatable) {
		return 0.0f;
	}
}
