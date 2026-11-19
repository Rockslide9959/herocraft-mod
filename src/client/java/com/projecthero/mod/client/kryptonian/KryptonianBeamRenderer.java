package com.projecthero.mod.client.kryptonian;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.eyebeam.EyeBeamFx;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.8 Heat Vision, drawn since v0.17.5 by the shared eye-beam look ({@link EyeBeamFx#HEAT}: red-orange heat, white-hot
 * centre, glowing eyes, flare + sparks + scorch at the hit, first-person beams in from the lower screen edge). Driven only by the
 * synced {@code heatVision} flag, so every player sees everyone's beams; each client ray-casts the far end itself along the
 * shooter's interpolated look (the same ray the server burns along). Hidden while locked in a clash ({@code BeamClashClient}).
 */
public final class KryptonianBeamRenderer {
	/**
	 * Beams the server sent explicitly ({@code LaserBeamPayload.KIND_HEAT_VISION}, received by {@code LaserBeamRenderer}):
	 * Kryptonians this client does not track -- their heatVision flag never arrives here.
	 */
	private record SentBeam(Vec3 start, Vec3 end, long startTick, int ticks) {
	}

	private static final List<SentBeam> SENT = new ArrayList<>();
	private static final int MAX_SENT = 64;

	private KryptonianBeamRenderer() {
	}

	/** A held beam's per-tick refresh: replaces that beam's previous refresh (same eyes) instead of stacking. */
	public static void addSent(Vec3 start, Vec3 end, long now, int ticks) {
		SENT.removeIf(b -> b.start().distanceToSqr(start) < 2.25);
		if (SENT.size() >= MAX_SENT) {
			SENT.remove(0);
		}
		SENT.add(new SentBeam(start, end, now, ticks));
	}

	public static void clearSent() {
		SENT.clear();
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(KryptonianBeamRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		for (Player player : level.players()) {
			if (Kryptonian.heatVisionActive(player) && !com.projecthero.mod.client.beamclash.BeamClashClient.hides(player)) {
				Vec3 eye = player.getEyePosition(partial);
				Vec3 dir = player.getViewVector(partial);
				EyeBeamFx.twin(player, 0, EyeBeamFx.HEAT, dir, EyeBeamFx.ray(level, player, eye, dir, KryptonianConfig.HEAT_RANGE, true), 1f, 1f);
			}
		}
		long gameTime = level.getGameTime();
		SENT.removeIf(b -> gameTime - b.startTick() >= b.ticks() || gameTime < b.startTick() - 40);
		for (SentBeam b : SENT) {
			if (!com.projecthero.mod.client.beamclash.BeamClashClient.hidesFrom(b.start())) {
				EyeBeamFx.twinAt(b.start(), b.end(), EyeBeamFx.HEAT, 1f, 1f);
			}
		}
	}
}
