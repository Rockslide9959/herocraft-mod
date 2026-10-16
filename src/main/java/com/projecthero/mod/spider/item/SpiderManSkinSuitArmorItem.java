package com.projecthero.mod.spider.item;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;

/**
 * v0.16.5 (user): the six film / comic Spider-Man suits -- Comic Classic, Brand New Day, TASM, TASM 2, 2002 and Future
 * Foundation -- each a minecraftskins.com skin drawn on the same skin-tight rig as the original suit
 * ({@code textures/armor/spider_man_<skin>.png}; every one but the Comic Classic has its chest and back spider darkened
 * toward black by {@code scratchpad/v0165_suits/emblem.js}). Like {@link SpiderManClassicArmorItem} it extends
 * {@link SpiderManArmorItem}, so the mask (H), the poses and every suit path cover it; only the texture differs. Crafted
 * from the matching original piece, which the player keeps ({@link SpiderSuitUpgradeRecipe}).
 */
public class SpiderManSkinSuitArmorItem extends SpiderManArmorItem {
	private final String skin;
	private final ResourceLocation texture;

	public SpiderManSkinSuitArmorItem(String skin, Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
		this.skin = skin;
		this.texture = ProjectHeroMod.id("textures/armor/spider_man_" + skin + ".png");
	}

	/** The suit's id part, e.g. {@code tasm2} in {@code spider_man_suit_tasm2_chestplate}. */
	public String skin() {
		return skin;
	}

	/** The 64x64 player-layout skin the suit is drawn in. */
	public ResourceLocation texture() {
		return texture;
	}
}
