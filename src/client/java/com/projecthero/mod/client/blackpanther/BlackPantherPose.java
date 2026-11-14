package com.projecthero.mod.client.blackpanther;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.data.BlackPantherState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * Black Panther's body language, keyed off the synced {@link BlackPantherState} so every viewer sees the same thing.
 *
 * <p>v0.17.4 (user: "make his ability animations really good too"): every move -- both kits -- is a key-framed clip with an
 * anticipation, a snap through the strike (strong ease-out), a follow-through and a recovery, faded in and out over vanilla's
 * own pose. Third person ({@link #apply}, from {@code HumanoidModelMixin}; the suit copies the posed model) drives both arms,
 * the torso twist (the shoulders move with it, the way vanilla's own swing moves them), a forward lean, the legs and a crouch
 * that borrows vanilla's sneak offsets. Flips and spins turn the whole body ({@link #flipDegrees} / {@link #spinDegrees}, from
 * {@code BlackPantherFlipMixin}). First person ({@link #firstPerson}, from {@code ItemInHandRendererBlackPantherMixin}) moves each
 * empty hand about the wrist with its own clip, written for the right hand and mirrored for the left.
 *
 * <p>Clips are written for a right-handed Black Panther; a left-handed one plays them mirrored. Angles in radians (model) or
 * degrees (first person); negative {@code xRot} swings an arm forward and up; a positive twist turns the chest to the right.
 */
public final class BlackPantherPose {
	/** Set while vanilla draws the first-person hand, so the third-person pose does not bend it. */
	public static boolean inFirstPersonHand;

	private static final float N = Float.NaN;
	// channels
	private static final int RX = 0, RY = 1, RZ = 2, LX = 3, LY = 4, LZ = 5, TWIST = 6, LEAN = 7, RLEG = 8, LLEG = 9, RLEGZ = 10, LLEGZ = 11,
			CROUCH = 12, HEAD = 13, CH = 14;
	private static final int SMOOTH = 0, SNAP = 1, LINEAR = 2;

	private BlackPantherPose() {
	}

	// ================================================================== clip data

	/** One key: when (ticks), how the segment into it eases, and the channel values (NaN = leave vanilla's). */
	private record Key(float t, int ease, float[] v) {
	}

	/** A clip: its keys, its length and how fast it fades in and out over vanilla's pose. */
	private record Clip(float duration, float in, float out, Key[] keys) {
	}

	private static Key k(float t, int ease, float rx, float ry, float rz, float lx, float ly, float lz, float twist, float lean, float rLeg,
			float lLeg, float crouch) {
		return new Key(t, ease, new float[] { rx, ry, rz, lx, ly, lz, twist, lean, rLeg, lLeg, N, N, crouch, N });
	}

	/** A key with the legs spread sideways and the head pitched too. */
	private static Key kx(float t, int ease, float rx, float ry, float rz, float lx, float ly, float lz, float twist, float lean, float rLeg,
			float lLeg, float rLegZ, float lLegZ, float crouch, float head) {
		return new Key(t, ease, new float[] { rx, ry, rz, lx, ly, lz, twist, lean, rLeg, lLeg, rLegZ, lLegZ, crouch, head });
	}

	private static Clip clip(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, keys);
	}

	// the fighting guard, out of the suit: lead (left) fist high, rear fist by the chin
	private static final float[] GUARD = { -1.15f, -0.4f, 0.12f, -1.45f, 0.5f, -0.1f };

	// ---- unsuited
	private static final Clip COMBO = clip(22f, 1.5f, 4f,
			k(0f, SMOOTH, GUARD[0], GUARD[1], GUARD[2], GUARD[3], GUARD[4], GUARD[5], 0f, 0.05f, N, N, 0.15f),
			// the jab: the lead fist snaps out, the chest turns into it
			k(1.6f, SNAP, GUARD[0], GUARD[1], GUARD[2], -1.62f, 0.12f, 0f, 0.32f, 0.12f, N, N, 0.15f),
			k(3.6f, SMOOTH, GUARD[0], GUARD[1], GUARD[2], GUARD[3], GUARD[4], GUARD[5], 0.05f, 0.08f, N, N, 0.15f),
			// the cross: the rear fist drives through, the hips turn the other way
			k(6.6f, SNAP, -1.62f, -0.12f, 0f, -1.0f, 0.6f, -0.2f, -0.45f, 0.16f, N, N, 0.2f),
			k(9f, SMOOTH, -1.0f, -0.3f, 0.4f, -1.0f, 0.3f, -0.4f, -0.1f, 0.0f, 0.1f, -0.1f, 0.35f),
			// the spinning kick: arms out for balance, the right leg whips round (the body spins, BlackPantherFlipMixin)
			k(11.5f, SMOOTH, -0.4f, 0f, 1.1f, -0.4f, 0f, -1.1f, 0f, -0.15f, -1.55f, 0.2f, 0.1f),
			k(14.5f, SNAP, -0.3f, 0f, 1.25f, -0.3f, 0f, -1.25f, 0f, -0.2f, -1.6f, 0.25f, 0.05f),
			k(17f, SMOOTH, GUARD[0], GUARD[1], GUARD[2], GUARD[3], GUARD[4], GUARD[5], 0f, 0.05f, N, N, 0.15f));
	private static final Clip SWEEP = clip(15f, 1.5f, 4f,
			k(2f, SMOOTH, -0.6f, 0f, 0.6f, -0.35f, 0f, -0.45f, 0f, 0.35f, -0.3f, 0.2f, 1f),
			kx(5f, SNAP, -0.4f, 0f, 1.2f, -0.2f, 0f, -0.3f, 0f, 0.4f, -0.4f, 0.35f, 1.3f, -0.1f, 1f, N),
			kx(9f, SMOOTH, -0.5f, 0f, 1.1f, -0.25f, 0f, -0.35f, 0f, 0.4f, -0.35f, 0.3f, 1.1f, -0.1f, 1f, N),
			k(12f, SMOOTH, GUARD[0], GUARD[1], GUARD[2], GUARD[3], GUARD[4], GUARD[5], 0f, 0.1f, N, N, 0.3f));
	private static final Clip TAKEDOWN = clip(19f, 1f, 4f,
			// grab: both hands shoot out
			k(2.5f, SNAP, -1.55f, 0.18f, 0f, -1.55f, -0.18f, 0f, 0f, 0.2f, N, N, 0.25f),
			// up and over: the arms haul it over his head as he springs
			kx(6f, SMOOTH, -2.9f, 0.15f, 0.1f, -2.9f, -0.15f, -0.1f, 0f, -0.15f, -0.4f, 0.3f, N, N, 0f, -0.4f),
			// the slam: everything comes down in front of him
			k(9.5f, SNAP, -0.55f, 0.2f, 0.1f, -0.55f, -0.2f, -0.1f, 0f, 0.55f, -0.6f, 0.4f, 0.9f),
			k(13f, SMOOTH, -0.7f, 0.1f, 0.2f, -0.7f, -0.1f, -0.2f, 0f, 0.4f, -0.5f, 0.35f, 0.7f));
	private static final Clip DISARM = clip(12f, 1f, 4f,
			k(2f, SMOOTH, -2.5f, -0.35f, 0.1f, -1.45f, 0.35f, 0f, 0.3f, 0f, N, N, 0.1f),
			k(3.8f, SNAP, -0.65f, -0.65f, 0f, -1.4f, 0.3f, 0f, -0.35f, 0.2f, N, N, 0.25f),
			k(7f, SMOOTH, -0.8f, -0.5f, 0.1f, -1.3f, 0.25f, 0f, -0.25f, 0.15f, N, N, 0.2f));
	private static final Clip VAULT = clip(17f, 1f, 3f,
			k(1.5f, SNAP, -2.6f, 0f, 0.2f, -2.6f, 0f, -0.2f, 0f, 0.1f, 0.4f, -0.3f, 0.1f),
			k(5f, SMOOTH, -2.0f, 0f, 0.3f, -2.0f, 0f, -0.3f, 0f, 0.4f, -1.3f, -1.3f, 0.6f),
			k(11f, SMOOTH, -1.9f, 0f, 0.3f, -1.9f, 0f, -0.3f, 0f, 0.4f, -1.3f, -1.3f, 0.6f),
			k(14f, SMOOTH, -0.5f, 0f, 0.5f, -0.5f, 0f, -0.5f, 0f, 0.15f, -0.3f, 0.2f, 0.4f));
	private static final Clip BACKFLIP = clip(17f, 1f, 3f,
			kx(1.5f, SNAP, -3.0f, 0f, 0.15f, -3.0f, 0f, -0.15f, 0f, -0.2f, 0.2f, 0.2f, N, N, 0.0f, -0.6f),
			k(5f, SMOOTH, -1.6f, 0f, 0.3f, -1.6f, 0f, -0.3f, 0f, 0.3f, -1.4f, -1.4f, 0.6f),
			k(11f, SMOOTH, -1.6f, 0f, 0.3f, -1.6f, 0f, -0.3f, 0f, 0.3f, -1.4f, -1.4f, 0.6f),
			k(14f, SMOOTH, -1.0f, 0f, 0.7f, -1.0f, 0f, -0.7f, 0f, 0.15f, -0.3f, 0.2f, 0.45f));
	private static final Clip KNIFE = clip(11f, 1f, 3f,
			k(3f, SMOOTH, -2.85f, 0.25f, 0.3f, -0.9f, 0.4f, -0.2f, 0.4f, -0.05f, N, N, 0.1f),
			k(4.8f, SNAP, -1.25f, -0.35f, 0f, -0.4f, 0.2f, -0.3f, -0.35f, 0.15f, N, N, 0.15f),
			k(7f, SMOOTH, -1.0f, -0.4f, 0f, -0.4f, 0.1f, -0.3f, -0.3f, 0.1f, N, N, 0.1f));
	private static final Clip SCAN = clip(16f, 2f, 4f,
			k(3f, SMOOTH, -1.62f, -0.05f, 0f, -1.35f, 0.62f, 0f, 0.1f, 0f, N, N, 0f),
			k(12f, LINEAR, -1.6f, 0.05f, 0f, -1.35f, 0.6f, 0f, 0.1f, 0f, N, N, 0f));
	private static final Clip RIPOSTE = clip(11f, 0.5f, 4f,
			k(1.5f, SNAP, -1.62f, -0.2f, 0f, -1.7f, 0.5f, -0.1f, -0.45f, 0.2f, N, N, 0.3f),
			k(5f, SMOOTH, -1.4f, -0.3f, 0.1f, -1.5f, 0.5f, -0.1f, -0.3f, 0.15f, N, N, 0.25f));
	private static final Clip RESOLVE = clip(26f, 2f, 5f,
			// arms crossed over the chest, fists at the shoulders
			k(4f, SMOOTH, -2.05f, -0.9f, 0.05f, -2.05f, 0.9f, -0.05f, 0f, 0.1f, N, N, 0.1f),
			k(10f, LINEAR, -2.05f, -0.9f, 0.05f, -2.05f, 0.9f, -0.05f, 0f, 0.15f, N, N, 0.2f),
			// thrown wide, chest out, head back
			kx(13f, SNAP, -0.35f, 0f, 1.35f, -0.35f, 0f, -1.35f, 0f, -0.2f, N, N, 0.12f, -0.12f, 0f, -0.45f),
			kx(20f, SMOOTH, -0.25f, 0f, 1.2f, -0.25f, 0f, -1.2f, 0f, -0.15f, N, N, 0.1f, -0.1f, 0f, -0.35f));

	// ---- suited
	private static final Clip SUIT_UP = clip(BlackPantherConfig.SUIT_UP_TICKS + 4f, 2f, 6f,
			// the right hand to the necklace...
			k(6f, SMOOTH, -1.25f, -0.75f, 0f, 0.05f, 0f, -0.1f, 0f, 0.05f, N, N, 0f),
			k(12f, LINEAR, -1.25f, -0.75f, 0f, 0.05f, 0f, -0.1f, 0f, 0.05f, N, N, 0f),
			// ...then the arms open as the suit runs out over them, head back as the mask closes
			kx(26f, SMOOTH, -0.15f, 0f, 0.55f, -0.15f, 0f, -0.55f, 0f, -0.05f, N, N, 0.08f, -0.08f, 0f, -0.25f),
			kx(36f, SMOOTH, -0.55f, -0.2f, 0.25f, -0.55f, 0.2f, -0.25f, 0f, 0.1f, N, N, 0.05f, -0.05f, 0.2f, 0.1f));
	private static final Clip SUIT_DOWN = clip(BlackPantherConfig.SUIT_DOWN_TICKS + 4f, 2f, 5f,
			kx(8f, SMOOTH, -0.15f, 0f, 0.55f, -0.15f, 0f, -0.55f, 0f, -0.05f, N, N, 0.08f, -0.08f, 0f, -0.2f),
			k(22f, SMOOTH, -1.25f, -0.75f, 0f, 0.05f, 0f, -0.1f, 0f, 0.05f, N, N, 0f),
			k(30f, LINEAR, -1.25f, -0.75f, 0f, 0.05f, 0f, -0.1f, 0f, 0.05f, N, N, 0f));
	private static final int S = BlackPantherConfig.CLAW_STEP;
	private static final Clip CLAW = clip(S * 3 + 4f, 1f, 4f,
			// slash 1: the right claws, high outside to low across
			k(0.8f, SMOOTH, -2.65f, 0.75f, 0.45f, -1.3f, 0.4f, -0.1f, 0.35f, -0.05f, N, N, 0.2f),
			k(2.6f, SNAP, -0.65f, -0.85f, -0.2f, -1.2f, 0.3f, -0.2f, -0.45f, 0.25f, N, N, 0.3f),
			// slash 2: the left claws, the mirror
			k(S + 0.8f, SMOOTH, -1.0f, -0.6f, 0f, -2.65f, -0.75f, -0.45f, -0.4f, 0.0f, N, N, 0.25f),
			k(S + 2.6f, SNAP, -1.1f, -0.3f, 0.2f, -0.65f, 0.85f, 0.2f, 0.45f, 0.25f, N, N, 0.3f),
			// slash 3: both arms high outside the shoulders, raking down into an X
			k(S * 2 + 1.2f, SMOOTH, -2.95f, 0.45f, 0.3f, -2.95f, -0.45f, -0.3f, 0f, -0.2f, N, N, 0.1f),
			k(S * 2 + 3f, SNAP, -0.45f, -0.7f, 0f, -0.45f, 0.7f, 0f, 0f, 0.5f, -0.5f, 0.35f, 0.75f),
			k(S * 2 + 6f, SMOOTH, -0.6f, -0.6f, 0f, -0.6f, 0.6f, 0f, 0f, 0.4f, -0.45f, 0.3f, 0.6f));
	private static final Clip PULSE = clip(16f, 1f, 5f,
			// gather: fists crossed in, low
			k(3.5f, SMOOTH, -1.2f, -0.6f, 0f, -1.2f, 0.6f, 0f, 0f, 0.25f, N, N, 0.55f),
			// release: everything flung out
			kx(5.5f, SNAP, -0.2f, 0f, 1.4f, -0.2f, 0f, -1.4f, 0f, -0.25f, N, N, 0.25f, -0.25f, 0f, -0.35f),
			kx(10f, SMOOTH, -0.25f, 0f, 1.25f, -0.25f, 0f, -1.25f, 0f, -0.15f, N, N, 0.2f, -0.2f, 0f, -0.25f));
	private static final Clip OVERLOAD = clip(28f, 2f, 6f,
			k(6f, SMOOTH, -1.25f, -0.62f, 0f, -1.25f, 0.62f, 0f, 0f, 0.35f, N, N, 0.9f),
			k(12f, LINEAR, -1.3f, -0.6f, 0f, -1.3f, 0.6f, 0f, 0f, 0.4f, N, N, 1f),
			kx(14f, SNAP, -2.6f, 0f, 0.7f, -2.6f, 0f, -0.7f, 0f, -0.35f, N, N, 0.4f, -0.4f, 0f, -0.6f),
			kx(22f, SMOOTH, -2.4f, 0f, 0.6f, -2.4f, 0f, -0.6f, 0f, -0.25f, N, N, 0.3f, -0.3f, 0f, -0.4f));
	private static final Clip POUNCE = clip(16f, 1f, 3f,
			k(2.5f, SMOOTH, 0.4f, 0f, 0.3f, 0.4f, 0f, -0.3f, 0f, 0.5f, -0.4f, 0.3f, 1f),
			k(5f, SNAP, -2.65f, 0.15f, 0.1f, -2.65f, -0.15f, -0.1f, 0f, 0.35f, 0.7f, 0.45f, 0f),
			k(12f, SMOOTH, -2.5f, 0.15f, 0.1f, -2.5f, -0.15f, -0.1f, 0f, 0.35f, 0.6f, 0.4f, 0f));
	private static final Clip POUNCE_STRIKE = clip(12f, 0.5f, 4f,
			k(1.2f, SMOOTH, -2.95f, 0.45f, 0.3f, -2.95f, -0.45f, -0.3f, 0f, -0.15f, N, N, 0.1f),
			k(3f, SNAP, -0.45f, -0.7f, 0f, -0.45f, 0.7f, 0f, 0f, 0.5f, -0.5f, 0.35f, 0.75f),
			k(7f, SMOOTH, -0.6f, -0.6f, 0f, -0.6f, 0.6f, 0f, 0f, 0.4f, -0.45f, 0.3f, 0.6f));
	private static final Clip LEAP = clip(16f, 1f, 4f,
			k(1.5f, SMOOTH, 0.5f, 0f, 0.25f, 0.5f, 0f, -0.25f, 0f, 0.4f, -0.3f, 0.2f, 0.9f),
			kx(4f, SNAP, -2.9f, 0f, 0.2f, -2.9f, 0f, -0.2f, 0f, -0.1f, 0.3f, 0.3f, N, N, 0f, -0.4f),
			k(12f, SMOOTH, -2.2f, 0f, 0.35f, -2.2f, 0f, -0.35f, 0f, 0.1f, -0.9f, -0.5f, 0.2f));
	private static final Clip DROP = clip(30f, 1f, 2f,
			k(2f, SNAP, -2.95f, 0f, 0.1f, -2.95f, 0f, -0.1f, 0f, 0.1f, 0f, 0f, 0f),
			k(30f, LINEAR, -2.95f, 0f, 0.1f, -2.95f, 0f, -0.1f, 0f, 0.1f, 0f, 0f, 0f));
	/** The landing after Drop Strike: one fist to the ground, the other arm back, a knee down. */
	private static final Clip LANDING = clip(18f, 0.5f, 6f,
			k(1f, SNAP, -0.45f, 0f, 0.25f, 0.7f, 0f, -0.55f, 0f, 0.55f, -1.25f, 0.45f, 1f),
			k(11f, LINEAR, -0.45f, 0f, 0.25f, 0.7f, 0f, -0.55f, 0f, 0.5f, -1.2f, 0.45f, 1f));
	private static final Clip THROW = clip(11f, 1f, 3f,
			k(3f, SMOOTH, -2.85f, 0.3f, 0.35f, -1.0f, 0.4f, -0.2f, 0.45f, -0.05f, N, N, 0.15f),
			k(4.8f, SNAP, -1.1f, -0.45f, 0f, -0.4f, 0.2f, -0.3f, -0.45f, 0.2f, N, N, 0.2f),
			k(7f, SMOOTH, -0.95f, -0.45f, 0f, -0.4f, 0.1f, -0.3f, -0.35f, 0.1f, N, N, 0.15f));
	private static final Clip FLASH = clip(11f, 1f, 3f,
			k(2.5f, SMOOTH, 0.7f, 0f, 0.2f, -0.6f, 0.2f, -0.2f, 0.2f, 0.1f, N, N, 0.25f),
			k(4.5f, SNAP, -1.9f, -0.1f, 0f, -0.5f, 0.2f, -0.2f, -0.15f, 0f, N, N, 0.1f));
	private static final Clip RETURN = clip(12f, 0.5f, 4f,
			k(1.5f, SNAP, -1.6f, 0f, 0f, -1.0f, 0.45f, 0f, -0.3f, 0.1f, N, N, 0.2f),
			k(6f, SMOOTH, -1.55f, 0f, 0f, -1.0f, 0.45f, 0f, -0.25f, 0.05f, N, N, 0.15f));

	// held stances (weight follows the toggle, no clip timing)
	private static final float[] ABSORB = { -1.25f, -0.62f, 0f, -1.25f, 0.62f, 0f, 0f, 0.2f, N, N, N, N, 0.35f, N };
	private static final float[] PARRY = { -1.0f, -0.25f, 0.15f, -1.95f, 0.55f, 0.05f, 0.2f, 0.1f, N, N, N, N, 0.3f, N };

	private static Clip clipFor(int animId) {
		return switch (animId) {
			case BlackPantherState.ANIM_COMBO -> COMBO;
			case BlackPantherState.ANIM_SWEEP -> SWEEP;
			case BlackPantherState.ANIM_TAKEDOWN -> TAKEDOWN;
			case BlackPantherState.ANIM_DISARM -> DISARM;
			case BlackPantherState.ANIM_VAULT -> VAULT;
			case BlackPantherState.ANIM_BACKFLIP -> BACKFLIP;
			case BlackPantherState.ANIM_KNIFE -> KNIFE;
			case BlackPantherState.ANIM_SCAN -> SCAN;
			case BlackPantherState.ANIM_RIPOSTE -> RIPOSTE;
			case BlackPantherState.ANIM_RESOLVE -> RESOLVE;
			case BlackPantherState.ANIM_SUIT_UP -> SUIT_UP;
			case BlackPantherState.ANIM_SUIT_DOWN -> SUIT_DOWN;
			case BlackPantherState.ANIM_CLAW -> CLAW;
			case BlackPantherState.ANIM_PULSE -> PULSE;
			case BlackPantherState.ANIM_OVERLOAD -> OVERLOAD;
			case BlackPantherState.ANIM_POUNCE -> POUNCE;
			case BlackPantherState.ANIM_POUNCE_STRIKE -> POUNCE_STRIKE;
			case BlackPantherState.ANIM_LEAP -> LEAP;
			case BlackPantherState.ANIM_DROP -> DROP;
			case BlackPantherState.ANIM_THROW -> THROW;
			case BlackPantherState.ANIM_FLASH -> FLASH;
			case BlackPantherState.ANIM_RETURN -> RETURN;
			default -> null;
		};
	}

	// ================================================================== sampling

	private static float ease(int mode, float t) {
		return switch (mode) {
			case SNAP -> 1f - (1f - t) * (1f - t) * (1f - t); // fast out of the wind-up, settling into the hit
			case LINEAR -> t;
			default -> t * t * (3f - 2f * t);
		};
	}

	/** The clip's weight at {@code time}: faded in from 0, out after the last key. */
	private static float weight(Clip c, float time) {
		if (time < 0f || time >= c.duration) {
			return 0f;
		}
		float in = c.in <= 0f ? 1f : Mth.clamp(time / c.in, 0f, 1f);
		float last = c.keys[c.keys.length - 1].t;
		float out = time <= last ? 1f : 1f - Mth.clamp((time - last) / Math.max(0.01f, Math.min(c.out, c.duration - last)), 0f, 1f);
		return ease(SMOOTH, in) * ease(SMOOTH, out);
	}

	/** The clip's channels at {@code time} ({@code base}: vanilla's values, used before the first key and for NaN). */
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
			float va = a == null ? base[ch] : val(a.v[ch], base[ch]);
			if (b == null) {
				out[ch] = va;
				continue;
			}
			float vb = val(b.v[ch], base[ch]);
			float ta = a == null ? 0f : a.t;
			float t = b.t <= ta ? 1f : Mth.clamp((time - ta) / (b.t - ta), 0f, 1f);
			out[ch] = Mth.lerp(ease(b.ease, t), va, vb);
		}
		return out;
	}

	private static float val(float v, float base) {
		return Float.isNaN(v) ? base : v;
	}

	// ================================================================== third person

	public static void apply(Player player, HumanoidModel<?> model) {
		if (inFirstPersonHand) {
			return; // the first-person hand: firstPerson() poses it
		}
		BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean right = player.getMainArm() == HumanoidArm.RIGHT;
		float[] base = base(model, right);
		float[] pose = null;
		float w = 0f;

		float age = player.level().getGameTime() - s.animStart + partial;
		Clip c = clipFor(s.animId);
		if (c == DROP && !player.onGround() && age >= c.duration) {
			age = c.duration - 1f; // still diving: hold the dive
		}
		if (c == DROP && (player.onGround() || player.isInWater()) && s.pulseStart >= s.animStart) {
			c = LANDING;
			age = player.level().getGameTime() - s.pulseStart + partial;
		}
		if (c != null && age >= 0f) {
			w = weight(c, age);
			if (w > 0.001f) {
				pose = sample(c, age, base);
			}
		}
		// the held stances win over a finished clip
		if (s.absorbing) {
			float[] held = s.suited ? ABSORB : PARRY;
			float[] hp = new float[CH];
			for (int ch = 0; ch < CH; ch++) {
				hp[ch] = val(held[ch], base[ch]);
			}
			if (s.animId == BlackPantherState.ANIM_RIPOSTE && w > 0.001f) {
				// the riposte plays over the parry guard
				for (int ch = 0; ch < CH; ch++) {
					hp[ch] = Mth.lerp(w, hp[ch], pose[ch]);
				}
			}
			pose = hp;
			w = 1f;
		}
		if (pose == null || w <= 0.001f) {
			return;
		}
		write(model, base, pose, w, right);
	}

	/** Vanilla's pose as channels (mirrored into the right-handed frame for a left-handed player). */
	private static float[] base(HumanoidModel<?> m, boolean right) {
		float[] b = new float[CH];
		float mir = right ? 1f : -1f;
		var ra = right ? m.rightArm : m.leftArm;
		var la = right ? m.leftArm : m.rightArm;
		var rl = right ? m.rightLeg : m.leftLeg;
		var ll = right ? m.leftLeg : m.rightLeg;
		b[RX] = ra.xRot;
		b[RY] = ra.yRot * mir;
		b[RZ] = ra.zRot * mir;
		b[LX] = la.xRot;
		b[LY] = la.yRot * mir;
		b[LZ] = la.zRot * mir;
		b[TWIST] = m.body.yRot * mir;
		b[LEAN] = m.crouching ? 0f : m.body.xRot;
		b[RLEG] = rl.xRot;
		b[LLEG] = ll.xRot;
		b[RLEGZ] = rl.zRot * mir;
		b[LLEGZ] = ll.zRot * mir;
		b[CROUCH] = m.crouching ? 1f : 0f;
		b[HEAD] = m.head.xRot;
		return b;
	}

	private static void write(HumanoidModel<?> m, float[] base, float[] p, float w, boolean right) {
		float mir = right ? 1f : -1f;
		float[] v = new float[CH];
		for (int ch = 0; ch < CH; ch++) {
			v[ch] = Mth.lerp(w, base[ch], p[ch]);
		}
		var ra = right ? m.rightArm : m.leftArm;
		var la = right ? m.leftArm : m.rightArm;
		var rl = right ? m.rightLeg : m.leftLeg;
		var ll = right ? m.leftLeg : m.rightLeg;

		// the crouch: vanilla's sneak offsets, by weight (the vanilla sneak itself is already in the base)
		float crouch = Mth.clamp(v[CROUCH], 0f, 1f) - (m.crouching ? 1f : 0f);
		float lean = v[LEAN] + Mth.clamp(v[CROUCH], 0f, 1f) * 0.5f;
		m.body.xRot = lean;
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
		// a lean past the sneak's own: the hips would swing back off the legs, so the legs follow them back and everything above
		// the hips drops to stay on top of the legs
		float sneakLean = Mth.clamp(v[CROUCH], 0f, 1f) * 0.5f;
		if (v[LEAN] != 0f) {
			float back = 12f * (Mth.sin(lean) - Mth.sin(sneakLean));
			float drop = 12f * (Mth.cos(sneakLean) - Mth.cos(lean));
			m.rightLeg.z += back;
			m.leftLeg.z += back;
			m.body.y += drop;
			m.head.y += drop;
			m.rightArm.y += drop;
			m.leftArm.y += drop;
		}
		// the twist carries the shoulders round with the chest (as vanilla's own swing does)
		float twist = v[TWIST] * mir;
		m.body.yRot = twist;
		m.rightArm.z = Mth.sin(twist) * 5.0f;
		m.rightArm.x = -Mth.cos(twist) * 5.0f;
		m.leftArm.z = -Mth.sin(twist) * 5.0f;
		m.leftArm.x = Mth.cos(twist) * 5.0f;
		// the lean tips the shoulders forward too: keep the arms on the leaning chest
		if (v[LEAN] != 0f) {
			float dz = Mth.sin(v[LEAN]) * 2.0f; // the shoulders sit 2 px down the chest from the neck pivot (the sneak's own lean is vanilla's)
			m.rightArm.z += dz;
			m.leftArm.z += dz;
		}

		ra.xRot = v[RX];
		ra.yRot = v[RY] * mir + twist;
		ra.zRot = v[RZ] * mir;
		la.xRot = v[LX];
		la.yRot = v[LY] * mir + twist;
		la.zRot = v[LZ] * mir;
		rl.xRot = v[RLEG];
		ll.xRot = v[LLEG];
		rl.zRot = v[RLEGZ] * mir;
		ll.zRot = v[LLEGZ] * mir;
		m.head.xRot = v[HEAD];
	}

	// ================================================================== whole-body flips and spins (BlackPantherFlipMixin)

	/** Head-over-heels about the hips: Vault forward (negative), Backflip backward. 0 when none plays. */
	public static float flipDegrees(Player player, float partial) {
		BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		return switch (s.animId) {
			case BlackPantherState.ANIM_VAULT -> -360f * ease(SMOOTH, Mth.clamp((age - 1.5f) / 11f, 0f, 1f)) * (age < 17f ? 1f : 0f);
			case BlackPantherState.ANIM_BACKFLIP -> 360f * ease(SMOOTH, Mth.clamp((age - 1.5f) / 11f, 0f, 1f)) * (age < 17f ? 1f : 0f);
			default -> 0f;
		};
	}

	/** A turn about the body's own axis: the combo's spinning kick and the Leg Sweep. 0 when none plays. */
	public static float spinDegrees(Player player, float partial) {
		BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		float dir = player.getMainArm() == HumanoidArm.RIGHT ? -1f : 1f;
		return switch (s.animId) {
			case BlackPantherState.ANIM_COMBO -> age < 17f ? dir * 360f * ease(SMOOTH, Mth.clamp((age - 9.5f) / 6f, 0f, 1f)) : 0f;
			case BlackPantherState.ANIM_SWEEP -> age < 15f ? dir * 360f * ease(SMOOTH, Mth.clamp((age - 2f) / 8f, 0f, 1f)) : 0f;
			default -> 0f;
		};
	}

	// ================================================================== first person

	/** One first-person key: the lead (main) hand's and the off hand's {dx, dy, dz, pitch, yaw, roll} (null = rest). */
	private record FKey(float t, int ease, float[] lead, float[] off) {
	}

	private record FClip(float duration, float in, float out, FKey[] keys) {
	}

	private static FKey fk(float t, int ease, float[] lead, float[] off) {
		return new FKey(t, ease, lead, off);
	}

	private static float[] f(float dx, float dy, float dz, float rx, float ry, float rz) {
		return new float[] { dx, dy, dz, rx, ry, rz };
	}

	private static final float[] REST = f(0, 0, 0, 0, 0, 0);
	private static final float[] F_GUARD_LEAD = f(-0.08f, 0.05f, 0.05f, 10f, 0f, 0f);
	private static final float[] F_GUARD_OFF = f(-0.12f, 0.12f, -0.05f, 15f, 5f, 0f);

	private static final FClip F_COMBO = new FClip(22f, 1.5f, 4f, new FKey[] {
			fk(0f, SMOOTH, F_GUARD_LEAD, F_GUARD_OFF),
			fk(1.6f, SNAP, F_GUARD_LEAD, f(-0.38f, 0.12f, -0.4f, 12f, 0f, 0f)),
			fk(3.6f, SMOOTH, F_GUARD_LEAD, F_GUARD_OFF),
			fk(6.6f, SNAP, f(-0.42f, 0.14f, -0.42f, 10f, 5f, 0f), f(-0.1f, 0.05f, 0.05f, 5f, 0f, 0f)),
			fk(9f, SMOOTH, f(0.05f, -0.05f, 0.05f, 0f, 0f, 0f), f(0.05f, -0.05f, 0.05f, 0f, 0f, 0f)),
			fk(12f, SMOOTH, f(0.35f, -0.25f, 0.1f, -10f, -40f, 35f), f(0.35f, -0.25f, 0.1f, -10f, -40f, 35f)),
			fk(15f, SNAP, f(0.4f, -0.35f, 0.15f, -15f, -45f, 45f), f(0.4f, -0.35f, 0.15f, -15f, -45f, 45f)),
			fk(17f, SMOOTH, F_GUARD_LEAD, F_GUARD_OFF) });
	private static final FClip F_SWEEP = new FClip(15f, 1.5f, 4f, new FKey[] {
			fk(3f, SMOOTH, f(0.25f, -0.35f, 0f, -20f, 0f, 40f), f(0.25f, -0.4f, 0f, -25f, 0f, 45f)),
			fk(10f, LINEAR, f(0.25f, -0.35f, 0f, -20f, 0f, 40f), f(0.25f, -0.4f, 0f, -25f, 0f, 45f)) });
	private static final FClip F_TAKEDOWN = new FClip(19f, 1f, 4f, new FKey[] {
			fk(2.5f, SNAP, f(-0.25f, 0.05f, -0.35f, 15f, 0f, 0f), f(-0.25f, 0.05f, -0.35f, 15f, 0f, 0f)),
			fk(6f, SMOOTH, f(-0.15f, 0.5f, -0.05f, 65f, 0f, 0f), f(-0.15f, 0.5f, -0.05f, 65f, 0f, 0f)),
			fk(9.5f, SNAP, f(-0.2f, -0.32f, -0.38f, -35f, 0f, 0f), f(-0.2f, -0.32f, -0.38f, -35f, 0f, 0f)),
			fk(13f, SMOOTH, f(-0.15f, -0.25f, -0.3f, -25f, 0f, 0f), f(-0.15f, -0.25f, -0.3f, -25f, 0f, 0f)) });
	private static final FClip F_DISARM = new FClip(12f, 1f, 4f, new FKey[] {
			fk(2f, SMOOTH, f(0.05f, 0.38f, 0.05f, 55f, 0f, -20f), f(-0.22f, 0.02f, -0.3f, 10f, 0f, 0f)),
			fk(3.8f, SNAP, f(-0.42f, -0.15f, -0.28f, -35f, 20f, 30f), f(-0.2f, 0f, -0.3f, 10f, 0f, 0f)),
			fk(7f, SMOOTH, f(-0.35f, -0.12f, -0.22f, -25f, 15f, 25f), f(-0.15f, 0f, -0.2f, 5f, 0f, 0f)) });
	private static final FClip F_TUCK = new FClip(17f, 1f, 3f, new FKey[] {
			fk(2f, SNAP, f(-0.15f, 0.25f, 0.1f, 35f, 0f, 0f), f(-0.15f, 0.25f, 0.1f, 35f, 0f, 0f)),
			fk(12f, LINEAR, f(-0.15f, 0.2f, 0.1f, 30f, 0f, 0f), f(-0.15f, 0.2f, 0.1f, 30f, 0f, 0f)) });
	private static final FClip F_BACKFLIP = new FClip(17f, 1f, 3f, new FKey[] {
			fk(2f, SNAP, f(0f, 0.55f, 0.1f, 75f, 0f, 0f), f(0f, 0.55f, 0.1f, 75f, 0f, 0f)),
			fk(6f, SMOOTH, f(-0.15f, 0.2f, 0.1f, 30f, 0f, 0f), f(-0.15f, 0.2f, 0.1f, 30f, 0f, 0f)),
			fk(12f, LINEAR, f(-0.15f, 0.2f, 0.1f, 30f, 0f, 0f), f(-0.15f, 0.2f, 0.1f, 30f, 0f, 0f)) });
	private static final FClip F_THROW = new FClip(11f, 1f, 3f, new FKey[] {
			fk(3f, SMOOTH, f(0.12f, 0.32f, 0.15f, 60f, -10f, 0f), f(0.05f, -0.1f, 0f, -10f, 0f, 0f)),
			fk(4.8f, SNAP, f(-0.25f, 0.05f, -0.42f, -10f, 10f, 0f), f(0.05f, -0.1f, 0f, -10f, 0f, 0f)),
			fk(7f, SMOOTH, f(-0.2f, 0f, -0.3f, -10f, 8f, 0f), REST) });
	private static final FClip F_FLASH = new FClip(11f, 1f, 3f, new FKey[] {
			fk(2.5f, SMOOTH, f(0.05f, -0.3f, -0.05f, -40f, 0f, 0f), null),
			fk(4.5f, SNAP, f(-0.2f, 0.22f, -0.4f, 30f, 0f, 0f), null) });
	private static final FClip F_SCAN = new FClip(16f, 2f, 4f, new FKey[] {
			fk(3f, SMOOTH, f(-0.22f, 0.25f, -0.25f, 40f, 0f, 0f), f(-0.38f, 0.12f, -0.2f, 20f, 30f, 0f)),
			fk(12f, LINEAR, f(-0.22f, 0.27f, -0.25f, 40f, 0f, 0f), f(-0.38f, 0.12f, -0.2f, 20f, 30f, 0f)) });
	private static final FClip F_RIPOSTE = new FClip(11f, 0.5f, 4f, new FKey[] {
			fk(1.5f, SNAP, f(-0.36f, 0.1f, -0.46f, 5f, 10f, 0f), f(-0.4f, 0.35f, -0.05f, 30f, 0f, -45f)),
			fk(5f, SMOOTH, f(-0.3f, 0.08f, -0.35f, 5f, 8f, 0f), f(-0.4f, 0.32f, -0.05f, 28f, 0f, -45f)) });
	private static final FClip F_RESOLVE = new FClip(26f, 2f, 5f, new FKey[] {
			fk(4f, SMOOTH, f(-0.5f, 0.22f, 0.05f, 20f, -40f, -45f), f(-0.5f, 0.22f, 0.05f, 20f, -40f, -45f)),
			fk(10f, LINEAR, f(-0.5f, 0.22f, 0.05f, 20f, -40f, -45f), f(-0.5f, 0.22f, 0.05f, 20f, -40f, -45f)),
			fk(13f, SNAP, f(0.32f, -0.2f, 0.1f, -20f, 0f, 40f), f(0.32f, -0.2f, 0.1f, -20f, 0f, 40f)),
			fk(20f, SMOOTH, f(0.28f, -0.18f, 0.1f, -18f, 0f, 36f), f(0.28f, -0.18f, 0.1f, -18f, 0f, 36f)) });
	private static final FClip F_SUIT_UP = new FClip(BlackPantherConfig.SUIT_UP_TICKS + 4f, 2f, 6f, new FKey[] {
			fk(6f, SMOOTH, f(-0.55f, 0.08f, 0.1f, 10f, -50f, -40f), null),
			fk(12f, LINEAR, f(-0.55f, 0.08f, 0.1f, 10f, -50f, -40f), null),
			fk(26f, SMOOTH, f(0.25f, -0.2f, 0.05f, -15f, 0f, 30f), f(0.25f, -0.2f, 0.05f, -15f, 0f, 30f)),
			fk(36f, SMOOTH, REST, REST) });
	private static final FClip F_SUIT_DOWN = new FClip(BlackPantherConfig.SUIT_DOWN_TICKS + 4f, 2f, 5f, new FKey[] {
			fk(8f, SMOOTH, f(0.25f, -0.2f, 0.05f, -15f, 0f, 30f), f(0.25f, -0.2f, 0.05f, -15f, 0f, 30f)),
			fk(22f, SMOOTH, f(-0.55f, 0.08f, 0.1f, 10f, -50f, -40f), REST),
			fk(30f, LINEAR, f(-0.55f, 0.08f, 0.1f, 10f, -50f, -40f), REST) });
	private static final FClip F_CLAW = new FClip(S * 3 + 4f, 1f, 4f, new FKey[] {
			fk(0.8f, SMOOTH, f(0.15f, 0.28f, 0.1f, 40f, -30f, -25f), REST),
			fk(2.6f, SNAP, f(-0.52f, -0.15f, -0.2f, -25f, 35f, 35f), REST),
			fk(S + 0.8f, SMOOTH, f(-0.1f, -0.05f, 0.05f, 0f, 0f, 0f), f(0.15f, 0.28f, 0.1f, 40f, -30f, -25f)),
			fk(S + 2.6f, SNAP, REST, f(-0.52f, -0.15f, -0.2f, -25f, 35f, 35f)),
			fk(S * 2 + 1.2f, SMOOTH, f(0f, 0.45f, 0.1f, 60f, 0f, 0f), f(0f, 0.45f, 0.1f, 60f, 0f, 0f)),
			fk(S * 2 + 3f, SNAP, f(-0.15f, -0.32f, -0.32f, -35f, 0f, 25f), f(-0.15f, -0.32f, -0.32f, -35f, 0f, 25f)),
			fk(S * 2 + 6f, SMOOTH, f(-0.12f, -0.25f, -0.25f, -28f, 0f, 20f), f(-0.12f, -0.25f, -0.25f, -28f, 0f, 20f)) });
	private static final FClip F_PULSE = new FClip(16f, 1f, 5f, new FKey[] {
			fk(3.5f, SMOOTH, f(-0.32f, 0f, 0.05f, 0f, -20f, -35f), f(-0.32f, 0f, 0.05f, 0f, -20f, -35f)),
			fk(5.5f, SNAP, f(0.32f, 0.1f, -0.22f, 0f, -30f, 45f), f(0.32f, 0.1f, -0.22f, 0f, -30f, 45f)),
			fk(10f, SMOOTH, f(0.28f, 0.08f, -0.18f, 0f, -25f, 40f), f(0.28f, 0.08f, -0.18f, 0f, -25f, 40f)) });
	private static final FClip F_OVERLOAD = new FClip(28f, 2f, 6f, new FKey[] {
			fk(6f, SMOOTH, f(-0.35f, -0.05f, 0.05f, 0f, -20f, -40f), f(-0.35f, -0.05f, 0.05f, 0f, -20f, -40f)),
			fk(12f, LINEAR, f(-0.38f, -0.08f, 0.05f, 0f, -20f, -42f), f(-0.38f, -0.08f, 0.05f, 0f, -20f, -42f)),
			fk(14f, SNAP, f(0.3f, 0.45f, -0.1f, 60f, 0f, 30f), f(0.3f, 0.45f, -0.1f, 60f, 0f, 30f)),
			fk(22f, SMOOTH, f(0.25f, 0.4f, -0.08f, 55f, 0f, 28f), f(0.25f, 0.4f, -0.08f, 55f, 0f, 28f)) });
	private static final FClip F_POUNCE = new FClip(16f, 1f, 3f, new FKey[] {
			fk(2.5f, SMOOTH, f(0f, -0.2f, 0.1f, -20f, 0f, 0f), f(0f, -0.2f, 0.1f, -20f, 0f, 0f)),
			fk(5f, SNAP, f(-0.2f, 0.32f, -0.38f, 45f, 0f, 0f), f(-0.2f, 0.32f, -0.38f, 45f, 0f, 0f)),
			fk(12f, SMOOTH, f(-0.18f, 0.28f, -0.32f, 40f, 0f, 0f), f(-0.18f, 0.28f, -0.32f, 40f, 0f, 0f)) });
	private static final FClip F_POUNCE_STRIKE = new FClip(12f, 0.5f, 4f, new FKey[] {
			fk(1.2f, SMOOTH, f(0f, 0.45f, 0.1f, 60f, 0f, 0f), f(0f, 0.45f, 0.1f, 60f, 0f, 0f)),
			fk(3f, SNAP, f(-0.15f, -0.32f, -0.32f, -35f, 0f, 25f), f(-0.15f, -0.32f, -0.32f, -35f, 0f, 25f)),
			fk(7f, SMOOTH, f(-0.12f, -0.25f, -0.25f, -28f, 0f, 20f), f(-0.12f, -0.25f, -0.25f, -28f, 0f, 20f)) });
	private static final FClip F_LEAP = new FClip(16f, 1f, 4f, new FKey[] {
			fk(1.5f, SMOOTH, f(0.05f, -0.25f, 0.1f, -25f, 0f, 0f), f(0.05f, -0.25f, 0.1f, -25f, 0f, 0f)),
			fk(4f, SNAP, f(-0.05f, 0.55f, 0.05f, 75f, 0f, 0f), f(-0.05f, 0.55f, 0.05f, 75f, 0f, 0f)),
			fk(12f, SMOOTH, f(-0.1f, 0.3f, 0.05f, 40f, 0f, 0f), f(-0.1f, 0.3f, 0.05f, 40f, 0f, 0f)) });
	private static final FClip F_DROP = new FClip(30f, 1f, 2f, new FKey[] {
			fk(2f, SNAP, f(-0.1f, 0.55f, 0f, 75f, 0f, 0f), f(-0.1f, 0.55f, 0f, 75f, 0f, 0f)),
			fk(30f, LINEAR, f(-0.1f, 0.55f, 0f, 75f, 0f, 0f), f(-0.1f, 0.55f, 0f, 75f, 0f, 0f)) });
	private static final FClip F_LANDING = new FClip(18f, 0.5f, 6f, new FKey[] {
			fk(1f, SNAP, f(-0.05f, -0.45f, -0.15f, -45f, 0f, 0f), f(0.25f, -0.35f, 0.15f, -20f, 0f, 35f)),
			fk(11f, LINEAR, f(-0.05f, -0.42f, -0.15f, -42f, 0f, 0f), f(0.25f, -0.33f, 0.15f, -20f, 0f, 35f)) });
	private static final FClip F_RETURN = new FClip(12f, 0.5f, 4f, new FKey[] {
			fk(1.5f, SNAP, f(-0.32f, 0.12f, -0.42f, 0f, 5f, 0f), null),
			fk(6f, SMOOTH, f(-0.3f, 0.1f, -0.38f, 0f, 5f, 0f), null) });

	private static final float[] F_ABSORB = f(-0.45f, 0.15f, -0.1f, 10f, -30f, -50f);
	private static final float[] F_PARRY_LEAD = f(-0.1f, -0.05f, 0f, 0f, 0f, 0f);
	private static final float[] F_PARRY_OFF = f(-0.45f, 0.36f, -0.05f, 30f, 0f, -50f);

	private static FClip fclipFor(int animId) {
		return switch (animId) {
			case BlackPantherState.ANIM_COMBO -> F_COMBO;
			case BlackPantherState.ANIM_SWEEP -> F_SWEEP;
			case BlackPantherState.ANIM_TAKEDOWN -> F_TAKEDOWN;
			case BlackPantherState.ANIM_DISARM -> F_DISARM;
			case BlackPantherState.ANIM_VAULT -> F_TUCK;
			case BlackPantherState.ANIM_BACKFLIP -> F_BACKFLIP;
			case BlackPantherState.ANIM_KNIFE, BlackPantherState.ANIM_THROW -> F_THROW;
			case BlackPantherState.ANIM_SCAN -> F_SCAN;
			case BlackPantherState.ANIM_RIPOSTE -> F_RIPOSTE;
			case BlackPantherState.ANIM_RESOLVE -> F_RESOLVE;
			case BlackPantherState.ANIM_SUIT_UP -> F_SUIT_UP;
			case BlackPantherState.ANIM_SUIT_DOWN -> F_SUIT_DOWN;
			case BlackPantherState.ANIM_CLAW -> F_CLAW;
			case BlackPantherState.ANIM_PULSE -> F_PULSE;
			case BlackPantherState.ANIM_OVERLOAD -> F_OVERLOAD;
			case BlackPantherState.ANIM_POUNCE -> F_POUNCE;
			case BlackPantherState.ANIM_POUNCE_STRIKE -> F_POUNCE_STRIKE;
			case BlackPantherState.ANIM_LEAP -> F_LEAP;
			case BlackPantherState.ANIM_DROP -> F_DROP;
			case BlackPantherState.ANIM_FLASH -> F_FLASH;
			case BlackPantherState.ANIM_RETURN -> F_RETURN;
			default -> null;
		};
	}

	/** The first-person clip playing now and its age, after the Drop Strike landing swap; null when none. */
	private static Object[] fcurrent(Player player, BlackPantherState s, float partial) {
		FClip c = fclipFor(s.animId);
		if (c == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (c == F_DROP && !player.onGround() && age >= c.duration) {
			age = c.duration - 1f;
		}
		if (c == F_DROP && (player.onGround() || player.isInWater()) && s.pulseStart >= s.animStart) {
			c = F_LANDING;
			age = player.level().getGameTime() - s.pulseStart + partial;
		}
		return age >= 0f && age < c.duration ? new Object[] { c, age } : null;
	}

	private static float fweight(FClip c, float time) {
		return weight(new Clip(c.duration, c.in, c.out, new Key[] { new Key(c.keys[c.keys.length - 1].t, SMOOTH, new float[CH]) }), time);
	}

	private static float[] fsample(FClip c, float time, boolean lead) {
		float[] out = new float[6];
		FKey a = null, b = null;
		for (FKey key : c.keys) {
			if (key.t <= time) {
				a = key;
			} else {
				b = key;
				break;
			}
		}
		float[] va = a == null ? REST : arm(a, lead);
		if (b == null) {
			return va.clone();
		}
		float[] vb = arm(b, lead);
		float ta = a == null ? 0f : a.t;
		float t = ease(b.ease, Mth.clamp((time - ta) / Math.max(0.01f, b.t - ta), 0f, 1f));
		for (int j = 0; j < 6; j++) {
			out[j] = Mth.lerp(t, va[j], vb[j]);
		}
		return out;
	}

	private static float[] arm(FKey k, boolean lead) {
		float[] v = lead ? k.lead : k.off;
		return v == null ? REST : v;
	}

	/** How much the first-person move owns the hands right now (0 = vanilla's hands). */
	public static float firstPersonWeight(AbstractClientPlayer player, float partial) {
		BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		if (s.absorbing) {
			return 1f;
		}
		Object[] cur = fcurrent(player, s, partial);
		return cur == null ? 0f : fweight((FClip) cur[0], (float) cur[1]);
	}

	/**
	 * Moves the first-person hand {@code arm} for the move playing now; true if anything was applied. Called inside vanilla's
	 * own push / pop for that hand, before the arm is drawn.
	 */
	public static boolean firstPerson(AbstractClientPlayer player, HumanoidArm arm, float partial, PoseStack pose) {
		BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null || !s.hasPower) {
			return false;
		}
		boolean lead = arm == player.getMainArm();
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float[] v = new float[6];
		boolean any = false;
		Object[] cur = fcurrent(player, s, partial);
		float w = 0f;
		if (cur != null) {
			FClip c = (FClip) cur[0];
			float age = (float) cur[1];
			w = fweight(c, age);
			if (w > 0.001f) {
				float[] d = fsample(c, age, lead);
				for (int j = 0; j < 6; j++) {
					v[j] = d[j] * w;
				}
				any = true;
			}
		}
		if (s.absorbing) {
			float[] held = s.suited ? F_ABSORB : (lead ? F_PARRY_LEAD : F_PARRY_OFF);
			float hw = s.animId == BlackPantherState.ANIM_RIPOSTE ? 1f - w : 1f;
			for (int j = 0; j < 6; j++) {
				v[j] = v[j] + (held[j] - v[j]) * hw;
			}
			any = true;
		}
		if (!any) {
			return false;
		}
		// mirrored for the left hand; the turns pivot about the hand
		float px = side * 0.45f;
		pose.translate(side * v[0], v[1], v[2]);
		pose.translate(px, -0.45f, -0.7f);
		pose.mulPose(Axis.ZP.rotationDegrees(side * v[5]));
		pose.mulPose(Axis.YP.rotationDegrees(side * v[4]));
		pose.mulPose(Axis.XP.rotationDegrees(v[3]));
		pose.translate(-px, 0.45f, 0.7f);
		return true;
	}
}
