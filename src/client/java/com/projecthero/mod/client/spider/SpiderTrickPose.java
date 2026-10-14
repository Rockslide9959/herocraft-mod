package com.projecthero.mod.client.spider;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.spider.SpiderAnim;
import com.projecthero.mod.spider.SpiderTricks;
import com.projecthero.mod.spider.data.SpiderMoveState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.2: the swing tricks' poses ({@link SpiderTricks}) -- driven by the synced animation id / start, so every viewer
 * sees the same trick. A whole-body rotation ({@link #applyBodyRotation}, from {@code SpiderSwingPose}) plus the limbs
 * ({@link #applyLimbs}), with jointed knees and elbows:
 * <ul>
 * <li>front flip: a forward somersault in a tight tuck, hands on the shins;</li>
 * <li>backflip: arms thrown up overhead, knees tucked, a full turn backward;</li>
 * <li>corkscrew: a barrel roll about his line of travel, arms crossed on the chest, legs straight together;</li>
 * <li>split: a straddle split, legs flung out wide, hands reaching for the toes;</li>
 * <li>twist: a full pirouette, arms tucked to the chest, legs crossed straight.</li>
 * </ul>
 */
public final class SpiderTrickPose {
	private SpiderTrickPose() {
	}

	/** {kind, ticks into it} of the trick {@code player} is throwing, or null. */
	private static float[] current(Player player, float partial) {
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		if (s == null || s.animId < SpiderAnim.TRICK_FRONT_FLIP || s.animId > SpiderAnim.TRICK_TWIST) {
			return null;
		}
		float t = player.level().getGameTime() - s.animStart + partial;
		if (t < 0f || t >= SpiderTricks.LENGTH) {
			return null;
		}
		return new float[] {s.animId - SpiderAnim.TRICK_FRONT_FLIP, t};
	}

	private static float smooth(float f) {
		f = Mth.clamp(f, 0f, 1f);
		return f * f * (3f - 2f * f);
	}

	/** The trick's whole-body turn. Returns true while a trick is playing. */
	public static boolean applyBodyRotation(Player player, PoseStack pose, float partial) {
		float[] c = current(player, partial);
		if (c == null) {
			return false;
		}
		int kind = (int) c[0];
		// the turn runs over most of the trick, leaving a beat at the end to open out
		float p = smooth(c[1] / (SpiderTricks.LENGTH - 2f));
		switch (kind) {
			case SpiderTricks.FRONT_FLIP -> about(pose, 0.9f, Axis.XP.rotationDegrees(360f * p));
			case SpiderTricks.BACKFLIP -> about(pose, 0.9f, Axis.XP.rotationDegrees(-360f * p));
			case SpiderTricks.CORKSCREW -> about(pose, 0.9f, Axis.ZP.rotationDegrees(-360f * p));
			case SpiderTricks.TWIST -> pose.mulPose(Axis.YP.rotationDegrees(360f * p));
			case SpiderTricks.SPLIT -> about(pose, 0.9f, Axis.XP.rotationDegrees(-18f * Mth.sin(Mth.PI * Math.min(1f, c[1]
					/ SpiderTricks.LENGTH))));
			default -> {
			}
		}
		return true;
	}

	private static void about(PoseStack pose, float pivot, org.joml.Quaternionf q) {
		pose.translate(0.0f, pivot, 0.0f);
		pose.mulPose(q);
		pose.translate(0.0f, -pivot, 0.0f);
	}

	/** The trick's limbs, eased in over 1.5 ticks and out over the last 2.5. */
	public static void applyLimbs(Player player, HumanoidModel<?> m, float partial) {
		float[] c = current(player, partial);
		if (c == null) {
			return;
		}
		int kind = (int) c[0];
		float t = c[1];
		float k = smooth(Math.min(1f, Math.min(t / 1.5f, (SpiderTricks.LENGTH - t) / 2.5f)));
		if (k <= 0f) {
			return;
		}
		switch (kind) {
			case SpiderTricks.FRONT_FLIP -> {
				// the tight tuck: knees to the chest, hands on the shins
				HipBend.pitch(m, k, 0.55f);
				legs(m, k, -1.75f, -1.7f, 2.15f, 2.1f, 0.12f);
				arms(m, k, -1.05f, 0.25f, 0.1f, -1.3f);
			}
			case SpiderTricks.BACKFLIP -> {
				// arms thrown up and back overhead, knees tucked
				HipBend.pitch(m, k, 0.2f);
				legs(m, k, -1.5f, -1.45f, 2.0f, 1.95f, 0.1f);
				arms(m, k, -2.9f, 0f, 0.35f, -0.2f);
			}
			case SpiderTricks.CORKSCREW -> {
				// straight as an arrow, arms folded across the chest (the arm turned in about itself, the forearm folded forward)
				HipBend.pitch(m, k, 0f);
				legs(m, k, 0.05f, 0.05f, 0.08f, 0.08f, -0.05f);
				arms(m, k, -0.35f, 0.95f, 0.05f, -1.9f);
			}
			case SpiderTricks.SPLIT -> {
				// a straddle: legs flung out wide, reaching for the toes
				HipBend.pitch(m, k, 0.35f);
				SpiderPerch.set(m.rightLeg, k, new Vector3f(m.rightLeg.x, m.rightLeg.y, m.rightLeg.z), -0.45f, 0f, 1.3f);
				SpiderPerch.set(m.leftLeg, k, new Vector3f(m.leftLeg.x, m.leftLeg.y, m.leftLeg.z), -0.45f, 0f, -1.3f);
				JointedLimbs.setJoint(JointedLimbs.RIGHT_LEG, null);
				JointedLimbs.setJoint(JointedLimbs.LEFT_LEG, null);
				SpiderPerch.set(m.rightArm, k, new Vector3f(m.rightArm.x, m.rightArm.y, m.rightArm.z), -0.75f, 0f, 1.05f);
				SpiderPerch.set(m.leftArm, k, new Vector3f(m.leftArm.x, m.leftArm.y, m.leftArm.z), -0.75f, 0f, -1.05f);
				JointedLimbs.setJoint(JointedLimbs.RIGHT_ARM, null);
				JointedLimbs.setJoint(JointedLimbs.LEFT_ARM, null);
			}
			case SpiderTricks.TWIST -> {
				// a pirouette: arms pulled in to the chest, legs straight and crossed
				HipBend.pitch(m, k, 0f);
				SpiderPerch.set(m.rightLeg, k, new Vector3f(m.rightLeg.x, m.rightLeg.y, m.rightLeg.z), 0.05f, 0f, -0.12f);
				SpiderPerch.set(m.leftLeg, k, new Vector3f(m.leftLeg.x, m.leftLeg.y, m.leftLeg.z), -0.05f, 0f, 0.12f);
				JointedLimbs.setJoint(JointedLimbs.RIGHT_LEG, new Quaternionf().rotationX(0.15f * k));
				JointedLimbs.setJoint(JointedLimbs.LEFT_LEG, null);
				arms(m, k, -0.25f, 1.05f, 0.1f, -2.2f);
			}
			default -> {
			}
		}
		m.head.xRot = Mth.lerp(k, m.head.xRot, kind == SpiderTricks.FRONT_FLIP ? 0.45f : kind == SpiderTricks.BACKFLIP
				? -0.5f : 0f);
		m.head.yRot = Mth.lerp(k, m.head.yRot, 0f);
		m.hat.copyFrom(m.head);
	}

	/** Both legs: thigh pitch (negative = forward / up), knee bend (jointed), splay. */
	private static void legs(HumanoidModel<?> m, float k, float thighR, float thighL, float kneeR, float kneeL, float splay) {
		SpiderPerch.set(m.rightLeg, k, new Vector3f(m.rightLeg.x, m.rightLeg.y, m.rightLeg.z), thighR, 0f, splay);
		SpiderPerch.set(m.leftLeg, k, new Vector3f(m.leftLeg.x, m.leftLeg.y, m.leftLeg.z), thighL, 0f, -splay);
		JointedLimbs.setJoint(JointedLimbs.RIGHT_LEG, new Quaternionf().rotationX(kneeR * k));
		JointedLimbs.setJoint(JointedLimbs.LEFT_LEG, new Quaternionf().rotationX(kneeL * k));
	}

	/**
	 * Both arms, mirrored: pitch, yaw (+ = in across the chest), roll (+ = out to the side), elbow bend (negative =
	 * forearm forward / in).
	 */
	private static void arms(HumanoidModel<?> m, float k, float x, float yIn, float zOut, float elbow) {
		SpiderPerch.set(m.rightArm, k, new Vector3f(m.rightArm.x, m.rightArm.y, m.rightArm.z), x, -yIn, zOut);
		SpiderPerch.set(m.leftArm, k, new Vector3f(m.leftArm.x, m.leftArm.y, m.leftArm.z), x, yIn, -zOut);
		JointedLimbs.setJoint(JointedLimbs.RIGHT_ARM, new Quaternionf().rotationX(elbow * k));
		JointedLimbs.setJoint(JointedLimbs.LEFT_ARM, new Quaternionf().rotationX(elbow * k));
	}
}
