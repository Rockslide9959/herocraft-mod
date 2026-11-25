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
 * <p>v0.17.7 (user: "Use this skin ... and add a red cape model", then "no i still want the suit and armour i was just saying remove
 * thors actual skin form the suit and keep the helmet"): the suit is the "Thor | Avengers" skin on PlanetMinecraft,
 * https://www.planetminecraft.com/skin/thor-avengers-6321094/ ({@code textures/armor/thor.png}, built by
 * {@code scratchpad/v0177/make_thor_skin.js}, the original download kept beside it) -- the tunic, shoulder plates, bracers, belt,
 * greaves, boots and the winged helmet (drawn on the chestplate's pass while nothing else is on the head) -- with Thor himself (face,
 * hair, beard, bare arms, hands) cleared to transparent so the wearer's own face and skin show, the cape painted on its back taken
 * off and the back repainted as armour. The cape is the red 3D cloth one ({@code client/thor/ThorCapeLayer}).
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
