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
	// v0.16.7 (user) Era II C rework: the new attacks' poses
	/** Right arm locked out on the aim, the left hand clamped round the right wrist to steady it (Mk VI Wrist Laser). */
	public static final int COMIC_LASER = 92;
	/** A low lunge, then a full body spin with both arms flung out level, the blades leading (Mk VII Blade Whirl). */
	public static final int COMIC_BLADE_WHIRL = 93;
	/** The right hand cupped high over the shoulder, then an overhand pitch, the left arm pointing (Mk VIII Singularity). */
	public static final int COMIC_SINGULARITY = 94;
	/** Both forearms snapped up in front, fists clenched, then flicked out to the sides as the blades slide out (Mk VII). */
	public static final int COMIC_BLADE_DEPLOY = 95;
	// v0.16.7 (user) Era II stage 3: the Mk IX Inferno's own poses (client IronManAbilityPose / IronManAbilityVisuals)
	/** Right palm thrust out with a hard recoil, the left hand braced back (Fire Blast). */
	public static final int MK9_FIRE_BLAST = 120;
	/** A wind-up, then a rising straight with a body twist; a burst of heat at the fist (Magma Fist). */
	public static final int MK9_MAGMA_FIST = 121;
	/** Both fists raised overhead, then slammed into the ground, hunched (Eruption); an expanding ground ring. */
	public static final int MK9_ERUPTION = 122;
	/** An underarm lob (Magma Bomb). */
	public static final int MK9_LOB = 123;
	/**
	 * Crouch in, then the arms and chest flung open (Heat Dump); expanding nova rings. The nova radius rides in the pose
	 * length: {@code ticks = MK9_HEAT_DUMP_BASE_TICKS + round(radius)}.
	 */
	public static final int MK9_HEAT_DUMP = 124;
	public static final int MK9_HEAT_DUMP_BASE_TICKS = 14;
	/** Arms spread low, palms down, drawing the heat in (Thermal Siphon). */
	public static final int MK9_SIPHON = 125;
	/** v0.16.7 (user): the right arm cocked high behind, then whipped across the body (Molten Lash). */
	public static final int MK9_LASH = 126;
	// v0.16.1: the Hulkbuster body's clips (client IronManHulkbusterAnimatable plays them while the full Mark 8 is worn);
	// numbered 100+ (the body never uses the humanoid poses)
	public static final int HB_PUNCH = 100;
	public static final int HB_ROCKET_FIST = 101;
	public static final int HB_PILE_DRIVER = 102;
	public static final int HB_EARTHQUAKE = 103;
	public static final int HB_GRAB = 104;
	public static final int HB_GRAB_POUND = 105;
	public static final int HB_GRAB_THROW = 106;
	public static final int HB_CHEST_BEAM = 107;
	public static final int HB_MISSILE_SWARM = 108;
	public static final int HB_HYDRAULIC_BRACE = 109;
	public static final int HB_REPLACEMENT_POD = 110;
	/** v0.16.7 (user): both huge palms thrust out together, then the recoil (Palm Cannon). */
	public static final int HB_PALM_CANNON = 111;
	/** v0.16.7 (user): the heavy three-point superhero landing after a hard flight landing / big fall ({@code HulkbusterLanding}). */
	public static final int HB_LAND_HEAVY = 112;
	/** v0.16.7 (user): the light touchdown (a gentle hover landing) -- the knees soak it up. */
	public static final int HB_LAND_LIGHT = 113;

	/** v0.16.1: the Hulkbuster body clip name for a HB_* pose id, or null. */
	public static String hulkbusterClip(int anim) {
		return switch (anim) {
			case HB_PUNCH -> "punch_combo";
			case HB_ROCKET_FIST -> "rocket_fist";
			case HB_PILE_DRIVER -> "pile_driver";
			case HB_EARTHQUAKE -> "earthquake_stomp";
			case HB_GRAB -> "grab";
			case HB_GRAB_POUND -> "grab_pound";
			case HB_GRAB_THROW -> "grab_throw";
			case HB_CHEST_BEAM -> "chest_beam";
			case HB_MISSILE_SWARM -> "missile_swarm";
			case HB_HYDRAULIC_BRACE -> "hydraulic_brace";
			case HB_REPLACEMENT_POD -> "replacement_pod";
			case HB_PALM_CANNON -> "palm_cannon";
			case HB_LAND_HEAVY -> "superhero_land";
			case HB_LAND_LIGHT -> "touchdown_land";
			default -> null;
		};
	}

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
