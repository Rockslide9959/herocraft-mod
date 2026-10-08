package com.projecthero.mod.carnage;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.14.25: Carnage's tuning, at {@code config/projecthero_carnage.json} (loaded through {@link VersionedConfig}, see
 * {@code docs/CONFIGS.md}). The natural meteor spawn is a once-a-minute roll per Overworld player at night: a small
 * chance for anyone, a bigger one for a Symbiote host (Carnage hunts his own kind).
 */
public final class CarnageConfig {
	public static final VersionedConfig<CarnageConfig> SPEC = VersionedConfig.builder(CarnageConfig.class, "projecthero_carnage.json", CarnageConfig::new)
			.balance("boss")
			.introduce(1, "v0.14.25 Carnage")
			// v0.15.15: 1000 HP base (cap 3500) and 2 HP every 2 s regeneration -- reset the boss section so an old file
			// does not keep 700 HP / the old regen forever
			.reset(2, "v0.15.15 Carnage 1000 HP, 2 HP per 2 s regen", "boss")
			.build();

	private static CarnageConfig instance = new CarnageConfig();

	public Integer configVersion;
	public boolean naturalSpawnEnabled = true;
	/** Per player per minute, at night in the Overworld. */
	public double spawnChancePerMinute = 0.002;
	/** The same, for a player bonded to a Symbiote. */
	public double symbioteHostChancePerMinute = 0.008;
	/** No second meteor in a dimension this soon after one landed (minutes). */
	public int cooldownAfterSpawnMinutes = 40;
	/** ...or after Carnage was killed (minutes). */
	public int cooldownAfterKillMinutes = 60;
	public Boss boss = new Boss();
	/** How long his brood has before it crawls back into him (ticks). */
	public int reabsorbAfterTicks = 400;

	public static final class Boss {
		public double health = 1000.0;
		public double healthPerExtraFighter = 250.0;
		public double maxHealth = 3500.0;
		/** v0.15.15: health regenerated every {@link #regenIntervalTicks} (doubled while frenzied; fire stops it). */
		public double regenAmount = 2.0;
		public int regenIntervalTicks = 40;
	}

	public static CarnageConfig get() {
		return instance;
	}

	public static double healthFor(int players) {
		Boss b = instance.boss;
		return Math.min(b.maxHealth, b.health + b.healthPerExtraFighter * Math.max(0, players - 1));
	}

	public static void load() {
		instance = SPEC.load();
	}
}
