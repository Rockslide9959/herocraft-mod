package com.projecthero.mod.andy;

/** v0.17.3: Andy's numbers in one place (ticks unless said otherwise). */
public final class AndyConfig {
	private AndyConfig() {
	}

	// ---------------------------------------------------------------- the body
	/** Part HP: head, body, right arm, left arm, right leg, left leg. */
	public static final float[] PART_HP = { 8f, 14f, 8f, 8f, 8f, 8f };
	/** A part that has not been hit for this long knits back at {@link #PART_KNIT} HP a second. */
	public static final int KNIT_DELAY = 100;
	public static final float PART_KNIT = 1.0f;
	/** An explosion lands this share of its damage on every part. */
	public static final float BLAST_SHARE = 0.55f;

	// ---------------------------------------------------------------- regeneration (Slow, Normal, Burst)
	public static final int SPEED_SLOW = 0;
	public static final int SPEED_NORMAL = 1;
	public static final int SPEED_BURST = 2;
	/** How long a broken part stays gone before it starts to regrow. */
	public static final int[] REGROW_DELAY = { 100, 50, 14 };
	/** How long the regrowth itself takes (the pixels streaming in). */
	public static final int[] REGROW_TICKS = { 56, 40, 24 };
	/** Hunger (food points) a regrown part costs; the body costs one more. */
	public static final int[] REGROW_FOOD = { 1, 2, 3 };
	/** Crimson Bullet: the regeneration delay is multiplied by this for {@link #CRIMSON_SLOW_TICKS}. */
	public static final float CRIMSON_SLOW_FACTOR = 2.5f;
	public static final int CRIMSON_SLOW_TICKS = 300;

	// ---------------------------------------------------------------- Undead
	/** The whole body re-forms from the head over this long (invulnerable, can't act). */
	public static final int REFORM_TICKS = 64;
	/** Within the reform: when each part starts and how long it takes (body first, then arms, then legs). */
	public static final int[] REFORM_START = { 0, 0, 14, 14, 28, 28 };
	public static final int[] REFORM_LEN = { 0, 24, 26, 26, 34, 34 };
	/** A launched limb or head lasts at most this long. */
	public static final int SEVERED_LIFE = 600;

	// ---------------------------------------------------------------- passives
	/** Bloody Arts: + melee (Victor: {@link #VICTOR_MELEE}). */
	public static final double BLOODY_ARTS_MELEE = 3.0;
	public static final double VICTOR_MELEE = 7.0;
	/** Victor hits harder with every move. */
	public static final float VICTOR_DAMAGE = 1.35f;
	/** Repair Headbutt: a head hit this hard (or the head breaking) snaps back at the nearest enemy within the reach. */
	public static final float HEADBUTT_TRIGGER = 4f;
	public static final double HEADBUTT_REACH = 4.0;
	public static final float HEADBUTT_DAMAGE = 8f;
	public static final int HEADBUTT_COOLDOWN = 40;
	/** One leg gone: Slowness at this amplifier. */
	public static final int ONE_LEG_SLOW = 2;

	// ---------------------------------------------------------------- moves
	public static final float PARTS_BULLET_DAMAGE = 7f;
	public static final double FINGER_SPEED = 2.6;
	public static final int PARTS_BULLET_COOLDOWN = 12;
	public static final float CRIMSON_DAMAGE = 20f;
	public static final int CRIMSON_COOLDOWN = 160;

	public static final float CRESCENT_DAMAGE = 13f;
	public static final double CRESCENT_REACH = 4.5;
	public static final int CRESCENT_COOLDOWN = 50;
	public static final float GREAT_CRESCENT_DAMAGE = 18f;
	public static final double GREAT_CRESCENT_RANGE = 18.0;
	public static final int GREAT_CRESCENT_COOLDOWN = 400;

	public static final double PROPULSION_SPEED = 0.95;
	public static final int PROPULSION_MAX = 200;
	public static final int PROPULSION_COOLDOWN = 30;
	public static final int GLIDE_TICKS = 14;
	public static final double GLIDE_SPEED = 1.15;
	public static final float GLIDE_DAMAGE = 7f;
	public static final int GLIDE_COOLDOWN = 80;

	public static final float VORTEX_DAMAGE = 16f;
	public static final double VORTEX_SPEED = 1.7;
	public static final int VORTEX_PIERCE = 3;
	public static final int VORTEX_COOLDOWN = 120;
	public static final float HEAD_BULLET_DAMAGE = 12f;
	public static final double HEAD_SPEED = 1.5;
	public static final int HEAD_BULLET_COOLDOWN = 200;

	public static final int VICTOR_TICKS = 600;
	public static final int VICTOR_COOLDOWN = 1800;

	public static final double DEAD_LINE_RANGE = 24.0;
	public static final float DEAD_LINE_DAMAGE = 4f;
	public static final int DEAD_LINE_HIT_EVERY = 4;
	public static final int DEAD_LINE_MAX = 100;
	public static final int DEAD_LINE_COOLDOWN = 60;
	public static final int QUARDEAD_TICKS = 300;
	public static final float CLONE_DAMAGE = 5f;
	public static final int QUARDEAD_COOLDOWN = 800;

	// ---------------------------------------------------------------- Victor's kit
	public static final int VULCAN_SHOTS = 10;
	public static final float VULCAN_DAMAGE = 4f;
	public static final int VULCAN_COOLDOWN = 60;
	public static final float DEAD_BLADE_DAMAGE = 22f;
	public static final double DEAD_BLADE_REACH = 5.5;
	public static final int DEAD_BLADE_COOLDOWN = 80;
	public static final double DEAD_ROAD_RANGE = 32.0;
	public static final float DEAD_ROAD_DAMAGE = 7f;
	public static final int DEAD_ROAD_TICKS = 50;
	public static final int DEAD_ROAD_COOLDOWN = 240;
	public static final int DIVISION_CLONE_TICKS = 160;
	public static final int DIVISION_COOLDOWN = 400;

	/** A boss takes at most this share of its max health from one hit. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
}
