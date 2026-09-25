package com.projecthero.mod.client.titanshifter;

import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/**
 * The GeckoLib {@link GeoModel} for a Titan form. Model, texture and animation file all come from the
 * entity's {@link com.projecthero.mod.titanshifter.TitanType}, so a new Titan type is just new asset files;
 * Hardening swaps in the crystal texture.
 */
public class TitanFormModel extends GeoModel<TitanFormEntity> {
	@Override
	public ResourceLocation getModelResource(TitanFormEntity animatable) {
		return animatable.titanType().geo();
	}

	@Override
	public ResourceLocation getTextureResource(TitanFormEntity animatable) {
		return animatable.isHardened() ? animatable.titanType().hardenedTexture() : animatable.titanType().texture();
	}

	@Override
	public ResourceLocation getAnimationResource(TitanFormEntity animatable) {
		return animatable.titanType().animation();
	}
}
