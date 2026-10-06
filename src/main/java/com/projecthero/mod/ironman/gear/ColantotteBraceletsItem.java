package com.projecthero.mod.ironman.gear;

import java.util.List;

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
 * v0.15.4: the Colantotte Bracelets -- a pair of red bracelets worn in the Stark Gear slot (Shift + N, or right-click them;
 * swaps with whatever is worn there). See {@link ColantotteBracelets} for what they do and how they are handed out.
 */
public class ColantotteBraceletsItem extends Item {
	public ColantotteBraceletsItem(Properties properties) {
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
		sp.displayClientMessage(Component.translatable("message.projecthero.ironman.bracelets_on").withStyle(ChatFormatting.RED), true);
		if (!old.isEmpty()) {
			if (held.isEmpty()) {
				return InteractionResultHolder.success(old); // swap whatever was worn into the hand
			}
			if (!player.getInventory().add(old)) {
				player.drop(old, false);
			}
		}
		return InteractionResultHolder.success(held);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		// a pair replaced by a newer one issued to its owner crumbles (no spare copies, see ColantotteBracelets)
		if (!level.isClientSide && entity instanceof Player p && level.getGameTime() % 20 == 0
				&& ColantotteBracelets.retired(stack, level.getServer())) {
			stack.setCount(0);
			ColantotteBracelets.crumbled(p);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		// short lines only -- tooltips never wrap on their own
		tooltip.add(Component.translatable("item.projecthero.colantotte_bracelets.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.colantotte_bracelets.tooltip2").withStyle(ChatFormatting.RED));
		tooltip.add(Component.translatable("item.projecthero.colantotte_bracelets.tooltip3").withStyle(ChatFormatting.RED));
	}
}
