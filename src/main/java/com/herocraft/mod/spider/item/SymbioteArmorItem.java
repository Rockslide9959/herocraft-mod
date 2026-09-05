package com.herocraft.mod.spider.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * A piece of the black Symbiote suit (v0.9.10). Like Max Steel's suit pieces -- and unlike the
 * craftable {@link SpiderManArmorItem} -- these are <em>synthesised</em> onto the player by
 * {@link com.herocraft.mod.symbiote.SymbioteSuit} when the Symbiote is activated (H) and
 * deleted when it retracts. Never crafted, never in a creative tab, never in a loot table, and given
 * no durability at all ({@code Item.Properties} without {@code .durability(...)}), so the suit is
 * genuinely unbreakable.
 *
 * <p>Extends {@link SpiderManArmorItem} on purpose: every {@code instanceof SpiderManArmorItem} check
 * the mod already has -- the shared armour renderer's mask handling, {@link
 * com.herocraft.mod.spider.SpiderMask#wearingHood} -- then covers the Symbiote suit for free. Only the
 * {@code armorSetId()} differs, pointing the shared GeckoLib armour path at the black-suit geometry
 * and texture.
 */
public class SymbioteArmorItem extends SpiderManArmorItem {
	public SymbioteArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "spider_man_symbiote";
	}
}
