package com.projecthero.mod.grave;

import com.projecthero.mod.grave.item.GraveComponents;
import com.projecthero.mod.grave.item.GraveItems;
import com.projecthero.mod.grave.item.TrophyRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.14.4: what a placed trophy head remembers -- the boss's power (which also picks the glow colour) and the kill
 * record. Filled from the item's components when it is placed ({@link #applyImplicitComponents}) and handed back to
 * the dropped item by the loot table's {@code copy_components} ({@link #collectImplicitComponents}), the same path
 * vanilla banners use, so breaking and re-placing a trophy never loses anything.
 */
public class TrophyHeadBlockEntity extends BlockEntity {
	private String powerKey;
	private TrophyRecord record;

	public TrophyHeadBlockEntity(BlockPos pos, BlockState state) {
		super(GraveItems.TROPHY_HEAD_BE, pos, state);
	}

	public String powerKey() {
		return powerKey;
	}

	public TrophyRecord record() {
		return record;
	}

	/** Colour of the glowing eyes / sigil / gem, for the block colour provider. */
	public int glowColor() {
		return TrophyHeads.glowColor(powerKey, getBlockState().getBlock() instanceof TrophyHeadBlock b
				? b.kind() == TrophyHeads.Kind.CHAMPION
				: getBlockState().getBlock() instanceof TrophyHeadWallBlock w && w.kind() == TrophyHeads.Kind.CHAMPION);
	}

	@Override
	protected void applyImplicitComponents(DataComponentInput input) {
		super.applyImplicitComponents(input);
		powerKey = input.get(GraveComponents.POWER_KEY);
		record = input.get(GraveComponents.TROPHY_RECORD);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (powerKey != null) {
			builder.set(GraveComponents.POWER_KEY, powerKey);
		}
		if (record != null) {
			builder.set(GraveComponents.TROPHY_RECORD, record);
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void removeComponentsFromTag(CompoundTag tag) {
		super.removeComponentsFromTag(tag);
		tag.remove("power_key");
		tag.remove("trophy_record");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (powerKey != null) {
			tag.putString("power_key", powerKey);
		}
		if (record != null) {
			TrophyRecord.CODEC.encodeStart(NbtOps.INSTANCE, record).result().ifPresent(t -> tag.put("trophy_record", t));
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		powerKey = tag.contains("power_key") ? tag.getString("power_key") : null;
		record = tag.contains("trophy_record")
				? TrophyRecord.CODEC.parse(NbtOps.INSTANCE, tag.get("trophy_record")).result().orElse(null)
				: null;
		// The glow colour is baked into the chunk mesh, so a client that just learned the power must re-mesh.
		if (level != null && level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
