package com.projecthero.mod.moonknight.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * Z -- Truncheon / Staff (tap: summon or stow, hold: staff spin, sneak: slam) -- built in Moon Knight Phase 4. Until then every use just says the move isn't ready.
 */
public final class MoonKnightTruncheon implements MoonKnightMove {
	public static final MoonKnightTruncheon INSTANCE = new MoonKnightTruncheon();

	private MoonKnightTruncheon() {
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

	/** A melee hit landed by the Moon Knight (combo slams, night healing). */
	public static void onMeleeHit(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target, float amount) {
	}
}
