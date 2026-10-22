package com.projecthero.mod.quirk.darkshadow;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Dark Shadow's synced keys (in {@code QuirkState.vals / vecs}, so every client draws the same creature), its pose
 * ids (80..99) and the client-safe reads the renderer, the poses and the HUD use. Timers are absolute game times; entity ids
 * are stored as {@code id + 1} (0 = none).
 */
public final class DarkShadow {
	private DarkShadow() {
	}

	// ---------------------------------------------------------------- vals
	/** The darkness it feels, 0..1000 (smoothed on the server; drives its size on every client). */
	public static final String V_DARK = "ds_dark";
	/** Berserk: 1 = the meter hit 100, 2 = on purpose (C). The overload itself is {@code QuirkState.overloadUntil}. */
	public static final String V_BERSERK = "ds_berserk";
	/** Black Ankh (armour mode) on. */
	public static final String V_ANKH = "ds_ankh";
	/** Abyssal Black Ankh: the wings are out (gliding flight). */
	public static final String V_ABYSSAL = "ds_abyssal";
	/** Shadow Shield held since. */
	public static final String V_SHIELD = "ds_shield";
	/** Recalled (tucked away). */
	public static final String V_RECALL = "ds_recall";
	/** Where it is reaching to ({@link #VEC_REACH}) since. */
	public static final String V_REACH_AT = "ds_reach_at";
	public static final String VEC_REACH = "ds_reach";
	/** Shadow Grab: the held creature (id + 1). */
	public static final String V_GRAB = "ds_grab";
	/** Dark Fang running until. */
	public static final String V_FANG = "ds_fang";
	/** The Black Ankh dash running until. */
	public static final String V_DASH = "ds_dash";
	/** Shadow Scout: launched at; {@link #VEC_SCOUT} is where it flies to. */
	public static final String V_SCOUT = "ds_scout";
	public static final String VEC_SCOUT = "ds_scout_at";
	/** Fetch: carrying you (1) or items (2) since {@link #V_FETCH_AT}. */
	public static final String V_FETCH = "ds_fetch";
	public static final String V_FETCH_AT = "ds_fetch_at";
	/** The Light Flare shines until. */
	public static final String V_FLARE = "ds_flare";

	// ---------------------------------------------------------------- poses / clips (animBase 80)
	public static final int ANIM_STRIKE = 80;
	public static final int ANIM_FANG = 81;
	public static final int ANIM_GRAB = 82;
	public static final int ANIM_SLAM = 83;
	public static final int ANIM_SHIELD = 84;
	public static final int ANIM_ANKH = 85;
	public static final int ANIM_ABYSSAL = 86;
	public static final int ANIM_FETCH = 87;
	public static final int ANIM_SCOUT = 88;
	public static final int ANIM_NOVA = 89;
	public static final int ANIM_RECALL = 90;
	public static final int ANIM_SUMMON = 91;
	public static final int ANIM_BERSERK = 92;
	public static final int ANIM_FLARE = 93;
	/** Light calmed it (no flare): it recoils and whimpers. */
	public static final int ANIM_CALM = 94;
	/** One of its own strikes while berserk. */
	public static final int ANIM_FRENZY_HIT = 95;
	public static final int ANIM_ANKH_DASH = 96;

	// ---------------------------------------------------------------- client-safe reads

	public static boolean is(Player p) {
		return Quirks.activeAs(p, Quirk.DARK_SHADOW);
	}

	public static long val(Player p, String key) {
		QuirkState s = Quirks.peek(p);
		return s == null ? 0L : s.val(key);
	}

	public static boolean running(Player p, String key) {
		return val(p, key) > p.level().getGameTime();
	}

	public static boolean on(Player p, String key) {
		return val(p, key) != 0L;
	}

	public static boolean berserk(Player p) {
		return on(p, V_BERSERK) && Quirks.overloaded(p);
	}

	public static boolean ankh(Player p) {
		return on(p, V_ANKH);
	}

	public static boolean abyssal(Player p) {
		return on(p, V_ABYSSAL);
	}

	public static boolean shielding(Player p) {
		return on(p, V_SHIELD);
	}

	public static boolean recalled(Player p) {
		return on(p, V_RECALL);
	}

	/** Darkness 0 (bright) .. 1 (pitch dark), as synced. */
	public static float darkness(Player p) {
		return Mth.clamp(val(p, V_DARK) / 1000f, 0f, 1f);
	}

	public static @Nullable Vec3 reach(Player p) {
		QuirkState s = Quirks.peek(p);
		return s == null ? null : Quirks.vec(s, VEC_REACH);
	}

	public static @Nullable Vec3 scoutTarget(Player p) {
		QuirkState s = Quirks.peek(p);
		return s == null ? null : Quirks.vec(s, VEC_SCOUT);
	}

	/** The Shadow meter 0..1 as the client sees it. */
	public static float shadow(Player p) {
		return Quirks.clientMeter(p) / 100f;
	}

	/** Its model scale for this darkness (client and server agree; Berserk overrides). */
	public static float size(float darkness, boolean berserk) {
		DarkShadowConfig.Light l = DarkShadowConfig.get().light;
		return berserk ? DarkShadowConfig.get().berserk.size : Mth.lerp(darkness, l.sizeMin, l.sizeMax);
	}
}
