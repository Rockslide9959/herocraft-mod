package com.projecthero.mod.client.flight;

import com.projecthero.mod.flight.landing.HeroLandingStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the superhero-landing body pose ({@link HeroLandingClient}), applied last from {@code HumanoidModelMixin} so it
 * wins over the flight pose it blends in from. Like {@code StrengthLandingPose} it moves the bones as well as turning them:
 * the hips drop, the torso leans from the hips and the neck, head and shoulders swing round with it. Model space: +Y is
 * down, -Z is forward, leg X positive swings the foot back, arm X negative raises the arm forward. Mirrored for a
 * left-handed player (the planted fist / hammer is always the main hand).
 *
 * <ul>
 *   <li>{@link HeroLandingStyle.Pose#FIST}: the main-side knee down, the other foot planted forward, the main fist punched
 *       into the ground, the free arm swept back and out, the head bowed -- it looks up just before he rises.</li>
 *   <li>{@link HeroLandingStyle.Pose#HAMMER}: the same kneel, but the main arm comes down from overhead and drives what it
 *       holds into the ground ahead.</li>
 *   <li>{@link HeroLandingStyle.Pose#WINGS}: the kneel with both arms swept back and up as the wings fold, settling in.</li>
 *   <li>{@link HeroLandingStyle.Pose#CROUCH}: both feet down in a deep lunge, arms out low for balance (jets).</li>
 *   <li>{@link HeroLandingStyle.Pose#BOARD}: crouched side-on on the Surfer's board, a hand down to it, then a step off.</li>
 * </ul>
 */
public final class HeroLandingPose {
	private HeroLandingPose() {
	}

	private static float smooth(float f) {
		return HeroLandingClient.smooth(f);
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		HeroLandingClient.State s = HeroLandingClient.get(player);
		if (s == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float k = s.weight(partial);
		if (k <= 0.001f) {
			return;
		}
		float t = s.age(partial);
		float side = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
		switch (s.style.pose) {
			case FIST -> kneel(m, k, t, s, side, Arms.FIST);
			case HAMMER -> kneel(m, k, t, s, side, Arms.HAMMER);
			case WINGS -> kneel(m, k, t, s, side, Arms.WINGS);
			case CROUCH -> crouch(m, k, t, side, false);
			case BOARD -> crouch(m, k, t, side, true);
		}
	}

	private enum Arms {
		FIST, HAMMER, WINGS
	}

	/** The impact bounce: the body sinks a little past the pose and settles back. */
	private static float bounce(float t, float strength) {
		return t > 1.5f && t < 5f ? Mth.sin((t - 1.5f) / 3.5f * Mth.PI) * (0.8f + 1.0f * strength) : 0f;
	}

	/** Lean the torso {@code lean} from hips at {@code hipY}; returns {neckY, neckZ}. */
	private static float[] torso(HumanoidModel<?> m, float k, float lean, float hipY, float yaw) {
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);
		m.body.xRot = lean;
		m.body.yRot = Mth.lerp(k, m.body.yRot, yaw);
		m.body.zRot = 0f;
		m.body.y = Mth.lerp(k, m.body.y, neckY);
		m.body.z = Mth.lerp(k, m.body.z, neckZ);
		m.head.y = m.body.y;
		m.head.z = m.body.z;
		// the shoulders sit 2 px below the neck, in the leaning body's frame
		float shoulderY = neckY + 2.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 2.0f * Mth.sin(lean);
		m.rightArm.y = Mth.lerp(k, m.rightArm.y, shoulderY);
		m.leftArm.y = Mth.lerp(k, m.leftArm.y, shoulderY);
		m.rightArm.z = Mth.lerp(k, m.rightArm.z, shoulderZ);
		m.leftArm.z = Mth.lerp(k, m.leftArm.z, shoulderZ);
		return new float[] { neckY, neckZ };
	}

	private static void head(HumanoidModel<?> m, float k, float t, float lookUpFrom, float bow, float up) {
		float look = t < lookUpFrom ? bow : Mth.lerp(smooth((t - lookUpFrom) / 4f), bow, up);
		m.head.xRot = Mth.lerp(k, m.head.xRot, look);
		m.head.yRot = Mth.lerp(k, m.head.yRot, 0f);
		m.head.zRot = 0f;
		m.hat.copyFrom(m.head);
	}

	private static void rot(ModelPart p, float k, float x, float y, float z) {
		p.xRot = Mth.lerp(k, p.xRot, x);
		p.yRot = Mth.lerp(k, p.yRot, y);
		p.zRot = Mth.lerp(k, p.zRot, z);
	}

	/** One knee down (the main side), the other foot planted forward. */
	private static void kneel(HumanoidModel<?> m, float k, float t, HeroLandingClient.State s, float side, Arms arms) {
		float drop = 6.0f * k + bounce(t, s.strength) * k;
		float leanTarget = arms == Arms.HAMMER ? 0.78f : arms == Arms.WINGS ? 0.85f : 0.95f;
		float lean = leanTarget * k;
		float hipY = 12.0f + drop;
		torso(m, k, lean, hipY, 0.12f * side);
		head(m, k, t, s.endAge() - 6f, 0.55f, -0.3f);

		ModelPart main = side > 0 ? m.rightArm : m.leftArm;
		ModelPart free = side > 0 ? m.leftArm : m.rightArm;
		ModelPart kneeLeg = side > 0 ? m.rightLeg : m.leftLeg;
		ModelPart footLeg = side > 0 ? m.leftLeg : m.rightLeg;
		switch (arms) {
			case FIST -> {
				// the fist driven straight down into the ground, the free arm swept back and out
				rot(main, k, -0.12f, 0f, 0.08f * side);
				rot(free, k, 0.85f, 0.2f * side, -1.0f * side);
			}
			case HAMMER -> {
				// overhead at touchdown, slammed down ahead by the third tick, then held there
				float strike = smooth((t - 0.5f) / 2.5f);
				float x = Mth.lerp(strike, -2.9f, -0.95f);
				rot(main, k, x, -0.1f * side, 0.05f * side);
				rot(free, k, 0.45f, 0f, -1.15f * side);
			}
			case WINGS -> {
				// swept back and up as they fold, settling to the sides through the hold
				float fold = smooth((t - 3f) / Math.max(4f, s.endAge() - 6f));
				float x = Mth.lerp(fold, 1.35f, 0.55f);
				float z = Mth.lerp(fold, 0.65f, 0.2f);
				rot(main, k, x, 0f, z * side);
				rot(free, k, x, 0f, -z * side);
			}
		}

		kneeLeg.y = hipY;
		footLeg.y = hipY;
		kneeLeg.z = Mth.lerp(k, kneeLeg.z, 0.8f);
		footLeg.z = Mth.lerp(k, footLeg.z, -0.8f);
		rot(kneeLeg, k, 1.047f, 0f, 0.06f * side);
		rot(footLeg, k, -0.85f, -0.1f * side, -0.1f * side);
	}

	/** Both feet down in a lunge; {@code board}: side-on on the Silver Surfer's board, a hand down to it. */
	private static void crouch(HumanoidModel<?> m, float k, float t, float side, boolean board) {
		float drop = (board ? 5.0f : 4.5f) * k + bounce(t, 0.5f) * k * 0.8f;
		float lean = (board ? 0.62f : 0.45f) * k;
		float hipY = 12.0f + drop;
		torso(m, k, lean, hipY, board ? 0.45f * side : 0.08f * side);
		head(m, k, t, 99f, board ? 0.2f : 0.35f, 0f);
		ModelPart main = side > 0 ? m.rightArm : m.leftArm;
		ModelPart free = side > 0 ? m.leftArm : m.rightArm;
		if (board) {
			// a hand down to the board, the other arm out wide
			rot(main, k, -0.15f, 0f, 0.3f * side);
			rot(free, k, -0.5f, 0f, -1.35f * side);
		} else {
			rot(main, k, -0.35f, 0f, 1.0f * side);
			rot(free, k, -0.35f, 0f, -1.0f * side);
		}
		// legs: the foot-leg forward, the other back, both feet on the ground (12 cos 0.89 = 7.5 px)
		float a = (float) Math.acos(Mth.clamp((12f - drop) / 12f, -1f, 1f));
		ModelPart back = side > 0 ? m.rightLeg : m.leftLeg;
		ModelPart front = side > 0 ? m.leftLeg : m.rightLeg;
		back.y = hipY;
		front.y = hipY;
		back.z = Mth.lerp(k, back.z, 0.6f);
		front.z = Mth.lerp(k, front.z, -0.6f);
		rot(back, k, a * 0.9f, 0.05f * side, 0.12f * side);
		rot(front, k, -a, -0.12f * side, -0.12f * side);
	}
}
