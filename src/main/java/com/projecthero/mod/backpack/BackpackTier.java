package com.projecthero.mod.backpack;

/**
 * v0.17.5 backpacks: the five tiers. Rows of nine slots (the screen is a chest screen that many rows tall), the canvas colour an
 * undyed one shows, and whether it shrugs off fire and lava like netherite gear. See {@code docs/BACKPACKS_REFERENCE.md}.
 */
public enum BackpackTier {
	LEATHER("leather", 3, 0x9C6236, false),
	IRON("iron", 4, 0x5E6B78, false),
	GOLD("gold", 5, 0x8E3B2E, false),
	DIAMOND("diamond", 6, 0x2E6E8E, false),
	NETHERITE("netherite", 6, 0x564C50, true);

	private final String id;
	private final int rows;
	private final int defaultColor;
	private final boolean fireproof;

	BackpackTier(String id, int rows, int defaultColor, boolean fireproof) {
		this.id = id;
		this.rows = rows;
		this.defaultColor = defaultColor;
		this.fireproof = fireproof;
	}

	public String id() {
		return id;
	}

	public int rows() {
		return rows;
	}

	public int slots() {
		return rows * 9;
	}

	/** RGB of the canvas when it has never been dyed. */
	public int defaultColor() {
		return defaultColor;
	}

	public boolean fireproof() {
		return fireproof;
	}

	public static BackpackTier byOrdinal(int i) {
		BackpackTier[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, i))];
	}
}
