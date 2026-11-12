package com.projecthero.mod.raven;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * v0.17.3 Raven: her items.
 * <ul>
 *   <li>{@link #BOOK_OF_AZARATH} ({@code projecthero:book_of_azarath}): read it at night to become Raven ({@link RavenLearning}). Rare in
 *   ancient city and stronghold library chests.</li>
 *   <li>{@link #CLOAK} ({@code projecthero:raven_cloak}): the craftable costume, a chestplate anyone can wear. It draws Raven's dark leotard
 *   (the armour layer) and the hooded cloak ({@code RavenCloakLayer}, client), whose colour follows a Raven wearer's Composure: blue,
 *   white when she is very calm, a dark red tint at Rage. Leather-strength; no powers of its own.</li>
 * </ul>
 */
public final class RavenItems {
	public static Item BOOK_OF_AZARATH;
	public static Holder<ArmorMaterial> CLOAK_MATERIAL;
	public static Item CLOAK;

	private RavenItems() {
	}

	public static void initialize() {
		if (BOOK_OF_AZARATH != null) {
			return;
		}
		BOOK_OF_AZARATH = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("book_of_azarath"),
				new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
					@Override
					public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
						tooltip.add(Component.translatable("item.projecthero.book_of_azarath.tooltip").withStyle(ChatFormatting.DARK_PURPLE));
					}

					@Override
					public boolean isFoil(ItemStack stack) {
						return true;
					}
				});
		CLOAK_MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("raven_cloak")),
				new ArmorMaterial(
						Map.of(ArmorItem.Type.BOOTS, 1, ArmorItem.Type.LEGGINGS, 2, ArmorItem.Type.CHESTPLATE, 3, ArmorItem.Type.HELMET, 1,
								ArmorItem.Type.BODY, 3),
						15,
						SoundEvents.ARMOR_EQUIP_LEATHER,
						() -> Ingredient.of(Items.BLUE_WOOL),
						List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("raven_cloak"))),
						0.0f,
						0.0f));
		CLOAK = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("raven_cloak"),
				new ArmorItem(CLOAK_MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE)
						.durability(ArmorItem.Type.CHESTPLATE.getDurability(15))) {
					@Override
					public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
						tooltip.add(Component.translatable("item.projecthero.raven_cloak.tooltip").withStyle(ChatFormatting.GRAY));
					}
				});
	}

	/** Wearing Raven's cloak: the hood and cape show. Client-safe. */
	public static boolean wearsCloak(Player player) {
		return CLOAK != null && player.getItemBySlot(EquipmentSlot.CHEST).is(CLOAK);
	}

	/** The cloak's colours: 0 = blue, 1 = white (very calm), 2 = dark red (Rage). */
	public static final int CLOAK_BLUE = 0, CLOAK_WHITE = 1, CLOAK_RED = 2;

	/** Composure at or above this turns a Raven's cloak white. */
	public static final float WHITE_ABOVE = 90f;
	/** Composure under this (Rage) tints it dark red. */
	public static final float RED_BELOW = RavenConfig.RAGE_BELOW;

	/** Which colour the cloak shows on {@code player} now: blue for anyone who isn't Raven. Client-safe. */
	public static int cloakColour(Player player) {
		if (!Raven.hasPower(player)) {
			return CLOAK_BLUE;
		}
		if (Raven.rage(player)) {
			return CLOAK_RED;
		}
		return Raven.composure(player) >= WHITE_ABOVE ? CLOAK_WHITE : CLOAK_BLUE;
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(BOOK_OF_AZARATH);
		output.accept(CLOAK);
	}
}
