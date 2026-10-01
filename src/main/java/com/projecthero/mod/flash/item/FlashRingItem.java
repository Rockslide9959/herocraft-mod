package com.projecthero.mod.flash.item;

import java.util.List;

import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * v0.14.11: the Flash Ring -- the Flash Suit compressed into a gold ring (the pieces ride in its
 * {@link DataComponents#CONTAINER}). It is normally worn on the finger (the {@code FLASH_RING} attachment, not an
 * inventory slot); this item form only exists when it comes off -- dropped on death, or handed back when the wearer
 * loses Super Speed. Right-click puts it back on (speedsters only); H then lets the suit out.
 */
public class FlashRingItem extends Item {
	public FlashRingItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!FlashSuit.mayWear(player)) {
			if (!level.isClientSide()) {
				FlashSuit.refuse(player);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		return FlashRing.putOn((ServerPlayer) player, stack)
				? InteractionResultHolder.consume(ItemStack.EMPTY)
				: InteractionResultHolder.fail(stack);
	}

	/** How many suit pieces are inside. */
	public static int pieces(ItemStack ring) {
		return (int) ring.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().count();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		int n = pieces(stack);
		tooltip.add(n > 0
				? Component.translatable("item.projecthero.flash_ring.holds", n).withStyle(ChatFormatting.GOLD)
				: Component.translatable("item.projecthero.flash_ring.empty").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.flash_ring.tooltip").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return pieces(stack) > 0;
	}
}
