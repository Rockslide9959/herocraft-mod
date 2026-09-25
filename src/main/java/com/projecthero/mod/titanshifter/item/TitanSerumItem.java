package com.projecthero.mod.titanshifter.item;

import java.util.List;

import com.projecthero.mod.titanshifter.TitanShifter;

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
 * The Titan Serum: using it unlocks Titan Shifting for good. It never transforms the player by itself (the
 * config can turn that on); the Titan Shift key does that. All rules live in {@link TitanShifter#grant}.
 */
public class TitanSerumItem extends Item {
	public TitanSerumItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		if (!TitanShifter.grant(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.titan_serum.already")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(held);
		}
		held.shrink(1);
		return InteractionResultHolder.success(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.titan_serum.desc1").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.titan_serum.desc2")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
