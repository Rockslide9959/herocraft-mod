package com.projecthero.mod.omnitrix.item;

import java.util.List;

import com.projecthero.mod.omnitrix.OmnitrixRecal;

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
 * v0.17.2 (user: "the player can right click the chip and then an option to choose to recalibrate or not will pop up, if the player
 * says no nothing happens but if they say yes then the player recalibrates"): the Recalibration Chip. Right-clicked in the main hand
 * while wearing the prototype Omnitrix (human, broken in with 25 transformations): the client opens the Yes / No screen
 * ({@link OmnitrixRecal#openConfirm}); Yes asks the server ({@link OmnitrixRecal#request}), which plays the insert -- the chip is
 * taken as it goes into the slot. Anything in the way is said on the action bar ({@link OmnitrixRecal#refusal}).
 */
public final class RecalibrationChipItem extends Item {
	public RecalibrationChipItem(Properties p) {
		super(p);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResultHolder.pass(stack);
		}
		if (level.isClientSide()) {
			if (OmnitrixRecal.refusal(player) == null) {
				OmnitrixRecal.openConfirm.accept(player);
			}
		} else if (player instanceof ServerPlayer sp) {
			OmnitrixRecal.tellRefusal(sp);
		}
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.recalibration_chip.hint").withStyle(ChatFormatting.GRAY));
	}
}
