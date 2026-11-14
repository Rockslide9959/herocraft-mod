package com.projecthero.mod.blackpanther.item;

import java.util.List;

import com.projecthero.mod.blackpanther.BlackPanther;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.4 (user: "make his suit craftable"): the Panther Habit -- the vibranium-weave suit, folded into a fang pendant. Use it as
 * the Black Panther and it flows into his necklace ({@link BlackPanther#bindHabit}); from then on H runs the suit out and the
 * keys switch to the suited moveset. It stays with the power through death; losing the power hands it back as this item.
 */
public class PantherHabitItem extends Item {
	public PantherHabitItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			return BlackPanther.hasPower(player) && !BlackPanther.hasHabit(player) ? InteractionResultHolder.success(stack)
					: InteractionResultHolder.pass(stack);
		}
		if (!BlackPanther.bindHabit(sp)) {
			return InteractionResultHolder.fail(stack);
		}
		stack.consume(1, player);
		return InteractionResultHolder.success(stack);
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
