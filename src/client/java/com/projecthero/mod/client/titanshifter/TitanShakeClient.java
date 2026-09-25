package com.projecthero.mod.client.titanshifter;

import net.minecraft.client.Minecraft;

/**
 * Client-side camera tremor for Titan footfalls and impacts, fed by {@code TitanShakePayload}. Purely visual:
 * {@link #offsetYaw()} / {@link #offsetPitch()} are read by the camera mixin, and the strength decays
 * linearly over the requested ticks.
 */
public final class TitanShakeClient {
	private static float intensity;
	private static int ticksLeft;
	private static int totalTicks = 1;

	private TitanShakeClient() {
	}

	public static void accept(float strength, int ticks) {
		if (strength <= 0.01f || ticks <= 0) {
			return;
		}
		// a stronger tremor replaces a weaker one; otherwise the current one just keeps running
		if (strength >= current() || ticksLeft <= 0) {
			intensity = Math.min(strength, 1.5f);
			ticksLeft = ticks;
			totalTicks = ticks;
		}
	}

	private static float current() {
		return ticksLeft <= 0 ? 0f : intensity * ticksLeft / (float) totalTicks;
	}

	public static void tick() {
		if (ticksLeft > 0) {
			ticksLeft--;
		}
	}

	private static float noise(long salt) {
		long t = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
		double phase = (t + salt * 7) * 1.9 + salt;
		return (float) (Math.sin(phase) * 0.6 + Math.sin(phase * 2.3 + 1.1) * 0.4);
	}

	public static float offsetYaw() {
		float c = current();
		return c <= 0f ? 0f : noise(1) * c * 1.2f;
	}

	public static float offsetPitch() {
		float c = current();
		return c <= 0f ? 0f : noise(2) * c * 1.6f;
	}
}
