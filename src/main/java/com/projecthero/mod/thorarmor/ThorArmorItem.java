package com.projecthero.mod.thorarmor;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of Thor's Armour (v0.12.32) -- chest, legs or boots (the supplied skin has no helmet art). Never
 * crafted: {@link ThorArmor} conjures the set when a bound Thor presses H (lightning strikes them and the
 * armour forms), and deletes it again when it leaves their inventory or they die. Renders through the shared
 * GeckoLib armour path using {@code geo/thor.geo.json} over {@code textures/armor/thor.png}.
 *
 * <p>v0.17.7 (user: "Use this skin ... and add a red cape model", then "remove the skin from it so players can see their skin
 * underneath and keep the helmet"): the armour no longer covers the body. What is drawn is the winged helmet from the "Thor |
 * Avengers" skin on PlanetMinecraft, https://www.planetminecraft.com/skin/thor-avengers-6321094/ -- only its helmet texels are kept in
 * {@code textures/armor/thor.png} ({@code scratchpad/v0177/make_thor_skin.js}, the original download kept beside it), drawn on the
 * chestplate's pass while nothing else is on the head -- and the red 3D cloth cape ({@code client/thor/ThorCapeLayer}). The greaves
 * and boots still give their armour but draw nothing.
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
