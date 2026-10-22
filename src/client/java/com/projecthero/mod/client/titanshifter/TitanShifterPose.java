package com.projecthero.mod.client.titanshifter;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.titanshifter.TitanHumanKit;
import com.projecthero.mod.titanshifter.TitanMove;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.data.TitanShifterState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user, Titan revamp): the human form's Partial Shifting poses, keyframed on the vanilla humanoid model from
 * {@code HumanoidModelMixin} (driven by the synced {@code TitanShifterState}: {@code lastAction} = {@link TitanHumanKit#POSE_BASE}
 * + the move, {@code lastActionTick} = its start). Negative {@code xRot} swings an arm forward / up. Each pose winds up to
 * the move's impact tick, holds the strike, and eases out.
 * <ul>
 *   <li><b>Titan Arm</b>: the main arm drawn back, then driven straight down the look (the body twists into it).</li>
 *   <li><b>Crystal Blade</b>: the blade arm raised across the chest, then swept out flat across the front.</li>
 *   <li><b>Steam Burst</b>: both arms flung wide, head thrown back.</li>
 *   <li><b>Bite Shift</b>: the hand brought up to the mouth.</li>
 *   <li><b>Crystal Wall</b>: both palms thrust forward and down at the ground ahead.</li>
 *   <li><b>Crystal Cocoon</b>: crouched, arms crossed over the chest, head down, for as long as it lasts.</li>
 * </ul>
 */
public final class TitanShifterPose {
	private TitanShifterPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		if (s == null || !s.unlocked || player.level() == null || s.phase() != TitanPhase.HUMAN) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart main = rightMain ? model.rightArm : model.leftArm;
		ModelPart other = rightMain ? model.leftArm : model.rightArm;
		float side = rightMain ? 1f : -1f;

		if (TitanShifter.cocooned(player)) {
			float w = 1f;
			pose(model.rightArm, w, -1.3f, -0.6f, 0f);
			pose(model.leftArm, w, -1.3f, 0.6f, 0f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, 0.6f);
			model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35f);
			model.hat.copyFrom(model.head);
			return;
		}

		int id = s.lastAction - TitanHumanKit.POSE_BASE;
		if (id < 0 || id >= TitanMove.values().length) {
			return;
		}
		TitanMove move = TitanMove.values()[id];
		int length = length(move);
		float age = (now - s.lastActionTick) + partial;
		if (length <= 0 || age < 0f || age >= length) {
			return;
		}
		float hit = Math.max(1, move.impactTick);
		float w = Math.min(1f, age / 2.5f) * Math.min(1f, (length - age) / 4f);
		float t = Mth.clamp(age / hit, 0f, 1f); // 0 -> 1 through the wind-up, 1 from the impact on
		switch (move) {
			case TITAN_ARM -> {
				// drawn back to the hip, then driven down the look on the impact tick
				float x = age < hit ? Mth.lerp(t, -0.2f, 0.7f) : model.head.xRot - (float) Math.PI / 2f;
				pose(main, w, x, age < hit ? 0.3f * side : model.head.yRot, 0f);
				pose(other, w, age < hit ? -0.5f : 0.5f, 0f, 0.15f * side);
				model.body.yRot = Mth.lerp(w, model.body.yRot, age < hit ? 0.45f * side : -0.25f * side);
			}
			case CRYSTAL_BLADE -> {
				// raised across the chest, then swept out flat across the front
				float sweep = age < hit ? 0f : Mth.clamp((age - hit) / 4f, 0f, 1f);
				pose(main, w, -1.45f, Mth.lerp(sweep, -0.9f * side, 0.9f * side), 0f);
				pose(other, w, -0.3f, 0f, 0.2f * side);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(sweep, 0.3f * side, -0.35f * side));
			}
			case STEAM_BURST -> {
				float out = Mth.clamp(age / hit, 0f, 1f);
				pose(model.rightArm, w, -0.3f, 0f, Mth.lerp(out, 0.2f, 1.4f));
				pose(model.leftArm, w, -0.3f, 0f, Mth.lerp(out, -0.2f, -1.4f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.45f);
				model.hat.copyFrom(model.head);
			}
			case BITE_SHIFT -> {
				pose(main, w, -2.1f, -0.55f * side, 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.2f);
				model.hat.copyFrom(model.head);
			}
			case CRYSTAL_WALL -> {
				float push = Mth.clamp(age / hit, 0f, 1f);
				pose(model.rightArm, w, Mth.lerp(push, -2.6f, -1.1f), -0.15f, 0f);
				pose(model.leftArm, w, Mth.lerp(push, -2.6f, -1.1f), 0.15f, 0f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.25f * push);
			}
			case CRYSTAL_COCOON -> {
				pose(model.rightArm, w, -1.3f, -0.6f, 0f);
				pose(model.leftArm, w, -1.3f, 0.6f, 0f);
			}
			default -> {
			}
		}
	}

	private static int length(TitanMove move) {
		return switch (move) {
			case TITAN_ARM -> 16;
			case CRYSTAL_BLADE -> 12;
			case STEAM_BURST -> 12;
			case BITE_SHIFT -> 12;
			case CRYSTAL_WALL -> 14;
			case CRYSTAL_COCOON -> 6;
			default -> 0;
		};
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
