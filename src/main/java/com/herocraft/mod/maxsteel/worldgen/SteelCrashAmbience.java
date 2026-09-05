package com.herocraft.mod.maxsteel.worldgen;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.herocraft.mod.diagnostics.TickWatchdog;
import com.herocraft.mod.maxsteel.entity.SteelEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Places the one-time Steel entity at a {@link SteelCrashSiteStructure} the first time its chunk
 * loads. Directly modelled on {@code CraterAmbience}, including the part that matters most:
 *
 * <h2>Placement is deferred one tick -- this is load-bearing, not a style choice</h2>
 * {@link ServerChunkEvents#CHUNK_LOAD} fires from inside the chunk's own FULL-status task while the
 * server thread is parked in {@code ServerChunkCache.getChunk -> managedBlock}. Anything in that
 * callback that asks the chunk system for a chunk -- including via {@code Level.getHeight} (used by
 * {@link SteelCrashSitePiece#steelHoverPos}) or {@code addFreshEntity} -- re-enters {@code
 * managedBlock} and deadlocks the server permanently. That was a real shipped freeze for the Mjolnir
 * crater; this class avoids it the same way: the callback only enqueues, and {@link
 * #processPending} does every world-touching step on the next ordinary server tick.
 *
 * <p>No per-tick world scan: sites enter {@link #PENDING} only from the chunk-load event (which names
 * the chunk) and are drained once.
 */
public final class SteelCrashAmbience {
	private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();

	private SteelCrashAmbience() {
	}

	public static void initialize() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk) ->
				TickWatchdog.run("SteelCrashAmbience.onChunkLoad", () -> onChunkLoad(level, chunk)));
		ServerTickEvents.END_SERVER_TICK.register(server ->
				TickWatchdog.run("SteelCrashAmbience.tick", () -> tick(server)));
	}

	public static void clearSessionState() {
		PENDING.clear();
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
				.get(SteelCrashSiteStructure.KEY);
		if (structure == null) {
			return;
		}
		StructureStart start = chunk.getStartForStructure(structure);
		if (start == null || !start.isValid()) {
			return;
		}
		SteelCrashSitePiece piece = null;
		for (StructurePiece candidate : start.getPieces()) {
			if (candidate instanceof SteelCrashSitePiece p) {
				piece = p;
				break;
			}
		}
		if (piece == null) {
			return;
		}
		// STOP -- no getHeight / getChunk / entity work here (deadlock). Defer everything.
		PENDING.add(new Pending(level, chunk.getPos(), start.getChunkPos().toLong(), piece));
	}

	private static void tick(MinecraftServer server) {
		Pending pending;
		while ((pending = PENDING.poll()) != null) {
			ServerLevel level = pending.level();
			ChunkPos origin = pending.originChunk();
			if (!level.hasChunk(origin.x, origin.z)) {
				continue; // unloaded again before we got to it; CHUNK_LOAD will re-queue
			}
			placeOrAdoptSteel(level, pending.piece(), pending.siteKey());
		}
	}

	private static void placeOrAdoptSteel(ServerLevel level, SteelCrashSitePiece piece, long siteKey) {
		BlockPos hover = piece.steelHoverPos(level);
		SteelCrashSpawnState state = SteelCrashSpawnState.get(level);

		if (!state.hasSpawned(siteKey)) {
			// Mark BEFORE spawning: a crash mid-spawn can only ever leave a site with no Steel, never a
			// duplicate.
			state.markSpawned(siteKey);
			SteelEntity.spawn(level, hover.getX() + 0.5, hover.getY(), hover.getZ() + 0.5);
			return;
		}
		// Already spawned once. The Steel either persisted (ordinary reload) or was bonded and gone.
		// Either way, never spawn a second one.
	}

	private record Pending(ServerLevel level, ChunkPos originChunk, long siteKey, SteelCrashSitePiece piece) {
	}
}
