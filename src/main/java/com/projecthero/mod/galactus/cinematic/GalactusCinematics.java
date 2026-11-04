package com.projecthero.mod.galactus.cinematic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2: the server half of the cutscenes. {@link #play} sends the scene to each fighter and
 * <b>holds</b> them for its length: kept where they stand (sideways; up and down stay free, so the ascension's lift is
 * real), and unhurtable. The client flies the camera ({@code GalactusCinematicClient}); what happens in the world during a
 * scene (the Herald's dive, the head rising, the islands moving) is the raid's own script. Static scratch state: cleared by
 * {@code ServerStateReset}.
 */
public final class GalactusCinematics {
	private static final Map<UUID, Hold> HELD = new HashMap<>();

	private record Hold(ResourceKey<Level> level, Vec3 at, long until) {
	}

	private GalactusCinematics() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(CinematicPayload.TYPE, CinematicPayload.CODEC);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && held(p)));
	}

	/** Plays {@code scene} for {@code players}, holding them for its length (minus {@code elapsed}). */
	public static void play(Collection<ServerPlayer> players, GalactusScenes.Scene scene, Vec3 anchor, float yaw, int elapsed, int bossId, float extra) {
		CinematicPayload p = new CinematicPayload(scene.ordinal(), anchor.x, anchor.y, anchor.z, yaw, elapsed, bossId, extra);
		for (ServerPlayer sp : players) {
			if (ServerPlayNetworking.canSend(sp, CinematicPayload.TYPE)) {
				ServerPlayNetworking.send(sp, p);
			}
			hold(sp, scene.length - elapsed);
		}
	}

	public static void hold(ServerPlayer p, int ticks) {
		HELD.put(p.getUUID(), new Hold(p.level().dimension(), p.position(), p.serverLevel().getGameTime() + Math.max(0, ticks)));
	}

	public static void release(ServerPlayer p) {
		HELD.remove(p.getUUID());
	}

	public static void stop(Collection<ServerPlayer> players) {
		for (ServerPlayer sp : players) {
			release(sp);
			if (ServerPlayNetworking.canSend(sp, CinematicPayload.TYPE)) {
				ServerPlayNetworking.send(sp, CinematicPayload.stop());
			}
		}
	}

	public static boolean held(ServerPlayer p) {
		Hold h = HELD.get(p.getUUID());
		return h != null && p.serverLevel().getGameTime() < h.until;
	}

	/** Every tick: anyone being held who has drifted sideways is put back (and stays out of harm). */
	public static void tick(MinecraftServer server) {
		if (HELD.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Hold>> it = HELD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Hold> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Hold h = e.getValue();
			if (p == null || p.level().dimension() != h.level || p.serverLevel().getGameTime() >= h.until) {
				it.remove();
				continue;
			}
			double dx = p.getX() - h.at.x, dz = p.getZ() - h.at.z;
			if (dx * dx + dz * dz > 0.09) {
				p.connection.teleport(h.at.x, p.getY(), h.at.z, p.getYRot(), p.getXRot());
			}
			p.fallDistance = 0;
			p.setRemainingFireTicks(0);
		}
	}

	/** A player moved by the raid during a scene (the trip to orbit): the hold follows them. */
	public static void moved(ServerPlayer p) {
		Hold h = HELD.get(p.getUUID());
		if (h != null) {
			HELD.put(p.getUUID(), new Hold(p.level().dimension(), p.position(), h.until));
		}
	}

	public static void clearStatics() {
		HELD.clear();
	}
}
