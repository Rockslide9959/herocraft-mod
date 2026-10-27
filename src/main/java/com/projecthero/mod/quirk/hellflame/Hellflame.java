package com.projecthero.mod.quirk.hellflame;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7, v0.17.1 (Quirks revamp): Hellflame's synced keys (in {@code QuirkState.vals} / {@code vecs}) and pose ids, and the
 * client-safe reads the HUD, the poses, the flame FX and the world renderer (Hell Spider's strings, the Mesh, the Hellblast
 * beam, the Fire Wall's sheets, Prominence Burn's sun) use. Timers are absolute game times (0 = off); the held moves store
 * the tick they started.
 */
public final class Hellflame {
	private Hellflame() {
	}

	// ---------------------------------------------------------------- synced vals
	/** Fire Jet Flight on: the tick it lit (0 = off). */
	public static final String V_FLIGHT = "hf_flight";
	/** Flame Cloak: burns until this game time. */
	public static final String V_CLOAK = "hf_cloak";
	/** Jet Burn dash: runs until this game time. */
	public static final String V_DASH = "hf_dash";
	/** v0.17.1 Jet Burst: runs until this game time. */
	public static final String V_BURST = "hf_burst";
	/** Hell Spider: the tick the strings were cast; {@link #VEC_SPIDER} holds the aim (yaw, pitch, size) then. */
	public static final String V_SPIDER = "hf_spider";
	public static final String VEC_SPIDER = "hf_spider";
	/** Hell Spider: Mesh: burns until this game time; {@link #V_MESH_AT} is when it was cast, {@link #VEC_MESH} its centre. */
	public static final String V_MESH = "hf_mesh";
	public static final String V_MESH_AT = "hf_mesh_at";
	public static final String VEC_MESH = "hf_mesh";
	/** Fire Wall: burns until this game time, between {@link #VEC_WALL_A} and {@link #VEC_WALL_B} (its base). */
	public static final String V_WALL = "hf_wall";
	public static final String V_WALL_AT = "hf_wall_at";
	public static final String VEC_WALL_A = "hf_wall_a";
	public static final String VEC_WALL_B = "hf_wall_b";
	/** Hellflame Barrage: runs until this game time. */
	public static final String V_BARRAGE = "hf_barrage";
	/** Hellflame Pillar: erupts at this game time at {@link #VEC_PILLAR} (the ground glows before). */
	public static final String V_PILLAR = "hf_pillar";
	public static final String VEC_PILLAR = "hf_pillar";
	/** Hellblast: the tick the charge started / the tick the beam fired (0 = off); {@link #V_BEAM_UNTIL} when it ends. */
	public static final String V_CHARGE = "hf_charge";
	public static final String V_BEAM = "hf_beam";
	public static final String V_BEAM_UNTIL = "hf_beam_until";
	/** Vanishing Fist: the flame decoy burns until this game time at {@link #VEC_DECOY} (x, y, z) facing {@link #V_DECOY_YAW}. */
	public static final String V_DECOY = "hf_decoy";
	public static final String V_DECOY_YAW = "hf_decoy_yaw";
	public static final String VEC_DECOY = "hf_decoy";
	/** v0.17.1 Flashfire Fist: the punch the chain is on (1..3) and when it was thrown. */
	public static final String V_FIST = "hf_fist";
	public static final String V_FIST_AT = "hf_fist_at";
	/** v0.17.1 Prominence Burn (the ultimate): the tick it ignited (0 = off); the comet flies from ignition + 30. */
	public static final String V_PROM = "hf_prom";
	/** Burnt out after Prominence Burn: Heat pinned at 100 until this game time. */
	public static final String V_BURNOUT = "hf_burnout";

	/** Pre-v0.17.1 keys (Plus Ultra: Full Power on Shift+V): removed from a save on sight. */
	public static final String[] LEGACY_VALS = { "hf_plus" };

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
	public static final int ANIM_FIST_1 = 29;
	public static final int ANIM_COOL = 30;
	public static final int ANIM_FIST_2 = 31;
	public static final int ANIM_OVERHEAT = 32;
	public static final int ANIM_FIST_3 = 33;
	public static final int ANIM_BURST = 34;
	public static final int ANIM_WAVE = 35;
	public static final int ANIM_HELLBLAST = 36;
	public static final int ANIM_PROMINENCE = 37;
	public static final int ANIM_FORGE = 38;
	/** The Hellblast beam firing (the charge is {@link #ANIM_HELLBLAST}). */
	public static final int ANIM_BEAM = 39;

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
		return until(p, V_CLOAK);
	}

	public static boolean dashing(Player p) {
		return until(p, V_DASH);
	}

	public static boolean bursting(Player p) {
		return until(p, V_BURST);
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

	/** Hellblast charge 0..1 of its maximum (0 when not charging). */
	public static float charge(Player p, float partial) {
		long at = val(p, V_CHARGE);
		if (at == 0L || p.level() == null) {
			return 0f;
		}
		float t = (p.level().getGameTime() - at + partial) / HellflameConfig.HELLBLAST_MAX_CHARGE;
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

	/** The Prominence Burn ultimate is running (ignition or comet). */
	public static boolean prominence(Player p) {
		return is(p) && val(p, V_PROM) != 0L;
	}

	/** Ticks since the ultimate ignited (partial included), or -1. */
	public static float prominenceAge(Player p, float partial) {
		long at = val(p, V_PROM);
		return at == 0L || p.level() == null ? -1f : p.level().getGameTime() - at + partial;
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

	/** Flame size now (1; the ultimate's sun is bigger). Kept for the renderers. */
	public static double size(Player p) {
		return 1.0;
	}
}
