package com.projecthero.mod.item;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Placeholder "Thor" armor material -- roughly netherite-tier defense/toughness so the suit feels
 * powerful. Tune freely once the real balance pass happens.
 */
public final class ModArmorMaterials {
	public static final Holder<ArmorMaterial> THOR = register("thor",
			Map.of(
					ArmorItem.Type.BOOTS, 3,
					ArmorItem.Type.LEGGINGS, 6,
					ArmorItem.Type.CHESTPLATE, 8,
					ArmorItem.Type.HELMET, 3),
			15,
			SoundEvents.ARMOR_EQUIP_NETHERITE,
			3.0f,
			0.1f,
			() -> Ingredient.of(Items.NETHERITE_INGOT));

	/**
	 * The Max Steel suit's raw {@code ArmorMaterial} defence -- what protects the player even at 0
	 * T.U.R.B.O. Energy. v0.9.2: <b>diamond level</b> (user request) -- full diamond protection
	 * (3/6/8/3 = 20), diamond toughness (2.0), no knockback resistance from the plate itself (Base Mode
	 * grants a little via an attribute; Strength Mode adds Resistance I on top). There is no longer a
	 * flat Base-Mode damage reduction in the damage hook. The suit is never crafted, so the repair
	 * ingredient is nominal, and the fallback vanilla armour texture reuses Thor's layer (GeckoLib
	 * renders the real model, so the flat layer is only ever seen if GeckoLib rendering itself fails).
	 */
	public static final Holder<ArmorMaterial> MAX_STEEL = registerWithLayer("max_steel", "thor",
			Map.of(
					ArmorItem.Type.BOOTS, 3,
					ArmorItem.Type.LEGGINGS, 6,
					ArmorItem.Type.CHESTPLATE, 8,
					ArmorItem.Type.HELMET, 3),
			15,
			SoundEvents.ARMOR_EQUIP_DIAMOND,
			2.0f,
			0.0f,
			() -> Ingredient.of(Items.DIAMOND));

	/**
	 * The craftable Spider-Man Suit (v0.6.18). A light cloth suit -- roughly chainmail-tier defence,
	 * no toughness -- worn for the look; the GeckoLib model {@code geo/spider_man.geo.json} is the
	 * point. Reuses Thor's flat fallback layer (never seen -- GeckoLib renders the real model).
	 */
	public static final Holder<ArmorMaterial> SPIDER_MAN = registerWithLayer("spider_man", "thor",
			Map.of(
					ArmorItem.Type.BOOTS, 2,
					ArmorItem.Type.LEGGINGS, 4,
					ArmorItem.Type.CHESTPLATE, 5,
					ArmorItem.Type.HELMET, 2),
			12,
			SoundEvents.ARMOR_EQUIP_LEATHER,
			0.0f,
			0.0f,
			() -> Ingredient.of(Items.STRING));

	/**
	 * The black Symbiote suit (v0.9.10) -- Spider-Man's toggleable upgrade. Deliberately a notch above
	 * full diamond: 4 / 7 / 9 / 4 armour (= 24, vs diamond's 20) and 3.0 toughness (vs 2.0), i.e.
	 * ~20-30% more effective protection than an ordinary Hero-Class Spider-Man, without approaching
	 * invulnerability. The suit is synthesised (never crafted), so the repair ingredient is nominal;
	 * the flat fallback layer reuses Thor's (never seen -- GeckoLib renders {@code geo/spider_man_symbiote.geo.json}).
	 * The extra defence only exists while the suit is worn, and the suit is only worn while the Symbiote
	 * is active -- so toggling it off returns the player to their exact normal values with nothing
	 * stacked.
	 */
	public static final Holder<ArmorMaterial> SYMBIOTE = registerWithLayer("spider_man_symbiote", "thor",
			Map.of(
					ArmorItem.Type.BOOTS, 4,
					ArmorItem.Type.LEGGINGS, 7,
					ArmorItem.Type.CHESTPLATE, 9,
					ArmorItem.Type.HELMET, 4),
			15,
			SoundEvents.ARMOR_EQUIP_NETHERITE,
			3.0f,
			0.0f,
			() -> Ingredient.of(Items.STRING));

	/**
	 * The Punisher's tactical armour (chest / legs / boots -- no helmet). v0.9.5: <b>diamond level</b>
	 * (user request) -- 9 / 7 / 4 armour, <b>3.0 toughness</b>, <b>no knockback resistance</b> from the
	 * plate itself, diamond equip sound + diamond repair. Per-piece durabilities (680 / 600 / 529) are
	 * set on the items in {@code PunisherItems}. The full-set bonus in
	 * {@link com.projecthero.mod.punisher.PunisherArmorSet} still stacks projectile reduction + a little
	 * knockback resist on top while the player holds the power. Renders through the shared GeckoLib
	 * armour path ({@code geo/punisher.geo.json}); the flat fallback layer reuses Thor's (never seen).
	 */
	public static final Holder<ArmorMaterial> PUNISHER = registerWithLayer("punisher", "thor",
			Map.of(
					ArmorItem.Type.BOOTS, 4,
					ArmorItem.Type.LEGGINGS, 7,
					ArmorItem.Type.CHESTPLATE, 9,
					ArmorItem.Type.HELMET, 3),
			15,
			SoundEvents.ARMOR_EQUIP_DIAMOND,
			3.0f,
			0.0f,
			() -> Ingredient.of(Items.DIAMOND));

	/**
	 * The Normal Symbiote Host's armour (v0.9.14) -- a non-Spider-Man bonded player's "living black
	 * diamond/iron armour". Renders through the shared GeckoLib armour path on its own bespoke
	 * {@code geo/symbiote_host.geo.json}, converted from a user-supplied Blockbench model (see
	 * {@code SymbioteHostArmorItem}); {@code symbiote_host_layer_1/2.png} are the old vanilla-layer
	 * fallback textures from the v0.9.19-v0.9.23 fully-transparent placeholder era and are no longer
	 * read by anything (GeckoLib doesn't use vanilla armor-layer textures at all), kept on disk only as
	 * art reference like every other converted set's old layer textures.
	 * Defence (3/6/5/3 = 17, toughness 1.0) is deliberately the midpoint between iron (15, toughness 0)
	 * and diamond (20, toughness 2) per the design spec -- stronger than iron, weaker than diamond, not
	 * a copy of either, and untouched by the texture change. Never crafted (synthesised by
	 * {@code SymbioteSuit} on suit-up, curse-locked, no durability -> unbreakable while bonded), so the
	 * repair ingredient is nominal.
	 */
	public static final Holder<ArmorMaterial> SYMBIOTE_HOST = registerWithLayer("symbiote_host", "symbiote_host",
			Map.of(
					ArmorItem.Type.BOOTS, 3,
					ArmorItem.Type.LEGGINGS, 5,
					ArmorItem.Type.CHESTPLATE, 6,
					ArmorItem.Type.HELMET, 3),
			15,
			SoundEvents.ARMOR_EQUIP_NETHERITE,
			1.0f,
			0.0f,
			() -> Ingredient.of(Items.STRING));

	/**
	 * The Green Lantern suit's raw {@code ArmorMaterial} defence -- the ring's construct shell around
	 * the wearer. v0.11.4 (user request): <b>diamond level</b> -- full diamond protection (3/6/8/3 = 20),
	 * diamond toughness (2.0), no knockback resistance from the plate itself, mirroring
	 * {@link #MAX_STEEL}'s own diamond-parity material exactly. Was a bespoke 18/6.0/0.20 hard-light
	 * material before this pass. Never crafted (synthesised on Suit Up), so the repair ingredient is
	 * nominal. Reuses Thor's flat fallback layer (never seen -- GeckoLib renders the real model).
	 */
	public static final Holder<ArmorMaterial> GREEN_LANTERN = registerWithLayer("green_lantern", "thor",
			Map.of(
					ArmorItem.Type.BOOTS, 3,
					ArmorItem.Type.LEGGINGS, 6,
					ArmorItem.Type.CHESTPLATE, 8,
					ArmorItem.Type.HELMET, 3),
			15,
			SoundEvents.ARMOR_EQUIP_DIAMOND,
			2.0f,
			0.0f,
			() -> Ingredient.of(Items.EMERALD));

	/** The craftable Wolverine Suit (v0.12.14): leather-level cloth costume; GeckoLib renders the real model. */
	public static final Holder<ArmorMaterial> WOLVERINE = registerWithLayer("wolverine", "thor",
			Map.of(
					ArmorItem.Type.BOOTS, 1,
					ArmorItem.Type.LEGGINGS, 2,
					ArmorItem.Type.CHESTPLATE, 3,
					ArmorItem.Type.HELMET, 1),
			15,
			SoundEvents.ARMOR_EQUIP_LEATHER,
			0.0f,
			0.0f,
			() -> Ingredient.of(Items.LEATHER));

	private ModArmorMaterials() {
	}

	public static void initialize() {
	}

	private static Holder<ArmorMaterial> register(String name, Map<ArmorItem.Type, Integer> defense, int enchantmentValue,
			Holder<net.minecraft.sounds.SoundEvent> equipSound, float toughness, float knockbackResistance,
			java.util.function.Supplier<Ingredient> repairIngredient) {
		return registerWithLayer(name, name, defense, enchantmentValue, equipSound, toughness,
				knockbackResistance, repairIngredient);
	}

	private static Holder<ArmorMaterial> registerWithLayer(String name, String layerName,
			Map<ArmorItem.Type, Integer> defense, int enchantmentValue,
			Holder<net.minecraft.sounds.SoundEvent> equipSound, float toughness, float knockbackResistance,
			java.util.function.Supplier<Ingredient> repairIngredient) {
		ArmorMaterial material = new ArmorMaterial(
				defense,
				enchantmentValue,
				equipSound,
				repairIngredient,
				List.of(new ArmorMaterial.Layer(ProjectHeroMod.id(layerName))),
				toughness,
				knockbackResistance);
		ResourceKey<ArmorMaterial> key = ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id(name));
		return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, key, material);
	}
}
