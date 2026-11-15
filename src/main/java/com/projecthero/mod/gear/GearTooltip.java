package com.projecthero.mod.gear;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** v0.17.5 gear: the shared tooltip lines ("Tier N gear" + a hint). Long lines wrap through the client TooltipWrap guard. */
public final class GearTooltip {
	private GearTooltip() {
	}

	public static void add(List<Component> tooltip, int tier, String hintKey) {
		tooltip.add(Component.translatable("projecthero.gear.tier." + tier).withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable(hintKey).withStyle(ChatFormatting.GRAY));
	}
}
