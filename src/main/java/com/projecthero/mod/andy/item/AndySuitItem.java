package com.projecthero.mod.andy.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.andy.Andy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.3 (user: "Add this skin as Andy's self healing suit, make this suit regenerate durability slowly and auto feed him ...
 * give it iron level armour protection"): one piece of Andy's Suit. Iron-level (2 / 6 / 5 / 2, iron durability, iron
 * enchantability, repaired with iron). Worn, every piece knits {@link #REPAIR_AMOUNT} durability every {@link #REPAIR_EVERY}
 * ticks, never past whole (v0.17.3: it no longer feeds him -- the regrowth is free). Drawn from the
 * user's skin ({@link #texture}) by the skin-costume path ({@code QuirkCostume}); the vanilla armour model is never drawn.
 */
public class AndySuitItem extends ArmorItem {
	public static final int REPAIR_EVERY = 40;
	public static final int REPAIR_AMOUNT = 1;
	public static final ResourceLocation WIDE = ProjectHeroMod.id("textures/entity/andy/andy_suit_wide.png");
	public static final ResourceLocation SLIM = ProjectHeroMod.id("textures/entity/andy/andy_suit_slim.png");

	public AndySuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	/** The suit's 64x64 skin for the wearer's arm width. */
	public static ResourceLocation texture(boolean slim) {
		return slim ? SLIM : WIDE;
	}

	public static boolean wornIn(LivingEntity e, EquipmentSlot slot) {
		return e.getItemBySlot(slot).getItem() instanceof AndySuitItem;
	}

	public static boolean anyWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.HEAD) || wornIn(e, EquipmentSlot.CHEST) || wornIn(e, EquipmentSlot.LEGS) || wornIn(e, EquipmentSlot.FEET);
	}


	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, level, entity, slot, selected);
		if (level.isClientSide || !(entity instanceof LivingEntity wearer)) {
			return;
		}
		if (level.getGameTime() % REPAIR_EVERY == 0) {
			mend(stack, wearer);
		}
	}

	/** One step of the self-repair, only while worn: the piece in its own armour slot (not a spare in the inventory). */
	public static boolean mend(ItemStack stack, LivingEntity wearer) {
		if (!(stack.getItem() instanceof AndySuitItem suit) || wearer.getItemBySlot(suit.getEquipmentSlot()) != stack) {
			return false;
		}
		knit(stack);
		return true;
	}

	/** One step of the self-repair (public for the tests). */
	public static void knit(ItemStack stack) {
		if (stack.isDamaged()) {
			stack.setDamageValue(Math.max(0, stack.getDamageValue() - REPAIR_AMOUNT));
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.andy_suit.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
