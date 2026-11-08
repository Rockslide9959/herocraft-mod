package com.projecthero.mod.andy.item;

import java.util.List;

import com.projecthero.mod.andy.Andy;

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
 * v0.17.3: the Undead Card -- the card from Andy's forehead, found rarely in ancient city and stronghold chests. Right-click:
 * it sinks into your forehead and you become the Undead (the Hero-Tier Andy power, replacing whatever Primary you held).
 * Used up.
 */
public class UndeadCardItem extends Item {
	public UndeadCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (Andy.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.andy.already").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (player instanceof ServerPlayer sp) {
			if (!Andy.grant(sp)) {
				return InteractionResultHolder.fail(stack);
			}
			if (!sp.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.undead_card.tooltip").withStyle(ChatFormatting.DARK_RED));
		tooltip.add(Component.translatable("item.projecthero.undead_card.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
