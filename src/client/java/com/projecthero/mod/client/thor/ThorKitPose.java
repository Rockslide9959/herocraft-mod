package com.projecthero.mod.client.thor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.blackpanther.BlackPantherPose;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorKit;
import com.projecthero.mod.power.ThorUltimates;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7 Cosmic revamp: the body animations of Thor's new moves, keyed off the synced {@link ThorFx} (anim id + start tick,
 * and the channels for held poses) so every viewer sees them. The clip technique of {@code SuperSoldierPose}: keyframes with
 * an anticipation (wind-up), a snap through the strike, a follow-through and a recovery faded back into vanilla's pose; the
 * torso twist carries the shoulders with it, the lean bends from the hips, a crouch drops the body with vanilla's sneak offsets.
 *
 * <p>Clips are written for the lead arm as the right arm: the Mjolnir hand, else the Stormbreaker hand, else the main arm
 * (Ragnarok's hurl is led by the Stormbreaker hand). A left lead plays them mirrored. Values are radians; per arm X (negative
 * = raised forward / up), Y (positive = in across the body), Z (positive = out to the side). {@code GRIP} re-grips the held
 * weapon along the arm (the same -90 degree turn as {@link ThorPose}). An aimed clip adds the crosshair pitch / yaw to the
 * lead arm (both arms for Crossfire).
 */
public final class ThorKitPose {
	private static final float N = Float.NaN;
	private static final int RX = 0, RY = 1, RZ = 2, LX = 3, LY = 4, LZ = 5, TWIST = 6, LEAN = 7, FLEG = 8, BLEG = 9, CROUCH = 10,
			HEAD = 11, GRIP = 12, CH = 13;
	private static final int SMOOTH = 0, SNAP = 1, LINEAR = 2;
	private static final float HALF_PI = (float) (Math.PI / 2);
	/**
	 * v0.17.7 (user: Stormbreaker held like a normal greataxe, the hand a third of the way up the haft): how much of a move's
	 * re-grip Stormbreaker takes -- enough to stand the head up out of the fist, not so much that the haft lies along the arm
	 * and its lower third runs back through the body.
	 */
	public static final float AXE_GRIP = 0.6f;

	private ThorKitPose() {
	}

	private record Key(float t, int ease, float[] v) {
	}

	/** {@code aim}: 0 none, 1 the lead arm follows the crosshair, 2 both arms. {@code hold}: the last pose holds while airborne. */
	private record Clip(float duration, float in, float out, int aim, boolean hold, Key[] keys) {
	}

	private static Key k(float t, int ease, float rx, float ry, float rz, float lx, float ly, float lz, float twist, float lean,
			float fLeg, float bLeg, float crouch, float head, float grip) {
		return new Key(t, ease, new float[] { rx, ry, rz, lx, ly, lz, twist, lean, fLeg, bLeg, crouch, head, grip });
	}

	private static Clip clip(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, 0, false, keys);
	}

	private static Clip aimed(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, 1, false, keys);
	}

	private static Clip held(float duration, float in, float out, Key... keys) {
		return new Clip(duration, in, out, 0, true, keys);
	}

	// ================================================================== Unarmed

	/** Thunder Fist released: from the cocked charge pose, the fist driven straight through, hips and shoulder behind it. */
	private static final Clip THUNDER_FIST = clip(18f, 0f, 6f,
			k(0, SMOOTH, 0.9f, -0.2f, 0.3f, -1.2f, 0.55f, 0.1f, 0.6f, 0.1f, 0.35f, -0.25f, 0.35f, 0.1f, 0),
			k(2.5f, SNAP, -1.62f, 0.12f, 0f, 0.65f, 0f, 0.25f, -0.6f, 0.28f, -0.55f, 0.5f, 0.15f, 0.1f, 0),
			k(9, SMOOTH, -1.55f, 0.1f, 0f, 0.55f, 0f, 0.2f, -0.55f, 0.24f, -0.5f, 0.45f, 0.1f, 0.05f, 0));
	/** Thunder Fist charging (held): the fist cocked back by the hip, the guard hand out, low, trembling as it builds. */
	private static final float[] FIST_CHARGE = { 0.95f, -0.25f, 0.35f, -1.25f, 0.55f, 0.1f, 0.65f, 0.12f, 0.35f, -0.25f, 0.45f, 0.15f, 0 };
	/** Lightning Palm: palm drawn to the shoulder, then thrust out along the aim, the other hand pulled back. */
	private static final Clip LIGHTNING_PALM = aimed(15f, 0f, 5f,
			k(0, SMOOTH, 0.7f - HALF_PI, -0.45f, 0.35f, 0.1f, 0f, 0.15f, 0.4f, 0.05f, 0.2f, -0.15f, 0.15f, 0f, 0),
			k(3, SNAP, 0f, 0f, 0f, 0.45f, 0f, 0.3f, -0.45f, 0.15f, -0.45f, 0.38f, 0.05f, 0f, 0),
			k(10, SMOOTH, 0.05f, 0f, 0f, 0.4f, 0f, 0.25f, -0.4f, 0.12f, -0.4f, 0.35f, 0f, 0f, 0));
	/** Asgardian Grapple: reach and seize, haul it up overhead one-handed, hold it there crackling, then hurl it forward. */
	private static final Clip GRAPPLE = clip(24f, 1f, 6f,
			k(0, SNAP, -1.5f, 0.1f, 0f, -0.3f, 0f, 0.3f, -0.3f, 0.25f, -0.3f, 0.25f, 0.2f, 0.1f, 0),
			k(4.5f, SMOOTH, -2.95f, 0f, 0.15f, -0.5f, 0f, 0.6f, 0f, -0.15f, 0f, 0.1f, 0f, -0.45f, 0),
			k(9, SMOOTH, -3.05f, 0f, 0.1f, -0.55f, 0f, 0.65f, 0.15f, -0.2f, 0.05f, 0.1f, 0f, -0.45f, 0),
			k(11, SNAP, -1.0f, 0.15f, 0f, 0.4f, 0f, 0.3f, -0.6f, 0.38f, -0.6f, 0.5f, 0.15f, 0.1f, 0),
			k(16, SMOOTH, -0.85f, 0.1f, 0f, 0.35f, 0f, 0.25f, -0.5f, 0.3f, -0.5f, 0.45f, 0.1f, 0.05f, 0));
	/** Storm Rush: from a boxer's guard, a lead jab, a cross, a hook, a dip, then the uppercut that launches. */
	private static final Clip STORM_RUSH = clip(19f, 1f, 4f,
			k(1, SMOOTH, -1.2f, 0.45f, 0.1f, -1.3f, 0.5f, 0.1f, 0.1f, 0.18f, -0.2f, 0.2f, 0.2f, 0.1f, 0),
			k(2.2f, SNAP, -1.62f, 0.1f, 0f, -1.15f, 0.55f, 0.1f, -0.4f, 0.22f, -0.35f, 0.3f, 0.2f, 0.1f, 0),
			k(3.8f, SMOOTH, -1.2f, 0.45f, 0.1f, -1.3f, 0.5f, 0.1f, 0.1f, 0.18f, -0.2f, 0.2f, 0.2f, 0.1f, 0),
			k(5.2f, SNAP, -1.15f, 0.55f, 0.1f, -1.62f, 0.1f, 0f, 0.45f, 0.22f, -0.35f, 0.3f, 0.2f, 0.1f, 0),
			k(6.6f, SMOOTH, -1.2f, 0.45f, 0.1f, -1.3f, 0.5f, 0.1f, 0f, 0.18f, -0.2f, 0.2f, 0.2f, 0.1f, 0),
			k(8.2f, SNAP, -1.45f, 0.95f, 0.65f, -1.2f, 0.5f, 0.1f, -0.65f, 0.2f, -0.35f, 0.3f, 0.25f, 0.1f, 0),
			k(10.2f, SMOOTH, 0.7f, 0.1f, 0.2f, -1.25f, 0.5f, 0.1f, 0.5f, 0.3f, -0.1f, 0.2f, 0.5f, 0.2f, 0),
			k(12.2f, SNAP, -2.45f, 0.2f, 0f, -0.8f, 0.3f, 0.2f, -0.5f, -0.12f, -0.5f, 0.4f, 0f, -0.35f, 0),
			k(15, SMOOTH, -2.35f, 0.15f, 0f, -0.7f, 0.25f, 0.2f, -0.4f, -0.1f, -0.45f, 0.35f, 0f, -0.3f, 0));
	/** Static Burst: curled in, forearms crossed, then flung wide open as the charge bursts out of him, head thrown back. */
	private static final Clip STATIC_BURST = clip(19f, 0f, 6f,
			k(0, SMOOTH, -1.3f, 1.0f, 0f, -1.3f, 1.0f, 0f, 0f, 0.3f, 0f, 0f, 0.6f, 0.3f, 0),
			k(4, SMOOTH, -1.35f, 1.05f, 0f, -1.35f, 1.05f, 0f, 0f, 0.35f, 0f, 0f, 0.7f, 0.35f, 0),
			k(5.5f, SNAP, -0.45f, 0f, 1.35f, -0.45f, 0f, 1.35f, 0f, -0.28f, -0.25f, 0.25f, 0f, -0.45f, 0),
			k(12, SMOOTH, -0.4f, 0f, 1.25f, -0.4f, 0f, 1.25f, 0f, -0.22f, -0.2f, 0.2f, 0f, -0.35f, 0));
	/** Ground Pound: both fists raised high, then driven down into the earth, the body following them to a crouch. */
	private static final Clip GROUND_POUND = clip(21f, 1f, 6f,
			k(0, SMOOTH, -2.9f, 0.1f, 0.25f, -2.9f, 0.1f, 0.25f, 0f, -0.22f, 0f, 0.1f, 0f, -0.35f, 0),
			k(5, SMOOTH, -3.0f, 0.1f, 0.2f, -3.0f, 0.1f, 0.2f, 0f, -0.25f, 0f, 0.1f, 0f, -0.4f, 0),
			k(7, SNAP, -0.45f, 0.25f, 0f, -0.45f, 0.25f, 0f, 0f, 0.45f, -0.6f, 0.6f, 0.75f, 0.4f, 0),
			k(14, SMOOTH, -0.5f, 0.25f, 0f, -0.5f, 0.25f, 0f, 0f, 0.42f, -0.55f, 0.55f, 0.7f, 0.35f, 0));
	/** A leap's take-off (Thunder Leap, Meteor Dive's launch): crouch, spring with the arms, then fists / weapon raised to slam. */
	private static final Clip LEAP = held(60f, 0f, 4f,
			k(0, SMOOTH, 0.7f, 0f, 0.15f, 0.7f, 0f, 0.15f, 0f, 0.4f, 0f, 0f, 0.7f, 0.2f, 1),
			k(3, SNAP, -2.6f, 0f, 0.3f, -2.6f, 0f, 0.3f, 0f, -0.1f, -0.5f, 0.5f, 0f, -0.2f, 1),
			k(10, SMOOTH, -2.95f, 0.05f, 0.15f, -2.85f, 0.05f, 0.2f, 0f, 0.1f, -0.85f, -0.25f, 0f, 0.45f, 1));
	/** The landing slam: everything driven into the ground, one knee down, held, then up. */
	private static final Clip LEAP_SLAM = clip(20f, 0f, 8f,
			k(0, SNAP, -0.35f, 0.2f, 0f, -0.35f, 0.2f, 0f, 0f, 0.48f, -0.9f, 0.7f, 0.85f, 0.3f, 1),
			k(9, SMOOTH, -0.4f, 0.2f, 0f, -0.4f, 0.2f, 0f, 0f, 0.45f, -0.85f, 0.65f, 0.8f, 0.25f, 1));
	/** Lightning Incarnate's closing explosion: curled tight, then spread-eagled as he bursts. */
	private static final Clip INCARNATE_BURST = clip(22f, 0f, 8f,
			k(0, SMOOTH, -1.3f, 1.0f, 0f, -1.3f, 1.0f, 0f, 0f, 0.35f, 0f, 0f, 0.8f, 0.35f, 0),
			k(3, SNAP, -2.4f, 0f, 1.0f, -2.4f, 0f, 1.0f, 0f, -0.4f, -0.3f, 0.3f, 0f, -0.55f, 0),
			k(13, SMOOTH, -2.3f, 0f, 0.95f, -2.3f, 0f, 0.95f, 0f, -0.35f, -0.25f, 0.25f, 0f, -0.5f, 0));
	/** God of Thunder: gathered low, then the roar -- chest out, arms flung down and back, head back, eyes alight. */
	private static final Clip GOD_OF_THUNDER = clip(24f, 1f, 6f,
			k(0, SMOOTH, -0.6f, 0.6f, 0f, -0.6f, 0.6f, 0f, 0f, 0.3f, 0f, 0f, 0.4f, 0.3f, 1),
			k(4, SNAP, 0.35f, 0f, 0.7f, 0.35f, 0f, 0.7f, 0f, -0.35f, -0.15f, 0.15f, 0f, -0.55f, 1),
			k(16, SMOOTH, 0.3f, 0f, 0.65f, 0.3f, 0f, 0.65f, 0f, -0.3f, -0.15f, 0.15f, 0f, -0.5f, 1));

	// ================================================================== Mjolnir

	/** Whirlwind Throw: the hammer spun round overhead by its strap, faster and faster, then hurled out along the aim. */
	private static final Clip WHIRLWIND = new Clip(24f, 1f, 7f, 0, false, new Key[] {
			k(0, SMOOTH, -2.2f, 0f, 1.1f, -0.4f, 0f, 0.45f, 0f, 0f, 0f, 0f, 0.1f, -0.2f, 0.4f),
			k(2.5f, LINEAR, -2.5f, 0.8f, 0.5f, -0.45f, 0f, 0.5f, 0.15f, -0.05f, 0f, 0f, 0.1f, -0.25f, 0.4f),
			k(4.5f, LINEAR, -2.5f, -0.8f, 0.9f, -0.45f, 0f, 0.5f, -0.1f, -0.05f, 0f, 0f, 0.1f, -0.25f, 0.4f),
			k(6.2f, LINEAR, -2.5f, 0.8f, 0.5f, -0.45f, 0f, 0.5f, 0.2f, -0.05f, 0f, 0f, 0.15f, -0.25f, 0.4f),
			k(7.8f, LINEAR, -2.5f, -0.8f, 0.9f, -0.45f, 0f, 0.5f, -0.1f, -0.05f, 0f, 0f, 0.15f, -0.25f, 0.4f),
			k(9.2f, SMOOTH, -2.4f, -0.4f, 1.0f, -1.2f, 0.3f, 0.2f, 0.55f, -0.1f, 0.3f, -0.2f, 0.2f, -0.1f, 0.4f),
			k(10.5f, SNAP, -1.45f, 0.1f, 0f, 0.45f, 0f, 0.3f, -0.65f, 0.32f, -0.55f, 0.45f, 0.1f, 0.05f, 0f),
			k(16, SMOOTH, -1.2f, 0.1f, 0f, 0.4f, 0f, 0.25f, -0.5f, 0.25f, -0.5f, 0.4f, 0.05f, 0.05f, 0f) });
	/** Storm Dash (Mjolnir / Twin): flung forward behind the weapon, the other arm streaming back. Aimed. */
	private static final Clip STORM_DASH = aimed(11f, 0f, 4f,
			k(0, SNAP, 0f, 0f, 0f, 0.9f, 0f, 0.3f, -0.3f, 0.6f, -0.8f, 0.8f, 0f, -0.2f, 1),
			k(7, SMOOTH, 0f, 0f, 0f, 0.85f, 0f, 0.3f, -0.25f, 0.55f, -0.7f, 0.7f, 0f, -0.2f, 1));
	/** Meteor Dive: the launch, the weapon raised at the top, then pitched over into the dive, weapon leading. Held in the air. */
	private static final Clip METEOR_DIVE = held(70f, 0f, 4f,
			k(0, SMOOTH, 0.6f, 0f, 0.2f, 0.6f, 0f, 0.2f, 0f, 0.35f, 0f, 0f, 0.6f, 0.2f, 1),
			k(3, SNAP, -2.9f, 0f, 0.2f, -2.7f, 0.1f, 0.3f, 0f, -0.15f, -0.3f, 0.3f, 0f, -0.3f, 1),
			k(9, SMOOTH, -3.05f, 0f, 0.15f, -2.9f, 0.1f, 0.2f, 0f, -0.25f, -0.2f, 0.4f, 0f, -0.35f, 1),
			k(13, SNAP, -2.7f, 0.15f, 0f, -2.6f, 0.15f, 0f, 0f, 1.05f, 0.35f, 0.45f, 0f, -0.8f, 1));

	// ================================================================== Stormbreaker

	/** Storm Hurl: the axe drawn back high, the other hand sighting, then hurled overarm along the aim. */
	private static final Clip BIFROST_HURL = new Clip(19f, 1f, 6f, 1, false, new Key[] {
			k(0, SMOOTH, -1.05f, -0.2f, 0.45f, -1.2f, 0.25f, 0f, 0.65f, -0.15f, 0.3f, -0.25f, 0.15f, -0.05f, 1),
			k(5, SMOOTH, -1.25f, -0.25f, 0.5f, -1.3f, 0.25f, 0f, 0.75f, -0.2f, 0.35f, -0.3f, 0.2f, -0.05f, 1),
			k(6.5f, SNAP, 0.15f, 0.1f, 0f, 0.45f, 0f, 0.3f, -0.75f, 0.38f, -0.6f, 0.5f, 0.1f, 0.05f, 0),
			k(12, SMOOTH, 0.5f, 0.1f, 0f, 0.4f, 0f, 0.25f, -0.6f, 0.3f, -0.55f, 0.45f, 0.05f, 0.05f, 0) });
	/** Storm Cleave: two hands raise the axe high (lightning finds the blade), then the chop drives down onto the target. */
	private static final Clip STORM_CLEAVE = clip(19f, 1f, 6f,
			k(0, SMOOTH, -3.0f, 0.15f, 0.1f, -3.0f, 0.35f, 0f, 0f, -0.25f, 0f, 0.1f, 0f, -0.3f, 1),
			k(5.5f, SMOOTH, -3.1f, 0.15f, 0.08f, -3.1f, 0.35f, 0f, 0f, -0.3f, 0f, 0.12f, 0f, -0.35f, 1),
			k(7.2f, SNAP, -0.7f, 0.2f, 0f, -0.7f, 0.45f, 0f, 0f, 0.55f, -0.6f, 0.5f, 0.4f, 0.2f, 1),
			k(12, SMOOTH, -0.75f, 0.2f, 0f, -0.75f, 0.45f, 0f, 0f, 0.5f, -0.55f, 0.45f, 0.35f, 0.15f, 1));
	/** Thunder Step's arrival: low, axe trailing, then one flat cleave across the front. */
	private static final Clip BIFROST_STEP = clip(15f, 0f, 6f,
			k(0, SMOOTH, 0.5f, -0.6f, 0.5f, -0.6f, 0f, 0.6f, 0.75f, 0.25f, -0.3f, 0.3f, 0.5f, 0.1f, 1),
			k(2.5f, SNAP, -1.5f, 1.25f, 0.2f, -0.4f, 0f, 0.8f, -0.85f, 0.2f, -0.45f, 0.4f, 0.3f, 0.05f, 1),
			k(8, SMOOTH, -1.4f, 1.35f, 0.2f, -0.4f, 0f, 0.75f, -0.8f, 0.18f, -0.4f, 0.35f, 0.25f, 0.05f, 1));
	/** Rending Arc: wound far round to the side, then one huge flat sweep through 180 degrees. */
	private static final Clip RENDING_ARC = clip(19f, 1f, 7f,
			k(0, SMOOTH, -1.4f, -1.1f, 0.35f, -0.6f, 0f, 0.55f, 0.95f, 0.15f, 0.25f, -0.2f, 0.35f, 0f, 1),
			k(4, SMOOTH, -1.45f, -1.25f, 0.4f, -0.6f, 0f, 0.6f, 1.05f, 0.2f, 0.3f, -0.25f, 0.4f, 0f, 1),
			k(5.8f, SNAP, -1.45f, 1.45f, 0.2f, -0.5f, 0f, 0.8f, -1.05f, 0.22f, -0.5f, 0.45f, 0.3f, 0.05f, 1),
			k(10, SMOOTH, -1.35f, 1.6f, 0.25f, -0.5f, 0f, 0.75f, -1.1f, 0.2f, -0.45f, 0.4f, 0.25f, 0.05f, 1));
	/** Executioner's Drop: the spring, the axe raised in both hands behind the head, the back arched. Held in the air. */
	private static final Clip EXECUTIONER = held(60f, 0f, 4f,
			k(0, SMOOTH, 0.6f, 0f, 0.2f, 0.6f, 0.2f, 0.2f, 0f, 0.4f, 0f, 0f, 0.6f, 0.2f, 1),
			k(3, SNAP, -3.0f, 0.1f, 0.05f, -3.0f, 0.35f, 0f, 0f, -0.35f, -0.6f, 0.3f, 0f, -0.2f, 1),
			k(10, SMOOTH, -3.15f, 0.1f, 0.05f, -3.15f, 0.35f, 0f, 0f, -0.4f, -0.7f, 0.2f, 0f, -0.1f, 1));
	/** Executioner's Drop landing: the chop into the ground with the whole body, crouched over it. */
	private static final Clip EXECUTIONER_SLAM = clip(21f, 0f, 8f,
			k(0, SNAP, -0.5f, 0.2f, 0f, -0.5f, 0.45f, 0f, 0f, 0.5f, -0.9f, 0.8f, 0.85f, 0.35f, 1),
			k(10, SMOOTH, -0.55f, 0.2f, 0f, -0.55f, 0.45f, 0f, 0f, 0.48f, -0.85f, 0.75f, 0.8f, 0.3f, 1));
	/** Lightning Dash: a lean into the lightning, the axe trailing low. */
	private static final Clip BIFROST_DASH = clip(9f, 0f, 4f,
			k(0, SNAP, 0.55f, 0f, 0.3f, 0.45f, 0f, 0.3f, 0f, 0.5f, -0.5f, 0.5f, 0f, -0.2f, 1),
			k(5, SMOOTH, 0.5f, 0f, 0.3f, 0.4f, 0f, 0.3f, 0f, 0.45f, -0.45f, 0.45f, 0f, -0.2f, 1));

	// ================================================================== Twin Gods

	/** Twin Throw: both weapons drawn back overhead, then flung forward together, crossing. */
	private static final Clip TWIN_THROW = clip(22f, 1f, 6f,
			k(0, SMOOTH, -2.65f, 0f, 0.5f, -2.65f, 0f, 0.5f, 0f, -0.3f, 0.2f, -0.2f, 0.1f, -0.2f, 1),
			k(5, SMOOTH, -2.8f, 0f, 0.55f, -2.8f, 0f, 0.55f, 0f, -0.35f, 0.25f, -0.25f, 0.15f, -0.2f, 1),
			k(6.5f, SNAP, -1.35f, 0.5f, 0f, -1.35f, 0.5f, 0f, 0f, 0.35f, -0.55f, 0.5f, 0.1f, 0.05f, 0),
			k(12, SMOOTH, -1.25f, 0.45f, 0f, -1.25f, 0.45f, 0f, 0f, 0.3f, -0.5f, 0.45f, 0.05f, 0.05f, 0));
	/** Thunderstruck Cleave: the weapons crossed in front while lightning runs hammer to axe, then both slash out in an X. */
	private static final Clip THUNDERSTRUCK = clip(25f, 1f, 7f,
			k(0, SMOOTH, -1.6f, 0.7f, 0f, -1.6f, 0.7f, 0f, 0f, 0.1f, -0.2f, 0.2f, 0.25f, 0.05f, 1),
			k(8, SMOOTH, -1.95f, 0.75f, 0f, -1.95f, 0.75f, 0f, 0f, 0.05f, -0.25f, 0.25f, 0.3f, 0f, 1),
			k(10.5f, SNAP, -1.25f, -1.05f, 0.65f, -1.25f, -1.05f, 0.65f, 0f, 0.32f, -0.55f, 0.5f, 0.15f, 0.05f, 1),
			k(16, SMOOTH, -1.15f, -1.1f, 0.6f, -1.15f, -1.1f, 0.6f, 0f, 0.28f, -0.5f, 0.45f, 0.1f, 0.05f, 1));
	/** Crossfire: both weapons levelled at the target, legs braced, as the two beams converge. Both arms aimed. */
	private static final Clip CROSSFIRE = new Clip(16f, 1f, 5f, 2, false, new Key[] {
			k(0, SNAP, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f, -0.35f, 0.3f, 0.15f, 0f, 1),
			k(10, SMOOTH, 0.02f, 0f, 0f, 0.02f, 0f, 0f, 0f, 0.12f, -0.35f, 0.3f, 0.15f, 0f, 1) });
	/** Storm Anchor: both weapons raised, then hurled down at the ground together. */
	private static final Clip STORM_ANCHOR = clip(19f, 1f, 6f,
			k(0, SMOOTH, -2.9f, 0f, 0.3f, -2.9f, 0f, 0.3f, 0f, -0.25f, 0f, 0.1f, 0f, -0.25f, 1),
			k(3.2f, SNAP, -0.9f, 0.2f, 0f, -0.9f, 0.2f, 0f, 0f, 0.5f, -0.5f, 0.45f, 0.2f, 0.3f, 0),
			k(10, SMOOTH, -0.85f, 0.2f, 0f, -0.85f, 0.2f, 0f, 0f, 0.45f, -0.45f, 0.4f, 0.15f, 0.25f, 0));

	// ================================================================== ultimates

	private static final int WF = ThorUltimates.WRATH_FINAL;
	/** Wrath of the Storm: risen, the hammer to the sky and the other arm out while the bolts rain, then brought down. */
	private static final Clip WRATH_STORM = clip(ThorUltimates.WRATH_END, 2f, 8f,
			k(0, SMOOTH, -1.2f, 0f, 0.2f, -0.6f, 0f, 0.6f, 0f, 0f, 0f, 0.2f, 0.3f, 0f, 1),
			k(6, SNAP, -3.1f, 0.1f, 0.12f, -0.45f, 0f, 1.0f, 0f, -0.15f, 0.15f, 0.35f, 0f, -0.6f, 1),
			k(WF - 6, SMOOTH, -3.12f, 0.1f, 0.1f, -0.5f, 0f, 1.05f, 0f, -0.2f, 0.15f, 0.35f, 0f, -0.65f, 1),
			k(WF - 2, SMOOTH, -3.2f, 0.1f, 0.1f, -0.4f, 0f, 1.1f, 0.2f, -0.3f, 0.2f, 0.3f, 0f, -0.6f, 1),
			k(WF, SNAP, -0.95f, 0.15f, 0f, -1.4f, 0.45f, 0f, -0.3f, 0.45f, -0.4f, 0.4f, 0f, 0.35f, 1),
			k(WF + 10, SMOOTH, -0.9f, 0.15f, 0f, -1.35f, 0.45f, 0f, -0.25f, 0.4f, -0.35f, 0.35f, 0f, 0.3f, 1));
	private static final int JT = ThorUltimates.JUDGE_THROW, JB = ThorUltimates.JUDGE_BURST;
	/** Stormbreaker's Judgement: the axe drawn far back, hurled, the empty hand held out at the impaled foe, then a fist as it lands. */
	private static final Clip JUDGEMENT = clip(ThorUltimates.JUDGE_END, 1f, 8f,
			k(0, SMOOTH, -2.8f, -0.1f, 0.35f, -1.4f, 0.2f, 0f, 0.65f, -0.25f, 0.35f, -0.3f, 0.2f, -0.1f, 1),
			k(JT - 1, SMOOTH, -2.95f, -0.15f, 0.4f, -1.45f, 0.2f, 0f, 0.75f, -0.3f, 0.4f, -0.35f, 0.25f, -0.1f, 1),
			k(JT + 0.5f, SNAP, -1.25f, 0.1f, 0f, 0.4f, 0f, 0.3f, -0.75f, 0.4f, -0.6f, 0.5f, 0.1f, 0.05f, 0),
			k(JT + 6, SMOOTH, -1.6f, 0f, 0f, 0.3f, 0f, 0.3f, -0.35f, 0.15f, -0.45f, 0.4f, 0.05f, 0f, 0),
			k(JB - 2, SMOOTH, -1.65f, 0f, 0f, 0.25f, 0f, 0.3f, -0.3f, 0.12f, -0.45f, 0.4f, 0.05f, 0f, 0),
			k(JB, SNAP, -0.55f, 0.3f, 0f, 0.35f, 0f, 0.3f, -0.15f, 0.25f, -0.4f, 0.35f, 0.15f, 0.2f, 0),
			k(JB + 8, SMOOTH, -0.6f, 0.3f, 0f, 0.3f, 0f, 0.3f, -0.15f, 0.22f, -0.4f, 0.35f, 0.15f, 0.15f, 0));
	/** Ragnarok: rising with the arms out, both weapons raised to the sky through the rain, the axe hurled down, then the pose. */
	private static final Clip RAGNAROK = clip(ThorUltimates.RAG_END, 2f, 10f,
			k(0, SMOOTH, -1.0f, 0f, 1.0f, -1.0f, 0f, 1.0f, 0f, 0f, 0.1f, 0.3f, 0.2f, -0.2f, 1),
			k(ThorUltimates.RAG_RISE_END, SMOOTH, -1.2f, 0f, 1.3f, -1.2f, 0f, 1.3f, 0f, -0.15f, 0.15f, 0.35f, 0f, -0.4f, 1),
			k(ThorUltimates.RAG_RAIN_START + 2, SNAP, -3.05f, 0.05f, 0.25f, -3.05f, 0.05f, 0.25f, 0f, -0.2f, 0.15f, 0.35f, 0f, -0.6f, 1),
			k(ThorUltimates.RAG_HURL - 3, SMOOTH, -3.15f, 0.05f, 0.2f, -3.1f, 0.05f, 0.25f, 0.4f, -0.3f, 0.2f, 0.3f, 0f, -0.5f, 1),
			k(ThorUltimates.RAG_HURL + 0.5f, SNAP, -0.9f, 0.2f, 0f, -3.0f, 0.05f, 0.3f, -0.6f, 0.5f, -0.3f, 0.4f, 0f, 0.4f, 0),
			k(ThorUltimates.RAG_IMPACT, SMOOTH, -0.6f, 0f, 0.8f, -0.6f, 0f, 0.8f, 0f, 0.1f, 0.1f, 0.3f, 0f, 0.35f, 0),
			k(ThorUltimates.RAG_IMPACT + 16, SMOOTH, -0.55f, 0f, 0.75f, -0.55f, 0f, 0.75f, 0f, 0.05f, 0.1f, 0.3f, 0f, 0.25f, 0));

	private static Clip clipFor(int anim) {
		return switch (anim) {
			case ThorFx.ANIM_THUNDER_FIST -> THUNDER_FIST;
			case ThorFx.ANIM_LIGHTNING_PALM -> LIGHTNING_PALM;
			case ThorFx.ANIM_GRAPPLE -> GRAPPLE;
			case ThorFx.ANIM_STORM_RUSH -> STORM_RUSH;
			case ThorFx.ANIM_STATIC_BURST -> STATIC_BURST;
			case ThorFx.ANIM_GROUND_POUND -> GROUND_POUND;
			case ThorFx.ANIM_LEAP -> LEAP;
			case ThorFx.ANIM_LEAP_SLAM -> LEAP_SLAM;
			case ThorFx.ANIM_WHIRLWIND -> WHIRLWIND;
			case ThorFx.ANIM_STORM_DASH -> STORM_DASH;
			case ThorFx.ANIM_METEOR_DIVE -> METEOR_DIVE;
			case ThorFx.ANIM_BIFROST_HURL -> BIFROST_HURL;
			case ThorFx.ANIM_STORM_CLEAVE -> STORM_CLEAVE;
			case ThorFx.ANIM_BIFROST_STEP -> BIFROST_STEP;
			case ThorFx.ANIM_RENDING_ARC -> RENDING_ARC;
			case ThorFx.ANIM_EXECUTIONER -> EXECUTIONER;
			case ThorFx.ANIM_EXECUTIONER_SLAM -> EXECUTIONER_SLAM;
			case ThorFx.ANIM_BIFROST_DASH -> BIFROST_DASH;
			case ThorFx.ANIM_TWIN_THROW -> TWIN_THROW;
			case ThorFx.ANIM_THUNDERSTRUCK -> THUNDERSTRUCK;
			case ThorFx.ANIM_CROSSFIRE -> CROSSFIRE;
			case ThorFx.ANIM_INCARNATE_BURST -> INCARNATE_BURST;
			case ThorFx.ANIM_WRATH_STORM -> WRATH_STORM;
			case ThorFx.ANIM_JUDGEMENT -> JUDGEMENT;
			case ThorFx.ANIM_RAGNAROK -> RAGNAROK;
			case ThorFx.ANIM_GOD_OF_THUNDER -> GOD_OF_THUNDER;
			case ThorFx.ANIM_STORM_ANCHOR -> STORM_ANCHOR;
			default -> null;
		};
	}

	/** True for the anim ids this class owns. */
	public static boolean owns(int anim) {
		return clipFor(anim) != null;
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

	/** Clip age, held at the last key while airborne for a held clip (capped so a stuck one still ends). */
	private static float age(Player p, Clip c, ThorFx fx, float partial) {
		float age = p.level().getGameTime() - fx.animStart() + partial;
		if (c.hold && !p.onGround() && !p.isInWater() && age < c.duration - c.out - 1) {
			age = Math.min(age, c.keys[c.keys.length - 1].t);
		}
		return age;
	}

	/** Which arm leads: the Mjolnir hand, else the Stormbreaker hand, else the main arm (Ragnarok: the axe hand). */
	private static boolean leadRight(Player p, int anim) {
		HumanoidArm main = p.getMainArm();
		HumanoidArm arm = main;
		boolean axeLeads = anim == ThorFx.ANIM_RAGNAROK;
		var first = axeLeads ? ModItems.STORMBREAKER : ModItems.MJOLNIR;
		var second = axeLeads ? ModItems.MJOLNIR : ModItems.STORMBREAKER;
		// v0.17.7: a weapon out on a scripted throw still leads from the hand it left
		ItemStack mainItem = com.projecthero.mod.power.ThorThrown.inHand(p, net.minecraft.world.InteractionHand.MAIN_HAND);
		ItemStack offItem = com.projecthero.mod.power.ThorThrown.inHand(p, net.minecraft.world.InteractionHand.OFF_HAND);
		if (mainItem.is(first)) {
			arm = main;
		} else if (offItem.is(first)) {
			arm = main.getOpposite();
		} else if (!mainItem.is(second) && offItem.is(second)) {
			arm = main.getOpposite();
		}
		return arm == HumanoidArm.RIGHT;
	}

	// ================================================================== third person

	/**
	 * Poses {@code m} for {@code p}'s current kit move, if one is playing. Returns {grip, weight} for {@link ThorPose}'s
	 * held-item grip, or null when no kit move owns the body.
	 */
	public static float[] apply(Player p, HumanoidModel<?> m, ThorFx fx) {
		if (BlackPantherPose.inFirstPersonHand) {
			return null;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		// held: the Thunder Fist charge
		if (fx.has(ThorFx.CH_FIST)) {
			float held = p.level().getGameTime() - fx.chargeStart() + partial;
			float w = ease(SMOOTH, Mth.clamp(held / 4f, 0f, 1f));
			float build = Mth.clamp(held / ThorKit.FIST_CHARGE_TICKS, 0f, 1f);
			float tremble = Mth.sin((p.tickCount + partial) * 2.9f) * (0.015f + 0.05f * build);
			float[] v = FIST_CHARGE.clone();
			v[RX] += tremble;
			v[TWIST] += build * 0.15f;
			v[CROUCH] += build * 0.15f;
			boolean right = p.getMainArm() == HumanoidArm.RIGHT;
			write(m, base(m, right), v, w, right, 0, p);
			return new float[] { 0f, w };
		}
		Clip c = clipFor(fx.anim());
		if (c == null) {
			return null;
		}
		float age = age(p, c, fx, partial);
		float w = weight(c, age);
		if (w <= 0.001f) {
			return null;
		}
		boolean right = leadRight(p, fx.anim());
		float[] base = base(m, right);
		float[] v = sample(c, age, base);
		write(m, base, v, w, right, c.aim, p);
		return new float[] { Mth.clamp(v[GRIP], 0f, 1f) * w, w };
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
		b[HEAD] = 0f; // the head channel is an offset on vanilla's look
		b[GRIP] = 0f;
		return b;
	}

	private static void write(HumanoidModel<?> m, float[] base, float[] p, float w, boolean right, int aim, Player player) {
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

		// crouch: vanilla's sneak offsets, scaled
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
		// the lean bends from the hips (HipBend keeps the bottom of the torso on the legs)
		float lean = v[LEAN] + Mth.clamp(v[CROUCH], 0f, 1f) * 0.25f;
		com.projecthero.mod.client.HipBend.pitch(m, 1f, lean);
		// the twist carries the shoulders round with it
		float twist = v[TWIST] * mir;
		m.body.yRot = twist;
		m.rightArm.z = Mth.sin(twist) * 5.0f + Mth.sin(lean) * 2.0f;
		m.rightArm.x = -Mth.cos(twist) * 5.0f;
		m.leftArm.z = -Mth.sin(twist) * 5.0f + Mth.sin(lean) * 2.0f;
		m.leftArm.x = Mth.cos(twist) * 5.0f;

		float aimX = aim > 0 ? -HALF_PI + headX : 0f;
		float aimY = aim > 0 ? headY : 0f;
		// arm on side s (+1 right, -1 left): yRot = -s * inward, zRot = s * outward
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

	/** {tick, x, y, z, xDeg, yDeg, zDeg} for the lead hand; the other hand plays its own table or none. */
	private static final float[][] FP_PUNCH = { { 0, 0.05f, -0.1f, 0.15f, 20, 0, 0 }, { 2.5f, -0.05f, 0.08f, -0.45f, -10, 0, 0 },
			{ 9, -0.04f, 0.06f, -0.4f, -8, 0, 0 }, { 15, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_RUSH = { { 0, 0, 0, 0, 0, 0, 0 }, { 2.2f, -0.05f, 0.05f, -0.45f, -10, 0, 0 }, { 3.8f, 0, 0, 0, 0, 0, 0 },
			{ 8.2f, -0.25f, 0.05f, -0.35f, -5, 40, 0 }, { 10.2f, 0.05f, -0.2f, 0.1f, 30, 0, 0 }, { 12.2f, -0.05f, 0.35f, -0.35f, -60, 0, 0 },
			{ 17, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_RUSH_OFF = { { 0, 0, 0, 0, 0, 0, 0 }, { 5.2f, -0.05f, 0.05f, -0.45f, -10, 0, 0 }, { 6.6f, 0, 0, 0, 0, 0, 0 },
			{ 17, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_PALM = { { 0, 0.1f, -0.05f, 0.1f, 10, 0, 20 }, { 3, -0.1f, 0.1f, -0.4f, -15, 0, -10 },
			{ 10, -0.08f, 0.08f, -0.35f, -12, 0, -10 }, { 15, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_OVERHEAD = { { 0, 0, 0.5f, 0.1f, 40, 0, -10 }, { 5, 0, 0.6f, 0.1f, 45, 0, -10 },
			{ 7, -0.1f, -0.35f, -0.3f, -80, 0, 5 }, { 13, -0.1f, -0.3f, -0.28f, -75, 0, 5 }, { 19, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_SWEEP = { { 0, 0.35f, 0.05f, 0.05f, 0, -50, 10 }, { 4, 0.4f, 0.05f, 0.05f, 0, -55, 10 },
			{ 6, -0.55f, 0.05f, -0.25f, 0, 70, -10 }, { 11, -0.55f, 0.05f, -0.2f, 0, 75, -10 }, { 18, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_THROW = { { 0, 0.15f, 0.45f, 0.25f, 50, 0, 10 }, { 5.5f, 0.18f, 0.5f, 0.3f, 55, 0, 10 },
			{ 7, -0.05f, 0.1f, -0.6f, -30, 0, 0 }, { 12, -0.05f, -0.1f, -0.5f, -20, 0, 0 }, { 18, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_RAISE = { { 0, 0, 0, 0, 0, 0, 0 }, { 6, 0, 0.7f, 0.1f, 60, 0, -10 }, { 60, 0, 0.72f, 0.1f, 62, 0, -10 },
			{ 72, -0.1f, -0.3f, -0.3f, -80, 0, 5 }, { 85, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_SLAM = { { 0, 0, -0.4f, -0.3f, -80, 0, 5 }, { 9, 0, -0.35f, -0.28f, -75, 0, 5 },
			{ 18, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_RAISED_HOLD = { { 0, 0, -0.1f, 0.1f, 20, 0, 0 }, { 3, 0, 0.6f, 0.1f, 55, 0, -10 },
			{ 60, 0, 0.6f, 0.1f, 55, 0, -10 } };
	private static final float[][] FP_LEVEL = { { 0, -0.05f, 0.05f, -0.3f, -5, 0, 0 }, { 12, -0.05f, 0.05f, -0.3f, -5, 0, 0 },
			{ 16, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_WHIRL = { { 0, 0, 0.5f, 0, 40, 0, -40 }, { 2.5f, 0.2f, 0.6f, 0, 40, 60, -40 },
			{ 4.5f, -0.2f, 0.6f, 0, 40, -60, -40 }, { 6.2f, 0.2f, 0.6f, 0, 40, 60, -40 }, { 7.8f, -0.2f, 0.6f, 0, 40, -60, -40 },
			{ 9.2f, 0.15f, 0.5f, 0.2f, 50, 0, 0 }, { 10.5f, -0.05f, 0.1f, -0.6f, -30, 0, 0 }, { 16, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_CROSS = { { 0, -0.2f, 0.05f, -0.2f, -10, 35, 0 }, { 8, -0.22f, 0.15f, -0.25f, -10, 38, 0 },
			{ 10.5f, 0.3f, 0.05f, -0.3f, -5, -45, 20 }, { 16, 0.3f, 0.05f, -0.28f, -5, -45, 20 }, { 23, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] FP_SPREAD = { { 0, -0.15f, -0.1f, 0.1f, 10, 40, 0 }, { 4, -0.15f, -0.1f, 0.1f, 10, 40, 0 },
			{ 5.5f, 0.35f, 0.15f, 0.1f, 20, -50, 30 }, { 12, 0.35f, 0.12f, 0.1f, 18, -45, 28 }, { 18, 0, 0, 0, 0, 0, 0 } };

	/** The first-person table for {@code anim}: {lead, other} (other may be null), or null for none. */
	private static float[][][] fpFor(int anim) {
		return switch (anim) {
			case ThorFx.ANIM_THUNDER_FIST, ThorFx.ANIM_GRAPPLE -> new float[][][] { FP_PUNCH, null };
			case ThorFx.ANIM_STORM_RUSH -> new float[][][] { FP_RUSH, FP_RUSH_OFF };
			case ThorFx.ANIM_LIGHTNING_PALM -> new float[][][] { FP_PALM, null };
			case ThorFx.ANIM_STATIC_BURST, ThorFx.ANIM_INCARNATE_BURST, ThorFx.ANIM_GOD_OF_THUNDER -> new float[][][] { FP_SPREAD, FP_SPREAD };
			case ThorFx.ANIM_GROUND_POUND, ThorFx.ANIM_STORM_CLEAVE, ThorFx.ANIM_STORM_ANCHOR -> new float[][][] { FP_OVERHEAD, FP_OVERHEAD };
			case ThorFx.ANIM_LEAP, ThorFx.ANIM_EXECUTIONER, ThorFx.ANIM_METEOR_DIVE -> new float[][][] { FP_RAISED_HOLD, FP_RAISED_HOLD };
			case ThorFx.ANIM_LEAP_SLAM, ThorFx.ANIM_EXECUTIONER_SLAM -> new float[][][] { FP_SLAM, FP_SLAM };
			case ThorFx.ANIM_WHIRLWIND -> new float[][][] { FP_WHIRL, null };
			case ThorFx.ANIM_STORM_DASH, ThorFx.ANIM_CROSSFIRE -> new float[][][] { FP_LEVEL, FP_LEVEL };
			case ThorFx.ANIM_BIFROST_HURL, ThorFx.ANIM_TWIN_THROW, ThorFx.ANIM_JUDGEMENT -> new float[][][] { FP_THROW,
					anim == ThorFx.ANIM_TWIN_THROW ? FP_THROW : null };
			case ThorFx.ANIM_BIFROST_STEP, ThorFx.ANIM_RENDING_ARC -> new float[][][] { FP_SWEEP, null };
			case ThorFx.ANIM_THUNDERSTRUCK -> new float[][][] { FP_CROSS, FP_CROSS };
			case ThorFx.ANIM_WRATH_STORM, ThorFx.ANIM_RAGNAROK -> new float[][][] { FP_RAISE, anim == ThorFx.ANIM_RAGNAROK ? FP_RAISE : null };
			default -> null;
		};
	}

	/** How much (0..1) a kit move owns {@code p}'s first-person hands right now (vanilla's swing is held off by it). */
	public static float firstPersonWeight(Player p, float partial) {
		ThorFx fx = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.THOR_FX, null);
		if (fx == null) {
			return 0f;
		}
		if (fx.has(ThorFx.CH_FIST)) {
			return 1f;
		}
		Clip c = clipFor(fx.anim());
		if (c == null || fpFor(fx.anim()) == null) {
			return 0f;
		}
		return weight(c, age(p, c, fx, partial));
	}

	/** First person, in the hand's space before the item / arm is drawn: the move's hand motion. */
	public static void firstPerson(Player p, boolean mainHand, HumanoidArm arm, float partial, PoseStack pose) {
		ThorFx fx = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.THOR_FX, null);
		if (fx == null) {
			return;
		}
		float[] v;
		float w;
		if (fx.has(ThorFx.CH_FIST)) {
			if (!mainHand) {
				return;
			}
			float held = p.level().getGameTime() - fx.chargeStart() + partial;
			float build = Mth.clamp(held / ThorKit.FIST_CHARGE_TICKS, 0f, 1f);
			float tr = Mth.sin((p.tickCount + partial) * 3.1f) * 0.01f * (1f + build * 3f);
			v = new float[] { 0, 0.1f + tr, -0.15f - build * 0.05f, 0.25f + build * 0.08f, 25, -10, 10 };
			w = Mth.clamp(held / 4f, 0f, 1f);
		} else {
			Clip c = clipFor(fx.anim());
			float[][][] t = fpFor(fx.anim());
			if (c == null || t == null) {
				return;
			}
			boolean lead = leadRight(p, fx.anim()) == (arm == HumanoidArm.RIGHT);
			float[][] table = lead ? t[0] : t[1];
			if (table == null) {
				return;
			}
			float age = age(p, c, fx, partial);
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

	// (v0.17.7: hidesHeld -- hiding the held weapon while a spectral copy flew -- is gone: Twin Throw, Judgement and Ragnarok now
	// really take the weapon out of the hand, com.projecthero.mod.power.ThorThrown)
}
