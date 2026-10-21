package com.projecthero.mod.ironman.scan;

/**
 * v0.16.7 (user): the Iron Man target scanner's threat assessment, as pure maths (no client or server classes) so the
 * gametests can pin it down. The server works out the real numbers ({@link IronManScan}) -- what one of the target's hits
 * does to <i>you</i> through your armour and your suit's integrity absorption, and how many of your hits it takes to
 * bring it down -- and this turns them into one of five levels the HUD colours and names.
 */
public final class IronManThreat {
	/** The five threat levels, mildest first. */
	public enum Level {
		MINIMAL(0xFF7FE9FF), LOW(0xFF66E0A0), MODERATE(0xFFFFC24A), HIGH(0xFFFF8A3C), EXTREME(0xFFFF4848);

		/** The HUD colour (cyan, green, amber, orange, red). */
		public final int colour;

		Level(int colour) {
			this.colour = colour;
		}

		public String langKey() {
			return "hud.projecthero.ironman.threat." + name().toLowerCase(java.util.Locale.ROOT);
		}

		public static Level of(int ordinal) {
			Level[] all = values();
			return all[Math.max(0, Math.min(all.length - 1, ordinal))];
		}
	}

	/** Max health from which a target counts as a boss (the old scanner's rule). */
	public static final float BOSS_HEALTH = 150f;

	private IronManThreat() {
	}

	/**
	 * The threat level.
	 *
	 * @param toYou      health one of its hits takes off you (after your armour and the suit's absorption)
	 * @param yourHealth your current health
	 * @param yourHits   how many of your hits bring it down (0 = you cannot hurt it)
	 * @param boss       a boss-sized target
	 * @param hasPower   a player with a power
	 * @param hostile    it attacks on sight (a monster, a player, or a mob already targeting you)
	 */
	public static Level level(float toYou, float yourHealth, int yourHits, boolean boss, boolean hasPower, boolean hostile) {
		if (toYou <= 0.01f && !boss) {
			return Level.MINIMAL;
		}
		float frac = toYou / Math.max(1f, yourHealth);
		boolean unkillable = yourHits <= 0;
		Level l;
		if (frac >= 0.5f || (boss && (frac >= 0.2f || unkillable))) {
			l = Level.EXTREME;
		} else if (frac >= 0.25f || boss || yourHits >= 25) {
			l = Level.HIGH;
		} else if (frac >= 0.1f || yourHits >= 8 || hasPower || unkillable) {
			l = Level.MODERATE;
		} else {
			l = Level.LOW;
		}
		// a neutral mob only fights back: never more than LOW unless it is already after you
		if (!hostile && l.ordinal() > Level.LOW.ordinal()) {
			l = Level.LOW;
		}
		return l;
	}

	/** Hits of {@code perHit} needed to take {@code health} (0 when {@code perHit} cannot hurt it). */
	public static int hitsToDown(float health, float perHit) {
		if (perHit <= 0.01f) {
			return 0;
		}
		return Math.max(1, (int) Math.ceil(health / perHit - 1e-4f));
	}

	/** "~1.5" -- a hit's damage as hearts, to the nearest half heart (at least half a heart when it does anything). */
	public static String hearts(float damage) {
		if (damage <= 0.01f) {
			return "0";
		}
		float h = Math.max(0.5f, Math.round(damage) / 2f);
		return h == Math.floor(h) ? String.valueOf((int) h) : String.format(java.util.Locale.ROOT, "%.1f", h);
	}
}
