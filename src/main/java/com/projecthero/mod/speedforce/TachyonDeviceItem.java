package com.projecthero.mod.speedforce;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.1: the Tachyon Device -- a small glowing tachyon enhancer. Held (either hand) by a Speedster running in Overdrive
 * with the Speed effect on, it lets the Speed Force in when a surge hits them (Charged Copper Plates run over, or a
 * lightning strike): see {@link SpeedForceUpgrade}. It is not used up. The tooltip lines are word-wrapped (TooltipWrap).
 */
public class TachyonDeviceItem extends Item {
	public TachyonDeviceItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.tachyon_device.hint").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.tachyon_device.hint2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.tachyon_device.hint3").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
