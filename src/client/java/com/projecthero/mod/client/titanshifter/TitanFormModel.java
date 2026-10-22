package com.projecthero.mod.client.titanshifter;

import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/**
 * The GeckoLib {@link GeoModel} for a Titan form. Model, texture and animation file all come from the entity's
 * {@link TitanType}. v0.16.7 (user, Titan revamp): while the Titan is transforming, the texture is the per-texel build
 * frame (skeleton, muscle, skin -- {@link TitanSkinFrames}); while a severed limb regrows, the regrowth frame; Hardening
 * swaps in the crystal atlas and the Emergency Titan is washed pale.
 */
public class TitanFormModel extends GeoModel<TitanFormEntity> {
	@Override
	public ResourceLocation getModelResource(TitanFormEntity animatable) {
		return animatable.titanType().geo();
	}

	@Override
	public ResourceLocation getTextureResource(TitanFormEntity e) {
		TitanType type = e.titanType();
		boolean pale = e.isEmergency();
		ResourceLocation tex = e.isHardened() ? type.hardenedTexture() : type.texture();
		float partial = TitanFx.partialTick();
		TitanTexels tx = TitanTexels.get(type.modelName());
		float build = TitanFx.buildProgress(e, partial);
		if (build >= 0.0f && build < 0.999f && tx != null) {
			return TitanSkinFrames.build(e, tx, type.texture(), pale, build);
		}
		TitanFx.observeLimbs(e);
		float[] regrow = TitanFx.regrowProgress(e, partial);
		if (TitanFx.anyRegrowing(regrow) && tx != null) {
			return TitanSkinFrames.regrow(e, tx, tex, pale, regrow);
		}
		if (TitanSkinFrames.has(e)) {
			TitanSkinFrames.drop(e);
		}
		return pale ? PaleTextures.of(tex) : tex;
	}

	@Override
	public ResourceLocation getAnimationResource(TitanFormEntity animatable) {
		return animatable.titanType().animation();
	}
}
