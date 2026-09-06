package com.projecthero.mod.punisher.item;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * A piece of the Punisher tactical armour set -- chest, legs or boots (there is no helmet). A real,
 * crafted, wearable item like the Spider-Man Suit; it renders through the shared GeckoLib armour
 * path using {@code geo/punisher.geo.json} over the supplied 128x128 skin. The full-set bonus
 * (projectile-damage reduction, knockback resistance, a touch less recoil) only applies while the
 * wearer holds the Punisher power -- see {@link com.projecthero.mod.punisher.PunisherArmorSet}.
 */
public class PunisherArmorItem extends SuperheroArmorItem {
	public PunisherArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "punisher";
	}
}
