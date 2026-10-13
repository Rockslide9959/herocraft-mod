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
	/** v0.14.27 (agent D): Mark III shoulder miniguns -- numbered high to stay clear of other new poses. */
	public static final int MINIGUN = 22;
	/** v0.15.11: the Mark 1 Shift+R ground pound ({@code IronManGroundPound}) -- fists overhead, then slammed down. */
	public static final int GROUND_POUND = 60;
	// v0.16.1 comic kit poses (IronManAbilityPose): numbered 80+ to stay clear of every other pose id
	/** Both palms thrown forward and wide (magnets, pressor, tractor, gravity tether). */
	public static final int COMIC_MAGNET = 80;
	/** A crouch then both arms flung down and back (rocket hop, booster launch, rocket skates). */
	public static final int COMIC_LAUNCH = 81;
	/** Arms spread wide, body level (glide). */
	public static final int COMIC_GLIDE = 82;
	/** A full-turn spin with one leg out (skate spin-kick). */
	public static final int COMIC_SPIN = 83;
	/** Both forearms crossed in front of the face (Hydraulic Brace, cloak). */
	public static final int COMIC_GUARD = 84;
	/** Both hands out in front at chest height, holding something (grab, tractor hold). */
	public static final int COMIC_GRAB = 85;
	/** A big overhead throw with the right arm (grab and throw, depth charge, oil slick). */
	public static final int COMIC_THROW = 86;
	/** The right arm raised straight up at the sky (orbital strike, replacement pod). */
	public static final int COMIC_SKY = 87;
	/** Head and arms tucked down, diving (meteor dive). */
	public static final int COMIC_DIVE = 88;
	/** Knee lifted high, then a stomp (earthquake stomp, pressure crush). */
	public static final int COMIC_STOMP = 89;
	/** Alternating hammer fists (grab and pound, punch combo). */
	public static final int COMIC_POUND = 90;
	/** Arms out to the sides, chest thrust out (lamp flash, floodlight, solar flare, overload burst, sonar pulse). */
	public static final int COMIC_FLARE = 91;

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
