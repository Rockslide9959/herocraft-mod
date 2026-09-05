package com.herocraft.mod.symbiote.worldgen;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.herocraft.mod.diagnostics.TickWatchdog;
import com.herocraft.mod.symbiote.entity.SymbioteEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Places the one-time {@link SymbioteEntity} at a {@link SymbioteMeteorStructure} or
 * {@link SymbioteLabStructure} the first time its chunk loads. One class handles both, since the only
 * difference is which structure key to look up and which {@link SymbioteSpawnPiece} to ask.
 *
 * <p>Directly modelled on {@code SteelCrashAmbience}, including the load-bearing part:
 *
 * <h2>Placement is deferred one tick</h2>
 * {@link ServerChunkEvents#CHUNK_LOAD} fires from inside the chunk's own FULL-status task while the
 * server thread is parked in {@code managedBlock}. Anything in that callback that asks the chunk
 * system for a chunk -- including {@code Level.getHeight} (used by the meteor's {@code symbiotePos}) or
 * {@code addFreshEntity} -- re-enters {@code managedBlock} and deadlocks the server permanently. So the
 * callback only enqueues; {@link #tick} does every world-touching step on the next ordinary tick.
 */
public final class SymbioteWorldgen {
	private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();

	private SymbioteWorldgen() {
	}

	public static void initialize() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk) ->
				TickWatchdog.run("SymbioteWorldgen.onChunkLoad", () -> onChunkLoad(level, chunk)));
		ServerTickEvents.END_SERVER_TICK.register(server ->
				TickWatchdog.run("SymbioteWorldgen.tick", () -> tick(server)));
	}

	public static void clearSessionState() {
		PENDING.clear();
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		check(level, chunk, SymbioteMeteorStructure.KEY);
		check(level, chunk, SymbioteLabStructure.KEY);
	}

	private static void check(ServerLevel level, LevelChunk chunk, ResourceKey<Structure> key) {
		Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
		if (structure == null) {
			return;
		}
		StructureStart start = chunk.getStartForStructure(structure);
		if (start == null || !start.isValid()) {
			return;
		}
		SymbioteSpawnPiece piece = null;
		for (StructurePiece candidate : start.getPieces()) {
			if (candidate instanceof SymbioteSpawnPiece p) {
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
			place(level, pending.piece(), pending.siteKey());
		}
	}

	private static void place(ServerLevel level, SymbioteSpawnPiece piece, long siteKey) {
		SymbioteSpawnState state = SymbioteSpawnState.get(level);
		if (state.hasSpawned(siteKey)) {
			return; // spawned once already; the entity persisted or was bonded. Never a second one.
		}
		BlockPos pos = piece.symbiotePos(level);
		// Mark BEFORE spawning: a crash mid-spawn can only ever leave a site with no Symbiote.
		state.markSpawned(siteKey);
		SymbioteEntity.spawn(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5);
	}

	private record Pending(ServerLevel level, ChunkPos originChunk, long siteKey, SymbioteSpawnPiece piece) {
	}
}
