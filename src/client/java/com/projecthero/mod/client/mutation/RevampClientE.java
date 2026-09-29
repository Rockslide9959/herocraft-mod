package com.projecthero.mod.client.mutation;

/**
 * v0.13.22 mutation revamp, batch E -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>16 Spider Climbing / Adhesion</li>
 * <li>17 Elasticity</li>
 * <li>18 Density Manipulation</li>
 * <li>22 Plant Manipulation</li>
 * <li>27 Size Manipulation</li>
 * </ul>
 */
public final class RevampClientE {
	private RevampClientE() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
	}
}
