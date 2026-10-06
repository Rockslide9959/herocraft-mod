package com.projecthero.mod.ironman.suit;

import java.util.Comparator;

import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 (agent D): "the suit calling and everything related has to work even if in unloaded chunks".
 *
 * <p>Every flow that has to reach a Suit Platform the player is nowhere near (the far call, the send-home deposit,
 * the death-return queue) goes through {@link #loadPlatform}: it puts a short-lived chunk ticket of our own type on
 * the platform's chunk and loads it synchronously, so the block entity itself takes / stores the real stacks and the
 * chunk stays loaded long enough to be saved with the change (the ticket expires by itself after
 * {@link #TICKET_TICKS} ticks -- nothing to clean up, nothing static to reset).
 *
 * <p>Flying suit parts and the Mark 7 pod ask {@link #entityTicking} before every step: an entity that wanders into a
 * chunk that is loaded but not entity-ticking freezes there and is saved with that chunk (where its stack would sit
 * until somebody walks by), so they deliver / hand over instead of stepping into one.
 */
public final class IronManChunkTickets {
	/** How long a platform chunk stays loaded after we touched it (ticks). */
	public static final int TICKET_TICKS = 100;

	/** Our own ticket type so it is obvious in a ticket dump what is holding the chunk. */
	public static final TicketType<ChunkPos> SUIT_CALL =
			TicketType.create("projecthero_suit_call", Comparator.comparingLong(ChunkPos::toLong), TICKET_TICKS);

	private IronManChunkTickets() {
	}

	/** Load (synchronously) and briefly hold the chunk containing {@code pos}. */
	public static void hold(ServerLevel level, BlockPos pos) {
		ChunkPos cp = new ChunkPos(pos);
		// radius 0 = ticket level 33: the chunk is fully loaded (block entities live, changes saved) without forcing
		// a ring of neighbours to generate the way a ticking ticket would
		level.getChunkSource().addRegionTicket(SUIT_CALL, cp, 0, cp);
		level.getChunk(cp.x, cp.z); // full, synchronous -- the block entity is there when this returns
	}

	/** The Suit Platform at {@code pos} with its chunk loaded and held, or {@code null} if no platform stands there. */
	public static IronManSuitPlatformBlockEntity loadPlatform(ServerLevel level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos)) {
			return null;
		}
		hold(level, pos);
		return level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be ? be : null;
	}

	/** As {@link #loadPlatform(ServerLevel, BlockPos)} for a platform in any dimension; null if that level is absent. */
	public static IronManSuitPlatformBlockEntity loadPlatform(MinecraftServer server, GlobalPos at) {
		ServerLevel level = server.getLevel(at.dimension());
		return level == null ? null : loadPlatform(level, at.pos());
	}

	/** True when an entity standing at {@code p} would keep ticking (its chunk is loaded with entities ticking). */
	public static boolean entityTicking(ServerLevel level, Vec3 p) {
		return level.isPositionEntityTicking(BlockPos.containing(p));
	}

	public static boolean entityTicking(ServerLevel level, BlockPos p) {
		return level.isPositionEntityTicking(p);
	}
}
