package com.projecthero.mod.falcon.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.1: one piece of the craftable Falcon gear -- the EXO-7 Wing Pack, the Falcon Goggles and the Flight Suit
 * (leggings and boots). Wearing all four makes the wearer the Falcon ({@code Falcon.tick}); any piece coming off takes the
 * power away. Worn as a full set the suit is drawn over the wearer's own skin (the client's {@code FalconLayer}); a lone
 * piece shows as flat armour.
 */
public class FalconGearItem extends ArmorItem {
	private final String tooltipKey;

	public FalconGearItem(Holder<ArmorMaterial> material, Type type, Properties properties, String tooltipKey) {
		super(material, type, properties);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.falcon_gear.set").withStyle(ChatFormatting.RED));
	}
}
