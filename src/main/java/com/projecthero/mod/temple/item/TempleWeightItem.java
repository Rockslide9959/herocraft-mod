package com.projecthero.mod.temple.item;

import java.util.List;

import com.projecthero.mod.temple.block.WeightAltarBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.7: a <b>temple weight</b> -- a carved stone weight from the Hall of Trials' urns (Feather 1, Ibis 2, Jackal 3,
 * Lion 4). Laid on the Weight Altar ({@link com.projecthero.mod.temple.block.WeightAltarBlock}); the scale balances at
 * {@link WeightAltarBlockEntity#TARGET}.
 */
public class TempleWeightItem extends Item {
	private final int weight;

	public TempleWeightItem(int weight, Properties props) {
		super(props);
		this.weight = weight;
	}

	public int weight() {
		return weight;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("item.projecthero.temple_weight.value", weight).withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("item.projecthero.temple_weight.hint", WeightAltarBlockEntity.TARGET).withStyle(ChatFormatting.GRAY));
	}
}
