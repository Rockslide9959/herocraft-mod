package com.projecthero.mod.flash.item;

import java.util.List;

import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.flash.FlashSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.14.11: a piece of the craftable Flash Suit -- the user's {@code flash.bbmodel} on the shared GeckoLib armour path
 * ({@code geo/flash.geo.json} over {@code textures/armor/flash.png}). Only a speedster (Super Speed) can wear it, and H
 * packs whatever pieces are worn into the Flash Ring: see {@link FlashSuit} and {@link com.projecthero.mod.flash.FlashRing}.
 */
public class FlashSuitItem extends SuperheroArmorItem {
	public FlashSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return FlashSuit.SET_ID;
	}

	/** Right-click to wear it: only a speedster (anyone else is told why). */
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!FlashSuit.mayWear(player)) {
			if (!level.isClientSide()) {
				FlashSuit.refuse(player);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return super.use(level, player, hand);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.flash_suit.tooltip").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.flash_suit.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
