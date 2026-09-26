package com.projecthero.mod.allmight.item;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of the All Might costume (v0.12.35: the supplied bbmodel) -- synthesised onto the wearer by {@code AllMightSuit}
 * while they are in the Power Form, never crafted. One model, one set id.
 */
public class AllMightArmorItem extends SuperheroArmorItem {
	public AllMightArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "all_might";
	}
}
