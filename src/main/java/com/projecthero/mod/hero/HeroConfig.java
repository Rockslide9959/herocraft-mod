package com.projecthero.mod.hero;

import com.projecthero.mod.config.VersionedConfig;

/**
 * Server-side, hand-rolled JSON config at {@code config/projecthero.json}. Deliberately dependency-free
 * (GSON is already on the classpath via Minecraft). Loaded once at mod init; missing file is written
 * with defaults. All experimental-power tuning that a server/modpack might want to change lives here.
 *
 * <p>Thor is intentionally NOT configured here -- its balance constants stay where they are, so
 * enabling/disabling experimental knobs can never change Thor behaviour.
 */
public final class HeroConfig {
	/**
	 * v0.14.21: loaded through the shared {@link VersionedConfig} (see {@code docs/CONFIGS.md}). No default here has
	 * changed since the file was created, so version 1 only stamps it. Bump it (with a step) when a balance default
	 * changes.
	 */
	public static final VersionedConfig<HeroConfig> SPEC = VersionedConfig.builder(HeroConfig.class, "projecthero.json", HeroConfig::new)
			.balance("mutationCapacity", "unstableMutationDurationTicks", "symbioteHostChance")
			.introduce(1, "versioned (v0.14.21)")
			.build();

	private static HeroConfig instance = new HeroConfig();

	public Integer configVersion;

	// ---- mutation ----
	/** Maximum minor mutations a single player may permanently own. Spec default: 3. */
	public int mutationCapacity = 3;
	/** Ticks the "Unstable Mutation" serum effect lasts before the exposure window closes. */
	public int unstableMutationDurationTicks = 60 * 20;

	// ---- abilities ----
	/** Global multiplier applied to every experimental ability's base cooldown. */
	public double cooldownMultiplier = 1.0;
	/** If false, abilities that would break/place blocks only produce cosmetic/temporary effects. */
	public boolean abilityTerrainDamage = true;
	/** If false, experimental abilities deal no damage to other players. */
	public boolean abilityPvpDamage = true;
	/** If false, fire-creating abilities never spread fire to the world. */
	public boolean abilityFireSpread = false;
	/** If false, hard crowd control (prisons, grabs, stuns) cannot be applied to players at all. */
	public boolean abilityHardCrowdControlOnPlayers = true;
	/** Multiplier on how much world-changing a large ultimate is allowed to do. */
	public double largeAbilityDestruction = 1.0;

	// ---- v0.17.7 City tier (mutations) ----
	/** No single mutation hit (abilities, or melee lifted by mutation bonuses) goes past this. City's ultimate max. */
	public double mutationMaxHit = 30.0;
	/** The most damage reduction all owned mutations' passive / mode rules may add up to together (0.5 = half off). */
	public double mutationMaxDamageReduction = 0.5;
	/** The most extra max health all owned mutations may add together (16 = 8 hearts). */
	public double mutationMaxBonusHealth = 16.0;

	// ---- worldgen ----
	/** Multiplier on structure spacing (>1 = rarer). Applied by the placement config where possible. */
	public double structureRarityMultiplier = 1.0;

	// ---- symbiote ----
	/**
	 * v0.9.10: chance a naturally spawning hostile mob is a rare <b>Symbiote Host</b> -- buffed, black
	 * particle aura, and it drops a free-floating Symbiote when killed. 0 disables the host spawn
	 * entirely (the meteor crater and the lab structure still generate). Default 0.0015 (~1 in 650).
	 */
	public double symbioteHostChance = 0.0015;
	/**
	 * v0.17.7 (user: "make symbiote entities disappear after a while please, a lot spawn sometimes"): ticks a wild free
	 * Symbiote may go without a player within {@link #freeSymbioteDespawnRange} blocks before it dissolves. Lab
	 * (confined), named, command-spawned and mid-bond / mid-Carnage-call Symbiotes never do. 0 or less = never despawn.
	 * Default 6000 (5 minutes).
	 */
	public int freeSymbioteDespawnTicks = 20 * 60 * 5;
	/** v0.17.7: a player this close (blocks) keeps a wild Symbiote around and restarts its despawn clock. Default 32. */
	public double freeSymbioteDespawnRange = 32.0;
	/**
	 * v0.17.7: a dying host (or a wild pet Symbiote tearing free) only frees a new Symbiote when fewer than this many wild
	 * ones are already within 32 blocks -- otherwise its Symbiote shrivels away with it. 0 or less = no cap. Default 3.
	 */
	public int freeSymbioteMaxNearby = 3;

	private HeroConfig() {
	}

	public static HeroConfig get() {
		return instance;
	}

	public static void load() {
		// migrates, fills in new keys, repairs a corrupt file and always rewrites it
		instance = SPEC.load();
	}

	public int scaledCooldown(int baseTicks) {
		if (baseTicks <= 0) {
			return 0;
		}
		return Math.max(1, (int) Math.round(baseTicks * cooldownMultiplier));
	}
}
