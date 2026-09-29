package com.projecthero.mod.hero.revamp;

/**
 * v0.13.22 mutation revamp, batch D -- common-side registration for:
 * <ul>
 * <li>10 Telekinesis</li>
 * <li>11 Teleportation</li>
 * <li>15 Invisibility / Light</li>
 * <li>19 Shadow Manipulation</li>
 * <li>23 Gravity Manipulation</li>
 * <li>26 Magnetic Manipulation</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file.
 */
public final class RevampBatchD {
	private RevampBatchD() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
	}
}
