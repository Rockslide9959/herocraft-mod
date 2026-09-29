package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.SymbioteAnim;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteTransform;
import com.projecthero.mod.symbiote.SymbioteVitals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.19: the Symbiote host's move animations and suit-up pose, the same technique as {@code AllMightPose}:
 * keyframed arm / body / leg / head angles applied to the vanilla model after vanilla's own animation, driven
 * by the synced {@link SymbioteVitals#animId} / {@link SymbioteVitals#animStart} (moves) and the synced suit
 * transform clock ({@link SymbioteTransform}). The GeckoLib suit copies the vanilla bones, so it follows too,
 * and every viewer sees the same pose.
 *
 * <p>Frames are {@code {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX}} in
 * radians (arm X negative = swung forward/up), smoothstepped between keys and faded in/out at the ends. Moves
 * flagged as aimed add the head's pitch / yaw to that arm, so the hand the tendril leaves really points where
 * the host is looking.
 */
public final class SymbiotePose {
	private static final float[] REST = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	private record Pose(float[][] frames, boolean aimRight, boolean aimLeft) {
	}

	private static float[] f(float tick, float... v) {
		float[] out = new float[12];
		out[0] = tick;
		System.arraycopy(v, 0, out, 1, Math.min(11, v.length));
		return out;
	}

	private static float[] rest(float tick) {
		float[] out = REST.clone();
		out[0] = tick;
		return out;
	}

	private static final Pose STRIKE = new Pose(new float[][] {
			rest(0),
			f(2, -1.5f, -0.1f, 0, 0.35f, 0, 0, 0.05f, -0.35f, -0.2f, 0.2f, 0),
			f(7, -1.45f, -0.1f, 0, 0.3f, 0, 0, 0.05f, -0.3f, -0.2f, 0.2f, 0),
			rest(12) }, true, false);
	private static final Pose SWEEP = new Pose(new float[][] {
			rest(0),
			f(3, -1.35f, 0.95f, 0, 0.3f, 0, 0, 0.05f, 0.45f, -0.15f, 0.15f, 0),
			f(8, -1.35f, -0.95f, 0, 0.3f, 0, 0, 0.05f, -0.5f, -0.15f, 0.15f, 0),
			rest(14) }, false, false);
	private static final Pose SPIKE_SHOT = new Pose(new float[][] {
			rest(0),
			f(2, -1.6f, 0, 0, 0.25f, 0, 0, 0, -0.25f, 0, 0, 0),
			f(5, -1.55f, 0, 0, 0.25f, 0, 0, 0, -0.2f, 0, 0, 0),
			rest(10) }, true, false);
	private static final Pose SPIKE_FAN = new Pose(new float[][] {
			rest(0),
			f(3, -1.55f, -0.35f, 0, -1.55f, 0.35f, 0, 0.12f, 0, -0.1f, 0.1f, 0.05f),
			f(7, -1.5f, -0.35f, 0, -1.5f, 0.35f, 0, 0.1f, 0, -0.1f, 0.1f, 0.05f),
			rest(12) }, true, true);
	private static final Pose LUNGE = new Pose(new float[][] {
			rest(0),
			f(2, 0.9f, 0, 0.35f, 0.9f, 0, -0.35f, 0.35f, 0, -0.55f, 0.55f, -0.25f),
			f(12, 0.8f, 0, 0.3f, 0.8f, 0, -0.3f, 0.3f, 0, -0.5f, 0.5f, -0.2f),
			rest(16) }, false, false);
	private static final Pose GRAPPLE = new Pose(new float[][] {
			rest(0),
			f(2, -1.65f, 0, 0, 0.2f, 0, 0, 0, -0.2f, 0, 0, 0),
			f(16, -1.6f, 0, 0, 0.2f, 0, 0, 0, -0.2f, 0, 0, 0),
			rest(20) }, true, false);
	private static final Pose BARRAGE = new Pose(barrageFrames(), true, true);
	private static final Pose BLADE_SLASH = new Pose(new float[][] {
			rest(0),
			f(3, -2.8f, 0.4f, 0, 0.3f, 0, 0, -0.1f, 0.35f, -0.1f, 0.1f, 0),
			f(6, -0.55f, -0.75f, 0, 0.3f, 0, 0, 0.15f, -0.45f, -0.2f, 0.2f, 0),
			rest(12) }, false, false);
	private static final Pose ONSLAUGHT_CHARGE = new Pose(chargeFrames(), false, false);
	private static final Pose ONSLAUGHT_RELEASE = new Pose(new float[][] {
			f(0, -0.3f, 0, 1.4f, -0.3f, 0, -1.4f, 0.2f, 0, 0, 0, 0.3f),
			f(3, -2.9f, 0, 0.45f, -2.9f, 0, -0.45f, -0.25f, 0, -0.1f, 0.1f, -0.55f),
			f(10, -2.8f, 0, 0.4f, -2.8f, 0, -0.4f, -0.2f, 0, -0.1f, 0.1f, -0.5f),
			rest(18) }, false, false);
	private static final Pose GRAB = new Pose(new float[][] {
			rest(0),
			f(2, -1.65f, 0, 0, 0.2f, 0, 0, 0, -0.2f, 0, 0, 0),
			f(6, -1.25f, 0, 0, 0.2f, 0, 0, -0.05f, -0.15f, 0, 0, 0),
			f(80, -1.25f, 0, 0, 0.2f, 0, 0, -0.05f, -0.15f, 0, 0, 0) }, true, false);
	private static final Pose THROW = new Pose(new float[][] {
			f(0, -2.7f, 0, 0, 0.3f, 0, 0, -0.15f, 0.3f, 0, 0, 0),
			f(3, -1.0f, 0, 0, 0.3f, 0, 0, 0.2f, -0.4f, -0.2f, 0.2f, 0),
			rest(12) }, false, false);
	private static final Pose BLADE_FORM = new Pose(new float[][] {
			rest(0),
			f(3, -0.9f, 0, 0.85f, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			f(8, -0.85f, 0, 0.8f, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			rest(12) }, false, false);
	private static final Pose SPIKES_FLEX = new Pose(new float[][] {
			rest(0),
			f(4, 0.25f, 0, 0.65f, 0.25f, 0, -0.65f, 0.25f, 0, 0.1f, -0.1f, 0.3f),
			f(9, 0.2f, 0, 0.6f, 0.2f, 0, -0.6f, 0.22f, 0, 0.1f, -0.1f, 0.28f),
			rest(14) }, false, false);
	private static final Pose RESURRECT = new Pose(new float[][] {
			f(0, -0.5f, 0, 1.9f, -0.5f, 0, -1.9f, -0.25f, 0, 0, 0, -0.6f),
			f(16, -0.45f, 0, 1.8f, -0.45f, 0, -1.8f, -0.2f, 0, 0, 0, -0.55f),
			rest(24) }, false, false);

	/** Suit coming on: arms flung out and head thrown back while the Symbiote spreads over the body. */
	private static final float[][] SUIT_UP = {
			rest(0),
			f(8, -0.25f, 0, 1.05f, -0.25f, 0, -1.05f, -0.15f, 0, 0.05f, -0.05f, -0.5f),
			f(20, -0.3f, 0, 1.15f, -0.3f, 0, -1.15f, -0.2f, 0, 0.05f, -0.05f, -0.6f),
			f(32, -0.25f, 0, 1.05f, -0.25f, 0, -1.05f, -0.15f, 0, 0.05f, -0.05f, -0.5f),
			rest(40) };
	/** Suit going off: the host hunches and hugs in as it draws back into them. */
	private static final float[][] SUIT_DOWN = {
			rest(0),
			f(6, -0.6f, 0.5f, 0, -0.6f, -0.5f, 0, 0.3f, 0, 0, 0, 0.3f),
			f(18, -0.55f, 0.45f, 0, -0.55f, -0.45f, 0, 0.28f, 0, 0, 0, 0.28f),
			rest(24) };

	private static float[][] barrageFrames() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		out.add(rest(0));
		boolean right = true;
		for (float t = 1.5f; t <= 25.5f; t += 3.0f) {
			out.add(right ? f(t, -1.55f, -0.1f, 0, -0.35f, 0, 0, 0.05f, -0.3f, -0.1f, 0.1f, 0)
					: f(t, -0.35f, 0, 0, -1.55f, 0.1f, 0, 0.05f, 0.3f, 0.1f, -0.1f, 0));
			right = !right;
		}
		out.add(rest(28));
		return out.toArray(new float[0][]);
	}

	private static float[][] chargeFrames() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		out.add(rest(0));
		for (int t = 10; t <= 60; t += 4) {
			float tremble = ((t / 4) % 2 == 0) ? 0.06f : -0.06f;
			out.add(f(t, -0.3f, 0, 1.4f + tremble, -0.3f, 0, -1.4f - tremble, 0.2f, 0, 0.1f, -0.1f, 0.3f));
		}
		return out.toArray(new float[0][]);
	}

	private SymbiotePose() {
	}

	private static Pose poseFor(int animId) {
		return switch (animId) {
			case SymbioteAnim.TENDRIL_STRIKE -> STRIKE;
			case SymbioteAnim.TENDRIL_SWEEP -> SWEEP;
			case SymbioteAnim.SPIKE_SHOT -> SPIKE_SHOT;
			case SymbioteAnim.SPIKE_FAN -> SPIKE_FAN;
			case SymbioteAnim.LUNGE -> LUNGE;
			case SymbioteAnim.GRAPPLE -> GRAPPLE;
			case SymbioteAnim.BARRAGE -> BARRAGE;
			case SymbioteAnim.BLADE_SLASH -> BLADE_SLASH;
			case SymbioteAnim.ONSLAUGHT_CHARGE -> ONSLAUGHT_CHARGE;
			case SymbioteAnim.ONSLAUGHT_RELEASE -> ONSLAUGHT_RELEASE;
			case SymbioteAnim.GRAB -> GRAB;
			case SymbioteAnim.THROW -> THROW;
			case SymbioteAnim.BLADE_FORM -> BLADE_FORM;
			case SymbioteAnim.SPIKES_FLEX -> SPIKES_FLEX;
			case SymbioteAnim.RESURRECT -> RESURRECT;
			default -> null;
		};
	}

	/** Used by tests / tooling: does a move id have a pose, and how long is it. */
	public static int lengthTicks(int animId) {
		Pose p = poseFor(animId);
		return p == null ? 0 : (int) p.frames()[p.frames().length - 1][0];
	}

	static float[] sample(float[][] f, float tick) {
		if (tick < 0f || tick > f[f.length - 1][0]) {
			return null;
		}
		for (int i = 1; i < f.length; i++) {
			if (tick <= f[i][0]) {
				float[] a = f[i - 1];
				float[] b = f[i];
				float t = (tick - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				t = t * t * (3f - 2f * t);
				float[] out = new float[12];
				for (int k = 1; k < 12; k++) {
					out[k] = Mth.lerp(t, a[k], b[k]);
				}
				return out;
			}
		}
		return null;
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		if (s == null || !s.hasSymbiote) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long gameTime = player.level().getGameTime();

		// the suit coming on / going off wins over everything else
		if (SymbioteTransform.isAnimating(s)) {
			float[][] frames = s.transformDir == SymbioteState.DIR_DOWN ? SUIT_DOWN : SUIT_UP;
			float end = frames[frames.length - 1][0];
			float tick = (gameTime - s.transformStartTick + partial) * end / Math.max(1, s.transformDurationTicks);
			blend(m, frames, tick, false, false);
			return;
		}

		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v != null && v.animId != SymbioteAnim.NONE) {
			Pose p = poseFor(v.animId);
			if (p != null && blend(m, p.frames(), gameTime - v.animStart + partial, p.aimRight(), p.aimLeft())) {
				return;
			}
		}
		// the shield: both forearms crossed in front of the chest while it is up
		if (s.shieldHeld) {
			m.rightArm.xRot = -1.35f;
			m.rightArm.yRot = -0.55f;
			m.rightArm.zRot = 0.0f;
			m.leftArm.xRot = -1.35f;
			m.leftArm.yRot = 0.55f;
			m.leftArm.zRot = 0.0f;
		}
	}

	/** @return true if a frame was applied (the pose is still running) */
	private static boolean blend(HumanoidModel<?> m, float[][] f, float tick, boolean aimRight, boolean aimLeft) {
		float[] p = sample(f, tick);
		if (p == null) {
			return false;
		}
		float end = f[f.length - 1][0];
		float w = Math.max(0f, Math.min(1f, Math.min(tick / 2f, (end - tick) / 3f)));
		// long, held poses (grab, charge) do not need to fade in over their whole first frame span
		if (f[0][0] == 0 && tick >= 2f && end - tick >= 3f) {
			w = 1f;
		}
		float headX = m.head.xRot;
		float headY = m.head.yRot;
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1] + (aimRight ? headX : 0f));
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2] + (aimRight ? headY : 0f));
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4] + (aimLeft ? headX : 0f));
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5] + (aimLeft ? headY : 0f));
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
		m.body.xRot = Mth.lerp(w, m.body.xRot, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, m.rightLeg.xRot * 0.3f + p[9]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, m.leftLeg.xRot * 0.3f + p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
		return true;
	}
}
