package com.herocraft.mod.grave.item;

import java.util.List;

import com.herocraft.mod.event.EventConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A stronger Totem of Undying: three deaths instead of one, then it is gone.
 *
 * <h2>Why the charge count lives on the stack</h2>
 * Every "prevented death" decrements {@link GraveComponents#TOTEM_CHARGES} on the exact
 * {@link ItemStack} that saved the player, and the stack is destroyed when it reaches zero. Because
 * the count travels with the item there is nothing to desynchronise: relogging, dying, storing it in a
 * chest, handing it to another player and restarting the server all preserve it, and there is no
 * external table a duplicated item could share an entry with.
 *
 * <p>The item is {@code stacksTo(1)} for the same reason -- a stack of three totems sharing one
 * charge count would be an obvious duplication route, and the vanilla totem is single-stack anyway.
 * A totem that somehow has no component at all is treated as freshly minted, so an item spawned by a
 * command or an old save still behaves correctly rather than being uncharged and useless.
 */
public class UndyingTotemItem extends Item {
	public UndyingTotemItem(Properties properties) {
		super(properties);
	}

	public static int maxCharges() {
		return Math.max(1, EventConfig.raid().undyingTotemCharges);
	}

	/**
	 * Charges remaining. An empty stack has none -- checking this first matters, because
	 * {@link #consumeCharge} shrinks the stack away on the last charge, and without the guard the
	 * component-less default below would read that spent, empty stack as a brand new totem and let it
	 * keep preventing deaths forever. A non-empty stack with no component (from {@code /give}, or an
	 * older save) is genuinely new and does default to full.
	 */
	public static int charges(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		Integer value = stack.get(GraveComponents.TOTEM_CHARGES);
		return value == null ? maxCharges() : Math.max(0, value);
	}

	public static ItemStack fresh(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(GraveComponents.TOTEM_CHARGES, maxCharges());
		return stack;
	}

	/**
	 * Spend one charge. Shrinks the stack to nothing when the last one goes.
	 *
	 * @return true if a charge was actually available and spent
	 */
	public static boolean consumeCharge(ItemStack stack) {
		int remaining = charges(stack);
		if (remaining <= 0) {
			return false;
		}
		remaining--;
		if (remaining <= 0) {
			stack.shrink(1);
		} else {
			stack.set(GraveComponents.TOTEM_CHARGES, remaining);
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.herocraft.undying_totem.charges", charges(stack), maxCharges())
				.withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.herocraft.undying_totem.hint")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
