package com.projecthero.mod.moonknight.temple;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Scarab of Khonshu: a golden scarab with a silver crescent on its back. Loot-only -- exactly one lies in the
 * hidden chamber of every Temple of Khonshu ({@code loot_table/chests/temple_of_khonshu.json}); it has no recipe and
 * is in no other loot table. Laid on the temple's {@link KhonshuAltarBlock} at night it begins the pact ritual
 * ({@link KhonshuRitual}); the item itself does nothing else.
 */
public class ScarabOfKhonshuItem extends Item {
	public ScarabOfKhonshuItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.scarab_of_khonshu.desc1").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.scarab_of_khonshu.desc2").withStyle(ChatFormatting.WHITE));
		tooltip.add(Component.translatable("item.projecthero.scarab_of_khonshu.desc3")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
