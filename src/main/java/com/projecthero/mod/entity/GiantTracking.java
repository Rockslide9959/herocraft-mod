package com.projecthero.mod.entity;

import java.util.Comparator;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4: giants that must be seen from far outside a player's chunk view (Galactus in the orbit stages and cutscenes,
 * the Worldship).
 *
 * <p>The bug this fixes ("Galactus is invisible at a low render distance"), in three layers:
 * <ol>
 * <li><b>Server tracking.</b> {@code ChunkMap.TrackedEntity.updatePlayer} only sends an entity to a player when the
 * entity's chunk is inside the player's chunk view and it is within {@code min(trackingRange, viewDistance * 16)} blocks.
 * The Devourer stands 50-120 blocks out (his position is his chest), so at render distance 2-4 he was never sent at all.
 * {@link AlwaysTracked} entities skip both view-distance rules ({@code TrackedEntityGiantMixin}) and are paired with every
 * player of their level within their own tracking range.</li>
 * <li><b>Client ticking.</b> The client only ticks an entity whose chunk it has loaded, so a giant in a chunk outside the
 * view froze (no position interpolation, no rig-input history, no animation clock). {@link AlwaysTracked} entities
 * return {@code true} from {@code isAlwaysTicking()} on the client (see {@link #alwaysTicking}).</li>
 * <li><b>Client drawing.</b> {@code LevelRenderer} skips an entity whose block position's chunk section hasn't been
 * compiled; for these giants it never is. {@code LevelRendererGiantMixin} lets them through.</li>
 * </ol>
 * Plus: a giant spawned in a chunk the server hasn't loaded is never tracked at all (C2's Devourer rises 105 blocks out
 * in the overworld, which has no raid ticket), so the raid keeps that chunk loaded with {@link #keepLoaded}.
 */
public final class GiantTracking {
	private GiantTracking() {
	}

	/** An entity sent to (and drawn for) every player of its level within its tracking range, whatever their view distance. */
	public interface AlwaysTracked {
	}

	/** Implemented on {@code ChunkMap.TrackedEntity} by {@code TrackedEntityGiantMixin} (for tests). */
	public interface Tracked {
		/** Is this entity currently sent to {@code player}? */
		boolean projecthero$sees(ServerPlayer player);

		/** Re-runs the vanilla pairing decision for {@code player}. */
		void projecthero$updatePlayer(ServerPlayer player);
	}

	/** Implemented on {@code ChunkMap} by {@code ChunkMapGiantMixin} (for tests). */
	public interface Map {
		Tracked projecthero$tracked(int entityId);
	}

	/** Short-lived ticket: refreshed every few ticks while the raid needs the chunk, so nothing has to remove it. */
	private static final TicketType<ChunkPos> TICKET = TicketType.create("projecthero_giant", Comparator.comparingLong(ChunkPos::toLong), 60);

	/** Level 31 at the centre (entity ticking: an entity there ticks and its moves are sent). */
	private static final int TICKET_RADIUS = 2;

	/** Keeps the chunk at {@code at} loaded and entity-ticking for the next 3 s (call again to extend). */
	public static void keepLoaded(ServerLevel level, Vec3 at) {
		ChunkPos c = new ChunkPos(SectionPos.blockToSectionCoord(at.x), SectionPos.blockToSectionCoord(at.z));
		level.getChunkSource().addRegionTicket(TICKET, c, TICKET_RADIUS, c);
	}

	/**
	 * The value an {@link AlwaysTracked} entity's {@code isAlwaysTicking()} returns: always on the client (its chunk may be
	 * one the client never loads), vanilla on the server (the server still gates ticking on the chunk).
	 */
	public static boolean alwaysTicking(net.minecraft.world.entity.Entity e, boolean vanilla) {
		return vanilla || e.level().isClientSide();
	}
}
