package com.projecthero.mod.miraculous.viperion;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Viperion's synced values (the keys of {@code MiraculousState#vals} / {@code vecs} his kit owns, all prefixed
 * {@code vp_}) and the small client-safe reads the HUD, the poses and the renderers share. Timers are absolute game times.
 */
public final class Viperion {
	/** Lyre Strike: the three-hit combo runs until. */
	public static final String V_COMBO = "vp_combo_until";
	/** Serpent Dash: slithering (low, the swim / crawl pose) until. */
	public static final String V_DASH = "vp_dash_until";
	/** Coil Leap: in the air since (cleared on landing). */
	public static final String V_LEAP = "vp_leap_until";
	/** String Shield: the spinning lyre guards the front until. */
	public static final String V_SHIELD = "vp_shield_until";
	/** Constrict: the strings hold a creature until; its entity id + 1 in {@link #V_CONSTRICT_ID}. */
	public static final String V_CONSTRICT = "vp_constrict_until";
	public static final String V_CONSTRICT_ID = "vp_constrict_id";
	/** Heat Sense: 1 while on (the glow is drawn by his own client only). */
	public static final String V_HEAT = "vp_heat";
	/** Hiss: mobs near run from him until. */
	public static final String V_HISS = "vp_hiss_until";
	/** Second Chance: the checkpoint's window ends at (0 = no checkpoint). */
	public static final String V_CP_UNTIL = "vp_cp_until";
	/** Second Chance: rewinds left on the checkpoint. */
	public static final String V_CP_LEFT = "vp_cp_left";
	/** Second Chance: the last rewind (or the checkpoint itself, negated) happened at this game time -- the screen FX. */
	public static final String V_REWIND_AT = "vp_rewind_at";
	public static final String V_CHECKPOINT_AT = "vp_checkpoint_at";
	/** Second Chance: where the checkpoint was set (the world marker). */
	public static final String X_CP = "vp_cp_pos";

	// the pose ids (140..159, see MiraculousState)
	public static final int ANIM_STRIKE = 140;
	public static final int ANIM_SWEEP = 141;
	public static final int ANIM_CHORD = 142;
	public static final int ANIM_RESONANCE = 143;
	public static final int ANIM_DASH = 144;
	public static final int ANIM_COIL = 145;
	public static final int ANIM_VENOM = 146;
	public static final int ANIM_CHECKPOINT = 147;
	public static final int ANIM_REWIND = 148;
	public static final int ANIM_CONSTRICT = 149;
	public static final int ANIM_SHIELD = 150;
	public static final int ANIM_HEAT = 151;
	public static final int ANIM_HISS = 152;

	private Viperion() {
	}

	/** Client-safe: transformed as Viperion. */
	public static boolean is(Player player) {
		return Miraculous.transformedAs(player, Kwami.SASS);
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

	/** Client-safe (both sides read it in {@code ViperionDashPoseMixin}): slithering low along the ground. */
	public static boolean slithering(Player player) {
		return is(player) && running(player, V_DASH);
	}

	public static boolean heatSense(Player player) {
		return is(player) && val(player, V_HEAT) != 0L;
	}

	public static boolean shielding(Player player) {
		return is(player) && running(player, V_SHIELD);
	}

	/** Client-safe: a Second Chance checkpoint is live (ticks left in its window, 0 = none). */
	public static int checkpointTicksLeft(Player player) {
		if (!is(player)) {
			return 0;
		}
		long until = val(player, V_CP_UNTIL);
		return until <= 0L ? 0 : (int) Math.max(0L, until - player.level().getGameTime());
	}

	public static int rewindsLeft(Player player) {
		return (int) val(player, V_CP_LEFT);
	}
}
