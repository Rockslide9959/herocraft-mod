package com.projecthero.mod.sentry.item;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.sentry.SentryConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * v0.17.5 rework (user: "dont put the suit on automatically when the player gets the power, make the suit craftable and make it repair
 * itself over time. Add this suit to the game as the sentry suit"): the <b>Sentry Suit</b>, two pieces -- the top (body, arms and the blue
 * cape) and the trousers (legs and boots). No helmet: the wearer's own face and hair always show. Anyone can wear it; only the Sentry gets
 * its bonus.
 *
 * <ul>
 *   <li><b>Protection</b>: diamond-level (top 8, trousers 6, toughness 2.5), diamond durability.</li>
 *   <li><b>Mends itself</b>: 1 durability every {@link SentryConfig#SUIT_REPAIR_INTERVAL} ticks, worn or anywhere in the inventory
 *   ({@link #selfRepair}); it never needs an anvil.</li>
 *   <li><b>The suit</b> ({@link #suitWorn}, top + trousers) on the Sentry: every Void gain x0.6 and the Sun fills x1.5 faster.</li>
 *   <li><b>The look</b> is drawn by {@code SentrySuitLayer} (the vanilla armour model is never drawn, {@code QuirkSuitArmourMixin}): the
 *   comics Sentry suit, fading into the Void suit when he merges with the Void; cape by {@code SentryCapeLayer}.</li>
 * </ul>
 * Crafted from gold, blue wool and hero alloy (data/projecthero/recipe/sentry_suit_*.json).
 */
public class SentrySuitItem extends ArmorItem {
	public static Holder<ArmorMaterial> MATERIAL;
	public static SentrySuitItem TOP, TROUSERS;

	public SentrySuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	static void register() {
		MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("sentry_suit")),
				new ArmorMaterial(Map.of(Type.BOOTS, 3, Type.LEGGINGS, 6, Type.CHESTPLATE, 8, Type.HELMET, 3, Type.BODY, 11), 15,
						SoundEvents.ARMOR_EQUIP_GOLD, () -> Ingredient.of(Items.GOLD_INGOT), List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("sentry_suit"))),
						2.5f, 0f));
		TOP = piece("sentry_suit_top", Type.CHESTPLATE);
		TROUSERS = piece("sentry_suit_trousers", Type.LEGGINGS);
	}

	private static SentrySuitItem piece(String path, Type type) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new SentrySuitItem(MATERIAL, type, new Properties().rarity(Rarity.EPIC).durability(type.getDurability(33)).fireResistant()));
	}

	public static boolean wornIn(LivingEntity e, EquipmentSlot slot) {
		return e != null && e.getItemBySlot(slot).getItem() instanceof SentrySuitItem;
	}

	/** The top is on (the body, the arms and the cape draw). Client-safe. */
	public static boolean topWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.CHEST);
	}

	/** The trousers are on (the legs draw). Client-safe. */
	public static boolean trousersWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.LEGS);
	}

	/** The whole suit (top + trousers): the Sentry's Void and Sun bonus. Client-safe (armour is synced to every viewer). */
	public static boolean suitWorn(LivingEntity e) {
		return topWorn(e) && trousersWorn(e);
	}

	/** Mends one point every {@link SentryConfig#SUIT_REPAIR_INTERVAL} ticks of game time. True if it mended now. */
	public static boolean selfRepair(ItemStack stack, long gameTime) {
		if (!stack.isDamaged() || gameTime % SentryConfig.SUIT_REPAIR_INTERVAL != 0L) {
			return false;
		}
		stack.setDamageValue(stack.getDamageValue() - 1);
		return true;
	}

	/** Every inventory slot ticks its item, the armour slots included. */
	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (!level.isClientSide()) {
			selfRepair(stack, level.getGameTime());
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.sentry_suit.tooltip").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.sentry_suit.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
