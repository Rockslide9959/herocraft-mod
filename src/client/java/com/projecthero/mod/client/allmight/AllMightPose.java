package com.projecthero.mod.client.allmight;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * All Might's poses (v0.12.33): keyframed limb poses for every Smash, the Plus Ultra flex, the Leap and the
 * transformation, applied to the humanoid model after vanilla's own animation. They are driven by the synced
 * {@link AllMightState#animId} / {@link AllMightState#animStart}, i.e. by the very game-time clock the server uses to
 * schedule each hit -- so the punch lands on the keyframe where the fist is fully extended and nothing can desync.
 * Because the costume's GeckoLib armour copies its bones from the vanilla model, the armour follows every pose too.
 *
 * <p>Each pose is a list of frames {@code {tick, rightArmX, rightArmY, rightArmZ, leftArmX, leftArmY, leftArmZ, bodyX, bodyY,
 * rightLegX, leftLegX, headX}} in radians (arm X negative = swung forward/up, right arm Z positive = raised out to his side),
 * interpolated with a smoothstep and faded in/out over the first/last ticks.
 *
 * <p>v0.16.6: the five Shift moves; Oklahoma and St. Louis also spin the whole body ({@link #spinDegrees}) and Manchester flips it
 * head over heels ({@link #flipDegrees}) -- both applied in {@code PlayerRenderer.setupRotations} by {@code AllMightSpinMixin}. In
 * v0.16.7 broken and hurt limbs show in the pose ({@link #applyInjuries}).
 */
public final class AllMightPose {
	private static final float[][] DETROIT = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 5, 0.9f, -0.2f, 0, -0.9f, 0.4f, 0, 0.1f, 0.5f, 0.3f, -0.3f, 0 },
			{ 6, -1.6f, -0.15f, 0, 0.4f, 0.2f, 0, 0.3f, -0.6f, -0.4f, 0.4f, 0 },
			{ 10, -1.6f, -0.15f, 0, 0.4f, 0.2f, 0, 0.3f, -0.6f, -0.4f, 0.4f, 0 },
			{ 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] TEXAS = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, 1.3f, 0.2f, 0, 1.3f, -0.2f, 0, -0.25f, 0, 0.3f, -0.3f, -0.15f },
			{ 8, -1.55f, 0.1f, 0, -1.55f, -0.1f, 0, 0.35f, 0, -0.4f, 0.4f, 0.1f },
			{ 18, -1.55f, 0.1f, 0, -1.55f, -0.1f, 0, 0.35f, 0, -0.4f, 0.4f, 0.1f },
			{ 24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] CAROLINA = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 5, 0.9f, 0, 0, 0.9f, 0, 0, 0.5f, 0, 0.7f, -0.7f, 0.2f },
			{ 7, -1.55f, -0.1f, 0, -1.55f, 0.1f, 0, 1.0f, 0, 0.6f, -0.3f, -0.6f },
			{ 18, -1.55f, -0.1f, 0, -1.55f, 0.1f, 0, 1.0f, 0, 0.6f, -0.3f, -0.6f },
			{ 26, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] NEW_HAMPSHIRE = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, 0.8f, 0, 0, 0.8f, 0, 0, 0.5f, 0, 0.8f, 0.8f, 0.3f },
			{ 9, -3.0f, 0, 0, -3.0f, 0, 0, -0.2f, 0, -0.2f, 0.2f, -0.3f },
			{ 22, -3.0f, 0, 0, -3.0f, 0, 0, -0.2f, 0, -0.2f, 0.2f, -0.3f },
			{ 32, -1.6f, -0.1f, 0, 0.3f, 0, 0, 0.5f, 0, 0.4f, -0.4f, 0.2f },
			{ 40, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] COWL = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -1.0f, 0.4f, 0.35f, -1.0f, -0.4f, -0.35f, -0.1f, 0, 0.1f, -0.1f, -0.1f },
			{ 10, -1.0f, 0.4f, 0.35f, -1.0f, -0.4f, -0.35f, -0.1f, 0, 0.1f, -0.1f, -0.1f },
			{ 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] UNITED_STATES = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 22, 1.7f, -0.3f, 0, -1.0f, 0.5f, 0, 0.15f, 0.9f, 0.5f, -0.5f, 0 },
			{ 29, 1.9f, -0.3f, 0, -1.0f, 0.5f, 0, 0.2f, 1.0f, 0.5f, -0.5f, 0 },
			{ 31, -1.65f, -0.1f, 0, 0.3f, 0.2f, 0, 0.55f, -0.8f, -0.5f, 0.5f, 0.1f },
			{ 44, -1.65f, -0.1f, 0, 0.3f, 0.2f, 0, 0.55f, -0.8f, -0.5f, 0.5f, 0.1f },
			{ 58, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] LEAP = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, 0.6f, 0, 0, 0.6f, 0, 0, 0.45f, 0, 0.7f, 0.7f, 0.2f },
			{ 6, -2.8f, 0, 0, -2.8f, 0, 0, -0.15f, 0, -0.2f, 0.2f, -0.3f },
			{ 14, -2.4f, 0, 0, -2.4f, 0, 0, -0.1f, 0, 0, 0, -0.2f },
			{ 22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] TRANSFORM_UP = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 8, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.1f, 0, 0.05f, -0.05f, 0.1f },
			{ 16, -0.25f, 0, 1.45f, -0.25f, 0, -1.45f, -0.18f, 0, 0.12f, -0.12f, -0.25f },
			{ 24, -0.25f, 0, 1.45f, -0.25f, 0, -1.45f, -0.18f, 0, 0.12f, -0.12f, -0.25f },
			{ 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] TRANSFORM_DOWN = {
			{ 0, -0.2f, 0, 0.8f, -0.2f, 0, -0.8f, 0, 0, 0, 0, 0 },
			{ 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };

	// ---------------------------------------------------------------- v0.16.6: the Shift moves

	/** Delaware: the right hand held out, three finger flicks (one per air bullet: ticks 5, 9, 13). */
	private static final float[][] DELAWARE = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -1.45f, -0.3f, 0, 0.35f, 0.15f, 0, 0.1f, -0.35f, -0.15f, 0.15f, 0 },
			{ 5, -1.66f, 0.02f, 0, 0.35f, 0.15f, 0, 0.16f, -0.28f, -0.15f, 0.15f, 0 },
			{ 7, -1.45f, -0.3f, 0, 0.35f, 0.15f, 0, 0.1f, -0.35f, -0.15f, 0.15f, 0 },
			{ 9, -1.66f, 0.02f, 0, 0.35f, 0.15f, 0, 0.16f, -0.28f, -0.15f, 0.15f, 0 },
			{ 11, -1.45f, -0.3f, 0, 0.35f, 0.15f, 0, 0.1f, -0.35f, -0.15f, 0.15f, 0 },
			{ 13, -1.66f, 0.02f, 0, 0.35f, 0.15f, 0, 0.16f, -0.28f, -0.15f, 0.15f, 0 },
			{ 17, -1.5f, -0.15f, 0, 0.35f, 0.15f, 0, 0.12f, -0.3f, -0.15f, 0.15f, 0 },
			{ 23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	/** Manchester: spring with the arms up, tuck through the flip, the right leg high overhead, then the axe kick down. */
	private static final float[][] MANCHESTER = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -2.6f, 0, 0.2f, -2.6f, 0, -0.2f, 0.3f, 0, -0.4f, -0.4f, 0.2f },
			{ 8, -0.6f, 0, 0.3f, -0.6f, 0, -0.3f, 0.5f, 0, -1.6f, -1.6f, 0.4f },
			{ 14, -0.6f, 0, 0.3f, -0.6f, 0, -0.3f, 0.5f, 0, -1.6f, -1.6f, 0.4f },
			{ 17, 0.9f, 0, 0.45f, 0.9f, 0, -0.45f, -0.25f, 0, -2.7f, 0.4f, -0.3f },
			{ 22, 0.6f, 0, 0.35f, 0.6f, 0, -0.35f, 0.35f, 0, -0.2f, 0.35f, 0.3f },
			{ 34, 0.6f, 0, 0.35f, 0.6f, 0, -0.35f, 0.35f, 0, -0.2f, 0.35f, 0.3f },
			{ 42, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	/** Oklahoma: arms flung straight out to his sides while the body spins (two full turns). */
	private static final float[][] OKLAHOMA = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -0.1f, 0, 1.45f, -0.1f, 0, -1.45f, 0.15f, 0, 0.25f, -0.25f, 0 },
			{ 18, -0.1f, 0, 1.45f, -0.1f, 0, -1.45f, 0.15f, 0, 0.25f, -0.25f, 0 },
			{ 24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	/** Missouri: the right hand raised high, then a slanting chop down and across as he lunges. */
	private static final float[][] MISSOURI = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 5, -2.9f, 0.2f, 0.35f, 0.4f, 0, 0, -0.15f, 0.4f, 0.3f, -0.3f, -0.2f },
			{ 7, -0.45f, -0.65f, 0, 0.5f, 0, 0, 0.45f, -0.5f, -0.6f, 0.5f, 0.2f },
			{ 14, -0.45f, -0.65f, 0, 0.5f, 0, 0, 0.45f, -0.5f, -0.6f, 0.5f, 0.2f },
			{ 22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	/** St. Louis: a twist, then the right leg swung out level as the body whips round once. */
	private static final float[][] ST_LOUIS = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.1f, 0.9f, 0.2f, -0.2f, 0 },
			{ 7, 0, 0, 1.3f, 0, 0, -1.3f, -0.2f, 0, -1.6f, 0.2f, 0 },
			{ 13, 0, 0, 1.3f, 0, 0, -1.3f, -0.2f, 0, -1.6f, 0.2f, 0 },
			{ 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	/** Pass On / Lend: a hand held out to the other player. */
	private static final float[][] PASS_ON = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, -1.25f, -0.2f, 0, 0, 0, 0, 0.1f, 0, 0, 0, 0.15f },
			{ 50, -1.25f, -0.2f, 0, 0, 0, 0, 0.1f, 0, 0, 0, 0.15f },
			{ 60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };

	private AllMightPose() {
	}

	private static float[][] frames(int animId) {
		return switch (animId) {
			case AllMightState.ANIM_DETROIT -> DETROIT;
			case AllMightState.ANIM_TEXAS -> TEXAS;
			case AllMightState.ANIM_CAROLINA -> CAROLINA;
			case AllMightState.ANIM_NEW_HAMPSHIRE -> NEW_HAMPSHIRE;
			case AllMightState.ANIM_COWL -> COWL;
			case AllMightState.ANIM_UNITED_STATES -> UNITED_STATES;
			case AllMightState.ANIM_LEAP -> LEAP;
			case AllMightState.ANIM_TRANSFORM_UP -> TRANSFORM_UP;
			case AllMightState.ANIM_TRANSFORM_DOWN -> TRANSFORM_DOWN;
			case AllMightState.ANIM_DELAWARE -> DELAWARE;
			case AllMightState.ANIM_MANCHESTER -> MANCHESTER;
			case AllMightState.ANIM_OKLAHOMA -> OKLAHOMA;
			case AllMightState.ANIM_MISSOURI -> MISSOURI;
			case AllMightState.ANIM_ST_LOUIS -> ST_LOUIS;
			case AllMightState.ANIM_PASS_ON -> PASS_ON;
			default -> null;
		};
	}

	/** Pose values at {@code tick} (fractional) of {@code f}, or null past the end. */
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

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		boolean posed = false;
		float[][] f = s.animId == AllMightState.ANIM_NONE ? null : frames(s.animId);
		if (f != null) {
			float tick = (player.level().getGameTime() - s.animStart) + partial();
			float[] p = sample(f, tick);
			if (p != null) {
				posed = true;
				// fade in over the first 2 ticks and out over the last 3, so it blends with the walk cycle instead of snapping
				float end = f[f.length - 1][0];
				float w = Math.min(1f, Math.min(tick / 2f, (end - tick) / 3f));
				w = Math.max(0f, w);
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1]);
				m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2]);
				m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4]);
				m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5]);
				m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
				// v0.16.1: the torso bends from the hips (not the neck), so the bottom of it stays on the legs
				com.projecthero.mod.client.HipBend.pitch(m, w, p[7]);
				m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
				m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, p[9]);
				m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, p[10]);
				m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
				m.hat.copyFrom(m.head);
			}
		}
		if (!posed) {
			applyInjuries(player, m, s);
		}
	}

	/**
	 * v0.16.7: injuries show in the body (between moves; a move's own pose wins while it plays):
	 * <ul>
	 * <li>a <b>broken arm</b> hangs dead straight, tucked to his side, and barely sways; if the other arm is whole it cradles the
	 * broken one across his stomach (unless it is swinging);</li>
	 * <li>a <b>hurt arm</b> (70+) swings only a third as far;</li>
	 * <li><b>broken legs</b>: a heavy limp -- the right leg stays stiff, the left takes short steps, the head bobs down as the bad
	 * leg takes the weight and stays bowed; <b>hurt legs</b> limp lightly.</li>
	 * </ul>
	 * Replaces v0.16.6's whole-body recoil (both arms limp).
	 */
	static void applyInjuries(Player player, HumanoidModel<?> m, AllMightState s) {
		boolean rightBroken = s.isBroken(AllMightState.Limb.RIGHT_ARM);
		boolean leftBroken = s.isBroken(AllMightState.Limb.LEFT_ARM);
		int legs = com.projecthero.mod.allmight.AllMightStrain.tier(s, AllMightState.Limb.LEGS);
		float pt = partial();
		float walkPos = player.walkAnimation.position(pt);
		float walkSpeed = Math.min(1f, player.walkAnimation.speed(pt));
		float sway = Mth.sin(walkPos * 0.6662f) * 0.1f * walkSpeed;
		boolean swinging = m.attackTime > 0f;
		// hurt arms swing less
		if (!rightBroken && com.projecthero.mod.allmight.AllMightStrain.tier(s, AllMightState.Limb.RIGHT_ARM) == 2 && !swinging) {
			m.rightArm.xRot *= 0.35f;
		}
		if (!leftBroken && com.projecthero.mod.allmight.AllMightStrain.tier(s, AllMightState.Limb.LEFT_ARM) == 2 && !swinging) {
			m.leftArm.xRot *= 0.35f;
		}
		if (rightBroken) {
			m.rightArm.xRot = 0.08f + sway * 0.4f;
			m.rightArm.yRot = 0f;
			m.rightArm.zRot = 0.04f;
			if (!leftBroken && !swinging) { // the good hand holds the broken forearm against his stomach
				m.leftArm.xRot = -0.6f;
				m.leftArm.yRot = 0.85f;
				m.leftArm.zRot = 0.05f;
			}
		}
		if (leftBroken) {
			m.leftArm.xRot = 0.08f - sway * 0.4f;
			m.leftArm.yRot = 0f;
			m.leftArm.zRot = -0.04f;
			if (!rightBroken && !swinging) {
				m.rightArm.xRot = -0.6f;
				m.rightArm.yRot = -0.85f;
				m.rightArm.zRot = -0.05f;
			}
		}
		if (rightBroken && leftBroken) {
			// both arms gone: the shoulders slump forward, the head bows
			com.projecthero.mod.client.HipBend.pitch(m, 1f, m.body.xRot + 0.18f);
			m.head.xRot = m.head.xRot + 0.25f;
		}
		if (legs >= 2 && !player.isPassenger() && !player.isFallFlying()) {
			float heavy = legs == 3 ? 1f : 0.45f;
			float step = Mth.sin(walkPos * 0.6662f);
			// the bad (right) leg stays stiff, the good one takes short steps
			m.rightLeg.xRot = Mth.lerp(heavy, m.rightLeg.xRot, step * 0.25f * walkSpeed);
			m.leftLeg.xRot = Mth.lerp(heavy, m.leftLeg.xRot, m.leftLeg.xRot * 0.6f);
			// the head bobs down each time the bad leg takes the weight (xRot only: vanilla never resets head.zRot, so a roll there
			// would accumulate frame on frame)
			m.head.xRot = m.head.xRot + Math.max(0f, step) * 0.18f * heavy * walkSpeed;
			if (legs == 3) {
				m.head.xRot = m.head.xRot + 0.12f;
			}
		}
		m.hat.copyFrom(m.head);
	}

	// ---------------------------------------------------------------- whole-body turns (AllMightSpinMixin)

	private static float eased(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** Oklahoma (two full turns) and St. Louis (one): degrees to turn the whole body about its vertical axis, 0 otherwise. */
	public static float spinDegrees(Player player, float partial) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		float tick = player.level().getGameTime() - s.animStart + partial;
		if (s.animId == AllMightState.ANIM_OKLAHOMA) {
			float t = (tick - AllMightConfig.OKLAHOMA_WINDUP) / AllMightConfig.OKLAHOMA_SPIN_TICKS;
			return t <= 0f || t >= 1f ? 0f : -720f * eased(t);
		}
		if (s.animId == AllMightState.ANIM_ST_LOUIS) {
			float t = (tick - 3f) / 8f;
			return t <= 0f || t >= 1f ? 0f : -360f * eased(t);
		}
		return 0f;
	}

	/** Manchester: degrees to flip the whole body head over heels (forward) during the flip, 0 otherwise. */
	public static float flipDegrees(Player player, float partial) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || !s.hasPower || s.animId != AllMightState.ANIM_MANCHESTER) {
			return 0f;
		}
		float tick = player.level().getGameTime() - s.animStart + partial;
		float t = (tick - AllMightConfig.MANCHESTER_WINDUP) / AllMightConfig.MANCHESTER_FLIP_TICKS;
		return t <= 0f || t >= 1f ? 0f : 360f * eased(t);
	}

	/** Used by tests: whether an animation id has a pose. */
	public static boolean hasPose(int animId) {
		return frames(animId) != null;
	}

	/** Used by the HUD/tests: the length of a pose in ticks (0 if none). */
	public static int lengthTicks(int animId) {
		float[][] f = frames(animId);
		return f == null ? 0 : (int) f[f.length - 1][0];
	}

	/** Whether the wearer currently looks fully transformed (used to keep the pose helper referenced in one place). */
	public static boolean fullPower(Player player) {
		return AllMight.isFullPower(player);
	}
}
