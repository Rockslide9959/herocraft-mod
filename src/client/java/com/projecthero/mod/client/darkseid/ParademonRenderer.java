package com.projecthero.mod.client.darkseid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.darkseid.entity.ParademonEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Parademons: one GeckoLib geo/animation set, a texture per variant (standard olive-grey, ranged with its orange
 * blaster visor, elite in black-and-gold, brute scarred red-brown), each with a glowmask for the eyes. The Brute
 * is drawn {@link ParademonEntity#BRUTE_SCALE} times bigger, matching its hit-box.
 */
public class ParademonRenderer extends GeoEntityRenderer<ParademonEntity> {
	public ParademonRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 0.5f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	static final class Model extends GeoModel<ParademonEntity> {
		private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/parademon.geo.json");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/parademon.animation.json");
		private static final ResourceLocation[] TEXTURES = {
				ProjectHeroMod.id("textures/entity/parademon.png"),
				ProjectHeroMod.id("textures/entity/parademon_ranged.png"),
				ProjectHeroMod.id("textures/entity/parademon_elite.png"),
				ProjectHeroMod.id("textures/entity/parademon_brute.png"),
		};

		@Override
		public ResourceLocation getModelResource(ParademonEntity animatable) {
			return MODEL;
		}

		@Override
		public ResourceLocation getTextureResource(ParademonEntity animatable) {
			return TEXTURES[animatable.variant().ordinal()];
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
		if (!isReRender && animatable.variant() == ParademonEntity.Variant.BRUTE) {
			poseStack.scale(ParademonEntity.BRUTE_SCALE, ParademonEntity.BRUTE_SCALE, ParademonEntity.BRUTE_SCALE);
		}
	}
}
