package com.projecthero.mod.moonknight.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * G -- Grappling Line (tap: grapple to a block, hold: grapple + dive kick, sneak: Yank) -- built in Moon Knight Phase 4. Until then every use just says the move isn't ready.
 */
public final class MoonKnightGrapple implements MoonKnightMove {
	public static final MoonKnightGrapple INSTANCE = new MoonKnightGrapple();

	private MoonKnightGrapple() {
	}

	@Override
	public void tap(ServerPlayer player) {
		notReady(player);
	}

	@Override
	public void holdStart(ServerPlayer player) {
		notReady(player);
	}

	@Override
	public void sneak(ServerPlayer player) {
		notReady(player);
	}

	private static void notReady(ServerPlayer player) {
		MoonKnightAbilities.say(player, "message.projecthero.moon_knight.not_ready");
	}
}
