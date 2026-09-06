package com.projecthero.mod.maxsteel.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The T.U.R.B.O. Stabilizer -- the alternative to reaching Experience Level 30 for bonding with Steel.
 * Holding one (or having it anywhere in the inventory) lets a below-level-30 player bond; the bond
 * consumes exactly one. It has no use of its own beyond that, so it does not need any interaction
 * behaviour here -- {@link com.projecthero.mod.maxsteel.entity.SteelEntity}'s right-click handler checks
 * for and consumes it.
 */
public class TurboStabilizerItem extends Item {
	public TurboStabilizerItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.turbo_stabilizer.tip1").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.turbo_stabilizer.tip2").withStyle(ChatFormatting.DARK_AQUA));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
