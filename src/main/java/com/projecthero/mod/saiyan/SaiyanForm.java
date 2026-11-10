package com.projecthero.mod.saiyan;

/**
 * Stand-in, replaced by core. v0.17.3: the Saiyan forms with the numbers the moves read. The core agent's real enum keeps
 * these names and methods; take the core's version on merge.
 */
public enum SaiyanForm {
	BASE(1.0f, 1.0f, 0f, 0xFF2A1A12, 0xFFFFFFFF),
	SSJ(1.35f, 1.15f, 0f, 0xFFFFD84A, 0xFFFFE070),
	SSJ2(1.6f, 1.2f, 0.05f, 0xFFFFE25A, 0xFFFFE890),
	SSJ3(1.85f, 1.25f, 0.12f, 0xFFFFD040, 0xFFFFD860),
	GOD(2.0f, 1.3f, 0.06f, 0xFFD8203A, 0xFFFF5040),
	BLUE(2.3f, 1.35f, 0.08f, 0xFF3AD0FF, 0xFF50C8FF),
	BLUE_OVERDRIVE(2.7f, 1.45f, 0.16f, 0xFF3AD0FF, 0xFFFF3030),
	INSTINCT(2.5f, 1.5f, 0.1f, 0xFFDDE4F0, 0xFFE8F0FF);

	private final float damageMult;
	private final float speedMult;
	private final float kiDrainPerTick;
	public final int hairColor;
	public final int auraColor;

	SaiyanForm(float damageMult, float speedMult, float kiDrainPerTick, int hairColor, int auraColor) {
		this.damageMult = damageMult;
		this.speedMult = speedMult;
		this.kiDrainPerTick = kiDrainPerTick;
		this.hairColor = hairColor;
		this.auraColor = auraColor;
	}

	public float damageMult() {
		return damageMult;
	}

	public float speedMult() {
		return speedMult;
	}

	public float kiDrainPerTick() {
		return kiDrainPerTick;
	}
}
