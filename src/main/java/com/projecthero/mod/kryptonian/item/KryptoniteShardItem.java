package com.projecthero.mod.kryptonian.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** v0.14.8: a shard of kryptonite (from kryptonite ore). Carried, held or dropped near a Kryptonian, it weakens him. */
public class KryptoniteShardItem extends Item {
	public KryptoniteShardItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.kryptonite_shard.tooltip").withStyle(ChatFormatting.GREEN));
	}
}
