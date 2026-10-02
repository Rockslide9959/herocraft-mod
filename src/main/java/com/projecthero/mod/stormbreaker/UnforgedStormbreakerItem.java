package com.projecthero.mod.stormbreaker;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.14.19: the crafted, still-cold Stormbreaker. It does nothing in the hand; thrown into lava in the Nether it is
 * forged into the real thing after 10 seconds ({@link StormbreakerForge}). Fire-resistant, so lava never eats it.
 */
public class UnforgedStormbreakerItem extends Item {
	public UnforgedStormbreakerItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.unforged_stormbreaker.line1").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.unforged_stormbreaker.line2").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.unforged_stormbreaker.line3").withStyle(ChatFormatting.DARK_GRAY));
	}
}
