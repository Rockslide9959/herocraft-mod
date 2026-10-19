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
 * look is the suit's costume skin on the parts this piece covers.
 */
public class QuirkSuitItem extends ArmorItem {
	public final QuirkSuits.Suit suit;

	public QuirkSuitItem(QuirkSuits.Suit suit, Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
		this.suit = suit;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.quirk_suit.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
