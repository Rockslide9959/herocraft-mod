package com.projecthero.mod.client.mutation;

/**
 * v0.13.22 mutation revamp, batch D -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>10 Telekinesis</li>
 * <li>11 Teleportation</li>
 * <li>15 Invisibility / Light</li>
 * <li>19 Shadow Manipulation</li>
 * <li>23 Gravity Manipulation</li>
 * <li>26 Magnetic Manipulation</li>
 * </ul>
 */
public final class RevampClientD {
	private RevampClientD() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
	}
}
