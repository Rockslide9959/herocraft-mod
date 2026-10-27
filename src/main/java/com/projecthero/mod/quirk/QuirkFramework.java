package com.projecthero.mod.quirk;

import java.util.UUID;

import com.projecthero.mod.quirk.fx.QuirkCameraPayload;
import com.projecthero.mod.quirk.fx.QuirkCuePayload;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.fx.QuirkSounds;
import com.projecthero.mod.quirk.meter.QuirkMeters;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.1 Quirk revamp, wave 0: the shared framework every kit builds on ({@code docs/QUIRK_FRAMEWORK_0171.md}):
 * <ul>
 * <li>{@code quirk.fx} -- the effect entity ({@link QuirkFx}), the shared particles, the camera juice ({@code QuirkCamera})
 * and the shared {@code quirk_*} sounds;</li>
 * <li>{@code quirk.meter} -- the three-band meters with the push past 100, and the pacing the user asked for;</li>
 * <li>{@link PlusUltra} -- the shared ultimate gauge and the hold-C ultimate;</li>
 * <li>{@link QuirkCombos} -- combo routes and the popup;</li>
 * <li>{@link QuirkPerks} -- the full-suit perk hook.</li>
 * </ul>
 * Wired from {@link QuirkMod} (init, server-state reset) and {@link Quirks} (the tick, logout / revoke cleanup).
 */
public final class QuirkFramework {
	private static boolean initialised;

	private QuirkFramework() {
	}

	/** From {@code QuirkMod.initialize}, before the kits (they register meter specs and routes against it). */
	static void initialize() {
		if (initialised) {
			return;
		}
		initialised = true;
		QuirkFx.initialize();
		QuirkParticles.register();
		QuirkSounds.initialize();
		PayloadTypeRegistry.playS2C().register(QuirkCameraPayload.TYPE, QuirkCameraPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(QuirkCuePayload.TYPE, QuirkCuePayload.CODEC);
		PlusUltra.initialize();
	}

	/** Every server tick for a kit-Quirk holder, after the kit's own tick (skipped while Erased). */
	static void tick(ServerPlayer p, Quirk q) {
		QuirkMeters.tick(p, q);
		PlusUltra.tick(p);
		QuirkPerks.tick(p, q);
	}

	static void clearSessionState() {
		QuirkMeters.clearSessionState();
		PlusUltra.clearSessionState();
		QuirkCombos.clearSessionState();
		QuirkPerks.clearSessionState();
	}

	static void forget(UUID id) {
		QuirkMeters.forget(id);
		PlusUltra.forget(id);
		QuirkCombos.forget(id);
		QuirkPerks.forget(id);
	}
}
