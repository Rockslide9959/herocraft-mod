package com.projecthero.mod.spin;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * v0.17.3 the Spin: the power's items. {@link #ZEPPELI_STEEL_BALLS} grants the Spin (the POWER agent wires its right-click;
 * rare loot in desert pyramids and trail ruins, see {@code docs/SPIN_DESIGN.md}). Icon: {@code textures/item/zeppeli_steel_balls.png},
 * made by {@code scratchpad/spin/build_steel_ball_textures.js}.
 */
public final class SpinItems {
	public static Item ZEPPELI_STEEL_BALLS;

	private SpinItems() {
	}

	public static void initialize() {
		if (ZEPPELI_STEEL_BALLS != null) {
			return;
		}
		ZEPPELI_STEEL_BALLS = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("zeppeli_steel_balls"),
				new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ZEPPELI_STEEL_BALLS);
	}
}
