package com.projecthero.mod.titanshifter;

import java.util.List;

/**
 * v0.16.7 (user, Titan revamp): every move of the four Titan kits and the human-form (Partial Shifting) kit, in one table
 * the server ({@link TitanKit}, {@link TitanHumanKit}) and the client HUD / guide both read.
 *
 * <p>Each entry: the cooldown id (kept in {@code TitanShifterState#abilityReadyAt}), the GeckoLib clip it triggers
 * ({@code animation.titan.<anim>} in the type's own animation file; human moves use player poses instead), the recharge,
 * the headline damage and the impact tick -- the tick after the key press on which the hit lands, so each clip's contact
 * frame must sit there.
 *
 * <pre>
 *            R                    G                     X                      Z                    V             C
 * Attack     Punch combo / Kick   Heavy Smash / Uppercut Leap / RAGE           Stomp / Dropkick     Roar / Nape   Regen / Hardening
 * Armoured   Punch combo / Elbow  Heavy Smash / Ground   Leap / SHOULDER       Stomp / Brace        Roar / Nape   Regen / Hardening
 *                                 Pound                  CHARGE
 * Female     Jab combo / Round-   Axe Kick / Sweep Kick  Leap / CRYSTAL FISTS  Stomp / Scream       Roar / Nape   Regen / Hardening
 *            house
 * Beast      Swipe combo /        Heavy Smash / Rock     Leap / BOULDER THROW  Stomp / Boulder      Roar / Nape   Regen / Hardening
 *            Pitcher's Fastball   Scatter                                      Volley
 * Human      Titan Arm / Crystal  Steam Burst / Bite     Crystal Wall /        -                    -             -
 *            Blade                Shift                  Crystal Cocoon
 * </pre>
 * N / Shift+N (grab, bite, set down) and H (shift) are unchanged.
 */
public enum TitanMove {
	// ---- shared by every Titan (the legacy ones read their numbers from the config) ----
	PUNCH("punch", "punch", -1, -1, 6),
	KICK("kick", "kick", 40, -1, 7),
	HEAVY_SMASH("heavy_smash", "smash", -1, -1, 20),
	LEAP("leap", "leap", -1, -1, 6),
	STOMP("stomp", "stomp", -1, -1, 10),
	ROAR("roar", "roar", -1, 0, 10),
	NAPE_GUARD("nape_guard", "nape_guard", 400, 0, 2),
	REGEN("regeneration", "regeneration", -1, 0, 0),
	HARDEN("hardening", "hardening", -1, 0, 0),
	// ---- Attack Titan ----
	UPPERCUT("uppercut", "uppercut", 120, 38, 8),
	RAGE("rage", "rage", 600, 0, 10),
	DROPKICK("dropkick", "dropkick", 180, 32, 8),
	// ---- Armoured Titan ----
	ELBOW("elbow", "elbow", 60, 30, 7),
	GROUND_POUND("ground_pound", "ground_pound", 240, 55, 26),
	SHOULDER_CHARGE("shoulder_charge", "shoulder_charge", 240, 40, 8),
	BRACE("brace", "brace", 300, 0, 4),
	// ---- Female Titan ----
	ROUNDHOUSE("roundhouse", "roundhouse", 50, 34, 8),
	AXE_KICK("axe_kick", "axe_kick", 140, 42, 12),
	SWEEP_KICK("sweep_kick", "sweep_kick", 160, 24, 9),
	CRYSTAL_FISTS("crystal_fists", "crystal_fists", 600, 0, 6),
	SCREAM("scream", "scream", 500, 0, 12),
	// ---- Beast Titan ----
	// v0.16.7 (user): Pitcher's Fastball replaced the Backhand on Shift+R -- 9 a stone, at most 3 stones on one target
	FASTBALL("fastball", "fastball", 160, 9, 22),
	ROCK_SCATTER("rock_scatter", "rock_scatter", 160, 18, 12),
	BOULDER_THROW("boulder_throw", "boulder_throw", 200, 45, 16),
	BOULDER_VOLLEY("boulder_volley", "boulder_volley", 280, 20, 14),
	// ---- human form: Partial Shifting (player poses, see TitanShifterPose on the client) ----
	TITAN_ARM("titan_arm", null, -1, -1, 6),
	CRYSTAL_BLADE("crystal_blade", null, -1, -1, 3),
	STEAM_BURST("steam_burst", null, -1, -1, 2),
	BITE_SHIFT("bite_shift", null, 0, 0, 0),
	CRYSTAL_WALL("crystal_wall", null, -1, 0, 4),
	CRYSTAL_COCOON("crystal_cocoon", null, -1, 0, 0);

	/** The cooldown id (also the lang suffix: {@code projecthero.titan_shifter.ability.<id>}). */
	public final String id;
	/** {@code animation.titan.<anim>}, or null for a human-form move. */
	public final String anim;
	private final int cooldown;
	private final double damage;
	/** Ticks from the key press to the hit (the clip's contact frame). */
	public final int impactTick;

	TitanMove(String id, String anim, int cooldown, double damage, int impactTick) {
		this.id = id;
		this.anim = anim;
		this.cooldown = cooldown;
		this.damage = damage;
		this.impactTick = impactTick;
	}

	/** Every clip this revamp added to the Titan action controller (each Titan type's animation file must define them). */
	public static final List<String> NEW_ANIMS = List.of("punch_left", "stumble", "nape_guard", "uppercut", "rage", "dropkick",
			"elbow", "ground_pound", "shoulder_charge", "brace", "roundhouse", "axe_kick", "sweep_kick", "crystal_fists", "scream",
			"fastball", "rock_scatter", "boulder_throw", "boulder_volley");

	public String nameKey() {
		return "projecthero.titan_shifter.ability." + id;
	}

	/** Recharge in ticks (the legacy moves read the live config). */
	public int cooldown() {
		var a = TitanShifterConfig.abilities();
		var h = TitanShifterConfig.human();
		return switch (this) {
			case PUNCH -> a.punchCooldown;
			case HEAVY_SMASH -> a.smashCooldown;
			case LEAP -> a.leapCooldown;
			case STOMP -> a.stompCooldown;
			case ROAR -> a.roarCooldown;
			case REGEN -> a.regenCooldown;
			case HARDEN -> a.hardenCooldown;
			case TITAN_ARM -> h.titanArmCooldown;
			case CRYSTAL_BLADE -> h.bladeCooldown;
			case STEAM_BURST -> h.steamBurstCooldown;
			case CRYSTAL_WALL -> h.wallCooldown;
			case CRYSTAL_COCOON -> h.cocoonCooldown;
			// v0.16.7 (user, Titan fixes): the revamp's moves read their sections too
			case KICK -> TitanShifterConfig.sharedMoves().kickCooldown;
			case NAPE_GUARD -> TitanShifterConfig.sharedMoves().napeGuardCooldown;
			case UPPERCUT -> TitanShifterConfig.attackTitan().uppercutCooldown;
			case RAGE -> TitanShifterConfig.attackTitan().rageCooldown;
			case DROPKICK -> TitanShifterConfig.attackTitan().dropkickCooldown;
			case ELBOW -> TitanShifterConfig.armoredTitan().elbowCooldown;
			case GROUND_POUND -> TitanShifterConfig.armoredTitan().groundPoundCooldown;
			case SHOULDER_CHARGE -> TitanShifterConfig.armoredTitan().shoulderChargeCooldown;
			case BRACE -> TitanShifterConfig.armoredTitan().braceCooldown;
			case ROUNDHOUSE -> TitanShifterConfig.femaleTitan().roundhouseCooldown;
			case AXE_KICK -> TitanShifterConfig.femaleTitan().axeKickCooldown;
			case SWEEP_KICK -> TitanShifterConfig.femaleTitan().sweepKickCooldown;
			case CRYSTAL_FISTS -> TitanShifterConfig.femaleTitan().crystalFistsCooldown;
			case SCREAM -> TitanShifterConfig.femaleTitan().screamCooldown;
			case FASTBALL -> TitanShifterConfig.beastTitan().fastballCooldown;
			case ROCK_SCATTER -> TitanShifterConfig.beastTitan().rockScatterCooldown;
			case BOULDER_THROW -> TitanShifterConfig.beastTitan().boulderThrowCooldown;
			case BOULDER_VOLLEY -> TitanShifterConfig.beastTitan().boulderVolleyCooldown;
			default -> cooldown;
		};
	}

	/** The built-in default recharge (what the config ships with); -1 for the moves that always read the config. */
	public int defaultCooldown() {
		return cooldown;
	}

	/** The built-in default damage; -1 for the moves that always read the config. */
	public double defaultDamage() {
		return damage;
	}

	/** The headline damage (before the type's damage scale, Rage and the target rules). */
	public double damage() {
		var d = TitanShifterConfig.damage();
		var h = TitanShifterConfig.human();
		return switch (this) {
			case PUNCH -> d.punch;
			case KICK -> d.kick;
			case HEAVY_SMASH -> d.heavySmash;
			case LEAP -> d.leapLanding;
			case STOMP -> d.stomp;
			case TITAN_ARM -> h.titanArmDamage;
			case CRYSTAL_BLADE -> h.bladeDamage;
			case STEAM_BURST -> h.steamBurstDamage;
			case UPPERCUT -> TitanShifterConfig.attackTitan().uppercutDamage;
			case DROPKICK -> TitanShifterConfig.attackTitan().dropkickDamage;
			case ELBOW -> TitanShifterConfig.armoredTitan().elbowDamage;
			case GROUND_POUND -> TitanShifterConfig.armoredTitan().groundPoundDamage;
			case SHOULDER_CHARGE -> TitanShifterConfig.armoredTitan().shoulderChargeDamage;
			case ROUNDHOUSE -> TitanShifterConfig.femaleTitan().roundhouseDamage;
			case AXE_KICK -> TitanShifterConfig.femaleTitan().axeKickDamage;
			case SWEEP_KICK -> TitanShifterConfig.femaleTitan().sweepKickDamage;
			case FASTBALL -> TitanShifterConfig.beastTitan().fastballDamage;
			case ROCK_SCATTER -> TitanShifterConfig.beastTitan().rockScatterDamage;
			case BOULDER_THROW -> TitanShifterConfig.beastTitan().boulderThrowDamage;
			case BOULDER_VOLLEY -> TitanShifterConfig.beastTitan().boulderVolleyDamage;
			default -> damage;
		};
	}

	/**
	 * The move on ability key {@code slot} (1-6 = R G X Z V C), tapped or with Shift, for a Titan of {@code type}; or the
	 * human kit when {@code type} is null. Null when the key has no move.
	 */
	public static TitanMove of(TitanType type, int slot, boolean shift) {
		if (type == null) {
			return switch (slot) {
				case 1 -> shift ? CRYSTAL_BLADE : TITAN_ARM;
				case 2 -> shift ? BITE_SHIFT : STEAM_BURST;
				case 3 -> shift ? CRYSTAL_COCOON : CRYSTAL_WALL;
				default -> null;
			};
		}
		return switch (slot) {
			case 1 -> shift ? switch (type) {
				case ARMORED_TITAN -> ELBOW;
				case FEMALE_TITAN -> ROUNDHOUSE;
				case BEAST_TITAN -> FASTBALL;
				default -> KICK;
			} : PUNCH;
			case 2 -> switch (type) {
				case ARMORED_TITAN -> shift ? GROUND_POUND : HEAVY_SMASH;
				case FEMALE_TITAN -> shift ? SWEEP_KICK : AXE_KICK;
				case BEAST_TITAN -> shift ? ROCK_SCATTER : HEAVY_SMASH;
				default -> shift ? UPPERCUT : HEAVY_SMASH;
			};
			case 3 -> shift ? signature(type) : LEAP;
			case 4 -> shift ? switch (type) {
				case ARMORED_TITAN -> BRACE;
				case FEMALE_TITAN -> SCREAM;
				case BEAST_TITAN -> BOULDER_VOLLEY;
				default -> DROPKICK;
			} : STOMP;
			case 5 -> shift ? NAPE_GUARD : ROAR;
			case 6 -> shift ? HARDEN : REGEN;
			default -> null;
		};
	}

	/** Shift+X: each Titan's signature. */
	public static TitanMove signature(TitanType type) {
		return switch (type) {
			case ARMORED_TITAN -> SHOULDER_CHARGE;
			case FEMALE_TITAN -> CRYSTAL_FISTS;
			case BEAST_TITAN -> BOULDER_THROW;
			default -> RAGE;
		};
	}

	/** The R combo's name for this type (Punch / Jab / Swipe all share the PUNCH cooldown id). */
	public static String comboNameKey(TitanType type) {
		return switch (type) {
			case FEMALE_TITAN -> "projecthero.titan_shifter.ability.jab";
			case BEAST_TITAN -> "projecthero.titan_shifter.ability.swipe";
			default -> PUNCH.nameKey();
		};
	}

	/** The move's display name for {@code type} (the R combo is named per type). */
	public String nameKey(TitanType type) {
		return this == PUNCH && type != null ? comboNameKey(type) : nameKey();
	}

	public static TitanMove byId(String id) {
		for (TitanMove m : values()) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		return null;
	}
}
