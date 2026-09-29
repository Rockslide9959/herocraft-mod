package com.projecthero.mod.client.mutation;

/**
 * v0.13.22 mutation revamp, batch B -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>05 Geokinesis</li>
 * <li>06 Crystalkinesis</li>
 * <li>08 Pyrokinesis</li>
 * <li>09 Cryokinesis</li>
 * <li>25 Water Manipulation</li>
 * </ul>
 */
public final class RevampClientB {
	private RevampClientB() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
	}
}
