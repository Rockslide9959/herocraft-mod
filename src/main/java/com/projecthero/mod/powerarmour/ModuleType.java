package com.projecthero.mod.powerarmour;

import java.util.Locale;

/**
 * v0.17.5 Power Armour: every upgrade module. Each one mounts on one {@link BodyPart}, takes one of that part's slots and
 * {@code output} of the core's output budget. An arm can carry one weapon ({@link #weapon}); the back carries one module (it
 * only ever has one slot).
 */
public enum ModuleType {
	// helmet
	NIGHT_VISOR(BodyPart.HELMET, 1, false),
	ORE_SCANNER(BodyPart.HELMET, 1, false),
	TARGET_LOCK(BodyPart.HELMET, 2, false),
	REBREATHER(BodyPart.HELMET, 1, false),
	// chest
	REACTIVE_PLATING(BodyPart.CHEST, 2, false),
	CAPACITOR_BANK(BodyPart.CHEST, 0, false),
	OVERCLOCK(BodyPart.CHEST, 0, false),
	SOLAR_PLATE(BodyPart.CHEST, 0, false),
	BREACHER_PLATING(BodyPart.CHEST, 1, false),
	// arms (one weapon per suit, plus the clamp)
	GATLING(BodyPart.ARMS, 3, true),
	FLAMER(BodyPart.ARMS, 3, true),
	TESLA_COIL(BodyPart.ARMS, 4, true),
	RAIL_CANNON(BodyPart.ARMS, 6, true),
	POWER_FIST(BodyPart.ARMS, 2, true),
	MAGNETIC_CLAMP(BodyPart.ARMS, 2, false),
	// back
	MISSILE_POD(BodyPart.BACK, 4, false),
	SHIELD_PROJECTOR(BodyPart.BACK, 4, false),
	REPAIR_DRONE(BodyPart.BACK, 3, false),
	SENTRY_TURRET(BodyPart.BACK, 5, false),
	THRUSTER_PACK(BodyPart.BACK, 5, false),
	// legs
	SERVO_BOOSTERS(BodyPart.LEGS, 2, false),
	SEISMIC_PLATES(BodyPart.LEGS, 2, false),
	STABILISERS(BodyPart.LEGS, 1, false);

	public final BodyPart part;
	/** Output budget this module needs from the core. */
	public final int output;
	/** An arm weapon (Shift+R): a suit mounts at most one. */
	public final boolean weapon;

	ModuleType(BodyPart part, int output, boolean weapon) {
		this.part = part;
		this.output = output;
		this.weapon = weapon;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The item id ({@code projecthero:pa_module_<id>}). */
	public String itemId() {
		return "pa_module_" + id();
	}

	public String translationKey() {
		return "item.projecthero." + itemId();
	}

	public static ModuleType byId(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (ModuleType m : values()) {
			if (m.id().equals(id)) {
				return m;
			}
		}
		return null;
	}
}
