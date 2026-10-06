package com.projecthero.mod.ironman.gear;

import java.util.List;

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
 * v0.15.1: Tony Stark's tinted Stark Glasses. Worn in the Stark Gear slot ({@link StarkGear}) -- put on from the Stark
 * Gear screen (Sneak + N) or by right-clicking them (swaps with whatever pair is already on). See {@link StarkGear} for
 * what they do.
 */
public class StarkGlassesItem extends Item {
	public StarkGlassesItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
		}
		ItemStack old = StarkGear.equip(sp, held);
		held.shrink(1);
		sp.displayClientMessage(Component.translatable("message.projecthero.ironman.glasses_on").withStyle(ChatFormatting.AQUA), true);
		if (!old.isEmpty()) {
			if (held.isEmpty()) {
				return InteractionResultHolder.success(old); // swap the old pair into the hand
			}
			if (!player.getInventory().add(old)) {
				player.drop(old, false);
			}
		}
		return InteractionResultHolder.success(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.stark_glasses.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.stark_glasses.tooltip2").withStyle(ChatFormatting.DARK_AQUA));
	}
}
