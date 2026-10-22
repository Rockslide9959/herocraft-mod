package com.projecthero.mod.quirk.hellflame;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Hellflame's synced keys (in {@code QuirkState.vals} / {@code vecs}) and pose ids, and the client-safe reads the
 * HUD, the poses, the flame FX and the world renderer (Hell Spider's strings, the Mesh, the Prominence beam) use.
 * Timers are absolute game times (0 = off); the held moves store the tick they started.
 */
public final class Hellflame {
	private Hellflame() {
	}

	// ---------------------------------------------------------------- synced vals
	/** Fire Jet Flight on: the tick it lit (0 = off). */
	public static final String V_FLIGHT = "hf_flight";
	/** Flame Cloak on (1). */
	public static final String V_CLOAK = "hf_cloak";
	/** Jet Burn dash: runs until this game time. */
	public static final String V_DASH = "hf_dash";
	/** Hell Spider: the tick the strings were cast; {@link #VEC_SPIDER} holds the aim (yaw, pitch, 0) then. */
	public static final String V_SPIDER = "hf_spider";
	public static final String VEC_SPIDER = "hf_spider";
	/** Hell Spider: Mesh: burns until this game time; {@link #V_MESH_AT} is when it was cast, {@link #VEC_MESH} its centre. */
	public static final String V_MESH = "hf_mesh";
	public static final String V_MESH_AT = "hf_mesh_at";
	public static final String VEC_MESH = "hf_mesh";
	/** Fire Wall: burns until this game time, between {@link #VEC_WALL_A} and {@link #VEC_WALL_B} (its base). */
	public static final String V_WALL = "hf_wall";
	public static final String VEC_WALL_A = "hf_wall_a";
	public static final String VEC_WALL_B = "hf_wall_b";
	/** Hellflame Barrage: runs until this game time. */
	public static final String V_BARRAGE = "hf_barrage";
	/** Fire Pillar: erupts at this game time at {@link #VEC_PILLAR} (the ground glows before). */
	public static final String V_PILLAR = "hf_pillar";
	public static final String VEC_PILLAR = "hf_pillar";
	/** Prominence Burn: the tick the charge started / the tick the beam fired (0 = off). */
	public static final String V_CHARGE = "hf_charge";
	public static final String V_BEAM = "hf_beam";
	/** Vanishing Fist: the flame decoy burns until this game time at {@link #VEC_DECOY} (x, y, z) facing {@link #V_DECOY_YAW}. */
	public static final String V_DECOY = "hf_decoy";
	public static final String V_DECOY_YAW = "hf_decoy_yaw";
	public static final String VEC_DECOY = "hf_decoy";
	/** Plus Ultra: full power until this game time. */
	public static final String V_PLUS = "hf_plus";
	/** Burnt out after Plus Ultra: Heat pinned at 100 until this game time. */
	public static final String V_BURNOUT = "hf_burnout";

	// ---------------------------------------------------------------- poses (animBase 20: ids 20..39)
	public static final int ANIM_JET = 20;
	public static final int ANIM_FLIGHT = 21;
	public static final int ANIM_SPIDER = 22;
	public static final int ANIM_MESH = 23;
	public static final int ANIM_WALL = 24;
	public static final int ANIM_CLOAK = 25;
	public static final int ANIM_BARRAGE = 26;
	public static final int ANIM_PILLAR = 27;
	public static final int ANIM_VANISH = 28;
	public static final int ANIM_COOL = 30;
	public static final int ANIM_PLUS = 31;
	public static final int ANIM_OVERHEAT = 32;

	// ---------------------------------------------------------------- client-safe reads

	/** Holds Hellflame and is not Erased. */
	public static boolean is(Player p) {
		return Quirks.activeAs(p, Quirk.HELLFLAME);
	}

	public static long val(Player p, String key) {
		QuirkState s = Quirks.peek(p);
		return s == null ? 0L : s.val(key);
	}

	public static @Nullable Vec3 vec(Player p, String key) {
		QuirkState s = Quirks.peek(p);
		return s == null ? null : Quirks.vec(s, key);
	}

	private static boolean until(Player p, String key) {
		return is(p) && p.level() != null && val(p, key) > p.level().getGameTime();
	}

	public static boolean flying(Player p) {
		return is(p) && val(p, V_FLIGHT) != 0L;
	}

	public static boolean cloaked(Player p) {
		return is(p) && val(p, V_CLOAK) != 0L;
	}

	public static boolean dashing(Player p) {
		return until(p, V_DASH);
	}

	public static boolean barraging(Player p) {
		return until(p, V_BARRAGE);
	}

	public static boolean charging(Player p) {
		return is(p) && val(p, V_CHARGE) != 0L;
	}

	public static boolean beaming(Player p) {
		return is(p) && val(p, V_BEAM) != 0L;
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

	public static boolean meshUp(Player p) {
		return until(p, V_MESH);
	}

	public static boolean wallUp(Player p) {
		return until(p, V_WALL);
	}

	public static boolean decoyUp(Player p) {
		return until(p, V_DECOY);
	}

	public static boolean plusUltra(Player p) {
		return until(p, V_PLUS);
	}

	public static boolean burntOut(Player p) {
		return Quirks.holds(p, Quirk.HELLFLAME) && p.level() != null && val(p, V_BURNOUT) > p.level().getGameTime();
	}

	/** A pillar is warming up or burning (a little past its eruption). */
	public static boolean pillarActive(Player p) {
		long at = val(p, V_PILLAR);
		return is(p) && at != 0L && p.level() != null && p.level().getGameTime() < at + HellflameConfig.PILLAR_BURN_TICKS;
	}

	public static boolean overheated(Player p) {
		return Quirks.holds(p, Quirk.HELLFLAME) && Quirks.overloaded(p);
	}

	/** The size multiplier of his flames right now (Plus Ultra makes everything bigger). */
	public static double size(Player p) {
		return plusUltra(p) ? HellflameConfig.PLUS_SIZE_MULT : 1.0;
	}
}
