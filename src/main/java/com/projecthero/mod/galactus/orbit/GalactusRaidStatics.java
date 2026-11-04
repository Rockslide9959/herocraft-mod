package com.projecthero.mod.galactus.orbit;

/**
 * v0.17.2 Galactus raid v2: every piece of static server scratch state the raid keeps, dropped in one call from
 * {@code ServerStateReset.clearAll} when a server stops (see the static-state leak note there).
 */
public final class GalactusRaidStatics {
	private GalactusRaidStatics() {
	}

	public static void clear() {
		OrbitDebris.clearStatics();
		com.projecthero.mod.galactus.cinematic.GalactusCinematics.clearStatics();
		OrbitRules.clearStatics();
		StageFeast.clearStatics();
	}
}
