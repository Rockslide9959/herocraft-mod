package com.projecthero.mod.client.titanshifter;

import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.entity.TitanCorpseEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/**
 * The abandoned Titan body. v0.16.7 (user, Titan revamp): the same rig as the living Titan (its corpse-only nape flaps
 * shown) with the torn-nape atlas ({@code <model>_corpse.png}); once it has sunk to its knees it dissolves texel by
 * texel -- skin, then muscle, then skeleton ({@link TitanSkinFrames#dissolve}).
 */
public class TitanCorpseModel extends GeoModel<TitanCorpseEntity> {
	@Override
	public ResourceLocation getModelResource(TitanCorpseEntity animatable) {
		return animatable.titanType().geo();
	}

	@Override
	public ResourceLocation getTextureResource(TitanCorpseEntity e) {
		TitanType type = e.titanType();
		ResourceLocation tex = type.corpseTexture();
		float t = TitanFx.dissolveProgress(e, TitanFx.partialTick());
		TitanTexels tx = TitanTexels.get(type.modelName());
		if (t > 0.0f && tx != null) {
			return TitanSkinFrames.dissolve(e, tx, tex, e.isEmergency(), t);
		}
		return e.isEmergency() ? PaleTextures.of(tex) : tex;
	}

	@Override
	public ResourceLocation getAnimationResource(TitanCorpseEntity animatable) {
		return animatable.titanType().animation();
	}
}
