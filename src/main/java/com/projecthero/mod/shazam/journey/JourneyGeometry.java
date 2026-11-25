package com.projecthero.mod.shazam.journey;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: the shared layout and timing of the journey (server, client and tests all read these). Every
 * position is an offset from a realm slot's origin {@code O} ({@link EternityRealm#origin}): the track runs along +X down the
 * middle of block row {@code z = O.z} (its centre line at {@code O.z + 0.5}), the rails sit in block layer {@code y = O.y}, and
 * the station platform, the carriage floor and the cave floor are all at {@code O.y + 1}.
 *
 * <pre>
 *  x: -105 ........ -24 ====== station ====== 24 ...... 125 ~~ glyphs ~~ 175 .. raw rock .. 256 (( the cave: 256..336, throne 327 ))
 *     west tunnel end      the train stops at 0       subway tunnel        the lightning flash     rock tunnel
 * </pre>
 */
public final class JourneyGeometry {
	private JourneyGeometry() {
	}

	// ---------------------------------------------------------------- the layout (x offsets from O)
	public static final int STATION_HALF = 24;
	public static final int WEST_END = -105;
	/** Where the tiled subway tunnel gives way to the glyph-carved stretch, and that to raw rock. */
	public static final int MAGIC_X = 125;
	public static final int ROCK_X = 175;
	/** The cave: an ellipse round (CAVE_X, 0), radii CAVE_RX by CAVE_RZ, its dome CAVE_H high. */
	public static final int CAVE_WEST = 256;
	public static final int CAVE_X = 296;
	public static final int CAVE_RX = 40;
	public static final int CAVE_RZ = 22;
	public static final int CAVE_H = 28;
	/** The buffer stop at the end of the line. */
	public static final int TRACK_END = 300;
	/** The throne's seat block (x), on the third step of the dais (the dais front step starts at {@link #DAIS_X}). */
	public static final int THRONE_X = 327;
	public static final int DAIS_X = 319;
	/** Where the player stands to meet him: up on the top step of the dais, a few blocks before the throne, facing it. */
	public static final double MEET_X = 322.5;
	/** How close (blocks, flat) to the Wizard starts the meeting. */
	public static final double MEET_RANGE = 7.0;

	// ---------------------------------------------------------------- the train
	/** Half the length of one car; two cars, their centres {@link #CAR_OFFSET} either side of the train's centre. */
	public static final double CAR_HALF = 8.0;
	public static final double CAR_OFFSET = 8.5;
	public static final double TRAIN_HALF = CAR_OFFSET + CAR_HALF;
	/** Half the car's width, its floor and roof heights (above the rail layer). */
	public static final double CAR_HALF_W = 1.45;
	public static final double CAR_FLOOR = 1.0;
	public static final double CAR_ROOF = 3.95;
	/** Door centres along each car (from the car's centre); each doorway is 2 wide. */
	public static final double[] DOORS = { -5.0, 0.0, 5.0 };
	public static final double DOOR_HALF = 1.0;
	public static final double DOOR_TOP = 3.35;
	/** Where the rider sits: in the front car, on the north bench, facing the far windows. */
	public static final double SEAT_X = CAR_OFFSET - 2.5;
	public static final double SEAT_Z = -0.95;
	public static final double SEAT_Y = 0.95;

	/** The train starts this far down the west tunnel and brakes to a stop at the platform over {@link #ARRIVE_TICKS}. */
	public static final double ARRIVE_FROM = -100.0;
	public static final int ARRIVE_TICKS = 120;
	public static final int DOOR_TICKS = 12;

	/** The ride: doors close (0-15), away at 20, full speed by 60, braking from 200, stopped at 250, doors open by 260. */
	public static final int RIDE_DEPART = 20;
	public static final int RIDE_CRUISE = 60;
	public static final int RIDE_BRAKE = 200;
	public static final int RIDE_STOP = 250;
	public static final int RIDE_TICKS = 262;
	public static final double RIDE_SPEED = 1.5;
	/** Ride ticks of the glyphs and the lightning flash (the walls turn to rock behind it). */
	public static final int RIDE_GLYPHS = 112;
	public static final int RIDE_FLASH = 150;

	/** The train's centre offset (x) after {@code t} ticks of the arrival. */
	public static double arriveOffset(double t) {
		double u = Mth.clamp(t / ARRIVE_TICKS, 0.0, 1.0);
		double k = 1.0 - u;
		return ARRIVE_FROM * k * k;
	}

	/** How open the doors are (0..1) after {@code t} ticks of the arrival. */
	public static double arriveDoors(double t) {
		return Mth.clamp((t - ARRIVE_TICKS - 4) / DOOR_TICKS, 0.0, 1.0);
	}

	/** The train's centre offset (x, from the station stop) after {@code t} ticks of the ride. */
	public static double rideOffset(double t) {
		double a = RIDE_SPEED / (RIDE_CRUISE - RIDE_DEPART);
		double accelDist = 0.5 * a * sq(RIDE_CRUISE - RIDE_DEPART);
		if (t <= RIDE_DEPART) {
			return 0.0;
		}
		if (t <= RIDE_CRUISE) {
			return 0.5 * a * sq(t - RIDE_DEPART);
		}
		double cruiseEnd = accelDist + RIDE_SPEED * (RIDE_BRAKE - RIDE_CRUISE);
		if (t <= RIDE_BRAKE) {
			return accelDist + RIDE_SPEED * (t - RIDE_CRUISE);
		}
		double d = RIDE_SPEED / (RIDE_STOP - RIDE_BRAKE);
		double tt = Math.min(t, RIDE_STOP) - RIDE_BRAKE;
		return cruiseEnd + RIDE_SPEED * tt - 0.5 * d * tt * tt;
	}

	/** The ride's final stop (centre offset): in the cave, short of the buffer stop. */
	public static double rideEnd() {
		return rideOffset(RIDE_STOP);
	}

	/** Speed (blocks / tick) during the ride, for sounds and streaks. */
	public static double rideSpeed(double t) {
		return Math.abs(rideOffset(t + 0.5) - rideOffset(t - 0.5));
	}

	/** How open the doors are during the ride: closing at the start, opening once stopped in the cave. */
	public static double rideDoors(double t) {
		if (t < 15) {
			return 1.0 - Mth.clamp(t / 15.0, 0.0, 1.0);
		}
		return Mth.clamp((t - RIDE_STOP - 2) / (double) DOOR_TICKS, 0.0, 1.0);
	}

	private static double sq(double v) {
		return v * v;
	}

	// ---------------------------------------------------------------- world positions

	/** The track's centre line at x offset {@code dx}, on the rail layer. */
	public static Vec3 track(BlockPos o, double dx) {
		return new Vec3(o.getX() + 0.5 + dx, o.getY(), o.getZ() + 0.5);
	}

	/** Where the player lands on the platform: the foot of the stairs they came down, facing the track. */
	public static Vec3 platformArrival(BlockPos o) {
		return new Vec3(o.getX() + 15.5, o.getY() + 1, o.getZ() + 5.5);
	}

	/** Where the player steps off in the cave (beside the front car's middle door), facing the throne. */
	public static Vec3 caveStepOff(BlockPos o) {
		return new Vec3(o.getX() + 0.5 + rideEnd() + CAR_OFFSET, o.getY() + 1, o.getZ() + 3.2);
	}

	public static Vec3 meetSpot(BlockPos o) {
		return new Vec3(o.getX() + MEET_X, o.getY() + 4, o.getZ() + 0.5);
	}

	/** The Wizard on his throne (seated) and where he stands once risen. */
	public static Vec3 throneSeat(BlockPos o) {
		return new Vec3(o.getX() + THRONE_X + 0.45, o.getY() + 4, o.getZ() + 0.5);
	}

	public static Vec3 throneStand(BlockPos o) {
		return new Vec3(o.getX() + THRONE_X - 0.9, o.getY() + 4, o.getZ() + 0.5);
	}
}
