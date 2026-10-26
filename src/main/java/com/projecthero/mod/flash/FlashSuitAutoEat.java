package com.projecthero.mod.flash;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.17.1 (user: "when player is wearing the flash armour make them automatically eat food please since their powers cause
 * them to use up a lot of food"): a speedster (the plain Speedster or the Speed Force Speedster) in the full Flash Suit
 * eats from their inventory on their own, a bite every 2 s while hungry.
 *
 * <p>It never wastes food: it picks the most filling food that still fits in the hunger bar (and only something bigger
 * when the bar is nearly empty). It never eats anything with a side effect -- golden apples, rotten flesh, spider eyes,
 * pufferfish, chorus fruit, suspicious stew -- and bowls and bottles come back to the inventory.
 */
public final class FlashSuitAutoEat {
	private static final int EVERY = 40;
	/** At or below this the suit eats even a food bigger than the gap (better a little waste than starving at speed). */
	private static final int STARVING = 6;

	private FlashSuitAutoEat() {
	}

	public static void tick(ServerPlayer p) {
		if (p.tickCount % EVERY != 0 || p.isCreative() || p.isSpectator() || p.isUsingItem() || !p.isAlive()) {
			return;
		}
		int food = p.getFoodData().getFoodLevel();
		if (food >= 20 || !FlashSuit.wearsFull(p) || !(SuperSpeedHandlers.owns(p) || SpeedForceSpeedster.hasPower(p))) {
			return;
		}
		int missing = 20 - food;
		Inventory inv = p.getInventory();
		int best = -1;
		int bestNutrition = 0;
		int smallest = -1;
		int smallestNutrition = Integer.MAX_VALUE;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			FoodProperties fp = safeFood(s);
			if (fp == null) {
				continue;
			}
			int n = fp.nutrition();
			if (n <= missing && n > bestNutrition) {
				best = i;
				bestNutrition = n;
			}
			if (n < smallestNutrition) {
				smallest = i;
				smallestNutrition = n;
			}
		}
		int slot = best >= 0 ? best : food <= STARVING ? smallest : -1;
		if (slot < 0) {
			return;
		}
		ItemStack stack = inv.getItem(slot);
		Component name = stack.getHoverName();
		ItemStack one = stack.split(1);
		ItemStack left = one.finishUsingItem(p.level(), p);
		if (!left.isEmpty()) {
			// a bowl, a bottle: back into the inventory (or dropped if it is full)
			if (!inv.add(left)) {
				p.drop(left, false);
			}
		}
		p.displayClientMessage(Component.translatable("message.projecthero.flash.auto_eat", name).withStyle(ChatFormatting.GOLD), true);
	}

	/** The food component of a stack the suit may eat on its own, or null. */
	private static FoodProperties safeFood(ItemStack s) {
		if (s.isEmpty()) {
			return null;
		}
		FoodProperties fp = s.get(DataComponents.FOOD);
		if (fp == null || !fp.effects().isEmpty() || s.is(Items.CHORUS_FRUIT) || s.is(Items.SUSPICIOUS_STEW)
				|| s.has(DataComponents.SUSPICIOUS_STEW_EFFECTS)) {
			return null;
		}
		return fp;
	}
}
