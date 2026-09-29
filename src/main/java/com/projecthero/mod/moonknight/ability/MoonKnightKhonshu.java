package com.projecthero.mod.moonknight.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * V -- Khonshu (tap: Moonbeam, hold 2 s: Eye of Khonshu, sneak: Khonshu's Judgement) + Khonshu's Resurrection -- built in Moon Knight Phase 6. Until then every use just says the move isn't ready.
 */
public final class MoonKnightKhonshu implements MoonKnightMove {
	public static final MoonKnightKhonshu INSTANCE = new MoonKnightKhonshu();

	private MoonKnightKhonshu() {
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

	/** Khonshu's Resurrection: cancel a fatal hit once per moon cycle. Returns true if the death was averted. */
	public static boolean tryResurrect(ServerPlayer player) {
		return false;
	}

	/** A mob died (Khonshu's Judgement refunds). */
	public static void onEntityKilled(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
	}
}
