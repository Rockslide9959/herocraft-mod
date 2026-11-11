package com.projecthero.mod.saiyan.kit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** v0.17.3 Saiyan revamp: Shift+Z, the Spirit Bomb. Stub contract -- filled in by its own build. */
public final class SaiyanSpiritBomb {
	private SaiyanSpiritBomb() {
	}

	public static void press(ServerPlayer p, boolean down) {
	}

	public static void tick(ServerPlayer p) {
	}

	public static int cooldownRemaining(Player p) {
		return 0;
	}

	public static int maxCooldown() {
		return 1;
	}

	public static boolean busy(Player p) {
		return false;
	}

	public static void clearSessionState() {
	}
}
