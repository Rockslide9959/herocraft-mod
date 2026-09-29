package com.projecthero.mod.event.raid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.network.RaidSkyPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Telling clients they are inside a Zombie Raid area, so the sky can be washed dark purple there
 * ("the timer ran out here"). The raid bar itself is a vanilla {@code ServerBossEvent} and needs no
 * packets from us -- this class only carries the sky-tint flag.
 *
 * <p>The point is the {@link #LAST_SENT} de-duplication: {@link #pushSky} is called every event
 * tick, but a packet only goes out when a player actually crosses the zone boundary. Keyed by player
 * UUID, pruned when a player is told to clear, and dropped outright when the raid ends -- so it
 * cannot grow.
 */
public final class ZombieRaidNetworking {
	private static final Map<UUID, RaidSkyPayload> LAST_SENT = new HashMap<>();
	/** Extra blocks past the raid-bar radius where the tint has already begun fading in. */
	private static final double FADE_MARGIN = 24.0;

	private ZombieRaidNetworking() {
	}

	public static void pushSky(ServerLevel level, ZombieRaid raid, double radius) {
		pushSky(level, raid.center(), radius, RaidSkyPayload.ZOMBIE_RAID_VIOLET);
	}

	/**
	 * v0.13.18: the same zone push for any raid, with its own sky colour (the Darkseid Raid uses it too). A colour
	 * change for a player already inside is just another de-duplicated packet.
	 */
	public static void pushSky(ServerLevel level, BlockPos c, double radius, int color) {
		if (c == null) {
			return;
		}
		RaidSkyPayload active = new RaidSkyPayload(true, c.getX(), c.getY(), c.getZ(), (float) radius, color);
		double outer = radius + FADE_MARGIN;
		double outerSq = outer * outer;
		for (ServerPlayer player : level.players()) {
			boolean near = player.distanceToSqr(c.getX() + 0.5, player.getY(), c.getZ() + 0.5) <= outerSq;
			RaidSkyPayload want = near ? active : RaidSkyPayload.CLEAR;
			RaidSkyPayload previous = LAST_SENT.get(player.getUUID());
			if (want.equals(previous)) {
				continue;
			}
			if (!want.active() && previous == null) {
				continue; // never showed this player the tint -- nothing to clear
			}
			if (!want.active() && (previous.x() != c.getX() || previous.z() != c.getZ())) {
				continue; // they are being shown a different raid's sky -- that raid clears its own
			}
			if (want.active()) {
				LAST_SENT.put(player.getUUID(), want);
			} else {
				LAST_SENT.remove(player.getUUID());
			}
			ServerPlayNetworking.send(player, want);
		}
	}

	/** Raid over: clear the tint for anyone who was being shown it. */
	public static void clearSky(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (LAST_SENT.remove(player.getUUID()) != null) {
				ServerPlayNetworking.send(player, RaidSkyPayload.CLEAR);
			}
		}
	}

	/** v0.13.18: clear only the players currently shown the sky of the raid centred at {@code c}. */
	public static void clearSky(ServerLevel level, BlockPos c) {
		if (c == null) {
			clearSky(level);
			return;
		}
		for (ServerPlayer player : level.players()) {
			RaidSkyPayload previous = LAST_SENT.get(player.getUUID());
			if (previous != null && previous.x() == c.getX() && previous.z() == c.getZ()) {
				LAST_SENT.remove(player.getUUID());
				ServerPlayNetworking.send(player, RaidSkyPayload.CLEAR);
			}
		}
	}

	/** One player, still connected (e.g. just respawned): drop any tint we were showing them. */
	public static void clearFor(ServerPlayer player) {
		LAST_SENT.remove(player.getUUID());
		if (player.connection != null) {
			ServerPlayNetworking.send(player, RaidSkyPayload.CLEAR);
		}
	}

	/** One player leaving the server: just forget them, no packet. */
	public static void forget(UUID playerId) {
		LAST_SENT.remove(playerId);
	}

	/** Drop the de-duplication cache. Called from {@code ServerStateReset} when a server stops. */
	public static void clearSessionState() {
		LAST_SENT.clear();
	}
}
