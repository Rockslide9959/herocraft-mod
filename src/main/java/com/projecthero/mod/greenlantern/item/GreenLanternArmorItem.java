package com.projecthero.mod.greenlantern.item;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of the Green Lantern hard-light suit -- helmet, chest, legs or boots. Synthesised on Suit
 * Up (not crafted); renders through the shared GeckoLib armour path on the {@code crimson_vanguard}
 * geometry, retextured green/black/white for a first pass (see {@code SuperheroArmorVisuals} --
 * TODO: a bespoke {@code geo/green_lantern.geo.json} is genuinely art-heavy follow-up work).
 */
public class GreenLanternArmorItem extends SuperheroArmorItem {
	public GreenLanternArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "green_lantern";
	}
}
