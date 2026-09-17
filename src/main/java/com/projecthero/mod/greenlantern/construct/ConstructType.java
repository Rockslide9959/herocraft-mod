package com.projecthero.mod.greenlantern.construct;

import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

/**
 * Every hard-light construct the ring can shape, with its balance numbers and the Mastery level that
 * unlocks it. This is the single source of truth {@link GreenLanternConstructs}, the construct-wheel
 * screen and the HUD all read from -- adding a construct means adding one entry here, not a new class.
 *
 * <p>{@link #kind} tells {@link GreenLanternConstructs} which spawn/tick strategy to use; several
 * constructs share the same kind (e.g. every flat-footprint one is {@link Kind#PLATFORM_BLOCKS}).
 */
public enum ConstructType {
	ENERGY_BLADE(Kind.MELEE_BUFF, GreenLanternConfig.ENERGY_BLADE_COST, GreenLanternConfig.ENERGY_BLADE_UPKEEP_PER_SEC,
			0f, 0, 1, GreenLanternState.MASTERY_I),
	CONTAINMENT_CAGE(Kind.CAGE, GreenLanternConfig.CAGE_COST, GreenLanternConfig.CAGE_UPKEEP_PER_SEC,
			GreenLanternConfig.CAGE_HP, GreenLanternConfig.CAGE_MAX_DURATION_TICKS, 2, GreenLanternState.MASTERY_III),
	SENTRY_TURRET(Kind.TURRET, GreenLanternConfig.TURRET_COST, GreenLanternConfig.TURRET_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.TURRET_MAX_DURATION_TICKS, 2, GreenLanternState.MASTERY_IV),
	BATTERING_RAM(Kind.INSTANT_RAM, GreenLanternConfig.RAM_COST, 0f, 0f, 0, 0, GreenLanternState.MASTERY_IV),
	// v0.11.4: Bonded (was Mastery II) -- a brand-new Green Lantern had no defensive construct option
	// at all before this; Wall is the obvious one to make available from the start.
	HARD_LIGHT_WALL(Kind.WALL, GreenLanternConfig.WALL_COST, GreenLanternConfig.WALL_UPKEEP_PER_SEC,
			GreenLanternConfig.WALL_HP, GreenLanternConfig.WALL_MAX_DURATION_TICKS, 2, GreenLanternState.MASTERY_BONDED),
	PLATFORM(Kind.PLATFORM_BLOCKS, GreenLanternConfig.PLATFORM_COST, GreenLanternConfig.PLATFORM_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.PLATFORM_MAX_DURATION_TICKS, GreenLanternConfig.PLATFORM_SLOT_WEIGHT,
			GreenLanternState.MASTERY_BONDED),
	BRIDGE(Kind.BRIDGE_BLOCKS, GreenLanternConfig.BRIDGE_BASE_COST, GreenLanternConfig.BRIDGE_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.BRIDGE_MAX_DURATION_TICKS, GreenLanternConfig.BRIDGE_SLOT_WEIGHT,
			GreenLanternState.MASTERY_I),
	STAIR_RAMP(Kind.RAMP_BLOCKS, GreenLanternConfig.RAMP_BASE_COST, GreenLanternConfig.RAMP_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.RAMP_MAX_DURATION_TICKS, GreenLanternConfig.RAMP_SLOT_WEIGHT,
			GreenLanternState.MASTERY_BONDED),
	MINING_DRILL(Kind.DRILL, GreenLanternConfig.DRILL_START_COST, 0f, 0f, 0,
			GreenLanternConfig.DRILL_SLOT_WEIGHT, GreenLanternState.MASTERY_III),
	LANTERN_LIGHT(Kind.LIGHT_BLOCKS, GreenLanternConfig.LANTERN_LIGHT_COST, GreenLanternConfig.LANTERN_LIGHT_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.LANTERN_LIGHT_MAX_DURATION_TICKS, GreenLanternConfig.LANTERN_LIGHT_SLOT_WEIGHT,
			GreenLanternState.MASTERY_BONDED),
	ATMOSPHERE_BUBBLE(Kind.BUBBLE, GreenLanternConfig.BUBBLE_COST, GreenLanternConfig.BUBBLE_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.BUBBLE_MAX_DURATION_TICKS, GreenLanternConfig.BUBBLE_SLOT_WEIGHT,
			GreenLanternState.MASTERY_II),
	RESCUE_TETHER(Kind.TETHER, GreenLanternConfig.TETHER_COST, GreenLanternConfig.TETHER_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.TETHER_MAX_PULL_TICKS, GreenLanternConfig.TETHER_SLOT_WEIGHT,
			GreenLanternState.MASTERY_I),
	CARRY_PLATFORM(Kind.PLATFORM_BLOCKS, GreenLanternConfig.CARRY_PLATFORM_COST, GreenLanternConfig.CARRY_PLATFORM_UPKEEP_PER_SEC,
			0f, GreenLanternConfig.CARRY_PLATFORM_MAX_DURATION_TICKS, GreenLanternConfig.CARRY_PLATFORM_SLOT_WEIGHT,
			GreenLanternState.MASTERY_III),
	// v0.11.4: a diamond pickaxe/axe/shovel synthesised straight into the inventory rather than a
	// block/marker construct -- dismiss-only (no automatic expiry), ended by upkeep failure, Shift+C,
	// or the player dropping any one of the three tools (see GreenLanternConstructs#tickKind's TOOL_KIT case).
	HARD_LIGHT_TOOLS(Kind.TOOL_KIT, GreenLanternConfig.TOOL_KIT_COST, GreenLanternConfig.TOOL_KIT_UPKEEP_PER_SEC,
			0f, 0, GreenLanternConfig.TOOL_KIT_SLOT_WEIGHT, GreenLanternState.MASTERY_BONDED);

	/** How {@link GreenLanternConstructs} spawns/ticks/dismisses a construct of this type. */
	public enum Kind {
		MELEE_BUFF, CAGE, TURRET, INSTANT_RAM, WALL, PLATFORM_BLOCKS, BRIDGE_BLOCKS, RAMP_BLOCKS,
		DRILL, LIGHT_BLOCKS, BUBBLE, TETHER, TOOL_KIT
	}

	private final Kind kind;
	private final float initialCost;
	private final float upkeepPerSec;
	private final float maxHp;
	private final int maxDurationTicks;
	private final int slotWeight;
	private final int requiredMastery;

	ConstructType(Kind kind, float initialCost, float upkeepPerSec, float maxHp, int maxDurationTicks,
			int slotWeight, int requiredMastery) {
		this.kind = kind;
		this.initialCost = initialCost;
		this.upkeepPerSec = upkeepPerSec;
		this.maxHp = maxHp;
		this.maxDurationTicks = maxDurationTicks;
		this.slotWeight = slotWeight;
		this.requiredMastery = requiredMastery;
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

	public int maxDurationTicks() {
		return maxDurationTicks;
	}

	public int slotWeight() {
		return slotWeight;
	}

	public int requiredMastery() {
		return requiredMastery;
	}

	public boolean unlockedFor(GreenLanternState state) {
		return state.hasMastery(requiredMastery);
	}

	public String translationKey() {
		return "projecthero.green_lantern.construct." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static ConstructType byOrdinal(int ordinal) {
		ConstructType[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : HARD_LIGHT_WALL;
	}
}
