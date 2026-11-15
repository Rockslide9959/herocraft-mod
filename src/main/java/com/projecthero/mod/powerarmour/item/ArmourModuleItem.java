package com.projecthero.mod.powerarmour.item;

import java.util.List;

import com.projecthero.mod.powerarmour.ModuleType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** v0.17.5 Power Armour: an upgrade module, socketed into a frame's slot in the Armour Editor. */
public class ArmourModuleItem extends Item {
	private final ModuleType type;

	public ArmourModuleItem(ModuleType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	public ModuleType type() {
		return type;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("projecthero.power_armour.module.mount", Component.translatable(type.part.translationKey()),
				type.output).withStyle(ChatFormatting.DARK_AQUA));
		if (type.weapon) {
			tooltip.add(Component.translatable("projecthero.power_armour.module.weapon").withStyle(ChatFormatting.GOLD));
		}
		tooltip.add(Component.translatable(type.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
	}
}
