package com.projecthero.mod.grave;

/**
 * How a player picked up the Gravebound Curse. Recorded for statistics, the debug command and the
 * curse HUD tooltip only -- it deliberately has <b>no effect whatsoever</b> on how the curse behaves
 * (spec section 7: "the source should not affect how the curse behaves"). Both real routes converge
 * on exactly the same timer, the same removal rules and the same raid.
 */
public enum CurseSource {
	/** Activated the Cursed Grave at the heart of a naturally generated Graveyard. */
	GRAVEYARD,
	/** Took damage from a rare naturally spawning Cursed Zombie. */
	CURSED_ZOMBIE,
	/** Deliberately triggered with a Grave Ritual Totem after a first clear. */
	RITUAL,
	/** Applied by an operator with the debug command. */
	COMMAND;

	public String translationKey() {
		return "curse.projecthero.source." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static CurseSource byName(String name) {
		for (CurseSource s : values()) {
			if (s.name().equals(name)) {
				return s;
			}
		}
		return COMMAND;
	}
}
