package com.projecthero.mod.client.galactus;

import com.projecthero.mod.galactus.GalactusSkyPayload;

import net.minecraft.client.Minecraft;

/**
 * v0.16.1: Galactus' sky on the client, from the server's {@link GalactusSkyPayload}: the stars come out in daylight inside
 * the arena (and fade over 24 blocks past its edge) -- read by {@code GalactusStarsMixin} on the star brightness. The violet
 * tint itself is the shared raid sky ({@code RaidSkyTint}).
 */
public final class GalactusSkyClient {
	private static final double FADE = 24.0;
	private static GalactusSkyPayload state;

	private GalactusSkyClient() {
	}

	public static void accept(GalactusSkyPayload p) {
		state = p.active() ? p : null;
	}

	public static void reset() {
		state = null;
	}

	/** 0..1: how strongly the local player is inside Galactus' sky. */
	public static float strength() {
		GalactusSkyPayload s = state;
		Minecraft mc = Minecraft.getInstance();
		if (s == null || mc.player == null) {
			return 0f;
		}
		double dx = mc.player.getX() - (s.x() + 0.5);
		double dz = mc.player.getZ() - (s.z() + 0.5);
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d <= s.radius()) {
			return 1f;
		}
		return d >= s.radius() + FADE ? 0f : (float) (1.0 - (d - s.radius()) / FADE);
	}

	/** Star brightness to show at least (daytime stars), 0..1. */
	public static float stars() {
		GalactusSkyPayload s = state;
		if (s == null) {
			return 0f;
		}
		float base = s.stage() == 3 ? 0.35f : s.stage() == 2 ? 1.0f : 0.85f;
		return base * strength();
	}

	public static float consumption() {
		return state == null ? 0f : state.consumption();
	}
}
