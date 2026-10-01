package com.projecthero.mod.ironman.sorter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.16: container arithmetic for the Sorter Bot -- "does it hold this item", "how much of it fits", "put it
 * in", plus a {@link Sim copy-on-write simulation} so a multi-stack load can be checked against a chest's space
 * before anything leaves the station. Merging into existing stacks always comes before taking an empty slot, the
 * same order a player's shift-click uses.
 */
public final class Stash {
	private Stash() {
	}

	/** True when {@code c} already holds at least one of the identical item (same components). */
	public static boolean holds(Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (ItemStack.isSameItemSameComponents(c.getItem(i), stack)) {
				return true;
			}
		}
		return false;
	}

	/** How many of {@code stack} would fit into {@code c} right now (capped at the stack's count). */
	public static int room(Container c, ItemStack stack) {
		return Sim.of(c).insert(stack.copy());
	}

	/**
	 * Insert {@code stack} into {@code c}. Shrinks {@code stack} by what went in (so whatever is left in it is the
	 * remainder) and returns how many items were inserted.
	 */
	public static int insert(Container c, ItemStack stack) {
		int moved = 0;
		int max = Math.min(c.getMaxStackSize(stack), stack.getMaxStackSize());
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack there = c.getItem(i);
			if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && c.canPlaceItem(i, stack)) {
				int add = Math.min(stack.getCount(), max - there.getCount());
				if (add > 0) {
					there.grow(add);
					stack.shrink(add);
					moved += add;
				}
			}
		}
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			if (c.getItem(i).isEmpty() && c.canPlaceItem(i, stack)) {
				int add = Math.min(stack.getCount(), max);
				c.setItem(i, stack.split(add));
				moved += add;
			}
		}
		if (moved > 0) {
			c.setChanged();
		}
		return moved;
	}

	/** A private copy of a container's slots that inserts can be tried against. */
	public static final class Sim {
		private final Container source;
		private final List<ItemStack> slots;

		private Sim(Container source) {
			this.source = source;
			this.slots = new ArrayList<>(source.getContainerSize());
			for (int i = 0; i < source.getContainerSize(); i++) {
				slots.add(source.getItem(i).copy());
			}
		}

		public static Sim of(Container c) {
			return new Sim(c);
		}

		/** Insert into the copy. Shrinks {@code stack} by what fitted and returns that amount. */
		public int insert(ItemStack stack) {
			int moved = 0;
			int max = Math.min(source.getMaxStackSize(stack), stack.getMaxStackSize());
			for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
				ItemStack there = slots.get(i);
				if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && source.canPlaceItem(i, stack)) {
					int add = Math.min(stack.getCount(), max - there.getCount());
					if (add > 0) {
						there.grow(add);
						stack.shrink(add);
						moved += add;
					}
				}
			}
			for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
				if (slots.get(i).isEmpty() && source.canPlaceItem(i, stack)) {
					int add = Math.min(stack.getCount(), max);
					slots.set(i, stack.split(add));
					moved += add;
				}
			}
			return moved;
		}
	}
}
