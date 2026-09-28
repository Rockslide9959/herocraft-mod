package com.projecthero.mod.client.oathbreaker;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.oathbreaker.entity.OathbreakerEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/** The GeckoLib {@link GeoModel} for the Oathbreaker -- one fixed geometry/texture/animation set. */
public class OathbreakerModel extends GeoModel<OathbreakerEntity> {
	private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/oathbreaker.geo.json");
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/oathbreaker.png");
	private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/oathbreaker.animation.json");

	@Override
	public ResourceLocation getModelResource(OathbreakerEntity animatable) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(OathbreakerEntity animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(OathbreakerEntity animatable) {
		return ANIMATION;
	}
}
