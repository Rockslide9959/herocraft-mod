package com.projecthero.mod.client.ghostrider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ghostrider.GhostRiderConfig;
import com.projecthero.mod.ghostrider.data.GhostRiderState;
import com.projecthero.mod.ghostrider.entity.HellCycleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.5: Ghost Rider's keyframed poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} (his layer
 * copies it). Driven by the synced {@link GhostRiderState}, so every viewer sees the same thing. Negative {@code xRot}
 * swings an arm forward / up.
 * <ul>
 *   <li><b>The change</b>: hunched, fists clenched out to the sides, then the head thrown back in a scream as the skull
 *       ignites. Reverting: doubled over, hands to the head.</li>
 *   <li><b>Hellfire Blast</b> (held): the main arm thrust along the look, the other braced.</li>
 *   <li><b>On the Hell Cycle</b>: both hands forward on the handlebars.</li>
 *   <li><b>Chain Spin</b>: the main arm overhead, whirling.</li>
 *   <li><b>Penance Stare</b>: head forward and down at the target, arms open at the sides.</li>
 *   <li><b>Hellfire Rush</b>: leaning into the charge, arms swept back.</li>
 *   <li>Short keyframes for the wave, whip, jump, mark, eruption, shield, wrath and summon.</li>
 * </ul>
 */
public final class GhostRiderPose {
	private GhostRiderPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		GhostRiderState s = player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart fist = rightMain ? model.rightArm : model.leftArm;
		ModelPart other = rightMain ? model.leftArm : model.rightArm;
		float side = rightMain ? 1f : -1f;

		if (s.transformed) {
			if (player.getVehicle() instanceof HellCycleEntity) {
				// hands on the bars
				pose(model.rightArm, 1f, -1.25f, 0.18f, 0f);
				pose(model.leftArm, 1f, -1.25f, -0.18f, 0f);
				pose(model.rightLeg, 1f, -1.2f, 0.25f, 0.15f);
				pose(model.leftLeg, 1f, -1.2f, -0.25f, -0.15f);
			}
			if (s.spinUntil > now) {
				float a = (now + partial) * 0.9f;
				fist.xRot = -2.9f;
				fist.yRot = 0f;
				fist.zRot = 0.35f * Mth.sin(a) * side;
				pose(other, 1f, -0.4f, 0f, 0.5f * side);
			}
			if (s.blasting) {
				fist.xRot = model.head.xRot - (float) Math.PI / 2f;
				fist.yRot = model.head.yRot;
				fist.zRot = 0f;
				pose(other, 1f, -0.5f, 0.4f * side, 0.2f * side);
			}
			if (s.stareTarget != -1) {
				pose(model.rightArm, 1f, -0.2f, 0f, 0.45f);
				pose(model.leftArm, 1f, -0.2f, 0f, -0.45f);
			}
			if (s.rushing) {
				pose(model.rightArm, 1f, 0.9f, 0f, 0.25f);
				pose(model.leftArm, 1f, 0.9f, 0f, -0.25f);
				model.body.xRot = 0.45f;
				model.head.xRot = Mth.lerp(0.6f, model.head.xRot, -0.3f);
				model.hat.copyFrom(model.head);
			}
		}

		if (s.animId == GhostRiderState.ANIM_NONE) {
			return;
		}
		float age = (now - s.animStart) + partial;
		int length = length(s.animId);
		if (age < 0f || age >= length) {
			return;
		}
		float w = Math.min(1f, age / 3f) * Math.min(1f, (length - age) / 4f);
		switch (s.animId) {
			case GhostRiderState.ANIM_TRANSFORM -> {
				float p = age / GhostRiderConfig.TRANSFORM_TICKS;
				float burn = Mth.clamp((p - GhostRiderRender.BURN_START) / 0.06f, 0f, 1f) * (1f - Mth.clamp((p - GhostRiderRender.ERUPT) / 0.04f, 0f, 1f));
				float scream = Mth.clamp((p - GhostRiderRender.ERUPT) / 0.05f, 0f, 1f);
				float shake = 0.06f * Mth.sin(age * 2.3f) * (1f - scream);
				// hunched, fists clenched low while the clothes wrap round him
				float rx = -0.5f, rz = 0.55f;
				// both hands up to the face while it burns
				rx = Mth.lerp(burn, rx, -2.3f);
				rz = Mth.lerp(burn, rz, -0.35f);
				// flung wide, head thrown back, as the skull erupts
				rx = Mth.lerp(scream, rx, -0.3f);
				rz = Mth.lerp(scream, rz, 1.25f);
				pose(model.rightArm, w, rx, 0f, rz + shake);
				pose(model.leftArm, w, rx, 0f, -rz - shake);
				model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(scream, Mth.lerp(burn, 0.45f, 0.6f), -0.7f));
				model.hat.copyFrom(model.head);
				model.body.xRot = Mth.lerp(w, model.body.xRot, Mth.lerp(scream, 0.3f, -0.1f));
			}
			case GhostRiderState.ANIM_REVERT -> {
				// doubled over, hands at the head
				pose(model.rightArm, w, -2.6f, 0f, -0.45f);
				pose(model.leftArm, w, -2.6f, 0f, 0.45f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.5f);
				model.hat.copyFrom(model.head);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35f);
			}
			case GhostRiderState.ANIM_WAVE -> {
				// the main hand sweeps down and forward along the ground
				float t = Mth.clamp(age / 6f, 0f, 1f);
				pose(fist, w, Mth.lerp(t, -2.6f, -0.6f), 0f, 0.15f * side);
				pose(other, w, -0.3f, 0f, 0.4f * side);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3f * t);
			}
			case GhostRiderState.ANIM_WHIP -> {
				// wound back overhead, then lashed forward
				float t = Mth.clamp(age / 4f, 0f, 1f);
				pose(fist, w, Mth.lerp(t, -3.0f, -1.3f), Mth.lerp(t, 0.6f, -0.2f) * side, 0f);
				pose(other, w, -0.2f, 0f, 0.3f * side);
			}
			case GhostRiderState.ANIM_CYCLE_JUMP -> {
				pose(model.rightArm, w, -2.2f, 0.2f, 0.2f);
				pose(model.leftArm, w, -2.2f, -0.2f, -0.2f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.4f);
				model.hat.copyFrom(model.head);
			}
			case GhostRiderState.ANIM_MARK -> {
				// the accusing finger
				pose(fist, w, model.head.xRot - 1.55f, model.head.yRot, 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot + 0.1f);
				model.hat.copyFrom(model.head);
			}
			case GhostRiderState.ANIM_STARE -> {
				model.head.xRot = Mth.lerp(w * 0.5f, model.head.xRot, model.head.xRot + 0.15f);
				model.hat.copyFrom(model.head);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.15f);
			}
			case GhostRiderState.ANIM_RUSH -> {
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.45f);
			}
			case GhostRiderState.ANIM_ERUPTION -> {
				// both fists up, then slammed into the ground
				float t = Mth.clamp((age - 2f) / 5f, 0f, 1f);
				pose(model.rightArm, w, Mth.lerp(t, -3.0f, -0.9f), 0f, Mth.lerp(t, -0.15f, 0.2f));
				pose(model.leftArm, w, Mth.lerp(t, -3.0f, -0.9f), 0f, Mth.lerp(t, 0.15f, -0.2f));
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.55f * t);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.35f * t);
				model.hat.copyFrom(model.head);
			}
			case GhostRiderState.ANIM_SHIELD -> {
				pose(model.rightArm, w, -0.35f, 0f, 1.2f);
				pose(model.leftArm, w, -0.35f, 0f, -1.2f);
			}
			case GhostRiderState.ANIM_WRATH -> {
				pose(model.rightArm, w, -2.7f, 0f, -0.35f);
				pose(model.leftArm, w, -2.7f, 0f, 0.35f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.6f);
				model.hat.copyFrom(model.head);
			}
			case GhostRiderState.ANIM_SUMMON -> {
				pose(fist, w, -2.9f, 0f, 0.2f * side);
			}
			default -> {
			}
		}
	}

	private static int length(int animId) {
		return switch (animId) {
			case GhostRiderState.ANIM_TRANSFORM -> GhostRiderConfig.TRANSFORM_TICKS + 6;
			case GhostRiderState.ANIM_REVERT -> GhostRiderConfig.REVERT_TICKS;
			case GhostRiderState.ANIM_WAVE -> 14;
			case GhostRiderState.ANIM_WHIP -> 10;
			case GhostRiderState.ANIM_CYCLE_JUMP -> 20;
			case GhostRiderState.ANIM_MARK -> 18;
			case GhostRiderState.ANIM_STARE -> GhostRiderConfig.STARE_TICKS + 4;
			case GhostRiderState.ANIM_RUSH -> 14;
			case GhostRiderState.ANIM_ERUPTION -> 22;
			case GhostRiderState.ANIM_SHIELD -> 14;
			case GhostRiderState.ANIM_WRATH -> 24;
			case GhostRiderState.ANIM_SUMMON -> 14;
			default -> 0;
		};
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
