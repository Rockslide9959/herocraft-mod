package com.projecthero.mod.symbiote;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The Symbiote's voice. v0.11.15: every Symbiote effect used to be built from slime / swim / fishing-line
 * sounds, which all read as "water". This swaps them for a layered, organic sculk-and-warden sound --
 * a wet, fibrous crawl with a dry clicking of tendrils over it -- so the suit sounds like a living
 * organism rather than a splash. One helper so every ability, the bonding phase and the suit itself stay
 * in the same sonic family.
 */
public final class SymbioteSounds {
	private SymbioteSounds() {
	}

	/** The general "living tissue moving" sound: suit flowing on/off, blades, tendrils, shield. */
	public static void organic(ServerLevel level, double x, double y, double z, float volume, float pitch) {
		level.playSound(null, x, y, z, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS,
				volume, Math.max(0.5f, pitch * 0.8f));
		level.playSound(null, x, y, z, SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS,
				volume * 0.45f, 0.55f + pitch * 0.35f);
	}

	public static void organic(ServerPlayer player, float volume, float pitch) {
		organic(player.serverLevel(), player.getX(), player.getY(), player.getZ(), volume, pitch);
	}

	/** A heavier lash / slam: a sculk charge with a low warden impact under it. */
	public static void lash(ServerPlayer player, float volume, float pitch) {
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_BLOCK_CHARGE,
				SoundSource.PLAYERS, volume, Math.max(0.5f, pitch));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT,
				SoundSource.PLAYERS, volume * 0.5f, 0.7f + pitch * 0.3f);
	}
}
