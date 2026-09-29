package com.projecthero.mod.moonknight.temple;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

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
 * The Altar of Khonshu's state: {@link AltarState#DORMANT_READY} (waiting for a scarab), {@link AltarState#HOLDING_SCARAB}
 * (a ritual is under way for {@link #ritualPlayer}) or {@link AltarState#SPENT} (a pact was sealed here -- never again).
 * All the rules live in {@link KhonshuRitual}; this class only stores, saves and syncs.
 *
 * <p>An in-progress ritual is deliberately <b>not</b> resumable: a block entity read back from disk while holding a
 * scarab (the server stopped, or the chunk unloaded, mid-ritual) cancels on its first tick and gives the scarab back.
 */
public class KhonshuAltarBlockEntity extends BlockEntity {
	public enum AltarState {
		DORMANT_READY, HOLDING_SCARAB, SPENT;

		static AltarState byName(String name) {
			for (AltarState s : values()) {
				if (s.name().equals(name)) {
					return s;
				}
			}
			return DORMANT_READY;
		}
	}

	AltarState state = AltarState.DORMANT_READY;
	@Nullable
	UUID ritualPlayer;
	/** Ticks of continuous kneeling (0 = the scarab is laid but the player has not knelt yet). */
	int progress;
	/** Ticks spent waiting for the player to kneel after laying the scarab. */
	int waitTicks;
	/** -1 outside the rebirth; else ticks since the white flash. */
	int rebirthTicks = -1;
	/** Transient: read back from disk while holding a scarab, so the ritual must be cancelled. */
	boolean restored;

	/** GameTest hooks (never saved): force the night / sky checks, and stop the level ticker so a test drives it. */
	@Nullable
	public Boolean forcedNight;
	@Nullable
	public Boolean forcedSky;
	public boolean testDriven;

	public KhonshuAltarBlockEntity(BlockPos pos, BlockState blockState) {
		super(KhonshuTemple.KHONSHU_ALTAR_BE, pos, blockState);
		if (blockState.hasProperty(KhonshuAltarBlock.SPENT) && blockState.getValue(KhonshuAltarBlock.SPENT)) {
			state = AltarState.SPENT;
		}
	}

	public AltarState altarState() {
		return state;
	}

	@Nullable
	public UUID ritualPlayer() {
		return ritualPlayer;
	}

	public int progress() {
		return progress;
	}

	public boolean inRebirth() {
		return rebirthTicks >= 0;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState blockState, KhonshuAltarBlockEntity be) {
		if (be.state == AltarState.HOLDING_SCARAB && !be.testDriven && level instanceof ServerLevel sl) {
			KhonshuRitual.tick(sl, pos, be);
		}
	}

	/** Save + push the new state to clients (the renderer shows the scarab while one is held). */
	void sync() {
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString("AltarState", state.name());
		if (ritualPlayer != null) {
			tag.putUUID("RitualPlayer", ritualPlayer);
		}
		tag.putInt("Progress", progress);
		tag.putInt("Rebirth", rebirthTicks);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		state = AltarState.byName(tag.getString("AltarState"));
		ritualPlayer = tag.hasUUID("RitualPlayer") ? tag.getUUID("RitualPlayer") : null;
		progress = tag.getInt("Progress");
		rebirthTicks = tag.contains("Rebirth") ? tag.getInt("Rebirth") : -1;
		// only meaningful server-side (a client never ticks the ritual): a ritual cannot survive a reload
		restored = state == AltarState.HOLDING_SCARAB;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putString("AltarState", state.name());
		tag.putInt("Progress", progress);
		tag.putInt("Rebirth", rebirthTicks);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
