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
	/** Then a 90 s recharge before the next transformation. */
	public static final int RECHARGE_TICKS = 90 * 20;
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
	/** The fallen pod opens this long after landing (then the first player to touch it gets the watch). */
	public static final int POD_OPEN_DELAY = 60;

	private OmnitrixConfig() {
	}
}
