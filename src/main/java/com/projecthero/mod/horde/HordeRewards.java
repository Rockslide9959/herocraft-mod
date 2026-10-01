package com.projecthero.mod.horde;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
 */
public final class HordeRewards {
	private HordeRewards() {
	}

	public static void placeChest(ServerLevel level, BlockPos pos, HordeKind kind, int winners) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
				Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			List<ItemStack> loot = roll(level, kind, winners);
			for (int i = 0; i < loot.size() && i < chest.getContainerSize(); i++) {
				chest.setItem(i, loot.get(i));
			}
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 60, 0.5, 0.8, 0.5, 0.3);
	}

	/** The chest's contents (at most 27 stacks). */
	public static List<ItemStack> roll(ServerLevel level, HordeKind kind, int winners) {
		RandomSource r = level.random;
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
				out.add(book(level, r, 20));
			}
			case SKELETON -> {
				add(out, Items.DIAMOND, between(r, 5, 9), more);
				add(out, Items.GOLD_INGOT, between(r, 12, 20), more);
				add(out, Items.IRON_INGOT, between(r, 20, 40), more);
				add(out, Items.GOLDEN_APPLE, between(r, 3, 5), more);
				add(out, Items.EMERALD, between(r, 12, 20), more);
				add(out, Items.EXPERIENCE_BOTTLE, between(r, 12, 20), more);
				add(out, Items.ARROW, 64, 1.0);
				out.add(EnchantmentHelper.enchantItem(r, new ItemStack(Items.BOW), 30, level.registryAccess(), Optional.empty()));
				if (r.nextFloat() < 0.35f) {
					out.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
				}
				out.add(book(level, r, 30));
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
				out.add(book(level, r, 30));
				out.add(book(level, r, 30));
			}
		}
		return out;
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

	private static ItemStack book(ServerLevel level, RandomSource r, int power) {
		return EnchantmentHelper.enchantItem(r, new ItemStack(Items.BOOK), power, level.registryAccess(), Optional.empty());
	}
}
