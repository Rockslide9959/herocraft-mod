package com.projecthero.mod.quirk.engine;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Engine Quirk's synced keys and pose ids, and client-safe reads of them (the HUD, the poses and the
 * exhaust FX all use these). Timers are absolute game times in {@code QuirkState.vals}.
 */
public final class Engine {
	private Engine() {
	}

	// ---------------------------------------------------------------- synced vals
	/** Recipro Burst runs until (game time). */
	public static final String V_BURST = "engine_burst";
	/** Gear Up runs until. */
	public static final String V_GEAR = "engine_gear";
	/** Recipro Turbo runs until. */
	public static final String V_TURBO = "engine_turbo";
	/** Recipro Extend's dash runs until. */
	public static final String V_EXTEND = "engine_extend";
	/** The kick combo runs until. */
	public static final String V_KICK = "engine_kick";
	/** The entity id carried (0 = none). */
	public static final String V_CARRY = "engine_carry";
	/** Cool Down's steam runs until. */
	public static final String V_STEAM = "engine_steam";

	// ---------------------------------------------------------------- poses (60..79)
	public static final int ANIM_KICK = 60;
	public static final int ANIM_SPIN = 61;
	public static final int ANIM_BURST = 62;
	public static final int ANIM_GEAR = 63;
	public static final int ANIM_LEAP = 64;
	public static final int ANIM_AIR_DASH = 65;
	public static final int ANIM_EXTEND = 66;
	public static final int ANIM_AFTERBURNER = 67;
	public static final int ANIM_CARRY = 68;
	public static final int ANIM_THROW = 69;
	public static final int ANIM_TURBO = 70;
	public static final int ANIM_COOL = 71;

	/** Holds Engine and it is not Erased. */
	public static boolean is(Player p) {
		return Quirks.activeAs(p, Quirk.ENGINE);
	}

	public static boolean running(Player p, String key) {
		QuirkState s = Quirks.peek(p);
		return s != null && s.val(key) > p.level().getGameTime();
	}

	public static boolean carrying(Player p) {
		QuirkState s = Quirks.peek(p);
		return s != null && s.val(V_CARRY) != 0L;
	}

	/** 0 = cruising, 1 = Recipro Burst, 2 = Gear Up, 3 = Recipro Turbo (the highest running gear). */
	public static int gear(Player p) {
		if (running(p, V_TURBO)) {
			return 3;
		}
		if (running(p, V_GEAR)) {
			return 2;
		}
		return running(p, V_BURST) ? 1 : 0;
	}

	public static boolean stalled(Player p) {
		return Quirks.holds(p, Quirk.ENGINE) && Quirks.overloaded(p);
	}
}
