package com.projecthero.mod.speedforce;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.1: the Speed Force Tachyon Device -- a Tachyon Device with a Speed Force Shard (the Black Flash's drop) set in it.
 * Held in either hand by a Speed Force Speedster: +{@link SpeedForceConfig#SHARD_DEVICE_METER_BONUS} Speed Force meter
 * ({@code SpeedForceSpeedster#meterMax}). It still counts as a Tachyon Device for the awakening.
 */
public class SpeedForceTachyonDeviceItem extends TachyonDeviceItem {
	public SpeedForceTachyonDeviceItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.speed_force_tachyon_device.hint").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.speed_force_tachyon_device.hint2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.tachyon_device.hint3").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
