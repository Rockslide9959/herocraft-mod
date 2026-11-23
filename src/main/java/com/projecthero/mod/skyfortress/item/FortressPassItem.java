package com.projecthero.mod.skyfortress.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.7: the <b>Fortress Pass</b> -- the Pad Sergeant's clearance chip. Hold it (either hand) and step onto the Sky
 * Fortress's Launch Pad to be flung up into the hangar. Never used up.
 */
public class FortressPassItem extends Item {
	public FortressPassItem(Properties props) {
		super(props);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("item.projecthero.fortress_pass.hint").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
