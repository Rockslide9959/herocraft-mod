package com.projecthero.mod.client.titanshifter;

import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.entity.TitanLimbChunkEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/**
 * v0.16.7 (user, Titan fixes): a severed Titan limb -- the Titan type's own geo and atlas (washed pale for an Emergency
 * Titan's), of which {@link TitanLimbChunkRenderer} draws only the limb's bones. Once it has lain steaming for a while it
 * dissolves texel by texel like the corpse: its skin, then its flesh, then its bone ({@link TitanSkinFrames#dissolve},
 * mapped onto the span of that limb's own dissolve times).
 */
public class TitanLimbChunkModel extends GeoModel<TitanLimbChunkEntity> {
	@Override
	public ResourceLocation getModelResource(TitanLimbChunkEntity e) {
		return e.titanType().geo();
	}

	@Override
	public ResourceLocation getTextureResource(TitanLimbChunkEntity e) {
		TitanType type = e.titanType();
		ResourceLocation tex = type.texture();
		TitanTexels tx = TitanTexels.get(type.modelName());
		float p = e.dissolveProgress(TitanFx.partialTick());
		if (p > 0.0f && tx != null) {
			return TitanSkinFrames.dissolve(e, tx, tex, e.isEmergency(), limbTime(tx, e.limb(), p));
		}
		return e.isEmergency() ? PaleTextures.of(tex) : tex;
	}

	/** The corpse-dissolve time at which {@code p} (0..1) of {@code limb}'s texels are gone. */
	static float limbTime(TitanTexels tx, int limb, float p) {
		int slot = TitanTexels.limbSlot(limb);
		float lo = tx.limbDissolveMin[slot], hi = tx.limbDissolveMax[slot];
		return p >= 1.0f ? 1.0f : lo - 0.001f + (hi - lo + 0.002f) * p;
	}

	@Override
	public ResourceLocation getAnimationResource(TitanLimbChunkEntity e) {
		return e.titanType().animation();
	}
}
