package com.projecthero.mod.item;

import java.util.function.Function;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item MJOLNIR = register("mjolnir", MjolnirItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
					.attributes(MjolnirItem.createAttributes()));

	/**
	 * Render-only stand-in that exists purely to own the 3D box model
	 * ({@code models/item/mjolnir_thrown.json}) for {@code MjolnirEntityRenderer}.
	 *
	 * <p>The real {@link #MJOLNIR} is a flat sprite on {@code minecraft:item/handheld}, so that it
	 * inherits vanilla's held-tool transforms verbatim and is gripped exactly like a pickaxe. A flat
	 * sprite would be nearly invisible edge-on in flight, though, so the thrown/resting hammer still
	 * needs real geometry -- and registering an item is the one guaranteed way to get an extra model
	 * baked without reaching for the model-loading API.
	 *
	 * <p>Deliberately not in any creative tab and never given to a player; it is only ever
	 * instantiated inside the entity renderer. This mirrors vanilla's trident, which likewise keeps a
	 * separate 3D model from its inventory sprite.
	 */
	public static final Item MJOLNIR_THROWN = register("mjolnir_thrown", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	// The Asgardian armour set (Helmet/Chestplate/Leggings/Boots of Asgard) was removed in v0.6.22 --
	// it was creative-only, purely cosmetic on top of the Power of Thor, and the user asked for it gone.
	// ModArmorMaterials.THOR stays registered: Max Steel and the Spider-Man suit reuse its flat fallback
	// armour layer (textures/models/armor/thor_layer_*.png).

	/** "changes 18": survival-craftable "give up your powers" item -- sneak + use strips every
	 *  Hero-Tier and experimental power. See {@link PowerSuppressorItem}. */
	public static final Item POWER_SUPPRESSOR = register("power_suppressor", PowerSuppressorItem::new,
			new Item.Properties().rarity(Rarity.UNCOMMON));

	private ModItems() {
	}

	public static void initialize() {
		ModDataComponents.initialize();
		ModArmorMaterials.initialize();
		ModCreativeTab.initialize();
	}

	private static <T extends Item> T register(String path, Function<Item.Properties, T> factory, Item.Properties settings) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path));
		T item = factory.apply(settings);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
