package com.projecthero.mod.hero.revamp;

/**
 * v0.13.22 mutation revamp, batch E -- common-side registration for:
 * <ul>
 * <li>16 Spider Climbing / Adhesion</li>
 * <li>17 Elasticity</li>
 * <li>18 Density Manipulation</li>
 * <li>22 Plant Manipulation</li>
 * <li>27 Size Manipulation</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file.
 */
public final class RevampBatchE {
	private RevampBatchE() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
	}
}
