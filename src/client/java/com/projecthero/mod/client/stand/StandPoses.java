package com.projecthero.mod.client.stand;

import org.joml.Quaternionf;

import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.render.JointedLimbs;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import static com.projecthero.mod.stand.StandClips.AIM_L;
import static com.projecthero.mod.stand.StandClips.AIM_R;
import static com.projecthero.mod.stand.StandClips.BODY;
import static com.projecthero.mod.stand.StandClips.HEAD;
import static com.projecthero.mod.stand.StandClips.LA;
import static com.projecthero.mod.stand.StandClips.LL;
import static com.projecthero.mod.stand.StandClips.RA;
import static com.projecthero.mod.stand.StandClips.RL;

/**
 * v0.17.2: a Stand pose ({@link com.projecthero.mod.stand.StandClips}) set straight on the Stand's own model, its elbows and knees as
 * real joints ({@link JointedLimbs}) -- never through {@code setupAnim}, so no player pose mixin leaks onto it.
 */
public final class StandPoses {
	private StandPoses() {
	}

	// ---------------------------------------------------------------- onto the model

	/** The pose {@code p} on the Stand's model, with the look's {@code lookPitch} / {@code lookYaw} (radians) and its joints. */
	static void apply(PlayerModel<?> m, float[] p, float lookPitch, float lookYaw) {
		part(m.rightArm, p, RA);
		part(m.leftArm, p, LA);
		m.rightArm.xRot += p[AIM_R] * lookPitch;
		m.leftArm.xRot += p[AIM_L] * lookPitch;
		part(m.rightLeg, p, RL);
		part(m.leftLeg, p, LL);
		m.head.xRot = lookPitch + p[HEAD];
		m.head.yRot = lookYaw + p[HEAD + 1];
		// the shoulders go round with the torso (as vanilla's attack swing moves them)
		float yaw = p[BODY + 1];
		m.body.yRot = yaw;
		m.body.zRot = p[BODY + 2];
		m.rightArm.z = Mth.sin(yaw) * 5f;
		m.rightArm.x = -Mth.cos(yaw) * 5f;
		m.leftArm.z = -Mth.sin(yaw) * 5f;
		m.leftArm.x = Mth.cos(yaw) * 5f;
		m.rightArm.yRot += yaw;
		m.leftArm.yRot += yaw;
		m.head.yRot += yaw * 0.5f;
		HipBend.pitch(m, 1f, p[BODY]);
		joint(JointedLimbs.RIGHT_ARM, -p[RA + 3]);
		joint(JointedLimbs.LEFT_ARM, -p[LA + 3]);
		joint(JointedLimbs.RIGHT_LEG, p[RL + 3]);
		joint(JointedLimbs.LEFT_LEG, p[LL + 3]);
	}

	/** The elbow / knee of {@code limb} folded by {@code angle} (x radians in the limb's own frame). */
	static void joint(int limb, float angle) {
		JointedLimbs.setJoint(limb, Math.abs(angle) > 0.002f ? new Quaternionf().rotationX(angle) : null);
	}

	private static void part(ModelPart part, float[] p, int slot) {
		part.xRot = p[slot];
		part.yRot = p[slot + 1];
		part.zRot = p[slot + 2];
	}

	static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
