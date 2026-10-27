package com.projecthero.mod.quirk.erasure;

/**
 * v0.16.7 revamp (docs/QUIRK_REVAMP_V0167.md), v0.17.1 Quirks revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.5): every number of
 * the Erasure Quirk (Shota Aizawa / Eraser Head), the ability ids, the synced kit vals and the pose ids (100..119). Ticks
 * unless noted. The user's slot rule: X = movement, V = utility / mode, R G Z C attacks; hold C at a full Plus Ultra gauge =
 * the ultimate (Underground Takedown).
 * <pre>
 *   R  Capture Bind          Shift+R  Capture Pull      hold R  Reel Lock
 *   G  Close Combat          Shift+G  Counter
 *   X  Scarf Pull            Shift+X  Evade Flip        hold X  Scarf Swing   (movement)
 *   Z  Bind and Spin         Shift+Z  Disarm Throw
 *   C  Scarf Whip            Shift+C  Multi-Bind        hold C  Underground Takedown (Plus Ultra)
 *   V  Erasure Stare (hold)  Shift+V  Goggles (toggle)                        (utility)
 * </pre>
 * Eye Drops left the keys (v0.17.1): the item is right-clicked. Erased creatures are Exposed ({@link ErasureExposed}).
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
	/** v0.17.1: hold R -- Reel Lock. */
	public static final String REEL = "quirk_erasure_reel";
	/** v0.17.1: Shift+X -- Evade Flip. */
	public static final String FLIP = "quirk_erasure_flip";
	/** v0.17.1: Shift+C -- Multi-Bind. */
	public static final String MULTI = "quirk_erasure_multi_bind";

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
	/** v0.17.1: Reel Lock -- 1 while R is held on a bound creature (it hangs strung up), and when it began. */
	public static final String V_REEL = "er_reel";
	public static final String V_REEL_AT = "er_reel_at";
	/** v0.17.1: Multi-Bind's creatures (entity id + 1, up to three: er_multi0..2) and till when. */
	public static final String V_MULTI_PREFIX = "er_multi";
	public static final String V_MULTI_UNTIL = "er_multi_until";
	/** v0.17.1: the Evade Flip's i-frames run until. */
	public static final String V_FLIP_UNTIL = "er_flip_until";
	/** v0.17.1: Underground Takedown -- the chain's creatures (id + 1, up to six: er_td0..5), the step reached, when it began. */
	public static final String V_TD_PREFIX = "er_td";
	public static final String V_TD_STEP = "er_td_step";
	public static final String V_TD_AT = "er_td_at";
	/** v0.17.1: the strung-up finish of the takedown lasts until. */
	public static final String V_TD_HANG_UNTIL = "er_td_hang";

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
	/** v0.17.1 */
	public static final int ANIM_REEL = 114;
	public static final int ANIM_FLIP = 115;
	public static final int ANIM_MULTI = 116;
	public static final int ANIM_TAKEDOWN = 117;
	public static final int ANIM_TD_KICK = 118;
	public static final int ANIM_STARE = 119;

	// ---------------------------------------------------------------- Dry Eyes (0 = fresh, 100 = dry)
	/** The stare dries his eyes this much a tick, raw (x {@link ErasureMeter#BUILD}: about a minute of staring to Burning)... */
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
	/** v0.17.1 Strained band (Dry Eyes 50-89): the stare reaches this far. */
	public static final double ERASE_RANGE_STRAINED = 32.0;
	/** v0.17.1 Burning band (90-99): the stare holds this many targets, and burns him this much a second. */
	public static final int BURNING_TARGETS = 2;
	public static final float BURNING_HP_PER_SECOND = 0.5f;
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
	/** v0.17.1: buffed from 3 (and it sets up a free follow-up). */
	public static final float LASH_DAMAGE = 6f;
	/** Speed the pulled creature is yanked in at, blocks a tick. */
	public static final double LASH_PULL = 1.25;
	public static final int LASH_COOLDOWN = 50;
	public static final int MISS_COOLDOWN = 12;
	/** v0.17.1 Pull to safety: Capture Pull on an ally yanks them out of lava, water or a fall (no harm, Slow Falling). */
	public static final int ALLY_PULL_SLOW_FALL = 40;

	// ---------------------------------------------------------------- hold R: Reel Lock (v0.17.1)
	/** R held this long (ticks) with a bound creature: it is strung up. */
	public static final int HOLD_TICKS = 6;
	/** Strung up this far above where it stood. */
	public static final double REEL_LIFT = 1.2;
	/** 2 a second, as two 1-damage tugs. */
	public static final float REEL_DAMAGE = 1f;
	public static final int REEL_DAMAGE_EVERY = 10;
	/** Dry Eyes a tick while he holds it (raw: 5 a second), paced like the stare. */
	public static final float REEL_DRY = 0.25f;
	/** At most this long in one hold (the bind is kept alive while he reels). */
	public static final int REEL_MAX_TICKS = 120;
	/** The heavy-use script's assumption: a typical reel lasts this many seconds. */
	public static final float REEL_TYPICAL_SECONDS = 3f;
	public static final int REEL_COOLDOWN = 40;
	/** Scarf whip shown out and back. */
	public static final int LASH_SHOW_TICKS = 9;

	// ---------------------------------------------------------------- G: Close Combat / Shift+G: Counter
	public static final double COMBO_RANGE = 3.6;
	/** The four strikes: when (ticks after the press) and how hard. */
	public static final int[] COMBO_AT = { 2, 6, 10, 16 };
	/** v0.17.1: buffed from 3 / 3 / 4 / 7. */
	public static final float[] COMBO_DAMAGE = { 5f, 5f, 6f, 10f };
	public static final int COMBO_TICKS = 22;
	public static final int COMBO_COOLDOWN = 50;
	/** The parry window. */
	public static final int COUNTER_WINDOW = 12;
	public static final int COUNTER_COOLDOWN = 80;
	/** v0.17.1: from 8. */
	public static final float COUNTER_DAMAGE = 10f;
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
	/** v0.17.1: 6 on arrival (from 4). */
	public static final float ZIP_KICK_DAMAGE = 6f;
	public static final int ZIP_COOLDOWN = 50;
	/** v0.17.1: hold X this long and the tap (Scarf Pull) becomes the Scarf Swing. */
	public static final int SWING_HOLD_TICKS = 5;
	/** v0.17.1 Shift+X Evade Flip: a backflip about five blocks back, with this long of i-frames. */
	public static final double FLIP_BACK = 1.05;
	public static final double FLIP_UP = 0.62;
	public static final int FLIP_IFRAMES = 6;
	public static final int FLIP_TICKS = 14;
	public static final int FLIP_COOLDOWN = 60;

	// ---------------------------------------------------------------- Z: Bind and Spin / Shift+Z: Disarm Throw
	public static final double SPIN_RANGE = 14.0;
	/** Two full turns round him over this long, at this radius... */
	public static final int SPIN_TICKS = 30;
	public static final double SPIN_RADIUS = 3.0;
	/** ...hitting what it swings into (once each)... */
	/** v0.17.1: 8 a hit (from 6)... */
	public static final float SPIN_HIT_DAMAGE = 8f;
	/** ...then flung the way he looks. */
	/** ...10 on the fling (from 6). */
	public static final float SPIN_THROW_DAMAGE = 10f;
	public static final double SPIN_THROW_SPEED = 1.6;
	public static final int SPIN_COOLDOWN = 200;
	public static final double DISARM_RANGE = 14.0;
	public static final int DISARM_COOLDOWN = 160;

	// ---------------------------------------------------------------- C: Scarf Whip / Shift+C: Goggles
	public static final double WHIP_RANGE = 10.0;
	/** v0.17.1: 7 and a stagger (from 5). */
	public static final float WHIP_DAMAGE = 7f;
	public static final int WHIP_STAGGER_TICKS = 20;
	public static final int WHIP_COOLDOWN = 25;
	/** v0.17.1 Shift+C Multi-Bind: the scarf fans out in a cone and ties up to three for 2.5 s. */
	public static final double MULTI_RANGE = 12.0;
	public static final double MULTI_CONE_DEGREES = 35.0;
	public static final int MULTI_TARGETS = 3;
	public static final int MULTI_TICKS = 50;
	public static final int MULTI_COOLDOWN = 240;
	/** v0.17.1: every scarf hit on an erased creature ties it this long. */
	public static final int ERASED_TIE_TICKS = 20;
	public static final int GOGGLES_COOLDOWN = 10;
	public static final int GOGGLES_NIGHT_VISION = 260;
	/** The goggles outline powered players / special mobs within this (client side). */
	public static final double GOGGLES_RANGE = 32.0;

	// ---------------------------------------------------------------- Plus Ultra: Underground Takedown (v0.17.1)
	/** Everything in this cone (range, half-angle in degrees) is erased for {@link #TD_ERASE_TICKS}; bosses for less. */
	public static final double TD_RANGE = 20.0;
	public static final double TD_CONE_DEGREES = 40.0;
	public static final int TD_ERASE_TICKS = 120;
	public static final int TD_BOSS_ERASE_TICKS = 80;
	/** Then he chains through up to six: zip, kick, bind -- one every {@link #TD_STEP_TICKS}. */
	public static final int TD_TARGETS = 6;
	public static final int TD_STARTUP_TICKS = 10;
	public static final int TD_STEP_TICKS = 7;
	public static final float TD_KICK_DAMAGE = 10f;
	/** It ends with all of them strung up this long (bosses are never bound). */
	public static final int TD_HANG_TICKS = 40;

	// ---------------------------------------------------------------- passives (v0.17.1)
	/** Exposed: an erased creature takes this much more damage from everything. */
	public static final float EXPOSED_DAMAGE_MULT = 1.2f;
	/** Underground: sneaking in the dark (light at or below this), mobs see him at this fraction of their range. */
	public static final int UNDERGROUND_MAX_LIGHT = 7;
	public static final double UNDERGROUND_VISIBILITY = 0.5;

	// ---------------------------------------------------------------- shared
	/** Bosses take at most this fraction of their max health a hit, and are never rooted / thrown. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
	public static final float BOSS_HEALTH_THRESHOLD = 150f;
}
