package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ironman.entity.HulkbusterClimb;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user) Hulkbuster fixes: the pilot's body language while climbing out of / into the Hulkbuster's open back --
 * keyed to the same synced tick as the path the server carries them along ({@link HulkbusterClimb}), so the hands meet
 * the rim and the plate where the suit really is, on every viewer's screen.
 *
 * <p>Out (facing away from the suit): stood inside -> both arms up for the rim, looking up -> hauled over it, pitched
 * forward, one hand pushing down on the rim and the other reaching for the plate, legs trailing -> knees on the plate ->
 * crouched at its end -> a hop with the arms out -> a knees-bent landing -> standing. In (facing the suit): crouch with the
 * arms swung back -> jump, arms up -> land kneeling on the plate reaching for the rim -> crawl in over it -> stood inside
 * with the arms a little out (the open suit's pose, so it closes on the right parts).
 *
 * <p>Channels: whole-body pitch about the hips (degrees, minus = forward, applied in {@code PlayerRendererMixin}), right /
 * left arm pitch and roll, right / left leg pitch, head pitch (degrees, the model's own signs: minus pitch = raised
 * forward, plus roll = out to the side for the right arm).
 */
public final class HulkbusterClimbPose {
	private static final int PITCH = 1, RAX = 2, RAZ = 3, LAX = 4, LAZ = 5, RLX = 6, LLX = 7, HX = 8;

	// tick, body pitch, rArm x/z, lArm x/z, rLeg x, lLeg x, head x
	private static final float[][] OUT = {
			{ 0, 0, -10, 6, -10, -6, 0, 0, 0 },
			{ 8, 6, -168, 12, -166, -12, 0, 0, -28 },
			{ 14, -12, -150, 16, -122, -10, 12, -18, -14 },
			{ 22, -62, -42, 12, -96, -16, 28, 16, -38 },
			{ 28, -36, -62, 10, -48, -24, -72, -52, -22 },
			{ 32, -22, -36, 24, -36, -24, -84, -78, -10 },
			{ HulkbusterClimb.OUT_HOP, -10, -62, 58, -62, -58, -30, -20, 0 },
			{ 38, -4, -40, 48, -40, -48, -14, -24, 4 },
			{ HulkbusterClimb.OUT_LAND, -26, -32, 34, -30, -36, -44, -32, 6 },
			{ 43, -8, -12, 14, -10, -14, -12, -10, 0 },
			{ HulkbusterClimb.OUT_TICKS, 0, 0, 4, 0, -4, 0, 0, 0 },
	};
	private static final float[][] IN = {
			{ 0, 0, -6, 6, -6, -6, 0, 0, 0 },
			{ HulkbusterClimb.IN_JUMP, -22, 34, 10, 34, -10, -62, -58, 8 },
			{ 9, -8, -172, 12, -170, -12, -18, -44, -16 },
			{ HulkbusterClimb.IN_ON_PLATE, -40, -104, 12, -98, -12, -76, -60, -26 },
			{ 16, -44, -118, 14, -110, -14, -74, -58, -28 },
			{ 24, -66, -150, 16, -140, -16, 22, 30, -40 },
			{ 30, -20, -60, 12, -62, -12, 6, -10, -10 },
			{ HulkbusterClimb.IN_TICKS, 0, IronManSentryClient.OPEN_ARM_PITCH, IronManSentryClient.OPEN_ARM_ROLL,
					IronManSentryClient.OPEN_ARM_PITCH, -IronManSentryClient.OPEN_ARM_ROLL, 0, 0, 0 },
	};

	private HulkbusterClimbPose() {
	}

	/** Is {@code kind} one of the Hulkbuster climbs? */
	public static boolean climbing(int kind) {
		return kind == IronManSentryEntity.STEP_CLIMB_OUT || kind == IronManSentryEntity.STEP_CLIMB_IN;
	}

	/** The climb's time this frame: the synced tick, a tick behind (the ride's position is interpolated from it). */
	private static float time(IronManSentryEntity s) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		return Math.max(0f, s.climbTick() - 1 + partial);
	}

	private static float[] sample(float[][] keys, float t) {
		float[] out = new float[keys[0].length];
		if (t <= keys[0][0]) {
			System.arraycopy(keys[0], 0, out, 0, out.length);
			return out;
		}
		for (int i = 1; i < keys.length; i++) {
			if (t <= keys[i][0]) {
				float[] a = keys[i - 1];
				float[] b = keys[i];
				float u = (t - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				u = u * u * (3f - 2f * u);
				for (int c = 0; c < out.length; c++) {
					out[c] = Mth.lerp(u, a[c], b[c]);
				}
				return out;
			}
		}
		System.arraycopy(keys[keys.length - 1], 0, out, 0, out.length);
		return out;
	}

	private static float[] frame(IronManSentryEntity s) {
		return sample(s.stepKind() == IronManSentryEntity.STEP_CLIMB_OUT ? OUT : IN, time(s));
	}

	/** {@code HumanoidModelMixin} (via {@link IronManSentryClient#applyStepPose}): the climbing pilot's limbs. */
	public static void apply(IronManSentryEntity s, HumanoidModel<?> model) {
		float[] f = frame(s);
		float d = Mth.DEG_TO_RAD;
		model.rightArm.xRot = f[RAX] * d;
		model.rightArm.yRot = 0f;
		model.rightArm.zRot = f[RAZ] * d;
		model.leftArm.xRot = f[LAX] * d;
		model.leftArm.yRot = 0f;
		model.leftArm.zRot = f[LAZ] * d;
		model.rightLeg.xRot = f[RLX] * d;
		model.rightLeg.yRot = 0f;
		model.rightLeg.zRot = 0.03f;
		model.leftLeg.xRot = f[LLX] * d;
		model.leftLeg.yRot = 0f;
		model.leftLeg.zRot = -0.03f;
		model.head.xRot = f[HX] * d;
		model.head.yRot = 0f;
		model.hat.copyFrom(model.head);
		model.body.xRot = 0f;
		model.body.yRot = 0f;
	}

	/** {@code PlayerRendererMixin#setupRotations}: the whole body pitched about the hips while it climbs. */
	public static void rotate(Player player, PoseStack poseStack) {
		IronManSentryEntity s = IronManSentryClient.stepping(player);
		if (s == null || !climbing(s.stepKind())) {
			return;
		}
		float pitch = frame(s)[PITCH];
		if (Math.abs(pitch) < 0.05f) {
			return;
		}
		float hips = 0.75f;
		poseStack.translate(0f, hips, 0f);
		poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
		poseStack.translate(0f, -hips, 0f);
	}
}
