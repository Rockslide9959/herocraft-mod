package com.projecthero.mod.quirk.zerogravity;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Zero Gravity's synced values (the keys of {@code QuirkState#vals} the kit owns), its pose ids (140..159) and
 * the small client-safe reads the HUD, the poses and the client movement share. Timers are absolute game times.
 */
public final class ZeroGravity {
	/** 1 while Zero G Self (Z) is on. */
	public static final String V_SELF = "zg_self";
	/** The weightless Float Leap (Shift+Z) runs until. */
	public static final String V_LEAP = "zg_leap_until";
	/** How many creatures she floats right now. */
	public static final String V_FLOATING = "zg_floating";
	/** The Debris Shield orbits until. */
	public static final String V_SHIELD = "zg_shield_until";
	/** The Meteor Shower runs until. */
	public static final String V_SHOWER = "zg_shower_until";
	/** The Uravity Meteor runs until. */
	public static final String V_METEOR = "zg_meteor_until";
	/** The Gunhead Combo runs until. */
	public static final String V_COMBO = "zg_combo_until";

	// the pose ids (140..159, Quirk.ZERO_GRAVITY.animBase)
	public static final int ANIM_TOUCH = 140;
	public static final int ANIM_FLING = 141;
	public static final int ANIM_RELEASE = 142;
	public static final int ANIM_SLAM = 143;
	public static final int ANIM_SHOWER = 144;
	public static final int ANIM_SHIELD = 145;
	public static final int ANIM_SELF = 146;
	public static final int ANIM_LEAP = 147;
	public static final int ANIM_COMET = 148;
	public static final int ANIM_METEOR = 149;
	public static final int ANIM_THROW = 150;
	public static final int ANIM_COMBO = 151;

	/** A scoreboard tag on everything she floats (and on her own body while it floats): the safety net on chunk load. */
	public static final String TAG_FLOAT = "projecthero_zg_float";
	/** A scoreboard tag on her rubble (block displays): an orphan found on load is removed. */
	public static final String TAG_DEBRIS = "projecthero_zg_debris";

	private ZeroGravity() {
	}

	/** Client-safe: holds Zero Gravity and it is not Erased. */
	public static boolean is(Player player) {
		return Quirks.activeAs(player, Quirk.ZERO_GRAVITY);
	}

	public static long val(Player player, String key) {
		QuirkState s = Quirks.peek(player);
		return s == null ? 0L : s.val(key);
	}

	/** Client-safe: a timer value still in the future. */
	public static boolean running(Player player, String key) {
		return val(player, key) > player.level().getGameTime();
	}

	/** Client-safe: Zero G Self is on. */
	public static boolean selfFloating(Player player) {
		return is(player) && val(player, V_SELF) != 0L;
	}

	/** Client-safe: the weightless leap is running. */
	public static boolean leaping(Player player) {
		return is(player) && running(player, V_LEAP);
	}
}
