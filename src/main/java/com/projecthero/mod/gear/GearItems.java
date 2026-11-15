package com.projecthero.mod.gear;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.loot.HeroRarity;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;

/** v0.17.5 gear tiers I-IV: every item and the gadget entities. See {@code docs/GEAR_TIERS_REFERENCE.md}. */
public final class GearItems {
	// Tier I gadgets (+ Tier III upgrades)
	public static Item GRAPPLE_LINE;
	public static Item SMOKE_BOMB;
	public static Item FLASHBANG;
	public static Item VIBRANIUM_GRAPPLE;
	public static Item VIBRANIUM_SMOKE_BOMB;
	public static Item VIBRANIUM_FLASHBANG;
	// Tier II
	public static Item HERO_ALLOY_HELMET;
	public static Item HERO_ALLOY_CHESTPLATE;
	public static Item HERO_ALLOY_LEGGINGS;
	public static Item HERO_ALLOY_BOOTS;
	public static Item HERO_ALLOY_SWORD;
	public static Item ALLOY_BATON;
	public static Item UTILITY_BELT;
	// Tier III
	public static Item VIBRANIUM_HELMET;
	public static Item VIBRANIUM_CHESTPLATE;
	public static Item VIBRANIUM_LEGGINGS;
	public static Item VIBRANIUM_BOOTS;
	public static Item VIBRANIUM_BLADE;
	// Tier IV
	public static Item CELESTIAL_HELMET;
	public static Item CELESTIAL_CHESTPLATE;
	public static Item CELESTIAL_LEGGINGS;
	public static Item CELESTIAL_BOOTS;
	public static Item CELESTIAL_WARHAMMER;
	public static Item CELESTIAL_UPGRADE_TEMPLATE;

	public static EntityType<GrappleHookEntity> GRAPPLE_HOOK;
	public static EntityType<GadgetProjectile> THROWN_GADGET;
	public static EntityType<SmokeCloudEntity> SMOKE_CLOUD;

	private GearItems() {
	}

	static void initialize() {
		GRAPPLE_HOOK = entity("grapple_hook", EntityType.Builder.<GrappleHookEntity>of(GrappleHookEntity::new, MobCategory.MISC)
				.sized(0.25f, 0.25f).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(20).build("grapple_hook"));
		THROWN_GADGET = entity("thrown_gadget", EntityType.Builder.<GadgetProjectile>of(GadgetProjectile::new, MobCategory.MISC)
				.sized(0.25f, 0.25f).clientTrackingRange(6).updateInterval(10).build("thrown_gadget"));
		SMOKE_CLOUD = entity("smoke_cloud", EntityType.Builder.<SmokeCloudEntity>of(SmokeCloudEntity::new, MobCategory.MISC)
				.sized(0.5f, 0.5f).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(40).build("smoke_cloud"));

		GRAPPLE_LINE = item("grapple_line", new GrappleLineItem(false, HeroRarity.props(HeroRarity.COMMON).durability(64)));
		SMOKE_BOMB = item("smoke_bomb", new ThrownGadgetItem(GadgetCooldowns.Kind.SMOKE, false, HeroRarity.props(HeroRarity.COMMON).stacksTo(16)));
		FLASHBANG = item("flashbang", new ThrownGadgetItem(GadgetCooldowns.Kind.FLASH, false, HeroRarity.props(HeroRarity.UNCOMMON).stacksTo(16)));
		VIBRANIUM_GRAPPLE = item("vibranium_grapple", new GrappleLineItem(true, HeroRarity.props(HeroRarity.RARE).durability(160).fireResistant()));
		VIBRANIUM_SMOKE_BOMB = item("vibranium_smoke_bomb", new ThrownGadgetItem(GadgetCooldowns.Kind.SMOKE, true,
				HeroRarity.props(HeroRarity.RARE).stacksTo(16)));
		VIBRANIUM_FLASHBANG = item("vibranium_flashbang", new ThrownGadgetItem(GadgetCooldowns.Kind.FLASH, true,
				HeroRarity.props(HeroRarity.RARE).stacksTo(16)));

		HERO_ALLOY_HELMET = armor("hero_alloy_helmet", GearMaterials.HERO_ALLOY, ArmorItem.Type.HELMET, 2, "hero_alloy", HeroRarity.UNCOMMON, GearMaterials.ALLOY_DURABILITY, false);
		HERO_ALLOY_CHESTPLATE = armor("hero_alloy_chestplate", GearMaterials.HERO_ALLOY, ArmorItem.Type.CHESTPLATE, 2, "hero_alloy", HeroRarity.UNCOMMON, GearMaterials.ALLOY_DURABILITY, false);
		HERO_ALLOY_LEGGINGS = armor("hero_alloy_leggings", GearMaterials.HERO_ALLOY, ArmorItem.Type.LEGGINGS, 2, "hero_alloy", HeroRarity.UNCOMMON, GearMaterials.ALLOY_DURABILITY, false);
		HERO_ALLOY_BOOTS = armor("hero_alloy_boots", GearMaterials.HERO_ALLOY, ArmorItem.Type.BOOTS, 2, "hero_alloy", HeroRarity.UNCOMMON, GearMaterials.ALLOY_DURABILITY, false);
		HERO_ALLOY_SWORD = item("hero_alloy_sword", new GearSwordItem(GearMaterials.ALLOY_TIER, 2, false, "item.projecthero.hero_alloy_sword.hint",
				HeroRarity.props(HeroRarity.UNCOMMON).attributes(SwordItem.createAttributes(GearMaterials.ALLOY_TIER, 3, -2.4f))));
		ALLOY_BATON = item("alloy_baton", new GearSwordItem(GearMaterials.ALLOY_TIER, 2, true, "item.projecthero.alloy_baton.hint",
				HeroRarity.props(HeroRarity.UNCOMMON).attributes(SwordItem.createAttributes(GearMaterials.ALLOY_TIER, 1, -1.8f))));
		UTILITY_BELT = item("utility_belt", new UtilityBeltItem(HeroRarity.props(HeroRarity.RARE).stacksTo(1)));

		VIBRANIUM_HELMET = armor("vibranium_helmet", GearMaterials.VIBRANIUM, ArmorItem.Type.HELMET, 3, "vibranium", HeroRarity.RARE, GearMaterials.VIBRANIUM_DURABILITY, true);
		VIBRANIUM_CHESTPLATE = armor("vibranium_chestplate", GearMaterials.VIBRANIUM, ArmorItem.Type.CHESTPLATE, 3, "vibranium", HeroRarity.RARE, GearMaterials.VIBRANIUM_DURABILITY, true);
		VIBRANIUM_LEGGINGS = armor("vibranium_leggings", GearMaterials.VIBRANIUM, ArmorItem.Type.LEGGINGS, 3, "vibranium", HeroRarity.RARE, GearMaterials.VIBRANIUM_DURABILITY, true);
		VIBRANIUM_BOOTS = armor("vibranium_boots", GearMaterials.VIBRANIUM, ArmorItem.Type.BOOTS, 3, "vibranium", HeroRarity.RARE, GearMaterials.VIBRANIUM_DURABILITY, true);
		VIBRANIUM_BLADE = item("vibranium_blade", new GearSwordItem(GearMaterials.VIBRANIUM_TIER, 3, false, "item.projecthero.vibranium_blade.hint",
				HeroRarity.props(HeroRarity.RARE).fireResistant().attributes(SwordItem.createAttributes(GearMaterials.VIBRANIUM_TIER, 3, -2.2f))));

		CELESTIAL_HELMET = armor("celestial_helmet", GearMaterials.CELESTIAL, ArmorItem.Type.HELMET, 4, "celestial", HeroRarity.EPIC, GearMaterials.CELESTIAL_DURABILITY, true);
		CELESTIAL_CHESTPLATE = armor("celestial_chestplate", GearMaterials.CELESTIAL, ArmorItem.Type.CHESTPLATE, 4, "celestial", HeroRarity.EPIC, GearMaterials.CELESTIAL_DURABILITY, true);
		CELESTIAL_LEGGINGS = armor("celestial_leggings", GearMaterials.CELESTIAL, ArmorItem.Type.LEGGINGS, 4, "celestial", HeroRarity.EPIC, GearMaterials.CELESTIAL_DURABILITY, true);
		CELESTIAL_BOOTS = armor("celestial_boots", GearMaterials.CELESTIAL, ArmorItem.Type.BOOTS, 4, "celestial", HeroRarity.EPIC, GearMaterials.CELESTIAL_DURABILITY, true);
		CELESTIAL_WARHAMMER = item("celestial_warhammer", new CelestialWarhammerItem(HeroRarity.props(HeroRarity.LEGENDARY).fireResistant()
				.attributes(SwordItem.createAttributes(GearMaterials.CELESTIAL_TIER, 5, -3.1f))));
		CELESTIAL_UPGRADE_TEMPLATE = item("celestial_upgrade_template", new Item(HeroRarity.props(HeroRarity.EPIC).fireResistant()) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				GearTooltip.add(tooltip, 4, "item.projecthero.celestial_upgrade_template.hint");
			}
		});
	}

	/** Every gear item, in creative-tab / Guidebook order. */
	public static List<Item> all() {
		return List.of(GRAPPLE_LINE, SMOKE_BOMB, FLASHBANG,
				HERO_ALLOY_HELMET, HERO_ALLOY_CHESTPLATE, HERO_ALLOY_LEGGINGS, HERO_ALLOY_BOOTS, HERO_ALLOY_SWORD, ALLOY_BATON, UTILITY_BELT,
				VIBRANIUM_HELMET, VIBRANIUM_CHESTPLATE, VIBRANIUM_LEGGINGS, VIBRANIUM_BOOTS, VIBRANIUM_BLADE,
				VIBRANIUM_GRAPPLE, VIBRANIUM_SMOKE_BOMB, VIBRANIUM_FLASHBANG,
				CELESTIAL_UPGRADE_TEMPLATE, CELESTIAL_HELMET, CELESTIAL_CHESTPLATE, CELESTIAL_LEGGINGS, CELESTIAL_BOOTS, CELESTIAL_WARHAMMER);
	}

	public static List<Item> celestialArmor() {
		return List.of(CELESTIAL_HELMET, CELESTIAL_CHESTPLATE, CELESTIAL_LEGGINGS, CELESTIAL_BOOTS);
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (Item i : all()) {
			output.accept(i);
		}
	}

	private static Item armor(String path, Holder<ArmorMaterial> material, ArmorItem.Type type, int tier, String set, HeroRarity rarity,
			int durability, boolean fireResistant) {
		Item.Properties p = HeroRarity.props(rarity).durability(type.getDurability(durability));
		if (fireResistant) {
			p = p.fireResistant();
		}
		return item(path, new GearArmorItem(material, type, tier, set, p));
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}

	private static <T extends Entity> EntityType<T> entity(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
