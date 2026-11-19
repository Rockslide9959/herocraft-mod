package com.projecthero.mod.client.mutation.v0145;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.eyebeam.EyeBeamFx;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.network.LaserBeamPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.5 Laser Vision: the eye beams as geometry, drawn since v0.17.5 by the shared eye-beam look ({@link EyeBeamFx#LASER}: a
 * red laser with a white-hot centre from each eye, glowing eyes, flare + sparks + scorch at the hit, first-person beams in from the
 * lower screen edge to the crosshair, your own beams over your head in the third-person back view and stopped short of the front
 * camera). Maximum Output adds the wide white-hot halo down the middle.
 *
 * <p>Driven only by the synced {@link MutationVisualState} ({@code p02.*} animation + the {@code p02.max} flag), so every player
 * sees everyone's beams; each client ray-casts the far end itself along the shooter's interpolated look. Shooters this client does
 * not track and the Laser Vision boss arrive instead as {@link LaserBeamPayload}s with the server's beam segment ({@code SENT}).
 */
public final class LaserBeamRenderer {
	private LaserBeamRenderer() {
	}

	/** A beam the server sent explicitly ({@link LaserBeamPayload}): from a shooter this client does not track, or a boss. */
	private record SentBeam(Vec3 start, Vec3 end, Kind kind, long startTick, int ticks) {
	}

	private static final List<SentBeam> SENT = new ArrayList<>();
	private static final int MAX_SENT = 256;

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(LaserBeamRenderer::render);
		ClientPlayNetworking.registerGlobalReceiver(LaserBeamPayload.TYPE, (payload, context) -> {
			ClientLevel level = context.client().level;
			if (level == null || payload.ticks() <= 0) {
				return;
			}
			if (payload.kind() == LaserBeamPayload.KIND_HEAT_VISION) {
				com.projecthero.mod.client.kryptonian.KryptonianBeamRenderer.addSent(payload.start(), payload.end(), level.getGameTime(),
						Math.min(payload.ticks(), 200));
				return;
			}
			Kind[] kinds = Kind.values();
			Kind kind = payload.kind() >= 0 && payload.kind() < kinds.length ? kinds[payload.kind()] : Kind.BEAM;
			if (payload.ticks() <= LaserBeamPayload.REFRESH_TICKS) {
				// a held beam's per-tick refresh replaces that beam's previous refresh (same eyes, same kind) instead of
				// stacking up to three translucent copies
				SENT.removeIf(b -> b.ticks() <= LaserBeamPayload.REFRESH_TICKS && b.kind() == kind
						&& b.start().distanceToSqr(payload.start()) < 2.25);
			}
			if (SENT.size() >= MAX_SENT) {
				SENT.remove(0);
			}
			SENT.add(new SentBeam(payload.start(), payload.end(), kind, level.getGameTime(), Math.min(payload.ticks(), 200)));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			SENT.clear();
			com.projecthero.mod.client.kryptonian.KryptonianBeamRenderer.clearSent();
		}));
	}

	/** Kind order matches the {@code LaserBeamPayload.KIND_*} constants; {@code width} scales the shared beam. */
	private enum Kind {
		BEAM(1f), PIERCE(1.45f), SWEEP(1.1f), RECOIL(1.8f), IGNITE(0.6f), MAX(1.7f);

		final float width;

		Kind(float width) {
			this.width = width;
		}
	}

	private static void render(WorldRenderContext context) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long gameTime = level.getGameTime();
		for (Player player : level.players()) {
			MutationVisualState s = MutationVisuals.state(player);
			String anim = s.anim();
			if ((anim.isEmpty() && !s.has("p02.max")) || com.projecthero.mod.client.beamclash.BeamClashClient.hides(player)) {
				continue;
			}
			float age = (gameTime - s.animStart()) + partial;
			int shot = LaserVisionHandlers.oneShotTicks(anim);
			boolean shotLive = shot > 0 && age >= 0 && age < shot;
			// the p02.max flag keeps Maximum Output's beam up through a Laser one-shot, and through any other power's
			// animation that briefly took the shared slot (it used to blink out until the beam loop was re-asserted)
			boolean foreignAnim = !anim.isEmpty() && !anim.startsWith("p02.");
			if (anim.equals(LaserVisionHandlers.ANIM_MAX) || (s.has("p02.max") && (shotLive || foreignAnim))) {
				beam(level, player, partial, Kind.MAX, player.getViewVector(partial), 1f);
			} else if (anim.equals(LaserVisionHandlers.ANIM_BEAM)) {
				beam(level, player, partial, Kind.BEAM, player.getViewVector(partial), 1f);
			}
			if (!shotLive) {
				continue;
			}
			float fade = 1f - age / shot;
			switch (anim) {
				case LaserVisionHandlers.ANIM_PIERCE -> beam(level, player, partial, Kind.PIERCE, player.getViewVector(partial), fade);
				case LaserVisionHandlers.ANIM_RECOIL -> beam(level, player, partial, Kind.RECOIL, player.getViewVector(partial), fade);
				case LaserVisionHandlers.ANIM_IGNITE -> beam(level, player, partial, Kind.IGNITE, player.getViewVector(partial), fade);
				case LaserVisionHandlers.ANIM_SWEEP -> {
					if (age <= LaserVisionHandlers.SWEEP_TICKS) {
						float progress = age / (LaserVisionHandlers.SWEEP_TICKS - 1);
						Vec3 dir = LaserVisionHandlers.sweepDirection(player.getViewYRot(partial), player.getViewXRot(partial), progress);
						beam(level, player, partial, Kind.SWEEP, dir, 1f);
					}
				}
				default -> {
				}
			}
		}
		// beams the server sent explicitly: shooters this client does not track, and Laser Vision bosses
		SENT.removeIf(b -> gameTime - b.startTick() >= b.ticks() || gameTime < b.startTick() - 40);
		for (SentBeam b : SENT) {
			float bAge = (gameTime - b.startTick()) + partial;
			if (bAge < 0 || bAge >= b.ticks() || com.projecthero.mod.client.beamclash.BeamClashClient.hidesFrom(b.start())) {
				continue;
			}
			float fade = b.ticks() > LaserBeamPayload.REFRESH_TICKS ? 1f - bAge / b.ticks() : 1f;
			if (fade > 0.01f) {
				EyeBeamFx.twinAt(b.start(), b.end(), EyeBeamFx.LASER, b.kind().width, fade);
			}
		}
	}

	/** One twin beam fired by {@code player} along {@code dir} (Piercing Blast goes through creatures). */
	private static void beam(ClientLevel level, Player player, float partial, Kind kind, Vec3 dir, float fade) {
		if (fade <= 0.01f) {
			return;
		}
		Vec3 eye = player.getEyePosition(partial);
		EyeBeamFx.Hit hit = EyeBeamFx.ray(level, player, eye, dir, LaserVisionHandlers.RANGE, kind != Kind.PIERCE);
		EyeBeamFx.twin(player, kind.ordinal(), EyeBeamFx.LASER, dir, hit, kind.width, fade, kind == Kind.MAX);
	}
}
