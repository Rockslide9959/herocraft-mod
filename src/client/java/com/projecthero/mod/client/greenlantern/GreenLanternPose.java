package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.3: Green Lantern body animations -- the same keyframe technique as {@code MaxSteelPose}: arm / body / leg / head
 * angles laid over vanilla's own animation, driven by synced state ({@link GreenLanternFx}, the Oath and barrier
 * attachments, the suit clock) so every viewer sees the same thing. The ring is on the right hand, so the right arm
 * leads almost everything.
 * <ul>
 *   <li>Ring Bolt: the ring arm snaps out along the crosshair and kicks. Construct Fist: a wind-up and a straight punch
 *   along the aim as the fist flies. War Hammer: both hands raise the hammer overhead and bring it down.</li>
 *   <li>Missile Barrage: the ring arm sweeps up to the sky. Shaping a construct: ring arm out, free hand spread.
 *   Protective Dome: arms flung wide. Ring Scan: arm out, sweeping the horizon. Giant Hand: reach, clench, fling.</li>
 *   <li>Held: Continuous Beam (both hands, the free one gripping the wrist), Emerald Gatling (braced, shaking), the
 *   Directional Shield (ring arm braced forward), reciting the Oath (ring fist raised before the face; punched to the
 *   sky when it completes), taking the ring off (hands together, head bowed).</li>
 *   <li>Suit up / down (v0.15.15): ring fist held forward and a little up, the head turned to watch the ring while the
 *   suit pours out of it (or back into it), then back to normal.</li>
 * </ul>
 * Frames are {@code {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX}} in radians.
 */
public final class GreenLanternPose {
	private static final float HALF_PI = (float) (Math.PI / 2);

	private static float[] f(float tick, float... v) {
		float[] out = new float[12];
		out[0] = tick;
		System.arraycopy(v, 0, out, 1, Math.min(11, v.length));
		return out;
	}

	private static float[] rest(float tick) {
		return f(tick);
	}

	/** Aimed: arm X / Y are offsets on the crosshair. */
	private static final float[][] BOLT = {
			f(0, 0, 0, 0, -0.3f, 0.2f, 0),
			f(2, -0.35f, 0, 0, -0.3f, 0.2f, 0, -0.06f, 0, 0, 0, -0.04f),
			rest(9) };
	private static final float[][] FIST = {
			f(0, 1.1f, 0.5f, 0, -0.5f, 0, -0.2f, 0, 0.45f, 0, 0, 0),
			f(3, 0, 0, 0, 0.3f, 0, -0.1f, 0.18f, -0.4f, -0.4f, 0.35f, 0),
			f(10, 0, 0, 0, 0.2f, 0, -0.1f, 0.12f, -0.3f, -0.3f, 0.25f, 0),
			rest(16) };
	private static final float[][] HAMMER = {
			rest(0),
			f(3, -2.95f, -0.25f, -0.2f, -2.95f, 0.25f, 0.2f, -0.18f, 0, 0.1f, -0.1f, -0.3f),
			f(7, -0.95f, -0.3f, -0.1f, -0.95f, 0.3f, 0.1f, 0.4f, 0, -0.45f, 0.4f, 0.25f),
			f(14, -0.9f, -0.3f, -0.1f, -0.9f, 0.3f, 0.1f, 0.38f, 0, -0.45f, 0.4f, 0.25f),
			rest(20) };
	private static final float[][] MISSILES = {
			rest(0),
			f(3, -2.5f, -0.2f, 0.2f, -0.6f, 0, -0.7f, -0.12f, 0, 0, 0, -0.35f),
			f(12, -2.6f, -0.2f, 0.25f, -0.6f, 0, -0.7f, -0.12f, 0, 0, 0, -0.3f),
			rest(18) };
	private static final float[][] CONSTRUCT = {
			rest(0),
			f(3, -1.45f, -0.15f, 0, -0.55f, 0.35f, -0.45f, 0.05f, 0.1f, 0, 0, 0),
			f(12, -1.4f, -0.1f, 0, -0.5f, 0.3f, -0.45f, 0.05f, 0.1f, 0, 0, 0),
			rest(18) };
	private static final float[][] DOME = {
			rest(0),
			f(3, 0.15f, 0, 0.35f, 0.15f, 0, -0.35f, 0.12f, 0, 0, 0, 0.2f),
			f(10, -0.35f, 0, 1.45f, -0.35f, 0, -1.45f, -0.12f, 0, 0.05f, -0.05f, -0.25f),
			f(22, -0.3f, 0, 1.35f, -0.3f, 0, -1.35f, -0.1f, 0, 0.05f, -0.05f, -0.2f),
			rest(28) };
	/** Aimed. */
	private static final float[][] THROW = {
			f(0, -1.1f, 0, 0, -0.4f, 0, -0.2f, -0.15f, 0.35f, 0, 0, 0),
			f(3, 0.35f, 0, 0, 0.2f, 0, -0.1f, 0.2f, -0.35f, -0.35f, 0.3f, 0),
			rest(10) };
	/** Aimed. */
	private static final float[][] GRAB = {
			f(0, 0, 0, 0, -0.4f, 0.3f, -0.3f),
			f(4, -0.25f, 0, 0, -0.4f, 0.3f, -0.3f, 0.05f, 0, 0, 0, 0),
			rest(12) };
	private static final float[][] OATH = {
			f(0, -2.0f, -0.45f, 0),
			f(4, -3.05f, 0, 0.12f, 0.2f, 0, -0.25f, -0.1f, 0, 0, 0, -0.45f),
			f(26, -3.05f, 0, 0.12f, 0.2f, 0, -0.25f, -0.1f, 0, 0, 0, -0.45f),
			rest(34) };
	private static final float[][] SCAN = {
			rest(0),
			f(3, -1.5f, 0, 0, 0, 0, 0, 0, -0.55f, 0, 0, 0),
			f(14, -1.5f, 0, 0, 0, 0, 0, 0, 0.55f, 0, 0, 0),
			rest(20) };
	/**
	 * Suit up / suit down, scaled to the suit clock (v0.15.15): the ring fist raised out in front, held there while the suit
	 * pours out of the ring (or back into it), the free hand open at the side; lowered as it settles.
	 */
	private static final float[][] SUIT = {
			rest(0),
			f(4, -1.9f, -0.3f, 0, 0.1f, 0, -0.2f, 0, 0, 0, 0, 0),
			f(24, -1.85f, -0.28f, 0, 0.14f, 0, -0.3f, 0, 0, 0.04f, -0.04f, 0),
			f(28, -0.6f, -0.1f, 0.2f, 0.05f, 0, -0.2f, 0, 0, 0, 0, 0),
			rest(32) };

	private GreenLanternPose() {
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		float age = player.tickCount + partial;
		GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);

		if (fx.has(GreenLanternFx.CH_RING_REMOVE)) {
			float held = now - fx.ringRemoveStart() + partial;
			float in = Math.min(1f, held / 6f);
			float tremble = Mth.sin(age * 2.3f) * 0.025f * Math.min(1f, held / GreenLanternConfig.RING_REMOVE_HOLD_TICKS * 2f);
			set(m, in, -1.15f + tremble, -0.4f, 0f, -1.1f - tremble, 0.6f, 0f);
			m.head.xRot = Mth.lerp(in, m.head.xRot, 0.45f);
			m.hat.copyFrom(m.head);
			return;
		}
		if (fx.has(GreenLanternFx.CH_GATLING)) {
			float tremble = Mth.sin(age * 3.1f) * 0.03f;
			aimRight(m, 1f, tremble);
			m.leftArm.xRot = -1.0f + tremble;
			m.leftArm.yRot = 0.8f;
			m.leftArm.zRot = 0f;
			m.body.yRot = -0.15f;
			m.rightLeg.xRot = m.rightLeg.xRot * 0.3f - 0.3f;
			m.leftLeg.xRot = m.leftLeg.xRot * 0.3f + 0.3f;
			return;
		}
		if (fx.has(GreenLanternFx.CH_BEAM)) {
			aimRight(m, 1f, Mth.sin(age * 1.7f) * 0.012f);
			m.leftArm.xRot = -HALF_PI + m.head.xRot + 0.12f;
			m.leftArm.yRot = m.head.yRot + 0.62f;
			m.leftArm.zRot = 0f;
			m.rightLeg.xRot = m.rightLeg.xRot * 0.3f - 0.25f;
			m.leftLeg.xRot = m.leftLeg.xRot * 0.3f + 0.25f;
			return;
		}
		if (fx.has(GreenLanternFx.CH_HAND)) {
			float since = now - fx.animStart() + partial;
			if (fx.anim() != GreenLanternFx.ANIM_GRAB || since > 4f) {
				aimRight(m, 1f, Mth.sin(age * 2.6f) * 0.025f);
				m.rightArm.xRot -= 0.25f;
				m.leftArm.xRot = -0.4f;
				m.leftArm.zRot = -0.3f;
				return;
			}
		}
		long reciting = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L);
		if (reciting > 0L) {
			float held = now - reciting + partial;
			float in = Math.min(1f, held / 8f);
			float rise = Math.min(1f, held / GreenLanternConfig.OATH_MODE_RECITE_TICKS);
			set(m, in, -1.85f - 0.35f * rise, -0.5f, 0f, 0.05f, 0f, -0.08f);
			m.head.xRot = Mth.lerp(in, m.head.xRot, -0.1f - 0.2f * rise);
			m.hat.copyFrom(m.head);
			return;
		}
		if (fx.anim() != GreenLanternFx.ANIM_NONE) {
			float tick = now - fx.animStart() + partial;
			float[][] frames = switch (fx.anim()) {
				case GreenLanternFx.ANIM_BOLT -> BOLT;
				case GreenLanternFx.ANIM_FIST -> FIST;
				case GreenLanternFx.ANIM_HAMMER -> HAMMER;
				case GreenLanternFx.ANIM_MISSILES -> MISSILES;
				case GreenLanternFx.ANIM_CONSTRUCT -> CONSTRUCT;
				case GreenLanternFx.ANIM_DOME -> DOME;
				case GreenLanternFx.ANIM_THROW -> THROW;
				case GreenLanternFx.ANIM_GRAB -> GRAB;
				case GreenLanternFx.ANIM_OATH -> OATH;
				case GreenLanternFx.ANIM_SCAN -> SCAN;
				default -> null;
			};
			boolean aimed = frames == BOLT || frames == FIST || frames == THROW || frames == GRAB;
			if (frames != null && tick <= frames[frames.length - 1][0]) {
				blend(m, frames, tick, aimed);
				return;
			}
		}
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f
				&& !player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false)) {
			// Directional Shield: the ring arm braced out in front, holding the shield up
			m.rightArm.xRot = -1.35f + m.head.xRot * 0.5f;
			m.rightArm.yRot = -0.25f + m.head.yRot;
			m.rightArm.zRot = 0f;
			m.leftArm.xRot = Mth.lerp(0.5f, m.leftArm.xRot, -0.45f);
			return;
		}
		if (s.suitAnimDir != GreenLanternState.SUIT_IDLE) {
			float end = SUIT[SUIT.length - 1][0];
			float tick = (now - s.suitAnimStartTick + partial) * end / Math.max(1, GreenLanternConfig.SUIT_UP_TICKS);
			blend(m, SUIT, tick, false);
			// v0.15.15: the head turns to watch the ring (a touch to the right and up) while the suit pours out of it
			float look = Mth.clamp(Math.min(tick / 4f, (29f - tick) / 5f), 0f, 1f);
			m.head.yRot = Mth.lerp(look, m.head.yRot, 0.2f);
			m.head.xRot = Mth.lerp(look, m.head.xRot, -0.05f);
			m.hat.copyFrom(m.head);
		}
	}

	private static void set(HumanoidModel<?> m, float w, float rx, float ry, float rz, float lx, float ly, float lz) {
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, rx);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, ry);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, rz);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, lx);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, ly);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, lz);
	}

	/** Right arm held straight out along the crosshair. */
	private static void aimRight(HumanoidModel<?> m, float w, float tremble) {
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -HALF_PI + m.head.xRot + tremble);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.1f + m.head.yRot);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0f);
	}

	private static float[] sample(float[][] f, float tick) {
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

	/** Applies a keyframe table; {@code aimed}: the right arm's X / Y are offsets on the crosshair aim. */
	private static void blend(HumanoidModel<?> m, float[][] f, float tick, boolean aimed) {
		float[] p = sample(f, tick);
		if (p == null) {
			return;
		}
		float end = f[f.length - 1][0];
		float w = Math.max(0f, Math.min(1f, (end - tick) / 4f));
		if (!aimed) {
			w = Math.min(w, Math.max(0f, Math.min(1f, tick / 2f)));
		}
		float headX = m.head.xRot;
		float headY = m.head.yRot;
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1] + (aimed ? -HALF_PI + headX : 0f));
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2] + (aimed ? -0.1f + headY : 0f));
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4]);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5]);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
		m.body.xRot = Mth.lerp(w, m.body.xRot, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, m.rightLeg.xRot * 0.3f + p[9]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, m.leftLeg.xRot * 0.3f + p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
	}
}
