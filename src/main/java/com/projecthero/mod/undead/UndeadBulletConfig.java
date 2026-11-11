package com.projecthero.mod.undead;

/**
 * v0.17.3 (user's rework): the numbers of Undead's R bullets, the Head Throw (hold X) and the Division Bullet (Shift+C). Ticks
 * unless said otherwise; speeds in blocks a tick.
 */
public final class UndeadBulletConfig {
	private UndeadBulletConfig() {
	}

	// ---------------------------------------------------------------- R: Parts Bullet (Finger / Hand / Arm)
	/** "Parts Bullet: Finger ... 7 damage, no cooldown, 60 block range" (tap R). */
	public static final float FINGER_DAMAGE = 7f;
	/** "Parts Bullet: Hand ... 12 damage, no cooldown, 60 block range" (Shift+R): the whole hand flies and regrows. */
	public static final float HAND_DAMAGE = 12f;
	/** "Parts Bullet: Arm ... 20 damage, no cooldown, 60 block range" (hold R for a second). */
	public static final float ARM_DAMAGE = 20f;
	/** Every R bullet flies dead straight (no gravity, so it lands on the crosshair) and is gone after this many blocks. */
	public static final double RANGE = 60.0;
	public static final double FINGER_SPEED = 3.2;
	public static final double HAND_SPEED = 2.8;
	public static final double ARM_SPEED = 2.6;
	/** Hold R this long and the arm flies (on the spot, still held); let go sooner and it is a finger. */
	public static final int ARM_HOLD = 20;
	/** From this far into the hold the arm visibly draws back (so a quick tap never flashes the wind-up). */
	public static final int ARM_WINDUP_SHOW = 6;

	// ---------------------------------------------------------------- hold X: Head Throw
	/**
	 * The thrown head flies a ballistic arc: this speed along the look at release, minus {@link #HEAD_GRAVITY} a tick, no drag.
	 * v0.17.3 (user: "Make the player able to throw their head up to 30 blocks please"): at 45 degrees over flat ground, let go
	 * from behind the shoulder (1.9 up), the tick-by-tick arc lands about 30 blocks out (about 29 from the same height; the old
	 * 1.118 gave about 25). {@link UndeadHeadThrow#predict} -- the trajectory line -- runs the same steps.
	 */
	public static final double HEAD_SPEED = 1.18;
	public static final double HEAD_GRAVITY = 0.05;
	/** The scripted arc lets go after this long at most (it normally ends on touching a block first). */
	public static final int HEAD_MAX_FLIGHT = 120;
	/** The trajectory line is predicted this far ahead at most. */
	public static final int HEAD_PREDICT_TICKS = 120;
	/** Free (the body has to grow back before he can throw again; the 2 s rip is the cost). */
	public static final int HEAD_THROW_COOLDOWN = 0;
	/** The hold X rip-off (wired by the X routing): the struggle shows from this far in. */
	public static final int HEAD_RIP_SHOW = 4;
	public static final int HEAD_RIP_HOLD = 40;

	// ---------------------------------------------------------------- Shift+C: Division Bullet
	/** "the clone lives for 20 seconds", "clones deal 8 damage per hit", "10 second cooldown", "has 30hp". */
	public static final int CLONE_LIFE = 400;
	public static final float CLONE_DAMAGE = 8f;
	public static final int DIVISION_COOLDOWN = 200;
	public static final float CLONE_HEALTH = 30f;
	/** The finger itself does a scratch (it is a seed, not a bullet). */
	public static final float DIVISION_FINGER_DAMAGE = 2f;
	public static final double DIVISION_SPEED = 2.4;
	/** Like a tamed wolf: walks back to the owner past FOLLOW_START, stops at FOLLOW_STOP, blinks to him past TELEPORT. */
	public static final double CLONE_FOLLOW_START = 6.0;
	public static final double CLONE_FOLLOW_STOP = 2.5;
	public static final double CLONE_TELEPORT = 24.0;
	/** Mobs hunting the owner within this far are the clone's business too. */
	public static final double CLONE_GUARD_RANGE = 16.0;
}
