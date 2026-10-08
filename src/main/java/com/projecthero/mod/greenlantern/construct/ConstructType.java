package com.projecthero.mod.greenlantern.construct;

import com.projecthero.mod.greenlantern.GreenLanternConfig;

/**
 * Every hard-light construct the ring can shape, with its balance numbers. This is the single source
 * of truth {@link GreenLanternConstructs}, the construct-wheel screen and the HUD all read from --
 * adding a construct means adding one entry here, not a new class.
 *
 * <p>v0.11.5: every construct is available the moment a Green Lantern bonds -- the Willpower Mastery
 * progression system that used to gate several of these behind cumulative-energy/situational
 * thresholds has been removed outright (see the deleted {@code GreenLanternMastery}).
 *
 * <p>{@link #kind} tells {@link GreenLanternConstructs} which spawn/tick strategy to use; several
 * constructs share the same kind (e.g. every flat-footprint one is {@link Kind#PLATFORM_BLOCKS}).
 */
public enum ConstructType {
	ENERGY_BLADE(Kind.MELEE_BUFF, GreenLanternConfig.ENERGY_BLADE_COST, GreenLanternConfig.ENERGY_BLADE_UPKEEP_PER_SEC,
			0f, 0, 1),
	CONTAINMENT_CAGE(Kind.CAGE, GreenLanternConfig.CAGE_COST, GreenLanternConfig.CAGE_UPKEEP_PER_SEC,
			GreenLanternConfig.CAGE_HP, GreenLanternConfig.CAGE_MAX_DURATION_TICKS, 2),
	SENTRY_TURRET(Kind.TURRET, GreenLanternConfig.TURRET_COST, GreenLanternConfig.TURRET_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.TURRET_MAX_DURATION_TICKS, 2),
	BATTERING_RAM(Kind.INSTANT, GreenLanternConfig.RAM_COST, 0f, 0f, 0, 0),
	HARD_LIGHT_WALL(Kind.WALL, GreenLanternConfig.WALL_COST, GreenLanternConfig.WALL_UPKEEP_PER_SEC,
			GreenLanternConfig.WALL_HP, GreenLanternConfig.WALL_MAX_DURATION_TICKS, 2),
	PLATFORM(Kind.PLATFORM_BLOCKS, GreenLanternConfig.PLATFORM_COST, GreenLanternConfig.PLATFORM_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.PLATFORM_MAX_DURATION_TICKS, GreenLanternConfig.PLATFORM_SLOT_WEIGHT),
	BRIDGE(Kind.BRIDGE_BLOCKS, GreenLanternConfig.BRIDGE_COST, GreenLanternConfig.BRIDGE_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.BRIDGE_MAX_DURATION_TICKS, GreenLanternConfig.BRIDGE_SLOT_WEIGHT),
	STAIR_RAMP(Kind.RAMP_BLOCKS, GreenLanternConfig.RAMP_COST, GreenLanternConfig.RAMP_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.RAMP_MAX_DURATION_TICKS, GreenLanternConfig.RAMP_SLOT_WEIGHT),
	// v0.11.7: free to equip, only draining GreenLanternConfig.DRILL_UPKEEP_PER_SEC while toggled on.
	MINING_DRILL(Kind.DRILL, GreenLanternConfig.DRILL_START_COST, GreenLanternConfig.DRILL_UPKEEP_PER_SEC, 0f, 0,
			GreenLanternConfig.DRILL_SLOT_WEIGHT),
	// v0.15.9: the light comes from the ring (GreenLanternRingLight) -- toggled, no time limit, night vision while on
	LANTERN_LIGHT(Kind.RING_LIGHT, GreenLanternConfig.LANTERN_LIGHT_COST, GreenLanternConfig.LANTERN_LIGHT_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.LANTERN_LIGHT_MAX_DURATION_TICKS, GreenLanternConfig.LANTERN_LIGHT_SLOT_WEIGHT),
	ATMOSPHERE_BUBBLE(Kind.BUBBLE, GreenLanternConfig.BUBBLE_COST, GreenLanternConfig.BUBBLE_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.BUBBLE_MAX_DURATION_TICKS, GreenLanternConfig.BUBBLE_SLOT_WEIGHT),
	// v0.11.5: an instant grab, not a standing construct any more -- see GreenLanternConstructs#rescueGrab.
	// v0.11.7: the hold itself now costs GreenLanternConfig.TETHER_UPKEEP_PER_SEC to maintain.
	RESCUE_TETHER(Kind.INSTANT, GreenLanternConfig.TETHER_COST, GreenLanternConfig.TETHER_UPKEEP_PER_SEC, 0f, 0, 0),
	CARRY_PLATFORM(Kind.PLATFORM_BLOCKS, GreenLanternConfig.CARRY_PLATFORM_COST, GreenLanternConfig.CARRY_PLATFORM_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.CARRY_PLATFORM_MAX_DURATION_TICKS, GreenLanternConfig.CARRY_PLATFORM_SLOT_WEIGHT),
	// v0.11.4: a diamond pickaxe/axe/shovel synthesised straight into the inventory rather than a
	// block/marker construct -- dismiss-only (no automatic expiry), ended by upkeep failure, Shift+C,
	// or the player dropping any one of the three tools (see GreenLanternConstructs#tickKind's TOOL_KIT case).
	HARD_LIGHT_TOOLS(Kind.TOOL_KIT, GreenLanternConfig.TOOL_KIT_COST, GreenLanternConfig.TOOL_KIT_UPKEEP_PER_SEC,
			0f, 0, GreenLanternConfig.TOOL_KIT_SLOT_WEIGHT),
	// v0.14.3: construct attacks and summons -- hard-light entities (HardLightConstructEntity) run by
	// GreenLanternConstructAttacks rather than block constructs; appended so saved selections keep their ordinals.
	BUZZSAW(Kind.ATTACK, GreenLanternConfig.BUZZSAW_COST, 0f, 0f, 0, 0),
	ANVIL_DROP(Kind.ATTACK, GreenLanternConfig.ANVIL_COST, 0f, 0f, 0, 0),
	CHAIN_SNARE(Kind.ATTACK, GreenLanternConfig.CHAINS_COST, 0f, 0f, GreenLanternConfig.CHAINS_DURATION_TICKS, 0),
	LAUNCH_PAD(Kind.SUMMON, GreenLanternConfig.PAD_COST, 0f, 0f, GreenLanternConfig.PAD_DURATION_TICKS, 1),
	EMERALD_WARRIOR(Kind.SUMMON, GreenLanternConfig.WARRIOR_COST, GreenLanternConfig.WARRIOR_UPKEEP_PER_SEC, 0f,
			GreenLanternConfig.WARRIOR_DURATION_TICKS, 3);

	/** v0.14.3: the construct wheel groups constructs into these four arcs. */
	public enum Category {
		ATTACK, DEFENSE, MOBILITY, UTILITY
	}

	/** How {@link GreenLanternConstructs} spawns/ticks/dismisses a construct of this type. */
	public enum Kind {
		MELEE_BUFF, CAGE, TURRET, INSTANT, WALL, PLATFORM_BLOCKS, BRIDGE_BLOCKS, RAMP_BLOCKS,
		DRILL,
		/** v0.15.9: Lantern Light -- light from the caster's ring + night vision ({@link GreenLanternRingLight}). */
		RING_LIGHT,
		BUBBLE, TOOL_KIT,
		/** v0.14.3: a one-shot hard-light attack entity (Buzzsaw, Anvil Drop, Chain Snare). */
		ATTACK,
		/** v0.14.3: a lasting hard-light entity (Launch Pad, Emerald Warrior). */
		SUMMON
	}

	private final Kind kind;
	private final float initialCost;
	private final float upkeepPerSec;
	private final float maxHp;
	private final int maxDurationTicks;
	private final int slotWeight;

	ConstructType(Kind kind, float initialCost, float upkeepPerSec, float maxHp, int maxDurationTicks,
			int slotWeight) {
		this.kind = kind;
		this.initialCost = initialCost;
		this.upkeepPerSec = upkeepPerSec;
		this.maxHp = maxHp;
		this.maxDurationTicks = maxDurationTicks;
		this.slotWeight = slotWeight;
	}

	public Kind kind() {
		return kind;
	}

	public float initialCost() {
		return initialCost;
	}

	public float upkeepPerSec() {
		return upkeepPerSec;
	}

	public float maxHp() {
		return maxHp;
	}

	/**
	 * v0.15.15 (explicit user request: "remove time limits and cooldowns for all constructs besides the sentry turret
	 * limitations"): every construct but the Sentry Turret lasts until it is dismissed (N), runs dry or is broken -- 0 =
	 * no time limit. The configured durations stay in {@code GreenLanternConfig} for the turret (and as reference).
	 */
	public int maxDurationTicks() {
		return this == SENTRY_TURRET ? maxDurationTicks : 0;
	}

	public int slotWeight() {
		return slotWeight;
	}

	/** v0.14.3: which arc of the construct wheel this sits in. */
	public Category category() {
		return switch (this) {
			case BATTERING_RAM, SENTRY_TURRET, ENERGY_BLADE, BUZZSAW, ANVIL_DROP, EMERALD_WARRIOR -> Category.ATTACK;
			case HARD_LIGHT_WALL, CONTAINMENT_CAGE, CHAIN_SNARE, ATMOSPHERE_BUBBLE, RESCUE_TETHER -> Category.DEFENSE;
			case PLATFORM, BRIDGE, STAIR_RAMP, CARRY_PLATFORM, LAUNCH_PAD -> Category.MOBILITY;
			default -> Category.UTILITY;
		};
	}

	/** v0.14.3: the one-line description the construct wheel shows for the hovered construct. */
	public String descriptionKey() {
		return translationKey() + ".desc";
	}

	public String translationKey() {
		return "projecthero.green_lantern.construct." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static ConstructType byOrdinal(int ordinal) {
		ConstructType[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : HARD_LIGHT_WALL;
	}
}
