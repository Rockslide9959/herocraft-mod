package com.projecthero.mod.loot;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5: remembers which players have looted a {@link HeroVaultBlock} (their UUIDs, in NBT as {@code "looted"}).
 * The set is also sent to clients so the vault can stop shimmering for a reader who has emptied it.
 */
public class HeroVaultBlockEntity extends BlockEntity {
	/** Hard cap so a busy server vault can never grow without bound (oldest entries drop first). */
	public static final int MAX_REMEMBERED = 512;

	private final Set<UUID> looted = new LinkedHashSet<>();

	public HeroVaultBlockEntity(BlockPos pos, BlockState state) {
		super(LootItems.HERO_VAULT_BLOCK_ENTITY, pos, state);
	}

	public boolean hasLooted(UUID player) {
		return looted.contains(player);
	}

	public Set<UUID> looted() {
		return Collections.unmodifiableSet(looted);
	}

	/** Marks the player as having looted it; returns false if they already had. Saves and syncs. */
	public boolean markLooted(UUID player) {
		if (!looted.add(player)) {
			return false;
		}
		while (looted.size() > MAX_REMEMBERED) {
			UUID oldest = looted.iterator().next();
			looted.remove(oldest);
		}
		changed();
		return true;
	}

	/** Forgets everyone (operators / tests: refill the vault). */
	public void reset() {
		looted.clear();
		changed();
	}

	private void changed() {
		setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ListTag list = new ListTag();
		for (UUID id : looted) {
			list.add(NbtUtils.createUUID(id));
		}
		tag.put("looted", list);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		looted.clear();
		for (Tag t : tag.getList("looted", Tag.TAG_INT_ARRAY)) {
			looted.add(NbtUtils.loadUUID(t));
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
