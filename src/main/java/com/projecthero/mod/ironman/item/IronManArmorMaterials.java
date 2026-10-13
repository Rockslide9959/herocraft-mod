package com.projecthero.mod.ironman.item;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One {@link ArmorMaterial} per Iron Man mark. Defense / toughness / knockback-resistance climb with
 * the technology tier; these are the "physical protection the armor still provides even at zero suit
 * energy" (spec section 28). Repulsors, flight, Unibeam, HUD etc. are all gated separately behind the
 * Tony Stark power + suit energy and are NOT part of the material.
 *
 * <p>Each material references an armor-layer texture at
 * {@code assets/projecthero/textures/models/armor/<name>_layer_1.png} (+ {@code _layer_2} for leggings).
 * Placeholder textures ship now; drop real ones in at those paths with no code change.
 */
public final class IronManArmorMaterials {
	// v0.16.1 comic rework (explicit user stats, helmet / chest / legs / boots, toughness): every suit at least diamond.
	// Each piece carries 0.175 knockback resistance (70% for a full suit, v0.14.31); the Hulkbuster 0.25 (fully immune).
	// Totals above vanilla's 30-point armour cap are simply capped by vanilla (Hulkbuster 28 + Vibranium fits).
	public static final Holder<ArmorMaterial> MARK_1 = register("mark_1", hclb(3, 8, 6, 3),
			8, SoundEvents.ARMOR_EQUIP_IRON, 2.0f, 0.175f, Items.IRON_INGOT);
	public static final Holder<ArmorMaterial> MARK_1_V2 = register("mark_1_v2", hclb(3, 8, 6, 3),
			10, SoundEvents.ARMOR_EQUIP_GOLD, 2.0f, 0.175f, Items.GOLD_INGOT);
	public static final Holder<ArmorMaterial> MARK_2 = register("mark_2", hclb(3, 8, 6, 3),
			10, SoundEvents.ARMOR_EQUIP_IRON, 2.5f, 0.175f, Items.IRON_INGOT);
	public static final Holder<ArmorMaterial> MARK_III = register("mark_iii", hclb(3, 8, 6, 3),
			12, SoundEvents.ARMOR_EQUIP_IRON, 3.0f, 0.175f);
	public static final Holder<ArmorMaterial> MARK_4 = register("mark_4", hclb(4, 9, 7, 3),
			14, SoundEvents.ARMOR_EQUIP_IRON, 3.0f, 0.175f, Items.IRON_INGOT);
	public static final Holder<ArmorMaterial> MARK_V = register("mark_v", hclb(3, 8, 6, 3),
			12, SoundEvents.ARMOR_EQUIP_LEATHER, 3.0f, 0.175f); // the quiet one
	public static final Holder<ArmorMaterial> MARK_6 = register("mark_6", hclb(4, 9, 7, 4),
			15, SoundEvents.ARMOR_EQUIP_IRON, 3.5f, 0.175f, Items.IRON_INGOT);
	public static final Holder<ArmorMaterial> MARK_VII = register("mark_vii", hclb(4, 9, 7, 4),
			15, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0f, 0.175f);
	public static final Holder<ArmorMaterial> MARK_8 = register("mark_8", hclb(5, 10, 8, 5),
			15, SoundEvents.ARMOR_EQUIP_NETHERITE, 5.0f, 0.25f);

	/** v0.16.1: defence per piece in the order the user writes it -- helmet / chestplate / leggings / boots. */
	private static Map<ArmorItem.Type, Integer> hclb(int helmet, int chest, int legs, int boots) {
		return Map.of(ArmorItem.Type.HELMET, helmet, ArmorItem.Type.CHESTPLATE, chest, ArmorItem.Type.LEGGINGS, legs,
				ArmorItem.Type.BOOTS, boots);
	}
	// "changes 17": Mark XLII / Mark L materials removed with those suits.

	/**
	 * "changes 22": the bare {@link RepulsorItem} worn in the boots slot. It is a pair of thruster
	 * discs strapped to your feet, not armour -- 1 point of protection and nothing else -- and it
	 * registers with an <b>empty layer list</b>, so no armour texture is drawn over the player. Its
	 * whole value is the flight and the hand blast, both of which live in the item / its flight
	 * handler rather than in this material.
	 */
	public static final Holder<ArmorMaterial> REPULSOR_BOOTS = registerNoLayers("repulsor_boots",
			Map.of(ArmorItem.Type.BOOTS, 1), 8, SoundEvents.ARMOR_EQUIP_IRON, 0.0f, 0.0f, Items.IRON_INGOT);

	private IronManArmorMaterials() {
	}

	public static void initialize() {
	}

	private static Holder<ArmorMaterial> register(String name, Map<ArmorItem.Type, Integer> defense, int enchantmentValue,
			Holder<SoundEvent> equipSound, float toughness, float knockbackResistance) {
		return register(name, defense, enchantmentValue, equipSound, toughness, knockbackResistance, Items.NETHERITE_INGOT);
	}

	private static Holder<ArmorMaterial> register(String name, Map<ArmorItem.Type, Integer> defense, int enchantmentValue,
			Holder<SoundEvent> equipSound, float toughness, float knockbackResistance, net.minecraft.world.item.Item repairItem) {
		return register(name, defense, enchantmentValue, equipSound, toughness, knockbackResistance, repairItem,
				List.of(new ArmorMaterial.Layer(ProjectHeroMod.id(name))));
	}

	/** A material that draws no armour layer on the player at all (see {@link #REPULSOR_BOOTS}). */
	private static Holder<ArmorMaterial> registerNoLayers(String name, Map<ArmorItem.Type, Integer> defense,
			int enchantmentValue, Holder<SoundEvent> equipSound, float toughness, float knockbackResistance,
			net.minecraft.world.item.Item repairItem) {
		return register(name, defense, enchantmentValue, equipSound, toughness, knockbackResistance, repairItem,
				List.of());
	}

	private static Holder<ArmorMaterial> register(String name, Map<ArmorItem.Type, Integer> defense, int enchantmentValue,
			Holder<SoundEvent> equipSound, float toughness, float knockbackResistance,
			net.minecraft.world.item.Item repairItem, List<ArmorMaterial.Layer> layers) {
		ArmorMaterial material = new ArmorMaterial(defense, enchantmentValue, equipSound,
				() -> Ingredient.of(repairItem), layers, toughness, knockbackResistance);
		ResourceKey<ArmorMaterial> key = ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id(name));
		return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, key, material);
	}
}
