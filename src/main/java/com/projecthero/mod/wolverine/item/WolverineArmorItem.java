package com.projecthero.mod.wolverine.item;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * A piece of the craftable Wolverine Suit (v0.12.14) -- the yellow-and-blue costume. Like the
 * Spider-Man Suit it is purely visual (worn by anyone, grants nothing) and renders through the shared
 * GeckoLib armour path using {@code geo/wolverine.geo.json} over {@code textures/armor/wolverine.png}.
 */
public class WolverineArmorItem extends SuperheroArmorItem {
	public WolverineArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "wolverine";
	}
}
