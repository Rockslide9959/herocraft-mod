package com.projecthero.mod.client.kryptonian;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.kryptonian.data.KryptonianState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.8: the Kryptonian's poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} (armour layers copy
 * it, so they follow). Driven by the synced {@link KryptonianState}, so every viewer sees the same thing.
 * <ul>
 *   <li><b>Flight</b>: as the body leans into the flight ({@link FlightPoseHelper#lean}), the main arm swings up and out
 *       ahead along the direction of travel -- one fist forward -- and the other arm lies along his side.</li>
 *   <li><b>Moves</b>: a short keyframe per move (punch, breath, clap, slam, flare, dash, throw).</li>
 * </ul>
 * Negative {@code xRot} swings an arm forward / up.
 */
public final class KryptonianPose {
	private KryptonianPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		var fist = rightMain ? model.rightArm : model.leftArm;
		var other = rightMain ? model.leftArm : model.rightArm;
		float side = rightMain ? 1f : -1f;

		if (s.flying) {
			float w = Mth.clamp((Math.abs(FlightPoseHelper.lean(player, partial)) - 10f) / 60f, 0f, 1f);
			if (w > 0f) {
				fist.xRot = Mth.lerp(w, fist.xRot, -(float) Math.PI);
				fist.yRot = Mth.lerp(w, fist.yRot, 0f);
				fist.zRot = Mth.lerp(w, fist.zRot, 0f);
				other.xRot = Mth.lerp(w, other.xRot, 0.1f);
				other.yRot = Mth.lerp(w, other.yRot, 0f);
				other.zRot = Mth.lerp(w, other.zRot, 0.08f * side);
			}
		}
		if (s.heatVision) {
			model.head.xRot -= 0.05f; // a slight squint forward
			model.hat.copyFrom(model.head);
		}
		if (s.breathing) {
			// v0.14.16: held Freeze Breath -- hands braced low and out, chin forward, for as long as it blows
			pose(model.rightArm, 1f, 0.35f, 0f, 0.25f);
			pose(model.leftArm, 1f, 0.35f, 0f, -0.25f);
			model.head.xRot -= 0.15f;
			model.hat.copyFrom(model.head);
		}

		if (s.animId == KryptonianState.ANIM_NONE || player.level() == null) {
			return;
		}
		float age = (player.level().getGameTime() - s.animStart) + partial;
		int length = length(s.animId);
		if (age < 0f || age >= length) {
			return;
		}
		// ease in over the first 3 ticks, out over the last 4
		float w = Math.min(1f, age / 3f) * Math.min(1f, (length - age) / 4f);
		switch (s.animId) {
			case KryptonianState.ANIM_PUNCH -> {
				pose(fist, w, -1.65f, 0.1f * side, 0f);
				pose(other, w, 0.5f, 0f, 0.1f * side);
			}
			case KryptonianState.ANIM_BREATH -> {
				pose(model.rightArm, w, 0.35f, 0f, 0.25f);
				pose(model.leftArm, w, 0.35f, 0f, -0.25f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot - 0.15f);
				model.hat.copyFrom(model.head);
			}
			case KryptonianState.ANIM_CLAP -> {
				float closing = Math.min(1f, age / 5f);
				pose(model.rightArm, w, -1.5f, Mth.lerp(closing, -0.9f, 0.35f), 0f);
				pose(model.leftArm, w, -1.5f, Mth.lerp(closing, 0.9f, -0.35f), 0f);
			}
			case KryptonianState.ANIM_SLAM -> {
				float down = Math.min(1f, age / 6f);
				float x = Mth.lerp(down, -2.9f, -0.6f);
				pose(model.rightArm, w, x, 0f, 0.15f);
				pose(model.leftArm, w, x, 0f, -0.15f);
			}
			case KryptonianState.ANIM_FLARE -> {
				pose(model.rightArm, w, -0.2f, 0f, 1.3f);
				pose(model.leftArm, w, -0.2f, 0f, -1.3f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.5f);
				model.hat.copyFrom(model.head);
			}
			case KryptonianState.ANIM_DASH -> {
				pose(model.rightArm, w, 0.7f, 0f, 0.2f);
				pose(model.leftArm, w, 0.7f, 0f, -0.2f);
			}
			case KryptonianState.ANIM_THROW -> {
				float fwd = Math.min(1f, age / 4f);
				pose(fist, w, Mth.lerp(fwd, -2.9f, -1.2f), 0f, 0f);
			}
			case KryptonianState.ANIM_BARRAGE -> {
				// v0.14.16: fists pumping in turn, one jab every 3 ticks
				float phase = Mth.sin(age * (float) Math.PI / 3f);
				pose(model.rightArm, w, -1.5f + 0.5f * phase, -0.1f, 0f);
				pose(model.leftArm, w, -1.5f - 0.5f * phase, 0.1f, 0f);
			}
			case KryptonianState.ANIM_METEOR -> {
				// v0.14.16: both fists driven up over the head -- through the rise and down the dive
				pose(model.rightArm, w, -2.95f, 0f, -0.12f);
				pose(model.leftArm, w, -2.95f, 0f, 0.12f);
			}
			default -> {
			}
		}
	}

	private static int length(int animId) {
		return switch (animId) {
			case KryptonianState.ANIM_PUNCH -> 9;
			case KryptonianState.ANIM_BREATH -> 30;
			case KryptonianState.ANIM_CLAP -> 12;
			case KryptonianState.ANIM_SLAM -> 12;
			case KryptonianState.ANIM_FLARE -> 44;
			case KryptonianState.ANIM_DASH -> 12;
			case KryptonianState.ANIM_THROW -> 9;
			case KryptonianState.ANIM_BARRAGE -> 26;
			case KryptonianState.ANIM_METEOR -> 120; // ends early: the impact switches to the slam pose
			default -> 0;
		};
	}

	private static void pose(net.minecraft.client.model.geom.ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
