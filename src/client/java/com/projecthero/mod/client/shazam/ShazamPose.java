package com.projecthero.mod.client.shazam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.ShazamAnim;
import com.projecthero.mod.shazam.ShazamConfig;
import com.projecthero.mod.shazam.ShazamMoves;
import com.projecthero.mod.shazam.data.ShazamState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 Shazam kit: the body animations of every move, keyed off the synced {@code ShazamState} ({@code anim} + {@code animStart},
 * and {@code hold} + {@code holdStart} for the held poses) so every viewer sees them. The clip technique of {@code SuperSoldierPose} /
 * {@code ThorKitPose}: keyframes with an anticipation (wind-up), a snap through the strike, a follow-through and a recovery faded back
 * into vanilla's pose; the torso twist carries the shoulders with it, the lean bends from the hips, a crouch drops the body with
 * vanilla's sneak offsets.
 *
 * <p>Clips are written for the main arm as the right arm; a left-handed player plays them mirrored. Values are radians; per arm X
 * (negative = raised forward / up), Y (positive = in across the body), Z (positive = out to the side). An aimed clip adds the crosshair
 * pitch / yaw to the lead arm (both arms for Zeus's Wrath).
 */
public final class ShazamPose {
	private static final int RX = 0, RY = 1, RZ = 2, LX = 3, LY = 4, LZ = 5, TWIST = 6, LEAN = 7, FLEG = 8, BLEG = 9, CROUCH = 10, HEAD = 11,
			CH = 12;
	private static final int SMOOTH = 0, SNAP = 1, LINEAR = 2;
	private static final float HALF_PI = (float) (Math.PI / 2);

	/** Set while the first-person arm renders through the player model (so no third-person pose leaks into it). */
	public static boolean inFirstPersonHand;

	private ShazamPose() {
	}

	private record Key(float t, int ease, float[] v) {
	}

	/** {@code aim}: 0 none, 1 the lead arm follows the crosshair, 2 both. {@code holdAt}: held at this time while airborne (NaN none). */
	private record Clip(float duration, float in, float out, int aim, float holdAt, Key[] keys) {
	}

	private static Key k(float t, int ease, float rx, float ry, float rz, float lx, float ly, float lz, float twist, float lean, float fLeg,
			float bLeg, float crouch, float head) {
		return new Key(t, ease, new float[] { rx, ry, rz, lx, ly, lz, twist, lean, fLeg, bLeg, crouch, head });
	}

	private static Clip clip(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, 0, Float.NaN, keys);
	}

	// ================================================================== R: the Strength of Hercules

	/** The boxer's guard every combo punch starts from. */
	private static Key guard(float t, float twist) {
		return k(t, SMOOTH, -1.15f, 0.45f, 0.1f, -1.25f, 0.5f, 0.1f, twist, 0.12f, -0.2f, 0.2f, 0.15f, 0.05f);
	}

	/** Combo 1: a straight right, hips and shoulder behind it. */
	private static final Clip COMBO_1 = clip(11f, 1f, 4f, guard(0, 0.2f),
			k(2.2f, SNAP, -1.62f, 0.08f, 0f, -1.2f, 0.55f, 0.1f, -0.5f, 0.24f, -0.42f, 0.36f, 0.15f, 0.05f),
			k(6, SMOOTH, -1.52f, 0.12f, 0f, -1.22f, 0.55f, 0.1f, -0.42f, 0.2f, -0.38f, 0.32f, 0.12f, 0.05f));
	/** Combo 2: the left cross, the body turning the other way. */
	private static final Clip COMBO_2 = clip(11f, 1f, 4f, guard(0, -0.2f),
			k(2.2f, SNAP, -1.15f, 0.55f, 0.1f, -1.62f, 0.08f, 0f, 0.55f, 0.24f, -0.42f, 0.36f, 0.15f, 0.05f),
			k(6, SMOOTH, -1.18f, 0.55f, 0.1f, -1.52f, 0.12f, 0f, 0.48f, 0.2f, -0.38f, 0.32f, 0.12f, 0.05f));
	/** Combo 3, the finisher: wound far back, then a huge right hook across the body that knocks it flying. */
	private static final Clip COMBO_3 = clip(18f, 1f, 6f,
			k(0, SMOOTH, -0.35f, -0.55f, 0.6f, -1.3f, 0.5f, 0.1f, 0.75f, 0.05f, 0.2f, -0.2f, 0.3f, 0.1f),
			k(3.2f, SMOOTH, -0.4f, -0.6f, 0.65f, -1.32f, 0.5f, 0.1f, 0.85f, 0.05f, 0.22f, -0.22f, 0.35f, 0.1f),
			k(5.2f, SNAP, -1.55f, 0.95f, 0.3f, 0.5f, 0f, 0.3f, -0.85f, 0.32f, -0.6f, 0.5f, 0.2f, 0.1f),
			k(11, SMOOTH, -1.45f, 1.05f, 0.3f, 0.45f, 0f, 0.28f, -0.9f, 0.28f, -0.55f, 0.45f, 0.18f, 0.05f));
	/** Herculean Uppercut: a deep dip, then the fist driven straight up, the body rising behind it. */
	private static final Clip UPPERCUT = clip(17f, 1f, 6f,
			k(0, SMOOTH, 0.6f, 0.2f, 0.25f, -1.2f, 0.5f, 0.1f, 0.45f, 0.35f, -0.1f, 0.15f, 0.6f, 0.2f),
			k(3, SMOOTH, 0.65f, 0.2f, 0.25f, -1.22f, 0.5f, 0.1f, 0.5f, 0.38f, -0.1f, 0.15f, 0.65f, 0.2f),
			k(5, SNAP, -2.65f, 0.25f, 0f, -0.6f, 0.3f, 0.25f, -0.5f, -0.15f, -0.4f, 0.4f, 0f, -0.35f),
			k(11, SMOOTH, -2.55f, 0.2f, 0f, -0.55f, 0.3f, 0.25f, -0.45f, -0.12f, -0.38f, 0.38f, 0f, -0.3f));
	/** The Haymaker charging (held): fist cocked back by the hip, guard hand out, low, trembling more as it builds. */
	private static final float[] HAYMAKER_CHARGE = { 0.95f, -0.25f, 0.35f, -1.25f, 0.55f, 0.1f, 0.7f, 0.12f, 0.35f, -0.25f, 0.45f, 0.15f };
	/** The Haymaker released: from the cocked charge, driven straight through with everything behind it. */
	private static final Clip HAYMAKER = clip(20f, 0f, 7f,
			k(0, SMOOTH, 0.95f, -0.25f, 0.35f, -1.25f, 0.55f, 0.1f, 0.7f, 0.12f, 0.35f, -0.25f, 0.45f, 0.15f),
			k(2.5f, SNAP, -1.62f, 0.12f, 0f, 0.65f, 0f, 0.25f, -0.7f, 0.32f, -0.65f, 0.55f, 0.15f, 0.1f),
			k(11, SMOOTH, -1.55f, 0.1f, 0f, 0.55f, 0f, 0.2f, -0.6f, 0.26f, -0.55f, 0.48f, 0.1f, 0.05f));

	// ================================================================== G: the Power of Zeus

	/** Lightning of Zeus: the hand thrown up to the sky, then brought down to point at the target (aimed). */
	private static final Clip ZEUS_CALL = new Clip(20f, 1f, 6f, 1, Float.NaN, new Key[] {
			k(0, SMOOTH, -1.45f, 0f, 0.25f, -0.25f, 0f, 0.45f, 0.1f, -0.15f, -0.1f, 0.1f, 0f, -0.4f),
			k(5, SMOOTH, -1.5f, 0f, 0.25f, -0.25f, 0f, 0.5f, 0.1f, -0.18f, -0.1f, 0.1f, 0f, -0.45f),
			k(7, SNAP, 0f, 0f, 0f, 0.35f, 0f, 0.35f, -0.35f, 0.15f, -0.35f, 0.3f, 0.05f, 0f),
			k(14, SMOOTH, 0.02f, 0f, 0f, 0.3f, 0f, 0.3f, -0.3f, 0.12f, -0.3f, 0.28f, 0.05f, 0f) });
	/** Thunderhead Slam: both fists raised high, held through a dive, then driven down into the ground. */
	private static final Clip SLAM = new Clip(24f, 1f, 7f, 0, 6f, new Key[] {
			k(0, SMOOTH, -2.9f, 0.1f, 0.25f, -2.9f, 0.1f, 0.25f, 0f, -0.22f, 0f, 0.1f, 0f, -0.35f),
			k(6, SMOOTH, -3.05f, 0.1f, 0.2f, -3.05f, 0.1f, 0.2f, 0f, -0.28f, 0f, 0.1f, 0f, -0.4f),
			k(8, SNAP, -0.45f, 0.25f, 0f, -0.45f, 0.25f, 0f, 0f, 0.48f, -0.65f, 0.65f, 0.8f, 0.4f),
			k(15, SMOOTH, -0.5f, 0.25f, 0f, -0.5f, 0.25f, 0f, 0f, 0.44f, -0.6f, 0.6f, 0.75f, 0.35f) });
	/** Zeus's Wrath (held): both arms out along the aim, braced, the stream pouring from the hands. */
	private static final float[] WRATH = { 0f, 0.18f, 0f, 0f, 0.18f, 0f, 0f, 0.12f, -0.4f, 0.4f, 0.25f, 0f };

	// ================================================================== C: the Speed of Mercury

	/** Mercury Blitz: flung forward, fist leading, the other arm streaming behind. */
	private static final Clip BLITZ = clip(11f, 0f, 4f,
			k(0, SNAP, -1.55f, 0.1f, 0f, 0.95f, 0f, 0.3f, -0.35f, 0.62f, -0.85f, 0.85f, 0f, -0.25f),
			k(7, SMOOTH, -1.5f, 0.1f, 0f, 0.9f, 0f, 0.3f, -0.3f, 0.55f, -0.75f, 0.75f, 0f, -0.2f));
	/** Thunderclap: arms flung wide, then the clap in front of him. */
	private static final Clip CLAP = clip(17f, 1f, 6f,
			k(0, SMOOTH, -1.35f, -0.95f, 0.35f, -1.35f, -0.95f, 0.35f, 0f, -0.12f, 0f, 0f, 0.05f, -0.1f),
			k(4, SMOOTH, -1.4f, -1.05f, 0.4f, -1.4f, -1.05f, 0.4f, 0f, -0.15f, 0f, 0f, 0.08f, -0.12f),
			k(5.5f, SNAP, -1.52f, 0.55f, 0f, -1.52f, 0.55f, 0f, 0f, 0.25f, -0.32f, 0.32f, 0.1f, 0.1f),
			k(11, SMOOTH, -1.48f, 0.5f, 0f, -1.48f, 0.5f, 0f, 0f, 0.2f, -0.3f, 0.3f, 0.08f, 0.08f));
	/** Mercury Rush, a hop's punch: right, then left on the next. */
	private static final Clip RUSH_A = clip(8f, 0f, 3f,
			k(0, SNAP, -1.62f, 0.1f, 0f, 0.6f, 0f, 0.3f, -0.5f, 0.35f, -0.5f, 0.45f, 0.2f, 0.05f),
			k(4, SMOOTH, -1.55f, 0.1f, 0f, 0.55f, 0f, 0.28f, -0.45f, 0.3f, -0.45f, 0.4f, 0.18f, 0.05f));
	private static final Clip RUSH_B = clip(8f, 0f, 3f,
			k(0, SNAP, 0.6f, 0f, 0.3f, -1.62f, 0.1f, 0f, 0.5f, 0.35f, -0.5f, 0.45f, 0.2f, 0.05f),
			k(4, SMOOTH, 0.55f, 0f, 0.28f, -1.55f, 0.1f, 0f, 0.45f, 0.3f, -0.45f, 0.4f, 0.18f, 0.05f));

	// ================================================================== Z: the Stamina of Atlas

	/** Atlas Quake: the knee drawn high, arms out for balance, then the stomp that drops the whole body onto it. */
	private static final Clip QUAKE = clip(19f, 1f, 6f,
			k(0, SMOOTH, -0.5f, 0f, 0.85f, -0.5f, 0f, 0.85f, 0f, -0.08f, -1.45f, 0.05f, 0f, -0.1f),
			k(4, SMOOTH, -0.55f, 0f, 0.9f, -0.55f, 0f, 0.9f, 0f, -0.1f, -1.55f, 0.05f, 0f, -0.12f),
			k(5.2f, SNAP, 0.3f, 0f, 0.45f, 0.3f, 0f, 0.45f, 0f, 0.35f, -0.3f, 0.5f, 0.7f, 0.3f),
			k(12, SMOOTH, 0.25f, 0f, 0.4f, 0.25f, 0f, 0.4f, 0f, 0.3f, -0.28f, 0.45f, 0.65f, 0.25f));
	/** Titan's Grip (held): the creature held high overhead in both hands, the knees bent under it. */
	private static final float[] GRIP = { -3.05f, 0.08f, 0.15f, -3.05f, 0.08f, 0.15f, 0f, -0.12f, -0.2f, 0.2f, 0.25f, -0.35f };
	/** Titan's Grip, the hurl: from overhead, thrown forward with the whole body. */
	private static final Clip HURL = clip(15f, 0f, 6f,
			k(0, SMOOTH, -3.05f, 0.08f, 0.15f, -3.05f, 0.08f, 0.15f, 0f, -0.25f, -0.2f, 0.2f, 0.2f, -0.35f),
			k(2.5f, SNAP, -1.15f, 0.1f, 0f, -1.15f, 0.1f, 0f, 0f, 0.42f, -0.65f, 0.55f, 0.1f, 0.1f),
			k(9, SMOOTH, -1.05f, 0.1f, 0f, -1.05f, 0.1f, 0f, 0f, 0.36f, -0.6f, 0.5f, 0.08f, 0.05f));
	/** Titan's Grip on a boss: a low shoulder heave and a two-handed shove. */
	private static final Clip SHOVE = clip(15f, 1f, 6f,
			k(0, SMOOTH, -1.3f, 0.2f, 0f, -1.3f, 0.2f, 0f, 0f, 0.4f, -0.2f, 0.25f, 0.5f, 0.1f),
			k(3, SNAP, -1.62f, 0.1f, 0f, -1.62f, 0.1f, 0f, 0f, 0.5f, -0.75f, 0.6f, 0.3f, 0.05f),
			k(9, SMOOTH, -1.55f, 0.1f, 0f, -1.55f, 0.1f, 0f, 0f, 0.45f, -0.7f, 0.55f, 0.25f, 0.05f));
	/**
	 * "SHAZAM!" Thunderstrike: gathered low, forearms crossed; the shout -- arms flung up to the sky, head back -- held as he rises and
	 * the colossal bolt hits; arms spread wide, crackling; then the release, arms flung out and down as the lightning bursts outward.
	 */
	private static final Clip ULT = clip(44f, 1f, 9f,
			k(0, SMOOTH, -1.3f, 1.0f, 0f, -1.3f, 1.0f, 0f, 0f, 0.35f, 0f, 0f, 0.6f, 0.3f),
			k(3, SMOOTH, -1.35f, 1.05f, 0f, -1.35f, 1.05f, 0f, 0f, 0.38f, 0f, 0f, 0.65f, 0.32f),
			k(5, SNAP, -2.9f, 0f, 0.55f, -2.9f, 0f, 0.55f, 0f, -0.35f, -0.15f, 0.15f, 0f, -0.6f),
			k(16, SMOOTH, -3.0f, 0f, 0.6f, -3.0f, 0f, 0.6f, 0f, -0.38f, -0.2f, 0.2f, 0f, -0.62f),
			k(18, SNAP, -1.6f, 0f, 1.3f, -1.6f, 0f, 1.3f, 0f, -0.2f, -0.2f, 0.2f, 0.1f, -0.3f),
			k(23.5f, SMOOTH, -1.65f, 0f, 1.35f, -1.65f, 0f, 1.35f, 0f, -0.22f, -0.22f, 0.22f, 0.12f, -0.32f),
			k(25, SNAP, -0.4f, 0f, 1.35f, -0.4f, 0f, 1.35f, 0f, 0.28f, -0.35f, 0.35f, 0.4f, 0.25f),
			k(34, SMOOTH, -0.35f, 0f, 1.25f, -0.35f, 0f, 1.25f, 0f, 0.24f, -0.3f, 0.3f, 0.35f, 0.2f));

	// ================================================================== X / V

	/** Sonic Leap: a crouch, then the spring -- one fist up like the old serials, held while he flies up. */
	private static final Clip LEAP = new Clip(32f, 0f, 5f, 0, 12f, new Key[] {
			k(0, SMOOTH, 0.7f, 0f, 0.15f, 0.7f, 0f, 0.15f, 0f, 0.4f, 0f, 0f, 0.7f, 0.2f),
			k(3, SNAP, -3.0f, 0.1f, 0.1f, 0.45f, 0f, 0.2f, 0f, -0.12f, -0.55f, 0.5f, 0f, -0.3f),
			k(12, SMOOTH, -3.05f, 0.1f, 0.08f, 0.5f, 0f, 0.2f, 0f, -0.08f, -0.7f, 0.2f, 0f, -0.25f) });
	/** Mercury Dash / Burst: a lean into the speed, arms back. */
	private static final Clip DASH = clip(10f, 0f, 4f,
			k(0, SNAP, 0.6f, 0f, 0.3f, 0.6f, 0f, 0.3f, 0f, 0.55f, -0.5f, 0.5f, 0f, -0.2f),
			k(6, SMOOTH, 0.55f, 0f, 0.3f, 0.55f, 0f, 0.3f, 0f, 0.5f, -0.45f, 0.45f, 0f, -0.2f));
	/** Wisdom of Solomon: two fingers to the temple, then the arm sweeps out across the field as the wave goes out. */
	private static final Clip WISDOM = clip(21f, 1f, 6f,
			k(0, SMOOTH, -2.3f, 0.95f, 0f, -0.2f, 0f, 0.1f, 0f, 0.05f, 0f, 0f, 0f, 0.15f),
			k(6, SMOOTH, -2.35f, 1.0f, 0f, -0.2f, 0f, 0.1f, 0f, 0.05f, 0f, 0f, 0f, 0.18f),
			k(8, SNAP, -1.5f, -0.6f, 0.45f, -0.25f, 0f, 0.2f, -0.25f, -0.05f, -0.15f, 0.15f, 0f, -0.1f),
			k(14, SMOOTH, -1.45f, -0.65f, 0.45f, -0.25f, 0f, 0.2f, -0.25f, -0.05f, -0.15f, 0.15f, 0f, -0.1f));
	/** Courage of Achilles: fists to the chest, then planted wide, chest out, head up. */
	private static final Clip COURAGE = clip(23f, 1f, 7f,
			k(0, SMOOTH, -1.6f, 1.1f, 0f, -1.6f, 1.1f, 0f, 0f, 0.15f, 0f, 0f, 0.2f, 0.15f),
			k(5, SMOOTH, -1.62f, 1.12f, 0f, -1.62f, 1.12f, 0f, 0f, 0.18f, 0f, 0f, 0.22f, 0.18f),
			k(7, SNAP, 0.3f, 0f, 0.55f, 0.3f, 0f, 0.55f, 0f, -0.25f, -0.35f, 0.35f, 0.25f, -0.3f),
			k(16, SMOOTH, 0.28f, 0f, 0.52f, 0.28f, 0f, 0.52f, 0f, -0.22f, -0.32f, 0.32f, 0.22f, -0.28f));

	private static Clip clipFor(int anim) {
		return switch (anim) {
			case ShazamAnim.COMBO_1 -> COMBO_1;
			case ShazamAnim.COMBO_2 -> COMBO_2;
			case ShazamAnim.COMBO_3 -> COMBO_3;
			case ShazamAnim.UPPERCUT -> UPPERCUT;
			case ShazamAnim.HAYMAKER -> HAYMAKER;
			case ShazamAnim.ZEUS_CALL -> ZEUS_CALL;
			case ShazamAnim.SLAM -> SLAM;
			case ShazamAnim.BLITZ -> BLITZ;
			case ShazamAnim.CLAP -> CLAP;
			case ShazamAnim.RUSH_A -> RUSH_A;
			case ShazamAnim.RUSH_B -> RUSH_B;
			case ShazamAnim.QUAKE -> QUAKE;
			case ShazamAnim.HURL -> HURL;
			case ShazamAnim.SHOVE -> SHOVE;
			case ShazamAnim.ULT -> ULT;
			case ShazamAnim.LEAP -> LEAP;
			case ShazamAnim.DASH -> DASH;
			case ShazamAnim.WISDOM -> WISDOM;
			case ShazamAnim.COURAGE -> COURAGE;
			default -> null;
		};
	}

	/** The held pose for a hold move (null: the hold plays clips instead -- Mercury Rush). */
	private static float[] heldFor(int hold) {
		return switch (hold) {
			case ShazamAnim.HOLD_HAYMAKER -> HAYMAKER_CHARGE;
			case ShazamAnim.HOLD_WRATH -> WRATH;
			case ShazamAnim.HOLD_GRIP -> GRIP;
			default -> null;
		};
	}

	// ================================================================== sampling

	private static float ease(int mode, float t) {
		return switch (mode) {
			case SNAP -> 1f - (1f - t) * (1f - t) * (1f - t);
			case LINEAR -> t;
			default -> t * t * (3f - 2f * t);
		};
	}

	private static float weight(Clip c, float time) {
		if (time < 0f || time >= c.duration) {
			return 0f;
		}
		float in = c.in <= 0f ? 1f : Mth.clamp(time / c.in, 0f, 1f);
		float last = c.keys[c.keys.length - 1].t;
		float out = time <= last ? 1f : 1f - Mth.clamp((time - last) / Math.max(0.01f, Math.min(c.out, c.duration - last)), 0f, 1f);
		return ease(SMOOTH, in) * ease(SMOOTH, out);
	}

	private static float[] sample(Clip c, float time, float[] base) {
		Key[] ks = c.keys;
		float[] out = new float[CH];
		int i = 0;
		while (i < ks.length && ks[i].t <= time) {
			i++;
		}
		Key a = i == 0 ? null : ks[i - 1];
		Key b = i < ks.length ? ks[i] : null;
		for (int ch = 0; ch < CH; ch++) {
			float va = a == null ? base[ch] : a.v[ch];
			if (b == null) {
				out[ch] = va;
				continue;
			}
			float vb = b.v[ch];
			float ta = a == null ? 0f : a.t;
			float t = b.t <= ta ? 1f : Mth.clamp((time - ta) / (b.t - ta), 0f, 1f);
			out[ch] = Mth.lerp(ease(b.ease, t), va, vb);
		}
		return out;
	}

	/** Clip age, held at {@code holdAt} while airborne (capped so a stuck one still ends). */
	private static float age(Player p, Clip c, ShazamState s, float partial) {
		float age = p.level().getGameTime() - s.animStart + partial;
		if (!Float.isNaN(c.holdAt) && age > c.holdAt && !p.onGround() && !p.isInWater() && age < 80f) {
			age = c.holdAt;
		}
		return age;
	}

	private static ShazamState live(Player p) {
		ShazamState s = Shazam.peek(p);
		return s != null && s.transformed ? s : null;
	}

	/** A hold move with a held pose is running, and no newer clip is playing over it. */
	private static boolean heldPose(Player p, ShazamState s, float partial) {
		return heldFor(s.hold) != null && !(s.animStart > s.holdStart && clipPlaying(p, s, partial));
	}

	// ================================================================== third person

	/** Poses {@code m} for {@code p}'s current move, if one is playing. */
	public static void apply(Player p, HumanoidModel<?> m) {
		if (inFirstPersonHand || com.projecthero.mod.client.blackpanther.BlackPantherPose.inFirstPersonHand) {
			return;
		}
		ShazamState s = live(p);
		if (s == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean right = p.getMainArm() == HumanoidArm.RIGHT;
		// sprint-flight: both fists out ahead, first, so a move below blends out of it (and wins while it plays)
		float fly = sprintFlight(p, partial);
		if (fly > 0.001f) {
			flightPose(p, m, fly, partial, right);
		}
		float[] held = heldFor(s.hold);
		if (held != null && heldPose(p, s, partial)) {
			float t = p.level().getGameTime() - s.holdStart - ShazamConfig.HOLD_TICKS + partial;
			float w = ease(SMOOTH, Mth.clamp((t + ShazamConfig.HOLD_TICKS) / 4f, 0f, 1f));
			float build = s.hold == ShazamAnim.HOLD_HAYMAKER ? ShazamMoves.haymakerCharge(p) : 0.6f;
			float tremble = Mth.sin((p.tickCount + partial) * 2.9f) * (0.012f + 0.05f * build);
			float[] v = held.clone();
			v[RX] += tremble;
			v[LX] -= tremble * 0.7f;
			if (s.hold == ShazamAnim.HOLD_HAYMAKER) {
				v[TWIST] += build * 0.18f;
				v[CROUCH] += build * 0.15f;
			}
			write(m, base(m, right), v, w, right, s.hold == ShazamAnim.HOLD_WRATH ? 2 : 0);
			return;
		}
		Clip c = clipFor(s.anim);
		if (c == null) {
			return;
		}
		float age = age(p, c, s, partial);
		float w = weight(c, age);
		if (w <= 0.001f) {
			return;
		}
		float[] base = base(m, right);
		write(m, base, sample(c, age, base), w, right, c.aim);
	}

	// ================================================================== sprint-flight (both fists forward)

	/**
	 * User (v0.17.7): "when shazam sprint flies make it so that he puts both his hands in front of him and make it so that you see the
	 * hands going in front of you in first person". Per player {prev, now}: 0..1, how far into the sprint-flight pose he is, moved
	 * linearly by 1 / {@link #SPRINT_BLEND_TICKS} a tick (smoothstepped when read), so it blends in and out instead of snapping.
	 * Client-only, keyed by UUID (no worlds or entities held); emptied when the level goes and trimmed to the players present.
	 */
	private static final Map<UUID, float[]> SPRINT = new HashMap<>();
	private static final float SPRINT_BLEND_TICKS = 5f;

	/** Sprint-flying right now: transformed Shazam (or Family) flying, sprinting along forward (the shared flight's FAST tier). */
	public static boolean sprintFlying(Player p) {
		return live(p) != null && Shazam.isFlying(p) && !p.onGround() && !p.isPassenger()
				&& com.projecthero.mod.client.FlightPoseHelper.tier(p) == com.projecthero.mod.client.FlightPoseHelper.Tier.FAST;
	}

	/** Once a client tick (ShazamClient), for every player in the level. */
	public static void clientTick(ClientLevel level) {
		if (level == null) {
			SPRINT.clear();
			return;
		}
		Set<UUID> present = new HashSet<>();
		for (Player p : level.players()) {
			boolean on = sprintFlying(p);
			float[] v = SPRINT.get(p.getUUID());
			if (v == null) {
				if (!on) {
					continue;
				}
				v = new float[2];
				SPRINT.put(p.getUUID(), v);
			}
			present.add(p.getUUID());
			v[0] = v[1];
			v[1] = Mth.clamp(v[1] + (on ? 1f : -1f) / SPRINT_BLEND_TICKS, 0f, 1f);
		}
		SPRINT.entrySet().removeIf(e -> !present.contains(e.getKey()) || e.getValue()[0] <= 0f && e.getValue()[1] <= 0f);
	}

	/** 0..1, eased: how far into the sprint-flight pose {@code p} is (0 when not Shazam / not sprint-flying). */
	public static float sprintFlight(Player p, float partial) {
		float[] v = SPRINT.get(p.getUUID());
		if (v == null || live(p) == null) {
			return 0f;
		}
		return ease(SMOOTH, Mth.lerp(partial, v[0], v[1]));
	}

	/**
	 * Third person: the classic flying pose. The body is already laid flat along the flight by the shared flight lean
	 * ({@code FlightPoseHelper}, which also levels the head), so in model space "straight up" (xRot -PI) is straight ahead: both arms
	 * reach along it, a touch below the head line, angled in so the fists close up in front of the face; the legs come together
	 * and trail straight behind (a little of the flight's scissor kept so it isn't stiff); a faint wind tremble on the arms.
	 * Symmetric, so a left-handed player's pose is its own mirror (the main / off arm sides are still resolved from the main arm).
	 */
	private static void flightPose(Player p, HumanoidModel<?> m, float w, float partial, boolean right) {
		float t = p.tickCount + partial;
		float mainTremble = Mth.sin(t * 0.93f) * 0.012f;
		float offTremble = Mth.sin(t * 0.81f + 1.7f) * 0.012f;
		ModelPart main = right ? m.rightArm : m.leftArm;
		ModelPart off = right ? m.leftArm : m.rightArm;
		float sMain = right ? 1f : -1f;
		// zRot +: the right arm, raised, swings in toward the middle (the left one mirrored)
		lerpPart(main, w, -(float) Math.PI + 0.1f + mainTremble, 0f, sMain * FLIGHT_ARM_IN);
		lerpPart(off, w, -(float) Math.PI + 0.1f + offTremble, 0f, -sMain * FLIGHT_ARM_IN);
		lerpPart(m.rightLeg, w, m.rightLeg.xRot * 0.3f, 0f, 0.015f);
		lerpPart(m.leftLeg, w, m.leftLeg.xRot * 0.3f, 0f, -0.015f);
	}

	/** How far the raised arms angle in (radians): the fists end up just apart in front of the face. */
	private static final float FLIGHT_ARM_IN = 0.2f;

	private static void lerpPart(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	private static boolean clipPlaying(Player p, ShazamState s, float partial) {
		Clip c = clipFor(s.anim);
		return c != null && weight(c, age(p, c, s, partial)) > 0.001f;
	}

	private static float[] base(HumanoidModel<?> m, boolean right) {
		float[] b = new float[CH];
		float mir = right ? 1f : -1f;
		ModelPart ra = right ? m.rightArm : m.leftArm;
		ModelPart la = right ? m.leftArm : m.rightArm;
		ModelPart fl = right ? m.rightLeg : m.leftLeg;
		ModelPart bl = right ? m.leftLeg : m.rightLeg;
		b[RX] = ra.xRot;
		b[RY] = -ra.yRot * mir;
		b[RZ] = ra.zRot * mir;
		b[LX] = la.xRot;
		b[LY] = la.yRot * mir;
		b[LZ] = -la.zRot * mir;
		b[TWIST] = m.body.yRot * mir;
		b[LEAN] = m.crouching ? 0f : m.body.xRot;
		b[FLEG] = fl.xRot;
		b[BLEG] = bl.xRot;
		b[CROUCH] = m.crouching ? 1f : 0f;
		b[HEAD] = 0f;
		return b;
	}

	private static void write(HumanoidModel<?> m, float[] base, float[] p, float w, boolean right, int aim) {
		float mir = right ? 1f : -1f;
		float[] v = new float[CH];
		for (int ch = 0; ch < CH; ch++) {
			v[ch] = Mth.lerp(w, base[ch], p[ch]);
		}
		ModelPart ra = right ? m.rightArm : m.leftArm;
		ModelPart la = right ? m.leftArm : m.rightArm;
		ModelPart fl = right ? m.rightLeg : m.leftLeg;
		ModelPart bl = right ? m.leftLeg : m.rightLeg;
		float headX = m.head.xRot;
		float headY = m.head.yRot;

		float crouch = Mth.clamp(v[CROUCH], 0f, 1f) - (m.crouching ? 1f : 0f);
		if (crouch != 0f) {
			m.body.y += 3.2f * crouch;
			m.head.y += 4.2f * crouch;
			m.rightArm.y += 3.2f * crouch;
			m.leftArm.y += 3.2f * crouch;
			m.rightLeg.z += 4.0f * crouch;
			m.leftLeg.z += 4.0f * crouch;
			m.rightLeg.y += 0.2f * crouch;
			m.leftLeg.y += 0.2f * crouch;
		}
		float lean = v[LEAN] + Mth.clamp(v[CROUCH], 0f, 1f) * 0.25f;
		com.projecthero.mod.client.HipBend.pitch(m, 1f, lean);
		float twist = v[TWIST] * mir;
		m.body.yRot = twist;
		m.rightArm.z = Mth.sin(twist) * 5.0f + Mth.sin(lean) * 2.0f;
		m.rightArm.x = -Mth.cos(twist) * 5.0f;
		m.leftArm.z = -Mth.sin(twist) * 5.0f + Mth.sin(lean) * 2.0f;
		m.leftArm.x = Mth.cos(twist) * 5.0f;

		float aimX = aim > 0 ? -HALF_PI + headX : 0f;
		float aimY = aim > 0 ? headY : 0f;
		float sR = right ? 1f : -1f;
		ra.xRot = v[RX] + aimX;
		ra.yRot = -sR * v[RY] + twist + aimY;
		ra.zRot = sR * v[RZ];
		float sL = -sR;
		la.xRot = v[LX] + (aim == 2 ? aimX : 0f);
		la.yRot = -sL * v[LY] + twist + (aim == 2 ? aimY : 0f);
		la.zRot = sL * v[LZ];
		fl.xRot = fl.xRot * 0.3f * (1f - w) + v[FLEG];
		bl.xRot = bl.xRot * 0.3f * (1f - w) + v[BLEG];
		m.head.xRot = headX + v[HEAD] * w;
		m.hat.copyFrom(m.head);
	}

	// ================================================================== first person

	/** {tick, x, y, z, xDeg, yDeg, zDeg} for one hand. */
	private static final float[][] FP_JAB = { { 0, 0, 0, 0, 0, 0, 0 }, { 2.2f, -0.06f, 0.08f, -0.48f, -10, 0, 0 }, { 6, -0.05f, 0.06f, -0.42f, -8, 0, 0 },
			{ 11, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_HOOK = { { 0, 0.18f, -0.05f, 0.12f, 10, -35, 10 }, { 3.2f, 0.2f, -0.05f, 0.14f, 12, -40, 10 },
			{ 5.2f, -0.35f, 0.08f, -0.4f, -10, 55, -10 }, { 11, -0.33f, 0.06f, -0.36f, -8, 55, -10 }, { 18, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_UPPER = { { 0, 0.02f, -0.35f, 0.1f, 35, 0, 0 }, { 3, 0.02f, -0.38f, 0.1f, 38, 0, 0 },
			{ 5, -0.05f, 0.4f, -0.35f, -60, 0, 0 }, { 11, -0.05f, 0.36f, -0.33f, -55, 0, 0 }, { 17, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_HAYMAKER = { { 0, 0.05f, -0.15f, 0.3f, 25, -10, 10 }, { 2.5f, -0.06f, 0.1f, -0.55f, -12, 0, 0 },
			{ 11, -0.05f, 0.08f, -0.5f, -10, 0, 0 }, { 20, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_POINT = { { 0, 0, 0.55f, 0.05f, 55, 0, -10 }, { 5, 0, 0.6f, 0.05f, 58, 0, -10 },
			{ 7, -0.05f, 0.05f, -0.45f, -8, 0, 0 }, { 14, -0.05f, 0.05f, -0.42f, -6, 0, 0 }, { 20, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_OVERHEAD = { { 0, 0, 0.5f, 0.1f, 40, 0, -10 }, { 6, 0, 0.6f, 0.1f, 45, 0, -10 },
			{ 8, -0.1f, -0.35f, -0.3f, -80, 0, 5 }, { 15, -0.1f, -0.3f, -0.28f, -75, 0, 5 }, { 24, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_CLAP = { { 0, 0.35f, 0.05f, 0.05f, 0, -50, 10 }, { 4, 0.4f, 0.05f, 0.05f, 0, -55, 10 },
			{ 5.5f, -0.25f, 0.05f, -0.35f, 0, 35, -10 }, { 11, -0.24f, 0.05f, -0.33f, 0, 35, -10 }, { 17, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_THROW = { { 0, 0, 0.6f, 0.1f, 55, 0, -10 }, { 2.5f, -0.05f, 0.05f, -0.55f, -25, 0, 0 },
			{ 9, -0.05f, 0, -0.5f, -20, 0, 0 }, { 15, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_PUSH = { { 0, -0.05f, -0.05f, -0.1f, 0, 10, 0 }, { 3, -0.08f, 0.02f, -0.55f, -10, 10, 0 },
			{ 9, -0.07f, 0.02f, -0.5f, -8, 10, 0 }, { 15, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_ULT = { { 0, -0.15f, -0.1f, 0.1f, 10, 40, 0 }, { 5, 0, 0.7f, 0.1f, 65, 0, -15 }, { 16, 0, 0.75f, 0.1f, 68, 0, -15 },
			{ 18, 0.35f, 0.2f, 0.05f, 20, -45, 35 }, { 23.5f, 0.36f, 0.2f, 0.05f, 20, -45, 35 }, { 25, 0.4f, -0.25f, 0.05f, -20, -50, 40 },
			{ 34, 0.38f, -0.22f, 0.05f, -18, -48, 38 }, { 44, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_LEAN = { { 0, 0.05f, -0.2f, 0.15f, 30, 0, 0 }, { 6, 0.05f, -0.2f, 0.15f, 30, 0, 0 }, { 10, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_TEMPLE = { { 0, -0.1f, 0.45f, 0.15f, 60, 30, 0 }, { 6, -0.1f, 0.47f, 0.15f, 62, 30, 0 },
			{ 8, 0.3f, 0.05f, -0.2f, 0, -40, 15 }, { 14, 0.3f, 0.05f, -0.2f, 0, -40, 15 }, { 21, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_CHEST = { { 0, -0.2f, -0.05f, 0.05f, 10, 40, 0 }, { 5, -0.2f, -0.05f, 0.05f, 10, 40, 0 },
			{ 7, 0.1f, -0.3f, 0.1f, 25, -10, 20 }, { 16, 0.1f, -0.28f, 0.1f, 24, -10, 20 }, { 23, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_STOMP = { { 0, 0.15f, -0.1f, 0.05f, 10, 0, 25 }, { 4, 0.16f, -0.1f, 0.05f, 10, 0, 25 },
			{ 5.2f, 0.1f, -0.35f, 0.05f, 25, 0, 15 }, { 12, 0.1f, -0.33f, 0.05f, 24, 0, 15 }, { 19, 0, 0, 0, 0, 0, 0 } };

	/** The first-person tables for {@code anim}: {lead, other} (either may be null). */
	private static float[][][] fpFor(int anim) {
		return switch (anim) {
			case ShazamAnim.COMBO_1, ShazamAnim.RUSH_A, ShazamAnim.BLITZ -> new float[][][] { FP_JAB, null };
			case ShazamAnim.COMBO_2, ShazamAnim.RUSH_B -> new float[][][] { null, FP_JAB };
			case ShazamAnim.COMBO_3 -> new float[][][] { FP_HOOK, null };
			case ShazamAnim.UPPERCUT -> new float[][][] { FP_UPPER, null };
			case ShazamAnim.HAYMAKER -> new float[][][] { FP_HAYMAKER, null };
			case ShazamAnim.ZEUS_CALL -> new float[][][] { FP_POINT, null };
			case ShazamAnim.SLAM -> new float[][][] { FP_OVERHEAD, FP_OVERHEAD };
			case ShazamAnim.CLAP -> new float[][][] { FP_CLAP, FP_CLAP };
			case ShazamAnim.HURL -> new float[][][] { FP_THROW, FP_THROW };
			case ShazamAnim.SHOVE -> new float[][][] { FP_PUSH, FP_PUSH };
			case ShazamAnim.ULT -> new float[][][] { FP_ULT, FP_ULT };
			case ShazamAnim.DASH -> new float[][][] { FP_LEAN, FP_LEAN };
			case ShazamAnim.WISDOM -> new float[][][] { FP_TEMPLE, null };
			case ShazamAnim.COURAGE -> new float[][][] { FP_CHEST, FP_CHEST };
			case ShazamAnim.QUAKE -> new float[][][] { FP_STOMP, FP_STOMP };
			default -> null;
		};
	}

	/** How much (0..1) a move owns {@code p}'s first-person hands right now (vanilla's swing is held off by it). */
	public static float firstPersonWeight(Player p, float partial) {
		ShazamState s = live(p);
		if (s == null) {
			return 0f;
		}
		if (heldPose(p, s, partial)) {
			return 1f;
		}
		Clip c = clipFor(s.anim);
		if (c == null || fpFor(s.anim) == null) {
			return 0f;
		}
		return weight(c, age(p, c, s, partial));
	}

	/** First person, in the hand's space before the arm is drawn: the move's hand motion. */
	public static void firstPerson(Player p, HumanoidArm arm, float partial, PoseStack pose) {
		ShazamState s = live(p);
		if (s == null) {
			return;
		}
		boolean lead = arm == p.getMainArm();
		float[] v = null;
		float w = 0f;
		if (heldPose(p, s, partial)) {
			float held = p.level().getGameTime() - s.holdStart + partial;
			w = Mth.clamp(held / 4f, 0f, 1f);
			float tr = Mth.sin((p.tickCount + partial) * 3.1f) * 0.012f;
			v = switch (s.hold) {
				case ShazamAnim.HOLD_HAYMAKER -> {
					float b = ShazamMoves.haymakerCharge(p);
					yield lead ? new float[] { 0, 0.1f + tr * (1 + 3 * b), -0.15f - b * 0.05f, 0.28f + b * 0.08f, 25, -10, 10 } : null;
				}
				case ShazamAnim.HOLD_WRATH -> new float[] { 0, lead ? -0.12f : -0.12f, 0.12f + tr, -0.35f, -8, 18, 0 };
				case ShazamAnim.HOLD_GRIP -> new float[] { 0, -0.05f, 0.65f + tr, 0.05f, 62, 10, -12 };
				default -> null;
			};
		} else {
			Clip c = clipFor(s.anim);
			float[][][] t = fpFor(s.anim);
			if (c == null || t == null) {
				return;
			}
			float[][] table = lead ? t[0] : t[1];
			if (table == null) {
				return;
			}
			float age = age(p, c, s, partial);
			w = weight(c, age);
			v = sampleFp(table, age);
		}
		if (v == null || w <= 0.001f) {
			return;
		}
		float sg = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.translate(sg * v[1] * w, v[2] * w, v[3] * w);
		pose.mulPose(Axis.YP.rotationDegrees(sg * v[5] * w));
		pose.mulPose(Axis.XP.rotationDegrees(v[4] * w));
		pose.mulPose(Axis.ZP.rotationDegrees(sg * v[6] * w));
	}

	/** First-person sprint-flight hand: {x (+ = out, mirrored), y, z, xDeg, yDeg (+ = turned in), zDeg}, fist forward toward the middle. */
	private static final float[] FP_FLIGHT = { -0.22f, 0.42f, -0.5f, -12f, 14f, 0f };
	/** How far below its place a hand starts when it rises into view (the off hand, or a hand that held an item). */
	private static final float FP_FLIGHT_RISE = 0.75f;

	/**
	 * First person, in the hand's space before the arm is drawn: the sprint-flight hand at weight {@code w} (0..1). {@code fromBelow}:
	 * the hand wasn't on screen before (the off hand, or a hand whose item just dipped away), so it rises from under the view
	 * instead of moving over from vanilla's resting arm. A faint wind tremble on top.
	 */
	public static void firstPersonFlight(Player p, HumanoidArm arm, float partial, float w, boolean fromBelow, PoseStack pose) {
		if (w <= 0.001f) {
			return;
		}
		float sg = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float t = p.tickCount + partial;
		float tremble = Mth.sin(t * (arm == p.getMainArm() ? 0.93f : 0.81f)) * 0.006f * w;
		float drop = fromBelow ? (1f - w) * FP_FLIGHT_RISE : 0f;
		float k = fromBelow ? 1f : w;
		pose.translate(sg * FP_FLIGHT[0] * k, FP_FLIGHT[1] * k - drop + tremble, FP_FLIGHT[2] * k);
		pose.mulPose(Axis.YP.rotationDegrees(sg * FP_FLIGHT[4] * k));
		pose.mulPose(Axis.XP.rotationDegrees(FP_FLIGHT[3] * k - 25f * (fromBelow ? 1f - w : 0f)));
		pose.mulPose(Axis.ZP.rotationDegrees(sg * FP_FLIGHT[5] * k));
	}

	private static float[] sampleFp(float[][] fr, float tick) {
		if (tick <= fr[0][0]) {
			return fr[0];
		}
		for (int i = 1; i < fr.length; i++) {
			if (tick <= fr[i][0]) {
				float[] a = fr[i - 1];
				float[] b = fr[i];
				float t = Mth.clamp((tick - a[0]) / Math.max(1e-4f, b[0] - a[0]), 0f, 1f);
				t = t * t * (3f - 2f * t);
				float[] v = new float[7];
				for (int k = 1; k < 7; k++) {
					v[k] = Mth.lerp(t, a[k], b[k]);
				}
				return v;
			}
		}
		return fr[fr.length - 1];
	}
}
