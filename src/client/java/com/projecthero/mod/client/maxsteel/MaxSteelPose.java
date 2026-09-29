package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.MaxSteelConfig;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.2: Max Steel's body animations, the same keyframe technique as {@code SymbiotePose} / {@code AllMightPose}:
 * arm / body / leg / head angles applied to the vanilla model after vanilla's own animation (the GeckoLib suit copies
 * the vanilla bones, so it follows), driven by the synced {@link MaxSteelState} transform clock and {@link MaxSteelFx}
 * -- so every viewer sees the same thing.
 *
 * <ul>
 *   <li><b>Armour up</b>: right fist to the T.U.R.B.O. core, then arms flung wide and head back as the nanites
 *       spread; <b>power down</b> runs it in reverse, hand back to the chest as the suit retracts into it.</li>
 *   <li><b>Mode swap</b>: a quick brace-and-flex as the new form rematerialises.</li>
 *   <li><b>Turbo Blast</b>: right arm held out along the crosshair while charging, a kick on release.</li>
 *   <li><b>Turbo Cannon</b>: side-on brace with the cannon arm aimed and the left hand steadying it (trembling at full
 *       charge), a heavy recoil on the shot.</li>
 * </ul>
 * Frames are {@code {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX}} in radians
 * (arm X negative = forward/up, right-arm Z positive = outward), smoothstepped between keys.
 */
public final class MaxSteelPose {
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

	/** Fist to the core, then arms wide and head thrown back while the suit spreads (scaled to the transform time). */
	private static final float[][] SUIT_UP = {
			rest(0),
			f(6, -1.25f, -0.75f, 0, 0.1f, 0, -0.1f, 0.12f, 0, 0, 0, 0.3f),
			f(15, -1.3f, -0.8f, 0, 0.1f, 0, -0.1f, 0.14f, 0, 0, 0, 0.32f),
			f(24, -0.2f, 0, 0.95f, -0.2f, 0, -0.95f, -0.12f, 0, 0.05f, -0.05f, -0.35f),
			f(34, -0.18f, 0, 0.9f, -0.18f, 0, -0.9f, -0.1f, 0, 0.05f, -0.05f, -0.3f),
			rest(40) };
	/** Arms out as the plates lift away, then the hand back to the core as it all draws in. */
	private static final float[][] SUIT_DOWN = {
			rest(0),
			f(6, -0.15f, 0, 0.55f, -0.15f, 0, -0.55f, -0.08f, 0, 0, 0, -0.2f),
			f(18, -1.25f, -0.75f, 0, 0.1f, 0, -0.1f, 0.12f, 0, 0, 0, 0.3f),
			f(26, -1.2f, -0.7f, 0, 0.1f, 0, -0.1f, 0.1f, 0, 0, 0, 0.25f),
			rest(32) };
	/** Reconfiguration: fists pulled in low, then a flex outward. */
	private static final float[][] SWAP = {
			rest(0),
			f(3, 0.35f, 0, 0.3f, 0.35f, 0, -0.3f, 0.18f, 0, -0.1f, 0.1f, 0.2f),
			f(8, -0.35f, 0, 0.75f, -0.35f, 0, -0.75f, -0.1f, 0, 0.05f, -0.05f, -0.15f),
			rest(14) };
	/** Blast release: the aimed arm kicks up. Arm X is added to the aim. */
	private static final float[][] BLAST = {
			f(0, 0, 0, 0, -0.9f, 0.5f, 0, 0, 0, 0, 0, 0),
			f(2, -0.45f, 0, 0, -0.9f, 0.5f, 0, -0.08f, 0, 0, 0, -0.05f),
			rest(8) };
	/** Cannon discharge: a heavy recoil through the whole body. Arm X is added to the aim. */
	private static final float[][] CANNON = {
			f(0, 0, 0, 0, -1.35f, 0.75f, 0, 0, -0.15f, -0.35f, 0.35f, 0),
			f(2, -0.7f, 0, 0, -1.1f, 0.6f, 0, -0.28f, -0.15f, -0.4f, 0.4f, -0.2f),
			f(9, -0.45f, 0, 0, -0.9f, 0.5f, 0, -0.15f, -0.12f, -0.3f, 0.3f, -0.1f),
			rest(18) };

	private MaxSteelPose() {
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		MaxSteelState s = player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();

		if (s.transformDir != MaxSteelState.DIR_IDLE) {
			float[][] frames = s.transformDir == MaxSteelState.DIR_SUITING_DOWN ? SUIT_DOWN : SUIT_UP;
			float end = frames[frames.length - 1][0];
			float tick = (now - s.transformStartTick + partial) * end / Math.max(1, s.transformDurationTicks);
			blend(m, frames, tick, false);
			return;
		}
		if (!s.transformed) {
			return;
		}
		MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
		if (fx == null) {
			return;
		}
		if (fx.cannonChargeStart() != 0L) {
			float held = now - fx.cannonChargeStart() + partial;
			float in = Math.min(1f, held / 4f);
			boolean full = held >= MaxSteelConfig.CANNON_MAX_CHARGE_TICKS;
			float tremble = full ? Mth.sin(held * 2.7f) * 0.03f : 0f;
			aimRight(m, in, tremble);
			m.leftArm.xRot = Mth.lerp(in, m.leftArm.xRot, -HALF_PI + m.head.xRot + 0.15f);
			m.leftArm.yRot = Mth.lerp(in, m.leftArm.yRot, m.head.yRot + 0.75f);
			m.leftArm.zRot = Mth.lerp(in, m.leftArm.zRot, 0f);
			m.body.yRot = Mth.lerp(in, m.body.yRot, -0.15f);
			m.rightLeg.xRot = Mth.lerp(in, m.rightLeg.xRot, m.rightLeg.xRot * 0.3f - 0.35f);
			m.leftLeg.xRot = Mth.lerp(in, m.leftLeg.xRot, m.leftLeg.xRot * 0.3f + 0.35f);
			return;
		}
		if (fx.blastChargeStart() != 0L) {
			float held = now - fx.blastChargeStart() + partial;
			if (held >= 3f) {
				float in = Math.min(1f, (held - 3f) / 3f);
				aimRight(m, in, 0f);
				m.leftArm.xRot = Mth.lerp(in, m.leftArm.xRot, -0.9f);
				m.leftArm.yRot = Mth.lerp(in, m.leftArm.yRot, 0.5f);
				return;
			}
		}
		if (fx.anim() != MaxSteelFx.ANIM_NONE) {
			float tick = now - fx.animStart() + partial;
			switch (fx.anim()) {
				case MaxSteelFx.ANIM_SWAP -> blend(m, SWAP, tick, false);
				case MaxSteelFx.ANIM_BLAST -> blend(m, BLAST, tick, true);
				case MaxSteelFx.ANIM_CANNON -> blend(m, CANNON, tick, true);
				default -> {
				}
			}
		}
	}

	/** Right arm held straight out along the crosshair, the way vanilla holds a bow. */
	private static void aimRight(HumanoidModel<?> m, float w, float tremble) {
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -HALF_PI + m.head.xRot + tremble);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.1f + m.head.yRot);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0f);
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

	/**
	 * Applies a keyframe table at {@code tick}. {@code aimed}: the right arm's X / Y are offsets on top of the
	 * crosshair aim (a shot kicks the arm up from where it pointed) rather than absolute.
	 */
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
