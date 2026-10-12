package com.projecthero.mod.ultron.contagion;

/**
 * v0.16.1: Ultron Omega's GeckoLib clip names and the tick timings his server-side moves are written against
 * ({@code animations/ultron/ultron_omega.animation.json}, {@code animation.ultron_omega.<clip>}).
 *
 * <p>The TRANSITION convention is the Titan's: the "action" controller spends {@link #TRANSITION} ticks blending into a
 * clip before the clip's own clock starts, so a clip keys its impact pose at {@code (server tick - TRANSITION)} and the
 * blow lands on screen on the server's damage tick. The placeholder generator
 * ({@code scratchpad/gen_ultron_omega_placeholder.js}) and the final model generator must both follow this table:
 *
 * <pre>
 * clip              length  loop                server impact tick(s)   clip key tick(s)
 * idle              40      loop                -                       -
 * fly               20      loop                -                       -
 * walk              20      loop                -                       -
 * vibranium_strike  44      once                12, 22, 36              8, 18, 32
 * concussive_blast  30      once                18                      14
 * lattice           100     once                beams 20..100           windup 0..16
 * siphon            100     loop                +charge at 100          -
 * split             30      once                20                      16
 * override          40      once                24                      20
 * upload            40      loop                -                       -
 * hurt              10      once                -                       2
 * stagger_kneel     30      hold last frame     -                       -
 * disintegrate      60      hold last frame     dies at 60              -
 * self_destruct     60      hold last frame     blast at 60             56
 * </pre>
 * Bones {@code neck} and {@code flinch} are never keyed (code-driven head look / hit flinch).
 */
// The model generator (scratchpad/gen_ultron_omega_model.js, OMEGA_TIMING) authors every clip to these numbers and writes
// scratchpad/ultron_omega_anim_timing.json; keep the two in step (reconciled for v0.16.1).
public final class OmegaAnims {
	public static final int TRANSITION = 4;
	public static final String PREFIX = "animation.ultron_omega.";

	public static final String IDLE = "idle";
	public static final String FLY = "fly";
	public static final String WALK = "walk";

	public static final String STRIKE = "vibranium_strike";
	public static final int STRIKE_TICKS = 44;
	public static final int[] STRIKE_HITS = { 12, 22, 36 };

	public static final String BLAST = "concussive_blast";
	public static final int BLAST_TICKS = 30;
	public static final int BLAST_IMPACT = 18;

	public static final String LATTICE = "lattice";
	public static final int LATTICE_WINDUP = 20;
	public static final int LATTICE_TICKS = 100;

	public static final String SIPHON = "siphon";
	public static final int SIPHON_TICKS = 100;

	public static final String SPLIT = "split";
	public static final int SPLIT_TICKS = 30;
	public static final int SPLIT_IMPACT = 20;

	public static final String OVERRIDE = "override";
	public static final int OVERRIDE_TICKS = 40;
	public static final int OVERRIDE_IMPACT = 24;

	public static final String UPLOAD = "upload";

	public static final String HURT = "hurt";
	public static final int HURT_TICKS = 10;

	public static final String KNEEL = "stagger_kneel";
	public static final int KNEEL_TICKS = 30;

	public static final String DISINTEGRATE = "disintegrate";
	public static final int DISINTEGRATE_TICKS = 60;

	public static final String SELF_DESTRUCT = "self_destruct";
	public static final int SELF_DESTRUCT_TICKS = 60;

	/** One-shot clips (played to the end). */
	public static final String[] ONCE = { STRIKE, BLAST, LATTICE, SPLIT, OVERRIDE, HURT };
	/** Clips held on their last frame. */
	public static final String[] HOLDS = { KNEEL, DISINTEGRATE, SELF_DESTRUCT };
	/** Looping action clips. */
	public static final String[] LOOPS = { SIPHON, UPLOAD };

	private OmegaAnims() {
	}
}
