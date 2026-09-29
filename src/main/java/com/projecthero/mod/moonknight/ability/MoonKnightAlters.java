package com.projecthero.mod.moonknight.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * C -- the Alters (tap: cycle, hold: radial picker, sneak: the alter's special) + alter passives -- built in Moon Knight Phase 5. Until then every use just says the move isn't ready.
 */
public final class MoonKnightAlters implements MoonKnightMove {
	public static final MoonKnightAlters INSTANCE = new MoonKnightAlters();

	private MoonKnightAlters() {
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

	/** Incoming-damage multiplier from the current alter (Steven: -15% melee). */
	public static float incomingFactor(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
		return 1.0f;
	}

	/** Outgoing-damage multiplier from the current alter (Marc +20% melee, Jake +50% from behind). */
	public static float outgoingFactor(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target,
			net.minecraft.world.damagesource.DamageSource source) {
		return 1.0f;
	}

	/** Keep the alter's attribute passives in step (called once a second while transformed, and on un-transform). */
	public static void reconcile(ServerPlayer player) {
	}

	/** The radial picker chose an alter (client payload). */
	public static void select(ServerPlayer player, int alterOrdinal) {
	}
}
