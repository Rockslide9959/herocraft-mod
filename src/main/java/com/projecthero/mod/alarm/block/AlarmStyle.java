package com.projecthero.mod.alarm.block;

import net.minecraft.util.StringRepresentable;

/**
 * v0.17.7: the look of an alarm block. {@link #TECH}: steel, screens and red lights (hideouts, the Vault, the Sky
 * Fortress). {@link #ANCIENT}: carved stone and braziers -- the Ancient Temple's "wards". Same behaviour either way.
 */
public enum AlarmStyle implements StringRepresentable {
	TECH("tech"), ANCIENT("ancient");

	private final String name;

	AlarmStyle(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
