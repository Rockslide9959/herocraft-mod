package com.projecthero.mod.hero;

import com.projecthero.mod.hero.item.HeroPackComponents;
import com.projecthero.mod.hero.item.HeroPackItems;
import com.projecthero.mod.hero.mutation.ModSerums;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * v0.14.8: which item stacks belong to a disabled experimental power ({@link Powers#ENABLED}) -- its reagent, its
 * serum potion (any potion item: drinkable, splash, lingering) or a research note written about it.
 */
public final class PowerItems {
	private PowerItems() {
	}

	/** The disabled power {@code stack} belongs to, or null (not a power item, or the power is enabled). */
	public static Power disabledPowerOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		String noteKey = stack.get(HeroPackComponents.RESEARCH_POWER);
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		for (Power p : Powers.all()) {
			if (Powers.isMutation(p)) { // v0.14.13: Hero-Tier Super Speed has no serum / reagent / note any more
				continue;
			}
			if (p.key().equals(noteKey) || stack.is(HeroPackItems.reagent(p))) {
				return p;
			}
			if (potion != null && ModSerums.serum(p) != null && potion.is(ModSerums.serum(p))) {
				return p;
			}
		}
		return null;
	}

	public static boolean isDisabledPowerItem(ItemStack stack) {
		return disabledPowerOf(stack) != null;
	}
}
