package com.projecthero.mod.quirk.overhaul;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Overhaul Quirk's synced keys (its {@code vals}) and pose ids, and the client-safe reads of them. Pose
 * ids are {@code Quirk.OVERHAUL.animBase} (40) .. 59.
 */
public final class Overhaul {
	private Overhaul() {
	}

	/** Fusion runs until this game time (0 = not fused). */
	public static final String V_FUSION = "ov_fusion";
	/** The Wall Burst stands until (HUD box). */
	public static final String V_WALL = "ov_wall";
	/** Disassemble Terrain's pits hold their prisoners until (HUD box). */
	public static final String V_PIT = "ov_pit";
	/** Focus was filled on the first tick (a new Quirk starts focused). */
	public static final String V_INIT = "ov_init";
	/** v0.16.7 revamp: 1 = the gloves are on (touches don't trigger). */
	public static final String V_GLOVES = "ov_gloves";
	/** v0.16.7 revamp: the Overhaul Strike dash runs until (the pose holds the reach). */
	public static final String V_STRIKE = "ov_strike";
	/** v0.16.7 revamp: Terrain Surf carries him until (X again ends it). */
	public static final String V_SURF = "ov_surf";

	public static final int ANIM_BASE = Quirk.OVERHAUL.animBase;
	public static final int ANIM_DISASSEMBLE = ANIM_BASE;
	public static final int ANIM_REASSEMBLE = ANIM_BASE + 1;
	public static final int ANIM_SPIKE_WAVE = ANIM_BASE + 2;
	public static final int ANIM_SPIKE_FIELD = ANIM_BASE + 3;
	public static final int ANIM_WALL = ANIM_BASE + 4;
	public static final int ANIM_PIT = ANIM_BASE + 5;
	public static final int ANIM_STRIKE = ANIM_BASE + 6;
	public static final int ANIM_DISARM = ANIM_BASE + 7;
	public static final int ANIM_FUSION = ANIM_BASE + 8;
	public static final int ANIM_PILLAR = ANIM_BASE + 9;
	public static final int ANIM_GLOVES_OFF = ANIM_BASE + 10;
	public static final int ANIM_GLOVES_ON = ANIM_BASE + 11;
	public static final int ANIM_SURF = ANIM_BASE + 12;
	public static final int ANIM_FUSION_END = ANIM_BASE + 13;
	public static final int ANIM_TOUCH = ANIM_BASE + 14;
	public static final int ANIM_LAST = ANIM_BASE + 19;

	/** Client-safe: holds Overhaul (and is not Erased). */
	public static boolean is(Player player) {
		return Quirks.activeAs(player, Quirk.OVERHAUL);
	}

	/** Client-safe: a timer val is still running. */
	public static boolean running(Player player, String key) {
		QuirkState s = Quirks.peek(player);
		return s != null && s.hasPower && Quirk.OVERHAUL.id.equals(s.quirk) && s.val(key) > player.level().getGameTime();
	}

	/** Client-safe: in the Fusion form. */
	public static boolean fused(Player player) {
		return running(player, V_FUSION);
	}

	/** Client-safe: the gloves are on (the touch is held back). */
	public static boolean gloved(Player player) {
		QuirkState s = Quirks.peek(player);
		return s != null && s.hasPower && Quirk.OVERHAUL.id.equals(s.quirk) && s.val(V_GLOVES) != 0L;
	}
}
