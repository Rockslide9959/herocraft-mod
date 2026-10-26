package com.projecthero.mod.speedforce;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.1 (user): the Black Flash Lure -- four Speed Force Shards round a netherite ingot. Used by a Speed Force Speedster
 * running at 100% momentum, it calls the Black Flash on purpose ({@link BlackFlash#lure}): +50% health, more shards and a
 * netherite ingot when he falls, and if he wins only the ordinary drain (never the 20-minute loss). Used up. Tooltip lines
 * word-wrapped (TooltipWrap).
 */
public class BlackFlashLureItem extends Item {
	public BlackFlashLureItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer p)) {
			return InteractionResultHolder.success(stack);
		}
		if (!SpeedForceSpeedster.hasPower(p)) {
			SpeedForceSpeedster.say(p, "message.projecthero.black_flash.lure_not_speedster", ChatFormatting.GRAY);
			return InteractionResultHolder.fail(stack);
		}
		if (!SpeedForceSpeedster.speedActive(p) || SpeedForceSpeedster.momentum(p) < SpeedForceConfig.LURE_MOMENTUM) {
			SpeedForceSpeedster.say(p, "message.projecthero.black_flash.lure_need_speed", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}
		if (BlackFlash.lure(p) == null) {
			SpeedForceSpeedster.say(p, "message.projecthero.black_flash.lure_busy", ChatFormatting.GRAY);
			return InteractionResultHolder.fail(stack);
		}
		SpeedForceSpeedster.sound(p, SoundEvents.WITHER_SPAWN, 0.6f, 1.6f);
		p.displayClientMessage(Component.translatable("message.projecthero.black_flash.lured").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
		stack.consume(1, p);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.black_flash_lure.hint").withStyle(ChatFormatting.DARK_RED));
		tooltip.add(Component.translatable("item.projecthero.black_flash_lure.hint2").withStyle(ChatFormatting.GRAY));
	}
}
