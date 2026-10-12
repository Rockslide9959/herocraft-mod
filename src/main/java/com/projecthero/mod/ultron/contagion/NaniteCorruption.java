package com.projecthero.mod.ultron.contagion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.block.ContagionBlocks;

import it.unimi.dsi.fastutil.longs.Long2IntMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>Nanite Corruption</b> -- how a Nexus changes the land, and how it is put back.
 *
 * <h2>Spread (safety rules)</h2>
 * Each Nexus walks the columns round it in order of distance (a precomputed spiral, {@link #OFFSETS}), a few columns a
 * tick and never past its current radius. In a column it converts, from the top down, natural blocks only:
 * <ul>
 *   <li>the block must be in the {@code projecthero:contagion_corruptible} tag (grass / dirt / stone / sand / gravel /
 *       natural logs / leaves);</li>
 *   <li>never a block entity, never leaves a player placed ({@code persistent}), never a block within
 *       {@link ContagionConfig.Nexus#blockEntityClearance} of any block entity (a base's chests, furnaces and beds keep the
 *       whole base clean), never inside a fuelled Firewall Beacon's radius;</li>
 *   <li>it stops at the first block that is neither corruptible nor see-through, so a player's floor or wall shields what
 *       is under it, and after two ground blocks.</li>
 * </ul>
 * Every converted block's original state is recorded in {@link ContagionState} <em>before</em> it changes. Blocks are set
 * with UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE: no neighbour updates, so nothing pops off, falls or decays.
 *
 * <h2>Revert</h2>
 * {@link #tickRevert} puts recorded blocks back a few hundred a tick (nearest first, the "blue wave"), only where the block
 * is still the corruption's own (a block the player broke or replaced is left as it is), and only in loaded chunks --
 * the rest wait in the persisted queue for their chunk.
 */
public final class NaniteCorruption {
	/** Column offsets (dx, dz) within 64 blocks, nearest first. */
	static final int[] OFFSETS = buildOffsets(64);
	/** The number of offsets within radius r is {@code RING_END[r]}. */
	static final int[] RING_END = buildRingEnd(64);

	/** Where the revert queue's walk is up to (rebuilt from 0 whenever the queue is compacted). */
	private static int revertHead;
	private static long nextFullPassAt;

	private NaniteCorruption() {
	}

	private static int[] buildOffsets(int r) {
		List<int[]> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz <= r * r) {
					list.add(new int[] { dx, dz });
				}
			}
		}
		list.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
		int[] out = new int[list.size() * 2];
		for (int i = 0; i < list.size(); i++) {
			out[i * 2] = list.get(i)[0];
			out[i * 2 + 1] = list.get(i)[1];
		}
		return out;
	}

	private static int[] buildRingEnd(int r) {
		int[] end = new int[r + 1];
		int n = OFFSETS.length / 2;
		for (int rr = 0; rr <= r; rr++) {
			int count = 0;
			for (int i = 0; i < n; i++) {
				int dx = OFFSETS[i * 2], dz = OFFSETS[i * 2 + 1];
				if (dx * dx + dz * dz <= rr * rr) {
					count = i + 1;
				}
			}
			end[rr] = count;
		}
		return end;
	}

	public static void clearSessionState() {
		revertHead = 0;
		nextFullPassAt = 0;
	}

	// ---------------------------------------------------------------- what may be corrupted, and into what

	/** The corruption's version of {@code state}, or null if it must never be touched. */
	public static BlockState corruptedFor(BlockState state) {
		if (!state.is(ContagionBlocks.CORRUPTIBLE) || state.hasBlockEntity()) {
			return null;
		}
		if (state.is(BlockTags.LEAVES)) {
			if (state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)) {
				return null; // placed by a player
			}
			return ContagionBlocks.CORRUPTED_LEAVES.defaultBlockState();
		}
		if (state.is(BlockTags.LOGS)) {
			BlockState log = ContagionBlocks.CORRUPTED_LOG.defaultBlockState();
			return state.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)) : log;
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
			return ContagionBlocks.CORRUPTED_STONE.defaultBlockState();
		}
		return ContagionBlocks.CORRUPTED_CIRCUITRY.defaultBlockState();
	}

	/** Is there a block entity within {@code r} blocks of {@code pos}? (Loaded chunks only; an unloaded one counts as yes.) */
	public static boolean nearBlockEntity(ServerLevel level, BlockPos pos, int r) {
		if (r <= 0) {
			return false;
		}
		int r2 = r * r;
		for (int cx = SectionPos.blockToSectionCoord(pos.getX() - r); cx <= SectionPos.blockToSectionCoord(pos.getX() + r); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(pos.getZ() - r); cz <= SectionPos.blockToSectionCoord(pos.getZ() + r); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					return true;
				}
				for (BlockPos be : chunk.getBlockEntitiesPos()) {
					if (be.distSqr(pos) <= r2) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- spread

	/**
	 * One tick of {@code rec}'s spread: up to {@code columns} columns and {@code budget} blocks. Returns the blocks changed.
	 * Never loads a chunk: a column whose chunk isn't loaded is skipped for now (the cursor waits on it).
	 */
	public static int spread(ServerLevel level, ContagionState state, ContagionState.NexusRecord rec, int columns, int budget) {
		int end = RING_END[Math.max(0, Math.min(rec.radius, RING_END.length - 1))];
		int changed = 0;
		long now = level.getGameTime();
		int clearance = ContagionConfig.nexus().blockEntityClearance;
		for (int c = 0; c < columns && rec.cursor < end && changed < budget; c++) {
			int i = rec.cursor;
			int x = rec.pos.getX() + OFFSETS[i * 2];
			int z = rec.pos.getZ() + OFFSETS[i * 2 + 1];
			if (level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)) == null) {
				return changed; // wait for it; never force a load
			}
			rec.cursor++;
			BlockPos column = new BlockPos(x, rec.pos.getY(), z);
			if (state.protectedByBeacon(column, now)) {
				Contagion.probeBeacons(level, column);
				continue;
			}
			if (nearBlockEntity(level, column, clearance)) {
				continue;
			}
			changed += corruptColumn(level, state, rec, x, z, budget - changed);
		}
		if (changed > 0) {
			state.setDirty();
		}
		return changed;
	}

	/** Converts the natural blocks at the top of column (x, z). */
	static int corruptColumn(ServerLevel level, ContagionState state, ContagionState.NexusRecord rec, int x, int z, int budget) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		int ground = 0;
		int changed = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = top; y > top - 24 && y > level.getMinBuildHeight() && changed < budget && ground < 2; y--) {
			p.set(x, y, z);
			BlockState st = level.getBlockState(p);
			if (st.isAir() || ContagionBlocks.isCorruption(st.getBlock())) {
				continue;
			}
			BlockState to = corruptedFor(st);
			if (to == null) {
				if (st.canBeReplaced() || !st.isSolid() && st.getFluidState().isEmpty()) {
					continue; // grass tufts, flowers, snow layers: look under them
				}
				break; // anything else (a floor, water, ore...) shields what is below
			}
			long key = p.asLong();
			if (state.recordAt(key) == null) {
				rec.blocks.put(key, state.stateId(st));
			}
			level.setBlock(p, to, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			changed++;
			if (!st.is(BlockTags.LOGS) && !st.is(BlockTags.LEAVES)) {
				ground++;
			}
		}
		return changed;
	}

	// ---------------------------------------------------------------- revert

	/** Puts back up to {@code budget} queued blocks (loaded chunks only). Returns how many were put back. */
	public static int tickRevert(ServerLevel level, ContagionState state, int budget) {
		int size = state.pendingPos.size();
		if (size == 0) {
			revertHead = 0;
			return 0;
		}
		if (revertHead >= size) {
			compact(state);
			revertHead = 0;
			size = state.pendingPos.size();
			if (size == 0 || level.getGameTime() < nextFullPassAt) {
				return 0;
			}
		}
		int done = 0;
		int examined = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		while (revertHead < size && done < budget && examined < budget * 4) {
			int i = revertHead++;
			examined++;
			long key = state.pendingPos.getLong(i);
			if (key == Long.MIN_VALUE) {
				continue;
			}
			p.set(key);
			if (!level.isLoaded(p)) {
				continue;
			}
			revertOne(level, state, p, state.pendingState.getInt(i));
			state.pendingPos.set(i, Long.MIN_VALUE);
			done++;
			if (done % 24 == 0) {
				level.sendParticles(UltronFx.CYAN, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.0);
			}
		}
		if (revertHead >= size) {
			// a whole pass: anything left is waiting for its chunk; look again in 5 s
			nextFullPassAt = level.getGameTime() + 100;
		}
		if (done > 0) {
			state.setDirty();
		}
		return done;
	}

	private static void compact(ContagionState state) {
		int w = 0;
		for (int r = 0; r < state.pendingPos.size(); r++) {
			long k = state.pendingPos.getLong(r);
			if (k != Long.MIN_VALUE) {
				state.pendingPos.set(w, k);
				state.pendingState.set(w, state.pendingState.getInt(r));
				w++;
			}
		}
		state.pendingPos.size(w);
		state.pendingState.size(w);
	}

	/** Puts the recorded original back at {@code p} if the corruption's block is still there. */
	static boolean revertOne(ServerLevel level, ContagionState state, BlockPos p, int stateId) {
		BlockState now = level.getBlockState(p);
		BlockState orig = state.state(stateId);
		if (orig == null || !ContagionBlocks.isCorruption(now.getBlock())) {
			return false;
		}
		level.setBlock(p, orig, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return true;
	}

	/**
	 * Queues the whole of a fallen Nexus's record, nearest first (so it reads as a blue wave rolling out from the spire),
	 * and drops the record.
	 */
	public static void queueRevertWave(ContagionState state, java.util.UUID nexusId) {
		ContagionState.NexusRecord rec = state.nexus.get(nexusId);
		if (rec == null) {
			return;
		}
		long[] keys = new long[rec.blocks.size()];
		int[] ids = new int[rec.blocks.size()];
		int k = 0;
		for (Long2IntMap.Entry e : rec.blocks.long2IntEntrySet()) {
			keys[k] = e.getLongKey();
			ids[k] = e.getIntValue();
			k++;
		}
		Integer[] order = new Integer[keys.length];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		BlockPos c = rec.pos;
		Arrays.sort(order, java.util.Comparator.comparingDouble(i -> BlockPos.of(keys[i]).distSqr(c)));
		// appended after whatever is still queued: the walk reaches the wave nearest-first
		for (Integer i : order) {
			state.pendingPos.add(keys[i]);
			state.pendingState.add(ids[i]);
		}
		state.nexus.remove(nexusId);
		state.compromised.values().removeIf(m -> nexusId.equals(m.nexus));
		state.setDirty();
	}

	/** A Purge Charge: puts back every recorded block within {@code radius} of {@code centre} right now. Returns the count. */
	public static int purgeArea(ServerLevel level, ContagionState state, Vec3 centre, double radius) {
		int r = (int) Math.ceil(radius);
		BlockPos c = BlockPos.containing(centre);
		int n = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (ContagionState.NexusRecord rec : state.nexus.values()) {
			if (rec.blocks.isEmpty() || rec.pos.distSqr(c) > (double) (r + ContagionConfig.nexus().maxRadius + 8) * (r + ContagionConfig.nexus().maxRadius + 8)) {
				continue;
			}
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx * dx + dy * dy + dz * dz > radius * radius) {
							continue;
						}
						p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
						long key = p.asLong();
						if (!rec.blocks.containsKey(key)) {
							continue;
						}
						int id = rec.blocks.remove(key);
						if (revertOne(level, state, p, id)) {
							n++;
						}
					}
				}
			}
		}
		if (n > 0) {
			state.setDirty();
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y + 0.5, centre.z, 20, radius * 0.4, 0.5, radius * 0.4, 0.1);
		}
		return n;
	}
}
