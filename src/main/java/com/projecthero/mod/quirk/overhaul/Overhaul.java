package com.projecthero.mod.quirk.overhaul;

import com.projecthero.mod.hero.AbilitySlot;
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
	/** Matter was filled on the first tick (a new Quirk starts full). */
	public static final String V_INIT = "ov_init";
	/** v0.16.7 (retired in v0.17.1): the Gloves toggle. Dropped from a save the first time the kit ticks. */
	public static final String V_GLOVES = "ov_gloves";
	/** v0.17.1: the gloves are off until this game time (a move tugged them off; bare-hand punches disassemble). */
	public static final String V_BARE = "ov_bare";
	/** v0.16.7 revamp: the Overhaul Strike dash runs until (the pose holds the reach). */
	public static final String V_STRIKE = "ov_strike";
	/** v0.16.7 (user): Terrain Surf is carrying him (Long.MAX_VALUE while X is held; 0 = off). */
	public static final String V_SURF = "ov_surf";
	/** v0.17.1: a held key's slot (ordinal + 1) and when it went down (the HUD's hold ring, the hold poses). */
	public static final String V_HOLD_SLOT = "ov_hold_slot";
	public static final String V_HOLD_AT = "ov_hold_at";
	/** v0.17.1: Thorn Forest is being aimed (G held past the tap) since. */
	public static final String V_THORN_AIM = "ov_thorn_aim";
	/** v0.17.1: Ground Shift carries him until. */
	public static final String V_SHIFT = "ov_shift";
	/** v0.17.1: Total Disassembly runs until. */
	public static final String V_ULT = "ov_ult";

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
	/** v0.17.1: the glove tug (a move pulls the right glove off). */
	public static final int ANIM_GLOVES_OFF = ANIM_BASE + 10;
	/** v0.17.1: Total Disassembly (both palms on the ground). Was the gloves-on pose. */
	public static final int ANIM_ULTIMATE = ANIM_BASE + 11;
	public static final int ANIM_SURF = ANIM_BASE + 12;
	public static final int ANIM_FUSION_END = ANIM_BASE + 13;
	public static final int ANIM_TOUCH = ANIM_BASE + 14;
	/** v0.17.1: Thorn Forest -- kneeling, palms pressed to the ground, rising slowly (held while it is aimed). */
	public static final int ANIM_THORN = ANIM_BASE + 15;
	/** v0.17.1: Shrapnel Burst -- a palm swept upward. */
	public static final int ANIM_SHRAPNEL = ANIM_BASE + 16;
	/** v0.17.1: Ground Shift -- standing still, carried by the floor. */
	public static final int ANIM_SHIFT = ANIM_BASE + 17;
	/** v0.17.1: Mend -- the item cupped in both hands. */
	public static final int ANIM_MEND = ANIM_BASE + 18;
	/** v0.17.1: Restore -- the held palms-out sweep. */
	public static final int ANIM_RESTORE = ANIM_BASE + 19;
	public static final int ANIM_LAST = ANIM_BASE + 19;

	/** Client-safe: holds Overhaul (and is not Erased). */
	public static boolean is(Player player) {
		return Quirks.activeAs(player, Quirk.OVERHAUL);
	}

	private static long val(Player player, String key) {
		QuirkState s = Quirks.peek(player);
		return s != null && s.hasPower && Quirk.OVERHAUL.id.equals(s.quirk) ? s.val(key) : 0L;
	}

	/** Client-safe: a timer val is still running. */
	public static boolean running(Player player, String key) {
		return val(player, key) > player.level().getGameTime();
	}

	/** Client-safe: in the Fusion form. */
	public static boolean fused(Player player) {
		return running(player, V_FUSION);
	}

	/** Client-safe: the gloves are on (the default; a move tugs them off for 8 s). */
	public static boolean gloved(Player player) {
		return !running(player, V_BARE);
	}

	/** Client-safe: the Thorn Forest is being aimed (G held). */
	public static boolean thornAiming(Player player) {
		return val(player, V_THORN_AIM) != 0L;
	}

	/** Client-safe: Total Disassembly runs. */
	public static boolean ultimate(Player player) {
		return running(player, V_ULT);
	}

	/** Client-safe: how far a held key is toward its hold move (0..1), or -1 when that key isn't held. */
	public static float holdCharge(Player player, AbilitySlot slot, int ticks) {
		if (val(player, V_HOLD_SLOT) != slot.ordinal() + 1L) {
			return -1f;
		}
		float age = player.level().getGameTime() - val(player, V_HOLD_AT);
		return Math.max(0f, Math.min(1f, age / ticks));
	}
}
