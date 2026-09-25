package com.projecthero.mod.thorarmor;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of Thor's Armour (v0.12.32) -- chest, legs or boots (the supplied skin has no helmet art). Never
 * crafted: {@link ThorArmor} conjures the set when a bound Thor presses H (lightning strikes them and the
 * armour forms), and deletes it again when it leaves their inventory or they die. Renders through the shared
 * GeckoLib armour path using {@code geo/thor.geo.json} over {@code textures/armor/thor.png}.
 */
public class ThorArmorItem extends SuperheroArmorItem {
	public ThorArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "thor";
	}
}
