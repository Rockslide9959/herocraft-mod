package com.herocraft.mod.ironman.item;

import java.util.List;
import java.util.Map;

import com.herocraft.mod.HeroCraftMod;

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
 * {@code assets/herocraft/textures/models/armor/<name>_layer_1.png} (+ {@code _layer_2} for leggings).
 * Placeholder textures ship now; drop real ones in at those paths with no code change.
 */
public final class IronManArmorMaterials {
	// Mark 1 / Mark 2 ("changes 12"): primitive first-generation prototypes, repaired with plain iron
	// rather than netherite -- deliberately lower defense than every Fabricator-built mark.
	public static final Holder<ArmorMaterial> MARK_1 = register("mark_1",
			Map.of(ArmorItem.Type.BOOTS, 2, ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.CHESTPLATE, 6, ArmorItem.Type.HELMET, 2),
			8, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f, Items.IRON_INGOT);
	public static final Holder<ArmorMaterial> MARK_2 = register("mark_2",
			Map.of(ArmorItem.Type.BOOTS, 2, ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.CHESTPLATE, 6, ArmorItem.Type.HELMET, 2),
			10, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f, Items.IRON_INGOT);
	// Mark 4 ("changes 15"): the strongest of the craftable "movie early-marks" line -- defence on par
	// with the Mark III, still repaired with iron like the rest of the primitive line.
	public static final Holder<ArmorMaterial> MARK_4 = register("mark_4",
			Map.of(ArmorItem.Type.BOOTS, 3, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.CHESTPLATE, 8, ArmorItem.Type.HELMET, 3),
			14, SoundEvents.ARMOR_EQUIP_IRON, 2.0f, 0.0f, Items.IRON_INGOT);
	// Mark 6 ("changes 16"): a hair tougher than the Mark 4, still iron-repaired.
	public static final Holder<ArmorMaterial> MARK_6 = register("mark_6",
			Map.of(ArmorItem.Type.BOOTS, 3, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.CHESTPLATE, 9, ArmorItem.Type.HELMET, 3),
			15, SoundEvents.ARMOR_EQUIP_IRON, 2.5f, 0.0f, Items.IRON_INGOT);

	public static final Holder<ArmorMaterial> MARK_III = register("mark_iii",
			Map.of(ArmorItem.Type.BOOTS, 3, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.CHESTPLATE, 8, ArmorItem.Type.HELMET, 3),
			12, SoundEvents.ARMOR_EQUIP_IRON, 2.0f, 0.0f);
	public static final Holder<ArmorMaterial> MARK_V = register("mark_v",
			Map.of(ArmorItem.Type.BOOTS, 2, ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.CHESTPLATE, 6, ArmorItem.Type.HELMET, 2),
			12, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f);
	public static final Holder<ArmorMaterial> MARK_VII = register("mark_vii",
			Map.of(ArmorItem.Type.BOOTS, 3, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.CHESTPLATE, 9, ArmorItem.Type.HELMET, 3),
			15, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0f, 0.1f);
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
				List.of(new ArmorMaterial.Layer(HeroCraftMod.id(name))));
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
		ResourceKey<ArmorMaterial> key = ResourceKey.create(Registries.ARMOR_MATERIAL, HeroCraftMod.id(name));
		return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, key, material);
	}
}
