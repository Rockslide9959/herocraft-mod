package com.projecthero.mod.miraculous.queenbee;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Queen Bee's synced values (the keys of {@code MiraculousState#vals} / {@code vecs} her kit owns, all prefixed
 * {@code qb_}) and the small client-safe reads the HUD, the poses and the renderers share. Timers are absolute game times.
 * "Pollen, buzz on!" in full plays the long transformation ({@link QueenBeeSequence}); "Buzz on!" alone is the quick one.
 */
public final class QueenBee {
	/** Top Strike: the combo's window for the next press runs until (the top is out on its string). */
	public static final String V_COMBO = "qb_combo_until";
	/** Top Throw: the top is in flight until; where it is now in {@link #X_TOP}. */
	public static final String V_THROW = "qb_throw_until";
	public static final String X_TOP = "qb_top_at";
	/** Spinning Shield: spinning until. */
	public static final String V_SHIELD = "qb_shield_until";
	/** Top Tangle: a creature is wrapped up until; its entity id + 1 in {@link #V_TANGLE_ID}. */
	public static final String V_TANGLE = "qb_tangle_until";
	public static final String V_TANGLE_ID = "qb_tangle_id";
	/** Top Swing: hooked until; the hook in {@link #X_SWING}. */
	public static final String V_SWING = "qb_swing_until";
	public static final String X_SWING = "qb_swing_hook";
	/** Top Copter: flying until (0 = not). */
	public static final String V_COPTER = "qb_copter_until";
	/** Stinger Dive: 1 rising, 2 diving (0 = not); the dive started at {@link #V_DIVE_AT}. */
	public static final String V_DIVE = "qb_dive";
	public static final String V_DIVE_AT = "qb_dive_at";
	/** Venom: armed until (the top glows; her next hit paralyses). */
	public static final String V_VENOM = "qb_venom_until";
	/** Venom landed: the paralysed creature (entity id + 1) and until when. */
	public static final String V_PARALYSED_ID = "qb_paralysed_id";
	public static final String V_PARALYSED = "qb_paralysed_until";
	/** Hive Sense: 1 while on (the outlines are drawn by her own client only). */
	public static final String V_HIVE = "qb_hive";
	/** Stinger Volley: the top hovers and fires until; the last stinger's target point in {@link #X_STING}, fired at {@link #V_STING_AT}. */
	public static final String V_VOLLEY = "qb_volley_until";
	public static final String X_STING = "qb_sting_to";
	public static final String V_STING_AT = "qb_sting_at";

	// the pose ids (180..199, see MiraculousState)
	public static final int ANIM_STRIKE = 180;
	public static final int ANIM_STRIKE_2 = 181;
	public static final int ANIM_STRIKE_3 = 182;
	public static final int ANIM_THROW = 183;
	public static final int ANIM_SHIELD = 184;
	public static final int ANIM_TANGLE = 185;
	public static final int ANIM_SWING = 186;
	public static final int ANIM_COPTER = 187;
	public static final int ANIM_DIVE = 188;
	public static final int ANIM_VENOM = 189;
	public static final int ANIM_VENOM_TOUCH = 190;
	public static final int ANIM_HIVE = 191;
	public static final int ANIM_COMMAND = 192;
	public static final int ANIM_STING = 193;
	public static final int ANIM_VOLLEY = 194;

	private QueenBee() {
	}

	/** Client-safe: transformed as Queen Bee. */
	public static boolean is(Player player) {
		return Miraculous.transformedAs(player, Kwami.POLLEN);
	}

	public static int transformTicks(MiraculousState s) {
		return Miraculous.formTicks(s);
	}

	/** Client-safe: a timer value still in the future. */
	public static boolean running(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s != null && s.val(key) > player.level().getGameTime();
	}

	public static long val(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s == null ? 0L : s.val(key);
	}

	public static @Nullable Vec3 vec(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s == null ? null : Miraculous.vec(s, key);
	}

	public static boolean shielding(Player player) {
		return is(player) && running(player, V_SHIELD);
	}

	public static boolean copter(Player player) {
		return is(player) && val(player, V_COPTER) != 0L;
	}

	public static boolean venomArmed(Player player) {
		return is(player) && running(player, V_VENOM);
	}

	public static boolean hiveSense(Player player) {
		return is(player) && val(player, V_HIVE) != 0L;
	}

	public static boolean throwing(Player player) {
		return is(player) && running(player, V_THROW);
	}

	public static boolean volley(Player player) {
		return is(player) && running(player, V_VOLLEY);
	}

	/** Client-safe: the top is off her hip (thrown, hovering for the volley, or spun overhead). */
	public static boolean topAway(Player player) {
		return throwing(player) || volley(player);
	}
}
