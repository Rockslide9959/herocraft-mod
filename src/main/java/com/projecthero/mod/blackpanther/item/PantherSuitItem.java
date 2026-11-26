package com.projecthero.mod.blackpanther.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.7 (user: "make the suit craftable instead please like the flash suit and you need to equip it"): one piece of the Black
 * Panther suit. Plain armour (12 armour and 4 toughness over the set, no durability: vibranium does not wear out); anyone can wear
 * it, but only the Black Panther wearing all four pieces gets the suited moveset ({@code BlackPanther.syncSuit}). The worn look is
 * the suit skin itself: the armour layer is the skin's own texture, and once the full set runs the suit-up the suit layer draws
 * over the body and hides the armour ({@code HumanoidArmorLayerMixin}).
 */
public class PantherSuitItem extends ArmorItem {
	public PantherSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.black_panther_suit.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.black_panther_suit.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
