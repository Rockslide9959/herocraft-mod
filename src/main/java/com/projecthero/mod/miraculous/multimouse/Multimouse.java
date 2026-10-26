package com.projecthero.mod.miraculous.multimouse;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Multimouse's synced values (the keys of {@code MiraculousState#vals} / {@code vecs} her kit owns, all prefixed
 * {@code mm_}) and the small client-safe reads the HUD, the poses and the renderers share. Timers are absolute game times.
 *
 * <p><b>The transformation</b> is the normal short one; the suit's reveal reads {@link #transformTicks}, which follows
 * {@code Miraculous.formTicks} -- so a long show-style sequence added to the shared core later drives her reveal too
 * (the clean hook: no Multimouse code has to change, the phrase just needs to map to the long form).
 */
public final class Multimouse {
	/** Rope Lash: the combo's window for the next press runs until (the rope is out). */
	public static final String V_COMBO = "mm_combo_until";
	/** Lasso Yank: the rope flies to a creature until; its entity id + 1 in {@link #V_LASSO_ID}. */
	public static final String V_LASSO = "mm_lasso_until";
	public static final String V_LASSO_ID = "mm_lasso_id";
	/** Rope Twirl: spinning until. */
	public static final String V_TWIRL = "mm_twirl_until";
	/** Rope Swing: hooked until; the hook in {@link #X_SWING}. */
	public static final String V_SWING = "mm_swing_until";
	public static final String X_SWING = "mm_swing_hook";
	/** Scurry: mouse-sized until (0 = full size). */
	public static final String V_SCURRY = "mm_scurry_until";
	/** Mouse Swarm: the three copies dart about until. */
	public static final String V_SWARM = "mm_swarm_until";
	/** Multitude: split until (0 = whole); copies left / at the start. */
	public static final String V_MULTI = "mm_multi_until";
	public static final String V_MULTI_LEFT = "mm_multi_left";
	public static final String V_MULTI_TOTAL = "mm_multi_total";
	/** Multitude: the recombination's shockwave lands at (the survivors are running back). */
	public static final String V_RECOMBINE = "mm_recombine_at";
	/** Mouse Hearing: 1 while on (the rings are drawn by her own client only). */
	public static final String V_HEARING = "mm_hearing";
	/** Rope Bind: a creature is tied up until; its entity id + 1 in {@link #V_BIND_ID}. */
	public static final String V_BIND = "mm_bind_until";
	public static final String V_BIND_ID = "mm_bind_id";
	/** Mouse Trap: the snares on the ground, {@code mm_trap_0..2}. */
	public static final String X_TRAP = "mm_trap_";

	// the pose ids (160..179, see MiraculousState)
	public static final int ANIM_LASH = 160;
	public static final int ANIM_LASH_2 = 161;
	public static final int ANIM_LASH_3 = 162;
	public static final int ANIM_LASSO = 163;
	public static final int ANIM_TWIRL = 164;
	public static final int ANIM_TRIP = 165;
	public static final int ANIM_SWING = 166;
	public static final int ANIM_SCURRY = 167;
	public static final int ANIM_SWARM = 168;
	public static final int ANIM_MULTITUDE = 169;
	public static final int ANIM_RECOMBINE = 170;
	public static final int ANIM_HEARING = 171;
	public static final int ANIM_GNAW = 172;
	public static final int ANIM_BIND = 173;
	public static final int ANIM_TRAP = 174;

	private Multimouse() {
	}

	/** Client-safe: transformed as Multimouse. */
	public static boolean is(Player player) {
		return Miraculous.transformedAs(player, Kwami.MULLO);
	}

	/** The transformation's length (the reveal); the hook a long sequence plugs into. */
	public static int transformTicks(MiraculousState s) {
		return Miraculous.formTicks(s);
	}

	/** Client-safe: a timer value still in the future. */
	public static boolean running(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s != null && s.val(key) > player.level().getGameTime();
	}

	public static long val(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s == null ? 0L : s.val(key);
	}

	public static @Nullable Vec3 vec(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s == null ? null : Miraculous.vec(s, key);
	}

	public static boolean scurrying(Player player) {
		return is(player) && val(player, V_SCURRY) != 0L;
	}

	public static boolean multitude(Player player) {
		return is(player) && val(player, V_MULTI) != 0L;
	}

	/** Client-safe: small (Scurry or Multitude). */
	public static boolean small(Player player) {
		return scurrying(player) || multitude(player);
	}

	public static boolean hearing(Player player) {
		return is(player) && val(player, V_HEARING) != 0L;
	}

	public static boolean twirling(Player player) {
		return is(player) && running(player, V_TWIRL);
	}

	/** Client-safe: Multitude's copies left (HUD). */
	public static int copiesLeft(Player player) {
		return (int) val(player, V_MULTI_LEFT);
	}

	/** Client-safe: ticks left of Multitude (0 = not split). */
	public static int multitudeTicksLeft(Player player) {
		long until = val(player, V_MULTI);
		return until <= 0L ? 0 : (int) Math.max(0L, until - player.level().getGameTime());
	}
}
