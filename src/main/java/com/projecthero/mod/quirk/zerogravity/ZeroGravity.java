package com.projecthero.mod.quirk.zerogravity;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Zero Gravity's synced values (the keys of {@code QuirkState#vals} / {@code vecs} the kit owns), its pose ids
 * (140..159) and the small client-safe reads the HUD, the poses and the client movement share. Timers are absolute game
 * times.
 */
public final class ZeroGravity {
	/** 1 while Float Self (X) is on. */
	public static final String V_SELF = "zg_self";
	/** The Zero-G Leap (Shift+X) runs until. */
	public static final String V_LEAP = "zg_leap_until";
	/** How many creatures / items she floats right now. */
	public static final String V_FLOATING = "zg_floating";
	/** How many blocks she holds up right now (touched blocks, the cloud, the shield). */
	public static final String V_BLOCKS = "zg_blocks";
	/** The Debris Shield orbits until. */
	public static final String V_SHIELD = "zg_shield_until";
	/** The Meteor Shower cloud hangs until (0 = no cloud). */
	public static final String V_CLOUD = "zg_cloud_until";
	/** The Zero Gravity Field runs until, centred on {@link #VEC_FIELD}. */
	public static final String V_FIELD = "zg_field_until";
	public static final String VEC_FIELD = "zg_field";
	/** Gunhead Martial Arts runs until. */
	public static final String V_GUNHEAD = "zg_gunhead_until";
	/** Settle runs until (0 = not settling). */
	public static final String V_SETTLE = "zg_settle_until";

	// the pose ids (140..159, Quirk.ZERO_GRAVITY.animBase)
	public static final int ANIM_TOUCH = 140;
	public static final int ANIM_RELEASE = 141;
	public static final int ANIM_SELF = 142;
	public static final int ANIM_LEAP = 143;
	public static final int ANIM_SHOWER = 144;
	public static final int ANIM_SHIELD = 145;
	public static final int ANIM_COMET = 146;
	public static final int ANIM_SLAM = 147;
	public static final int ANIM_GUNHEAD = 148;
	public static final int ANIM_FIELD = 149;
	public static final int ANIM_CATCH = 150;
	public static final int ANIM_SICK = 151;
	public static final int ANIM_RAIN = 152;

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

	public static @Nullable Vec3 vec(Player player, String key) {
		QuirkState s = Quirks.peek(player);
		return s == null ? null : Quirks.vec(s, key);
	}

	/** Client-safe: a timer value still in the future. */
	public static boolean running(Player player, String key) {
		return player.level() != null && val(player, key) > player.level().getGameTime();
	}

	/** Client-safe: Float Self is on. */
	public static boolean selfFloating(Player player) {
		return is(player) && val(player, V_SELF) != 0L;
	}

	/** Client-safe: the weightless leap is running. */
	public static boolean leaping(Player player) {
		return is(player) && running(player, V_LEAP);
	}

	/** Client-safe: she is settling (kneeling, breathing). */
	public static boolean settling(Player player) {
		return is(player) && running(player, V_SETTLE);
	}

	/** Client-safe: she holds something up (the "fingertips ready" stance). */
	public static boolean holding(Player player) {
		return is(player) && (val(player, V_FLOATING) > 0L || val(player, V_BLOCKS) > 0L);
	}
}
