package com.projecthero.mod.miraculous.catnoir;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.6: Cat Noir's ids and his CLIENT-SAFE reads of the shared {@link MiraculousState} (the kit keeps everything it
 * needs to show in the state's {@code vals} / {@code vecs}, so every viewer sees the same baton, pose and hand).
 *
 * <pre>
 *   R  Baton Strike      Shift+R  Low Sweep
 *   G  Baton Extend      Shift+G  Baton Throw
 *   X  Pole Vault        Shift+X  Rooftop Run (toggle)
 *   Z  Baton Spin        Shift+Z  CATACLYSM
 *   V  Cat Leap          Shift+V  Pounce Strike
 *   C  Night Vision      Shift+C  Cat Reflexes
 * </pre>
 */
public final class CatNoir {
	// ---------------------------------------------------------------- ability ids (lang: projecthero.miraculous.ability.<id>)
	public static final String STRIKE = "cat_noir_baton_strike";
	public static final String SWEEP = "cat_noir_low_sweep";
	public static final String EXTEND = "cat_noir_baton_extend";
	public static final String THROW = "cat_noir_baton_throw";
	public static final String VAULT = "cat_noir_pole_vault";
	public static final String ROOFTOP = "cat_noir_rooftop_run";
	public static final String SPIN = "cat_noir_baton_spin";
	public static final String CATACLYSM = "cat_noir_cataclysm";
	public static final String LEAP = "cat_noir_cat_leap";
	public static final String POUNCE = "cat_noir_pounce_strike";
	public static final String NIGHT_VISION = "cat_noir_night_vision";
	public static final String REFLEXES = "cat_noir_cat_reflexes";

	// ---------------------------------------------------------------- animation ids (40..59, Miraculous.setAnim)
	public static final int ANIM_STRIKE = 40;
	public static final int ANIM_SWEEP = 41;
	public static final int ANIM_EXTEND = 42;
	public static final int ANIM_THROW = 43;
	public static final int ANIM_VAULT = 44;
	public static final int ANIM_SPIN = 45;
	public static final int ANIM_LEAP = 46;
	public static final int ANIM_POUNCE = 47;
	public static final int ANIM_NIGHT_VISION = 48;
	public static final int ANIM_REFLEXES = 49;
	public static final int ANIM_DODGE_LEFT = 50;
	public static final int ANIM_DODGE_RIGHT = 51;
	public static final int ANIM_CATACLYSM_CALL = 52;
	public static final int ANIM_CATACLYSM_TOUCH = 53;
	public static final int ANIM_CATCH = 54;
	public static final int ANIM_ROOFTOP = 55;

	// ---------------------------------------------------------------- synced vals / vecs keys
	/** Night Vision on (1). */
	public static final String V_NIGHT = "cn_night";
	/** Rooftop Run on (1). */
	public static final String V_ROOFTOP = "cn_rooftop";
	/** CATACLYSM charged until this game time. */
	public static final String V_CATACLYSM = "cn_cataclysm";
	/** Cataclysm has been used this transformation (1): the baton is rotted black. */
	public static final String V_ROTTED = "cn_rotted";
	/** The thrown baton's entity id + 1 (0 = in hand). */
	public static final String V_THROWN = "cn_thrown";
	/** Baton Extend: how far the staff reaches this time, hundredths of a block. */
	public static final String V_EXTEND_LENGTH = "cn_extend_len";
	/** Cat Reflexes: running until this game time, with this many dodges left. */
	public static final String V_REFLEX_UNTIL = "cn_reflex_until";
	public static final String V_REFLEX_LEFT = "cn_reflex_left";
	/** Baton Extend: the direction it shot out in. */
	public static final String VEC_EXTEND_DIR = "cn_extend_dir";
	/** Pole Vault: where the baton is planted. */
	public static final String VEC_VAULT_BASE = "cn_vault_base";

	private CatNoir() {
	}

	/** Transformed as Cat Noir (client-safe). */
	public static boolean active(Player player) {
		return Miraculous.transformedAs(player, Kwami.PLAGG);
	}

	/** A kit value of a transformed Cat Noir (0 otherwise). Client-safe. */
	public static long val(Player player, String key) {
		if (!active(player)) {
			return 0L;
		}
		MiraculousState s = Miraculous.peek(player);
		return s == null ? 0L : s.val(key);
	}

	public static boolean nightVision(Player player) {
		return val(player, V_NIGHT) != 0L;
	}

	public static boolean rooftop(Player player) {
		return val(player, V_ROOFTOP) != 0L;
	}

	/** The hand crackles with CATACLYSM (charged, untouched, not expired). */
	public static boolean charged(Player player) {
		long until = val(player, V_CATACLYSM);
		return until > 0L && player.level() != null && player.level().getGameTime() < until;
	}

	public static boolean rotted(Player player) {
		return val(player, V_ROTTED) != 0L;
	}

	/** The thrown baton's entity id, or -1 while it is with him. */
	public static int thrownId(Player player) {
		long v = val(player, V_THROWN);
		return v == 0L ? -1 : (int) (v - 1L);
	}

	public static int reflexesLeft(Player player) {
		long until = val(player, V_REFLEX_UNTIL);
		if (until <= 0L || player.level() == null || player.level().getGameTime() >= until) {
			return 0;
		}
		return (int) val(player, V_REFLEX_LEFT);
	}

	/**
	 * Rooftop Run: is he against a wall (so he climbs it like a ladder: walk into it to go up, Sneak to cling)? A thin
	 * probe round his box, off the floor and ceiling. Client-safe; asked from {@code LivingEntity#onClimbable}.
	 */
	public static boolean climbing(Player player) {
		if (player.isSpectator() || player.getAbilities().flying || !rooftop(player)) {
			return false;
		}
		AABB b = player.getBoundingBox();
		AABB probe = new AABB(b.minX - 0.08, b.minY + 0.25, b.minZ - 0.08, b.maxX + 0.08, b.maxY - 0.2, b.maxZ + 0.08);
		return !player.level().noCollision(player, probe);
	}
}
