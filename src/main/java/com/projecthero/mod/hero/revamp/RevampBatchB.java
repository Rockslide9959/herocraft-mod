package com.projecthero.mod.hero.revamp;

/**
 * v0.13.22 mutation revamp, batch B -- common-side registration for:
 * <ul>
 * <li>05 Geokinesis</li>
 * <li>06 Crystalkinesis</li>
 * <li>08 Pyrokinesis</li>
 * <li>09 Cryokinesis</li>
 * <li>25 Water Manipulation</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file.
 */
public final class RevampBatchB {
	private RevampBatchB() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
	}
}
