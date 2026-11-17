package com.projecthero.mod.omnitrix;

/**
 * v0.17.1: every Omnitrix number in one place (the alien kits keep their own move numbers in their kit classes).
 */
public final class OmnitrixConfig {
	/** A transformation lasts three minutes. */
	public static final int TRANSFORM_TICKS = 3 * 60 * 20;
	/** The watch light flashes red for the last 15 s. */
	public static final int WARN_TICKS = 15 * 20;
	/** v0.17.1 (user): the dial can turn at most once every 0.2 s. */
	public static final int DIAL_COOLDOWN_TICKS = 4;
	/** Then a 60 s recharge before the next transformation (v0.17.2, user: "For prototype omnitrix the recharge time should be 60 seconds instead of 90"). */
	public static final int RECHARGE_TICKS = 60 * 20;
	/**
	 * v0.17.2 (user: "The recalibrated omnitrix should have a battery system instead of timer like the prototype, player should be able to stay an alien for about 4 minutes in total this time and detransform at will without the omnitrix going into recharge mode, full recharge of the battery should be 90 seconds"): the Recalibrated Omnitrix runs on a battery instead -- 4 minutes of alien time in all, drained only while
	 * transformed, refilled while human from empty to full in 90 s. Turning back at will costs nothing; running it flat times
	 * the watch out until the battery is full again.
	 */
	public static final int BATTERY_TICKS = 4 * 60 * 20;
	/** Empty to full. */
	public static final int BATTERY_RECHARGE_TICKS = 90 * 20;
	/** The least charge a transformation needs. */
	public static final int BATTERY_MIN_TICKS = 5 * 20;
	/** v0.17.2 (user: "switching alien forms should drain like 30 seconds from the timer"): alien to alien on the wheel. */
	public static final int SWITCH_COST_TICKS = 30 * 20;
	/** An early Shift+V revert still costs a (shorter) recharge: 10 s, so swapping can not be spammed. */
	public static final int EARLY_RECHARGE_TICKS = 10 * 20;
	/** A kit key held this long becomes a hold move (only for keys the kit declares holdable). */
	public static final int KIT_HOLD_TICKS = 8;
	/** How long the transform pose / DNA-helix flash plays. */
	public static final int TRANSFORM_ANIM_TICKS = 16;

	// ---------------------------------------------------------------- the Containment Pod
	/** The extraction lasts 40 s. */
	public static final int EXTRACTION_TICKS = 40 * 20;
	/** Fuel one extraction burns (a Redstone Block is 1, an Echo Shard 2). */
	public static final int FUEL_PER_EXTRACTION = 8;
	public static final int FUEL_REDSTONE_BLOCK = 1;
	public static final int FUEL_ECHO_SHARD = 2;
	public static final int MAX_FUEL = 32;
	/** Stepping out of a running pod: the extraction fails and the field lashes out for this much (it bypasses armour too). */
	public static final float STEP_OUT_DAMAGE = 6.0f;
	/** How far (horizontally, from the pod's centre) the wearer may sway before they count as having stepped out. */
	public static final double POD_RADIUS = 0.8;

	// ---------------------------------------------------------------- v0.17.2: the recalibration (the Recalibration Chip, OmnitrixRecal)
	/** Transformations with the prototype before the watch can take a recalibration (it has to have learnt its wearer). */
	public static final int RECAL_MIN_TRANSFORMS = 25;
	/** v0.17.2 (user: "The omnitrix then enters recalibration mode for 3 minutes"): recalibration mode, after the chip goes in. */
	public static final int RECAL_MODE_TICKS = 3 * 60 * 20;

	/**
	 * The extraction's damage pulses: {tick (since the start), damage}. Every pulse is {@code projecthero:omnitrix_extraction}
	 * damage: it ignores armour, enchantments and Resistance and cannot be survived with a totem (it bypasses invulnerability),
	 * but Absorption soaks it and healing still works.
	 *
	 * <p><b>Calibration (user):</b> "removing the omnitrix should deal enough damage to bring a player that just ate an
	 * enchanted golden apple to 2 hearts". A 20/20 player who eats one (Absorption IV = 16, Regeneration II 20 s, Resistance I,
	 * Fire Resistance) right before starting, with no other healing:
	 * <ul>
	 *   <li>Regeneration II heals 1 HP whenever its remaining duration is a multiple of 25 ticks (vanilla {@code 50 >> 1}):
	 *       at 400, 375, ..., 25 -- 16 heals. The one at 400 lands the tick after eating, at full health, and is wasted; the
	 *       other 15 all land below full health because the first pulse (tick 20) strips the shield and 8 HP first.</li>
	 *   <li>Pool: 20 HP + 16 Absorption + 15 healed = 51. The schedule totals 47, so the apple eater ends at <b>4 HP</b>.</li>
	 *   <li>Health peaks at 18.5 (tick ~375), never full, so no heal is wasted after the first pulse.</li>
	 *   <li>Without the apple the first pulse (24) is already lethal.</li>
	 * </ul>
	 * Starting up to 5 ticks after eating gives exactly 4 HP; each further 25 ticks of dawdling loses one heal (3 HP after
	 * 1.25-2.5 s). Natural food healing at a full hunger bar comes on top.
	 */
	public static final float[][] EXTRACTION_PULSES = buildPulses();

	private static float[][] buildPulses() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		out.add(new float[] { 20, 24.0f });                 // the shield-stripper: 16 Absorption + 8 HP
		for (int t = 40; t <= 600; t += 20) {
			out.add(new float[] { t, 0.5f });               // 29 steady pulses while Regeneration fights back
		}
		for (int t = 620; t <= 780; t += 20) {
			out.add(new float[] { t, 0.6f });               // 9 harder pulses as the Omnitrix's grip breaks
		}
		out.add(new float[] { 800, 3.1f });                 // the final wrench as it comes off
		return out.toArray(new float[0][]);
	}

	/** The schedule's total (47). */
	public static float extractionTotal() {
		float sum = 0f;
		for (float[] p : EXTRACTION_PULSES) {
			sum += p[1];
		}
		return sum;
	}

	// ---------------------------------------------------------------- getting it
	/** The natural Omnitrix meteor: chance per surface player per minute (Overworld, never Peaceful). About once in 50 hours. */
	public static final double NATURAL_METEOR_CHANCE_PER_MINUTE = 1.0 / 3000.0;
	/** After any Omnitrix meteor lands, no natural one for this long. */
	public static final int NATURAL_METEOR_COOLDOWN_MINUTES = 120;
	/** The Meteor Scanner drops the meteor within this many blocks of the user. */
	public static final int SCANNER_RADIUS = 50;
	/** ...and refuses while an Omnitrix pod or meteor is within this many blocks. */
	public static final int SCANNER_BLOCK_RADIUS = 160;
	/** v0.17.1: the fallen pod smokes (too hot to glow) this long after landing; it still opens only when right-clicked. */
	public static final int POD_OPEN_DELAY = 60;
	/** v0.17.1 (user: "only open when the player right-clicks it"): the lid's hinge swing, ticks after the right-click. */
	public static final int POD_LID_TICKS = 30;
	/** v0.17.1: once the lid is up, a player within this many blocks of the pod's centre makes the watch jump at them. */
	public static final double POD_LATCH_RANGE = 3.0;
	/** v0.17.1: the watch's leap from the pod onto the wrist (its cuff springs open first), ticks. */
	public static final int POD_JUMP_TICKS = 12;
	/** v0.17.1: the shake-off after the watch latches on (grab, yank, shake, give up), ticks; movement is slowed meanwhile. */
	public static final int LATCH_TICKS = 50;
	/** v0.17.1: how much of the wearer's speed the shake-off takes away (-0.85 = 15% left, no jumping). */
	public static final double LATCH_SLOW = -0.85;
	/** v0.17.1 (user: "a much larger crater"): the crater's bowl radius and depth at its centre, blocks. */
	public static final int CRATER_RADIUS = 9;
	public static final int CRATER_DEPTH = 5;
	/** v0.17.1: columns of the crater carved per tick (centre out), so the blast never lands as one big spike. */
	public static final int CRATER_COLUMNS_PER_TICK = 48;
	/** v0.17.1 (user: "falls from much higher up"): the meteor starts this far above (and 0.45x this far beside) its impact. */
	public static final int METEOR_FALL_HEIGHT = 220;
	/** ...and takes this long to come down. */
	public static final int METEOR_FALL_TICKS = 110;

	private OmnitrixConfig() {
	}

	/** v0.17.2 the failsafe: how long it stays offline after it throws an alien back to human (20 s; then armed again). */
	public static final int FAILSAFE_OFFLINE_TICKS = 20 * 20;
	/** v0.17.2: the share of the alien's health a failsafe transformation starts on. */
	public static final float FAILSAFE_ALIEN_HEALTH = 0.5f;
	/** The health the failsafe leaves you on (2 hearts) and the Resistance V it wraps you in. */
	public static final float FAILSAFE_HEALTH = 4f;
	public static final int FAILSAFE_SHIELD_TICKS = 60;
	/** v0.17.1 the wrong-alien glitch: the chance a slap lands on another alien (never under Master Control). */
	public static final float GLITCH_CHANCE = 0.5f;
	/** v0.17.2 (user): the 11th mistransform finds the next hidden alien (Cannonbolt), the 22nd the one after, and so on. */
	public static final int UNLOCK_EVERY_MISTRANSFORMS = 11;
	/**
	 * v0.17.1 Master Control: one dial step every MASTER_STEP_TICKS of its sequence. v0.17.5 (user: "it should be a random 6 number
	 * combination"): the sequence plays when the wearer enters his Master Control code in the watch's function mode
	 * ({@link OmnitrixFunction}) -- on, and again to turn it off. The v0.17.4 ritual and the Shift+N off-hold are gone.
	 */
	public static final int MASTER_STEP_TICKS = 4;
	/**
	 * v0.17.5 round 2 (user: "the omnitrix first flashes white and black with the pieces of the omnitrix alternating in these colours
	 * at the same time, make this happen for 2 seconds and then the dial rotates between every alien"): the code's sequence opens
	 * with this long of the white / black flash before the dial run starts.
	 */
	public static final int MASTER_FLASH_TICKS = 40;
	/** The flash swaps its white and black pieces every this many ticks (~7 swaps a second). */
	public static final int MASTER_FLASH_SWAP_TICKS = 3;

	// ---------------------------------------------------------------- v0.17.5: function mode and the codes (OmnitrixFunction / OmnitrixCodes)
	/** Digits in a code. */
	public static final int CODE_LENGTH = 6;
	/**
	 * The self-destruct code, the same on every watch (an easter egg: Ben TEN, said three times). Never told to players; the
	 * Guidebook's Master Control note hints at it. It starts a harmless countdown ({@link #SELF_DESTRUCT_TICKS}).
	 */
	public static final String SELF_DESTRUCT_CODE = "101010";
	/** A wrong code locks function mode this long (10 s). */
	public static final int CODE_LOCK_TICKS = 10 * 20;
	/** The shortest time between two submitted codes (anti-spam, on top of the lock). */
	public static final int CODE_SUBMIT_GAP_TICKS = 5;
	/** Entering / leaving function mode: the core sinking (or rising) and the face going back to normal. */
	public static final int FUNCTION_FOLD_TICKS = 6;
	/** The self-destruct countdown (15 s) and the overheated watch's lock-out after the fizzle (30 s, the red face). */
	public static final int SELF_DESTRUCT_TICKS = 15 * 20, SELF_DESTRUCT_FIZZLE_TICKS = 30 * 20;
	/**
	 * The watch's flicker that reveals one unknown Master Control digit (and its place): the chance on each transformation, and
	 * on each full minute spent transformed. At ~12 transformations and ~30 transformed minutes an hour that is ~1.35 flickers an
	 * hour, so all six take ~4-5 hours of ordinary play on the watch alone (Data Fragments speed it up). Never two within
	 * {@link #REVEAL_COOLDOWN_TICKS} (20 min of game time) of each other.
	 */
	public static final float REVEAL_TRANSFORM_CHANCE = 0.05f, REVEAL_MINUTE_CHANCE = 0.025f;
	public static final int REVEAL_COOLDOWN_TICKS = 20 * 60 * 20;
	/** Once all six digits are known, the share of flickers that reveal a whole hidden-alien code instead (the rest show nothing new). */
	public static final float REVEAL_ALIEN_CODE_CHANCE = 0.2f;
	/** An Omnitrix Data Fragment: the chance it holds a whole hidden-alien code rather than a Master Control digit. */
	public static final float FRAGMENT_ALIEN_CODE_CHANCE = 0.15f;
	/** The chance a Hero Vault roll holds an Omnitrix Data Fragment, by vault tier 1-4. */
	public static final float[] FRAGMENT_VAULT_CHANCE = { 0.04f, 0.08f, 0.14f, 0.22f };
}
