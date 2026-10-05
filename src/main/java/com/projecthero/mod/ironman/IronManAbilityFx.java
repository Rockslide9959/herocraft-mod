package com.projecthero.mod.ironman;

import com.projecthero.mod.network.IronManPosePayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.14.26: the server side of the Iron Man attack animations -- each ability tells every client watching which pose to
 * play and for how long ({@link IronManPosePayload}). One-shot attacks call {@link #play}; held ones (charging a
 * repulsor, the shield, the Unibeam, the flamethrower, the wrist laser) call {@link #hold} every tick and it re-sends a
 * short window every few ticks, so the pose drops the moment the ability stops.
 */
public final class IronManAbilityFx {
	public static final int REPULSOR = 1;
	public static final int CHARGED = 2;
	public static final int CHARGING = 3;
	public static final int BARRIER = 4;
	public static final int UNIBEAM = 5;
	public static final int FLAME = 6;
	public static final int LASER = 7;
	public static final int PUNCH = 8;
	public static final int FLARE = 9;
	public static final int MISSILES = 10;
	public static final int ROCKET = 11;
	/** v0.14.27 (agent C): the Shift+R repulsor dash ({@code IronManDash}). */
	public static final int DASH = 20;
	/** v0.14.27 (agent C): the sonic clap ({@code IronManSonicClap}). */
	public static final int SONIC_CLAP = 21;

	private IronManAbilityFx() {
	}

	public static void play(ServerPlayer player, int anim, int ticks) {
		IronManPosePayload p = new IronManPosePayload(player.getId(), anim, ticks);
		ServerPlayNetworking.send(player, p);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, p);
			}
		}
	}

	/** For a held ability: call every tick it is active. */
	public static void hold(ServerPlayer player, int anim) {
		if (player.tickCount % 3 == 0) {
			play(player, anim, 6);
		}
	}
}
