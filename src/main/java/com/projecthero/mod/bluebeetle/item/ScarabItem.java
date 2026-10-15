package com.projecthero.mod.bluebeetle.item;

import java.util.List;

import com.projecthero.mod.bluebeetle.BlueBeetle;

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
 * v0.16.5: the Scarab (Khaji-Da) -- dug out of the suspicious sand of desert pyramids. Right-click: it latches onto the
 * player's spine and bonds (the Hero-Tier Blue Beetle power, replacing whatever Primary they held). Used up.
 */
public class ScarabItem extends Item {
	public ScarabItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (BlueBeetle.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.scarab.already").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (player instanceof ServerPlayer sp) {
			if (!BlueBeetle.grant(sp)) {
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
		tooltip.add(Component.translatable("item.projecthero.scarab.tooltip").withStyle(ChatFormatting.BLUE));
		tooltip.add(Component.translatable("item.projecthero.scarab.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
