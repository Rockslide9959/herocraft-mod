package com.projecthero.mod.quirk.erasure;

/**
 * v0.16.7 revamp (docs/QUIRK_REVAMP_V0167.md): every number of the Erasure Quirk (Shota Aizawa / Eraser Head), the
 * ability ids, the synced kit vals and the pose ids (100..119). Ticks unless noted. The user's slot rule: X = movement,
 * V = utility, the rest mainly attacks.
 * <pre>
 *   R  Capture Bind          Shift+R  Capture Pull
 *   G  Close Combat          Shift+G  Counter
 *   X  Scarf Swing (hold)    Shift+X  Scarf Pull                (movement)
 *   Z  Bind and Spin         Shift+Z  Disarm Throw
 *   C  Scarf Whip            Shift+C  Goggles (toggle)
 *   V  Erasure Stare (hold)  Shift+V  Eye Drops (uses the item)  (utility)
 * </pre>
 * The Quirk numbers are static (no config file), like every Quirk's; nothing to migrate. Old saves: the Dry Eyes meter
 * used to count DOWN from 100; {@link #V_INIT} is a new key, so the first tick after the update starts it fresh at 0.
 */
public final class ErasureConfig {
	private ErasureConfig() {
	}

	// ---------------------------------------------------------------- ability ids
	public static final String BIND = "quirk_erasure_bind";
	/** Capture Pull (the id predates the revamp's name). */
	public static final String LASH = "quirk_erasure_lash";
	public static final String COMBO = "quirk_erasure_combo";
	public static final String COUNTER = "quirk_erasure_counter";
	public static final String SWING = "quirk_erasure_swing";
	/** Scarf Pull (the id predates the revamp's name). */
	public static final String ZIP = "quirk_erasure_zip";
	public static final String SPIN = "quirk_erasure_spin";
	public static final String DISARM = "quirk_erasure_disarm";
	public static final String WHIP = "quirk_erasure_whip";
	public static final String GOGGLES = "quirk_erasure_goggles";
	/** Erasure Stare (the id predates the revamp's name). */
	public static final String ERASE = "quirk_erasure_erase";
	public static final String DROPS = "quirk_erasure_eye_drops";

	// ---------------------------------------------------------------- synced kit vals / vecs (QuirkState)
	/** 1 while the stare is held. */
	public static final String V_ERASE = "er_erase";
	/** When the current stare began (hair rises from here). */
	public static final String V_ERASE_AT = "er_erase_at";
	/** The creature the stare holds (entity id + 1; the goggles lock onto it). */
	public static final String V_STARE_TARGET = "er_stare_target";
	/** 1 while his eyes are forced shut (Dry Eyes hit 100). */
	public static final String V_SHUT = "er_shut";
	public static final String V_SHUT_AT = "er_shut_at";
	/** 1 = swinging on the scarf, 2 = reeling to a creature, 3 = reeling to a block. */
	public static final String V_LINE = "er_line";
	public static final String V_LINE_AT = "er_line_at";
	/** The swing's rope length x1000. */
	public static final String V_ROPE = "er_rope";
	/** The zip's creature (entity id + 1). */
	public static final String V_ZIP_TARGET = "er_zip";
	public static final String P_HOOK = "er_hook";
	/** The whip / pull / disarm / bind throw: when it went out, and where the tip went. */
	public static final String V_LASH_AT = "er_lash_at";
	public static final String P_LASH = "er_lash";
	/** The bound creature (entity id + 1) and till when. */
	public static final String V_BIND = "er_bind";
	public static final String V_BIND_UNTIL = "er_bind_until";
	/** Bind and Spin's creature (id + 1) and when the spin started. */
	public static final String V_SPIN = "er_spin";
	public static final String V_SPIN_AT = "er_spin_at";
	/** Close Combat started at. */
	public static final String V_COMBO_AT = "er_combo_at";
	/** Counter: the parry window is open until. */
	public static final String V_COUNTER = "er_counter";
	/** 1 while the goggles are down over the eyes. */
	public static final String V_GOGGLES = "er_goggles";
	/** v0.16.7 revamp: 1 once Dry Eyes has been set fresh (0) for this life (a new key: old saves restart fresh). */
	public static final String V_INIT = "er_init2";

	// ---------------------------------------------------------------- poses (Quirk.ERASURE.animBase = 100)
	public static final int ANIM_PULL = 101;
	public static final int ANIM_DISARM = 102;
	public static final int ANIM_SWING = 103;
	public static final int ANIM_ZIP = 104;
	public static final int ANIM_BIND = 105;
	public static final int ANIM_WHIP = 106;
	public static final int ANIM_SPIN = 107;
	public static final int ANIM_COMBO = 108;
	public static final int ANIM_GOGGLES = 109;
	public static final int ANIM_DROPS = 110;
	public static final int ANIM_BLINK = 111;
	public static final int ANIM_COUNTER = 112;
	public static final int ANIM_COUNTER_HIT = 113;

	// ---------------------------------------------------------------- Dry Eyes (0 = fresh, 100 = dry)
	/** The stare dries his eyes this much a tick (~10 s from fresh)... */
	public static final float STARE_DRY = 0.5f;
	/** ...this much less with the goggles down. */
	public static final float GOGGLES_DRY_MULT = 0.75f;
	/** Not staring, they recover this a tick (~8 s from dry). */
	public static final float RECOVER = 0.6f;
	/** Shut, they recover this a tick... */
	public static final float SHUT_RECOVER = 1.2f;
	/** ...and open again once at or below this, and at least {@link #SHUT_MIN_TICKS} after they shut. */
	public static final float SHUT_OPEN_AT = 30f;
	public static final int SHUT_MIN_TICKS = 60;

	// ---------------------------------------------------------------- V: Erasure Stare (hold) / Shift+V: Eye Drops
	public static final double ERASE_RANGE = 24.0;
	/** Half-angle of the stare cone, degrees (the creature you look at). */
	public static final double ERASE_CONE_DEGREES = 7.0;
	/** Goggles down: once locked on, the stare keeps hold of its target anywhere in this half-angle (it can't dodge). */
	public static final double GOGGLES_LOCK_DEGREES = 45.0;
	/** Each tick of the stare suppresses for this long (the effect fades a moment after the stare breaks). */
	public static final int SUPPRESS_TICKS = 6;
	/** After a stare ends (the blink). */
	public static final int ERASE_COOLDOWN = 10;
	public static final int DROPS_COOLDOWN = 200;

	// ---------------------------------------------------------------- R: Capture Bind / Shift+R: Capture Pull
	public static final double BIND_RANGE = 16.0;
	public static final int BIND_TICKS = 80;
	public static final int BIND_COOLDOWN = 160;
	public static final double LASH_RANGE = 16.0;
	public static final float LASH_DAMAGE = 3f;
	/** Speed the pulled creature is yanked in at, blocks a tick. */
	public static final double LASH_PULL = 1.25;
	public static final int LASH_COOLDOWN = 50;
	public static final int MISS_COOLDOWN = 12;
	/** Scarf whip shown out and back. */
	public static final int LASH_SHOW_TICKS = 9;

	// ---------------------------------------------------------------- G: Close Combat / Shift+G: Counter
	public static final double COMBO_RANGE = 3.6;
	/** The four strikes: when (ticks after the press) and how hard. */
	public static final int[] COMBO_AT = { 2, 6, 10, 16 };
	public static final float[] COMBO_DAMAGE = { 3f, 3f, 4f, 7f };
	public static final int COMBO_TICKS = 22;
	public static final int COMBO_COOLDOWN = 50;
	/** The parry window. */
	public static final int COUNTER_WINDOW = 12;
	public static final int COUNTER_COOLDOWN = 80;
	public static final float COUNTER_DAMAGE = 8f;
	/** The riposte reaches attackers this close (a shooter further off is only blocked). */
	public static final double COUNTER_REACH = 5.0;
	public static final int COUNTER_BIND_TICKS = 40;

	// ---------------------------------------------------------------- X: Scarf Swing / Shift+X: Scarf Pull
	public static final double SWING_RANGE = 30.0;
	public static final int SWING_COOLDOWN = 10;
	public static final double SWING_SLACK_BREAK = 6.0;
	public static final double ZIP_RANGE = 26.0;
	public static final double ZIP_SPEED = 1.35;
	public static final double ZIP_ARRIVE = 2.2;
	public static final int ZIP_MAX_TICKS = 34;
	public static final float ZIP_KICK_DAMAGE = 4f;
	public static final int ZIP_COOLDOWN = 50;

	// ---------------------------------------------------------------- Z: Bind and Spin / Shift+Z: Disarm Throw
	public static final double SPIN_RANGE = 14.0;
	/** Two full turns round him over this long, at this radius... */
	public static final int SPIN_TICKS = 30;
	public static final double SPIN_RADIUS = 3.0;
	/** ...hitting what it swings into (once each)... */
	public static final float SPIN_HIT_DAMAGE = 6f;
	/** ...then flung the way he looks. */
	public static final float SPIN_THROW_DAMAGE = 6f;
	public static final double SPIN_THROW_SPEED = 1.6;
	public static final int SPIN_COOLDOWN = 200;
	public static final double DISARM_RANGE = 14.0;
	public static final int DISARM_COOLDOWN = 160;

	// ---------------------------------------------------------------- C: Scarf Whip / Shift+C: Goggles
	public static final double WHIP_RANGE = 10.0;
	public static final float WHIP_DAMAGE = 5f;
	public static final int WHIP_COOLDOWN = 25;
	public static final int GOGGLES_COOLDOWN = 10;
	public static final int GOGGLES_NIGHT_VISION = 260;
	/** The goggles outline powered players / special mobs within this (client side). */
	public static final double GOGGLES_RANGE = 32.0;

	// ---------------------------------------------------------------- shared
	/** Bosses take at most this fraction of their max health a hit, and are never rooted / thrown. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
	public static final float BOSS_HEALTH_THRESHOLD = 150f;
}
