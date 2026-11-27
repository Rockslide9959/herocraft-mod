package com.projecthero.mod.blackpanther.plane;

/**
 * v0.17.7 Black Panther Ancestral Plane: <b>the Trial of the Shades</b>, the first half of Bast's test -- the rules alone
 * (no Minecraft types), so the server and the tests share them.
 *
 * <p>Ghostly panther shades come for the player on the shrine floor, three rounds: 4 shades one at a time, then 6 a little
 * quicker, then 8 two at a time. Each shade appears on the rim (a growl), stalks in to about 4 blocks, <b>crouches with its
 * eyes flaring</b> (the tell, about a second), then pounces at where the player stood. One hit from anything kills a shade --
 * strike it as it stalks in, as it crouches, or in mid-leap. A pounce that lands within {@link #POUNCE_REACH} wounds the
 * player (no damage, just a mark: the shade springs back to the rim and stalks again); sidestepping it is a miss and it stalks
 * again. Kill every shade of the round before the third wound to win it; a third wound loses the round, which starts over
 * (wounds reset each round, won rounds stay won). Beat round 3 and the trial is passed.
 */
public final class ShadeTrial {
	public static final int ROUNDS = 3;
	public static final int MAX_WOUNDS = 3;
	/** A pounce that lands this close (flat blocks) wounds. */
	public static final double POUNCE_REACH = 1.6;
	/** Shades stop stalking at this distance before they crouch. */
	public static final double STALK_STOP = 4.0;
	public static final int APPEAR_TICKS = 14;
	public static final int POUNCE_TICKS = 10;
	public static final int RETREAT_TICKS = 12;
	/** Pause between rounds (and after a lost round) before the next shade comes. */
	public static final int BREATHER_TICKS = 60;

	private static final int[] SHADES = { 4, 6, 8 };
	private static final int[] CONCURRENT = { 1, 1, 2 };
	private static final int[] WINDUP = { 24, 20, 18 };
	private static final int[] STALK_MAX = { 70, 60, 54 };
	private static final double[] STALK_SPEED = { 0.13, 0.15, 0.16 };
	private static final int[] SPAWN_GAP = { 20, 16, 14 };

	public enum Outcome {
		NONE, ROUND_WON, TRIAL_WON, ROUND_LOST
	}

	private int round;
	private int killed;
	private int spawned;
	private int wounds;

	public int round() {
		return round;
	}

	public int killed() {
		return killed;
	}

	public int spawned() {
		return spawned;
	}

	public int wounds() {
		return wounds;
	}

	public boolean won() {
		return round >= ROUNDS;
	}

	public static int shadesIn(int round) {
		return SHADES[clamp(round)];
	}

	public static int concurrent(int round) {
		return CONCURRENT[clamp(round)];
	}

	/** Ticks a shade crouches, eyes flaring, before it pounces. */
	public static int windup(int round) {
		return WINDUP[clamp(round)];
	}

	/** The longest a shade stalks before it crouches wherever it is. */
	public static int stalkMax(int round) {
		return STALK_MAX[clamp(round)];
	}

	public static double stalkSpeed(int round) {
		return STALK_SPEED[clamp(round)];
	}

	public static int spawnGap(int round) {
		return SPAWN_GAP[clamp(round)];
	}

	private static int clamp(int round) {
		return Math.max(0, Math.min(ROUNDS - 1, round));
	}

	public int total() {
		return shadesIn(round);
	}

	/** May another shade appear now, with {@code alive} on the floor? */
	public boolean canSpawn(int alive) {
		return !won() && spawned < total() && alive < concurrent(round);
	}

	public void noteSpawned() {
		spawned++;
	}

	/** A shade was struck down. */
	public Outcome kill() {
		if (won()) {
			return Outcome.TRIAL_WON;
		}
		killed++;
		if (killed >= total()) {
			round++;
			killed = 0;
			spawned = 0;
			wounds = 0;
			return won() ? Outcome.TRIAL_WON : Outcome.ROUND_WON;
		}
		return Outcome.NONE;
	}

	/** A pounce landed on the player. */
	public Outcome wound() {
		if (won()) {
			return Outcome.TRIAL_WON;
		}
		wounds++;
		if (wounds >= MAX_WOUNDS) {
			restartRound();
			return Outcome.ROUND_LOST;
		}
		return Outcome.NONE;
	}

	/** The round starts over (its shades are cleared by the caller). */
	public void restartRound() {
		killed = 0;
		spawned = 0;
		wounds = 0;
	}

	/** Did a pounce that landed {@code distance} (flat blocks) from the player hit him? */
	public static boolean pounceHits(double distance) {
		return distance <= POUNCE_REACH;
	}

	/** Test / command hook: jump straight to round {@code r}. */
	public void setRound(int r) {
		round = Math.max(0, Math.min(ROUNDS, r));
		restartRound();
	}
}
