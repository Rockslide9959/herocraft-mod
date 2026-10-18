package com.projecthero.mod.quirk.darkshadow;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Dark Shadow's synced keys (in {@code QuirkState.vals / vecs}), its pose ids (80..99) and the client-safe
 * reads the renderer, the poses and the HUD use. Timers are absolute game times; entity ids are stored as {@code id + 1}
 * (0 means none).
 */
public final class DarkShadow {
	private DarkShadow() {
	}

	// ---------------------------------------------------------------- vals
	/** Shadow Guard held: the game time it went up. */
	public static final String V_GUARD = "ds_guard";
	/** Shadow Counter window: open until. */
	public static final String V_COUNTER = "ds_counter";
	/** Black Fallen Angel on (1). */
	public static final String V_ANGEL = "ds_angel";
	/** Black Ankh on (1). */
	public static final String V_ANKH = "ds_ankh";
	/** Abyssal Rampage until. */
	public static final String V_RAMPAGE = "ds_rampage";
	/** Abyssal Swallow: the swallowed entity (id + 1) and until when. */
	public static final String V_SWALLOW = "ds_swallow";
	public static final String V_SWALLOW_END = "ds_swallow_end";
	/** Shadow Grab: the grabbed entity (id + 1) and until when Dark Shadow holds it. */
	public static final String V_GRAB = "ds_grab";
	public static final String V_GRAB_END = "ds_grab_end";
	/** Berserk (1) -- the overload the kit started (the overload itself is {@code QuirkState.overloadUntil}). */
	public static final String V_BERSERK = "ds_berserk";
	/** Shadow Dive until. */
	public static final String V_DIVE = "ds_dive";
	/** Shadow Flurry until. */
	public static final String V_FLURRY = "ds_flurry";
	/** Where Dark Shadow is reaching to ({@link #VEC_REACH}) since. */
	public static final String V_REACH_AT = "ds_reach_at";
	public static final String VEC_REACH = "ds_reach";

	// ---------------------------------------------------------------- poses (animBase 80)
	public static final int ANIM_STRIKE = 80;
	public static final int ANIM_FLURRY = 81;
	public static final int ANIM_GUARD = 82;
	public static final int ANIM_COUNTER = 83;
	public static final int ANIM_ANGEL = 84;
	public static final int ANIM_DIVE = 85;
	public static final int ANIM_GRAB = 86;
	public static final int ANIM_THROW = 87;
	public static final int ANIM_RAMPAGE = 88;
	public static final int ANIM_SWALLOW = 89;
	public static final int ANIM_ANKH = 90;
	public static final int ANIM_CALM = 91;
	/** One of Dark Shadow's own strikes while berserk / rampaging. */
	public static final int ANIM_FRENZY_HIT = 92;

	// ---------------------------------------------------------------- client-safe reads

	public static boolean is(Player p) {
		return Quirks.activeAs(p, Quirk.DARK_SHADOW);
	}

	public static long val(Player p, String key) {
		QuirkState s = Quirks.peek(p);
		return s == null ? 0L : s.val(key);
	}

	/** A timer val still running. */
	public static boolean running(Player p, String key) {
		return val(p, key) > p.level().getGameTime();
	}

	public static boolean on(Player p, String key) {
		return val(p, key) != 0L;
	}

	public static boolean berserk(Player p) {
		return on(p, V_BERSERK) && Quirks.overloaded(p);
	}

	public static boolean rampaging(Player p) {
		return running(p, V_RAMPAGE);
	}

	public static boolean guarding(Player p) {
		return on(p, V_GUARD);
	}

	public static boolean angel(Player p) {
		return on(p, V_ANGEL);
	}

	public static boolean ankh(Player p) {
		return on(p, V_ANKH);
	}

	public static @Nullable Vec3 reach(Player p) {
		QuirkState s = Quirks.peek(p);
		return s == null ? null : Quirks.vec(s, VEC_REACH);
	}

	/** Shadow 0..1 as the client sees it. */
	public static float shadow(Player p) {
		return Quirks.clientMeter(p) / 100f;
	}
}
