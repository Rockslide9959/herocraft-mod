package com.projecthero.mod.ironman.gantry;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** v0.15.4: the Stark Gantry Floor's item -- a short how-to in the tooltip (TooltipWrap word-wraps it). */
public class StarkGantryFloorItem extends BlockItem {
	public StarkGantryFloorItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("block.projecthero.stark_gantry_floor.tip1").withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("block.projecthero.stark_gantry_floor.tip2").withStyle(ChatFormatting.DARK_AQUA));
	}
}
