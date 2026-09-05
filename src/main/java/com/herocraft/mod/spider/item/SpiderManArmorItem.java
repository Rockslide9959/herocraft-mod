package com.herocraft.mod.spider.item;

import com.herocraft.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * A piece of the craftable Spider-Man Suit (v0.6.18). Unlike Max Steel's synthesised pieces this one
 * is a real, crafted, wearable item -- it renders through the mod's shared GeckoLib armour path using
 * {@code geo/spider_man.geo.json} (converted from the Spider-Man GeckoLib entity bundle to GeckoLib's
 * armour rig by {@code scratchpad/convert_spiderman_geo.js}) over {@code textures/armor/spider_man.png}.
 *
 * <p>Purely a costume: it grants no powers and does not interact with the Spider-Man Hero Class in
 * any way. A Spider-Man player can wear it, an ordinary player can wear it, and taking it off changes
 * nothing about the power.
 */
public class SpiderManArmorItem extends SuperheroArmorItem {
	public SpiderManArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "spider_man";
	}
}
