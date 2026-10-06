package com.projecthero.mod.syndicate;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.31: the Kingpin's Cane swing animations -- purely cosmetic, no damage / cooldown is touched. Every main-hand swing
 * of the cane (an attack, a miss, a block hit) by a player, the Kingpin or any other humanoid plays the next of three
 * strikes, in order:
 * <ol>
 *   <li>{@link #OVERHEAD} -- the cane raised high over the shoulder, then brought straight down in a heavy chop with a
 *       lunge;</li>
 *   <li>{@link #SWIPE} -- wound back out to the right, then swept flat across the body as the torso twists;</li>
 *   <li>{@link #THRUST} -- drawn back to the hip, then driven straight forward at the target, fencing-style.</li>
 * </ol>
 * A pause of more than {@link #RESET_TICKS} starts the chain over at the overhead strike. The swing itself is vanilla's
 * (the client sees the same {@code swing} call every viewer is sent), so nothing new goes over the network: each client
 * counts the swings it sees ({@code KingpinCanePose}, client) and plays these keyframes.
 *
 * <p>The tables live here (common code, plain floats) so the GameTests can check them. Body frames are {@code {tick,
 * wArmX, wArmY, wArmZ, fArmX, fArmY, fArmZ, bodyX, bodyY, frontLegX, backLegX, headX, grip}} in radians for a cane in
 * the right hand (mirrored for the left): "w" = the cane arm, "f" = the free arm, arm X negative = forward / up, arm Y
 * positive = toward the right, body Y positive = torso turned right, grip -1..1 = how far the cane is turned out of its
 * resting angle to follow the arm (1 = straight out of the fist along the arm, negative = laid back the other way). First-person frames are {@code {tick, x,
 * y, z, xDeg, yDeg, zDeg}} about the hand: X positive tips the cane back toward the camera, negative forward / down.
 */
public final class KingpinCaneSwing {
	public static final int OVERHEAD = 0;
	public static final int SWIPE = 1;
	public static final int THRUST = 2;
	public static final int STYLES = 3;
	/** One strike's length in ticks (half a second). */
	public static final int SWING_TICKS = 10;
	/** A swing more than this many ticks after the last one starts the chain over at the overhead strike. */
	public static final int RESET_TICKS = 40;
	/** A swing call this soon after the last strike began is the same swing (e.g. held-down mining), not a new strike. */
	public static final int MIN_GAP_TICKS = SWING_TICKS - 3;

	private KingpinCaneSwing() {
	}

	/** Whether {@code stack} is the Kingpin's Cane. */
	public static boolean isCane(ItemStack stack) {
		return SyndicateItems.KINGPIN_CANE != null && stack.is(SyndicateItems.KINGPIN_CANE);
	}

	/** Whether {@code entity} has the cane in its main hand. */
	public static boolean wielding(LivingEntity entity) {
		return isCane(entity.getMainHandItem());
	}

	/**
	 * The strike a new swing at {@code now} plays, after the strike {@code last} (-1 for none) that began at
	 * {@code lastStart}: the next in the chain, or the overhead strike again after a pause.
	 */
	public static int nextStyle(int last, long lastStart, long now) {
		if (last < 0 || now < lastStart || now - lastStart > RESET_TICKS) {
			return OVERHEAD;
		}
		return (last + 1) % STYLES;
	}

	/** Whether a swing call at {@code now} starts a new strike after one that began at {@code lastStart}. */
	public static boolean isNewStrike(long lastStart, long now) {
		return lastStart < 0 || now < lastStart || now - lastStart >= MIN_GAP_TICKS;
	}

	private static float[] f(float tick, float... v) {
		float[] out = new float[13];
		out[0] = tick;
		System.arraycopy(v, 0, out, 1, Math.min(12, v.length));
		return out;
	}

	private static float[] rest(float tick) {
		return f(tick);
	}

	private static final int SW = SWING_TICKS;

	/** Overhead Strike: raised high over the right shoulder (the cane lies back over it), then a straight chop down. */
	private static final float[][] BODY_OVERHEAD = {
			f(0, -2.6f, 0.15f, -0.2f, -0.2f, 0, -0.25f, -0.1f, 0.25f, 0, 0, -0.15f, -0.35f),
			f(3, -3.0f, 0.1f, -0.15f, -0.35f, 0, -0.35f, -0.18f, 0.3f, 0.05f, -0.05f, -0.2f, -0.6f),
			f(6, -0.55f, -0.05f, 0, 0.35f, 0, -0.3f, 0.35f, -0.1f, -0.55f, 0.45f, 0.3f, 0.75f),
			f(8, -0.5f, -0.05f, 0, 0.3f, 0, -0.3f, 0.32f, -0.1f, -0.5f, 0.42f, 0.28f, 0.75f),
			rest(SW) };
	/** Side Swipe: wound back out to the right, then swept flat across to the left, the torso twisting with it. */
	private static final float[][] BODY_SWIPE = {
			f(0, -1.3f, 1.0f, 0.2f, -0.2f, 0, -0.2f, 0, 0.6f, 0, 0, 0, 0.9f),
			f(3, -1.35f, 1.1f, 0.2f, -0.25f, 0, -0.2f, 0, 0.65f, 0, 0, 0, 0.9f),
			f(6, -1.45f, -0.95f, 0, 0.3f, 0, -0.15f, 0.08f, -0.6f, -0.15f, 0.12f, 0.05f, 0.9f),
			f(8, -1.35f, -1.05f, 0, 0.25f, 0, -0.15f, 0.06f, -0.55f, -0.12f, 0.1f, 0.03f, 0.9f),
			rest(SW) };
	/** Cane Thrust: drawn back to the hip, then driven straight out at the target with a lunge. */
	private static final float[][] BODY_THRUST = {
			f(0, -0.6f, 0.25f, 0.1f, -0.3f, 0, -0.2f, -0.05f, 0.4f, 0, 0, 0, 1),
			f(3, -0.75f, 0.3f, 0.1f, -0.4f, 0, -0.25f, -0.08f, 0.45f, 0.05f, -0.05f, 0, 1),
			f(5, -1.6f, -0.05f, 0, 0.45f, 0, -0.15f, 0.2f, -0.3f, -0.6f, 0.5f, 0.1f, 1),
			f(8, -1.55f, -0.05f, 0, 0.4f, 0, -0.15f, 0.18f, -0.28f, -0.55f, 0.45f, 0.08f, 1),
			rest(SW) };

	private static final float[][] FP_OVERHEAD = {
			{0, 0, 0, 0, 0, 0, 0},
			{3, -0.08f, 0.3f, 0.1f, 40, 0, 10},
			{6, -0.15f, 0.06f, -0.2f, -50, 0, 0},
			{8, -0.15f, 0.08f, -0.18f, -46, 0, 0},
			{SW, 0, 0, 0, 0, 0, 0} };
	private static final float[][] FP_SWIPE = {
			{0, 0, 0, 0, 0, 0, 0},
			{3, 0.18f, 0.05f, 0, -15, 35, -50},
			{6, -0.48f, 0.08f, -0.12f, -25, -40, 80},
			{8, -0.5f, 0.06f, -0.12f, -20, -42, 82},
			{SW, 0, 0, 0, 0, 0, 0} };
	private static final float[][] FP_THRUST = {
			{0, 0, 0, 0, 0, 0, 0},
			{3, 0.04f, -0.1f, 0.18f, -65, 0, 5},
			{5, -0.04f, 0.12f, -0.45f, -70, 0, 0},
			{8, -0.04f, 0.12f, -0.4f, -66, 0, 0},
			{SW, 0, 0, 0, 0, 0, 0} };

	/** The third-person keyframes of {@code style}. */
	public static float[][] body(int style) {
		return switch (style) {
			case SWIPE -> BODY_SWIPE;
			case THRUST -> BODY_THRUST;
			default -> BODY_OVERHEAD;
		};
	}

	/** The first-person keyframes of {@code style}. */
	public static float[][] firstPerson(int style) {
		return switch (style) {
			case SWIPE -> FP_SWIPE;
			case THRUST -> FP_THRUST;
			default -> FP_OVERHEAD;
		};
	}

	/** {@code frames} at {@code tick}, smoothstepped between keyframes; null past the last frame. */
	public static float[] sample(float[][] frames, float tick) {
		if (tick < 0) {
			return null;
		}
		for (int i = 1; i < frames.length; i++) {
			if (tick <= frames[i][0]) {
				float[] a = frames[i - 1];
				float[] b = frames[i];
				float t = Mth.clamp((tick - a[0]) / Math.max(1e-4f, b[0] - a[0]), 0f, 1f);
				t = t * t * (3f - 2f * t);
				float[] out = new float[a.length];
				out[0] = tick;
				for (int k = 1; k < a.length; k++) {
					out[k] = Mth.lerp(t, a[k], b[k]);
				}
				return out;
			}
		}
		return null;
	}

	/**
	 * How much (0..1) the strike overrides the model's own pose {@code tick} ticks in: snaps in over the first tick
	 * (vanilla's swing starts at once too) and eases back out over the last three.
	 */
	public static float weight(float tick) {
		if (tick < 0 || tick > SWING_TICKS) {
			return 0f;
		}
		return Mth.clamp(Math.min(tick + 0.5f, (SWING_TICKS - tick) / 3f), 0f, 1f);
	}
}
