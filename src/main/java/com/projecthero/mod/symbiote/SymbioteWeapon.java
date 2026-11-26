package com.projecthero.mod.symbiote;

/**
 * v0.17.5 (user: "create an actual blade model and create other weapons like axes and stuff"): the weapons a Normal host's right
 * arm can grow. V's Form cycle runs Normal -> Blade -> Axe -> Mace -> Whip -> Thorns -> Normal ({@link SymbioteVitalsManager#cycleForm});
 * while one is out {@link SymbioteVitals#bladeActive} is set (the old Blade rules -- an empty main hand, 0.3 Biomass a second -- cover
 * every weapon) and {@link SymbioteVitals#weapon} holds the ordinal.
 *
 * <p>Numbers are balanced against the old Blade (+5 damage, +50% attack speed, a 2-damage bite, Blade Slash 8 + 2 on Z) and the kit's
 * scale (Tendril Strike 15, Maw Bite 18, Spear 26, Onslaught 20 + DoT on 90 s):
 * <pre>
 *   weapon  punch damage  attack speed  extra reach  on a punch                Z (shares Z's tap cooldown slot, own length)
 *   Blade   +2            +50%          +0.5         2-damage bite (as before)  Blade Slash   12 + 2, 5-block cone        8 s
 *   Axe     +3            -25%          0            breaks a raised shield     Cleave        14 + 4, 4.5-block line     8 s
 *   Mace    +2            -15%          0            knockback 0.8              Ground Smash  14, 4-block ring, Slow II   8 s
 *   Whip    +1            +10%          +2.5         --                         Lash          12, 7 blocks, yanks in      8 s
 * </pre>
 */
public enum SymbioteWeapon {
	// v0.17.5 moveset v3 (user: "lower cooldown of z move"): every weapon Z 8 s (was 15 / 14 / 16 / 10 s)
	// v0.17.7 City tier: punch bonus on top of the suit's 11 -- Blade 13, Axe 14, Mace 13, Whip 12 (was +5 / +8 / +6 / +3)
	BLADE("blade", 2.0, 0.5, 0.5, 160),
	AXE("axe", 3.0, -0.25, 0.0, 160),
	MACE("mace", 2.0, -0.15, 0.0, 160),
	WHIP("whip", 1.0, 0.10, 2.5, 160);

	/** Lang / id fragment ({@code projecthero.symbiote.form.<id>}). */
	public final String id;
	/** Added to the host's attack damage while it is out. */
	public final double bonusDamage;
	/** Multiplies the base attack speed (ADD_MULTIPLIED_BASE). */
	public final double attackSpeed;
	/** Added to {@code entity_interaction_range} (stacks with the suit's own +1). */
	public final double reach;
	/** The Z move's cooldown in ticks (all four share Z's tap entry, so a weapon swap never hands out a fresh Z). */
	public final int zCooldown;

	SymbioteWeapon(String id, double bonusDamage, double attackSpeed, double reach, int zCooldown) {
		this.id = id;
		this.bonusDamage = bonusDamage;
		this.attackSpeed = attackSpeed;
		this.reach = reach;
		this.zCooldown = zCooldown;
	}

	public static SymbioteWeapon byOrdinal(int i) {
		SymbioteWeapon[] all = values();
		return i >= 0 && i < all.length ? all[i] : BLADE;
	}
}
