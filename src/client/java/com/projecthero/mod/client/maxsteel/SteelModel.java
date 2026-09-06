package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.maxsteel.entity.SteelEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/** The GeckoLib {@link GeoModel} for the Steel Ultralink entity -- the supplied model, unmodified. */
public class SteelModel extends GeoModel<SteelEntity> {
	private static final ResourceLocation GEO = ProjectHeroMod.id("geo/steel_entity.geo.json");
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/steel.png");
	private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/steel_entity.animation.json");

	@Override
	public ResourceLocation getModelResource(SteelEntity animatable) {
		return GEO;
	}

	@Override
	public ResourceLocation getTextureResource(SteelEntity animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(SteelEntity animatable) {
		return ANIMATION;
	}
}
