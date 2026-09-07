package com.projecthero.mod.symbiote;

import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Symbiote host's iron stomach: while the suit is active, its host can eat raw meat (and raw
 * potato / kelp) and get the full value of the cooked version -- the alien does the processing. Kept
 * out of the {@code LivingEntity} mixin itself because a {@code Map} built from {@link Items}
 * constants cannot live in a field merged into {@code LivingEntity} (it would run during vanilla's
 * bootstrap, before {@code Items} is initialised, and NPE); this ordinary class initialises long
 * after that.
 */
public final class SymbioteDiet {
	/** Raw food -> the item whose cooked {@link FoodProperties} an active host gets from it instead. */
	private static final Map<Item, Item> RAW_TO_COOKED = Map.ofEntries(
			Map.entry(Items.BEEF, Items.COOKED_BEEF),
			Map.entry(Items.PORKCHOP, Items.COOKED_PORKCHOP),
			Map.entry(Items.CHICKEN, Items.COOKED_CHICKEN),
			Map.entry(Items.MUTTON, Items.COOKED_MUTTON),
			Map.entry(Items.RABBIT, Items.COOKED_RABBIT),
			Map.entry(Items.COD, Items.COOKED_COD),
			Map.entry(Items.SALMON, Items.COOKED_SALMON),
			Map.entry(Items.POTATO, Items.BAKED_POTATO),
			Map.entry(Items.KELP, Items.DRIED_KELP));

	private SymbioteDiet() {
	}

	/**
	 * The {@link FoodProperties} {@code eater} should actually get from finishing {@code stack} -- the
	 * cooked equivalent's stats if {@code eater} is an active Symbiote host eating a raw food we cover,
	 * otherwise {@code original} unchanged.
	 */
	public static FoodProperties resolve(Player eater, ItemStack stack, FoodProperties original) {
		// v0.9.24: any bonded host, suit on or off -- "the Symbiote is always with you".
		if (!Symbiote.hasSymbiote(eater)) {
			return original;
		}
		Item cooked = RAW_TO_COOKED.get(stack.getItem());
		if (cooked == null) {
			return original;
		}
		FoodProperties cookedFood = new ItemStack(cooked).get(DataComponents.FOOD);
		return cookedFood != null ? cookedFood : original;
	}
}
