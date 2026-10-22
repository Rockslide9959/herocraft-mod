package com.projecthero.mod.client.titanshifter;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.16.7 (user, Titan revamp) -- the emissive eyes ({@code <model>_glow.png}, the Armoured Titan's white eyes), drawn
 * full-bright over the body once its skin has formed. A type without a glow texture draws nothing.
 */
public class TitanGlowLayer<T extends TitanFormEntity> extends GeoRenderLayer<T> {
	private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

	public TitanGlowLayer(GeoRenderer<T> renderer) {
		super(renderer);
	}

	public static ResourceLocation glowOf(ResourceLocation texture) {
		String p = texture.getPath();
		return ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), p.substring(0, p.length() - 4) + "_glow.png");
	}

	@Override
	public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		if (animatable.isInvisible()) {
			return;
		}
		float build = TitanFx.buildProgress(animatable, partialTick);
		if (build >= 0.0f && build < 0.93f) {
			return; // no eyes yet
		}
		ResourceLocation glow = glowOf(animatable.titanType().texture());
		boolean exists = EXISTS.computeIfAbsent(glow, id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
		if (!exists) {
			return;
		}
		RenderType type = RenderType.eyes(glow);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
				0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}
}
