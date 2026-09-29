package com.projecthero.mod.moonknight.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * X -- the Cape (tap: Cape Glide, hold: Cape Shroud, sneak: Shadow Step) -- built in Moon Knight Phase 3. Until then every use just says the move isn't ready.
 */
public final class MoonKnightCape implements MoonKnightMove {
	public static final MoonKnightCape INSTANCE = new MoonKnightCape();

	private MoonKnightCape() {
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

	/** Incoming-damage multiplier from this key (Cape Shroud). */
	public static float incomingFactor(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
		return 1.0f;
	}

	/** True if a fall should do no damage right now (gliding). */
	public static boolean negatesFall(ServerPlayer player) {
		return false;
	}
}
