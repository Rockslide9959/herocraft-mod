package com.projecthero.mod.stormbreaker;

import java.util.List;

import com.projecthero.mod.thoracquire.forge.StarForge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Stormbreaker Head (v0.17.7; id {@code unforged_stormbreaker}, shown as "Stormbreaker Head" -- the v0.14.19 "Unforged
 * Stormbreaker" kept its id, so old ones in inventories are simply heads). Crafted without a handle. Set it in a Star Forge,
 * a worthy Thor channels lightning into it for 10 s ({@link StarForge}), then a Living Branch finishes it into Stormbreaker.
 * A forged head (custom data {@link StarForge#FORGED_TAG}) glints and only needs the branch. Fire-resistant.
 */
public class UnforgedStormbreakerItem extends Item {
	public UnforgedStormbreakerItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return StarForge.isForged(stack);
	}

	@Override
	public Component getName(ItemStack stack) {
		return StarForge.isForged(stack) ? Component.translatable("item.projecthero.unforged_stormbreaker.forged") : super.getName(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		if (StarForge.isForged(stack)) {
			tooltip.add(Component.translatable("item.projecthero.unforged_stormbreaker.forged.line1").withStyle(ChatFormatting.GOLD));
			tooltip.add(Component.translatable("item.projecthero.unforged_stormbreaker.forged.line2").withStyle(ChatFormatting.GREEN));
			return;
		}
		tooltip.add(Component.translatable("item.projecthero.unforged_stormbreaker.line1").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.unforged_stormbreaker.line2").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.unforged_stormbreaker.line3").withStyle(ChatFormatting.DARK_GRAY));
	}
}
