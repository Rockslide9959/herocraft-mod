package com.projecthero.mod.quirk.engine;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.item.QuirkSuits;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp): the Engine Quirk's synced keys and pose ids, and client-safe reads of them (the HUD, the poses, the
 * exhaust FX, the calf engines and the engine sound all use these). Timers are absolute game times in {@code QuirkState.vals}.
 *
 * <p>The heart of the kit is the <b>gearbox</b> (V / Shift+V): {@link #V_GEAR_NO} (0..3) is the gear he is in,
 * {@link #V_MOMENTUM} (0..100) how far the run has built up in it. <b>Recipro Turbo</b> (C, the mode) doubles the gear for
 * 30 s ({@link #V_TURBO}); the engines then stall for a minute (the Quirk overload).
 */
public final class Engine {
	private Engine() {
	}

	// ---------------------------------------------------------------- synced vals
	/** The gear engaged, 0..3. */
	public static final String V_GEAR_NO = "engine_gear_no";
	/** The run's momentum in this gear, 0..100 (mirrored from the server a few times a second). */
	public static final String V_MOMENTUM = "engine_momentum";
	/** Recipro Turbo (the mode) runs until (game time); the minute-long stall follows. */
	public static final String V_TURBO = "engine_turbo";
	/** The Turbo Strike (X in Turbo) rushes until. */
	public static final String V_STRIKE = "engine_strike";
	/** Recipro Dash runs until. */
	public static final String V_DASH = "engine_dash";
	/** Recipro Extend Kick charges until (it lands then). */
	public static final String V_EXTEND_KICK = "engine_xkick";
	/** The Kick Barrage / the finishing combo run until. */
	public static final String V_BARRAGE = "engine_barrage";
	public static final String V_FINISHER = "engine_finisher";
	/** The last sliding turn (game time), for the skid pose and sparks. */
	public static final String V_SLIDE = "engine_slide";

	/** Pre-revamp keys: removed from a save the first time the kit ticks (a carried creature is set down). */
	public static final String LEGACY_GEAR = "engine_gear";
	public static final String LEGACY_KICK = "engine_kick";
	public static final String LEGACY_CARRY = "engine_carry";
	public static final String LEGACY_STEAM = "engine_steam";
	/** v0.16.7: Recipro Burst / Extend (V at Gear 3) were replaced by Recipro Turbo; their timers are dropped. */
	public static final String LEGACY_BURST = "engine_burst";
	public static final String LEGACY_EXTEND = "engine_extend";

	// ---------------------------------------------------------------- poses (60..79)
	public static final int ANIM_SHIFT_UP = 60;
	public static final int ANIM_SHIFT_DOWN = 61;
	/** C: Recipro Turbo engaged -- the sprinter's set and the launch (the old Recipro Burst pose id). */
	public static final int ANIM_TURBO_ON = 62;
	public static final int ANIM_KICK = 64;
	public static final int ANIM_SPIN = 65;
	public static final int ANIM_DASH = 66;
	public static final int ANIM_BARRAGE = 67;
	public static final int ANIM_EXTEND_KICK = 68;
	/** X in Turbo: the Turbo Strike (a flying kick). */
	public static final int ANIM_STRIKE = 69;
	public static final int ANIM_RISING = 70;
	public static final int ANIM_AXE = 71;
	public static final int ANIM_SWEEP = 72;
	public static final int ANIM_STALL = 73;
	public static final int ANIM_FINISHER = 74;

	/** Holds Engine and it is not Erased. */
	public static boolean is(Player p) {
		return Quirks.activeAs(p, Quirk.ENGINE);
	}

	/** Holds Engine at all (Erased or not): the engines are part of his body -- the calf pipes are always there. */
	public static boolean hasEngines(Player p) {
		return Quirks.holds(p, Quirk.ENGINE);
	}

	/** Wears the Ingenium hero suit (chestplate, leggings and boots): heat builds 50% slower. Client-safe. */
	public static boolean suitWorn(LivingEntity e) {
		return QuirkSuits.fullSetWorn(e, QuirkSuits.Suit.INGENIUM);
	}

	public static long val(Player p, String key) {
		QuirkState s = Quirks.peek(p);
		return s == null ? 0L : s.val(key);
	}

	public static boolean running(Player p, String key) {
		return val(p, key) > p.level().getGameTime();
	}

	/** The gear engaged, 0..3 (0 while stalled). */
	public static int gear(Player p) {
		if (stalled(p)) {
			return 0;
		}
		return (int) Math.max(0L, Math.min(3L, val(p, V_GEAR_NO)));
	}

	/** The run's momentum, 0..1. */
	public static float momentum(Player p) {
		return Math.max(0f, Math.min(100f, val(p, V_MOMENTUM))) / 100f;
	}

	/** Recipro Turbo is running. */
	public static boolean turbo(Player p) {
		return running(p, V_TURBO) && !stalled(p);
	}

	/** Ticks of Recipro Turbo left. */
	public static int turboLeft(Player p) {
		return (int) Math.max(0L, val(p, V_TURBO) - p.level().getGameTime());
	}

	public static boolean stalled(Player p) {
		return Quirks.holds(p, Quirk.ENGINE) && Quirks.overloaded(p);
	}

	/**
	 * How hard the engines are working, 0..~6 (the FX / sound / pose intensity): the gear, plus two in Recipro Turbo, one
	 * more in the dash or the Turbo Strike.
	 */
	public static float drive(Player p) {
		if (stalled(p)) {
			return 0f;
		}
		float d = gear(p);
		if (turbo(p)) {
			d = Math.max(1f, d) + 2f;
		}
		if (running(p, V_DASH) || running(p, V_STRIKE)) {
			d = Math.max(d, 3f) + 1f;
		}
		return d;
	}
}
