package com.projecthero.mod.client.ultron.contagion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.ultron.contagion.entity.VibraniumCoreEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.16.1: the Vibranium Core. Its glowmask pulses once a second ({@link OmegaRenderer#corePulse}, the clock the real
 * Omega's eyes follow), brighter the more it is charged, and a violet-red aura grows round the crystal with the charge.
 */
public class VibraniumCoreRenderer extends GeoEntityRenderer<VibraniumCoreEntity> {
	static final ResourceLocation GLOWMASK = ProjectHeroMod.id("textures/entity/ultron/vibranium_core_glowmask.png");

	public VibraniumCoreRenderer(EntityRendererProvider.Context context) {
		super(context, new GeoModel<>() {
			@Override
			public ResourceLocation getModelResource(VibraniumCoreEntity a) {
				return ProjectHeroMod.id("geo/ultron/vibranium_core.geo.json");
			}

			@Override
			public ResourceLocation getTextureResource(VibraniumCoreEntity a) {
				return ProjectHeroMod.id("textures/entity/ultron/vibranium_core.png");
			}

			@Override
			public ResourceLocation getAnimationResource(VibraniumCoreEntity a) {
				return ProjectHeroMod.id("animations/ultron/vibranium_core.animation.json");
			}
		});
		this.shadowRadius = 1.6f;
		addRenderLayer(new GeoRenderLayer<>(this) {
			@Override
			public void render(PoseStack poseStack, VibraniumCoreEntity animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
					MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
				float k = 0.55f + 0.25f * animatable.charge() + 0.2f * OmegaRenderer.corePulse(animatable.level().getGameTime(), partialTick);
				int c = Math.max(0, Math.min(255, (int) (255 * k)));
				RenderType glow = RenderType.eyes(GLOWMASK);
				getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF000000 | (c << 16) | (c << 8) | c);
			}
		});
	}

	@Override
	public void render(VibraniumCoreEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		super.render(entity, yaw, partialTick, pose, buffers, light);
		float pulse = OmegaRenderer.corePulse(entity.level().getGameTime(), partialTick);
		pose.pushPose();
		pose.translate(0, 3.0, 0);
		TurboDraw.sphere(TurboDraw.buffer(buffers), pose, 1.0f + entity.charge() * 1.4f + pulse * 0.2f, 0xB040FF, 0.10f + 0.12f * entity.charge());
		pose.popPose();
	}
}
