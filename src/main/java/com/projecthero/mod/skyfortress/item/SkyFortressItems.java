package com.projecthero.mod.skyfortress.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.loot.HeroRarity;
import com.projecthero.mod.skyfortress.block.SkyFortressBlocks;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/**
 * v0.17.7 Sky Fortress items: the <b>Fortress Pass</b> (the Launch Pad's key, dropped by the Pad Sergeant), the
 * <b>Parachute</b> (from the hangar's crate; opens by itself in a long fall) and the fortress's artifact,
 * <b>Kestrel's Thruster</b> (rolled per player from its Fortress Vault).
 */
public final class SkyFortressItems {
	public static Item FORTRESS_PASS;
	public static Item PARACHUTE;
	public static Item KESTREL_THRUSTER;

	private SkyFortressItems() {
	}

	public static void initialize() {
		FORTRESS_PASS = item("fortress_pass", new FortressPassItem(HeroRarity.props(HeroRarity.RARE).stacksTo(1)));
		PARACHUTE = item("parachute", new ParachuteItem(HeroRarity.props(HeroRarity.COMMON).stacksTo(4)));
		KESTREL_THRUSTER = item("kestrel_thruster", new KestrelThrusterItem(HeroRarity.props(HeroRarity.LEGENDARY).stacksTo(1).fireResistant()));
	}

	public static Item[] all() {
		return new Item[] { FORTRESS_PASS, PARACHUTE, KESTREL_THRUSTER };
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (Item i : all()) {
			output.accept(i);
		}
		for (Item i : SkyFortressBlocks.all()) {
			output.accept(i);
		}
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
