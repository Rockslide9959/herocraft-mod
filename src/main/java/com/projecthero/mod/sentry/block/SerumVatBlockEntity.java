package com.projecthero.mod.sentry.block;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** v0.17.5: the O.X.E. serum vat's memory -- who has already taken their Sentry Serum from it (one each). */
public class SerumVatBlockEntity extends BlockEntity {
	private final Set<UUID> claimed = new HashSet<>();

	public SerumVatBlockEntity(BlockPos pos, BlockState state) {
		super(SentryBlocks.SERUM_VAT_BE, pos, state);
	}

	/** Records {@code who}; false if they already had theirs. */
	public boolean claim(UUID who) {
		if (!claimed.add(who)) {
			return false;
		}
		setChanged();
		return true;
	}

	public boolean claimed(UUID who) {
		return claimed.contains(who);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ListTag list = new ListTag();
		for (UUID id : claimed) {
			list.add(NbtUtils.createUUID(id));
		}
		tag.put("claimed", list);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		claimed.clear();
		for (Tag t : tag.getList("claimed", Tag.TAG_INT_ARRAY)) {
			claimed.add(NbtUtils.loadUUID(t));
		}
	}
}
