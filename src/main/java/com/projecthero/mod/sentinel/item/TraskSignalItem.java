package com.projecthero.mod.sentinel.item;

import java.util.List;

import com.projecthero.mod.sentinel.SentinelPurge;
import com.projecthero.mod.sentinel.SentinelPurgeEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.15.1: the Trask Signal -- a stolen Trask Industries mutant tracker. Switching it on broadcasts your power signature
 * to the Sentinel Program: a Sentinel Purge begins where you stand, and everyone nearby is drawn into it. Consumed on use
 * (not in creative); refused if a purge is already running nearby.
 */
public class TraskSignalItem extends Item {
	public TraskSignalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		SentinelPurgeEvent purge = SentinelPurge.start(server, sp.blockPosition(), sp);
		if (purge == null) {
			sp.displayClientMessage(Component.translatable("message.projecthero.trask_signal.busy").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}
		if (!sp.getAbilities().instabuild) {
			held.shrink(1);
		}
		return InteractionResultHolder.consume(held);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.trask_signal.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.trask_signal.warning").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
	}
}
