package com.projecthero.mod.ironman.gantry;

import com.projecthero.mod.ironman.suit.IronManSuitPoses;

/**
 * v0.15.4 (v0.15.5: staged build): the timetable of a Stark Gantry suit-up -- a pure function of the frame (ticks since
 * the sequence started) and the {@link Plan} (which pieces the suit has), so the server (which moves the real stacks),
 * the client renderer (lift, hatches, arms, the part in a clamp, the texel-by-texel build on the body), the wearer's pose
 * and the gametests all read the same numbers.
 *
 * <p>v0.15.5, explicit user request: the suit goes on in <b>parts</b>, not whole pieces ({@link #STAGES}):
 * <ol>
 *   <li>the top half of the chest (no jacket layer) is carried on, the bottom half builds itself down from it, then the
 *       jacket layer builds on;</li>
 *   <li>the right gauntlet is carried on (no sleeve layer), the rest of the arm builds itself up from it, then the sleeve
 *       layer; the same for the left arm;</li>
 *   <li>the boots are carried on one at a time, right then left (no pant-leg layer; v0.15.6);</li>
 *   <li>the top half of the leggings is carried on, the bottom half builds itself down, then the pant-leg layer builds on
 *       (down the legs and over the boots);</li>
 *   <li>the helmet is carried on without its faceplate, and the faceplate is carried on last.</li>
 * </ol>
 * v0.15.8, explicit user request: the carried parts go on to a fixed beat ({@link #PIECE_GAP} apart, a gauntlet / boot
 * pair {@link #PAIR_GAP} apart, riding the elevator side by side), the arms move faster ({@link #CARRY}), and each
 * build starts the moment its parent part is on and runs alongside the later carries at its own speed -- stages overlap.
 *
 * <p>A <b>carry</b> stage is the robotic arm work of v0.15.4 (elevator up, jaws close, carried round onto the body, clamps
 * lock, let go); a <b>build</b> stage has the part build itself on, texel by texel behind a hot seam (the arms stay back
 * at their ready hover -- they only ever fit parts). Stages of pieces the suit lacks are left out. The lead-in (lift, hatches, arm masts) and the outro are
 * mirror images ({@link #edge}), so taking a suit OFF is exactly this timetable played backwards ({@link #frame}).
 *
 * <p>The real armour stack of a piece moves floor -> slot at the fit of that piece's <b>first</b> carry stage (chest:
 * the chest top; feet: the boots; legs: the leggings top; head: the helmet) -- the later stages of a piece only reveal
 * more of what is already worn (the client hides the not-yet-built texels).
 */
public final class GantryTimeline {
	/** Lift + hatches + arms coming up, before the first stage (and the same, reversed, after the last). */
	public static final int LEAD = 40;
	public static final int OUTRO = 40;
	/**
	 * Ticks of an arm-carried part / a self-building part. (v0.15.8: briefly 20 for faster arms; the user asked for the
	 * original speed back -- to keep the beat, an arm whose next part is due heads off for it the tick after its clamps
	 * lock instead of holding and swinging back to its hover, see {@link Plan#end}.)
	 */
	public static final int CARRY = 30;
	public static final int BUILD = 16;
	/**
	 * v0.15.8, user request: ticks between one carried part going on and the next (1 s). v0.15.9, user request: the second
	 * gauntlet / boot of a pair too (was 0.2 s) -- every carried part is 1 s after the one before.
	 */
	public static final int PIECE_GAP = 20;
	public static final int PAIR_GAP = PIECE_GAP;
	/** v0.15.8: a gauntlet / boot pair rides up the elevator side by side -- each this far off its centre (blocks). */
	public static final double PAIR_OFFSET = 0.2;
	/** How high the centre lift raises the wearer (blocks). */
	public static final float LIFT = 0.5f;

	public static final int LIFT_TO = 14;
	public static final int HATCH_FROM = 3;
	public static final int HATCH_TO = 15;
	public static final int RISE_FROM = 14;
	public static final int RISE_TO = 32;
	public static final int UNFOLD_FROM = 30;
	public static final int UNFOLD_TO = 40;

	/** Fractions of a carry stage. */
	public static final float REACH = 0.22f;
	public static final float ELEVATOR_UP = 0.28f;
	public static final float GRIP_CLOSE = 0.26f;
	public static final float GRIP = 0.36f;
	public static final float SINK_FROM = 0.40f;
	public static final float SINK_TO = 0.70f;
	public static final float FIT = 0.75f;
	public static final float LET_GO = 0.88f;
	/** A build stage's texels appear between these fractions of it (k = 0 .. 1 along the part). */
	public static final float BUILD_FROM = 0.06f;
	public static final float BUILD_TO = 0.90f;
	/** Sneaking only cancels after this many ticks (a shift still held from the screen must not cancel it at once). */
	public static final int CANCEL_GRACE = 10;

	// ---------------- the stages ----------------

	/** Rack slots (the Suit Platform's layout): 0 HEAD, 1 CHEST, 2 LEGS, 3 FEET. */
	public static final int HEAD = 0;
	public static final int CHEST = 1;
	public static final int LEGS = 2;
	public static final int FEET = 3;

	public static final int TORSO_TOP = 0;
	public static final int TORSO_BOTTOM = 1;
	public static final int JACKET = 2;
	public static final int R_GAUNTLET = 3;
	public static final int R_ARM = 4;
	public static final int R_SLEEVE = 5;
	public static final int L_GAUNTLET = 6;
	public static final int L_ARM = 7;
	public static final int L_SLEEVE = 8;
	/** v0.15.6, user request: one boot at a time -- the right, then the left (taken off left, then right). */
	public static final int R_BOOT = 9;
	public static final int L_BOOT = 10;
	public static final int THIGH_TOP = 11;
	public static final int THIGH_BOTTOM = 12;
	public static final int PANTS = 13;
	public static final int HELMET = 14;
	public static final int FACEPLATE = 15;
	public static final int STAGES = 16;

	/** Is stage s an arm carry (true) or a self-build (false)? */
	private static final boolean[] CARRIED = { true, false, false, true, false, false, true, false, false, true, true, true,
			false, false, true, true };
	/** The piece (rack slot) each stage belongs to (the pant layer: the leggings; it also covers the boots). */
	private static final int[] PIECE = { CHEST, CHEST, CHEST, CHEST, CHEST, CHEST, CHEST, CHEST, CHEST, FEET, FEET, LEGS, LEGS,
			LEGS, HEAD, HEAD };
	/**
	 * Which robotic arm carries a carried stage (true = the one on the wearer's right). v0.15.8: fixed for the gauntlets
	 * and boots (each side's own arm); the chest, leggings, helmet and faceplate go to whichever arm is free soonest
	 * ({@link Plan#rightArm}) -- this is only their tie-break.
	 */
	private static final boolean[] RIGHT_ARM = { true, true, true, true, true, true, false, false, false, true, false, false,
			false, false, true, false };

	public static boolean carried(int stage) {
		return CARRIED[stage];
	}

	public static int pieceOf(int stage) {
		return PIECE[stage];
	}

	public static boolean rightArmCarries(int stage) {
		return RIGHT_ARM[stage];
	}

	/** The stage that brings piece {@code rackSlot} onto the body -- the real stack moves at its fit. */
	public static int firstStage(int rackSlot) {
		return switch (rackSlot) {
			case HEAD -> HELMET;
			case CHEST -> TORSO_TOP;
			case LEGS -> THIGH_TOP;
			default -> R_BOOT;
		};
	}

	/** Bit mask of the rack slots in {@code slots}. */
	public static int maskOf(int[] slots) {
		int m = 0;
		for (int s : slots) {
			if (s >= 0 && s < 4) {
				m |= 1 << s;
			}
		}
		return m;
	}

	/** The stages of one sequence, each one's first frame, and the whole length. */
	public static final class Plan {
		private final int[] stages;
		private final int[] begin;
		/** v0.15.8: per entry -- which arm carries it, and the frame its carry ends (cut short by that arm's next part). */
		private final boolean[] right;
		private final int[] end;
		private final int total;
		private final int mask;

		private Plan(int mask) {
			this.mask = mask;
			boolean[] has = new boolean[STAGES];
			for (int s = 0; s < STAGES; s++) {
				has[s] = (mask & (1 << PIECE[s])) != 0 || s == PANTS && (mask & (1 << FEET)) != 0;
			}
			// v0.15.8, user request: the carried parts go on to a fixed beat -- each fits PIECE_GAP after the one before,
			// the second gauntlet / boot PAIR_GAP after the first -- without waiting for the builds, which start the moment
			// their parent part is on and run alongside the later carries at their own (unchanged) speed
			int[] fit = new int[STAGES];
			int[] be = new int[STAGES];
			java.util.Arrays.fill(fit, -1);
			int fitOffset = Math.round(CARRY * FIT);
			boolean[] arm = new boolean[STAGES];
			int lastR = Integer.MIN_VALUE / 2;
			int lastL = Integer.MIN_VALUE / 2;
			int prev = -1;
			for (int s = 0; s < STAGES; s++) {
				if (!has[s] || !CARRIED[s]) {
					continue;
				}
				boolean pair = (s == L_GAUNTLET && prev == R_GAUNTLET) || (s == L_BOOT && prev == R_BOOT);
				fit[s] = prev < 0 ? LEAD + fitOffset : fit[prev] + (pair ? PAIR_GAP : PIECE_GAP);
				be[s] = fit[s] - fitOffset;
				boolean limb = s == R_GAUNTLET || s == L_GAUNTLET || s == R_BOOT || s == L_BOOT;
				if (limb) {
					arm[s] = RIGHT_ARM[s];
				} else if (lastR != lastL) {
					arm[s] = lastR < lastL; // whichever arm finished its last part longer ago
				} else {
					// neither has worked yet: leave free the arm the next gauntlet / boot needs
					int next = -1;
					for (int n = s + 1; n < STAGES && next < 0; n++) {
						if (has[n] && CARRIED[n]) {
							next = n;
						}
					}
					boolean nextLimb = next == R_GAUNTLET || next == L_GAUNTLET || next == R_BOOT || next == L_BOOT;
					arm[s] = nextLimb ? !RIGHT_ARM[next] : RIGHT_ARM[s];
				}
				if (arm[s]) {
					lastR = fit[s];
				} else {
					lastL = fit[s];
				}
				prev = s;
			}
			for (int s = 0; s < STAGES; s++) {
				if (!has[s] || CARRIED[s]) {
					continue;
				}
				int from = switch (s) {
					case TORSO_BOTTOM -> fit[TORSO_TOP];
					case JACKET -> has[TORSO_BOTTOM] ? be[TORSO_BOTTOM] + BUILD : fit[TORSO_TOP];
					case R_ARM -> fit[R_GAUNTLET];
					case R_SLEEVE -> has[R_ARM] ? be[R_ARM] + BUILD : fit[R_GAUNTLET];
					case L_ARM -> fit[L_GAUNTLET];
					case L_SLEEVE -> has[L_ARM] ? be[L_ARM] + BUILD : fit[L_GAUNTLET];
					case THIGH_BOTTOM -> fit[THIGH_TOP];
					// the pant layer runs down the legs and over the boots: after both
					default -> Math.max(has[THIGH_BOTTOM] ? be[THIGH_BOTTOM] + BUILD : fit[THIGH_TOP],
							Math.max(fit[R_BOOT], fit[L_BOOT]));
				};
				be[s] = Math.max(LEAD, from);
			}
			int n = 0;
			int end = LEAD;
			int[] st = new int[STAGES];
			int[] bg = new int[STAGES];
			for (int s = 0; s < STAGES; s++) {
				if (has[s]) {
					st[n] = s;
					bg[n] = be[s];
					end = Math.max(end, be[s] + (CARRIED[s] ? CARRY : BUILD));
					n++;
				}
			}
			stages = java.util.Arrays.copyOf(st, n);
			begin = java.util.Arrays.copyOf(bg, n);
			total = end + OUTRO;
			right = new boolean[n];
			this.end = new int[n];
			for (int i = 0; i < n; i++) {
				right[i] = arm[stages[i]];
				this.end[i] = begin[i] + (CARRIED[stages[i]] ? CARRY : BUILD);
			}
			for (int i = 0; i < n; i++) {
				if (!CARRIED[stages[i]]) {
					continue;
				}
				for (int j = 0; j < n; j++) {
					if (j != i && CARRIED[stages[j]] && right[j] == right[i] && begin[j] > begin[i] && begin[j] < this.end[i]) {
						this.end[i] = begin[j]; // the arm sets off for its next part
					}
				}
			}
		}

		/** v0.15.8: does the right arm (true) or the left carry entry {@code i}? */
		public boolean rightArm(int i) {
			return right[i];
		}

		/** v0.15.8: the frame entry {@code i} ends -- for a carry, cut short if its arm has to set off for its next part. */
		public int end(int i) {
			return end[i];
		}

		/** v0.15.8: the same arm's carry that entry {@code i} cuts short (it starts from that arm's pose), or -1. */
		public int cutFrom(int i) {
			for (int j = 0; j < stages.length; j++) {
				if (j != i && CARRIED[stages[j]] && right[j] == right[i] && end[j] == begin[i]
						&& begin[j] + length(j) > begin[i]) {
					return j;
				}
			}
			return -1;
		}

		public int mask() {
			return mask;
		}

		public int total() {
			return total;
		}

		public int count() {
			return stages.length;
		}

		public int stage(int i) {
			return stages[i];
		}

		public int begin(int i) {
			return begin[i];
		}

		public int length(int i) {
			return CARRIED[stages[i]] ? CARRY : BUILD;
		}

		/** Index of stage {@code s} in this plan, or -1. */
		public int indexOf(int s) {
			for (int i = 0; i < stages.length; i++) {
				if (stages[i] == s) {
					return i;
				}
			}
			return -1;
		}

		/** The plan entry frame {@code f} falls in, or -1 during the lead-in / outro. */
		public int at(float f) {
			if (stages.length == 0 || f < LEAD || f >= total - OUTRO) {
				return -1;
			}
			for (int i = stages.length - 1; i >= 0; i--) {
				if (f >= begin[i]) {
					return i;
				}
			}
			return -1;
		}

		/** v0.15.8: the carried entry the right ({@code true}) / left arm is working at frame {@code f}, or -1. */
		public int carrying(float f, boolean right) {
			for (int i = 0; i < stages.length; i++) {
				int s = stages[i];
				if (CARRIED[s] && this.right[i] == right && f >= begin[i] && f < end[i]) {
					return i;
				}
			}
			return -1;
		}

		/** v0.15.8: the entry of the other gauntlet / boot of entry {@code i}'s pair, or -1 (not a pair, or not in the plan). */
		public int partner(int i) {
			return switch (stages[i]) {
				case R_GAUNTLET -> indexOf(L_GAUNTLET);
				case L_GAUNTLET -> indexOf(R_GAUNTLET);
				case R_BOOT -> indexOf(L_BOOT);
				case L_BOOT -> indexOf(R_BOOT);
				default -> -1;
			};
		}

		/**
		 * v0.15.9: does entry {@code i}'s gauntlet / boot ride up the elevator together with its pair's other part? Only
		 * when they go on less than {@link #PIECE_GAP} apart -- since v0.15.9 (every part 1 s apart) they never do and each
		 * rides up alone, centred.
		 */
		public boolean ridesWithPartner(int i) {
			int p = partner(i);
			return p >= 0 && Math.abs(begin[p] - begin[i]) < PIECE_GAP;
		}

		/** v0.15.8: where entry {@code i}'s part sits across the elevator pad (blocks, + = the wearer's right). */
		public double padX(int i) {
			if (!ridesWithPartner(i)) {
				return 0.0;
			}
			return right[i] ? PAIR_OFFSET : -PAIR_OFFSET;
		}

		/** How far through plan entry {@code i} frame {@code f} is (0..1, clamped). */
		public float frac(float f, int i) {
			return clamp((f - begin[i]) / (float) length(i));
		}

		/** Frame at which carried entry {@code i}'s jaws have closed and it leaves the elevator. */
		public int liftTick(int i) {
			return begin[i] + Math.round(length(i) * GRIP);
		}

		/** Frame at which carried entry {@code i} reaches the body. */
		public int fitTick(int i) {
			return begin[i] + Math.round(length(i) * FIT);
		}

		public int letGoTick(int i) {
			// v0.15.8: an arm that sets off for its next part lets go as it goes
			return Math.min(begin[i] + Math.round(length(i) * LET_GO), Math.max(fitTick(i), end[i] - 1));
		}

		/** Frame at which the real stack of {@code rackSlot} goes onto the body (-1 if the suit has no such piece). */
		public int equipTick(int rackSlot) {
			int i = indexOf(firstStage(rackSlot));
			return i < 0 ? -1 : fitTick(i);
		}

		/** Taking the suit off: the tick at which piece {@code rackSlot} comes off the body. */
		public int removeTick(int rackSlot) {
			int e = equipTick(rackSlot);
			return e < 0 ? -1 : total - e;
		}

		/**
		 * The frame at which a texel of stage {@code s} at build position {@code k} (0..1 along the part) is on the body:
		 * a carried part all at its fit, a built part spread over its window. {@code Float.NaN} if the plan lacks it.
		 */
		public float appear(int s, float k) {
			int i = indexOf(s);
			if (i < 0) {
				return Float.NaN;
			}
			if (CARRIED[s]) {
				return fitTick(i);
			}
			return begin[i] + length(i) * (BUILD_FROM + (BUILD_TO - BUILD_FROM) * clamp(k));
		}
	}

	private static final Plan[] PLANS = new Plan[16];

	public static Plan plan(int mask) {
		mask &= 15;
		Plan p = PLANS[mask];
		if (p == null) {
			p = new Plan(mask);
			PLANS[mask] = p;
		}
		return p;
	}

	public static Plan plan(int[] slots) {
		return plan(maskOf(slots));
	}

	/** A whole suit (all four pieces): about 11 s (v0.15.8; it was 22 s one part after another). */
	public static final Plan FULL = plan(15);

	private GantryTimeline() {
	}

	/** The frame a sequence shows {@code t} ticks in: the timetable itself on, backwards off. */
	public static float frame(boolean equip, float t, Plan plan) {
		return equip ? t : plan.total() - t;
	}

	// ---------------- the lead-in / outro (mirror images) ----------------

	/** Frames into the lead-in, or out of the end of the outro -- whichever edge {@code f} is nearer. */
	public static float edge(float f, Plan plan) {
		return Math.min(f, plan.total() - f);
	}

	/** Lift height above the floor top (blocks). */
	public static float lift(float f, Plan plan) {
		return LIFT * smooth(edge(f, plan) / LIFT_TO);
	}

	/** Hatch panels: 0 shut .. 1 fully slid open. */
	public static float hatch(float f, Plan plan) {
		return smooth((edge(f, plan) - HATCH_FROM) / (float) (HATCH_TO - HATCH_FROM));
	}

	/** Arm masts: 0 sunk in the floor .. 1 fully up. */
	public static float rise(float f, Plan plan) {
		return smooth((edge(f, plan) - RISE_FROM) / (float) (RISE_TO - RISE_FROM));
	}

	/** Arms: 0 folded upright .. 1 at the ready hover. */
	public static float unfold(float f, Plan plan) {
		return smooth((edge(f, plan) - UNFOLD_FROM) / (float) (UNFOLD_TO - UNFOLD_FROM));
	}

	/** The front elevator during a carry stage: 0 sunk .. 1 up at the floor (u = the stage fraction). */
	public static float elevator(float u) {
		if (u < SINK_FROM) {
			return smooth(u / ELEVATOR_UP);
		}
		return 1f - smooth((u - SINK_FROM) / (SINK_TO - SINK_FROM));
	}

	// ---------------- where each part is on the wearer ----------------

	/** The wearer's arm pose during a sequence: tilted forward / out (radians). */
	public static final float ARM_X = -0.18f;
	public static final float ARM_Z = 0.62f;

	/**
	 * A point on the wearer's body for stage {@code s} at build position {@code k}, in the wearer's frame in blocks
	 * (x: their right, y: up from the feet, z: forward is negative), on the part's front surface -- where a carried part
	 * is gripped ({@code k} = 0.5) and where a build's leading edge is ({@code side} -1 / +1 picks the left / right
	 * limb of a two-limb part).
	 */
	public static double[] partPoint(int s, float k, int side) {
		k = clamp(k);
		return switch (s) {
			case TORSO_TOP -> px(0, 21, -2.6);
			case TORSO_BOTTOM -> px(2.2 * side, 18 - 6 * k, -2.6);
			case JACKET -> px(2.2 * side, 24 - 12 * k, -2.8);
			case R_GAUNTLET -> arm(true, 7.5, -2.4);
			case R_ARM -> arm(true, 5 - 7 * k, -2.4);
			case R_SLEEVE -> arm(true, 10 - 12 * k, -2.6);
			case L_GAUNTLET -> arm(false, 7.5, -2.4);
			case L_ARM -> arm(false, 5 - 7 * k, -2.4);
			case L_SLEEVE -> arm(false, 10 - 12 * k, -2.6);
			case R_BOOT -> px(2, 2.2, -2.6);
			case L_BOOT -> px(-2, 2.2, -2.6);
			case THIGH_TOP -> px(0, 10, -2.6);
			case THIGH_BOTTOM -> px(2 * side, 8 - 4 * k, -2.6);
			case PANTS -> px(2 * side, 12 - 12 * k, -2.8);
			case HELMET -> px(0, 28, -1);
			default -> px(0, 28, -4.4); // the faceplate
		};
	}

	/** Model pixels (x toward the wearer's right) -> blocks. */
	private static double[] px(double x, double y, double z) {
		return new double[] { x / 16.0, y / 16.0, z / 16.0 };
	}

	/** A point {@code d} model pixels down the arm from the shoulder pivot, in the gantry pose. */
	private static double[] arm(boolean right, double d, double z) {
		double out = 5 + d * Math.sin(ARM_Z);
		double y = 22 - d * Math.cos(ARM_Z) * Math.cos(ARM_X);
		double fwd = z - d * Math.sin(-ARM_X);
		return px(right ? out : -out, y, fwd);
	}

	/** The lowest point of carried stage {@code s}'s part above the feet (blocks) -- so it stands on the elevator pad. */
	public static double partBottom(int s) {
		return switch (s) {
			case TORSO_TOP -> 18.0 / 16.0;
			case R_GAUNTLET, L_GAUNTLET -> 0.80;
			case R_BOOT, L_BOOT -> 0.0;
			case THIGH_TOP -> 8.0 / 16.0;
			default -> 24.0 / 16.0;
		};
	}

	// ---------------- the wearer's body pose ----------------

	/**
	 * The wearer's pose at frame {@code f}, as {weight, key...} in {@link IronManSuitPoses}' key layout: arms held down
	 * and out (clear of the arm masts at their sides), a slightly wide stance, and the head following the work. Eases in
	 * over 10 frames and out over the last 10.
	 */
	public static float[] pose(float f, Plan plan) {
		int total = plan.total();
		float w = smooth(f / 10f) * smooth((total - f) / 10f);
		float[] out = new float[1 + IronManSuitPoses.SIZE];
		out[0] = w;
		out[1 + IronManSuitPoses.RAX] = ARM_X;
		out[1 + IronManSuitPoses.RAZ] = ARM_Z;
		out[1 + IronManSuitPoses.LAX] = ARM_X;
		out[1 + IronManSuitPoses.LAZ] = -ARM_Z;
		out[1 + IronManSuitPoses.RLZ] = 0.10f;
		out[1 + IronManSuitPoses.LLZ] = -0.10f;
		// the head looks down at the part being worked on (chest a little, the boots most), straight ahead for the helmet
		float head = 0.25f;
		int i = plan.at(f);
		if (i >= 0) {
			head = headFor(plan.stage(i));
			if (i + 1 < plan.count()) {
				float u = plan.frac(f, i);
				head += (headFor(plan.stage(i + 1)) - head) * smooth((u - 0.8f) / 0.2f);
			}
		} else if (f >= total - OUTRO) {
			head = -0.05f;
		}
		out[1 + IronManSuitPoses.HX] = head;
		// a glance at the arm being built
		if (i >= 0) {
			int s = plan.stage(i);
			if (s >= R_GAUNTLET && s <= R_SLEEVE) {
				out[1 + IronManSuitPoses.HY] = 0.35f;
			} else if (s >= L_GAUNTLET && s <= L_SLEEVE) {
				out[1 + IronManSuitPoses.HY] = -0.35f;
			}
		}
		return out;
	}

	private static float headFor(int s) {
		return switch (s) {
			case TORSO_TOP, TORSO_BOTTOM, JACKET -> 0.35f;
			case R_GAUNTLET, R_ARM, R_SLEEVE, L_GAUNTLET, L_ARM, L_SLEEVE -> 0.30f;
			case R_BOOT, L_BOOT, PANTS -> 0.55f;
			case THIGH_TOP, THIGH_BOTTOM -> 0.45f;
			default -> -0.05f;
		};
	}

	public static float smooth(float x) {
		x = clamp(x);
		return x * x * (3f - 2f * x);
	}

	public static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}
