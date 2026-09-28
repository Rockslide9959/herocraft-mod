package com.projecthero.mod.client.oathbreaker;

/**
 * Client-side camera FOV ease, fed by {@code WorldEventZoomPayload}. Purely visual: {@link #factor()}
 * is read by {@code WorldEventZoomMixin} and multiplied into the vanilla FOV, decaying linearly back to
 * 1.0 over the requested ticks -- the same shape {@code TitanShakeClient} uses for camera shake.
 */
public final class WorldEventZoomClient {
	private static float amount;
	private static int ticksLeft;
	private static int totalTicks = 1;

	private WorldEventZoomClient() {
	}

	public static void accept(float amount, int ticks) {
		if (amount <= 0.001f || ticks <= 0) {
			return;
		}
		if (amount >= current() || ticksLeft <= 0) {
			WorldEventZoomClient.amount = Math.min(amount, 0.6f);
			ticksLeft = ticks;
			totalTicks = ticks;
		}
	}

	private static float current() {
		return ticksLeft <= 0 ? 0f : amount * ticksLeft / (float) totalTicks;
	}

	public static void tick() {
		if (ticksLeft > 0) {
			ticksLeft--;
		}
	}

	/** Multiply straight into the vanilla FOV: 1.0 = no change, less than 1.0 = zoomed in. */
	public static float factor() {
		return 1.0f - current();
	}
}
