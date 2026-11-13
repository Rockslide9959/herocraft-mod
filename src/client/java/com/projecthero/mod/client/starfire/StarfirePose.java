package com.projecthero.mod.client.starfire;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireAbilityManager;
import com.projecthero.mod.starfire.StarfireConfig;
import com.projecthero.mod.starfire.StarfireMoves;
import com.projecthero.mod.starfire.data.StarfireState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 animation round (user: "make her ability animations better"): Starfire's body language for every move, on the vanilla
 * humanoid model ({@code HumanoidModelMixin}, after the flight pose so a move made mid-air wins) and on the first-person arms
 * ({@code mixin.StarfireFirstPersonPoseMixin}). Everything is driven by her synced {@link StarfireState} -- each move's start is its
 * cooldown stamp minus its cooldown, the holds are their synced flags, the throwing hand and the carry are {@link StarfireState#anim}
 * bits -- so every viewer sees the same thing. Keyframes are smoothstepped, each layer eases in from whatever pose it lands on and
 * eases out again (anticipation, action, follow-through; no snapping), and layers stack: flight under the holds under the moves.
 *
 * <ul>
 *   <li><b>Starbolt</b>: an overhand sweep from alternating hands -- wound up high and out, released along the look, followed through
 *   across the body with the torso twisting into it and the other arm swinging back for balance.</li>
 *   <li><b>Barrage</b>: rapid left-right jabs, the torso turning with the arc the bolts spray across.</li>
 *   <li><b>Star Burst</b>: the hands sweep in to cup the ball at her chest, drawing it in and trembling as it grows; on release both
 *   palms drive out along the look, then the recoil throws her back.</li>
 *   <li><b>Eye Beams</b>: head and shoulders thrust forward, fists swept back behind her.</li>
 *   <li><b>Supernova</b>: curled into a tight ball, arms crossed and shaking harder as the sun swells; at the blast she flings out
 *   into a star, head thrown back.</li>
 *   <li><b>Star Dash / Comet Dive</b>: the body laid along the line of travel, a fist leading; a brake flourish after the dash, a
 *   fists-down landing crouch after the dive.</li>
 *   <li><b>Tamaranean Charge</b>: a coil, then a shoulder-first tackle; <b>Ground Pound</b>: fists clasped overhead, then a crouching
 *   double-fist slam; <b>Grab</b>: a reach and a two-handed carry, then an overhead hurl.</li>
 *   <li><b>Language Kiss</b> (lean in, hands up, a heel kick) and <b>Glorious Radiance</b> (arms open wide to the light).</li>
 *   <li><b>Flight</b>: a graceful hover (arms a little out, one knee back), trailing arms cruising, arms swept back at full speed, and a
 *   lean-back brake when she pulls up; all blended off the shared flight lean.</li>
 * </ul>
 * The client trackers here ({@link #TRACKS}) are dropped when leaving a world ({@link #clear}) and pruned to the players in view.
 */
public final class StarfirePose {
	private StarfirePose() {
	}

	// ================================================================ keyframes
	// Arm rows: {tick, xRot, yRot, zRot, aimX, aimY} for the RIGHT arm (yRot / zRot mirror for the left). aimX / aimY 1 = the value is
	// an offset from the look (pitch / yaw), 0 = absolute; interpolated, so a swing can travel from a body pose onto the look.
	// Body rows: {tick, twist, lean, rightLegX, leftLegX, headX, headForward(px), crouch, legSpread} for a right-side move (twist and
	// the legs mirror for the left). Lean is degrees, the rest radians.

	private static final float[][] THROW_ARM = {
			{ 0.0f, -0.4f, 0.2f, 0.2f, 0, 0 },
			{ 0.55f, -2.75f, 0.7f, 0.55f, 0, 0 },
			{ 1.25f, 0.0f, -0.1f, 0.0f, 1, 1 },
			{ 3.2f, 0.62f, -0.62f, -0.1f, 1, 1 },
			{ 9.0f, 0.45f, -0.3f, 0.0f, 1, 1 } };
	private static final float[][] THROW_OTHER = {
			{ 0.0f, 0f, 0f, 0f, 0, 0 },
			{ 0.55f, -0.55f, 0f, 0.12f, 0, 0 },
			{ 1.25f, 0.45f, 0f, 0.22f, 0, 0 },
			{ 3.2f, 0.55f, 0f, 0.28f, 0, 0 },
			{ 9.0f, 0.2f, 0f, 0.1f, 0, 0 } };
	private static final float[][] THROW_BODY = {
			{ 0.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 0.55f, 0.4f, -3f, 0.12f, -0.1f, 0f, 0f, 0f, 0f },
			{ 1.25f, -0.24f, 6f, 0.3f, -0.25f, 0f, -0.4f, 0f, 0f },
			{ 3.2f, -0.42f, 8f, 0.32f, -0.28f, 0f, -0.5f, 0f, 0f },
			{ 9.0f, -0.1f, 2f, 0.1f, -0.1f, 0f, 0f, 0f, 0f } };
	private static final float THROW_IN = 0.55f, THROW_HOLD = 3.2f, THROW_OUT = 9.0f;

	/** One barrage jab, ticks since that hand's last bolt; the yaw is the arc's on top. */
	private static final float[][] JAB_ARM = {
			{ 0.0f, 0.75f, 0.3f, 0.25f, 1, 1 },
			{ 0.8f, 0.0f, -0.05f, 0.0f, 1, 1 },
			{ 2.2f, 0.12f, -0.12f, 0.0f, 1, 1 },
			{ 5.0f, 0.75f, 0.3f, 0.25f, 1, 1 } };

	private static final float[][] BURST_CHARGE_ARM = {
			{ 0f, -0.3f, 0.1f, 0.85f, 0, 0 },
			{ 3f, -0.85f, -0.15f, 0.5f, 0, 0 },
			{ 6.5f, -1.05f, -0.52f, 0.06f, 0, 0 },
			{ 40f, -0.85f, -0.56f, 0.06f, 0, 0 } };
	private static final float[][] BURST_CHARGE_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 6.5f, 0f, -4f, -0.25f, 0.3f, 0.15f, 0f, 0.12f, 0f },
			{ 40f, 0f, -8f, -0.3f, 0.38f, 0.2f, 0f, 0.22f, 0f } };
	private static final float[][] BURST_RELEASE_ARM = {
			{ 0f, -0.85f, -0.56f, 0.06f, 0, 0 },
			{ 1.2f, 0.02f, -0.12f, 0f, 1, 1 },
			{ 2.6f, 0.0f, -0.1f, 0f, 1, 1 },
			{ 4.8f, -0.8f, 0.05f, 0.12f, 1, 1 },
			{ 16f, -0.3f, 0.05f, 0.15f, 1, 1 } };
	private static final float[][] BURST_RELEASE_BODY = {
			{ 0f, 0f, -8f, -0.3f, 0.38f, 0.2f, 0f, 0.22f, 0f },
			{ 1.2f, 0f, 11f, -0.35f, 0.45f, 0f, -0.8f, 0f, 0f },
			{ 2.6f, 0f, 11f, -0.35f, 0.45f, 0f, -0.8f, 0f, 0f },
			{ 4.8f, 0f, -14f, -0.25f, 0.15f, -0.3f, 0.5f, 0f, 0f },
			{ 16f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] NOVA_CHARGE_ARM = {
			{ 0f, 0f, 0f, 0f, 0, 0 },
			{ 5f, -1.3f, -0.78f, 0f, 0, 0 },
			{ 32f, -1.4f, -0.9f, 0.05f, 0, 0 },
			{ 40f, -1.05f, -0.62f, 0.4f, 0, 0 } };
	private static final float[][] NOVA_CHARGE_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 5f, 0f, 4f, -0.9f, -0.5f, 0.35f, -0.5f, 0f, 0f },
			{ 32f, 0f, 6f, -1.15f, -0.8f, 0.5f, -0.8f, 0f, 0f },
			{ 40f, 0f, 0f, -0.6f, -0.4f, 0.2f, 0f, 0f, 0f } };
	private static final float[][] NOVA_BLAST_ARM = {
			{ 0f, -1.05f, -0.62f, 0.4f, 0, 0 },
			{ 1.5f, -0.3f, 0f, 2.35f, 0, 0 },
			{ 12f, -0.35f, 0f, 2.2f, 0, 0 },
			{ 26f, 0f, 0f, 0.2f, 0, 0 } };
	private static final float[][] NOVA_BLAST_BODY = {
			{ 0f, 0f, 0f, -0.6f, -0.4f, 0.2f, 0f, 0f, 0f },
			{ 1.5f, 0f, -10f, 0.1f, 0.1f, -0.55f, 0.6f, 0f, 0.45f },
			{ 12f, 0f, -8f, 0.1f, 0.1f, -0.45f, 0.4f, 0f, 0.4f },
			{ 26f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] DASH_LEAD = {
			{ 0f, -1.0f, 0f, 0.1f, 0, 0 },
			{ 1.2f, -2.95f, -0.08f, 0.05f, 0, 0 },
			{ 6f, -2.95f, -0.08f, 0.05f, 0, 0 },
			{ 9f, -1.2f, 0f, 0.7f, 0, 0 },
			{ 13f, 0f, 0f, 0.1f, 0, 0 } };
	private static final float[][] DASH_OTHER = {
			{ 0f, 0.3f, 0f, 0.2f, 0, 0 },
			{ 1.2f, 0.4f, 0f, 0.18f, 0, 0 },
			{ 6f, 0.4f, 0f, 0.18f, 0, 0 },
			{ 9f, -0.6f, 0f, 0.8f, 0, 0 },
			{ 13f, 0f, 0f, 0.1f, 0, 0 } };
	private static final float[][] DASH_BODY = {
			{ 0f, 0f, -6f, -0.4f, 0.2f, 0f, 0f, 0.1f, 0f },
			{ 1.2f, 0f, 0f, 0.05f, 0.3f, 0f, 0f, 0f, 0f },
			{ 6f, 0f, 0f, 0.05f, 0.3f, 0f, 0f, 0f, 0f },
			{ 9f, 0f, -14f, -0.45f, -0.1f, 0f, 0f, 0f, 0.15f },
			{ 13f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] IMPACT_ARM = {
			{ 0f, -2.9f, -0.1f, 0f, 0, 0 },
			{ 1f, -0.45f, -0.1f, 0.35f, 0, 0 },
			{ 7f, -0.45f, -0.1f, 0.35f, 0, 0 },
			{ 15f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] IMPACT_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 1f, 0f, 8f, -0.6f, 0.5f, 0.35f, 0f, 0.85f, 0.1f },
			{ 7f, 0f, 8f, -0.6f, 0.5f, 0.35f, 0f, 0.85f, 0.1f },
			{ 15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] CHARGE_LEAD = {
			{ 0f, 0.45f, 0.1f, 0.1f, 0, 0 },
			{ 1.4f, -1.7f, -0.45f, 0f, 0, 0 },
			{ 6.5f, -1.7f, -0.45f, 0f, 0, 0 },
			{ 9f, -1.2f, -0.2f, 0.2f, 0, 0 },
			{ 15f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] CHARGE_OTHER = {
			{ 0f, -0.4f, 0f, 0.1f, 0, 0 },
			{ 1.4f, -1.15f, 0.25f, 0.05f, 0, 0 },
			{ 6.5f, -1.15f, 0.25f, 0.05f, 0, 0 },
			{ 9f, -0.5f, 0f, 0.3f, 0, 0 },
			{ 15f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] CHARGE_BODY = {
			{ 0f, 0.3f, -6f, -0.3f, 0.3f, 0f, 0f, 0.15f, 0f },
			{ 1.4f, -0.45f, 48f, 0.55f, -0.45f, 0.3f, -0.6f, 0f, 0f },
			{ 6.5f, -0.45f, 48f, 0.55f, -0.45f, 0.3f, -0.6f, 0f, 0f },
			{ 9f, -0.2f, 20f, 0.2f, -0.2f, 0.1f, 0f, 0f, 0f },
			{ 15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] SLAM_ARM = {
			{ 0f, -2.95f, -0.28f, 0f, 0, 0 },
			{ 1.2f, -3.1f, -0.3f, 0f, 0, 0 },
			{ 2.2f, -0.55f, -0.18f, 0.1f, 0, 0 },
			{ 8f, -0.55f, -0.18f, 0.1f, 0, 0 },
			{ 18f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] SLAM_BODY = {
			{ 0f, 0f, 0f, -0.5f, -0.3f, 0.2f, 0f, 0f, 0f },
			{ 1.2f, 0f, -5f, -0.2f, 0f, -0.1f, 0f, 0.1f, 0f },
			{ 2.2f, 0f, 12f, -0.5f, 0.45f, 0.45f, 0f, 1.0f, 0.12f },
			{ 8f, 0f, 12f, -0.5f, 0.45f, 0.45f, 0f, 1.0f, 0.12f },
			{ 18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float SLAM_WINDUP = 1.2f;

	private static final float[][] REACH_ARM = {
			{ 0f, -0.5f, 0.1f, 0.1f, 0, 0 },
			{ 2f, -1.6f, -0.12f, 0f, 0, 0 },
			{ 4f, -1.3f, -0.32f, 0f, 0, 0 } };
	private static final float[][] HURL_ARM = {
			{ 0f, -1.3f, -0.32f, 0f, 0, 0 },
			{ 0.8f, -2.8f, -0.2f, 0.05f, 0, 0 },
			{ 1.8f, 0.15f, -0.15f, 0f, 1, 1 },
			{ 4f, 0.6f, -0.25f, 0f, 1, 1 },
			{ 12f, 0.3f, 0f, 0.1f, 1, 1 } };
	private static final float[][] HURL_BODY = {
			{ 0f, 0f, -5f, -0.15f, 0.2f, 0f, 0f, 0f, 0f },
			{ 0.8f, 0f, -9f, -0.2f, 0.3f, -0.2f, 0.3f, 0f, 0f },
			{ 1.8f, 0f, 14f, 0.35f, -0.3f, 0.1f, -0.6f, 0f, 0f },
			{ 4f, 0f, 10f, 0.3f, -0.25f, 0f, -0.4f, 0f, 0f },
			{ 12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] KISS_ARM = {
			{ 0f, 0f, 0f, 0f, 0, 0 },
			{ 4f, -1.5f, -0.6f, 0.1f, 0, 0 },
			{ 14f, -1.45f, -0.55f, 0.1f, 0, 0 },
			{ 24f, 0f, 0f, 0f, 0, 0 } };
	private static final float[][] KISS_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 4f, 0f, 10f, 0f, 0.75f, 0.15f, -0.8f, 0f, 0f },
			{ 14f, 0f, 10f, 0f, 0.75f, 0.15f, -0.8f, 0f, 0f },
			{ 24f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] RADIANCE_ON_ARM = {
			{ 0f, 0f, 0f, 0f, 0, 0 },
			{ 3f, -0.45f, 0f, 1.4f, 0, 0 },
			{ 9f, -0.5f, 0f, 1.3f, 0, 0 },
			{ 20f, 0f, 0f, 0.1f, 0, 0 } };
	private static final float[][] RADIANCE_OFF_ARM = {
			{ 0f, 0f, 0f, 0f, 0, 0 },
			{ 3f, -0.75f, -0.55f, 0.15f, 0, 0 },
			{ 9f, -0.7f, -0.5f, 0.15f, 0, 0 },
			{ 18f, 0f, 0f, 0f, 0, 0 } };
	private static final float[][] RADIANCE_ON_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 3f, 0f, -7f, -0.1f, 0.1f, -0.35f, 0f, 0f, 0.12f },
			{ 9f, 0f, -6f, -0.1f, 0.1f, -0.3f, 0f, 0f, 0.1f },
			{ 20f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] RADIANCE_OFF_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 3f, 0f, 3f, 0f, 0f, 0.15f, 0f, 0f, 0f },
			{ 9f, 0f, 3f, 0f, 0f, 0.15f, 0f, 0f, 0f },
			{ 18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] BRAKE_ARM = {
			{ 0f, 0.2f, 0f, 0.2f, 0, 0 },
			{ 3f, -1.2f, 0f, 0.75f, 0, 0 },
			{ 8f, -0.9f, 0f, 0.6f, 0, 0 },
			{ 16f, -0.2f, 0f, 0.35f, 0, 0 } };
	private static final float[][] BRAKE_BODY = {
			{ 0f, 0f, 0f, 0.05f, 0.2f, 0f, 0f, 0f, 0f },
			{ 3f, 0f, -26f, -0.75f, -0.4f, 0f, 0f, 0f, 0f },
			{ 8f, 0f, -16f, -0.55f, -0.2f, 0f, 0f, 0f, 0f },
			{ 16f, 0f, 0f, -0.1f, 0.3f, 0f, 0f, 0f, 0f } };

	/** Ticks after Z each barrage bolt leaves (the server's {@link StarfireMoves#barrageDue}). */
	private static final int[] BARRAGE_FIRE = new int[StarfireConfig.BARRAGE_BOLTS];

	static {
		for (int i = 0; i < BARRAGE_FIRE.length; i++) {
			int e = 0;
			while (StarfireMoves.barrageDue(e) <= i && e < 200) {
				e++;
			}
			BARRAGE_FIRE[i] = e;
		}
	}

	/** Smoothstepped keyframe sample, clamped to the first / last row outside the table. */
	static float[] key(float[][] k, float t) {
		if (t <= k[0][0]) {
			return k[0];
		}
		for (int i = 1; i < k.length; i++) {
			if (t <= k[i][0]) {
				float[] a = k[i - 1], b = k[i];
				float u = (t - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				u = u * u * (3f - 2f * u);
				float[] out = new float[a.length];
				out[0] = t;
				for (int c = 1; c < a.length; c++) {
					out[c] = Mth.lerp(u, a[c], b[c]);
				}
				return out;
			}
		}
		return k[k.length - 1];
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** In over {@code [0, in]}, held to {@code hold}, out by {@code out}; 0 outside. */
	static float env(float t, float in, float hold, float out) {
		if (t < 0f || t >= out) {
			return 0f;
		}
		float a = in <= 0f ? 1f : smooth(t / in);
		float b = t <= hold ? 1f : 1f - smooth((t - hold) / (out - hold));
		return a * b;
	}

	// ================================================================ client tracking (per player in view)

	/** What the synced state alone can't say: who threw when (to cross-fade the hands), the beams' ease, the brake, the slam. */
	static final class Track {
		long boltReady = Long.MIN_VALUE;
		/** [0] right, [1] left: this throw's and the previous throw's start (game time). */
		final float[] throwAt = { -1000f, -1000f };
		final float[] prevAt = { -1000f, -1000f };
		float lastThrow = -1000f, prevThrow = -1000f;
		int lastSide = 1, prevSide = -1;
		boolean beams;
		float beamStart = -1000f;
		float beamW, beamWPrev;
		float flyW, flyWPrev;
		boolean carrying;
		float carryStart = -1000f;
		boolean diving;
		float diveStart = -1000f;
		long lastDiveUntil;
		float impactAt = -1000f;
		float stageW, stageWPrev;
		long poundReady = Long.MIN_VALUE;
		float poundStart = -1000f;
		float slamAt = -1000f;
		boolean slamFromAir;
		boolean pounding;
		double lastX, lastY, lastZ;
		boolean hasLast;
		double vy, vyPrev, hs;
		final double[] speeds = new double[4];
		float brakeAt = -1000f;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	/** Leaving a world. */
	public static void clear() {
		TRACKS.clear();
		LEAN_CACHE.clear();
	}

	/** Once a client tick: every Starfire in view. */
	public static void clientTick(ClientLevel level) {
		if (level == null) {
			clear();
			return;
		}
		Set<UUID> present = new HashSet<>();
		long now = level.getGameTime();
		for (Player p : level.players()) {
			StarfireState s = Starfire.peek(p);
			if (s == null || !s.hasPower) {
				continue;
			}
			present.add(p.getUUID());
			tick(p, s, TRACKS.computeIfAbsent(p.getUUID(), k -> new Track()), now);
		}
		TRACKS.keySet().retainAll(present);
		if (LEAN_CACHE.size() > 64) {
			LEAN_CACHE.clear();
		}
	}

	private static void tick(Player p, StarfireState s, Track tr, long now) {
		// movement (other players' velocity is unreliable on the client; their interpolated position is not)
		double dx = tr.hasLast ? p.getX() - tr.lastX : 0, dy = tr.hasLast ? p.getY() - tr.lastY : 0, dz = tr.hasLast ? p.getZ() - tr.lastZ : 0;
		tr.lastX = p.getX();
		tr.lastY = p.getY();
		tr.lastZ = p.getZ();
		tr.hasLast = true;
		tr.vyPrev = tr.vy;
		tr.vy = dy;
		tr.hs = Math.hypot(dx, dz);
		double old = tr.speeds[3];
		System.arraycopy(tr.speeds, 0, tr.speeds, 1, 3);
		tr.speeds[0] = tr.hs;

		// starbolts: each new cooldown stamp is a throw, from the hand the synced bit names
		Long ready = s.abilityReadyAt.get(StarfireAbilityManager.STARBOLT);
		if (ready != null && ready != tr.boltReady) {
			long start = ready - StarfireConfig.BOLT_COOLDOWN;
			if (start > now - 10) {
				int side = (s.anim & StarfireState.ANIM_RIGHT_HAND) != 0 ? 1 : -1;
				int i = side > 0 ? 0 : 1;
				tr.prevAt[i] = tr.throwAt[i];
				tr.throwAt[i] = start;
				tr.prevThrow = tr.lastThrow;
				tr.prevSide = tr.lastSide;
				tr.lastThrow = start;
				tr.lastSide = side;
			}
			tr.boltReady = ready;
		}
		// eye beams: eased in and out
		if (s.beams && !tr.beams) {
			tr.beamStart = now;
		}
		tr.beams = s.beams;
		tr.beamWPrev = tr.beamW;
		tr.beamW = approach(tr.beamW, s.beams ? 1f : 0f, s.beams ? 0.4f : 0.25f);
		// flight
		tr.flyWPrev = tr.flyW;
		tr.flyW = approach(tr.flyW, s.flying ? 1f : 0f, 0.18f);
		// a brake: pulling up out of real speed (not the end of a dash / tackle / dive, which have their own recoveries)
		boolean moveEnding = now - started(s, StarfireAbilityManager.STAR_DASH, StarfireConfig.DASH_COOLDOWN) < 16
				|| now - started(s, StarfireAbilityManager.CHARGE, StarfireConfig.CHARGE_COOLDOWN) < 18 || s.diveUntil > now - 10;
		if (s.flying && !moveEnding && now - tr.brakeAt > 20 && old > 0.6 && tr.hs < old * 0.72) {
			tr.brakeAt = now;
		}
		// the carry
		boolean carrying = (s.anim & StarfireState.ANIM_CARRYING) != 0;
		if (carrying && !tr.carrying) {
			tr.carryStart = now;
		}
		tr.carrying = carrying;
		// Comet Dive: when it began, launch (rising) vs dive, and the landing (its timer drops to impact + 3)
		boolean diving = s.diveUntil > now;
		if (diving && !tr.diving) {
			tr.diveStart = now;
		}
		tr.diving = diving;
		if (s.diveUntil > 0 && s.diveUntil < tr.lastDiveUntil) {
			tr.impactAt = s.diveUntil - 3;
		}
		tr.lastDiveUntil = s.diveUntil;
		tr.stageWPrev = tr.stageW;
		tr.stageW = approach(tr.stageW, diving && tr.vy > 0.3 ? 1f : 0f, 0.35f);
		// Ground Pound: on the ground it slams at once; from the air it slams where she lands
		Long pr = s.abilityReadyAt.get(StarfireAbilityManager.GROUND_POUND);
		if (pr != null && pr != tr.poundReady) {
			long start = pr - StarfireConfig.POUND_COOLDOWN;
			if (start > now - 10) {
				tr.poundStart = start;
				if (p.onGround()) {
					tr.slamAt = start;
					tr.slamFromAir = false;
					tr.pounding = false;
				} else {
					tr.pounding = true;
				}
			}
			tr.poundReady = pr;
		}
		if (tr.pounding && ((p.onGround() && now > tr.poundStart) || now - tr.poundStart > StarfireConfig.POUND_MAX_FALL_TICKS + 4)) {
			tr.pounding = false;
			tr.slamAt = now;
			tr.slamFromAir = true;
		}
	}

	private static float approach(float v, float target, float rate) {
		float n = v + (target - v) * rate;
		return Math.abs(target - n) < 0.002f ? target : n;
	}

	/** When a move began: its cooldown stamp minus its cooldown (-1000 if never). */
	private static float started(StarfireState s, String id, int cooldown) {
		Long r = s.abilityReadyAt.get(id);
		return r == null ? -1000f : r - cooldown;
	}

	// ================================================================ evaluation

	/** One evaluation: the lean channel (always) and, with a model, the limbs. */
	private static final class Ctx {
		HumanoidModel<?> m;
		float aimX, aimY;
		int lead = 1;
		float twist, lean, headX, headFwd, headRoll, crouch, spread;
		float ovTarget, ovW;
		final float[] armW = new float[2];

		void arm(int side, float[] row, float w) {
			arm(side, row, w, 0f);
		}

		/** Poses one arm toward a row (right-arm values; mirrored for the left), {@code yawAdd} on top of an aimed yaw. */
		void arm(int side, float[] row, float w, float yawAdd) {
			if (m == null || w <= 0.001f) {
				return;
			}
			ModelPart part = side > 0 ? m.rightArm : m.leftArm;
			float x = row[1] + row[4] * aimX;
			float y = side * row[2] + row[5] * (aimY + yawAdd);
			float z = side * row[3];
			part.xRot = Mth.lerp(w, part.xRot, x);
			part.yRot = Mth.lerp(w, part.yRot, y);
			part.zRot = Mth.lerp(w, part.zRot, z);
			int i = side > 0 ? 0 : 1;
			armW[i] += (1f - armW[i]) * w;
		}

		void both(float[] row, float w) {
			arm(1, row, w);
			arm(-1, row, w);
		}

		/** The body row of a move on {@code side} (+1 right): twist and legs mirror; {@code legs} scales the leg pose. */
		void body(float[] row, int side, float w, float legs) {
			if (w <= 0.001f) {
				return;
			}
			twist = Mth.lerp(w, twist, side * row[1]);
			lean = Mth.lerp(w, lean, row[2]);
			headX = Mth.lerp(w, headX, row[5]);
			headFwd = Mth.lerp(w, headFwd, row[6]);
			crouch = Mth.lerp(w, crouch, row[7]);
			spread = Mth.lerp(w, spread, row[8]);
			if (m != null && legs > 0f) {
				float rl = side > 0 ? row[3] : row[4];
				float ll = side > 0 ? row[4] : row[3];
				float lw = w * legs;
				m.rightLeg.xRot = Mth.lerp(lw, m.rightLeg.xRot, rl);
				m.leftLeg.xRot = Mth.lerp(lw, m.leftLeg.xRot, ll);
				m.rightLeg.yRot = Mth.lerp(lw, m.rightLeg.yRot, 0f);
				m.leftLeg.yRot = Mth.lerp(lw, m.leftLeg.yRot, 0f);
			}
		}

		/** The lean overridden toward {@code target} degrees (laid along a dash / dive). */
		void override(float target, float w) {
			if (w <= 0.001f) {
				return;
			}
			ovTarget = ovW <= 0f ? target : Mth.lerp(w, ovTarget, target);
			ovW += (1f - ovW) * w;
		}
	}

	private static void evaluate(Player p, StarfireState s, Ctx c, float partial) {
		Track tr = TRACKS.get(p.getUUID());
		long gt = p.level().getGameTime();
		float now = gt + partial;
		boolean rightMain = p.getMainArm() == HumanoidArm.RIGHT;
		c.lead = rightMain ? 1 : -1;
		float breathe = Mth.sin((p.tickCount + partial) * 0.08f);

		// ---- flight: hover / cruise / speed, blended off the shared lean; a lean-back brake when she pulls up
		float flyW = tr == null ? (s.flying ? 1f : 0f) : Mth.lerp(partial, tr.flyWPrev, tr.flyW);
		if (flyW > 0.001f && c.m != null) {
			float l = FlightPoseHelper.lean(p, partial);
			float hover = 1f - Mth.clamp(Math.abs(l) / 30f, 0f, 1f);
			float fast = Mth.clamp((l - 35f) / 45f, 0f, 1f);
			float cruise = Math.max(0f, 1f - hover - fast);
			float flutter = Mth.sin((p.tickCount + partial) * 0.15f);
			for (int side = 1; side >= -1; side -= 2) {
				float ph = side > 0 ? 0f : 0.9f;
				float b = Mth.sin((p.tickCount + partial) * 0.08f + ph);
				float x = hover * (-0.15f + 0.05f * b) + cruise * 0.35f + fast * 0.1f;
				float z = hover * (0.36f + 0.05f * b) + cruise * 0.24f + fast * 0.16f + 0.025f * flutter;
				c.arm(side, new float[] { 0, x, 0.05f * hover, z, 0, 0 }, flyW);
			}
			float rl = hover * (-0.12f + 0.04f * breathe) + cruise * 0.12f + fast * 0.04f;
			float ll = hover * (0.32f - 0.04f * breathe) + cruise * 0.32f + fast * 0.2f;
			c.m.rightLeg.xRot = Mth.lerp(flyW, c.m.rightLeg.xRot, rl);
			c.m.leftLeg.xRot = Mth.lerp(flyW, c.m.leftLeg.xRot, ll);
		}
		if (tr != null && s.flying) {
			float a = now - tr.brakeAt;
			float w = env(a, 2f, 8f, 16f) * flyW;
			if (w > 0f) {
				c.both(key(BRAKE_ARM, a), w);
				c.body(key(BRAKE_BODY, a), 1, w, 1f);
			}
		}

		// ---- the carry (Tamaranean Grab holding a creature)
		if (tr != null && tr.carrying) {
			float a = now - tr.carryStart;
			float w = env(a, 1.5f, 1e9f, 2e9f);
			float[] row = key(REACH_ARM, a).clone();
			row[1] += 0.04f * breathe;
			c.both(row, w);
			c.body(new float[] { 0, 0, -5f, -0.15f, 0.2f, 0, 0, 0, 0 }, 1, w * smooth(a / 4f), 0.6f);
		}

		// ---- Eye Beams: head and shoulders thrust forward, fists swept back
		float beamW = tr == null ? (s.beams ? 1f : 0f) : Mth.lerp(partial, tr.beamWPrev, tr.beamW);
		if (beamW > 0.001f) {
			float tb = tr == null ? 10f : now - tr.beamStart;
			float bump = s.beams ? env(tb, 2f, 2f, 8f) : 0f;
			float jit = Mth.sin(now * 3.1f) * 0.8f;
			float tremble = Mth.sin(now * 2.3f) * 0.03f;
			c.both(new float[] { 0, 0.6f + tremble, 0.15f, 0.42f, 0, 0 }, beamW);
			c.body(new float[] { 0, 0f, 17f + 7f * bump + jit, 0.35f, -0.1f, 0f, -1.4f - 0.6f * bump, 0f, 0f }, 1, beamW, 0.8f);
		}

		// ---- Star Burst charging: hands sweep in and cup the ball, drawing it in and trembling as it grows
		if (s.chargeMove == StarfireState.CHARGE_BURST && s.chargeStart > 0) {
			float ct = now - s.chargeStart;
			float f = Mth.clamp(ct / StarfireConfig.BURST_CHARGE_TICKS, 0f, 1f);
			float w = env(ct, 2.5f, 1e9f, 2e9f);
			for (int side = 1; side >= -1; side -= 2) {
				float[] row = key(BURST_CHARGE_ARM, ct).clone();
				row[1] += Mth.sin(now * 2.7f + side) * 0.035f * (0.3f + f);
				c.arm(side, row, w);
			}
			c.body(key(BURST_CHARGE_BODY, ct), 1, w, 0.8f);
		}
		// ---- Supernova charging: curled into a ball, arms crossed, shaking harder as the sun swells
		if (s.chargeMove == StarfireState.CHARGE_NOVA && s.chargeStart > 0) {
			float ct = now - s.chargeStart;
			float f = Mth.clamp(ct / StarfireConfig.NOVA_CHARGE_TICKS, 0f, 1f);
			float w = env(ct, 3f, 1e9f, 2e9f);
			float shake = (0.02f + 0.07f * f * f);
			for (int side = 1; side >= -1; side -= 2) {
				float[] row = key(NOVA_CHARGE_ARM, ct).clone();
				row[1] += Mth.sin(now * 3.3f + side * 1.7f) * shake;
				row[3] += Mth.sin(now * 2.9f + side) * shake * 0.6f;
				c.arm(side, row, w);
			}
			float[] body = key(NOVA_CHARGE_BODY, ct).clone();
			body[2] += Mth.sin(now * 3.7f) * 6f * shake;
			c.body(body, 1, w, 1f);
		}

		// ---- Barrage: rapid left-right jabs, the torso turning with the arc
		float bs = started(s, StarfireAbilityManager.BARRAGE, StarfireConfig.BARRAGE_COOLDOWN);
		float ba = now - bs;
		if (ba >= 0f && ba < StarfireConfig.BARRAGE_TICKS + 10) {
			float w = env(ba, 2f, StarfireConfig.BARRAGE_TICKS + 2, StarfireConfig.BARRAGE_TICKS + 10);
			float first = BARRAGE_FIRE[0], last = BARRAGE_FIRE[BARRAGE_FIRE.length - 1];
			float sweep = Mth.clamp((ba - first) / Math.max(1f, last - first), 0f, 1f);
			float arcDeg = Mth.lerp(sweep, StarfireMoves.barrageYaw(0), StarfireMoves.barrageYaw(BARRAGE_FIRE.length - 1));
			float arc = arcDeg * Mth.DEG_TO_RAD;
			for (int side = 1; side >= -1; side -= 2) {
				float since = 99f;
				for (int i = BARRAGE_FIRE.length - 1; i >= 0; i--) {
					int bside = i % 2 == 0 ? -1 : 1;
					if (bside == side && BARRAGE_FIRE[i] <= ba) {
						since = ba - BARRAGE_FIRE[i];
						break;
					}
				}
				c.arm(side, key(JAB_ARM, since > 5f ? 0f : since), w, arc);
			}
			float jit = Mth.sin(ba * 2.2f) * 1.2f;
			c.body(new float[] { 0, arc * 0.55f, 7f + jit, 0.25f, -0.2f, 0f, -0.3f, 0f, 0.05f }, 1, w, 0.7f);
		}

		// ---- Starbolt throws: per hand, the previous throw under the newest, so a quick re-throw blends out of its follow-through
		if (tr != null) {
			// the torso twist and lean follow the newest throw, blended out of the one before
			float pa = now - tr.prevThrow, la = now - tr.lastThrow;
			float pw = env(pa, THROW_IN, THROW_HOLD, THROW_OUT), lw = env(la, THROW_IN, THROW_HOLD, THROW_OUT);
			c.body(key(THROW_BODY, pa), tr.prevSide, pw, 0.6f);
			c.body(key(THROW_BODY, la), tr.lastSide, lw, 0.6f);
			// the arm that isn't throwing swings back for balance (unless it is mid-throw itself)
			for (int i = 0; i < 2; i++) {
				int side = i == 0 ? 1 : -1;
				float own = env(now - tr.throwAt[i], THROW_IN, THROW_HOLD, THROW_OUT);
				float otherAge = now - tr.throwAt[1 - i];
				float ow = env(otherAge, THROW_IN, THROW_HOLD, THROW_OUT) * 0.8f * (1f - own);
				c.arm(side, key(THROW_OTHER, otherAge), ow);
			}
			for (int i = 0; i < 2; i++) {
				int side = i == 0 ? 1 : -1;
				float a0 = now - tr.prevAt[i], a1 = now - tr.throwAt[i];
				c.arm(side, key(THROW_ARM, a0), env(a0, THROW_IN, THROW_HOLD, THROW_OUT));
				c.arm(side, key(THROW_ARM, a1), env(a1, THROW_IN, THROW_HOLD, THROW_OUT));
			}
		}

		// ---- Star Burst release: palms drive out, then the recoil
		float ra = now - started(s, StarfireAbilityManager.STAR_BURST, StarfireConfig.BURST_COOLDOWN);
		if (ra >= 0f && ra < 16f) {
			float w = env(ra, 0.6f, 5.5f, 16f);
			c.both(key(BURST_RELEASE_ARM, ra), w);
			c.body(key(BURST_RELEASE_BODY, ra), 1, w, 0.8f);
		}

		// ---- Supernova blast: flung out into a star
		float na = s.novaAt > 0 ? now - s.novaAt : -1f;
		if (na >= 0f && na < 26f) {
			float w = env(na, 0.5f, 12f, 26f);
			c.both(key(NOVA_BLAST_ARM, na), w);
			c.body(key(NOVA_BLAST_BODY, na), 1, w, 1f);
		}

		// ---- Star Dash: laid along the line, a fist leading; a brake flourish after
		float da = now - started(s, StarfireAbilityManager.STAR_DASH, StarfireConfig.DASH_COOLDOWN);
		if (da >= 0f && da < 13f) {
			float w = env(da, 1.2f, 6f, 13f);
			c.arm(c.lead, key(DASH_LEAD, da), w);
			c.arm(-c.lead, key(DASH_OTHER, da), w);
			c.body(key(DASH_BODY, da), c.lead, w, 1f);
			float target = Mth.clamp(90f + p.getXRot(), 20f, 160f);
			if (p.onGround()) {
				target = Math.min(target, 50f);
			}
			c.override(target, env(da, 1.5f, 5.5f, 11f));
		}

		// ---- Comet Dive: the body along the path (straight up on the launch, one fist high; both fists ahead in the dive)
		if (tr != null) {
			float ia = now - tr.impactAt;
			float impact = env(ia, 0.8f, 7f, 15f);
			if (tr.diving) {
				float w = env(now - tr.diveStart, 1.5f, 1e9f, 2e9f) * (1f - smooth(ia / 1.0f) * (ia >= 0f && ia < 15f ? 1f : 0f));
				if (w > 0.001f) {
					float stage = Mth.lerp(partial, tr.stageWPrev, tr.stageW);
					float[] up = { 0, -3.05f, -0.05f, -0.05f, 0, 0 };
					float[] fwd = { 0, -2.95f, -0.12f, 0f, 0, 0 };
					float[] side = { 0, 0.15f, 0f, 0.12f, 0, 0 };
					c.arm(c.lead, lerpRow(stage, fwd, up), w);
					c.arm(-c.lead, lerpRow(stage, fwd, side), w);
					c.body(new float[] { 0, 0, 0, 0.05f, 0.15f, 0, 0, 0, 0 }, c.lead, w, 1f);
					double vy = Mth.lerp(partial, tr.vyPrev, tr.vy);
					float path = 90f + (float) Math.toDegrees(Math.atan2(-vy, Math.max(tr.hs, 1e-3)));
					c.override(Mth.clamp(path, 0f, 175f), w);
				}
			}
			if (impact > 0f) {
				c.both(key(IMPACT_ARM, ia), impact);
				c.body(key(IMPACT_BODY, ia), 1, impact, 1f);
			}
		}

		// ---- Tamaranean Charge: coil, then a shoulder-first tackle
		float ca = now - started(s, StarfireAbilityManager.CHARGE, StarfireConfig.CHARGE_COOLDOWN);
		if (ca >= 0f && ca < 15f) {
			float w = env(ca, 0.8f, 6.5f, 15f);
			c.arm(c.lead, key(CHARGE_LEAD, ca), w);
			c.arm(-c.lead, key(CHARGE_OTHER, ca), w);
			c.body(key(CHARGE_BODY, ca), c.lead, w, 1f);
		}

		// ---- Ground Pound: fists clasped overhead through the drop, then the crouching slam
		if (tr != null) {
			if (tr.pounding) {
				float w = env(now - tr.poundStart, 2f, 1e9f, 2e9f);
				c.both(SLAM_ARM[0], w);
				c.body(SLAM_BODY[0], 1, w, 1f);
			}
			float sa = now - tr.slamAt + (tr.slamFromAir ? SLAM_WINDUP : 0f);
			float w = env(sa, 0.8f, 8f, 18f);
			if (tr.slamFromAir && sa >= 0f && sa < 18f) {
				w = env(sa, 0f, 8f, 18f);
			}
			if (w > 0f) {
				c.both(key(SLAM_ARM, sa), w);
				c.body(key(SLAM_BODY, sa), 1, w, 1f);
			}
		}

		// ---- Grab: the overhead hurl
		float ha = now - started(s, StarfireAbilityManager.GRAB, StarfireConfig.GRAB_COOLDOWN);
		if (ha >= 0f && ha < 12f && (tr == null || !tr.carrying)) {
			float w = env(ha, 0.3f, 4f, 12f);
			c.both(key(HURL_ARM, ha), w);
			c.body(key(HURL_BODY, ha), 1, w, 0.8f);
		}

		// ---- Language Kiss: lean in, hands up, a heel kick
		float ka = now - started(s, StarfireAbilityManager.KISS, StarfireConfig.KISS_COOLDOWN);
		if (ka >= 0f && ka < 24f) {
			float w = env(ka, 3f, 14f, 24f);
			c.both(key(KISS_ARM, ka), w);
			c.body(key(KISS_BODY, ka), 1, w, 1f);
			c.headRoll = Mth.lerp(w, c.headRoll, 0.22f);
		}

		// ---- Glorious Radiance: arms opened wide to the light (on), drawn in (off)
		float la2 = now - started(s, StarfireAbilityManager.RADIANCE, StarfireConfig.RADIANCE_COOLDOWN);
		if (la2 >= 0f && la2 < 20f) {
			boolean on = s.radiance;
			float w = on ? env(la2, 2.5f, 9f, 20f) : env(la2, 2.5f, 9f, 18f);
			c.both(key(on ? RADIANCE_ON_ARM : RADIANCE_OFF_ARM, la2), w);
			c.body(key(on ? RADIANCE_ON_BODY : RADIANCE_OFF_BODY, la2), 1, w, 0.8f);
		}
	}

	private static float[] lerpRow(float t, float[] a, float[] b) {
		float[] o = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			o[i] = Mth.lerp(t, a[i], b[i]);
		}
		return o;
	}

	// ================================================================ the model (every viewer)

	/** From {@code HumanoidModelMixin}, after the flight pose. */
	public static void apply(Player player, HumanoidModel<?> m) {
		StarfireState s = Starfire.peek(player);
		if (s == null || !s.hasPower || player.level() == null || player.isPassenger()) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		Ctx c = new Ctx();
		c.m = m;
		c.aimX = m.head.xRot - Mth.HALF_PI;
		c.aimY = m.head.yRot;
		evaluate(player, s, c, partial);

		if (Math.abs(c.twist) > 1e-4f) {
			float t = c.twist;
			m.body.yRot += t;
			float sin = Mth.sin(t) * 5f, cos = Mth.cos(t) * 5f;
			m.rightArm.z += sin;
			m.rightArm.x += 5f - cos;
			m.leftArm.z -= sin;
			m.leftArm.x -= 5f - cos;
			// the part of each arm no move posed turns with the torso
			m.rightArm.yRot += t * (1f - c.armW[0]);
			m.leftArm.yRot += t * (1f - c.armW[1]);
		}
		if (Math.abs(c.headX) > 1e-4f || Math.abs(c.headFwd) > 1e-4f || Math.abs(c.headRoll) > 1e-4f) {
			m.head.xRot += c.headX;
			m.head.z += c.headFwd;
			m.head.zRot += c.headRoll;
			m.hat.copyFrom(m.head);
		}
		if (Math.abs(c.spread) > 1e-4f) {
			m.rightLeg.zRot += c.spread;
			m.leftLeg.zRot -= c.spread;
		}
		if (c.crouch > 0.01f) {
			QuirkKeyframes.crouch(m, c.crouch);
		}
	}

	private static final Map<Integer, float[]> LEAN_CACHE = new HashMap<>();

	/**
	 * From {@code FlightPoseHelper.lean}: her moves' share of the whole-body lean (degrees forward). Laid along a dash / dive it
	 * overrides the flight lean; the rest (a throw's lean-in, the beams' thrust, the recoil, the tackle...) adds on top. The head stays
	 * on the look ({@code HumanoidModelMixin#levelHead}).
	 */
	public static float adjustLean(Player player, float base, float partial) {
		StarfireState s = Starfire.peek(player);
		if (s == null || !s.hasPower || player.level() == null || player.isPassenger()) {
			return base;
		}
		float now = player.level().getGameTime() + partial;
		float[] hit = LEAN_CACHE.get(player.getId());
		if (hit != null && hit[0] == now && hit[1] == base) {
			return hit[2];
		}
		Ctx c = new Ctx();
		evaluate(player, s, c, partial);
		float out = Mth.lerp(c.ovW, base, c.ovTarget) + c.lean;
		LEAN_CACHE.put(player.getId(), new float[] { now, base, out });
		return out;
	}

	// ================================================================ first person (the local player's bare arms)

	// rows: {tick, pitch, yaw, roll, x, y, z} (QuirkFirstPersonPose: pitch - = up into view, yaw + = toward the centre,
	// x + = toward the centre, y + = up, z - = into the screen)
	private static final float[][] FP_THROW = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 0.55f, 22f, -18f, 0f, -0.1f, 0.1f, 0.06f },
			{ 1.25f, -20f, 20f, 8f, 0.15f, 0.16f, -0.3f },
			{ 3.2f, -8f, 34f, 12f, 0.3f, 0.05f, -0.25f },
			{ 9f, 0f, 10f, 0f, 0.05f, 0f, 0f } };
	private static final float[][] FP_JAB = {
			{ 0f, 10f, -5f, 0f, 0f, 0f, 0.1f },
			{ 0.8f, -10f, 20f, 5f, 0.15f, 0.08f, -0.42f },
			{ 2.2f, -8f, 20f, 5f, 0.12f, 0.06f, -0.38f },
			{ 5f, 10f, -5f, 0f, 0f, 0f, 0.1f } };
	private static final float[][] FP_BURST_CHARGE = {
			{ 0f, 10f, -10f, 0f, -0.2f, -0.1f, 0.1f },
			{ 6.5f, -18f, 38f, 0f, 0.36f, 0.12f, -0.08f },
			{ 40f, -14f, 40f, 0f, 0.38f, 0.1f, 0.0f } };
	private static final float[][] FP_BURST_RELEASE = {
			{ 0f, -14f, 40f, 0f, 0.38f, 0.1f, 0.0f },
			{ 1.2f, -8f, 28f, 0f, 0.28f, 0.1f, -0.5f },
			{ 2.6f, -8f, 28f, 0f, 0.28f, 0.1f, -0.48f },
			{ 4.6f, -32f, 24f, 0f, 0.25f, 0.22f, -0.3f },
			{ 14f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] FP_HURL = {
			{ 0f, -10f, 30f, 0f, 0.28f, 0.08f, -0.3f },
			{ 0.8f, -55f, 20f, 0f, 0.2f, 0.35f, -0.1f },
			{ 1.8f, 15f, 25f, 0f, 0.25f, -0.1f, -0.5f },
			{ 4f, 20f, 20f, 0f, 0.2f, -0.2f, -0.4f },
			{ 12f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] FP_SLAM = {
			{ 0f, -55f, 25f, 0f, 0.25f, 0.3f, -0.15f },
			{ 1.2f, -62f, 25f, 0f, 0.25f, 0.34f, -0.12f },
			{ 2.2f, 35f, 25f, 0f, 0.25f, -0.35f, -0.35f },
			{ 8f, 35f, 25f, 0f, 0.25f, -0.35f, -0.35f },
			{ 18f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] FP_NOVA_CHARGE = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 5f, -25f, 55f, 20f, 0.5f, 0.15f, 0f },
			{ 32f, -28f, 58f, 22f, 0.52f, 0.17f, 0.02f },
			{ 40f, -20f, 40f, 10f, 0.4f, 0.15f, 0f } };
	private static final float[][] FP_NOVA_BLAST = {
			{ 0f, -20f, 40f, 10f, 0.4f, 0.15f, 0f },
			{ 1.5f, -25f, -45f, -20f, -0.45f, 0.35f, 0.1f },
			{ 12f, -22f, -40f, -18f, -0.42f, 0.3f, 0.1f },
			{ 26f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[] FP_DASH_LEAD = { 0f, -25f, 15f, 0f, 0.1f, 0.15f, -0.35f };
	private static final float[] FP_DIVE = { 0f, -25f, 20f, 0f, 0.15f, 0.12f, -0.35f };
	private static final float[] FP_CHARGE_LEAD = { 0f, -5f, 30f, 0f, 0.3f, 0f, -0.25f };
	private static final float[] FP_CHARGE_OTHER = { 0f, -15f, 15f, 0f, 0.15f, 0.05f, -0.1f };
	private static final float[] FP_CARRY = { 0f, -10f, 30f, 0f, 0.28f, 0.08f, -0.3f };
	private static final float[] FP_KISS = { 0f, -20f, 35f, 15f, 0.32f, 0.2f, -0.3f };
	private static final float[] FP_RADIANCE = { 0f, -25f, -25f, -20f, -0.2f, 0.25f, 0f };
	private static final float[] FP_BEAMS = { 0f, 30f, 0f, 0f, 0f, -0.5f, 0.2f };
	/** The off arm out of view (vanilla never draws an empty off hand, so it slides in from below). */
	private static final float[] FP_HIDDEN = { 0f, 35f, 0f, 0f, 0f, -0.9f, 0.25f };
	private static final float[] FP_REST = { 0f, 0f, 0f, 0f, 0f, 0f, 0f };

	private static final class Fp {
		final float[] main = FP_REST.clone();
		final float[] off = FP_HIDDEN.clone();

		void lerp(float[] dst, float[] row, float w) {
			if (w <= 0.001f) {
				return;
			}
			for (int i = 1; i < 7; i++) {
				dst[i] = Mth.lerp(w, dst[i], row[i]);
			}
		}

		void both(float[] row, float w) {
			lerp(main, row, w);
			lerp(off, row, w);
		}
	}

	/** The local player's first-person arm sample (both hands empty), or null for vanilla. */
	public static QuirkFirstPersonPose.Sample firstPerson(Player p, float partial) {
		StarfireState s = Starfire.peek(p);
		if (s == null || !s.hasPower || !p.getMainHandItem().isEmpty()) {
			return null;
		}
		Track tr = TRACKS.get(p.getUUID());
		float now = p.level().getGameTime() + partial;
		int mainSide = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		Fp f = new Fp();

		if (tr != null && tr.carrying) {
			f.both(FP_CARRY, env(now - tr.carryStart, 2f, 1e9f, 2e9f));
		}
		float beamW = tr == null ? 0f : Mth.lerp(partial, tr.beamWPrev, tr.beamW);
		f.lerp(f.main, FP_BEAMS, beamW);
		if (s.chargeMove == StarfireState.CHARGE_BURST && s.chargeStart > 0) {
			float ct = now - s.chargeStart;
			float[] row = key(FP_BURST_CHARGE, ct).clone();
			float shake = 0.6f + 2.2f * Mth.clamp(ct / StarfireConfig.BURST_CHARGE_TICKS, 0f, 1f);
			row[1] += Mth.sin(now * 2.9f) * shake;
			f.both(row, env(ct, 2f, 1e9f, 2e9f));
		}
		if (s.chargeMove == StarfireState.CHARGE_NOVA && s.chargeStart > 0) {
			float ct = now - s.chargeStart;
			float[] row = key(FP_NOVA_CHARGE, ct).clone();
			float shake = 0.8f + 4f * Mth.clamp(ct / StarfireConfig.NOVA_CHARGE_TICKS, 0f, 1f);
			row[1] += Mth.sin(now * 3.3f) * shake;
			row[2] += Mth.sin(now * 2.7f) * shake * 0.5f;
			f.both(row, env(ct, 3f, 1e9f, 2e9f));
		}
		// barrage
		float ba = now - started(s, StarfireAbilityManager.BARRAGE, StarfireConfig.BARRAGE_COOLDOWN);
		if (ba >= 0f && ba < StarfireConfig.BARRAGE_TICKS + 10) {
			float w = env(ba, 2f, StarfireConfig.BARRAGE_TICKS + 2, StarfireConfig.BARRAGE_TICKS + 10);
			float first = BARRAGE_FIRE[0], last = BARRAGE_FIRE[BARRAGE_FIRE.length - 1];
			float sweep = Mth.clamp((ba - first) / Math.max(1f, last - first), 0f, 1f);
			float arcDeg = Mth.lerp(sweep, StarfireMoves.barrageYaw(0), StarfireMoves.barrageYaw(BARRAGE_FIRE.length - 1));
			for (int side = 1; side >= -1; side -= 2) {
				float since = 99f;
				for (int i = BARRAGE_FIRE.length - 1; i >= 0; i--) {
					int bside = i % 2 == 0 ? -1 : 1;
					if (bside == side && BARRAGE_FIRE[i] <= ba) {
						since = ba - BARRAGE_FIRE[i];
						break;
					}
				}
				float[] row = key(FP_JAB, since > 5f ? 0f : since).clone();
				row[2] += -side * arcDeg * 0.6f;
				row[4] += -side * arcDeg / 35f * 0.15f;
				f.lerp(side == mainSide ? f.main : f.off, row, w);
			}
		}
		// throws
		if (tr != null) {
			for (int i = 0; i < 2; i++) {
				int side = i == 0 ? 1 : -1;
				float[] dst = side == mainSide ? f.main : f.off;
				float a0 = now - tr.prevAt[i], a1 = now - tr.throwAt[i];
				f.lerp(dst, key(FP_THROW, a0), env(a0, THROW_IN, THROW_HOLD, THROW_OUT));
				f.lerp(dst, key(FP_THROW, a1), env(a1, THROW_IN, THROW_HOLD, THROW_OUT));
			}
		}
		float ra = now - started(s, StarfireAbilityManager.STAR_BURST, StarfireConfig.BURST_COOLDOWN);
		if (ra >= 0f && ra < 14f) {
			f.both(key(FP_BURST_RELEASE, ra), env(ra, 0.6f, 4.6f, 14f));
		}
		float na = s.novaAt > 0 ? now - s.novaAt : -1f;
		if (na >= 0f && na < 26f) {
			f.both(key(FP_NOVA_BLAST, na), env(na, 0.5f, 12f, 26f));
		}
		float da = now - started(s, StarfireAbilityManager.STAR_DASH, StarfireConfig.DASH_COOLDOWN);
		if (da >= 0f && da < 13f) {
			f.lerp(f.main, FP_DASH_LEAD, env(da, 1.2f, 6f, 13f));
		}
		if (tr != null && tr.diving) {
			float w = env(now - tr.diveStart, 1.5f, 1e9f, 2e9f);
			f.lerp(f.main, FP_DIVE, w);
			f.lerp(f.off, FP_DIVE, w * (1f - tr.stageW));
		}
		float ca = now - started(s, StarfireAbilityManager.CHARGE, StarfireConfig.CHARGE_COOLDOWN);
		if (ca >= 0f && ca < 15f) {
			float w = env(ca, 0.8f, 6.5f, 15f);
			f.lerp(f.main, FP_CHARGE_LEAD, w);
			f.lerp(f.off, FP_CHARGE_OTHER, w);
		}
		if (tr != null) {
			if (tr.pounding) {
				f.both(FP_SLAM[0], env(now - tr.poundStart, 2f, 1e9f, 2e9f));
			}
			float sa = now - tr.slamAt + (tr.slamFromAir ? SLAM_WINDUP : 0f);
			float w = tr.slamFromAir ? env(sa, 0f, 8f, 18f) : env(sa, 0.8f, 8f, 18f);
			if (w > 0f) {
				f.both(key(FP_SLAM, sa), w);
			}
		}
		float ha = now - started(s, StarfireAbilityManager.GRAB, StarfireConfig.GRAB_COOLDOWN);
		if (ha >= 0f && ha < 12f && (tr == null || !tr.carrying)) {
			f.both(key(FP_HURL, ha), env(ha, 0.3f, 4f, 12f));
		}
		float ka = now - started(s, StarfireAbilityManager.KISS, StarfireConfig.KISS_COOLDOWN);
		if (ka >= 0f && ka < 24f) {
			f.both(FP_KISS, env(ka, 3f, 14f, 24f));
		}
		float la = now - started(s, StarfireAbilityManager.RADIANCE, StarfireConfig.RADIANCE_COOLDOWN);
		if (la >= 0f && la < 20f && s.radiance) {
			f.both(FP_RADIANCE, env(la, 2.5f, 9f, 20f));
		}

		boolean offShown = false;
		for (int i = 1; i < 7; i++) {
			offShown |= Math.abs(f.off[i] - FP_HIDDEN[i]) > 1e-3f;
		}
		boolean mainPosed = false;
		for (int i = 1; i < 7; i++) {
			mainPosed |= Math.abs(f.main[i]) > 1e-3f;
		}
		if (!offShown && !mainPosed) {
			return null;
		}
		float[] v = new float[QuirkFirstPersonPose.COLS];
		System.arraycopy(f.main, 1, v, 1, 6);
		System.arraycopy(f.off, 1, v, 7, 6);
		return new QuirkFirstPersonPose.Sample(v, 1f, offShown && p.getOffhandItem().isEmpty());
	}

	// ================================================================ effects timing

	/**
	 * 0..1: how bright the release flash at a hand is ({@code side} +1 right, -1 left) -- a starbolt or barrage bolt leaving it, peaking
	 * on the throw's release frame. For {@code StarfireFx}.
	 */
	public static float handFlash(Player p, int side, float partial) {
		StarfireState s = Starfire.peek(p);
		Track tr = TRACKS.get(p.getUUID());
		if (s == null || tr == null) {
			return 0f;
		}
		float now = p.level().getGameTime() + partial;
		float a = now - tr.throwAt[side > 0 ? 0 : 1];
		float best = flashCurve(a, 1.25f);
		float ba = now - started(s, StarfireAbilityManager.BARRAGE, StarfireConfig.BARRAGE_COOLDOWN);
		if (ba >= 0f && ba < StarfireConfig.BARRAGE_TICKS + 4) {
			for (int i = BARRAGE_FIRE.length - 1; i >= 0; i--) {
				int bside = i % 2 == 0 ? -1 : 1;
				if (bside == side && BARRAGE_FIRE[i] <= ba) {
					best = Math.max(best, flashCurve(ba - BARRAGE_FIRE[i], 0.8f) * 0.8f);
					break;
				}
			}
		}
		return best;
	}

	/** 0..1: the Star Burst release flash between her palms. */
	public static float burstFlash(Player p, float partial) {
		StarfireState s = Starfire.peek(p);
		if (s == null) {
			return 0f;
		}
		float ra = p.level().getGameTime() + partial - started(s, StarfireAbilityManager.STAR_BURST, StarfireConfig.BURST_COOLDOWN);
		return flashCurve(ra, 1.2f);
	}

	private static float flashCurve(float a, float peak) {
		if (a < 0f || a > peak + 4f) {
			return 0f;
		}
		return a < peak ? smooth(a / peak) : 1f - smooth((a - peak) / 4f);
	}
}
