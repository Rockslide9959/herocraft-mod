package com.projecthero.mod.hero.revamp;

/**
 * v0.13.22 mutation revamp, batch A -- common-side registration for:
 * <ul>
 * <li>01 Super Strength</li>
 * <li>02 Laser Vision</li>
 * <li>03 Flight</li>
 * <li>04 Super Speed</li>
 * <li>12 Super Regeneration</li>
 * <li>13 Super Durability</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file.
 */
public final class RevampBatchA {
	private RevampBatchA() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
	}
}
