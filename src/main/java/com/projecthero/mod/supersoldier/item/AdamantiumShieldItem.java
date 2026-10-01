package com.projecthero.mod.supersoldier.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Adamantium Shield (v0.14.9): a real shield -- it extends vanilla {@link ShieldItem}, so it raises and blocks
 * exactly like one -- that is round (red / white rings, a blue centre, a white star; drawn by the client's
 * {@code AdamantiumShieldRenderer}) and unbreakable: it has no durability, and vanilla only wears down and only lets an
 * axe disable the plain {@code minecraft:shield}. In a Super Soldier's hand it is also his best weapon: C throws it
 * (9 per hit, bouncing between up to 4 enemies) and it comes back.
 */
public class AdamantiumShieldItem extends ShieldItem {
	public AdamantiumShieldItem(Properties properties) {
		super(properties);
	}

	@Override
	public String getDescriptionId(ItemStack stack) {
		return getDescriptionId(); // never "item.projecthero.adamantium_shield.<colour>" (no banners on this one)
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.adamantium_shield.desc1").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.adamantium_shield.desc2").withStyle(ChatFormatting.BLUE));
	}
}
