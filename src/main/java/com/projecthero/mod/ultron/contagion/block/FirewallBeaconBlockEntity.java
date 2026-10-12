package com.projecthero.mod.ultron.contagion.block;

import com.projecthero.mod.ultron.contagion.ContagionState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.1: the Firewall Beacon's block entity. Once a second it keeps the block's {@link FirewallBeaconBlock#LIT} state in
 * step with the fuel in {@link ContagionState} and makes sure the beacon is registered there. It also carries the synced
 * "probe" flicker (the blue hex dome the client draws at the edge Ultron is pushing on).
 */
public class FirewallBeaconBlockEntity extends BlockEntity {
	/** Ticks the dome keeps flickering after a probe. */
	public static final int PROBE_TICKS = 50;

	private long probeUntil;
	private float probeYaw;

	public FirewallBeaconBlockEntity(BlockPos pos, BlockState state) {
		super(ContagionBlocks.FIREWALL_BEACON_BE, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FirewallBeaconBlockEntity be) {
		if (!(level instanceof ServerLevel server) || server.getGameTime() % 20 != 0 || server.dimension() != Level.OVERWORLD) {
			return;
		}
		ContagionState s = ContagionState.get(server);
		if (!s.beacons.containsKey(pos)) {
			s.beacons.put(pos.immutable(), server.getGameTime());
			s.setDirty();
		}
		boolean lit = s.beaconFuelled(pos, server.getGameTime());
		if (state.getValue(FirewallBeaconBlock.LIT) != lit) {
			server.setBlock(pos, state.setValue(FirewallBeaconBlock.LIT, lit), Block.UPDATE_ALL);
		}
	}

	/** Ultron pushed on the dome from direction {@code yaw} (degrees, from the beacon towards the push). */
	public void probe(float yaw) {
		if (level == null) {
			return;
		}
		probeUntil = level.getGameTime() + PROBE_TICKS;
		probeYaw = yaw;
		setChanged();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	public long probeUntil() {
		return probeUntil;
	}

	public float probeYaw() {
		return probeYaw;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putLong("ProbeUntil", probeUntil);
		tag.putFloat("ProbeYaw", probeYaw);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		probeUntil = tag.getLong("ProbeUntil");
		probeYaw = tag.getFloat("ProbeYaw");
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
