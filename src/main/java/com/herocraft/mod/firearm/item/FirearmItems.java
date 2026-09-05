package com.herocraft.mod.firearm.item;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.firearm.Firearms;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Every item in the Punisher firearm arsenal: the guns, their ammunition, and the crafting
 * components. Registered from {@link com.herocraft.mod.HeroCraftMod}. The four weapons are built
 * across Phases 1-2; the fields for the later ones stay null until their phase wires them.
 */
public final class FirearmItems {
	// ---- weapons ----
	public static FirearmItem PUNISHER_PISTOL;
	public static FirearmItem PUNISHER_ASSAULT_RIFLE;
	public static FirearmItem PUNISHER_SHOTGUN;
	public static FirearmItem PUNISHER_SNIPER;

	// ---- ammunition ----
	public static Item PISTOL_AMMO;
	public static Item RIFLE_AMMO;
	public static Item SHOTGUN_SHELL;
	public static Item SNIPER_AMMO;

	// ---- crafting components ----
	public static Item WEAPON_PARTS;
	public static Item GUN_BARREL;
	public static Item WEAPON_SCOPE;

	private FirearmItems() {
	}

	public static void initialize() {
		WEAPON_PARTS = register("weapon_parts", new Item(new Item.Properties()));
		GUN_BARREL = register("gun_barrel", new Item(new Item.Properties()));
		WEAPON_SCOPE = register("weapon_scope", new Item(new Item.Properties()));

		PISTOL_AMMO = register("pistol_ammo", new Item(new Item.Properties().stacksTo(64)));
		RIFLE_AMMO = register("rifle_ammo", new Item(new Item.Properties().stacksTo(64)));
		SHOTGUN_SHELL = register("shotgun_shell", new Item(new Item.Properties().stacksTo(64)));
		SNIPER_AMMO = register("sniper_ammo", new Item(new Item.Properties().stacksTo(64)));

		PUNISHER_PISTOL = register("punisher_pistol",
				new FirearmItem(Firearms.PISTOL, new Item.Properties().rarity(Rarity.UNCOMMON)));
		PUNISHER_ASSAULT_RIFLE = register("punisher_assault_rifle",
				new FirearmItem(Firearms.RIFLE, new Item.Properties().rarity(Rarity.UNCOMMON)));
		PUNISHER_SHOTGUN = register("punisher_shotgun",
				new FirearmItem(Firearms.SHOTGUN, new Item.Properties().rarity(Rarity.UNCOMMON)));
		PUNISHER_SNIPER = register("punisher_sniper",
				new FirearmItem(Firearms.SNIPER, new Item.Properties().rarity(Rarity.RARE)));
	}

	/** Everything Punisher, in a stable order, for the creative tab. Guns, ammo, components. */
	public static void addToCreativeTab(CreativeModeTab.Output out) {
		accept(out, PUNISHER_PISTOL);
		accept(out, PUNISHER_ASSAULT_RIFLE);
		accept(out, PUNISHER_SHOTGUN);
		accept(out, PUNISHER_SNIPER);
		accept(out, PISTOL_AMMO);
		accept(out, RIFLE_AMMO);
		accept(out, SHOTGUN_SHELL);
		accept(out, SNIPER_AMMO);
		accept(out, WEAPON_PARTS);
		accept(out, GUN_BARREL);
		accept(out, WEAPON_SCOPE);
	}

	private static void accept(CreativeModeTab.Output out, Item item) {
		if (item != null) {
			out.accept(item);
		}
	}

	private static <T extends Item> T register(String path, T item) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HeroCraftMod.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
