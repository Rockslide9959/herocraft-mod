package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Hellflame's synced keys (in {@code QuirkState.vals}) and pose ids, and the client-safe reads the HUD, the poses
 * and the flame FX use. Timers are absolute game times (0 = off); the held moves store the tick they started.
 */
public final class Hellflame {
	private Hellflame() {
	}

	// ---------------------------------------------------------------- synced vals
	/** Flame Cloak on (1). */
	public static final String V_CLOAK = "hf_cloak";
	/** Flame Boost: the tick the jets lit (0 = off). */
	public static final String V_BOOST = "hf_boost";
	/** Hellflame Beam: the tick it started (0 = off). */
	public static final String V_BEAM = "hf_beam";
	/** Prominence Burn: the tick the charge started (0 = not charging). */
	public static final String V_CHARGE = "hf_charge";
	/** Hellflame Sun: burns until this game time. */
	public static final String V_SUN = "hf_sun";
	/** Flashfire dash: runs until this game time. */
	public static final String V_DASH = "hf_dash";

	// ---------------------------------------------------------------- poses (animBase 20: ids 20..39)
	public static final int ANIM_SPIDER = 20;
	public static final int ANIM_NET = 21;
	public static final int ANIM_JET = 22;
	public static final int ANIM_BEAM = 23;
	public static final int ANIM_BOOST = 24;
	public static final int ANIM_DODGE = 25;
	public static final int ANIM_FIST = 26;
	public static final int ANIM_UPPERCUT = 27;
	public static final int ANIM_CHARGE = 28;
	public static final int ANIM_PROMINENCE = 29;
	public static final int ANIM_SUN = 30;
	public static final int ANIM_CLOAK = 31;
	public static final int ANIM_VENT = 32;
	public static final int ANIM_OVERHEAT = 33;

	// ---------------------------------------------------------------- client-safe reads

	/** Holds Hellflame and is not Erased. */
	public static boolean is(Player p) {
		return Quirks.activeAs(p, Quirk.HELLFLAME);
	}

	public static long val(Player p, String key) {
		QuirkState s = Quirks.peek(p);
		return s == null ? 0L : s.val(key);
	}

	public static boolean cloaked(Player p) {
		return is(p) && val(p, V_CLOAK) != 0L;
	}

	public static boolean boosting(Player p) {
		return is(p) && val(p, V_BOOST) != 0L;
	}

	public static boolean beaming(Player p) {
		return is(p) && val(p, V_BEAM) != 0L;
	}

	public static boolean charging(Player p) {
		return is(p) && val(p, V_CHARGE) != 0L;
	}

	/** Prominence charge 0..1 (0 when not charging). */
	public static float charge(Player p, float partial) {
		long at = val(p, V_CHARGE);
		if (at == 0L || p.level() == null) {
			return 0f;
		}
		float t = (p.level().getGameTime() - at + partial) / HellflameConfig.PROMINENCE_CHARGE_TICKS;
		return Math.max(0f, Math.min(1f, t));
	}

	public static boolean sunUp(Player p) {
		return is(p) && val(p, V_SUN) > p.level().getGameTime();
	}

	public static boolean dashing(Player p) {
		return is(p) && val(p, V_DASH) > p.level().getGameTime();
	}

	public static boolean overheated(Player p) {
		return Quirks.holds(p, Quirk.HELLFLAME) && Quirks.overloaded(p);
	}
}
