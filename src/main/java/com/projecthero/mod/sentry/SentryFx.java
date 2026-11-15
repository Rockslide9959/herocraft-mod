package com.projecthero.mod.sentry;

import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** v0.17.5 Sentry: sends {@link SentryFxPayload}s to every player within 96 blocks of the effect. */
public final class SentryFx {
	private static final double RANGE = 96.0;

	private SentryFx() {
	}

	public static void send(ServerLevel level, int kind, Vec3 at, float size, int ticks, Vec3 to, int owner) {
		Vec3 t = to == null ? at : to;
		SentryFxPayload p = new SentryFxPayload(kind, at.x, at.y, at.z, size, ticks, t.x, t.y, t.z, owner);
		for (ServerPlayer sp : level.players()) {
			if (sp.distanceToSqr(at) <= RANGE * RANGE) {
				ServerPlayNetworking.send(sp, p);
			}
		}
	}

	public static void send(ServerLevel level, int kind, Vec3 at, float size, int ticks) {
		send(level, kind, at, size, ticks, null, -1);
	}
}
