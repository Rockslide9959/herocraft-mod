package com.projecthero.mod.darkseid.entity;

/**
 * Darkseid's GeckoLib clip names and the tick timings the server's attack scripts are written against. Every
 * number here MUST match {@code animations/darkseid.animation.json}; both are produced from the same table in
 * {@code scratchpad/darkseid/gen_darkseid_assets.js}, which fails the build of the assets if a clip's length or a
 * keyed impact frame drifts from the value below. A clip that lands its blow at tick N is the reason the server
 * applies that blow at tick N -- change one, change both.
 */
public final class DarkseidAnims {
	public static final String IDLE = "idle";
	public static final String WALK = "walk";
	public static final String RUN = "run";

	public static final String ENTRANCE = "entrance";
	public static final int ENTRANCE_TICKS = 80;

	public static final String MELEE_1 = "melee_attack_1";
	public static final int MELEE_1_TICKS = 24;
	public static final int MELEE_1_IMPACT = 11;

	public static final String MELEE_2 = "melee_attack_2";
	public static final int MELEE_2_TICKS = 26;
	public static final int MELEE_2_IMPACT = 12;

	public static final String MELEE_COMBO = "melee_combo";
	public static final int MELEE_COMBO_TICKS = 44;
	public static final int[] MELEE_COMBO_IMPACTS = { 10, 20, 34 };

	public static final String GROUND_SLAM = "ground_slam";
	public static final int GROUND_SLAM_TICKS = 50;
	public static final int GROUND_SLAM_IMPACT = 26;

	public static final String BEAM_CHARGE = "omega_beam_charge";
	public static final String BEAM_FIRE = "omega_beam_fire";
	public static final int BEAM_FIRE_TICKS = 24;

	public static final String BARRAGE = "omega_barrage";
	public static final int BARRAGE_TICKS = 36;
	public static final int BARRAGE_RELEASE = 20;

	public static final String GRIP = "grip";
	public static final int GRIP_REACH_TICKS = 20;
	public static final String GRIP_THROW = "grip_throw";
	public static final int GRIP_THROW_TICKS = 16;
	public static final int GRIP_THROW_RELEASE = 8;

	public static final String CHARGE = "charge";
	public static final int CHARGE_WINDUP_TICKS = 24;
	public static final String CHARGE_RUSH = "charge_rush";

	public static final String TELEPORT = "teleport";
	public static final int TELEPORT_TICKS = 12;
	public static final String TELEPORT_ARRIVE = "teleport_arrive";
	public static final int TELEPORT_ARRIVE_TICKS = 12;

	public static final String RAGE = "rage_transition";
	public static final int RAGE_TICKS = 60;
	public static final int RAGE_ROAR = 30;

	public static final String ANNIHILATION_CHARGE = "omega_annihilation_charge";
	public static final String ANNIHILATION_RELEASE = "omega_annihilation_release";
	public static final int ANNIHILATION_RELEASE_TICKS = 24;
	public static final int ANNIHILATION_BLAST = 6;

	public static final String SWEEP = "omega_sweep";

	public static final String SUMMON = "summon";
	public static final int SUMMON_TICKS = 24;
	public static final int SUMMON_OPEN = 16;

	public static final String HURT = "hurt";
	public static final int HURT_TICKS = 10;

	public static final String STAGGER = "stagger";
	public static final String STAGGER_RECOVER = "stagger_recover";
	public static final int STAGGER_RECOVER_TICKS = 14;

	public static final String DEATH = "death";
	public static final int DEATH_TICKS = 110;
	/** The Omega energy bursts out of him. */
	public static final int DEATH_BURST = 72;
	/** The Boom Tube opens behind him. */
	public static final int DEATH_BOOM_TUBE = 78;
	/** He starts fading into the Boom Tube. */
	public static final int DEATH_FADE_START = 84;

	/** Every one-shot clip the server may trigger. */
	static final String[] ACTION_CLIPS = {
			ENTRANCE, MELEE_1, MELEE_2, MELEE_COMBO, GROUND_SLAM, BEAM_FIRE, BARRAGE, GRIP_THROW, TELEPORT,
			TELEPORT_ARRIVE, RAGE, ANNIHILATION_RELEASE, SUMMON, HURT, STAGGER_RECOVER, DEATH,
	};
	/** Clips that hold their last frame until the next trigger replaces them (their length is decided in Java). */
	static final String[] ACTION_HOLDS = { BEAM_CHARGE, GRIP, CHARGE, ANNIHILATION_CHARGE, STAGGER };
	/** Triggered clips that loop. */
	static final String[] ACTION_LOOPS = { CHARGE_RUSH, SWEEP };

	private DarkseidAnims() {
	}
}
