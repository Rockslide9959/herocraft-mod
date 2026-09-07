package com.projecthero.mod.punisher.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.item.ModArmorMaterials;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Items belonging to the Punisher power itself (as opposed to the generic firearm engine's items in
 * {@code FirearmItems}): the Frag Grenade / C4 render stand-ins, the three tactical armour pieces,
 * and the Vigilante Training Manual.
 */
public final class PunisherItems {
	/** Render stand-in for the thrown grenade entity. Not crafted -- the grenade is an ability. */
	public static Item FRAG_GRENADE;
	/** Render stand-in for the placed C4 charge entity. Not in any tab; never given out. */
	public static Item C4_CHARGE;

	public static PunisherArmorItem TACTICAL_VEST;
	public static PunisherArmorItem TACTICAL_LEGGINGS;
	public static PunisherArmorItem TACTICAL_BOOTS;

	public static Item VIGILANTE_TRAINING_MANUAL;

	private PunisherItems() {
	}

	public static void initialize() {
		FRAG_GRENADE = register("frag_grenade", new Item(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
		C4_CHARGE = register("c4_charge", new Item(new Item.Properties().stacksTo(1)));

		TACTICAL_VEST = registerArmor("punisher_tactical_vest", ArmorItem.Type.CHESTPLATE);
		TACTICAL_LEGGINGS = registerArmor("punisher_tactical_leggings", ArmorItem.Type.LEGGINGS);
		TACTICAL_BOOTS = registerArmor("punisher_tactical_boots", ArmorItem.Type.BOOTS);

		VIGILANTE_TRAINING_MANUAL = register("vigilante_training_manual",
				new VigilanteTrainingManualItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

		// v0.10.1: the Punisher armour now renders through the "Mark 2" model the user picked -- the geo
		// and texture files at these paths were replaced in place; the "punisher" set id is unchanged.
		SuperheroArmorVisuals.register("punisher", new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/punisher.geo.json"),
				ProjectHeroMod.id("textures/armor/punisher.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
	}

	public static void addToCreativeTab(CreativeModeTab.Output out) {
		out.accept(TACTICAL_VEST);
		out.accept(TACTICAL_LEGGINGS);
		out.accept(TACTICAL_BOOTS);
		out.accept(FRAG_GRENADE);
		out.accept(VIGILANTE_TRAINING_MANUAL);
	}

	private static PunisherArmorItem registerArmor(String path, ArmorItem.Type type) {
		// v0.9.5: diamond-level material (9/7/4 armour, 3 toughness, no knockback resistance) + explicit
		// per-piece durabilities. No more boots attribute override -- the material now gives the boots
		// exactly 4 / 3 / 0 natively. No .fireResistant() (diamond-level, not netherite).
		Item.Properties props = new Item.Properties().rarity(Rarity.UNCOMMON).durability(durabilityFor(type));
		return (PunisherArmorItem) register(path, new PunisherArmorItem(ModArmorMaterials.PUNISHER, type, props));
	}

	private static int durabilityFor(ArmorItem.Type type) {
		return switch (type) {
			case CHESTPLATE -> 680;
			case LEGGINGS -> 600;
			case BOOTS -> 529;
			default -> 400;
		};
	}

	private static Item register(String path, Item item) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
