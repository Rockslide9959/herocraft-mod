package com.projecthero.mod.horde;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.projecthero.mod.event.reward.Valuables;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * v0.14.12: a beaten Horde block turns into a chest of loot. Every horde pays diamonds, gold, iron, golden apples,
 * emeralds and experience; the harder the horde the more of each and the rarer the extras:
 * <ul>
 *   <li><b>Zombie</b>: the base haul, a 15% enchanted golden apple, one enchanted book;</li>
 *   <li><b>Skeleton</b> (slightly better): bigger stacks, a 35% enchanted golden apple, an enchanted bow and a stack of
 *       arrows, one book;</li>
 *   <li><b>Spider</b> (the best -- the hardest fight): bigger again, a guaranteed enchanted golden apple (+40% for a
 *       second), netherite scrap, a 50% totem of undying, a 30% block of diamond, two books.</li>
 * </ul>
 * A second surviving fighter adds a third more of every stack (up to double for four or more).
 *
 * <p>v0.14.21: every chest also pays lapis lazuli, redstone and amethyst shards (scaled by fighters like the rest), the
 * Skeleton chest a 40% chance of 1-2 lapis blocks, and the Spider chest 2-4 lapis blocks and a 25% ancient debris -- see
 * {@link com.projecthero.mod.event.reward.Valuables}.
 *
 * <p>v0.14.16: the haul is no longer packed into the chest's first slots -- {@link #fill} splits stacks and scatters them
 * across random slots the way vanilla's loot chests do, so the chest looks (and is) full.
 */
public final class HordeRewards {
	private HordeRewards() {
	}

	public static void placeChest(ServerLevel level, BlockPos pos, HordeKind kind, int winners) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
				Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			fill(chest, roll(level, kind, winners), level.random);
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 60, 0.5, 0.8, 0.5, 0.3);
	}

	/** The chest's contents (at most 27 stacks). */
	public static List<ItemStack> roll(ServerLevel level, HordeKind kind, int winners) {
		return roll(level.random, level.registryAccess(), kind, winners);
	}

	/** {@link #roll(ServerLevel, HordeKind, int)} with an explicit random (seeded in the gametests). */
	public static List<ItemStack> roll(RandomSource r, RegistryAccess registries, HordeKind kind, int winners) {
		double more = Math.min(2.0, 1.0 + (Math.max(1, winners) - 1) / 3.0);
		List<ItemStack> out = new ArrayList<>();
		switch (kind) {
			case ZOMBIE -> {
				add(out, Items.DIAMOND, between(r, 3, 6), more);
				add(out, Items.GOLD_INGOT, between(r, 8, 16), more);
				add(out, Items.IRON_INGOT, between(r, 16, 32), more);
				add(out, Items.GOLDEN_APPLE, between(r, 2, 4), more);
				add(out, Items.EMERALD, between(r, 8, 16), more);
				add(out, Items.EXPERIENCE_BOTTLE, between(r, 8, 16), more);
				add(out, Items.COOKED_BEEF, between(r, 16, 24), 1.0);
				if (r.nextFloat() < 0.15f) {
					out.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
				}
				// v0.14.21 valuables
				Valuables.add(out, r, Items.LAPIS_LAZULI, 16, 28, more);
				Valuables.add(out, r, Items.REDSTONE, 12, 24, more);
				Valuables.add(out, r, Items.AMETHYST_SHARD, 6, 12, more);
				out.add(Valuables.book(r, registries, 20));
			}
			case SKELETON -> {
				add(out, Items.DIAMOND, between(r, 5, 9), more);
				add(out, Items.GOLD_INGOT, between(r, 12, 20), more);
				add(out, Items.IRON_INGOT, between(r, 20, 40), more);
				add(out, Items.GOLDEN_APPLE, between(r, 3, 5), more);
				add(out, Items.EMERALD, between(r, 12, 20), more);
				add(out, Items.EXPERIENCE_BOTTLE, between(r, 12, 20), more);
				add(out, Items.ARROW, 64, 1.0);
				out.add(EnchantmentHelper.enchantItem(r, new ItemStack(Items.BOW), 30, registries, Optional.empty()));
				if (r.nextFloat() < 0.35f) {
					out.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
				}
				// v0.14.21 valuables
				Valuables.add(out, r, Items.LAPIS_LAZULI, 20, 32, more);
				Valuables.add(out, r, Items.REDSTONE, 16, 28, more);
				Valuables.add(out, r, Items.AMETHYST_SHARD, 8, 14, more);
				Valuables.chance(out, r, 0.4, Items.LAPIS_BLOCK, 1, 2);
				out.add(Valuables.book(r, registries, 30));
			}
			case SPIDER -> {
				add(out, Items.DIAMOND, between(r, 8, 14), more);
				add(out, Items.GOLD_INGOT, between(r, 16, 28), more);
				add(out, Items.IRON_INGOT, between(r, 32, 48), more);
				add(out, Items.GOLDEN_APPLE, between(r, 5, 8), more);
				add(out, Items.EMERALD, between(r, 16, 28), more);
				add(out, Items.EXPERIENCE_BOTTLE, between(r, 20, 32), more);
				add(out, Items.NETHERITE_SCRAP, between(r, 1, 3), more);
				out.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, r.nextFloat() < 0.4f ? 2 : 1));
				if (r.nextFloat() < 0.5f) {
					out.add(new ItemStack(Items.TOTEM_OF_UNDYING));
				}
				if (r.nextFloat() < 0.3f) {
					out.add(new ItemStack(Items.DIAMOND_BLOCK));
				}
				// v0.14.21 valuables
				Valuables.add(out, r, Items.LAPIS_LAZULI, 24, 40, more);
				Valuables.add(out, r, Items.LAPIS_BLOCK, 2, 4, more);
				Valuables.add(out, r, Items.REDSTONE, 20, 32, more);
				Valuables.add(out, r, Items.AMETHYST_SHARD, 10, 16, more);
				Valuables.chance(out, r, 0.25, Items.ANCIENT_DEBRIS, 1, 1);
				out.add(Valuables.book(r, registries, 30));
				out.add(Valuables.book(r, registries, 30));
			}
		}
		return out;
	}

	/**
	 * v0.14.16: puts {@code loot} into {@code container}'s empty slots like a vanilla loot chest: stacks are split into
	 * smaller ones until roughly 65-85% of the free slots are used, shuffled, and dropped into random slots. Nothing is
	 * lost -- every item in {@code loot} ends up in the container (if the stacks ever outnumber the slots, the
	 * leftovers merge into matching stacks or any slot still empty).
	 */
	public static void fill(Container container, List<ItemStack> loot, RandomSource r) {
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				free.add(i);
			}
		}
		List<ItemStack> stacks = new ArrayList<>();
		List<ItemStack> splittable = new ArrayList<>();
		for (ItemStack s : loot) {
			if (s.isEmpty()) {
				continue;
			}
			ItemStack copy = s.copy();
			stacks.add(copy);
			if (copy.getCount() > 1) {
				splittable.add(copy);
			}
		}
		int target = Math.min(free.size(), (int) Math.round(free.size() * (0.65 + r.nextFloat() * 0.2)));
		while (stacks.size() < target && !splittable.isEmpty()) {
			ItemStack s = splittable.remove(r.nextInt(splittable.size()));
			ItemStack part = s.split(Mth.randomBetweenInclusive(r, 1, s.getCount() / 2));
			stacks.add(part);
			if (s.getCount() > 1) {
				splittable.add(s);
			}
			if (part.getCount() > 1 && r.nextBoolean()) {
				splittable.add(part);
			}
		}
		Collections.shuffle(stacks, new java.util.Random(r.nextLong()));
		Collections.shuffle(free, new java.util.Random(r.nextLong()));
		int next = 0;
		for (ItemStack s : stacks) {
			if (next < free.size()) {
				container.setItem(free.get(next++), s);
				continue;
			}
			// more stacks than slots (never with the rolls above): top up a matching stack, then anything empty
			for (int i = 0; i < container.getContainerSize() && !s.isEmpty(); i++) {
				ItemStack there = container.getItem(i);
				if (there.isEmpty()) {
					container.setItem(i, s.copyAndClear());
				} else if (ItemStack.isSameItemSameComponents(there, s) && there.getCount() < there.getMaxStackSize()) {
					int move = Math.min(s.getCount(), there.getMaxStackSize() - there.getCount());
					there.grow(move);
					s.shrink(move);
				}
			}
		}
	}

	private static int between(RandomSource r, int lo, int hi) {
		return lo + r.nextInt(hi - lo + 1);
	}

	/** {@code count} x {@code more}, split into full stacks. */
	private static void add(List<ItemStack> out, Item item, int count, double more) {
		int total = (int) Math.round(count * more);
		int max = new ItemStack(item).getMaxStackSize();
		while (total > 0) {
			int n = Math.min(max, total);
			out.add(new ItemStack(item, n));
			total -= n;
		}
	}
}
