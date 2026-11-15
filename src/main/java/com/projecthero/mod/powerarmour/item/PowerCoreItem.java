package com.projecthero.mod.powerarmour.item;

import java.util.List;

import com.projecthero.mod.powerarmour.CoreType;
import com.projecthero.mod.powerarmour.PowerArmourAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.5 Power Armour: a power core. Its charge lives in {@link PowerArmourAttachments#CORE_CHARGE} (shown as the item bar);
 * a crafted core starts full. Slot one into a frame in the Armour Editor, recharge a spent one in a Core Charger.
 */
public class PowerCoreItem extends Item {
	private final CoreType type;

	public PowerCoreItem(CoreType type, Properties properties) {
		super(properties.stacksTo(1));
		this.type = type;
	}

	public CoreType type() {
		return type;
	}

	public static float charge(ItemStack stack) {
		if (!(stack.getItem() instanceof PowerCoreItem core)) {
			return 0f;
		}
		Float c = stack.get(PowerArmourAttachments.CORE_CHARGE);
		return c == null ? core.type.capacity : Mth.clamp(c, 0f, core.type.capacity);
	}

	public static void setCharge(ItemStack stack, float charge) {
		if (stack.getItem() instanceof PowerCoreItem core) {
			stack.set(PowerArmourAttachments.CORE_CHARGE, Mth.clamp(charge, 0f, core.type.capacity));
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13f * charge(stack) / type.capacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0x55E6FF;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("projecthero.power_armour.core.charge", Math.round(charge(stack)), Math.round(type.capacity))
				.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("projecthero.power_armour.core.output", type.output).withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable("item.projecthero." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
	}
}
