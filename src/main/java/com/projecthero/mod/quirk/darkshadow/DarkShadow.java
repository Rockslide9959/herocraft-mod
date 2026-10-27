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
	// v0.17.1 revamp
	/** Abyssal Rampage's three swings run until. */
	public static final String V_RAMPAGE = "ds_rampage";
	/** Shadow Lunge yanks you until; {@link #VEC_REACH} is where. */
	public static final String V_LUNGE = "ds_lunge";
	/** Shadow Toss: G is held past the grab and it carries the creature (1) / threw it (2). */
	public static final String V_TOSS = "ds_toss";
	/** Unleash (controlled Berserk, Shift+C) runs until. */
	public static final String V_UNLEASH = "ds_unleash";
	/** Abyssal Black Body (the ultimate) runs until. */
	public static final String V_BLACKBODY = "ds_blackbody";
	/** A claw dive in Black Body runs until. */
	public static final String V_DIVE = "ds_dive";
	/** After Black Body it stays hidden until (N won't call it out). */
	public static final String V_HIDE_LOCK = "ds_hide_lock";
	/** X is held (Black Fallen Angel's hold) since. */
	public static final String V_HOLD_X = "ds_hold_x";
	/** Shadow Scout's marks end at; {@link #V_MARK_PREFIX}0..7 are the marked creatures (id + 1). */
	public static final String V_MARK_UNTIL = "ds_mark_until";
	public static final String V_MARK_PREFIX = "ds_mk";
	public static final int MAX_MARKS = 8;

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
	// v0.17.1 revamp (the 80..99 block is full past 99: the rest sit at 180+, clear of every other Quirk's ids)
	/** C: Abyssal Rampage -- Tokoyami braced, the shadow swinging over his head. */
	public static final int ANIM_RAMPAGE = 97;
	/** X tap: Shadow Lunge -- a point, and he is yanked after it. */
	public static final int ANIM_LUNGE = 98;
	/** Shadow Toss: the throw. */
	public static final int ANIM_TOSS = 99;
	/** The fall catch. */
	public static final int ANIM_CATCH = 180;
	/** Abyssal Black Body: the merge. */
	public static final int ANIM_BLACKBODY = 181;
	/** A claw dive in Black Body. */
	public static final int ANIM_DIVE = 182;
	/** Shift+C: Unleash -- the command that lets it loose. */
	public static final int ANIM_UNLEASH = 183;

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

	/** v0.17.1: Abyssal Black Body (the ultimate) is on. */
	public static boolean blackBody(Player p) {
		return running(p, V_BLACKBODY);
	}

	/** v0.17.1: the controlled Berserk (Unleash) -- it never turns on you or your squad. */
	public static boolean unleashed(Player p) {
		return running(p, V_UNLEASH) && berserk(p);
	}

	/** v0.17.1 (client-safe): {@code e} wears one of {@code p}'s Shadow Scout marks. */
	public static boolean marked(Player p, net.minecraft.world.entity.Entity e) {
		QuirkState s = Quirks.peek(p);
		if (s == null || s.val(V_MARK_UNTIL) <= p.level().getGameTime()) {
			return false;
		}
		for (int i = 0; i < MAX_MARKS; i++) {
			if (s.val(V_MARK_PREFIX + i) == e.getId() + 1L) {
				return true;
			}
		}
		return false;
	}

	/** v0.17.1 (client-safe): the entity ids {@code p}'s marks are on (empty when none). */
	public static int[] marks(Player p) {
		QuirkState s = Quirks.peek(p);
		if (s == null || s.val(V_MARK_UNTIL) <= p.level().getGameTime()) {
			return new int[0];
		}
		int[] out = new int[MAX_MARKS];
		int n = 0;
		for (int i = 0; i < MAX_MARKS; i++) {
			long v = s.val(V_MARK_PREFIX + i);
			if (v > 0L) {
				out[n++] = (int) (v - 1L);
			}
		}
		return java.util.Arrays.copyOf(out, n);
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
