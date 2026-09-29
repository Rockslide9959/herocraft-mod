package com.projecthero.mod.moonknight.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * R -- Crescent Darts (tap: one dart, hold: a fan, sneak: Moon Mark) -- built in Moon Knight Phase 3. Until then every use just says the move isn't ready.
 */
public final class MoonKnightDarts implements MoonKnightMove {
	public static final MoonKnightDarts INSTANCE = new MoonKnightDarts();

	private MoonKnightDarts() {
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

	/** Outgoing-damage multiplier from this key (Moon Mark: +30% against a marked target). */
	public static float outgoingFactor(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
		return 1.0f;
	}
}
