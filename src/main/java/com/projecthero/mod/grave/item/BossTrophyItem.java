package com.projecthero.mod.grave.item;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Powered Zombie Boss's head, named for the power it had -- "Geokinetic Zombie Head", "Laser Vision
 * Zombie Head", and so on.
 *
 * <p>Like {@link CorruptedPowerCoreItem} this is one item plus a {@link GraveComponents#POWER_KEY}
 * component rather than one item per power, so the roster can grow without new registrations. The
 * wave-12 boss drops a visually distinct variant (a separate item with its own texture and
 * {@link Rarity#EPIC}), which is the "final boss trophy" the design asks for.
 *
 * <p>These are collectibles, not equipment: no attributes, no use action. They are deliberately
 * plain items rather than placeable heads -- a placeable block would need its own block, block entity,
 * model and a way to carry the power name onto the placed block, which is a lot of surface area for a
 * decoration, and the design only asks for placement "if practical".
 */
public class BossTrophyItem extends Item {
	private final boolean finalBoss;

	public BossTrophyItem(Properties properties, boolean finalBoss) {
		super(properties);
		this.finalBoss = finalBoss;
	}

	public static ItemStack of(Item item, String powerKey) {
		ItemStack stack = new ItemStack(item);
		stack.set(GraveComponents.POWER_KEY, powerKey);
		return stack;
	}

	@Override
	public Component getName(ItemStack stack) {
		String key = stack.get(GraveComponents.POWER_KEY);
		if (key == null || key.isEmpty()) {
			return super.getName(stack);
		}
		return Component.translatable(finalBoss
				? "item.projecthero.final_boss_trophy.named"
				: "item.projecthero.boss_trophy.named", BossPowers.displayName(key));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.boss_trophy.hint")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return finalBoss;
	}
}
