package com.projecthero.mod.ironman.crate;

import java.util.ArrayList;

import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.7 (user) ERA I: what a placed Suit Crate holds -- the four real armour stacks (slot 0 helmet .. 3 boots), each
 * stamped with the suit's integrity and energy, exactly the data the Suit Crate item carries in its
 * {@code minecraft:container} component. Placing the item fills it ({@link #applyImplicitComponents}); breaking the block
 * hands it back to the dropped item through the loot table's {@code copy_components} ({@link #collectImplicitComponents}).
 */
public class SuitCrateBlockEntity extends BlockEntity {
	private final NonNullList<ItemStack> slots = NonNullList.withSize(4, ItemStack.EMPTY);

	public SuitCrateBlockEntity(BlockPos pos, BlockState state) {
		super(SuitCrateBlocks.SUIT_CRATE_BE, pos, state);
	}

	// ------------------------------------------------------------------ contents

	/** Copies of the four slots. */
	public NonNullList<ItemStack> contents() {
		NonNullList<ItemStack> out = NonNullList.withSize(4, ItemStack.EMPTY);
		for (int i = 0; i < 4; i++) {
			out.set(i, slots.get(i).copy());
		}
		return out;
	}

	public void setContents(NonNullList<ItemStack> in) {
		for (int i = 0; i < 4; i++) {
			slots.set(i, i < in.size() ? in.get(i).copy() : ItemStack.EMPTY);
		}
		setChanged();
	}

	/** Take the piece in slot {@code bit} (0 HEAD .. 3 FEET) out of the crate; EMPTY if none. */
	public ItemStack take(int bit) {
		ItemStack s = slots.get(bit);
		slots.set(bit, ItemStack.EMPTY);
		setChanged();
		return s;
	}

	public boolean isEmptyCrate() {
		for (ItemStack s : slots) {
			if (!s.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Rack-slot mask of the armour pieces inside. */
	public int mask() {
		int m = 0;
		for (int i = 0; i < 4; i++) {
			m |= slots.get(i).getItem() instanceof IronManArmorItem ? 1 << i : 0;
		}
		return m;
	}

	/** The suit id of the first piece inside, or null if the crate is empty. */
	public String suitId() {
		for (ItemStack s : slots) {
			if (s.getItem() instanceof IronManArmorItem a) {
				return a.suitId();
			}
		}
		return null;
	}

	/** The piece the suit's integrity / energy is read from: the chestplate, else any piece; EMPTY if none. */
	public ItemStack reference() {
		if (slots.get(1).getItem() instanceof IronManArmorItem) {
			return slots.get(1);
		}
		for (ItemStack s : slots) {
			if (s.getItem() instanceof IronManArmorItem) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	public float integrity() {
		String id = suitId();
		return id == null ? 0f : IronManEnergy.stackIntegrity(reference(), id);
	}

	public float energy() {
		String id = suitId();
		return id == null ? 0f : IronManEnergy.stackEnergy(reference(), id);
	}

	/** Re-stamp every piece with {@code integrity} (each keeps its own energy). */
	public void setIntegrity(float integrity) {
		for (ItemStack s : slots) {
			if (s.getItem() instanceof IronManArmorItem a) {
				IronManEnergy.stampStack(s, IronManEnergy.stackEnergy(s, a.suitId()), integrity);
			}
		}
		setChanged();
	}

	// ------------------------------------------------------------------ item <-> block

	@Override
	protected void applyImplicitComponents(DataComponentInput input) {
		super.applyImplicitComponents(input);
		NonNullList<ItemStack> in = NonNullList.withSize(4, ItemStack.EMPTY);
		input.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(in);
		for (int i = 0; i < 4; i++) {
			slots.set(i, in.get(i));
		}
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(new ArrayList<>(slots)));
	}

	@Override
	@SuppressWarnings("deprecation")
	public void removeComponentsFromTag(CompoundTag tag) {
		super.removeComponentsFromTag(tag);
		tag.remove("Items");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, slots, true, registries);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		for (int i = 0; i < 4; i++) {
			slots.set(i, ItemStack.EMPTY);
		}
		ContainerHelper.loadAllItems(tag, slots, registries);
	}
}
