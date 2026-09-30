package com.projecthero.mod.client.mutation.v0145;

import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.5: Super Strength's superhero landing ({@code p01.landing}, played by the server on every Power Leap / dive
 * landing so every viewer sees it). The body slams down onto one knee -- right knee to the ground, left foot planted
 * forward -- leaning from the hips with the right fist driven into the ground and the head bowed; it holds for ~0.6 s,
 * looks up, then rises.
 *
 * <p>{@code MutationPose} only rotates bones, so like {@code HulkPose} this also moves them: the hips drop, and the
 * neck swings forward and down around the hip point with the head and shoulders following it. Model space: +Y is
 * down, -Z is forward, leg X positive swings the foot back, arm X negative raises the arm forward. Applied after
 * {@code MutationPose} from {@code HumanoidModelMixin}.
 */
public final class StrengthLandingPose {
	/** Ticks: slam down by IN, hold to HOLD_END, back on your feet by END. */
	public static final float IN = 2.0f;
	public static final float HOLD_END = 12.0f;
	public static final float END = 20.0f;

	/** Hip drop (px): hips 6 px off the ground. */
	private static final float DROP = 6.0f;
	/** Forward lean from the hips (rad) -- deep enough for the planted fist to reach the ground. */
	private static final float LEAN = 0.95f;
	/** Right leg folded back so the knee end rests on the ground (acos(6 / 12)). */
	private static final float KNEE_BACK = 1.047f;
	/** Left leg forward, foot planted. */
	private static final float FOOT_FORWARD = -0.85f;
	private static final float HEAD_BOW = 0.55f;
	private static final float HEAD_UP = -0.3f;

	private StrengthLandingPose() {
	}

	private static float smooth(float f) {
		f = Mth.clamp(f, 0f, 1f);
		return f * f * (3f - 2f * f);
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		MutationVisualState s = MutationVisuals.state(player);
		if (!SuperStrengthHandlers.LANDING_POSE.equals(s.anim())) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = (player.level().getGameTime() - s.animStart()) + partial;
		if (t < 0f || t >= END) {
			return;
		}
		float k = t < IN ? smooth(t / IN) : t < HOLD_END ? 1f : 1f - smooth((t - HOLD_END) / (END - HOLD_END));
		if (k <= 0f) {
			return;
		}
		// the impact: the body sinks a pixel past the pose and settles back
		float bounce = t > IN && t < IN + 3f ? Mth.sin((t - IN) / 3f * Mth.PI) * 1.2f : 0f;
		float drop = DROP * k + bounce;
		float lean = LEAN * k;
		float hipY = 12.0f + drop;
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);

		m.body.xRot = lean;
		m.body.yRot = Mth.lerp(k, m.body.yRot, 0.12f);
		m.body.y = neckY;
		m.body.z = neckZ;

		// head bowed through the impact, then it lifts to look up just before rising
		float look = t < 9f ? HEAD_BOW : Mth.lerp(smooth((t - 9f) / 4f), HEAD_BOW, HEAD_UP);
		m.head.y = neckY;
		m.head.z = neckZ;
		m.head.xRot = Mth.lerp(k, m.head.xRot, look);
		m.head.yRot = Mth.lerp(k, m.head.yRot, 0.0f);
		m.head.zRot = 0.0f;
		m.hat.copyFrom(m.head);

		// shoulders sit 2 px below the neck, in the leaning body's frame
		float shoulderY = neckY + 2.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 2.0f * Mth.sin(lean);
		m.rightArm.y = Mth.lerp(k, m.rightArm.y, shoulderY);
		m.leftArm.y = Mth.lerp(k, m.leftArm.y, shoulderY);
		m.rightArm.z = Mth.lerp(k, m.rightArm.z, shoulderZ);
		m.leftArm.z = Mth.lerp(k, m.leftArm.z, shoulderZ);
		// right fist driven straight down into the ground, left forearm resting over the forward knee
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -0.12f);
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0.0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.08f);
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -0.95f);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.2f);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.1f);

		// one knee down: right leg folded back under, left foot planted forward
		m.rightLeg.y = hipY;
		m.leftLeg.y = hipY;
		m.rightLeg.z = Mth.lerp(k, m.rightLeg.z, 0.8f);
		m.leftLeg.z = Mth.lerp(k, m.leftLeg.z, -0.8f);
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, KNEE_BACK);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, FOOT_FORWARD);
		m.rightLeg.yRot = Mth.lerp(k, m.rightLeg.yRot, 0.0f);
		m.leftLeg.yRot = Mth.lerp(k, m.leftLeg.yRot, -0.1f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, 0.06f);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -0.1f);
	}
}
