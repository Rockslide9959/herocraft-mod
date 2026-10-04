package com.projecthero.mod.ironman.sorter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.16: container arithmetic for the Sorter Bot -- "does it hold this item", "how much of it fits", "put it
 * in", plus a {@link Sim copy-on-write simulation} so a multi-stack load can be checked against a chest's space
 * before anything leaves the station. Merging into existing stacks always comes before taking an empty slot, the
 * same order a player's shift-click uses.
 *
 * <h2>Repacking (v0.14.21)</h2>
 * {@link #repack} rewrites a container's layout: partial stacks of the identical item (same components) are
 * merged, everything is moved to the front with no gaps, and the stacks are ordered -- stacks that belong in the
 * container first, then by {@link SortCategory}, then by item id, then fullest first. The order is a stable sort
 * of the merged stacks, so repacking an already-packed container changes nothing (that is what makes Tidy
 * idempotent). The bot repacks every container it opens, and {@link Sim} simulates against the repacked layout,
 * so a container whose only free space is "between" split stacks still counts that space.
 */
public final class Stash {
	private Stash() {
	}

	/** The default order: category, then item id, then fullest first. */
	public static final Comparator<ItemStack> ORDER = Comparator
			.<ItemStack>comparingInt(s -> SortCategory.of(s).ordinal())
			.thenComparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
			.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

	/** True when {@code c} already holds at least one of the identical item (same components). */
	public static boolean holds(Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (ItemStack.isSameItemSameComponents(c.getItem(i), stack)) {
				return true;
			}
		}
		return false;
	}

	/** How many of {@code stack} would fit into {@code c} once repacked (capped at the stack's count). */
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

	// ------------------------------------------------------------------ repacking (v0.14.21)

	/**
	 * The merged stacks of {@code slots} in their packed order (copies). Merging fills the earliest stack of each
	 * identical item first, so only the last stack of an item is partial.
	 */
	public static List<ItemStack> packed(List<ItemStack> slots, int containerMax, Comparator<ItemStack> order) {
		List<ItemStack> merged = new ArrayList<>();
		for (ItemStack s : slots) {
			if (s.isEmpty()) {
				continue;
			}
			ItemStack rest = s.copy();
			int max = Math.min(containerMax, rest.getMaxStackSize());
			for (ItemStack m : merged) {
				if (rest.isEmpty()) {
					break;
				}
				if (m.getCount() < max && ItemStack.isSameItemSameComponents(m, rest)) {
					int add = Math.min(rest.getCount(), max - m.getCount());
					m.grow(add);
					rest.shrink(add);
				}
			}
			while (!rest.isEmpty()) {
				merged.add(rest.split(max));
			}
		}
		merged.sort(order); // stable: equal keys keep their merged order
		return merged;
	}

	private static List<ItemStack> slotsOf(Container c) {
		List<ItemStack> out = new ArrayList<>(c.getContainerSize());
		for (int i = 0; i < c.getContainerSize(); i++) {
			out.add(c.getItem(i));
		}
		return out;
	}

	/** True when {@link #repack} would change {@code c}. */
	public static boolean needsRepack(Container c, Comparator<ItemStack> order) {
		List<ItemStack> packed = packed(slotsOf(c), c.getMaxStackSize(), order);
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack want = i < packed.size() ? packed.get(i) : ItemStack.EMPTY;
			if (!ItemStack.matches(c.getItem(i), want)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Merge split stacks, close every gap and put the stacks in {@code order}. Items only move inside {@code c},
	 * so the total never changes. Returns true if anything moved.
	 */
	public static boolean repack(Container c, Comparator<ItemStack> order) {
		if (!needsRepack(c, order)) {
			return false;
		}
		List<ItemStack> packed = packed(slotsOf(c), c.getMaxStackSize(), order);
		if (packed.size() > c.getContainerSize()) {
			return false; // cannot happen (merging never adds stacks); refuse rather than lose anything
		}
		for (int i = 0; i < c.getContainerSize(); i++) {
			c.setItem(i, i < packed.size() ? packed.get(i) : ItemStack.EMPTY);
		}
		c.setChanged();
		return true;
	}

	/** {@link #repack} in the default order. */
	public static boolean repack(Container c) {
		return repack(c, ORDER);
	}

	/** v0.14.20 (Tidy): merge split stacks. Since v0.14.21 this is a full {@link #repack}. */
	public static boolean compact(Container c) {
		return repack(c, ORDER);
	}

	/** v0.14.20: true when {@code c} has split stacks of the identical item that could be merged. */
	public static boolean fragmented(Container c) {
		List<ItemStack> slots = slotsOf(c);
		int n = 0;
		for (ItemStack s : slots) {
			if (!s.isEmpty()) {
				n++;
			}
		}
		return packed(slots, c.getMaxStackSize(), ORDER).size() < n;
	}

	/** Used slots once repacked. */
	public static int packedSlots(Container c) {
		return packed(slotsOf(c), c.getMaxStackSize(), ORDER).size();
	}

	/** A private copy of a container's slots -- already repacked -- that inserts can be tried against. */
	public static final class Sim {
		private final Container source;
		private final List<ItemStack> slots;

		private Sim(Container source) {
			this.source = source;
			List<ItemStack> packed = packed(slotsOf(source), source.getMaxStackSize(), ORDER);
			this.slots = new ArrayList<>(source.getContainerSize());
			for (int i = 0; i < source.getContainerSize(); i++) {
				slots.add(i < packed.size() ? packed.get(i) : ItemStack.EMPTY);
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
