package com.projecthero.mod.wolverine.item;

import java.util.List;

import com.projecthero.mod.wolverine.Wolverine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Wolverine Serum: the one way to become Wolverine. It does not create a healing factor, it
 * <em>ascends</em> one -- using it without Super Regeneration does nothing and consumes nothing. All
 * of the rules live in {@link Wolverine#ascendFromSuperRegeneration}; this class only owns the item.
 */
public class WolverineSerumItem extends Item {
	public WolverineSerumItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		if (Wolverine.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.wolverine_serum.already")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(held);
		}
		if (!Wolverine.hasSuperRegeneration(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.wolverine_serum.no_regeneration")
					.withStyle(ChatFormatting.RED), true);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BREWING_STAND_BREW,
					SoundSource.PLAYERS, 0.6f, 0.6f);
			return InteractionResultHolder.fail(held);
		}
		if (!Wolverine.ascendFromSuperRegeneration(sp)) {
			return InteractionResultHolder.fail(held);
		}
		held.shrink(1);
		return InteractionResultHolder.success(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.wolverine_serum.desc1")
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.wolverine_serum.desc2")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
