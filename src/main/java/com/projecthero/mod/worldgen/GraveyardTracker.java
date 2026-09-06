package com.projecthero.mod.worldgen;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * A tiny cache of where Graveyards are, so "is this zombie spawning near a Graveyard?" can be answered
 * without ever running a structure search.
 *
 * <h2>Why not just call the structure locator</h2>
 * The design is explicit that Graveyard proximity must not cost a world-wide structure search per
 * zombie spawn, and that performance wins over the proximity bonus if it comes to it.
 * {@code ServerLevel#findNearestMapStructure} walks outward chunk by chunk running the placement
 * calculator; on the natural-spawn path, which runs many times a second, that is exactly the
 * "expensive structure check" the spec forbids.
 *
 * <p>So nothing is ever searched. Graveyards are recorded <em>as a side effect of their chunk
 * loading</em> -- the same hook {@code CraterAmbience} uses for craters -- and the proximity test is a
 * distance comparison against a handful of cached longs. It costs nothing per spawn, it needs no
 * chunk access, and it degrades gracefully: a Graveyard nobody has ever been near simply does not
 * grant the bonus yet, which is the harmless direction to be wrong in.
 *
 * <h2>Bounded and session-scoped</h2>
 * Entries are packed {@link ChunkPos} longs keyed by dimension id -- no {@code ServerLevel},
 * no entity, nothing that could pin a world in memory -- and the per-dimension set is capped at
 * {@value #MAX_PER_DIMENSION} with oldest-out eviction, so a very long exploration session cannot grow
 * it without bound. {@code ServerStateReset} clears it when the server stops.
 */
public final class GraveyardTracker {
	private static final int MAX_PER_DIMENSION = 256;

	/** dimension id -> packed chunk positions of known Graveyards. */
	private static final Map<ResourceLocation, Set<Long>> KNOWN = new ConcurrentHashMap<>();

	private GraveyardTracker() {
	}

	public static void initialize() {
		ServerChunkEvents.CHUNK_LOAD.register(GraveyardTracker::onChunkLoad);
	}

	/**
	 * Records a Graveyard the first time one of its chunks loads. Deliberately does nothing but read
	 * data already attached to the chunk it was handed and put a long in a set -- no height queries, no
	 * entity lookups, no further chunk access. (See {@code CraterAmbience}'s class javadoc for the
	 * deadlock this restraint avoids: touching the chunk system from inside a chunk-load callback is
	 * how that bug happened.)
	 */
	private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
				.get(GraveyardStructure.KEY);
		if (structure == null) {
			return;
		}
		StructureStart start = chunk.getStartForStructure(structure);
		if (start == null || !start.isValid()) {
			return;
		}
		Set<Long> set = KNOWN.computeIfAbsent(level.dimension().location(), key -> new LinkedHashSet<>());
		synchronized (set) {
			ChunkPos origin = start.getChunkPos();
			if (set.add(origin.toLong()) && set.size() > MAX_PER_DIMENSION) {
				Iterator<Long> it = set.iterator();
				it.next();
				it.remove();
			}
		}
	}

	/**
	 * @return true if a known Graveyard lies within {@code radiusBlocks} of {@code pos}. Pure
	 *         arithmetic over a small cached set -- safe to call from a spawn path.
	 */
	public static boolean nearGraveyard(ServerLevel level, BlockPos pos, int radiusBlocks) {
		Set<Long> set = KNOWN.get(level.dimension().location());
		if (set == null || set.isEmpty()) {
			return false;
		}
		long radiusSq = (long) radiusBlocks * radiusBlocks;
		synchronized (set) {
			for (long packed : set) {
				ChunkPos chunk = new ChunkPos(packed);
				long dx = (long) chunk.getMiddleBlockX() - pos.getX();
				long dz = (long) chunk.getMiddleBlockZ() - pos.getZ();
				if (dx * dx + dz * dz <= radiusSq) {
					return true;
				}
			}
		}
		return false;
	}

	/** Manually record a Graveyard, for the debug command and for a raid started from a known site. */
	public static void remember(ServerLevel level, BlockPos pos) {
		Set<Long> set = KNOWN.computeIfAbsent(level.dimension().location(), key -> new LinkedHashSet<>());
		synchronized (set) {
			set.add(new ChunkPos(pos).toLong());
		}
	}

	public static void clearSessionState() {
		KNOWN.clear();
	}
}
