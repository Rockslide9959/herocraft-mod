package com.projecthero.mod.hero.power.p09;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * v0.16.5 Cryokinesis rework: the power's own temporary ice (Glacier Wall, the Ice Slide ramp, the Ice Pillar, frozen
 * water). Unlike the shared {@code TempBlocks} FIFO, every placement keeps its own melt time (a 1 s slide block never
 * waits behind a 15 s wall), a block that has since been mined or replaced is simply forgotten (never restored over a
 * player's build), a placement in an unloaded chunk waits until the chunk is back, and <b>everything still standing is
 * melted back the moment the server stops</b> -- so a save never keeps conjured ice. Cleared by
 * {@link #clearSessionState} (wired into {@code ServerStateReset} through {@code RevampBatchB}).
 */
public final class CryoIce {
	private static final class Entry {
		final ServerLevel level;
		final BlockPos pos;
		final BlockState placed;
		final BlockState previous;
		long meltAt;

		Entry(ServerLevel level, BlockPos pos, BlockState placed, BlockState previous, long meltAt) {
			this.level = level;
			this.pos = pos;
			this.placed = placed;
			this.previous = previous;
			this.meltAt = meltAt;
		}
	}

	/** Hard cap, so a runaway loop can never pile up thousands of blocks. */
	public static final int MAX_TRACKED = 3000;
	private static final BlockParticleOption ICE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
	private static final Map<Long, Entry> BY_POS = new HashMap<>();
	private static final List<Entry> ORDER = new ArrayList<>();

	private CryoIce() {
	}

	private static long key(ServerLevel level, BlockPos pos) {
		return pos.asLong() * 31L + System.identityHashCode(level);
	}

	/** Air, plants, snow layers and water (which the cold freezes) can take conjured ice. */
	public static boolean replaceable(BlockState st) {
		return st.isAir() || st.canBeReplaced() || st.getFluidState().is(Fluids.WATER) && st.is(Blocks.WATER);
	}

	/**
	 * Places {@code state} at {@code pos} for {@code ttl} ticks (only into a replaceable cell, and only when terrain
	 * effects are on). Re-placing our own ice just extends its melt time. Returns whether ice is there now.
	 */
	public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ttl) {
		if (!AbilityHelpers.canGrief()) {
			return false;
		}
		BlockPos at = pos.immutable();
		long k = key(level, at);
		Entry existing = BY_POS.get(k);
		long now = level.getGameTime();
		if (existing != null && level.getBlockState(at) == existing.placed) {
			existing.meltAt = Math.max(existing.meltAt, now + ttl);
			return true;
		}
		BlockState cur = level.getBlockState(at);
		if (!replaceable(cur) || level.isOutsideBuildHeight(at)) {
			return false;
		}
		if (ORDER.size() >= MAX_TRACKED) {
			Entry oldest = ORDER.remove(0);
			BY_POS.remove(key(oldest.level, oldest.pos));
			restore(oldest, false);
		}
		// water left behind is a source again; anything else (grass, snow layer) comes back as it was
		BlockState previous = cur.getFluidState().is(Fluids.WATER) ? Blocks.WATER.defaultBlockState() : cur;
		level.setBlock(at, state, Block.UPDATE_ALL);
		Entry e = new Entry(level, at, state, previous, now + ttl);
		BY_POS.put(k, e);
		ORDER.add(e);
		return true;
	}

	/** Whether {@code pos} holds conjured ice right now. */
	public static boolean isConjured(ServerLevel level, BlockPos pos) {
		Entry e = BY_POS.get(key(level, pos));
		return e != null && level.getBlockState(pos) == e.placed;
	}

	public static int tracked() {
		return ORDER.size();
	}

	/** v0.16.6: melts these cells (if they still hold our ice) on the next tick -- an Ice Fortress dropped early. */
	public static void meltEarly(ServerLevel level, Iterable<BlockPos> cells) {
		long now = level.getGameTime();
		for (BlockPos pos : cells) {
			Entry e = BY_POS.get(key(level, pos));
			if (e != null) {
				e.meltAt = Math.min(e.meltAt, now);
			}
		}
	}

	/** Once per server tick: melt what is due (chunks that are loaded), forget what has been mined away. */
	public static void tick(MinecraftServer server) {
		if (ORDER.isEmpty()) {
			return;
		}
		Iterator<Entry> it = ORDER.iterator();
		int budget = 400;
		while (it.hasNext() && budget-- > 0) {
			Entry e = it.next();
			if (e.level.getGameTime() < e.meltAt) {
				continue;
			}
			if (!e.level.hasChunkAt(e.pos)) {
				continue; // waits until the chunk is loaded again (or the server stops)
			}
			it.remove();
			BY_POS.remove(key(e.level, e.pos));
			restore(e, true);
		}
	}

	private static void restore(Entry e, boolean effects) {
		if (e.level.getBlockState(e.pos) != e.placed) {
			return; // mined, replaced or already gone: never overwrite what is there now
		}
		e.level.setBlock(e.pos, e.previous, Block.UPDATE_ALL);
		if (effects && e.level.random.nextInt(3) == 0) {
			e.level.sendParticles(ICE_DUST, e.pos.getX() + 0.5, e.pos.getY() + 0.5, e.pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
			if (e.level.random.nextInt(4) == 0) {
				e.level.playSound(null, e.pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.35f, 1.4f);
			}
		}
	}

	/** Melts everything at once (server stopping -- the chunks are force-loaded by the set, so nothing is left in a save). */
	public static void meltAll() {
		for (Entry e : ORDER) {
			restore(e, false);
		}
		ORDER.clear();
		BY_POS.clear();
	}

	/** After the server has stopped: drop the references to its levels. */
	public static void clearSessionState() {
		ORDER.clear();
		BY_POS.clear();
		CryoHoldMoves.clearSessionState(); // v0.16.6: the hold moves' per-player summons / fortress cells
	}
}
