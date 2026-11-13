package com.projecthero.mod.saiyan;

/**
 * v0.17.3 Saiyan revamp: the looks of the two forms (the numbers are in {@link SaiyanConfig}). Base, and Super Saiyan -- reached
 * only through a full Rage bar and held for exactly 3 minutes ({@link Saiyan#transform}). Shared by the server and the client
 * (hair and aura colours, form names).
 */
public enum SaiyanForm {
	BASE("base", 0xFF1C1A1A, 0xFFEFF4FF),
	SSJ("ssj", 0xFFFFD84A, 0xFFFFE066),
	/** v0.17.3 the secret Ultra Instinct ({@link SaiyanUltraInstinct}): silver. Only from Super Saiyan. */
	ULTRA("ultra", 0xFFD8DEE8, 0xFFE8F0FF);

	/** Lang name ({@code projecthero.saiyan.form.<key>}). */
	public final String key;
	/** The hair colour (ARGB) the hair renderer tints with. Base is black. */
	public final int hairColor;
	/** The aura colour (ARGB). Base's is the plain white Ki of a charge. */
	public final int auraColor;

	SaiyanForm(String key, int hairColor, int auraColor) {
		this.key = key;
		this.hairColor = hairColor;
		this.auraColor = auraColor;
	}

	/** Kept for the aura code: every form is in play now. */
	public SaiyanForm playableOrSsj() {
		return this;
	}

	public String nameKey() {
		return "projecthero.saiyan.form." + key;
	}
}
