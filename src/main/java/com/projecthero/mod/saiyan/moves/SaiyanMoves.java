package com.projecthero.mod.saiyan.moves;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Stand-in, replaced by moves. The v0.17.3 Saiyan core calls these with the move ids "combo" (left click on a creature),
 * "feint" (R), "ki_beam" (G), "spirit_orb" (Shift+G), "barrage" (Z), "sky_combo" (Shift+Z), "dragon_rush" (C) and
 * "instant_step" (V); {@code down} is the key's press (true) and release (false). This no-op only keeps the core compiling
 * until the moves branch is merged.
 */
public final class SaiyanMoves {
	private SaiyanMoves() {
	}

	/** A move key went down or came up. */
	public static void press(ServerPlayer player, String id, boolean down) {
	}

	/** Every tick, for every Saiyan (the running moves). */
	public static void tick(ServerPlayer player) {
	}

	/** Ticks left on the move's cooldown (for the HUD). */
	public static int cooldownRemaining(Player player, String id) {
		return 0;
	}

	/** The move's full cooldown in ticks (for the HUD's sweep). */
	public static int maxCooldown(String id) {
		return 0;
	}
}
