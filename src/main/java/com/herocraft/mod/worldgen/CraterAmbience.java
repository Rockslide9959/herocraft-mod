package com.herocraft.mod.worldgen;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.herocraft.mod.diagnostics.TickWatchdog;
import com.herocraft.mod.entity.MjolnirEntity;
import com.herocraft.mod.item.ModDataComponents;
import com.herocraft.mod.item.ModItems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Everything about a natural, unclaimed Mjolnir Crater that happens on its own: placing the one-time
 * hammer the first time its chunk loads, and the warning lightning/thunder while it sits there
 * unclaimed.
 *
 * <h2>Why this never scans the world</h2>
 * There is no per-tick search of loaded chunks or entities anywhere in this class. Craters enter the
 * small {@link #ACTIVE} map only from {@link ServerChunkEvents#CHUNK_LOAD} (which already tells us
 * exactly which chunk to look at) and leave it the moment they're claimed or their chunk unloads;
 * {@link #serverTick} only ever iterates that map, which in practice holds a handful of entries at
 * most -- the number of currently-loaded, still-unclaimed craters on the whole server.
 *
 * <h2>Why placement is deferred a tick instead of running in the chunk-load callback</h2>
 * <b>This is load-bearing, not a style choice -- doing the work inline is a hard server deadlock.</b>
 * {@link ServerChunkEvents#CHUNK_LOAD} fires from inside the chunk's own FULL-status task, which the
 * server thread is running while parked in {@code ServerChunkCache.getChunk -> managedBlock},
 * pumping chunk tasks as it waits. Anything in that callback that asks the chunk system for a chunk
 * -- including indirectly, via {@code Level.getHeight}, which calls {@code getChunk(FULL)} -- re-enters
 * {@code managedBlock} and waits for chunk-system progress that only the already-parked thread could
 * make. It parks in {@code Unsafe.park} and never returns: no further ticks, no autosave, no log
 * output, and (in singleplayer, where the integrated server has no watchdog) no crash either -- the
 * player can still walk around client-side while the world is permanently frozen.
 *
 * <p>That was a real shipped bug, reproduced and confirmed from a watchdog crash report whose stack
 * showed exactly this cycle, with {@link MjolnirCraterPiece#hammerSpawnPos} as the inner call. So
 * {@link #onChunkLoad} now does nothing but read data already in hand off the chunk it was handed
 * and enqueue it; every world-touching step happens in {@link #processPendingCraters} on the next
 * ordinary server tick, when the chunk system is idle and safe to query.
 */
public final class CraterAmbience {
	private static final int LIGHTNING_MIN_INTERVAL_TICKS = 80;  // 4s
	private static final int LIGHTNING_MAX_INTERVAL_TICKS = 240; // 12s
	private static final double LIGHTNING_MIN_RADIUS = 6.0;
	private static final double LIGHTNING_MAX_RADIUS = 20.0;
	/** How far around the crater center to look for an already-placed, still-resting hammer. */
	private static final double FIND_HAMMER_RADIUS = 12.0;

	private static final Map<UUID, ActiveCrater> ACTIVE = new HashMap<>();

	/**
	 * Craters whose chunk has loaded but whose hammer placement hasn't run yet -- drained at the top
	 * of the very next {@link #serverTick}. See the class javadoc: this queue is the entire reason
	 * the chunk-load callback can't deadlock the server any more.
	 */
	private static final Queue<PendingCrater> PENDING = new ConcurrentLinkedQueue<>();

	private CraterAmbience() {
	}

	public static void initialize() {
		// See TickWatchdog's javadoc -- a permanent, silent-unless-anomalous timing guard around
		// exactly the two call sites a "the world sometimes completely freezes" report named (crater
		// chunk-load placement and the ambient-lightning tick), not temporary debug spam.
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk) ->
				TickWatchdog.run("CraterAmbience.onChunkLoad", () -> onChunkLoad(level, chunk)));
		ServerChunkEvents.CHUNK_UNLOAD.register(CraterAmbience::onChunkUnload);
		ServerTickEvents.END_SERVER_TICK.register(server ->
				TickWatchdog.run("CraterAmbience.serverTick", () -> serverTick(server)));
	}

	// ---------------- placement (once per crater, ever) ----------------

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		Structure structure = resolveStructure(level);
		if (structure == null) {
			return;
		}
		StructureStart start = chunk.getStartForStructure(structure);
		if (start == null || !start.isValid()) {
			return;
		}

		MjolnirCraterPiece piece = null;
		for (StructurePiece candidate : start.getPieces()) {
			if (candidate instanceof MjolnirCraterPiece craterPiece) {
				piece = craterPiece;
				break;
			}
		}
		if (piece == null) {
			return;
		}

		// STOP. Everything below this point used to run right here, and that was the "world freezes
		// forever" bug -- see the class javadoc. Nothing in this method may touch the chunk system
		// (no getHeight, no getChunk, no entity lookup, no entity spawn); all of it is deferred.
		PENDING.add(new PendingCrater(level, chunk.getPos(), start.getChunkPos().toLong(), piece));
	}

	/**
	 * The real placement work, run on an ordinary server tick rather than inside the chunk-load
	 * callback. Every call here is one that would deadlock if made from {@link #onChunkLoad}.
	 */
	private static void processPendingCraters() {
		PendingCrater pending;
		while ((pending = PENDING.poll()) != null) {
			ServerLevel level = pending.level();
			ChunkPos origin = pending.originChunk();
			// The chunk can unload again between the event and this tick. Nothing has been marked or
			// spawned yet, so simply dropping it is safe and complete: whenever that chunk loads
			// again, CHUNK_LOAD re-queues exactly this work.
			if (!level.hasChunk(origin.x, origin.z)) {
				continue;
			}
			placeOrAdoptHammer(level, pending.piece(), pending.craterKey());
		}
	}

	private static void placeOrAdoptHammer(ServerLevel level, MjolnirCraterPiece piece, long craterKey) {
		BlockPos spawnPos = piece.hammerSpawnPos(level);
		CraterSpawnState spawnState = CraterSpawnState.get(level);

		if (!spawnState.hasSpawned(craterKey)) {
			// The one and only time this specific crater ever places a hammer. Marking it spawned
			// BEFORE actually spawning (not after) means a crash mid-spawn still can't leave the flag
			// unset -- worst case is a crater that silently never got its hammer, never a duplicate.
			spawnState.markSpawned(craterKey);
			// armedImmediately=true: this hammer has never been near a player, unlike a Q-drop or a
			// worthiness ejection landing at someone's feet -- see MjolnirEntity#pickupArmed's javadoc
			// for the gametest-caught bug this fixes (a player who walked straight up to a natural
			// hammer and stopped would otherwise never actually collect it).
			MjolnirEntity hammer = MjolnirEntity.createResting(level, null,
					new ItemStack(ModItems.MJOLNIR),
					new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5), Vec3.ZERO, true);
			level.addFreshEntity(hammer);
			activate(level, hammer, spawnPos);
			return;
		}

		// Not the first time this chunk has loaded. The hammer either survived (ordinary chunk
		// reload/server restart -- it's a normal persisted entity) or was already claimed; either way,
		// re-discover it rather than ever spawning a second one.
		MjolnirEntity existing = findUnclaimedHammer(level, spawnPos);
		if (existing != null) {
			activate(level, existing, spawnPos);
		}
	}

	/** A crater whose chunk has loaded, waiting for a safe tick to actually place/adopt its hammer. */
	private record PendingCrater(ServerLevel level, ChunkPos originChunk, long craterKey,
			MjolnirCraterPiece piece) {
	}

	private static MjolnirEntity findUnclaimedHammer(ServerLevel level, BlockPos center) {
		AABB box = new AABB(center).inflate(FIND_HAMMER_RADIUS);
		return level.getEntitiesOfClass(MjolnirEntity.class, box, CraterAmbience::isUnclaimed)
				.stream().findFirst().orElse(null);
	}

	private static boolean isUnclaimed(MjolnirEntity entity) {
		return entity.getState() == MjolnirEntity.State.RESTING
				&& entity.getItem().get(ModDataComponents.BOUND_OWNER) == null;
	}

	private static Structure resolveStructure(ServerLevel level) {
		return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(MjolnirCraterStructure.KEY);
	}

	// ---------------- ambient lightning while unclaimed ----------------

	private static void activate(ServerLevel level, MjolnirEntity hammer, BlockPos craterCenter) {
		ACTIVE.computeIfAbsent(hammer.getUUID(), id -> {
			ActiveCrater active = new ActiveCrater(level, craterCenter, id);
			active.nextStrikeTick = level.getGameTime() + randomInterval(level.random);
			return active;
		});
	}

	/**
	 * Forget every active crater and any queued placement. Called from {@code ServerStateReset} once
	 * the owning server has stopped: both collections hold a {@link ServerLevel} (and {@code PENDING}
	 * additionally holds a {@link MjolnirCraterPiece}), which would otherwise keep the whole previous
	 * world reachable when a singleplayer client opens the next one in the same JVM. Nothing is lost --
	 * a crater re-activates from {@link #onChunkLoad} the next time its chunk loads.
	 */
	public static void clearSessionState() {
		ACTIVE.clear();
		PENDING.clear();
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		ChunkPos unloading = chunk.getPos();
		ACTIVE.values().removeIf(active -> active.level == level
				&& new ChunkPos(active.craterCenter).equals(unloading));
	}

	private static void serverTick(MinecraftServer server) {
		// Deferred chunk-load work first -- see the class javadoc. This is where a newly loaded
		// crater actually gets its hammer, safely outside the chunk-load callback.
		processPendingCraters();

		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<ActiveCrater> it = ACTIVE.values().iterator();
		while (it.hasNext()) {
			ActiveCrater active = it.next();
			if (!stillUnclaimed(active)) {
				// Taken, bound, or otherwise gone: the crater stays, the light show stops for good --
				// nothing re-adds this entry, since placement only ever happens once per crater.
				it.remove();
				continue;
			}

			long now = active.level.getGameTime();
			if (now < active.nextStrikeTick) {
				continue;
			}
			strike(active);
			active.nextStrikeTick = now + randomInterval(active.level.random);
		}
	}

	private static boolean stillUnclaimed(ActiveCrater active) {
		Entity entity = active.level.getEntity(active.hammerId);
		return entity instanceof MjolnirEntity hammer && !hammer.isRemoved() && isUnclaimed(hammer);
	}

	/** Visual-only lightning (no fire, no entity damage) plus a distance-attenuated thunder cue and a
	 * restrained spark flourish -- see requirements 7-11: a warning, not a hazard. */
	private static void strike(ActiveCrater active) {
		RandomSource random = active.level.random;
		double angle = random.nextDouble() * Math.PI * 2.0;
		double radius = Mth.lerp(random.nextDouble(), LIGHTNING_MIN_RADIUS, LIGHTNING_MAX_RADIUS);
		double x = active.craterCenter.getX() + 0.5 + Math.cos(angle) * radius;
		double z = active.craterCenter.getZ() + 0.5 + Math.sin(angle) * radius;
		int y = active.level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));

		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(active.level);
		if (bolt != null) {
			bolt.moveTo(x, y, z);
			bolt.setVisualOnly(true);
			active.level.addFreshEntity(bolt);
		}

		active.level.playSound(null, active.craterCenter.getX() + 0.5, active.craterCenter.getY() + 0.5,
				active.craterCenter.getZ() + 0.5, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT,
				1.0f, 0.85f + random.nextFloat() * 0.25f);

		if (random.nextFloat() < 0.5f) {
			active.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					active.craterCenter.getX() + 0.5, active.craterCenter.getY() + 0.6, active.craterCenter.getZ() + 0.5,
					6, 0.4, 0.3, 0.4, 0.02);
		}
	}

	private static int randomInterval(RandomSource random) {
		return LIGHTNING_MIN_INTERVAL_TICKS
				+ random.nextInt(LIGHTNING_MAX_INTERVAL_TICKS - LIGHTNING_MIN_INTERVAL_TICKS + 1);
	}

	private static final class ActiveCrater {
		final ServerLevel level;
		final BlockPos craterCenter;
		final UUID hammerId;
		long nextStrikeTick;

		ActiveCrater(ServerLevel level, BlockPos craterCenter, UUID hammerId) {
			this.level = level;
			this.craterCenter = craterCenter;
			this.hammerId = hammerId;
		}
	}
}
