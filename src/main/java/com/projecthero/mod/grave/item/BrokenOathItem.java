package com.projecthero.mod.grave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** v0.14.23: the Oathbreaker's trophy -- the smithing template for an Oathbound Necrotic Blade ({@link OathboundBladeRecipe}). */
public class BrokenOathItem extends Item {
	public BrokenOathItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.broken_oath.tip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.broken_oath.tip2").withStyle(ChatFormatting.DARK_GRAY));
	}
}
