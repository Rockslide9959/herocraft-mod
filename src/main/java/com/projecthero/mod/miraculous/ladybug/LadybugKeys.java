package com.projecthero.mod.miraculous.ladybug;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Ladybug's synced keys in the core's {@code MiraculousState} ({@code vals} = numbers, {@code vecs} = points)
 * and her animation ids (20..39), plus CLIENT-SAFE readers -- the renderers, poses and HUD read only these. Times are
 * absolute game times; entity ids are stored +1 (0 means "none").
 */
public final class LadybugKeys {
	private LadybugKeys() {
	}

	// ---------------------------------------------------------------- ability ids
	public static final String STRIKE = "ladybug_yoyo_strike";
	public static final String RICOCHET = "ladybug_ricochet";
	public static final String BIND = "ladybug_yoyo_bind";
	public static final String FLING = "ladybug_lasso_fling";
	public static final String SWING = "ladybug_yoyo_swing";
	public static final String ZIP = "ladybug_zip_line";
	public static final String SHIELD = "ladybug_spinning_shield";
	public static final String LUCKY_CHARM = "ladybug_lucky_charm";
	public static final String VAULT = "ladybug_pole_vault";
	public static final String WIRE = "ladybug_trip_wire";
	public static final String SCAN = "ladybug_yoyo_scan";
	public static final String PHONE = "ladybug_yoyo_phone";

	// ---------------------------------------------------------------- animation ids (MiraculousState.animId)
	public static final int ANIM_STRIKE = 20;
	public static final int ANIM_RICOCHET = 21;
	public static final int ANIM_BIND = 22;
	public static final int ANIM_YANK = 23;
	public static final int ANIM_FLING = 24;
	public static final int ANIM_ZIP = 25;
	public static final int ANIM_CHARM = 26;
	public static final int ANIM_MIRACULOUS = 27;
	public static final int ANIM_VAULT = 28;
	public static final int ANIM_WIRE = 29;
	public static final int ANIM_SCAN = 30;
	public static final int ANIM_PHONE = 31;
	public static final int ANIM_SWING = 32;

	// ---------------------------------------------------------------- vals
	/** Spinning Shield: game time the hold began (0 = down). */
	public static final String V_SHIELD = "lb_shield";
	/** 1 = swinging, 2 = zipping. */
	public static final String V_LINE = "lb_line";
	/** Rope length, millimetres. */
	public static final String V_ROPE = "lb_rope";
	public static final String V_LINE_AT = "lb_line_at";
	/** Bound entity id + 1, and when the bind ends. */
	public static final String V_BIND = "lb_bind";
	public static final String V_BIND_UNTIL = "lb_bind_until";
	/** Flung entity id + 1 (the string follows it while the anim runs). */
	public static final String V_FLUNG = "lb_flung";
	public static final String V_WIRE_UNTIL = "lb_wire_until";
	/** Which Lucky Charm is out (see {@link LuckyCharm.Kind}); 0 none. */
	public static final String V_CHARM = "lb_charm";
	/** 1 once the Lucky Charm has been used this transformation. */
	public static final String V_CHARM_USED = "lb_charm_used";
	/** The charm's item entity id + 1 while it lies on the ground. */
	public static final String V_CHARM_ITEM = "lb_charm_item";
	public static final String V_VISION_UNTIL = "lb_vision_until";
	/** Miraculous Ladybug: game time it was cast (every client starts its swarm from it). */
	public static final String V_ML_AT = "lb_ml_at";
	/** 1 while the parachute canopy is open. */
	public static final String V_CHUTE = "lb_chute";

	// ---------------------------------------------------------------- vecs
	public static final String P_HOOK = "lb_hook";
	public static final String P_TIP = "lb_tip";
	/** Ricochet: the bounce points, x y z x y z ... */
	public static final String P_RICO = "lb_rico";
	public static final String P_VAULT = "lb_vault";
	public static final String P_WIRE_A = "lb_wire_a";
	public static final String P_WIRE_B = "lb_wire_b";
	public static final String P_ML = "lb_ml";

	// ---------------------------------------------------------------- client-safe reads

	/** Her state, or null unless she is transformed as Ladybug. */
	public static @Nullable MiraculousState state(Player player) {
		if (!Miraculous.transformedAs(player, Kwami.TIKKI)) {
			return null;
		}
		return Miraculous.peek(player);
	}

	public static long now(Player player) {
		return player.level().getGameTime();
	}

	public static boolean shielding(Player player) {
		MiraculousState s = state(player);
		return s != null && s.val(V_SHIELD) > 0L;
	}

	/** 0 none, 1 swinging, 2 zipping. */
	public static int line(Player player) {
		MiraculousState s = state(player);
		return s == null ? 0 : (int) s.val(V_LINE);
	}

	public static @Nullable Vec3 hook(Player player) {
		MiraculousState s = state(player);
		return s == null || s.val(V_LINE) == 0L ? null : Miraculous.vec(s, P_HOOK);
	}

	public static double rope(MiraculousState s) {
		return s.val(V_ROPE) / 1000.0;
	}

	/** The bound entity's id, or -1. */
	public static int bound(Player player) {
		MiraculousState s = state(player);
		if (s == null || s.val(V_BIND) == 0L || s.val(V_BIND_UNTIL) < now(player)) {
			return -1;
		}
		return (int) (s.val(V_BIND) - 1L);
	}

	public static boolean wireUp(Player player) {
		MiraculousState s = state(player);
		return s != null && s.val(V_WIRE_UNTIL) > now(player);
	}

	public static boolean vision(Player player) {
		MiraculousState s = state(player);
		return s != null && s.val(V_VISION_UNTIL) > now(player);
	}

	/** The ricochet's bounce points. */
	public static List<Vec3> points(MiraculousState s, String key) {
		List<Double> v = s.vec(key);
		List<Vec3> out = new ArrayList<>();
		for (int i = 0; i + 2 < v.size(); i += 3) {
			out.add(new Vec3(v.get(i), v.get(i + 1), v.get(i + 2)));
		}
		return out;
	}

	/** Ticks since the running anim began, or -1 if {@code animId} is not the one running. */
	public static float animAge(MiraculousState s, int animId, long now, float partial) {
		return s.animId == animId ? (now - s.animStart) + partial : -1f;
	}
}
