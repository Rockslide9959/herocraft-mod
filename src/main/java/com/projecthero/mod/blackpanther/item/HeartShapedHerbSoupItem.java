package com.projecthero.mod.blackpanther.item;

import java.util.List;

import com.projecthero.mod.blackpanther.BlackPanther;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.3: Heart-Shaped Herb Soup (bowl + 3 herbs). Drink it and the Ancestral Plane opens; the Black Panther power follows
 * ({@link BlackPanther#drink}). Refused before drinking if the player can't take the power, so a refusal costs nothing.
 */
public class HeartShapedHerbSoupItem extends Item {
	public HeartShapedHerbSoupItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp && !BlackPanther.canDrink(sp)) {
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack result = super.finishUsingItem(stack, level, entity);
		if (entity instanceof ServerPlayer sp && BlackPanther.canDrink(sp)) {
			BlackPanther.drink(sp);
		}
		return result;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.heart_shaped_herb_soup.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.heart_shaped_herb_soup.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
