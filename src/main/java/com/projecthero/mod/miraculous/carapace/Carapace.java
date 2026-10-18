package com.projecthero.mod.miraculous.carapace;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Carapace's synced values (the keys of {@code MiraculousState#vals} / {@code vecs} his kit owns) and the small
 * client-safe reads the HUD, the poses and the renderers share. Timers are absolute game times.
 */
public final class Carapace {
	/** Shield Bash: charging until. */
	public static final String V_BASH = "cp_bash_until";
	/** 1 while the Shield is thrown (not on his back, not on his arm). */
	public static final String V_SHIELD_OUT = "cp_shield_out";
	/** Taunt: drawing the aggro (and 30% less damage) until. */
	public static final String V_TAUNT = "cp_taunt_until";
	/** 1 while Shield Surf is on (the shield under his feet). */
	public static final String V_SURF = "cp_surf";
	/** Shell Roll: rolling (and untouchable) until. */
	public static final String V_ROLL = "cp_roll_until";
	/** Shell Guard: held up since this game time (0 = down). */
	public static final String V_GUARD = "cp_guard_since";
	/** Shelter: the dome stands until; its centre is {@link #X_SHELTER}. */
	public static final String V_SHELTER = "cp_shelter_until";
	public static final String X_SHELTER = "cp_shelter_at";
	/** Shell Barrier: the wall stands until; its bottom centre {@link #X_BARRIER}, its facing (flat unit vector) {@link #X_BARRIER_DIR}. */
	public static final String V_BARRIER = "cp_barrier_until";
	public static final String X_BARRIER = "cp_barrier_at";
	public static final String X_BARRIER_DIR = "cp_barrier_dir";
	/** Protect: the bubble holds until; on entity id {@link #V_PROTECT_ID} minus one. */
	public static final String V_PROTECT = "cp_protect_until";
	public static final String V_PROTECT_ID = "cp_protect_id";
	/** Steadfast runs until. */
	public static final String V_STEADFAST = "cp_steadfast_until";
	/** Tidal Swim runs until. */
	public static final String V_TIDAL = "cp_tidal_until";

	// the pose ids (100..119, see MiraculousState)
	public static final int ANIM_BASH = 100;
	public static final int ANIM_THROW = 101;
	public static final int ANIM_CATCH = 102;
	public static final int ANIM_SLAM = 103;
	public static final int ANIM_TAUNT = 104;
	public static final int ANIM_SURF = 105;
	public static final int ANIM_ROLL = 106;
	public static final int ANIM_SHELTER = 107;
	public static final int ANIM_BARRIER = 108;
	public static final int ANIM_PROTECT = 109;
	public static final int ANIM_STEADFAST = 110;
	public static final int ANIM_TIDAL = 111;

	private Carapace() {
	}

	/** Client-safe: transformed as Carapace. */
	public static boolean is(Player player) {
		return Miraculous.transformedAs(player, Kwami.WAYZZ);
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

	/** Client-safe: the Shield is thrown. */
	public static boolean shieldOut(Player player) {
		return val(player, V_SHIELD_OUT) != 0L;
	}

	public static boolean surfing(Player player) {
		return is(player) && val(player, V_SURF) != 0L;
	}

	public static boolean guarding(Player player) {
		return is(player) && val(player, V_GUARD) != 0L;
	}

	public static boolean rolling(Player player) {
		return is(player) && running(player, V_ROLL);
	}
}
