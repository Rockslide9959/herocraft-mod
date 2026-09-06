package com.projecthero.mod.grave.item;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Corrupted Power Core: the crystallised remains of a Powered Zombie Boss's Experimental Power.
 *
 * <p>The core <b>remembers which power</b> its boss had ({@link GraveComponents#POWER_KEY}), which is
 * what makes "Corrupted Geokinesis Core" a distinct object from "Corrupted Laser Vision Core" without
 * fourteen item registrations -- one item, a component, and a name built from the power's own
 * translation key. That also means a power added to the boss roster later automatically gets a
 * correctly named core with no new item.
 *
 * <p>It is deliberately <b>not</b> consumable. Eating a core to gain the power would collapse the
 * entire mutation progression -- serum, exposure event, research, capacity limit -- into "kill a
 * boss", so a core has no use action at all. It is a crafting and research ingredient: valuable, inert
 * on its own, and architected so power research, experimental upgrades and superhero technology can
 * all consume it later without changing the item.
 */
public class CorruptedPowerCoreItem extends Item {
	public CorruptedPowerCoreItem(Properties properties) {
		super(properties);
	}

	/** @return a core stamped with {@code powerKey}. */
	public static ItemStack of(Item item, String powerKey) {
		ItemStack stack = new ItemStack(item);
		stack.set(GraveComponents.POWER_KEY, powerKey);
		return stack;
	}

	public static String powerKey(ItemStack stack) {
		String key = stack.get(GraveComponents.POWER_KEY);
		return key == null ? "" : key;
	}

	@Override
	public Component getName(ItemStack stack) {
		String key = powerKey(stack);
		if (key.isEmpty()) {
			return super.getName(stack);
		}
		return Component.translatable("item.projecthero.corrupted_power_core.named", BossPowers.displayName(key));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		String key = powerKey(stack);
		if (!key.isEmpty()) {
			tooltip.add(Component.translatable("item.projecthero.corrupted_power_core.power",
					BossPowers.displayName(key)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		tooltip.add(Component.translatable("item.projecthero.corrupted_power_core.hint")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
