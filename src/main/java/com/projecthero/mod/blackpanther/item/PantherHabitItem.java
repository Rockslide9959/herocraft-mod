package com.projecthero.mod.blackpanther.item;

import java.util.List;

import com.projecthero.mod.blackpanther.BlackPanther;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Panther Habit: v0.17.4-0.17.6's folded suit, bound into the necklace. v0.17.7 made the suit a crafted necklace
 * ({@link BlackPantherNecklaceItem}), so the habit has no recipe and is only kept so old stacks do not vanish: in a player's
 * inventory (or on use) it turns into the necklace, one for one ({@link BlackPanther#convertLegacyItems}). Not in the creative tab.
 */
public class PantherHabitItem extends Item {
	public PantherHabitItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (!level.isClientSide() && entity instanceof ServerPlayer player) {
			BlackPanther.convertLegacyItems(player);
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer sp) {
			BlackPanther.convertLegacyItems(sp);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.panther_habit.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.panther_habit.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
