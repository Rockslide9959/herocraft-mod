package com.projecthero.mod.andy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.17.3 (user: "the undead power should have some form of auto feeding" -- passive): food is what his regrowth runs on, so he
 * eats on his own. Once his food (hunger bar + saturation) falls below {@link AndyConfig#AUTO_EAT_BELOW}, every
 * {@link AndyConfig#AUTO_EAT_EVERY} ticks he eats one piece of plain food from his inventory -- the least filling first, and only
 * if it won't be wasted. Never anything with an effect (golden apples, rotten flesh, spider eyes, pufferfish, suspicious stew,
 * chorus fruit, honey), never in creative.
 */
public final class AndyAutoEat {
	private static final Map<UUID, Long> NEXT = new ConcurrentHashMap<>();

	private AndyAutoEat() {
	}

	public static void clearSessionState() {
		NEXT.clear();
	}

	public static void forget(UUID id) {
		NEXT.remove(id);
	}

	/** Every tick for an Andy (from {@link Andy#tick}). */
	public static void tick(ServerPlayer p) {
		if (p.getAbilities().instabuild || p.isSpectator() || !p.isAlive()) {
			return;
		}
		long now = p.level().getGameTime();
		Long next = NEXT.get(p.getUUID());
		if (next != null && now < next) {
			return;
		}
		var food = p.getFoodData();
		if (food.getFoodLevel() + food.getSaturationLevel() >= AndyConfig.AUTO_EAT_BELOW) {
			return;
		}
		int slot = pick(p, 20 - food.getFoodLevel());
		NEXT.put(p.getUUID(), now + AndyConfig.AUTO_EAT_EVERY);
		if (slot < 0) {
			return;
		}
		ItemStack stack = p.getInventory().getItem(slot);
		ItemStack left = stack.finishUsingItem(p.level(), p); // eats it: food, saturation, stats, the bowl back
		if (left != stack) {
			p.getInventory().setItem(slot, ItemStack.EMPTY);
			if (!left.isEmpty() && !p.getInventory().add(left)) {
				p.drop(left, false);
			}
		}
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6f,
				0.9f + p.getRandom().nextFloat() * 0.2f);
	}

	/** The inventory slot of the least filling plain food that fits in {@code room} points (or the smallest if none fits), or -1. */
	static int pick(ServerPlayer p, int room) {
		int best = -1, bestNut = Integer.MAX_VALUE;
		boolean bestFits = false;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			FoodProperties f = s.get(DataComponents.FOOD);
			if (s.isEmpty() || f == null || !plain(s, f)) {
				continue;
			}
			boolean fits = f.nutrition() <= room;
			if (best < 0 || (fits && !bestFits) || (fits == bestFits && f.nutrition() < bestNut)) {
				best = i;
				bestNut = f.nutrition();
				bestFits = fits;
			}
		}
		return best;
	}

	private static boolean plain(ItemStack s, FoodProperties f) {
		if (!f.effects().isEmpty() || f.canAlwaysEat()) {
			return false;
		}
		return !s.is(Items.SUSPICIOUS_STEW) && !s.is(Items.CHORUS_FRUIT) && !s.is(Items.HONEY_BOTTLE) && !s.is(Items.ROTTEN_FLESH)
				&& !s.is(Items.SPIDER_EYE) && !s.is(Items.POISONOUS_POTATO) && !s.is(Items.PUFFERFISH);
	}
}
