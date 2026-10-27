package com.projecthero.mod.quirk.erasure;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * v0.16.7: the Erasure Quirk's server init -- the kit ({@link ErasureKit}) and the END_SERVER_TICK that holds its binds.
 * What Erasure does to mobs is {@link ErasureMobs} (with the {@code Erasure*Mixin}s).
 */
public final class ErasureMod {
	private ErasureMod() {
	}

	public static void initialize() {
		QuirkKits.register(Quirk.ERASURE, new ErasureKit());
		ErasureMeter.register(); // v0.17.1 framework: Dry Eyes bands + pacing
		ErasureItems.initialize(); // v0.16.7 revamp: the Eye Drops
		ServerTickEvents.END_SERVER_TICK.register(ErasureMoves::serverTick);
		PayloadTypeRegistry.playS2C().register(ErasureGogglesPayload.TYPE, ErasureGogglesPayload.CODEC);
	}
}
