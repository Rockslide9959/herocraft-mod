package com.projecthero.mod.hero.power.p02;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import com.projecthero.mod.network.LaserBeamPayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Laser beams for the viewers the synced-animation path cannot reach (bug fix: "when lasers shoot at a player
 * sometimes they can't see the lasers being shot at them").
 *
 * <p>A player's Laser Vision beam is drawn on each client from the shooter's synced {@code p02.*} animation -- which
 * only reaches clients that <b>track</b> the shooter. A player's tracking range is capped by the viewer's own render
 * distance (vanilla: {@code min(512, clamp(renderDistance, 2, serverViewDistance) * 16)} blocks), so a viewer on 6
 * chunks tracks only 96 blocks while the beams reach {@link LaserVisionHandlers#RANGE 100}: shot from further out
 * they saw nothing. Those viewers now get an explicit {@link LaserBeamPayload} with the server's own beam segment.
 * Viewers who track the shooter are skipped -- they already draw the beam, glued to the shooter's aim.
 *
 * <p>A Laser Vision <em>boss</em> is not a player and has no synced beam at all: every player near its beam gets
 * the payload.
 */
public final class LaserBeams {
	/** Anyone whose eyes are within this of the beam segment sees it (well past the beams' 100-block reach). */
	public static final double VIEW_RADIUS = 128.0;

	/** Test seam: how a payload reaches a player. */
	static BiConsumer<ServerPlayer, LaserBeamPayload> sender = (player, payload) -> {
		if (ServerPlayNetworking.canSend(player, LaserBeamPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	};

	private LaserBeams() {
	}

	/** Replaces the sender (gametests capture recipients with it); returns the previous one to restore. */
	public static BiConsumer<ServerPlayer, LaserBeamPayload> swapSender(BiConsumer<ServerPlayer, LaserBeamPayload> next) {
		BiConsumer<ServerPlayer, LaserBeamPayload> prev = sender;
		sender = next;
		return prev;
	}

	/**
	 * Who must be sent a beam from {@code a} to {@code b}: every player in the level whose eyes are within
	 * {@link #VIEW_RADIUS} of the segment, except the shooter and the players tracking the shooter (they draw it
	 * themselves). With {@code shooter == null} (or a non-player shooter, e.g. a boss) nobody is skipped.
	 */
	public static List<ServerPlayer> recipients(ServerLevel level, Entity shooter, Vec3 a, Vec3 b) {
		return recipients(level, shooter, a, b, shooter instanceof ServerPlayer ? trackers(shooter) : Set.of());
	}

	/** {@link #recipients(ServerLevel, Entity, Vec3, Vec3)} with an explicit set of the shooter's trackers (tests). */
	public static List<ServerPlayer> recipients(ServerLevel level, Entity shooter, Vec3 a, Vec3 b, Set<ServerPlayer> trackers) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer viewer : level.players()) {
			if (viewer == shooter || trackers.contains(viewer)) {
				continue;
			}
			if (distanceToSegment(viewer.getEyePosition(), a, b) <= VIEW_RADIUS) {
				out.add(viewer);
			}
		}
		return out;
	}

	private static Set<ServerPlayer> trackers(Entity shooter) {
		Collection<ServerPlayer> tracking = PlayerLookup.tracking(shooter);
		return tracking.isEmpty() ? Set.of() : new HashSet<>(tracking);
	}

	/** Sends one beam to its {@link #recipients}. */
	public static void send(ServerLevel level, Entity shooter, Vec3 a, Vec3 b, int kind, int ticks) {
		List<ServerPlayer> to = recipients(level, shooter, a, b);
		if (to.isEmpty()) {
			return;
		}
		LaserBeamPayload payload = new LaserBeamPayload(a, b, kind, ticks);
		for (ServerPlayer viewer : to) {
			sender.accept(viewer, payload);
		}
	}

	/**
	 * Whether anyone at all needs a payload for a beam that starts at {@code a} and runs at most {@code reach} along
	 * {@code dir} -- lets a held beam skip its per-tick end-point ray-cast when every viewer already draws it.
	 */
	public static boolean anyRecipient(ServerLevel level, Entity shooter, Vec3 a, Vec3 dir, double reach) {
		return !recipients(level, shooter, a, a.add(dir.scale(reach))).isEmpty();
	}

	static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lengthSq = ab.lengthSqr();
		if (lengthSq < 1.0e-9) {
			return point.distanceTo(a);
		}
		double t = Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / lengthSq));
		return point.distanceTo(a.add(ab.scale(t)));
	}
}
