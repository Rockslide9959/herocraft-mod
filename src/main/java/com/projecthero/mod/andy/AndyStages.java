package com.projecthero.mod.andy;

import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.world.entity.EntityDimensions;

/**
 * v0.17.3 (user: the rolling head and the staged regrowth): how Andy gets about with what he has left. Worked out on both
 * sides purely from which parts are there, gone or still growing ({@link AndyState} part times, already synced) -- no
 * packets -- so it is the same whatever took the parts (a shatter, a boost, a launch, a hit). From only a head the regrowth
 * (v0.17.3 NBC order: the torso once the head is there, the arms and legs once the torso has {@link AndyConfig#LIMB_NEEDS_BODY} frames) runs through them in
 * order:
 * <pre>
 *   ROLL      just the head, nothing growing yet: a rolling head          0.5 x 0.5 box, eyes at 0.25   speed x0.32
 *   CRAWL     no legs; the torso growing out of the neck (he lies on it,   0.6 x 0.6 box, eyes at 0.40   speed x0.38
 *             inching along), then the arms growing: a belly crawl, pulling
 *             with both arms in turn as they lengthen (or one arm)
 *   ARM_WALK  no legs, both arms usable (their muscle is in): walking on   0.6 x 1.3 box, eyes at 1.05   speed x0.50
 *             his hands while the torso finishes
 *   SHUFFLE   both legs growing, the bone still pushing out: shuffling     0.6 x 1.5 box, eyes at 1.28   speed x0.45
 *             on the stumps, knees bent, low to the ground, a waddle
 *   WOBBLE    both legs growing, full length (the muscle and skin still    standing box                 speed x0.70
 *             building): a wobbly, unsteady walk, short steps
 *   LIMP      one whole leg, the other gone or growing: hopping / limping  standing box                 speed x0.70
 *   NORMAL    both legs                                                    standing box                 speed x1
 * </pre>
 * The box only grows when there is room for the bigger one ({@code AndyCrawlPoseMixin}).
 */
public final class AndyStages {
	public static final int ROLL = 0;
	public static final int CRAWL = 1;
	public static final int ARM_WALK = 2;
	public static final int SHUFFLE = 3;
	public static final int WOBBLE = 4;
	public static final int LIMP = 5;
	public static final int NORMAL = 6;

	/** An arm can carry him once its muscle has built (the skin is still closing over it). */
	public static final float ARM_USE = AndyConfig.LAYER_MUSCLE[0] + AndyConfig.LAYER_MUSCLE[1];
	/** Growing legs can stand once their bone is full length (the muscle and skin still building: wobbly). */
	public static final float LEG_STAND = AndyConfig.LAYER_BONE[0] + AndyConfig.LAYER_BONE[1];

	/** Movement speed by stage (a multiplier on the player's walking speed). */
	public static final double[] SPEED = { 0.32, 0.38, 0.50, 0.45, 0.70, 0.70, 1.0 };
	/** Jump strength by stage. */
	public static final double[] JUMP = { 0.45, 0.0, 0.5, 0.4, 0.7, 0.85, 1.0 };

	private static final EntityDimensions[] DIMS = {
			EntityDimensions.scalable(0.5f, 0.5f).withEyeHeight(0.25f),
			EntityDimensions.scalable(0.6f, 0.6f).withEyeHeight(0.4f),
			EntityDimensions.scalable(0.6f, 1.3f).withEyeHeight(1.05f),
			EntityDimensions.scalable(0.6f, 1.5f).withEyeHeight(1.28f),
	};

	private AndyStages() {
	}

	/** The stage for {@code s} at {@code now}. */
	public static int stage(AndyState s, float now) {
		if (s == null || !s.hasPower || !s.has(AndyState.HEAD, now)) {
			return NORMAL;
		}
		int legs = (s.has(AndyState.RIGHT_LEG, now) ? 1 : 0) + (s.has(AndyState.LEFT_LEG, now) ? 1 : 0);
		if (legs == 2) {
			return NORMAL;
		}
		if (legs == 1) {
			return LIMP;
		}
		boolean body = s.has(AndyState.BODY, now);
		float legP = Math.max(growth(s, AndyState.RIGHT_LEG, now), growth(s, AndyState.LEFT_LEG, now));
		if (legP > 0f && (body || s.frame(AndyState.BODY, now) >= AndyConfig.LIMB_NEEDS_BODY)) {
			return legP >= LEG_STAND ? WOBBLE : SHUFFLE;
		}
		if (usable(s, AndyState.RIGHT_ARM, now) && usable(s, AndyState.LEFT_ARM, now) && (body || s.regrowth(AndyState.BODY, now) > 0.5f)) {
			return ARM_WALK;
		}
		// the torso growing out of the neck lays him down on it (it grows along the ground behind the head, not into it)
		if (s.has(AndyState.RIGHT_ARM, now) || s.has(AndyState.LEFT_ARM, now) || growing(s, AndyState.RIGHT_ARM, now)
				|| growing(s, AndyState.LEFT_ARM, now) || body || growing(s, AndyState.BODY, now)) {
			return CRAWL;
		}
		return ROLL;
	}

	/** An arm whole, or grown far enough to carry him. */
	public static boolean usable(AndyState s, int arm, float now) {
		return s.has(arm, now) || s.regrowth(arm, now) >= ARM_USE;
	}

	/** How far a growing part is (0 when not growing). */
	private static float growth(AndyState s, int part, float now) {
		return growing(s, part, now) ? s.regrowth(part, now) : 0f;
	}

	/** Regrowing and already showing. */
	public static boolean growing(AndyState s, int part, float now) {
		float p = s.regrowth(part, now);
		return s.broken(part) && p > 0f && p < 1f;
	}

	/** The box for a stage, or null for the vanilla one. */
	public static EntityDimensions dims(int stage) {
		return stage >= 0 && stage < DIMS.length ? DIMS[stage] : null;
	}
}
