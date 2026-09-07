package com.projecthero.mod.grave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Heart of the Grave. Craft it from Gravebound Ingots and a Totem of Undying, then eat it: the
 * grave's power settles into your chest and the next time you would die it revives you exactly like a
 * held Totem of Undying, once. Eating another Heart before that happens does nothing extra -- the
 * charge does not stack.
 *
 * <p>The revive itself lives in {@code GraveboundEvents.onAllowDeath}; the eat is caught in
 * {@code LivingEntityEatMixin} -> {@code GraveboundEvents.onFinishedEating}.
 */
public class HeartOfTheGraveItem extends Item {
	public HeartOfTheGraveItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.heart_of_the_grave.hint").withStyle(ChatFormatting.GRAY));
	}
}
