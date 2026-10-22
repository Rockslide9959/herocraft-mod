package com.projecthero.mod.quirk.explosion;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp): the Explosion Quirk's synced values (the {@code QuirkState#vals} keys its kit owns), its pose ids
 * (120..139) and the small client-safe reads the HUD, poses and effects share. Timers are absolute game times.
 */
public final class Explosion {
	/** 1 while Explosion Propulsion (X, held) flies him. */
	public static final String V_PROPEL = "ex_propel";
	/** The Auto-Cannon volley runs until. */
	public static final String V_AUTO = "ex_auto_until";
	/** Blast Rush's chain of blasts runs until. */
	public static final String V_BLAST_RUSH = "ex_blast_rush_until";
	/** Howitzer Impact runs until (it started at {@code animStart}); {@link #V_HOWITZER_KIND}: 1 the full one, 2 the Cluster. */
	public static final String V_HOWITZER = "ex_howitzer_until";
	public static final String V_HOWITZER_KIND = "ex_howitzer_kind";
	/** The game time the Howitzer's rise ends and the spinning dive starts. */
	public static final String V_HOWITZER_DIVE = "ex_howitzer_dive";
	/** Gauntlet Grenade fires at this game time (the pin is pulled until then). */
	public static final String V_GAUNTLET = "ex_gauntlet_at";
	/** Cool Off's steam runs until. */
	public static final String V_COOL = "ex_cool_until";
	/** Arm Strain, 0..100 (on the HUD as a hairline; the arms smoke when it is high). Mirrored a few times a second. */
	public static final String V_STRAIN = "ex_strain";

	/** Pre-revamp keys (Blast Rush flight, the smoke cloud, Max Shot, the Counter): removed from a save on sight. */
	public static final String[] LEGACY_VALS = { "ex_rush", "ex_smoke_until", "ex_max_shot_at", "ex_counter_until" };
	public static final String LEGACY_VEC_SMOKE = "ex_smoke";

	// the pose ids (120..139)
	public static final int ANIM_AP_SHOT = 120;
	public static final int ANIM_AUTO = 121;
	public static final int ANIM_PUNCH = 122;
	public static final int ANIM_BLAST_RUSH = 123;
	public static final int ANIM_STUN = 124;
	public static final int ANIM_COOL = 125;
	public static final int ANIM_PROPEL = 126;
	public static final int ANIM_DASH = 127;
	public static final int ANIM_HOWITZER = 128;
	public static final int ANIM_GAUNTLET = 129;
	public static final int ANIM_CLUSTER = 130;
	public static final int ANIM_HOWITZER_CLUSTER = 131;
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

	public static boolean propelling(Player player) {
		return is(player) && val(player, V_PROPEL) != 0L;
	}

	public static boolean spinning(Player player) {
		return is(player) && running(player, V_HOWITZER);
	}

	public static boolean charging(Player player) {
		return is(player) && running(player, V_GAUNTLET);
	}

	/** Client-safe: the synced Arm Strain, 0..100. */
	public static float strain(Player player) {
		return val(player, V_STRAIN);
	}
}
