package com.projecthero.mod.client.behemoth;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/** The GeckoLib {@link GeoModel} for The Abyssal Behemoth -- one fixed geometry/texture/animation set. */
public class BehemothModel extends GeoModel<AbyssalBehemothEntity> {
	private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/abyssal_behemoth.geo.json");
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/abyssal_behemoth.png");
	private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/abyssal_behemoth.animation.json");

	@Override
	public ResourceLocation getModelResource(AbyssalBehemothEntity animatable) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(AbyssalBehemothEntity animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(AbyssalBehemothEntity animatable) {
		return ANIMATION;
	}
}
