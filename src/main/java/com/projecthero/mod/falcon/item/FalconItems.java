package com.projecthero.mod.falcon.item;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * v0.17.1: the Falcon gear (all craftable, recipes in {@code data/projecthero/recipe/falcon_*.json}): the EXO-7 Wing Pack
 * (chest), the Falcon Goggles (head) and the Flight Suit leggings and boots. Iron-level protection; the flat armour layer
 * ({@code textures/models/armor/falcon_layer_1/2.png}) only shows for a piece worn without the rest of the set.
 */
public final class FalconItems {
	public static Holder<ArmorMaterial> MATERIAL;
	public static FalconGearItem GOGGLES;
	public static FalconGearItem WING_PACK;
	public static FalconGearItem LEGGINGS;
	public static FalconGearItem BOOTS;

	private FalconItems() {
	}

	public static void initialize() {
		MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("falcon")),
				new ArmorMaterial(
						Map.of(ArmorItem.Type.BOOTS, 2, ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.CHESTPLATE, 6,
								ArmorItem.Type.HELMET, 2, ArmorItem.Type.BODY, 6),
						12,
						SoundEvents.ARMOR_EQUIP_ELYTRA,
						() -> Ingredient.of(Items.IRON_INGOT),
						List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("falcon"))),
						1.0f,
						0.0f));
		GOGGLES = piece("falcon_goggles", ArmorItem.Type.HELMET, "item.projecthero.falcon_goggles.tooltip");
		WING_PACK = piece("falcon_wing_pack", ArmorItem.Type.CHESTPLATE, "item.projecthero.falcon_wing_pack.tooltip");
		LEGGINGS = piece("falcon_flight_suit_leggings", ArmorItem.Type.LEGGINGS, "item.projecthero.falcon_flight_suit.tooltip");
		BOOTS = piece("falcon_flight_suit_boots", ArmorItem.Type.BOOTS, "item.projecthero.falcon_flight_suit.tooltip");
	}

	private static FalconGearItem piece(String path, ArmorItem.Type type, String tooltip) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new FalconGearItem(MATERIAL, type, new Item.Properties().rarity(Rarity.RARE).durability(type.getDurability(20)), tooltip));
	}

	/** Appended to the {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(GOGGLES);
		output.accept(WING_PACK);
		output.accept(LEGGINGS);
		output.accept(BOOTS);
	}

	/** The item that belongs in {@code slot}, or null for a hand slot. */
	public static Item pieceFor(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> GOGGLES;
			case CHEST -> WING_PACK;
			case LEGS -> LEGGINGS;
			case FEET -> BOOTS;
			default -> null;
		};
	}

	/** Wearing all four pieces. Client-safe. */
	public static boolean fullSet(Player player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).is(GOGGLES) && player.getItemBySlot(EquipmentSlot.CHEST).is(WING_PACK)
				&& player.getItemBySlot(EquipmentSlot.LEGS).is(LEGGINGS) && player.getItemBySlot(EquipmentSlot.FEET).is(BOOTS);
	}

	/** Wearing any piece. Client-safe. */
	public static boolean anyPiece(Player player) {
		for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (player.getItemBySlot(s).getItem() instanceof FalconGearItem) {
				return true;
			}
		}
		return false;
	}

	/** Puts the whole set on (anything worn goes into the inventory, or drops). Used by the grant command / tests. */
	public static void equipFullSet(Player player) {
		for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack old = player.getItemBySlot(s);
			if (old.is(pieceFor(s))) {
				continue;
			}
			if (!old.isEmpty() && !player.getInventory().add(old.copy())) {
				player.drop(old.copy(), false);
			}
			player.setItemSlot(s, new ItemStack(pieceFor(s)));
		}
	}
}
