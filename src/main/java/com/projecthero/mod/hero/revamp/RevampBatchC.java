package com.projecthero.mod.hero.revamp;

/**
 * v0.13.22 mutation revamp, batch C -- common-side registration for:
 * <ul>
 * <li>07 Electrokinesis</li>
 * <li>14 Sonic Scream</li>
 * <li>20 Energy Absorption</li>
 * <li>21 Shockwave Manipulation</li>
 * <li>24 Wind Manipulation</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file.
 */
public final class RevampBatchC {
	private RevampBatchC() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
	}
}
