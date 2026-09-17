package com.projecthero.mod.greenlantern.item;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of the Green Lantern hard-light suit -- helmet, chest, legs or boots. Synthesised on Suit
 * Up (not crafted); renders through the shared GeckoLib armour path on its own bespoke
 * {@code geo/green_lantern.geo.json} (see {@code SuperheroArmorVisuals}). The "helmet" piece
 * deliberately has no head geometry at all (v0.11.3) -- the suit design has no helmet, so
 * {@code PlayerModelMixin#helmetRetracted} always shows the wearer's own hat/hair layer through it.
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
