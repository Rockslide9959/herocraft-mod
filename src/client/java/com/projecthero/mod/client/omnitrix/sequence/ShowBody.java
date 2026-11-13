package com.projecthero.mod.client.omnitrix.sequence;

import com.projecthero.mod.client.HipBend;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * v0.17.3 touch-up (user: "make sure their limbs don't go weird or detach from the body"): turning the torso without breaking the
 * body. Setting {@code body.xRot / yRot / zRot} on its own swings the torso about the neck and leaves the shoulders and the hips where
 * they were, so the arms float off the shoulders and the torso slides off the legs. These keep everything joined:
 * <ul>
 *   <li>{@link #lean}: forward / back about the hips ({@link HipBend}: the neck, head and shoulders follow).</li>
 *   <li>{@link #twist}: a turn at the waist; the shoulders swing round with the chest and the arms turn with it.</li>
 *   <li>{@link #tilt}: a sideways tilt about the hips; the neck, head and shoulders follow and the arms roll with the chest.</li>
 * </ul>
 * Call {@link #reset} first (vanilla rest pivots), then {@link #tilt} / {@link #twist}, then {@link #lean}, then the limbs' own
 * rotations, then {@link #layers}. Used by the Cannonbolt, Wildvine, Upgrade and Grey Matter shows.
 */
final class ShowBody {
	private static final float TORSO = 12f, SHOULDER_Y = 2f, SHOULDER_X = 5f, HIP_X = 1.9f;

	private ShowBody() {
	}

	/** The rest pivots of a standing player, with the body {@code drop} px lower (a crouch), and every part's rotation zeroed. */
	static void reset(HumanoidModel<?> m, float drop) {
		for (ModelPart part : new ModelPart[] { m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg }) {
			part.xRot = part.yRot = part.zRot = 0f;
			part.z = 0f;
		}
		m.body.x = m.head.x = 0f;
		m.body.y = m.head.y = drop;
		m.rightArm.x = -SHOULDER_X;
		m.leftArm.x = SHOULDER_X;
		m.rightArm.y = m.leftArm.y = SHOULDER_Y + drop;
		m.rightLeg.x = -HIP_X;
		m.leftLeg.x = HIP_X;
		m.rightLeg.y = m.leftLeg.y = TORSO + drop;
	}

	/** Leans the torso forward (+) or back (-) about the hips. */
	static void lean(HumanoidModel<?> m, float pitch) {
		HipBend.pitch(m, 1f, pitch);
	}

	/** Turns the chest (radians, + toward the player's left as yaw does) at the waist; the shoulders and arms turn with it. */
	static void twist(HumanoidModel<?> m, float yaw) {
		float c = Mth.cos(yaw), s = Mth.sin(yaw);
		m.body.yRot += yaw;
		m.head.yRot += yaw;
		for (ModelPart arm : new ModelPart[] { m.rightArm, m.leftArm }) {
			float dx = arm.x - m.body.x, dz = arm.z - m.body.z;
			arm.x = m.body.x + dx * c + dz * s;
			arm.z = m.body.z - dx * s + dz * c;
			arm.yRot += yaw;
		}
	}

	/** Tilts the torso sideways (radians, as {@code zRot}) about the middle of the hips; neck, head and shoulders follow. */
	static void tilt(HumanoidModel<?> m, float roll) {
		float c = Mth.cos(roll), s = Mth.sin(roll);
		float hx = m.body.x, hy = m.body.y + TORSO;
		// the neck: 12 px up the tilted torso from the hips
		float nx = hx + TORSO * s, ny = hy - TORSO * c;
		float dHeadX = m.head.x - m.body.x, dHeadY = m.head.y - m.body.y;
		m.body.x = nx;
		m.body.y = ny;
		m.body.zRot += roll;
		m.head.x = nx + dHeadX;
		m.head.y = ny + dHeadY;
		m.head.zRot += roll;
		for (int side = -1; side <= 1; side += 2) {
			ModelPart arm = side < 0 ? m.rightArm : m.leftArm;
			float ox = side * SHOULDER_X, oy = SHOULDER_Y;
			arm.x = nx + ox * c - oy * s;
			arm.y = ny + ox * s + oy * c;
			arm.zRot += roll;
		}
	}

	/** Copies the sleeves, pants, jacket and hat onto their parts (after posing). */
	static void layers(HumanoidModel<?> m) {
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.rightPants.copyFrom(m.rightLeg);
			pm.leftPants.copyFrom(m.leftLeg);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}

	/** The pose track's spare channels, used here as the waist twist and the sideways tilt ({@link FourArmsShow.Track}). */
	static final int TWIST = FourArmsShow.SPLIT, TILT = FourArmsShow.QR;

	/** A new keyframed pose track ({@link FourArmsShow.Track}: Catmull-Rom through the keys, holds, cuts). */
	static FourArmsShow.Track track() {
		return new FourArmsShow.Track();
	}

	/**
	 * Poses the whole body from one sample of a pose track: rest pivots dropped by {@code DROP}, then the tilt, twist and lean, the
	 * head, the shoulders' shrug, the limbs' rotations and their elbows and knees (bent only while {@code bend}). Leaves the layers.
	 */
	static void apply(HumanoidModel<?> m, float[] k, boolean bend) {
		reset(m, k[FourArmsShow.DROP]);
		tilt(m, k[TILT]);
		twist(m, k[TWIST]);
		lean(m, k[FourArmsShow.LEAN]);
		m.head.xRot += k[FourArmsShow.HX];
		m.head.yRot += k[FourArmsShow.HY];
		m.head.zRot += k[FourArmsShow.HZ];
		m.rightArm.y += k[FourArmsShow.SHRUG];
		m.leftArm.y += k[FourArmsShow.SHRUG];
		limb(m.rightArm, k, FourArmsShow.RA);
		limb(m.leftArm, k, FourArmsShow.LA);
		limb(m.rightLeg, k, FourArmsShow.RL);
		limb(m.leftLeg, k, FourArmsShow.LL);
		if (bend) {
			bend(k[FourArmsShow.RA + 3], k[FourArmsShow.RA + 4], k[FourArmsShow.LA + 3], k[FourArmsShow.LA + 4], k[FourArmsShow.RL + 3],
					k[FourArmsShow.RL + 4], k[FourArmsShow.LL + 3], k[FourArmsShow.LL + 4]);
		}
	}

	private static void limb(ModelPart part, float[] k, int ch) {
		part.xRot += k[ch];
		part.yRot += k[ch + 1];
		part.zRot += k[ch + 2];
	}

	/** Bends all four limbs: {rArm flex, twist, lArm flex, twist, rLeg knee, twist, lLeg knee, twist}. */
	static void bend(float... k) {
		BentLimbs.bend(BentLimbs.RIGHT_ARM, k[0], k[1]);
		BentLimbs.bend(BentLimbs.LEFT_ARM, k[2], k[3]);
		BentLimbs.bend(BentLimbs.RIGHT_LEG, k[4], k[5]);
		BentLimbs.bend(BentLimbs.LEFT_LEG, k[6], k[7]);
	}
}
