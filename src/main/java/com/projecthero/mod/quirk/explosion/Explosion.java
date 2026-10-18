package com.projecthero.mod.quirk.explosion;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Explosion Quirk's synced values (the {@code QuirkState#vals} keys its kit owns), its pose ids
 * (120..139) and the small client-safe reads the HUD, poses and effects share. Timers are absolute game times.
 */
public final class Explosion {
	/** 1 while Blast Rush (Z, held) flies him. */
	public static final String V_RUSH = "ex_rush";
	/** The Auto-Cannon volley runs until. */
	public static final String V_AUTO = "ex_auto_until";
	/** The smoke cloud hangs until (its centre is {@link #VEC_SMOKE}). */
	public static final String V_SMOKE = "ex_smoke_until";
	public static final String VEC_SMOKE = "ex_smoke";
	/** Howitzer Impact spins until (it started at {@code animStart}). */
	public static final String V_HOWITZER = "ex_howitzer_until";
	/** Max Shot fires at this game time (charging until then). */
	public static final String V_MAX_SHOT = "ex_max_shot_at";
	/** The Explosive Counter window is open until. */
	public static final String V_COUNTER = "ex_counter_until";
	/** Arm Strain, 0..100 (hidden from the HUD; the arms smoke when it is high). Mirrored a few times a second. */
	public static final String V_STRAIN = "ex_strain";

	// the pose ids (120..139)
	public static final int ANIM_AP_SHOT = 120;
	public static final int ANIM_AUTO = 121;
	public static final int ANIM_PUNCH = 122;
	public static final int ANIM_UPPERCUT = 123;
	public static final int ANIM_STUN = 124;
	public static final int ANIM_SMOKE = 125;
	public static final int ANIM_RUSH = 126;
	public static final int ANIM_DASH = 127;
	public static final int ANIM_HOWITZER = 128;
	public static final int ANIM_MAX_SHOT = 129;
	public static final int ANIM_CLUSTER = 130;
	public static final int ANIM_COUNTER = 131;
	public static final int ANIM_COUNTER_HIT = 132;
	public static final int ANIM_STRAIN = 133;

	private Explosion() {
	}

	/** Client-safe: holds Explosion and it is not Erased. */
	public static boolean is(Player player) {
		return Quirks.activeAs(player, Quirk.EXPLOSION);
	}

	public static long val(Player player, String key) {
		QuirkState s = Quirks.peek(player);
		return s == null ? 0L : s.val(key);
	}

	/** Client-safe: a timer value still in the future. */
	public static boolean running(Player player, String key) {
		return val(player, key) > player.level().getGameTime();
	}

	public static boolean rushing(Player player) {
		return is(player) && val(player, V_RUSH) != 0L;
	}

	public static boolean spinning(Player player) {
		return is(player) && running(player, V_HOWITZER);
	}

	public static boolean charging(Player player) {
		return is(player) && running(player, V_MAX_SHOT);
	}

	/** Client-safe: the synced Arm Strain, 0..100. */
	public static float strain(Player player) {
		return val(player, V_STRAIN);
	}
}
