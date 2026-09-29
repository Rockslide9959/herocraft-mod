package com.projecthero.mod.client.mutation;

/**
 * v0.13.22 mutation revamp, batch A -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>01 Super Strength</li>
 * <li>02 Laser Vision</li>
 * <li>03 Flight</li>
 * <li>04 Super Speed</li>
 * <li>12 Super Regeneration</li>
 * <li>13 Super Durability</li>
 * </ul>
 */
public final class RevampClientA {
	private RevampClientA() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
	}
}
