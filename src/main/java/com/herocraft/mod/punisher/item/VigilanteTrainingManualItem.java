package com.herocraft.mod.punisher.item;

import java.util.List;

import com.herocraft.mod.punisher.VigilanteTraining;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Vigilante Training Manual, found in an Abandoned Vigilante Safehouse. Right-click to begin
 * Vigilante Training (spec section 32) -- the objective chain that ends in the Punisher power. The
 * manual is consumed only when training actually starts.
 */
public class VigilanteTrainingManualItem extends Item {
	public VigilanteTrainingManualItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
			InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		if (VigilanteTraining.begin(sp)) {
			held.shrink(1);
			return InteractionResultHolder.consume(held);
		}
		return InteractionResultHolder.fail(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.herocraft.vigilante_training_manual.desc")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
