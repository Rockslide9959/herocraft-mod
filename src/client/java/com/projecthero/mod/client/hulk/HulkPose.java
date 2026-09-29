package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.15: Banner's side of the unwilling change -- he drops to his knees, hunched over and clutching his head, shaking,
 * while the Hulk phases onto him ({@code animation.hulk.transform_forced} holds the same pose on the Hulk model, so the
 * two bodies line up through the cross-fade). Applied to the vanilla humanoid model after its own animation, from
 * {@code HumanoidModelMixin}.
 *
 * <p>The vanilla body pivots at the neck; to lean from the hips like the Hulk rig does, the neck is moved forward and
 * down around a fixed hip point, and the head and shoulders follow it. Model space: +Y is down, -Z is forward, leg X
 * positive swings the foot back, arm X negative raises the arm forward.
 */
public final class HulkPose {
	/** Full-kneel numbers: hip drop (px), forward lean (rad), leg swing back (rad), head bow (rad). */
	private static final float DROP = 8.0f;
	private static final float LEAN = 0.38f;
	/** Hips 4 px off the ground: the legs fold back almost flat so the leg ends rest on the ground behind him (acos(4/12)). */
	private static final float LEG_BACK = 1.23f;
	private static final float HEAD_BOW = 0.6f;
	/** Arms raised to clutch the sides of the head (in the body's frame) and angled in. */
	private static final float ARM_UP = -2.55f;
	private static final float ARM_IN = 0.45f;
	/** Ticks to drop to the knees. */
	private static final float DROP_TICKS = 6.0f;

	private HulkPose() {
	}

	/** 0..1: how far into the kneel Banner is right now (0 unless the unwilling change is under way). */
	public static float kneel(Player player, float partialTick) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !Hulk.changing(s, player.level().getGameTime())) {
			return 0.0f;
		}
		float t = (player.level().getGameTime() - s.formChangedAt) + partialTick;
		float f = Math.max(0.0f, Math.min(1.0f, t / DROP_TICKS));
		// he is gone (the Hulk is solid) by the time the Hulk stands, so Banner never has to get back up
		return f * f * (3.0f - 2.0f * f);
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float k = kneel(player, partial);
		if (k <= 0.0f) {
			// vanilla never resets these (they are always 0 for it) and the model is shared between players
			m.body.z = 0.0f;
			m.head.z = 0.0f;
			m.hat.z = 0.0f;
			return;
		}
		float age = player.tickCount + partial;
		// fighting it: a shiver that gets worse as the growth takes hold
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		float grow = s == null ? 0.0f
				: Math.max(0.0f, Math.min(1.0f, ((player.level().getGameTime() - s.formChangedAt) + partial - HulkConfig.FORCED_KNEEL_TICKS)
						/ HulkConfig.FORCED_GROWTH_TICKS));
		float shake = (0.03f + 0.06f * grow) * k;
		float jx = Mth.sin(age * 2.7f) * shake;
		float jz = Mth.cos(age * 3.3f) * shake;

		float lean = LEAN * k + jx * 0.5f;
		float drop = DROP * k;
		float hipY = 12.0f + drop;
		// the body leans from the hips: the neck swings forward and down
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);
		m.body.xRot = lean;
		m.body.yRot = Mth.lerp(k, m.body.yRot, 0.0f);
		m.body.y = neckY;
		m.body.z = neckZ;

		m.head.y = neckY;
		m.head.z = neckZ;
		m.head.xRot = Mth.lerp(k, m.head.xRot, HEAD_BOW) + jz;
		m.head.yRot = Mth.lerp(k, m.head.yRot, 0.0f);
		m.head.zRot = jx;
		m.hat.copyFrom(m.head);

		// shoulders sit 2 px below the neck, in the leaning body's frame
		float shoulderY = neckY + 2.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 2.0f * Mth.sin(lean);
		m.rightArm.y = Mth.lerp(k, m.rightArm.y, shoulderY);
		m.leftArm.y = Mth.lerp(k, m.leftArm.y, shoulderY);
		m.rightArm.z = Mth.lerp(k, m.rightArm.z, shoulderZ);
		m.leftArm.z = Mth.lerp(k, m.leftArm.z, shoulderZ);
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, ARM_UP + lean) + jz;
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, ARM_UP + lean) - jz;
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0.0f);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, ARM_IN);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -ARM_IN);

		// on his knees: the legs fold back under him
		m.rightLeg.y = hipY;
		m.leftLeg.y = hipY;
		m.rightLeg.z = 0.0f;
		m.leftLeg.z = 0.0f;
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, LEG_BACK);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, LEG_BACK);
		m.rightLeg.yRot = Mth.lerp(k, m.rightLeg.yRot, 0.0f);
		m.leftLeg.yRot = Mth.lerp(k, m.leftLeg.yRot, 0.0f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, 0.08f);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -0.08f);
	}
}
