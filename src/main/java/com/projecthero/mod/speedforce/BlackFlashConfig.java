package com.projecthero.mod.speedforce;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.17.1: the Black Flash's tuning, at {@code config/projecthero_black_flash.json} (loaded through {@link VersionedConfig},
 * see {@code docs/CONFIGS.md}). Every section is balance.
 */
public final class BlackFlashConfig {
	public static final VersionedConfig<BlackFlashConfig> SPEC = VersionedConfig.builder(BlackFlashConfig.class, "projecthero_black_flash.json", BlackFlashConfig::new)
			.balance("boss", "moves")
			.introduce(1, "v0.17.1 the Black Flash")
			.reset(2, "v0.17.1 (user: \"make the black flash deal more damage\"): every move +50%", "moves")
			.build();

	private static BlackFlashConfig instance = new BlackFlashConfig();

	public Integer configVersion;
	public Boss boss = new Boss();
	public Moves moves = new Moves();

	public static final class Boss {
		public double health = 600.0;
		public double armor = 8.0;
		/** Base MOVEMENT_SPEED (a zombie is 0.23). */
		public double movementSpeed = 0.42;
		/** He regenerates once nothing has hit him for this long (ticks)... */
		public int regenDelayTicks = 100;
		/** ... this much health a second. */
		public double regenPerSecond = 2.0;
		/** No target this long (ticks): he goes back into the Speed Force. */
		public int noTargetDespawnTicks = 5 * 60 * 20;
		/** His hits on a player never take more than this fraction of their max health (before armour). */
		public double playerDamageCap = 0.8;
		/** XP when he falls. */
		public int xpReward = 500;
	}

	/** v0.17.1 (user): every hit 50% harder than first built (config v2). */
	public static final class Moves {
		public double punch = 9.0;
		public double blitz = 15.0;
		public double blitzShock = 7.5;
		public double rapidPerHit = 3.75;
		public double mach = 21.0;
		public double machShock = 7.5;
		public double lightning = 15.0;
		public double vortexTick = 3.0;
		public double vortexFinal = 9.0;
		public double armsTick = 1.5;
		public double sweepPerHit = 9.0;
		public double massPunch = 42.0;
		public double massShock = 12.0;
		/** The Time Remnant he splits off: its health and how long it lasts (ticks); it hits at this fraction of his damage. */
		public double remnantHealth = 60.0;
		public int remnantTicks = 200;
		public double remnantDamageScale = 0.6;
		/** Phase 2's Flash Time: how long the world crawls (ticks) for everyone near him. */
		public int flashTimeTicks = 50;
	}

	public static BlackFlashConfig get() {
		return instance;
	}

	public static void load() {
		instance = SPEC.load();
	}
}
