package com.projecthero.mod.spider.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * v0.16.2: a piece of the Spider-Man Suit v2 -- the upgraded classic red-and-blue suit, crafted from the matching
 * original piece (which the player keeps, see {@code SpiderSuitUpgradeRecipe}).
 *
 * <p>Extends {@link SpiderManArmorItem} on purpose and keeps its {@code "spider_man"} armour-set id, so every path the
 * original suit goes through -- the skin-tight suit rig ({@code SpiderSuitRender}), the mask (H, {@code SpiderMask}),
 * the jointed limbs, the perch and the hang, the hidden skin overlay -- covers v2 too. Only the texture differs
 * ({@code textures/armor/spider_man_v2.png}). Worn on the chest (alone or with the rest of the set) it gives a
 * Spider-Man the Web Wings glide ({@code SpiderWebWings}) and swaps Shift+C's Web Wall for the Trip Mine
 * ({@code SpiderTripMines}).
 */
public class SpiderManV2ArmorItem extends SpiderManArmorItem {
	public SpiderManV2ArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}
}
