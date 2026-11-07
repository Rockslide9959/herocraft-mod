package com.projecthero.mod.miraculous.ryuko;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Ryuko's synced values (the keys of {@code MiraculousState#vals} / {@code vecs} her kit owns, all prefixed
 * {@code ry_}) and the small client-safe reads her HUD, poses and renderers share. Timers are absolute game times. "Longg,
 * bring the storm!" in full plays the long transformation ({@link RyukoSequence}); "Bring the storm!" alone is the quick one.
 */
public final class Ryuko {
	public static final int WIND = 0;
	public static final int WATER = 1;
	public static final int LIGHTNING = 2;

	/** The element she is on: {@link #WIND}, {@link #WATER} or {@link #LIGHTNING}. */
	public static final String V_ELEMENT = "ry_element";
	/** Blade Combo: which swing is next, and until when the next press continues it. */
	public static final String V_COMBO = "ry_combo";
	public static final String V_COMBO_UNTIL = "ry_combo_until";
	/** Fencing Lunge: running until. */
	public static final String V_LUNGE = "ry_lunge_until";
	/** Whirlwind: spinning until; where in {@link #X_WHIRL}. */
	public static final String V_WHIRL = "ry_whirl_until";
	public static final String X_WHIRL = "ry_whirl_at";
	/** Water dash: sliding until. */
	public static final String V_SLIDE = "ry_slide_until";
	/** Storm Rise: gliding until. */
	public static final String V_GLIDE = "ry_glide_until";
	/** Sword Throw: the blade is out until; where it is now in {@link #X_SWORD}. */
	public static final String V_THROW = "ry_throw_until";
	public static final String X_SWORD = "ry_sword_at";
	/** Dragon Form: until (0 = not). */
	public static final String V_FORM = "ry_form_until";
	/** Storm Guard: until, and the element it was raised in. */
	public static final String V_GUARD = "ry_guard_until";
	public static final String V_GUARD_ELEMENT = "ry_guard_element";
	/** Parry: guarding until. */
	public static final String V_PARRY = "ry_parry_until";
	/** A parry landed: Riposte is open until. */
	public static final String V_RIPOSTE = "ry_riposte_until";

	// the pose ids (200..219, see MiraculousState)
	public static final int ANIM_STRIKE = 200;
	public static final int ANIM_STRIKE_2 = 201;
	public static final int ANIM_STRIKE_3 = 202;
	public static final int ANIM_LUNGE = 203;
	public static final int ANIM_ELEMENT = 204;
	public static final int ANIM_BURST = 205;
	public static final int ANIM_THROW = 206;
	public static final int ANIM_PARRY = 207;
	public static final int ANIM_RIPOSTE = 208;
	public static final int ANIM_FORM = 209;

	private Ryuko() {
	}

	public static boolean is(Player player) {
		return Miraculous.transformedAs(player, Kwami.LONGG);
	}

	private static long val(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s == null ? 0L : s.val(key);
	}

	public static int element(Player player) {
		return (int) val(player, V_ELEMENT);
	}

	public static boolean swordAway(Player player) {
		return val(player, V_THROW) > player.level().getGameTime();
	}

	public static boolean inForm(Player player) {
		return val(player, V_FORM) > player.level().getGameTime();
	}

	public static boolean gliding(Player player) {
		return val(player, V_GLIDE) > player.level().getGameTime();
	}

	public static boolean guarding(Player player) {
		return val(player, V_GUARD) > player.level().getGameTime();
	}

	public static boolean parrying(Player player) {
		return val(player, V_PARRY) > player.level().getGameTime();
	}

	/** Her element's colour, RGB. */
	public static int color(int element) {
		return switch (element) {
			case WATER -> 0x3FA8FF;
			case LIGHTNING -> 0xFFE45A;
			default -> 0xD8F4EC;
		};
	}
}
