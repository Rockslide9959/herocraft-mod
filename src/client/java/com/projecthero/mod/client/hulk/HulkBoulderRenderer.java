package com.projecthero.mod.client.hulk;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hulk.entity.HulkBoulderEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** v0.13.14: draws the chunk of earth the Hulk tears up ({@code scratchpad/gen_hulk_boulder.js}), tumbling in flight. */
public class HulkBoulderRenderer extends GeoEntityRenderer<HulkBoulderEntity> {
	public HulkBoulderRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		withScale(1.7f); // sized for a 3.2-block Hulk
		this.shadowRadius = 0.9f;
	}

	private static final class Model extends GeoModel<HulkBoulderEntity> {
		private static final ResourceLocation GEO = ProjectHeroMod.id("geo/hulk_boulder.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/hulk_boulder.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/hulk_boulder.animation.json");

		@Override
		public ResourceLocation getModelResource(HulkBoulderEntity animatable) {
			return GEO;
		}

		@Override
		public ResourceLocation getTextureResource(HulkBoulderEntity animatable) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(HulkBoulderEntity animatable) {
			return ANIMATION;
		}
	}
}
