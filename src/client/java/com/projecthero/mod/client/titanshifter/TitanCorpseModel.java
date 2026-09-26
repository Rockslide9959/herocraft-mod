package com.projecthero.mod.client.titanshifter;

import com.projecthero.mod.titanshifter.entity.TitanCorpseEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/** The sliced Titan corpse: {@code titan_corpse} geometry + its atlas texture, animated by the Titan's own death animation. */
public class TitanCorpseModel extends GeoModel<TitanCorpseEntity> {
	@Override
	public ResourceLocation getModelResource(TitanCorpseEntity animatable) {
		return animatable.titanType().corpseGeo();
	}

	@Override
	public ResourceLocation getTextureResource(TitanCorpseEntity animatable) {
		return animatable.titanType().corpseTexture();
	}

	@Override
	public ResourceLocation getAnimationResource(TitanCorpseEntity animatable) {
		return animatable.titanType().animation();
	}
}
