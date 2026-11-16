package com.projecthero.mod.powerarmour;

import java.util.Locale;

/**
 * v0.17.5 Power Armour: the swappable power cores. {@code capacity} is the charge it holds (energy units, "E"); {@code output}
 * is how much module load it can run at once (the editor's output budget).
 */
public enum CoreType {
	FUSION_CELL(1000f, 6),
	ARC_CORE(2500f, 10),
	STAR_CORE(6000f, 16);

	public final float capacity;
	public final int output;

	CoreType(float capacity, int output) {
		this.capacity = capacity;
		this.output = output;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static CoreType byId(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (CoreType c : values()) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}
}
