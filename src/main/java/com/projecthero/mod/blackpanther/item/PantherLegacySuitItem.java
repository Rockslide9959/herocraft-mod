package com.projecthero.mod.blackpanther.item;

import java.util.List;

import com.projecthero.mod.blackpanther.BlackPanther;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.7 legacy: one of the four Black Panther armour pieces of the first v0.17.7 build (the suit is the necklace now). The ids stay
 * registered so old stacks load; no recipe, not in the creative tab, hidden from JEI. In a player's inventory or armour slots it
 * turns into the Black Panther Necklace ({@link BlackPanther#convertLegacyItems}: one necklace per set of four, at least one, never one per piece).
 */
public class PantherLegacySuitItem extends Item {
	public PantherLegacySuitItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (!level.isClientSide() && entity instanceof ServerPlayer player) {
			BlackPanther.convertLegacyItems(player);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.black_panther_suit.legacy").withStyle(ChatFormatting.GRAY));
	}
}
