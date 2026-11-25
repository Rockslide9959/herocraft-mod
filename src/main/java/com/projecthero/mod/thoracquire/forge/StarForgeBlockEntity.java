package com.projecthero.mod.thoracquire.forge;

import java.util.UUID;

import com.projecthero.mod.thoracquire.ThorAcquire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: the Star Forge's contents and clocks -- the head inside, the channel progress (0..{@link StarForge#CHANNEL_TICKS})
 * and who is channelling, and the finishing clock. Synced to clients (the renderer draws the head, the burning core and the
 * rising axe from it). The rules live in {@link StarForge}.
 */
public class StarForgeBlockEntity extends BlockEntity {
	ItemStack head = ItemStack.EMPTY;
	int channel;
	UUID channeler;
	long lastPing;
	int finish;
	/** Client: the game time the current channel started (for the renderer's smooth glow), from {@link #channel}. */
	long clientChannelSeen;

	public StarForgeBlockEntity(BlockPos pos, BlockState state) {
		super(ThorAcquire.STAR_FORGE_BE, pos, state);
	}

	public ItemStack head() {
		return head;
	}

	public int channel() {
		return channel;
	}

	public boolean channelling() {
		return channeler != null;
	}

	public UUID channeler() {
		return channeler;
	}

	public int finish() {
		return finish;
	}

	void tick(Level level, BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server) {
			StarForge.tick(server, pos, state, this);
		}
	}

	void sync() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (!head.isEmpty()) {
			tag.put("Head", head.save(registries));
		}
		tag.putInt("Channel", channel);
		if (channeler != null) {
			tag.putUUID("Channeler", channeler);
		}
		tag.putInt("Finish", finish);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		head = tag.contains("Head") ? ItemStack.parse(registries, tag.getCompound("Head")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		channel = tag.getInt("Channel");
		channeler = tag.hasUUID("Channeler") ? tag.getUUID("Channeler") : null;
		finish = tag.getInt("Finish");
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
