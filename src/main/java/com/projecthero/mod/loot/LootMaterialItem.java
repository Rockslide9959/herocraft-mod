package com.projecthero.mod.loot;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.5: a gear-tier material (Salvaged Tech, Hero Alloy, Vibranium Shard, Celestial Core). Its rarity comes from the
 * {@link HeroRarity#COMPONENT} in its properties; the tooltip adds a "Tier N material" line and a hint
 * ({@code item.projecthero.<id>.hint}) of what it is for. Lines wrap through the client {@code TooltipWrap} guard.
 */
public class LootMaterialItem extends Item {
	private final int gearTier;

	public LootMaterialItem(int gearTier, Properties properties) {
		super(properties);
		this.gearTier = gearTier;
	}

	public int gearTier() {
		return gearTier;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("projecthero.loot.material_tier." + gearTier).withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable(getDescriptionId() + ".hint").withStyle(ChatFormatting.GRAY));
	}
}
