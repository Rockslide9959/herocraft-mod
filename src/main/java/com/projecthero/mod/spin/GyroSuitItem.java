package com.projecthero.mod.spin;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * v0.17.4 (user: "add his suit please: https://www.planetminecraft.com/skin/gyro-zeppeli-costume-steel-ball-run-anime-palette/ ... When
 * the suit is worn if the player has steel balls in their inventory they show up on the suit's holsters on their hip and the player
 * doesn't have to hold the balls to throw them, they automatically equip and he can just use his abilities. make spin charge 30% faster
 * when wearing the suit please and also when riding a horse"): one piece of Gyro Zeppeli's costume -- hat, coat, trousers (the holster
 * belt) and boots. Leather-level protection (1 / 3 / 2 / 1) with iron durability, repaired with leather, crafted from leather.
 *
 * <p><b>The look</b> is the costume skin ({@link #texture}, 64 x 64, wide and slim) drawn over the wearer by the skin-costume path
 * ({@code QuirkCostume}); the vanilla armour model is never drawn. Source skin: planetminecraft.com/skin/gyro-zeppeli-costume-steel-ball-
 * run-anime-palette (the user's pick). Its licence must be checked before a public release (CLAUDE.md), and until the PNG is in
 * {@code textures/entity/spin/} the suit draws nothing but the holsters (QuirkCostume draws nothing for a missing texture).
 *
 * <p><b>The suit</b> ({@link #suitWorn}) is the coat and the trousers together: then his Steel Balls work from the inventory (the
 * holsters, {@link SpinBalls#inHand}) and the Spin bar refills {@link SpinConfig#SUIT_SPIN_REGEN_BONUS} faster. The holsters on the belt
 * show while the trousers are on.
 */
public class GyroSuitItem extends ArmorItem {
	public static final ResourceLocation WIDE = ProjectHeroMod.id("textures/entity/spin/gyro_suit_wide.png");
	public static final ResourceLocation SLIM = ProjectHeroMod.id("textures/entity/spin/gyro_suit_slim.png");
	/** The skin's source page (the user's pick), for the credits / licence check before a public release. */
	public static final String SOURCE = "https://www.planetminecraft.com/skin/gyro-zeppeli-costume-steel-ball-run-anime-palette/";

	public static Holder<ArmorMaterial> MATERIAL;
	public static GyroSuitItem HAT, COAT, TROUSERS, BOOTS;

	public GyroSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	static void register() {
		// leather: 1 / 3 / 2 / 1, enchantability 15, the leather sound, repaired with leather; the vanilla layer is never drawn
		MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("gyro_suit")),
				new ArmorMaterial(Map.of(Type.BOOTS, 1, Type.LEGGINGS, 2, Type.CHESTPLATE, 3, Type.HELMET, 1, Type.BODY, 3), 15,
						SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER), List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("gyro_suit"))), 0f,
						0f));
		HAT = piece("gyro_suit_hat", Type.HELMET);
		COAT = piece("gyro_suit_coat", Type.CHESTPLATE);
		TROUSERS = piece("gyro_suit_trousers", Type.LEGGINGS);
		BOOTS = piece("gyro_suit_boots", Type.BOOTS);
	}

	private static GyroSuitItem piece(String path, Type type) {
		// iron durability (multiplier 15)
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new GyroSuitItem(MATERIAL, type, new Properties().rarity(Rarity.UNCOMMON).durability(type.getDurability(15))));
	}

	/** The costume's 64x64 skin for the wearer's arm width. */
	public static ResourceLocation texture(boolean slim) {
		return slim ? SLIM : WIDE;
	}

	public static boolean wornIn(LivingEntity e, EquipmentSlot slot) {
		return e.getItemBySlot(slot).getItem() instanceof GyroSuitItem;
	}

	public static boolean anyWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.HEAD) || wornIn(e, EquipmentSlot.CHEST) || wornIn(e, EquipmentSlot.LEGS) || wornIn(e, EquipmentSlot.FEET);
	}

	/** The suit: the coat and the holster trousers both worn. Client-safe (armour is synced to every viewer). */
	public static boolean suitWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.CHEST) && wornIn(e, EquipmentSlot.LEGS);
	}

	/** The holster belt (the trousers) is on: the balls in his inventory hang on his hips. */
	public static boolean holstersWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.LEGS);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.gyro_suit.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
