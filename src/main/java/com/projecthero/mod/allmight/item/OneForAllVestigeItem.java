package com.projecthero.mod.allmight.item;

import java.util.List;

import com.projecthero.mod.allmight.AllMight;

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

/** The Vestige of One For All: using it grants the All Might power for good. All rules live in {@link AllMight#grant}. */
public class OneForAllVestigeItem extends Item {
	public OneForAllVestigeItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		if (!AllMight.grant(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.all_might.already").withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(held);
		}
		held.shrink(1);
		return InteractionResultHolder.success(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.one_for_all_vestige.desc1").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.one_for_all_vestige.desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
