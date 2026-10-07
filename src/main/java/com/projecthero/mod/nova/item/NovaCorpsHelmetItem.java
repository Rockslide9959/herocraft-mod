package com.projecthero.mod.nova.item;

import java.util.List;

import com.projecthero.mod.nova.Nova;

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
 * v0.15.13: the Nova Corps Helmet the dying Centurion hands over. Using it (right-click) puts the Nova Force into the
 * player -- the Hero-Tier Nova power, which replaces whatever Primary power they held -- and the helmet is used up (it
 * becomes the uniform, which H then puts on and takes off). Someone who already carries the Nova Force keeps it.
 */
public class NovaCorpsHelmetItem extends Item {
	public NovaCorpsHelmetItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (Nova.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.nova_helmet.already").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (player instanceof ServerPlayer sp) {
			if (!bond(sp, stack)) {
				return InteractionResultHolder.fail(stack);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** Grants Nova and uses the helmet up (kept in creative). True if the player became Nova. */
	public static boolean bond(ServerPlayer player, ItemStack stack) {
		if (!Nova.grant(player)) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.nova_corps_helmet.tooltip").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.nova_corps_helmet.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
