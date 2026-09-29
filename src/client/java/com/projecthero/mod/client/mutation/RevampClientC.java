package com.projecthero.mod.client.mutation;

/**
 * v0.13.22 mutation revamp, batch C -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>07 Electrokinesis</li>
 * <li>14 Sonic Scream</li>
 * <li>20 Energy Absorption</li>
 * <li>21 Shockwave Manipulation</li>
 * <li>24 Wind Manipulation</li>
 * </ul>
 */
public final class RevampClientC {
	private RevampClientC() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
	}
}
