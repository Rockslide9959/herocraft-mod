package com.projecthero.mod.client.ultron.contagion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.ultron.contagion.entity.UltronOmegaEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.16.1: Ultron Omega through GeckoLib ({@code geo/ultron/ultron_omega.geo.json}, authored {@link #MODEL_HEIGHT} blocks
 * tall and scaled to the hit-box). The glowmask (eyes, mouth, the red lines) is drawn by {@link Glow}: the <b>real</b>
 * Omega's eyes flicker in step with the Vibranium Core's pulse (every second, the same clock the Core's crystal pulses on);
 * the decoys' burn steady -- the tell for players without a Mind Stone or a scanner. A red shell while he is shielded.
 */
public class OmegaRenderer extends GeoEntityRenderer<UltronOmegaEntity> {
	public static final float MODEL_HEIGHT = 4.0f;
	static final ResourceLocation GLOWMASK = ProjectHeroMod.id("textures/entity/ultron/ultron_omega_glowmask.png");

	public OmegaRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 0.9f;
		addRenderLayer(new Glow(this));
	}

	static final class Model extends GeoModel<UltronOmegaEntity> {
		private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/ultron/ultron_omega.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/ultron/ultron_omega.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/ultron/ultron_omega.animation.json");

		@Override
		public ResourceLocation getModelResource(UltronOmegaEntity animatable) {
			return MODEL;
		}

		@Override
		public ResourceLocation getTextureResource(UltronOmegaEntity animatable) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(UltronOmegaEntity animatable) {
			return ANIMATION;
		}
	}

	/** The Core's pulse: 1 at the start of every second, fading over half a second. */
	public static float corePulse(long gameTime, float partialTick) {
		float t = ((gameTime % 20) + partialTick) / 20f;
		return t < 0.5f ? 1f - t * 2f : 0f;
	}

	static final class Glow extends GeoRenderLayer<UltronOmegaEntity> {
		Glow(GeoRenderer<UltronOmegaEntity> renderer) {
			super(renderer);
		}

		@Override
		public void render(PoseStack poseStack, UltronOmegaEntity animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
				MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
			float k = 1f;
			if (!animatable.isDecoy()) {
				// the real one: dim between Core pulses, flaring with each one
				k = 0.35f + 0.65f * corePulse(animatable.level().getGameTime(), partialTick);
			}
			if (animatable.inVision()) {
				k *= 0.5f + 0.5f * Mth.sin((animatable.tickCount + partialTick) * 0.9f);
			}
			int c = Math.max(0, Math.min(255, (int) (255 * k)));
			int colour = 0xFF000000 | (c << 16) | (c << 8) | c;
			RenderType glow = RenderType.eyes(GLOWMASK);
			getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
		}
	}

	@Override
	public void preRender(PoseStack poseStack, UltronOmegaEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (isReRender) {
			return;
		}
		float scale = animatable.getBbHeight() / MODEL_HEIGHT;
		if (Math.abs(scale - 1.0f) > 0.01f) {
			poseStack.scale(scale, scale, scale);
		}
	}

	@Override
	public void render(UltronOmegaEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		super.render(entity, yaw, partialTick, pose, buffers, light);
		if (entity.shielded()) {
			float time = entity.tickCount + partialTick;
			pose.pushPose();
			pose.translate(0, entity.getBbHeight() * 0.5, 0);
			pose.scale(1f, 1.5f, 1f);
			TurboDraw.sphere(TurboDraw.buffer(buffers), pose, 1.9f, 0xFF2010, 0.16f + 0.05f * Mth.sin(time * 0.3f));
			pose.popPose();
		}
	}

	@Override
	public boolean shouldShowName(UltronOmegaEntity entity) {
		return false;
	}
}
