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

	/**
	 * v0.12.16: the Wolverine Suit tears and bloodies as its wearer's health drops -- four textures,
	 * chosen by health fraction (over 90% clean, then 90 / 65 / 40%), and a fifth, almost fully torn off, during the Death Surge.
	 */
	@Override
	public ResourceLocation getTextureResource(SuperheroArmorItem animatable,
			software.bernie.geckolib.renderer.GeoRenderer<SuperheroArmorItem> renderer) {
		if ("wolverine".equals(animatable.armorSetId()) && renderer instanceof software.bernie.geckolib.renderer.GeoArmorRenderer<?> armor
				&& armor.getCurrentEntity() instanceof net.minecraft.world.entity.LivingEntity wearer) {
			float frac = wearer.getMaxHealth() <= 0 ? 1.0f : wearer.getHealth() / wearer.getMaxHealth();
			// Death Surge: the suit is almost completely torn off
			int stage = (wearer instanceof net.minecraft.world.entity.player.Player p && com.projecthero.mod.wolverine.Wolverine.resurrecting(p))
					? 4 : frac > 0.9f ? 0 : frac > 0.65f ? 1 : frac > 0.4f ? 2 : 3;
			if (stage > 0) {
				return com.projecthero.mod.ProjectHeroMod.id("textures/armor/wolverine_damaged_" + stage + ".png");
			}
		}
		return getTextureResource(animatable);
	}

	@Override
	public ResourceLocation getAnimationResource(SuperheroArmorItem animatable) {
		return SuperheroArmorVisuals.get(animatable.armorSetId()).animation();
	}
}
