package com.projecthero.mod.powerarmour;

import java.util.Locale;

/** v0.17.5 Power Armour: where an upgrade module mounts. */
public enum BodyPart {
	HELMET,
	CHEST,
	ARMS,
	BACK,
	LEGS;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "projecthero.power_armour.part." + id();
	}
}
