package com.projecthero.mod.event.reward;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * v0.14.21: the shared "valuables" helpers every horde and raid reward pool uses for its ores and gems -- lapis (and
 * lapis blocks for the bigger events), diamonds, emeralds, gold, iron, redstone, amethyst, the odd netherite scrap or
 * ancient debris, enchanted books and golden apples. The tables themselves stay with each event
 * ({@code HordeRewards}, {@code ZombieRaidRewards}, {@code SupervillainRaidRewards}, {@code DarkseidRaidRewards}); this
 * class only holds the arithmetic so they all scale the same way.
 */
public final class Valuables {
	/** Every vanilla item an event pays out as a "valuable" (used by the gametests). */
	public static final Set<Item> ALL = Set.of(Items.LAPIS_LAZULI, Items.LAPIS_BLOCK, Items.DIAMOND, Items.DIAMOND_BLOCK,
			Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT, Items.REDSTONE, Items.AMETHYST_SHARD, Items.NETHERITE_SCRAP,
			Items.ANCIENT_DEBRIS, Items.ENCHANTED_BOOK, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);

	private Valuables() {
	}

	/**
	 * The party bonus the Horde chests have always used: a second fighter adds a third more of every stack, up to double
	 * for four or more.
	 */
	public static double partyScale(int players) {
		return Math.min(2.0, 1.0 + (Math.max(1, players) - 1) / 3.0);
	}

	/** {@code lo..hi} (inclusive) times {@code scale}, rounded, split into full stacks. Nothing for a scale of 0. */
	public static void add(List<ItemStack> out, RandomSource r, Item item, int lo, int hi, double scale) {
		int n = lo + (hi > lo ? r.nextInt(hi - lo + 1) : 0);
		stacks(out, item, (int) Math.round(n * Math.max(0.0, scale)));
	}

	/** With probability {@code chance}: {@code lo..hi} of {@code item} (not scaled -- these are the rare extras). */
	public static void chance(List<ItemStack> out, RandomSource r, double chance, Item item, int lo, int hi) {
		if (r.nextDouble() < chance) {
			add(out, r, item, lo, hi, 1.0);
		}
	}

	/** {@code total} of {@code item}, split into full stacks. */
	public static void stacks(List<ItemStack> out, Item item, int total) {
		int max = new ItemStack(item).getMaxStackSize();
		while (total > 0) {
			int n = Math.min(max, total);
			out.add(new ItemStack(item, n));
			total -= n;
		}
	}

	/** An enchanted book rolled like an enchanting table at {@code power} levels. */
	public static ItemStack book(RandomSource r, RegistryAccess registries, int power) {
		return EnchantmentHelper.enchantItem(r, new ItemStack(Items.BOOK), power, registries, Optional.empty());
	}

	/** Linear blend for wave-scaled amounts: {@code a} at {@code f = 0}, {@code b} at {@code f = 1}. */
	public static int lerp(int a, int b, double f) {
		return (int) Math.round(a + (b - a) * Math.max(0.0, Math.min(1.0, f)));
	}
}
