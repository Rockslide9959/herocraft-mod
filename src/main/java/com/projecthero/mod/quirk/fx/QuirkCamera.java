package com.projecthero.mod.quirk.fx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirk framework: camera "juice" triggered from the server with a tiny payload ({@link QuirkCameraPayload}),
 * played by {@code client.quirk.QuirkCameraFx}:
 * <ul>
 * <li><b>shake</b> -- a noise tremor that decays over its ticks, scaled by distance from the source;</li>
 * <li><b>hit-stop</b> -- the world's motion freezes for 2-4 ticks on a heavy hit, then lets go with the shake;</li>
 * <li><b>FOV punch</b> -- the view widens (positive) or tightens (negative) and springs back (dashes, launches);</li>
 * <li><b>vignette</b> -- a coloured edge glow that fades (heat orange, erased red, nausea green...).</li>
 * </ul>
 * The client scales every one of them by the player's vanilla "Distortion Effects" / "FOV Effects" accessibility sliders,
 * so a player who turned those down gets a gentler (or no) shake and FOV punch. Presets keep the moves consistent:
 * <pre>
 *   QuirkCamera.preset(attacker, QuirkCamera.Preset.HEAVY_HIT);           // the attacker's own camera
 *   QuirkCamera.shakeAround(level, blastPos, 16, 0.8f, 12);               // everyone near a blast
 * </pre>
 */
public final class QuirkCamera {
	public static final int SHAKE = 1;
	public static final int HIT_STOP = 2;
	public static final int FOV_PUNCH = 3;
	public static final int VIGNETTE = 4;

	/** Hit-stop is never longer than this (ticks): a freeze longer than a blink reads as lag. */
	public static final int MAX_HIT_STOP = 4;

	/** The shared presets every kit should use (strength, ticks). */
	public enum Preset {
		/** A landed light hit: a tiny shake. */
		LIGHT_HIT(0.12f, 4, 0, 0f),
		/** A heavy hit: a 2-tick hit-stop and a firm shake. */
		HEAVY_HIT(0.35f, 7, 2, 0f),
		/** A finisher / big blast: a 3-tick hit-stop, a strong shake and a small FOV kick. */
		FINISHER(0.6f, 10, 3, 0.06f),
		/** A dash or launch: an FOV punch outward, no shake. */
		DASH(0f, 0, 0, 0.12f),
		/** An ultimate going off: 4-tick hit-stop, a long strong shake and a big FOV punch. */
		ULTIMATE(0.9f, 18, 4, 0.18f);

		public final float shake;
		public final int shakeTicks;
		public final int hitStop;
		public final float fov;

		Preset(float shake, int shakeTicks, int hitStop, float fov) {
			this.shake = shake;
			this.shakeTicks = shakeTicks;
			this.hitStop = hitStop;
			this.fov = fov;
		}
	}

	private QuirkCamera() {
	}

	private static void send(ServerPlayer p, int kind, float strength, int ticks, int colour) {
		if (ServerPlayNetworking.canSend(p, QuirkCameraPayload.TYPE)) {
			ServerPlayNetworking.send(p, new QuirkCameraPayload(kind, strength, ticks, colour));
		}
	}

	/** A shake of {@code strength} (about 0.1 = a bump, 1.0 = an earthquake) for {@code ticks}. */
	public static void shake(ServerPlayer p, float strength, int ticks) {
		if (strength > 0.01f && ticks > 0) {
			send(p, SHAKE, Math.min(strength, 1.5f), ticks, 0);
		}
	}

	/** Shakes everyone within {@code radius} of {@code at}, falling off linearly with distance. */
	public static void shakeAround(ServerLevel level, Vec3 at, double radius, float strength, int ticks) {
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceTo(at);
			if (d <= radius) {
				shake(p, strength * (float) (1.0 - d / radius * 0.8), ticks);
			}
		}
	}

	/** Freezes {@code p}'s view of the world for {@code ticks} (capped at {@link #MAX_HIT_STOP}). */
	public static void hitStop(ServerPlayer p, int ticks) {
		if (ticks > 0) {
			send(p, HIT_STOP, 1f, Math.min(ticks, MAX_HIT_STOP), 0);
		}
	}

	/** Widens (positive, e.g. 0.12 = +12%) or tightens (negative) the FOV for {@code ticks}, springing back. */
	public static void fovPunch(ServerPlayer p, float amount, int ticks) {
		if (Math.abs(amount) > 0.001f && ticks > 0) {
			send(p, FOV_PUNCH, amount, ticks, 0);
		}
	}

	/** A coloured screen-edge glow ({@code rgb}) of {@code strength} 0..1, fading over {@code ticks}. */
	public static void vignette(ServerPlayer p, int rgb, float strength, int ticks) {
		if (strength > 0.01f && ticks > 0) {
			send(p, VIGNETTE, Math.min(1f, strength), ticks, rgb);
		}
	}

	/** Plays a preset on {@code p}'s camera. */
	public static void preset(ServerPlayer p, Preset preset) {
		if (preset.hitStop > 0) {
			hitStop(p, preset.hitStop);
		}
		if (preset.shake > 0f) {
			shake(p, preset.shake, preset.shakeTicks);
		}
		if (preset.fov != 0f) {
			fovPunch(p, preset.fov, 8);
		}
	}
}
