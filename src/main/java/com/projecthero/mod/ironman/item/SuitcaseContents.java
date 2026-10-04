package com.projecthero.mod.ironman.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * v0.14.21: the Mark V Suitcase now actually <em>holds</em> the four armour stacks it stands for (in the vanilla
 * {@code minecraft:container} component, slot 0 helmet .. 3 boots), so a suit folded into the case and unfolded again
 * is the very same armour -- enchantments, custom names, every component -- instead of four freshly made pieces.
 *
 * <p>A case with no stored pieces at all (one from the creative tab or an older world) still unfolds a fresh Mark V,
 * exactly as before; {@link #isLegacyEmpty} tells the two apart.
 */
public final class SuitcaseContents {
	public static final int SLOTS = 4;

	private SuitcaseContents() {
	}

	public static int slotOf(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> 0;
			case CHESTPLATE -> 1;
			case LEGGINGS -> 2;
			case BOOTS -> 3;
			default -> -1;
		};
	}

	/** The four slots (empty stacks where nothing is stored). Copies -- writing them back is {@link #write}. */
	public static NonNullList<ItemStack> read(ItemStack caseStack) {
		NonNullList<ItemStack> out = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		ItemContainerContents c = caseStack.get(DataComponents.CONTAINER);
		if (c != null) {
			c.copyInto(out);
		}
		return out;
	}

	/**
	 * Always leaves the component present (even when every slot is empty): an <em>empty</em> modern case must never be
	 * mistaken for a legacy one, or a case emptied mid-sequence would unfold a free suit.
	 */
	public static void write(ItemStack caseStack, NonNullList<ItemStack> slots) {
		caseStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(new ArrayList<>(slots)));
	}

	/** True for a case that has never had a suit folded into it (creative tab / pre-0.14.21) -- it unfolds a fresh suit. */
	public static boolean isLegacyEmpty(ItemStack caseStack) {
		return !caseStack.has(DataComponents.CONTAINER);
	}

	public static boolean isEmpty(ItemStack caseStack) {
		for (ItemStack s : read(caseStack)) {
			if (!s.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Put one piece in its slot; false (stack untouched) if the slot is taken. */
	public static boolean put(ItemStack caseStack, ItemStack piece) {
		if (!(piece.getItem() instanceof IronManArmorItem a)) {
			return false;
		}
		int idx = slotOf(a.getType());
		NonNullList<ItemStack> slots = read(caseStack);
		if (idx < 0 || !slots.get(idx).isEmpty()) {
			return false;
		}
		slots.set(idx, piece.copy());
		write(caseStack, slots);
		return true;
	}

	/** Take the piece in {@code type}'s slot out of the case (EMPTY if there is none). */
	public static ItemStack take(ItemStack caseStack, ArmorItem.Type type) {
		int idx = slotOf(type);
		if (idx < 0) {
			return ItemStack.EMPTY;
		}
		NonNullList<ItemStack> slots = read(caseStack);
		ItemStack out = slots.get(idx);
		if (out.isEmpty()) {
			return ItemStack.EMPTY;
		}
		slots.set(idx, ItemStack.EMPTY);
		write(caseStack, slots);
		return out;
	}

	public static List<ItemStack> nonEmpty(ItemStack caseStack) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : read(caseStack)) {
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}
}
