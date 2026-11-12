package com.projecthero.mod.starfire.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** v0.17.3 Starfire: the Tamaranean Starstone. Its right-click is {@code StarfireLearning}'s; it shimmers like an enchanted item. */
public class StarstoneItem extends Item {
	public StarstoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.tamaranean_starstone.tooltip").withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.translatable("item.projecthero.tamaranean_starstone.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
