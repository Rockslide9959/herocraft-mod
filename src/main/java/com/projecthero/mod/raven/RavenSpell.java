package com.projecthero.mod.raven;

/**
 * v0.17.3 Raven revamp: the ten spells of the <b>Spell Wheel</b> (hold V, point, let go; tap V casts the picked one). The order is
 * the wheel's (clockwise from the top) and the icon atlas's ({@code textures/gui/raven/spells.png}, 16 px each, left to right). The
 * picked spell is saved as its ordinal in {@code RavenState.spell}. Each spell's cooldown is kept under its {@link #id}.
 */
public enum RavenSpell {
	VOID_VORTEX("void_vortex", true, true, RavenConfig.VORTEX_COST, RavenConfig.VORTEX_COOLDOWN),
	SHADOW_BINDING("shadow_binding", true, true, RavenConfig.BIND_COST, RavenConfig.BIND_COOLDOWN),
	SOUL_DETONATION("soul_detonation", true, false, RavenConfig.DETONATE_COST, RavenConfig.DETONATE_COOLDOWN),
	SOUL_DEVOUR("soul_devour", true, false, RavenConfig.DEVOUR_COST, RavenConfig.DEVOUR_COOLDOWN),
	DEBRIS_STORM("debris_storm", true, false, RavenConfig.DEBRIS_COST, RavenConfig.DEBRIS_COOLDOWN),
	DIMENSIONAL_RIFT("dimensional_rift", false, true, RavenConfig.RIFT_COST, RavenConfig.RIFT_COOLDOWN),
	SOUL_RESTORATION("soul_restoration", false, false, RavenConfig.HEAL_COST, RavenConfig.HEAL_COOLDOWN),
	TELEKINETIC_PULL("telekinetic_pull", false, false, RavenConfig.PULL_COST, RavenConfig.PULL_COOLDOWN),
	ASTRAL_SANCTUARY("astral_sanctuary", false, false, RavenConfig.SANCTUARY_COST, RavenConfig.SANCTUARY_COOLDOWN),
	SHADOW_VEIL("shadow_veil", false, false, RavenConfig.VEIL_COST, RavenConfig.VEIL_COOLDOWN);

	private static final RavenSpell[] VALUES = values();

	/** Cooldown key and lang id ({@code projecthero.raven.spell.<id>}, {@code .desc}, {@code .short}). */
	public final String id;
	/** A Combat spell (the first five) rather than a Utility one. */
	public final boolean combat;
	/** Cast at a spot where she looks: an Unsettled misfire moves the spot instead of wasting the spell. */
	public final boolean placed;
	public final float cost;
	public final int cooldown;

	RavenSpell(String id, boolean combat, boolean placed, float cost, int cooldown) {
		this.id = id;
		this.combat = combat;
		this.placed = placed;
		this.cost = cost;
		this.cooldown = cooldown;
	}

	/** The spell for a saved ordinal (anything out of range is the first). */
	public static RavenSpell of(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : VOID_VORTEX;
	}

	public static RavenSpell byId(String id) {
		for (RavenSpell s : VALUES) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}

	public static int count() {
		return VALUES.length;
	}

	public String langKey() {
		return "projecthero.raven.spell." + id;
	}
}
