package com.projecthero.mod.saiyan;

/**
 * v0.17.3: the Saiyan's forms. Shared by the server (stats, Ki), the moves ({@link #damageMult()}) and the client (hair and
 * aura colours, form names). Numbers are on the mod's damage scale: Base is a little above a plain player and well under a
 * full Kryptonian (+14 attack, 80% damage reduction).
 *
 * <p>Only {@link #BASE} and {@link #SSJ} are in play (user, v0.17.3: "only super saiyan for now"). The later entries stay only
 * so the form ordinals in old saves still parse; {@link #playableOrSsj()} turns them into Super Saiyan, and nothing can
 * transform into them.
 *
 * <pre>
 *   form            attack  health  damage taken  speed  moves x  Ki drain/s  cost
 *   Base             +4      +4       -10%         x1.10   1.00      -        0
 *   Super Saiyan     +8      +8       -25%         x1.20   1.35      -       15
 * </pre>
 */
public enum SaiyanForm {
	BASE("base", 0xFF1C1A1A, 0xFFEFF4FF, 4.0, 4.0, 0.10f, 1.10f, 1.00f, 0.0f, 0f, false),
	SSJ("ssj", 0xFFFFD84A, 0xFFFFE066, 8.0, 8.0, 0.25f, 1.20f, 1.35f, 0.0f, 15f, false),
	SSJ2("ssj2", 0xFFFFE15C, 0xFFFFE680, 11.0, 12.0, 0.35f, 1.28f, 1.65f, 1.0f, 20f, false),
	SSJ3("ssj3", 0xFFFFCC33, 0xFFFFD84D, 14.0, 12.0, 0.40f, 1.32f, 1.95f, 3.0f, 30f, false),
	GOD("god", 0xFFC81E3A, 0xFFFF5A3C, 14.0, 16.0, 0.50f, 1.35f, 2.10f, 1.2f, 30f, true),
	BLUE("blue", 0xFF35C8FF, 0xFF55D4FF, 17.0, 20.0, 0.60f, 1.40f, 2.50f, 2.0f, 40f, true),
	BLUE_OVERDRIVE("blue_overdrive", 0xFF35C8FF, 0xFFFF2A2A, 21.0, 20.0, 0.60f, 1.50f, 3.00f, 4.0f, 20f, true),
	INSTINCT("instinct", 0xFFD8DDE6, 0xFFCFE6FF, 18.0, 20.0, 0.55f, 1.50f, 2.80f, 1.0f, 30f, false);

	/** Lang / command name ({@code projecthero.saiyan.form.<key>}). */
	public final String key;
	/** The hair colour (ARGB) the hair renderer tints with. Base is black. */
	public final int hairColor;
	/** The aura colour (ARGB). Base's is the plain white Ki of a charge. */
	public final int auraColor;
	/** Added to attack damage (melee). */
	public final double attackBonus;
	/** Added to max health. */
	public final double healthBonus;
	/** Share of every incoming hit taken away (0..1). */
	public final float damageReduction;
	private final float speedMult;
	private final float damageMult;
	private final float kiDrainPerSecond;
	/** Ki spent to enter the form. */
	public final float cost;
	/** God and the Blues heal 1 HP every 4 seconds. */
	public final boolean regenerates;

	SaiyanForm(String key, int hairColor, int auraColor, double attackBonus, double healthBonus, float damageReduction, float speedMult,
			float damageMult, float kiDrainPerSecond, float cost, boolean regenerates) {
		this.key = key;
		this.hairColor = hairColor;
		this.auraColor = auraColor;
		this.attackBonus = attackBonus;
		this.healthBonus = healthBonus;
		this.damageReduction = damageReduction;
		this.speedMult = speedMult;
		this.damageMult = damageMult;
		this.kiDrainPerSecond = kiDrainPerSecond;
		this.cost = cost;
		this.regenerates = regenerates;
	}

	/** Multiplier for the moves' damage (Feint, Ki Beam, ...): 1.0 in Base. */
	public float damageMult() {
		return damageMult;
	}

	/** Movement multiplier (walking, the Ki Dash, flight). */
	public float speedMult() {
		return speedMult;
	}

	/** Ki lost every tick while in this form (0 for Base and Super Saiyan). */
	public float kiDrainPerTick() {
		return kiDrainPerSecond / 20f;
	}

	/** The forms a player can be in, in wheel order. */
	public static final SaiyanForm[] PLAYABLE = { BASE, SSJ };

	/** Base or Super Saiyan. */
	public boolean playable() {
		return this == BASE || this == SSJ;
	}

	/** This form if it's in play, otherwise Super Saiyan (an old save left in a removed form). */
	public SaiyanForm playableOrSsj() {
		return playable() ? this : SSJ;
	}

	/** Lang key of the form's name. */
	public String nameKey() {
		return "projecthero.saiyan.form." + key;
	}

	public static SaiyanForm byOrdinal(int i) {
		SaiyanForm[] all = values();
		return i >= 0 && i < all.length ? all[i] : BASE;
	}

	public static SaiyanForm byKey(String key) {
		for (SaiyanForm f : values()) {
			if (f.key.equals(key)) {
				return f;
			}
		}
		return null;
	}
}
