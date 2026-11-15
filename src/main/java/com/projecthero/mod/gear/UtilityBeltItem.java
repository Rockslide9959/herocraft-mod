package com.projecthero.mod.gear;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.5 Tier II: the Utility Belt. No slot, no curio: carried anywhere in the inventory (hotbar, backpack rows or the
 * offhand) it gives every gadget one spare charge and {@link GadgetCooldowns#BELT_COOLDOWN_FACTOR} cooldowns.
 */
public class UtilityBeltItem extends Item {
	public UtilityBeltItem(Properties properties) {
		super(properties);
	}

	public static boolean carried(Player player) {
		if (GearItems.UTILITY_BELT == null) {
			return false;
		}
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(GearItems.UTILITY_BELT)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		GearTooltip.add(tooltip, 2, "item.projecthero.utility_belt.hint");
	}
}
