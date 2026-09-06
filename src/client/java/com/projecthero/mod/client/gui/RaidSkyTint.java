package com.projecthero.mod.client.gui;

import com.projecthero.mod.network.RaidSkyPayload;

import net.minecraft.client.Minecraft;

/**
 * Client-side state for the dark-purple raid sky. The server sends a {@link RaidSkyPayload} when the
 * local player crosses into or out of a Zombie Raid area; the fog and sky-colour mixins
 * ({@code FogRendererMixin}, {@code ClientLevelMixin}) ask {@link #strength()} each frame and blend
 * the sky toward violet by that amount, so the transition at the edge of the raid is a smooth fade
 * rather than a hard pop.
 */
public final class RaidSkyTint {
	/** Blocks past the full-strength radius over which the tint fades to nothing. */
	private static final double FADE = 24.0;

	private static RaidSkyPayload state = RaidSkyPayload.CLEAR;

	private RaidSkyTint() {
	}

	public static void accept(RaidSkyPayload payload) {
		state = payload;
	}

	public static void reset() {
		state = RaidSkyPayload.CLEAR;
	}

	/** 0 = untinted, 1 = full dark purple, by the local player's horizontal distance to the raid. */
	public static float strength() {
		RaidSkyPayload s = state;
		if (!s.active()) {
			return 0.0f;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return 0.0f;
		}
		double dx = mc.player.getX() - (s.x() + 0.5);
		double dz = mc.player.getZ() - (s.z() + 0.5);
		double d = Math.sqrt(dx * dx + dz * dz);
		double r = s.radius();
		if (d <= r) {
			return 1.0f;
		}
		if (d >= r + FADE) {
			return 0.0f;
		}
		return (float) (1.0 - (d - r) / FADE);
	}
}
