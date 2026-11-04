package com.projecthero.mod.galactus.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2: the five cutscenes' shared timing contract (server scripts and client cameras both read it),
 * plus the scripted paths both sides need to agree on (the Herald's dive from orbit). Each scene is anchored on a point
 * and a facing: {@code f} = forward (where the action is), {@code r} = right, up = +Y.
 */
public final class GalactusScenes {
	private GalactusScenes() {
	}

	public enum Scene {
		/** C1: the Herald streaks down from orbit to the beacon. */
		HERALD(160),
		/** C2: the sky tears, the Devourer's head rises, roars, and the party is lifted into space. */
		DEVOURER(280),
		/** C2 (orbit side): the arrival sweep over the meteor field. */
		ARRIVAL(70),
		/** C3: the Worldship pulls the islands into its hull; Galactus kneels over the Earth; the Surfer turns. */
		WORLDSHIP(200),
		/** C4: he lets go of the Earth and rises to his full height. */
		RISE(180),
		/** C5: the Nullifier fires; he comes apart into stars; the fall home. */
		ENDING(240),
		/** The loss: the Earth is drained grey. */
		CONSUMED(140),
		/** The fall home ends: a white fade back in at the beacon. */
		HOME(40);

		public final int length;

		Scene(int length) {
			this.length = length;
		}

		public static Scene byId(int id) {
			return id >= 0 && id < values().length ? values()[id] : null;
		}
	}

	// ---------------------------------------------------------------- C1 timings
	public static final int HERALD_BOOM = 70;
	public static final int HERALD_LAND = 112;
	public static final int HERALD_TITLE = 118;

	// ---------------------------------------------------------------- C2 timings
	public static final int DEVOURER_RISE_START = 40;
	public static final int DEVOURER_RISE_END = 140;
	public static final int DEVOURER_EYE_R = 148;
	public static final int DEVOURER_EYE_L = 160;
	public static final int DEVOURER_ROAR = 172;
	public static final int DEVOURER_LIFT = 205;
	public static final int DEVOURER_WARP = 232;
	public static final int DEVOURER_WHITE = 258;
	/** How far in front of the anchor the head rises, and his height. */
	public static final double DEVOURER_DISTANCE = 105.0;
	public static final float DEVOURER_HEIGHT = 230f;

	// ---------------------------------------------------------------- C3 / C4 / C5 timings
	public static final int WORLDSHIP_PULL = 50;
	public static final int WORLDSHIP_LOCK = 130;
	public static final int WORLDSHIP_SURFER = 150;
	public static final int RISE_STAND = 30;
	public static final int RISE_PLANT = 132;
	public static final int ENDING_CRACK = 60;
	public static final int ENDING_BURST = 120;
	public static final int ENDING_LEAVE = 150;
	public static final int ENDING_WHITE = 205;
	public static final int CONSUMED_WHITE = 110;

	public static Vec3 forward(float yawDeg) {
		float y = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(y), 0, Mth.cos(y));
	}

	public static Vec3 right(float yawDeg) {
		float y = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(y), 0, -Mth.sin(y));
	}

	public static double ease(double t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/**
	 * The Herald's dive (C1), {@code t} in scene ticks: a star high over the far horizon that grows and falls in a long
	 * curve, breaks the sound barrier at {@link #HERALD_BOOM}, pulls up hard and comes to a hover over the beacon at
	 * {@link #HERALD_LAND}.
	 */
	public static Vec3 heraldPos(Vec3 anchor, float yawDeg, double t) {
		Vec3 f = forward(yawDeg), r = right(yawDeg);
		Vec3 start = anchor.add(f.scale(150)).add(r.scale(-40)).add(0, 190, 0);
		Vec3 boom = anchor.add(f.scale(46)).add(r.scale(-12)).add(0, 46, 0);
		Vec3 end = anchor.add(f.scale(6)).add(0, 6.5, 0);
		if (t <= 20) {
			return start;
		}
		if (t <= HERALD_BOOM) {
			double k = (t - 20) / (HERALD_BOOM - 20.0);
			k = k * k; // accelerating
			return start.lerp(boom, k);
		}
		if (t <= HERALD_LAND) {
			double k = (t - HERALD_BOOM) / (double) (HERALD_LAND - HERALD_BOOM);
			double e = 1 - (1 - k) * (1 - k) * (1 - k); // braking
			Vec3 mid = boom.lerp(end, e);
			// the pull-up: a dip below the straight line, then up into the hover
			return mid.add(0, -Math.sin(k * Math.PI) * 8, 0);
		}
		double bob = Math.sin((t - HERALD_LAND) * 0.12) * 0.25;
		return end.add(0, bob, 0);
	}

	/** The head's rise (C2): the colossus' feet, scene tick {@code t}. */
	public static Vec3 devourerFeet(Vec3 anchor, float yawDeg, double t) {
		Vec3 f = forward(yawDeg);
		double k = ease((t - DEVOURER_RISE_START) / (double) (DEVOURER_RISE_END - DEVOURER_RISE_START));
		double y = Mth.lerp(k, -DEVOURER_HEIGHT - 20, -DEVOURER_HEIGHT * 0.52);
		return anchor.add(f.scale(DEVOURER_DISTANCE)).add(0, y, 0);
	}
}
