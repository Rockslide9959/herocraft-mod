package com.projecthero.mod.targetdummy;

/**
 * v0.17.5: what sort of damage a hit on a {@link TargetDummyEntity} was -- picks the colour of its floating number.
 * Sent as the ordinal, so only ever append.
 */
public enum DummyDamageKind {
	/** Fists, weapons, mob attacks, anything plain. */
	PHYSICAL(0xF2F2F2),
	/** Arrows, tridents, thrown things, shots. */
	PROJECTILE(0x8EE36B),
	/** Magic, potions, poison, wither, thorns, sonic booms. */
	MAGIC(0xC58CFF),
	/** Fire, burning, lava, magma. */
	FIRE(0xFF8C2E),
	/** Explosions. */
	EXPLOSION(0xFF4B4B),
	/** Lightning. */
	LIGHTNING(0x7FE9FF),
	/** Freezing (powder snow, ice powers that use the freeze type). */
	FREEZE(0xB9F0FF),
	/** A hero power: the mod's own damage types, and player-caused hits right after an ability key. */
	POWER(0x52A8FF),
	/** Something set the dummy's health straight down without a damage source ("true damage"). */
	DIRECT(0xFF5FD2);

	/** The crit colour (bold, bigger): bright gold. */
	public static final int CRIT_RGB = 0xFFD23C;

	private final int rgb;

	DummyDamageKind(int rgb) {
		this.rgb = rgb;
	}

	public int rgb() {
		return rgb;
	}

	public static DummyDamageKind byId(int id) {
		DummyDamageKind[] all = values();
		return id >= 0 && id < all.length ? all[id] : PHYSICAL;
	}
}
