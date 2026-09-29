package com.projecthero.mod.symbiote.worldgen;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.projecthero.mod.diagnostics.TickWatchdog;
import com.projecthero.mod.symbiote.block.SymbioteBlocks;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;

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
 * One-time setup of a {@link SymbioteMeteorStructure} or {@link SymbioteLabStructure} the first time its
 * chunk loads. A lab gets its confined {@link SymbioteEntity}; a meteor (v0.13.19) gets nothing -- its
 * Symbiote is inside the Symbiote Meteorite block the crater generates with -- unless it is an older crater
 * with no meteorite, which gets one placed at its centre.
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
			return; // handled once already; the entity persisted / bonded, or the meteorite is there. Never again.
		}
		// Mark BEFORE touching the world: a crash mid-way can only ever leave a site with nothing extra.
		state.markSpawned(siteKey);
		if (piece instanceof SymbioteMeteorPiece meteor) {
			// v0.13.19: the meteor's Symbiote waits inside the Symbiote Meteorite block and only crawls out when
			// a player breaks it (SymbioteMeteoriteBlock). New craters generate the block themselves; an older
			// crater from before the block existed gets one placed at its centre instead of a free entity.
			if (meteor.findMeteorite(level) == null) {
				level.setBlock(meteor.meteoritePos(level), SymbioteBlocks.SYMBIOTE_METEORITE.defaultBlockState(), 3);
			}
			return;
		}
		// A lab: the Symbiote is loose in its containment cell -- confined to it for good.
		BlockPos pos = piece.symbiotePos(level);
		SymbioteEntity.spawnConfined(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
				SymbioteEntity.LAB_HOME_RADIUS);
	}

	private record Pending(ServerLevel level, ChunkPos originChunk, long siteKey, SymbioteSpawnPiece piece) {
	}
}
