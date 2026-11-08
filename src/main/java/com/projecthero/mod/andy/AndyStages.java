package com.projecthero.mod.andy;

import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.world.entity.EntityDimensions;

/**
 * v0.17.3 (user: the rolling head and the staged regrowth): how Andy gets about with what he has left. Worked out on both
 * sides purely from which parts are there, gone or still growing ({@link AndyState} part times, already synced) -- no
 * packets -- so it is the same whatever took the parts (a shatter, a boost, a launch, a hit):
 * <pre>
 *   ROLL      just the head (a body may be growing): a rolling head      0.5 x 0.5 box, eyes at 0.25   speed x0.32
 *   DRAG      no legs, an arm there or growing (or only a body):           0.6 x 0.6 box, eyes at 0.40   speed x0.38
 *             lying flat, pulling himself along with the arm
 *   ARM_WALK  no legs, both arms whole: walking on his hands, the body     0.6 x 1.3 box, eyes at 1.05   speed x0.50
 *             hanging between them
 *   NUB_HOP   no whole leg, one growing (and a body): hopping on the nub   0.6 x 1.5 box, eyes at 1.30   speed x0.58
 *   LEG_HOP   one whole leg, the other gone: hopping on it                 standing box                 speed x0.68
 *   LIMP      one whole leg, the other growing: limping                    standing box                 speed x0.82
 *   NORMAL    both legs                                                    standing box                 speed x1
 * </pre>
 * The box only grows when there is room for the bigger one ({@code AndyStagePoseMixin}).
 */
public final class AndyStages {
	public static final int ROLL = 0;
	public static final int DRAG = 1;
	public static final int ARM_WALK = 2;
	public static final int NUB_HOP = 3;
	public static final int LEG_HOP = 4;
	public static final int LIMP = 5;
	public static final int NORMAL = 6;

	/** Movement speed by stage (a multiplier on the player's walking speed). */
	public static final double[] SPEED = { 0.32, 0.38, 0.50, 0.58, 0.68, 0.82, 1.0 };
	/** Jump strength by stage. */
	public static final double[] JUMP = { 0.45, 0.0, 0.5, 0.75, 0.85, 0.9, 1.0 };

	private static final EntityDimensions[] DIMS = {
			EntityDimensions.scalable(0.5f, 0.5f).withEyeHeight(0.25f),
			EntityDimensions.scalable(0.6f, 0.6f).withEyeHeight(0.4f),
			EntityDimensions.scalable(0.6f, 1.3f).withEyeHeight(1.05f),
			EntityDimensions.scalable(0.6f, 1.5f).withEyeHeight(1.3f),
	};

	private AndyStages() {
	}

	/** The stage for {@code s} at {@code now}. */
	public static int stage(AndyState s, float now) {
		if (s == null || !s.hasPower || !s.has(AndyState.HEAD, now)) {
			return NORMAL;
		}
		int legs = (s.has(AndyState.RIGHT_LEG, now) ? 1 : 0) + (s.has(AndyState.LEFT_LEG, now) ? 1 : 0);
		boolean legGrowing = growing(s, AndyState.RIGHT_LEG, now) || growing(s, AndyState.LEFT_LEG, now);
		if (legs == 2) {
			return NORMAL;
		}
		if (legs == 1) {
			return legGrowing ? LIMP : LEG_HOP;
		}
		boolean body = s.has(AndyState.BODY, now);
		if (legGrowing && body) {
			return NUB_HOP;
		}
		boolean ra = s.has(AndyState.RIGHT_ARM, now), la = s.has(AndyState.LEFT_ARM, now);
		if (ra && la && (body || s.regrowth(AndyState.BODY, now) > 0.5f)) {
			return ARM_WALK;
		}
		if (ra || la || growing(s, AndyState.RIGHT_ARM, now) || growing(s, AndyState.LEFT_ARM, now) || body) {
			return DRAG;
		}
		return ROLL;
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
