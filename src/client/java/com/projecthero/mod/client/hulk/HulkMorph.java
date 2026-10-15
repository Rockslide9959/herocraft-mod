package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.4 (user: "make it actually look like the player is transforming into the hulk instead of the hulk phasing in and the
 * player phasing out"): the Banner -> Hulk change is a real morph now -- no see-through body at any point. Only ever ONE body is
 * drawn, fully solid:
 * <ol>
 *   <li><b>Phase A</b> (the first {@link #SWAP_AT} of the change): Banner. Hulk green spreads through his own skin texel by
 *       texel from the sternum outward, dark veins leading the front ({@link HulkMorphSkin}); his clothes' second layer tears
 *       away as the green reaches it (and shreds fly off, {@link HulkMorphFx}); he grows (the server's SCALE attribute) and
 *       bulges -- chest, arms and thighs swell in irregular throbs ({@link #bulge}).</li>
 *   <li><b>The swap</b> at {@link #SWAP_AT}: the GeckoLib Hulk takes over in one frame. The Hulk model is the player model's
 *       own shape (16 x 32 x 8 px, the same 64x64 skin layout) and Banner is by then wearing exactly the Hulk texture, so
 *       matching the silhouette is: the same SCALE attribute (both renderers apply it), the same per-part bulge (the Hulk's
 *       bones get the identical numbers, {@code HulkRenderer}), and {@link #FIT_RATIO} to cover the player renderer's 0.9375
 *       pre-scale against the Hulk's {@link HulkRenderer#MODEL_FIT}. A gamma flash, a burst of torn cloth and a roar cover
 *       what is left (the two poses).</li>
 *   <li><b>Phase B</b>: only the Hulk, playing his transform clip (kept on time through phase A by an invisible "ghost" render
 *       of the Hulk), the bulge settling and the fit easing to 1 while the server keeps growing him; a green glow fades off.</li>
 *   <li><b>The change back</b> (and the death's slow change back, lying on his back): the mirror -- the Hulk shrinks (server)
 *       until {@link #REVERT_SWAP_AT}, swaps to a fully green Banner under a green puff, and the green recedes texel by texel
 *       toward his chest.</li>
 * </ol>
 * Everything is computed client side from the synced {@code HulkState} (formChangedAt, hulk, dying, transformStyle) and the
 * game time, so every viewer sees the same morph.
 *
 * <p><b>Tunables</b>: {@link #SWAP_AT}, {@link #REVERT_SWAP_AT}, {@link #GREEN_DONE}, {@link #GREEN_BEATS}, {@link #GLOW_TICKS},
 * the bulge amplitudes ({@link #BULGE_BODY} ...), and in {@link HulkMorphSkin} the front / vein widths.
 */
public final class HulkMorph {
	/** Into the Hulk: the fraction of {@link Hulk#changeTicks} at which the Hulk model replaces Banner. */
	public static final float SWAP_AT = 0.55f;
	/** Back to Banner: the fraction of the shrink at which Banner replaces the Hulk. */
	public static final float REVERT_SWAP_AT = 0.45f;
	/** The green has covered all of Banner by this fraction of phase A (he wears the full Hulk skin for the rest). */
	public static final float GREEN_DONE = 0.9f;
	/** Heartbeat surges the green spreads in (slow change / quick change). */
	public static final int GREEN_BEATS = 4;
	public static final int GREEN_BEATS_QUICK = 2;
	/** The Hulk's glow after the swap fades over this many ticks. */
	public static final float GLOW_TICKS = 30.0f;
	/** Swell at the peak (the swap), as a fraction of the part's thickness. */
	public static final float BULGE_BODY = 0.30f;
	public static final float BULGE_ARM = 0.34f;
	public static final float BULGE_LEG = 0.20f;
	public static final float BULGE_HEAD = 0.05f;
	/** The shrink back swells less (he deflates rather than bursts). */
	public static final float REVERT_BULGE = 0.5f;
	/** The player renderer's own 0.9375 pre-scale over the Hulk renderer's {@link HulkRenderer#MODEL_FIT}. */
	public static final float FIT_RATIO = 0.9375f / HulkRenderer.MODEL_FIT;

	/** Part indices for {@link #bulge}. */
	public static final int BODY = 0, RIGHT_ARM = 1, LEFT_ARM = 2, RIGHT_LEG = 3, LEFT_LEG = 4, HEAD = 5, PARTS = 6;
	private static final float[] SCRATCH = new float[PARTS];

	/** Where in the morph a player is. */
	public enum Phase {
		/** No morph: Banner or the Hulk as normal. */
		NONE,
		/** Into the Hulk, Banner still drawn: greening and bulging. */
		GREENING,
		/** Into the Hulk, after the swap: the Hulk drawn, settling. */
		SETTLING,
		/** Back to Banner, the Hulk still drawn: shrinking. */
		SHRINKING,
		/** Back to Banner, after the swap: Banner drawn, the green receding. */
		RECEDING
	}

	private HulkMorph() {
	}

	static HulkState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
	}

	/** Ticks since the last change started (with the partial tick). */
	private static float clock(Player player, HulkState s, float partial) {
		return (player.level().getGameTime() - s.formChangedAt) + partial;
	}

	/** Length of the current change back (the death's is the slow one). */
	private static float revertTicks(HulkState s) {
		return s.dying() ? HulkConfig.DEATH_REVERT_TICKS : HulkConfig.GROWTH_TICKS;
	}

	private static float swapTicks(HulkState s) {
		return SWAP_AT * Hulk.changeTicks(s);
	}

	public static Phase phase(Player player, float partial) {
		HulkState s = state(player);
		if (s == null || !s.hasPower) {
			return Phase.NONE;
		}
		float t = clock(player, s, partial);
		if (s.hulk) {
			if (s.dying()) {
				return Phase.NONE; // lying there as the Hulk until the server starts the change back
			}
			int len = Hulk.changeTicks(s);
			if (t >= len) {
				return Phase.NONE;
			}
			return t < swapTicks(s) ? Phase.GREENING : Phase.SETTLING;
		}
		float len = revertTicks(s);
		if (t >= len) {
			return Phase.NONE;
		}
		return t < REVERT_SWAP_AT * len ? Phase.SHRINKING : Phase.RECEDING;
	}

	/** Whether the Hulk model (not Banner) is the body drawn for this player right now. */
	public static boolean hulkShown(Player player, float partial) {
		HulkState s = state(player);
		if (s == null || !s.hasPower) {
			return false;
		}
		return switch (phase(player, partial)) {
			case GREENING, RECEDING -> false;
			case SETTLING, SHRINKING -> true;
			case NONE -> s.hulk;
		};
	}

	/**
	 * Phase A only: the Hulk is rendered without drawing anything, so his GeckoLib transform clip runs from the change's first
	 * tick and is on the right frame at the swap.
	 */
	public static boolean ghostHulk(Player player, float partial) {
		return phase(player, partial) == Phase.GREENING;
	}

	/**
	 * How much of Banner's skin is Hulk green, 0..1, or -1 when Banner is not mid-morph (draw his own skin untouched).
	 */
	public static float green(Player player, float partial) {
		HulkState s = state(player);
		if (s == null || !s.hasPower) {
			return -1.0f;
		}
		Phase ph = phase(player, partial);
		float t = clock(player, s, partial);
		if (ph == Phase.GREENING) {
			float u = Mth.clamp(t / (swapTicks(s) * GREEN_DONE), 0.0f, 1.0f);
			int beats = s.combat.transformStyle == HulkState.TRANSFORM_QUICK ? GREEN_BEATS_QUICK : GREEN_BEATS;
			// a staircase: monotone, but it surges on each heartbeat and nearly stalls between them
			double w = Math.PI * 2.0 * beats;
			return Mth.clamp((float) (u - Math.sin(w * u) / w), 0.0f, 1.0f);
		}
		if (ph == Phase.RECEDING) {
			float len = revertTicks(s);
			float from = REVERT_SWAP_AT * len;
			float u = Mth.clamp((t - from) / ((len - from) * GREEN_DONE), 0.0f, 1.0f);
			return 1.0f - smooth(u);
		}
		return -1.0f;
	}

	/** 0..1: how swollen the body is (both bodies use the same number, so the swap matches). */
	public static float bulgeEnvelope(Player player, float partial) {
		HulkState s = state(player);
		if (s == null) {
			return 0.0f;
		}
		float t = clock(player, s, partial);
		switch (phase(player, partial)) {
			case GREENING: {
				float u = t / swapTicks(s);
				return smooth((u - 0.12f) / 0.88f);
			}
			case SETTLING: {
				float a = swapTicks(s);
				return 1.0f - smooth((t - a) / Math.max(1.0f, Hulk.changeTicks(s) - a));
			}
			case SHRINKING: {
				float a = REVERT_SWAP_AT * revertTicks(s);
				return REVERT_BULGE * smooth(t / a);
			}
			case RECEDING: {
				float len = revertTicks(s);
				float a = REVERT_SWAP_AT * len;
				return REVERT_BULGE * (1.0f - smooth((t - a) / Math.max(1.0f, len - a)));
			}
			default:
				return 0.0f;
		}
	}

	/**
	 * Fills {@code out[PARTS]} with each part's swell factor (1 = normal thickness). A function of the change clock only, so
	 * Banner's last frame and the Hulk's first frame get the same numbers. Returns false (and leaves out alone) when there is
	 * no swell.
	 */
	public static boolean bulge(Player player, float partial, float[] out) {
		float env = bulgeEnvelope(player, partial);
		if (env <= 0.001f) {
			return false;
		}
		HulkState s = state(player);
		float t = clock(player, s, partial);
		out[BODY] = 1.0f + BULGE_BODY * env * throb(t, 0.71f, 0.0f);
		out[RIGHT_ARM] = 1.0f + BULGE_ARM * env * throb(t, 0.93f, 1.7f);
		out[LEFT_ARM] = 1.0f + BULGE_ARM * env * throb(t, 0.87f, 4.1f);
		out[RIGHT_LEG] = 1.0f + BULGE_LEG * env * throb(t, 0.61f, 2.6f);
		out[LEFT_LEG] = 1.0f + BULGE_LEG * env * throb(t, 0.66f, 5.3f);
		out[HEAD] = 1.0f + BULGE_HEAD * env * throb(t, 0.5f, 0.9f);
		return true;
	}

	/** 0.6..1: an irregular muscle throb (two sines beating against each other, sharpened). */
	private static float throb(float t, float freq, float phase) {
		float a = Mth.sin(t * freq + phase);
		float b = Mth.sin(t * freq * 1.618f + phase * 0.5f);
		float v = 0.5f + 0.25f * (a + b); // 0..1
		return 0.6f + 0.4f * v * v;
	}

	/** Body widening (x) from its swell; the chest pushes out more (z = the full factor). */
	public static float bodyWidth(float bodyBulge) {
		return 1.0f + (bodyBulge - 1.0f) * 0.75f;
	}

	/** How far (px) the arms move out so the widened chest does not swallow them. */
	public static float armOffset(float bodyBulge) {
		return 4.0f * (bodyWidth(bodyBulge) - 1.0f);
	}

	/**
	 * The Hulk model's extra size factor so its silhouette meets Banner's at the swap: {@link #FIT_RATIO} at the swap, easing
	 * to 1 by the end of the change (and from 1 up to it while he shrinks back). 1 outside the morph.
	 */
	public static float hulkFit(Player player, float partial) {
		HulkState s = state(player);
		if (s == null) {
			return 1.0f;
		}
		float t = clock(player, s, partial);
		switch (phase(player, partial)) {
			case SETTLING: {
				float a = swapTicks(s);
				return Mth.lerp(smooth((t - a) / Math.max(1.0f, Hulk.changeTicks(s) - a)), FIT_RATIO, 1.0f);
			}
			case SHRINKING: {
				float a = REVERT_SWAP_AT * revertTicks(s);
				return Mth.lerp(smooth(t / a), 1.0f, FIT_RATIO);
			}
			default:
				return 1.0f;
		}
	}

	/** 0..1: the Hulk's gamma glow after the swap (drawn as extra light on him), fading over {@link #GLOW_TICKS}. */
	public static float glow(Player player, float partial) {
		HulkState s = state(player);
		if (s == null || !s.hasPower || !s.hulk || s.dying()) {
			return 0.0f;
		}
		float since = clock(player, s, partial) - swapTicks(s);
		if (since < 0.0f || since > GLOW_TICKS) {
			return 0.0f;
		}
		float f = 1.0f - since / GLOW_TICKS;
		return f * f;
	}

	/**
	 * Banner's swell on the vanilla model (from {@code HumanoidModelMixin}, after the change pose). Scales are reset to 1 for
	 * every player first, so nothing leaks to the next player drawn with the shared model.
	 */
	public static void applyBulge(Player player, HumanoidModel<?> m, boolean firstPersonHand) {
		float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		if (hulkShown(player, partial)) {
			return;
		}
		float[] b = SCRATCH; // rendering is single-threaded
		if (!bulge(player, partial, b)) {
			return;
		}
		float bx = bodyWidth(b[BODY]);
		m.body.xScale = bx;
		m.body.zScale = b[BODY];
		m.rightArm.xScale = b[RIGHT_ARM];
		m.rightArm.zScale = b[RIGHT_ARM];
		m.leftArm.xScale = b[LEFT_ARM];
		m.leftArm.zScale = b[LEFT_ARM];
		m.rightLeg.xScale = b[RIGHT_LEG];
		m.rightLeg.zScale = b[RIGHT_LEG];
		m.leftLeg.xScale = b[LEFT_LEG];
		m.leftLeg.zScale = b[LEFT_LEG];
		m.head.xScale = b[HEAD];
		m.head.yScale = b[HEAD];
		m.head.zScale = b[HEAD];
		m.hat.copyFrom(m.head);
		if (!firstPersonHand) {
			float off = armOffset(b[BODY]);
			m.rightArm.x -= off;
			m.leftArm.x += off;
		}
	}

	/** Resets every scale {@link #applyBulge} may set (the model is shared between players). */
	public static void resetScale(HumanoidModel<?> m) {
		m.head.xScale = m.head.yScale = m.head.zScale = 1.0f;
		m.hat.xScale = m.hat.yScale = m.hat.zScale = 1.0f;
		m.body.xScale = m.body.yScale = m.body.zScale = 1.0f;
		m.rightArm.xScale = m.rightArm.yScale = m.rightArm.zScale = 1.0f;
		m.leftArm.xScale = m.leftArm.yScale = m.leftArm.zScale = 1.0f;
		m.rightLeg.xScale = m.rightLeg.yScale = m.rightLeg.zScale = 1.0f;
		m.leftLeg.xScale = m.leftLeg.yScale = m.leftLeg.zScale = 1.0f;
	}

	static float smooth(float f) {
		f = Mth.clamp(f, 0.0f, 1.0f);
		return f * f * (3.0f - 2.0f * f);
	}
}
