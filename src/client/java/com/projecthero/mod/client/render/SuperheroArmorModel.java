package com.projecthero.mod.client.render;

import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.armor.SuperheroArmorVisuals;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/**
 * The shared GeckoLib {@link GeoModel} for every superhero armour set. It holds no per-set state --
 * it just forwards to {@link SuperheroArmorVisuals}, keyed by the piece's
 * {@link SuperheroArmorItem#armorSetId() armour-set id}. That is what lets one model + one renderer
 * serve Thor and all five Iron Man marks while each keeps its own texture (and, later, its own
 * geometry/animation) -- see docs/ARMOR_MODELS.md.
 */
public class SuperheroArmorModel extends GeoModel<SuperheroArmorItem> {
	@Override
	public ResourceLocation getModelResource(SuperheroArmorItem animatable) {
		return SuperheroArmorVisuals.get(animatable.armorSetId()).geometry();
	}

	@Override
	public ResourceLocation getTextureResource(SuperheroArmorItem animatable) {
		return SuperheroArmorVisuals.get(animatable.armorSetId()).texture();
	}

	@Override
	public ResourceLocation getAnimationResource(SuperheroArmorItem animatable) {
		return SuperheroArmorVisuals.get(animatable.armorSetId()).animation();
	}
}
