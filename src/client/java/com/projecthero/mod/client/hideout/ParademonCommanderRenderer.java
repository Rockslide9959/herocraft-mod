package com.projecthero.mod.client.hideout;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.darkseid.entity.ParademonEntity;
import com.projecthero.mod.hideout.boss.ParademonCommanderBoss;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.17.5: the Parademon Commander -- the Parademons' own GeckoLib geo / animations (no new body), in the crimson
 * {@code parademon_commander} texture (+ glowmask), drawn {@link ParademonCommanderBoss#SCALE} times bigger to match its
 * hit-box.
 */
public class ParademonCommanderRenderer extends GeoEntityRenderer<ParademonEntity> {
	public ParademonCommanderRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 0.8f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	static final class Model extends GeoModel<ParademonEntity> {
		private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/parademon.geo.json");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/parademon.animation.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/parademon_commander.png");

		@Override
		public ResourceLocation getModelResource(ParademonEntity animatable) {
			return MODEL;
		}

		@Override
		public ResourceLocation getTextureResource(ParademonEntity animatable) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(ParademonEntity animatable) {
			return ANIMATION;
		}
	}

	@Override
	public void preRender(PoseStack poseStack, ParademonEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (!isReRender) {
			poseStack.scale(ParademonCommanderBoss.SCALE, ParademonCommanderBoss.SCALE, ParademonCommanderBoss.SCALE);
		}
	}
}
