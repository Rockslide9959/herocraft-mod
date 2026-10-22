package com.projecthero.mod.quirk.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.16.7: one piece of a Quirk hero suit (see {@link QuirkSuits}). Plain iron-level armour that anyone can wear; the
 * look is the suit's costume skin on the parts this piece covers. A {@link #retired} piece (the old Ingenium helmet) only
 * exists so saves keep it: no protection, never drawn, no part of the set, salvaged back into iron.
 */
public class QuirkSuitItem extends ArmorItem {
	public final QuirkSuits.Suit suit;
	public final boolean retired;

	public QuirkSuitItem(QuirkSuits.Suit suit, Holder<ArmorMaterial> material, Type type, Properties properties, boolean retired) {
		super(material, type, properties);
		this.suit = suit;
		this.retired = retired;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (retired) {
			tooltip.add(Component.translatable("item.projecthero.quirk_suit.retired").withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("item.projecthero.quirk_suit.retired.salvage").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		tooltip.add(Component.translatable("item.projecthero.quirk_suit.tooltip").withStyle(ChatFormatting.GRAY));
		if (suit == QuirkSuits.Suit.INGENIUM) {
			tooltip.add(Component.translatable("item.projecthero.ingenium_suit.set_bonus").withStyle(ChatFormatting.AQUA));
		}
	}
}
