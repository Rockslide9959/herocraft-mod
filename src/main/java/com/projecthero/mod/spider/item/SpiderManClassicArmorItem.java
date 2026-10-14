package com.projecthero.mod.spider.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * v0.16.4 (user): a second take on the original suit, to compare against it -- the Classic suit (the user's namemc skin
 * 00e5e6395e138103, {@code textures/armor/spider_man_classic.png}). It behaves exactly like the original suit (no Web
 * Wings, no Trip Mine); like {@link SpiderManV2ArmorItem} it extends {@link SpiderManArmorItem} so every suit path covers
 * it, and only the texture differs. Crafted from the matching original piece, which the player keeps.
 */
public class SpiderManClassicArmorItem extends SpiderManArmorItem {
	public SpiderManClassicArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}
}
