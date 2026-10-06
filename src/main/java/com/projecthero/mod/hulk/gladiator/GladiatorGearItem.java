package com.projecthero.mod.hulk.gladiator;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.15.3: one piece of the Gladiator Gear. Does nothing on its own -- it goes in its own Gladiator Gear slot (tap N as
 * Banner); with all seven in, the Hulk comes out as Gladiator Hulk.
 */
public class GladiatorGearItem extends Item {
	private final int slot;

	public GladiatorGearItem(int slot, Properties properties) {
		super(properties);
		this.slot = slot;
	}

	/** Which Gladiator Gear slot this piece belongs in. */
	public int slot() {
		return slot;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.gladiator_gear.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.gladiator_gear.tooltip2").withStyle(ChatFormatting.DARK_GREEN));
	}
}
