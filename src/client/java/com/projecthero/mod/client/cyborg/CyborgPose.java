package com.projecthero.mod.client.cyborg;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.cyborg.CyborgConfig;
import com.projecthero.mod.cyborg.data.CyborgState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.5: Cyborg's keyframed poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} (his plates layer
 * copies it). Driven by the synced {@link CyborgState}. Negative {@code xRot} swings an arm forward / up.
 * <ul>
 *   <li><b>The conversion</b>: arms flung out, head back, shuddering as the plates spread.</li>
 *   <li><b>Sonic Cannon</b>: the cannon arm held along the look while it charges, then a recoil.</li>
 *   <li><b>Hover</b>: arms a little out from the sides, legs together.</li>
 *   <li><b>Defense Matrix</b>: the main palm pushed forward.</li>
 *   <li>Short keyframes for the burst, punch, grapple, dash, override, scan, Boom Tube, waypoint and repair.</li>
 * </ul>
 */
public final class CyborgPose {
	private CyborgPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart cannon = rightMain ? model.rightArm : model.leftArm;
		ModelPart other = rightMain ? model.leftArm : model.rightArm;
		float side = rightMain ? 1f : -1f;

		{ // always Cyborg (v0.16.5 rework)
			if (s.hovering) {
				pose(model.rightArm, 1f, 0.1f, 0f, 0.45f);
				pose(model.leftArm, 1f, 0.1f, 0f, -0.45f);
				pose(model.rightLeg, 1f, 0.05f, 0f, 0.05f);
				pose(model.leftLeg, 1f, 0.05f, 0f, -0.05f);
			}
			if (s.chargeStart != 0L) {
				cannon.xRot = model.head.xRot - (float) Math.PI / 2f;
				cannon.yRot = model.head.yRot;
				cannon.zRot = 0f;
				// the other hand steadies the cannon arm
				pose(other, 1f, -1.2f, 0.55f * side, 0f);
			}
			if (s.shieldUntil > now && s.shieldHp > 0f) {
				cannon.xRot = model.head.xRot - (float) Math.PI / 2f + 0.1f;
				cannon.yRot = model.head.yRot;
			}
		}

		if (s.animId == CyborgState.ANIM_NONE) {
			return;
		}
		float age = (now - s.animStart) + partial;
		int length = length(s.animId);
		if (age < 0f || age >= length) {
			return;
		}
		float w = Math.min(1f, age / 3f) * Math.min(1f, (length - age) / 4f);
		switch (s.animId) {
			case CyborgState.ANIM_CONVERT -> {
				float shake = 0.08f * Mth.sin(age * 2.1f);
				pose(model.rightArm, w, -0.2f + shake, 0f, 1.1f);
				pose(model.leftArm, w, -0.2f - shake, 0f, -1.1f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.5f + shake);
				model.hat.copyFrom(model.head);
			}
			case CyborgState.ANIM_PLATES_ON -> {
				pose(model.rightArm, w, 0.0f, 0f, 0.5f);
				pose(model.leftArm, w, 0.0f, 0f, -0.5f);
			}
			case CyborgState.ANIM_PLATES_OFF -> {
				pose(model.rightArm, w, -0.6f, 0f, -0.3f);
				pose(model.leftArm, w, -0.6f, 0f, 0.3f);
			}
			case CyborgState.ANIM_CANNON -> {
				// after the shot: the recoil (the charging hold is above)
				float t = Mth.clamp((age - CyborgConfig.CANNON_CHARGE_TICKS) / 6f, 0f, 1f);
				if (t > 0f && s.chargeStart == 0L) {
					cannon.xRot = model.head.xRot - (float) Math.PI / 2f - 0.35f * (1f - t);
					cannon.yRot = model.head.yRot;
				}
			}
			case CyborgState.ANIM_BURST -> {
				pose(model.rightArm, w, model.head.xRot - 1.5f, -0.2f, 0f);
				pose(model.leftArm, w, model.head.xRot - 1.5f, 0.2f, 0f);
			}
			case CyborgState.ANIM_PUNCH -> {
				float t = Mth.clamp(age / 4f, 0f, 1f);
				pose(cannon, w, Mth.lerp(t, -0.6f, model.head.xRot - 1.6f), 0f, 0f);
				pose(other, w, 0.4f, 0f, 0.2f * side);
				model.body.yRot = Mth.lerp(w, model.body.yRot, -0.35f * side * (1f - t));
			}
			case CyborgState.ANIM_GRAPPLE -> {
				pose(cannon, w, model.head.xRot - 1.5f, model.head.yRot, 0f);
			}
			case CyborgState.ANIM_DASH -> {
				pose(model.rightArm, w, 0.8f, 0f, 0.2f);
				pose(model.leftArm, w, 0.8f, 0f, -0.2f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3f);
			}
			case CyborgState.ANIM_OVERRIDE -> {
				pose(cannon, w, model.head.xRot - 1.45f, model.head.yRot, 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot + 0.05f);
			}
			case CyborgState.ANIM_SCAN -> {
				// two fingers to the red eye
				pose(cannon, w, -2.4f, -0.5f * side, -0.1f * side);
			}
			case CyborgState.ANIM_BOOM_TUBE -> {
				pose(cannon, w, -2.9f, 0f, 0.15f * side);
				pose(other, w, -0.3f, 0f, 0.5f * side);
			}
			case CyborgState.ANIM_WAYPOINT -> {
				pose(cannon, w, -0.9f, 0f, -0.2f * side);
			}
			case CyborgState.ANIM_REPAIR -> {
				pose(model.rightArm, w, -1.3f, 0f, -0.6f);
				pose(model.leftArm, w, -1.3f, 0f, 0.6f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.5f);
				model.hat.copyFrom(model.head);
			}
			default -> {
			}
		}
	}

	private static int length(int animId) {
		return switch (animId) {
			case CyborgState.ANIM_CONVERT -> CyborgConfig.CONVERT_TICKS;
			case CyborgState.ANIM_PLATES_ON -> CyborgConfig.PLATES_ON_TICKS;
			case CyborgState.ANIM_PLATES_OFF -> CyborgConfig.PLATES_OFF_TICKS;
			case CyborgState.ANIM_CANNON -> CyborgConfig.CANNON_CHARGE_TICKS + 8;
			case CyborgState.ANIM_BURST -> 12;
			case CyborgState.ANIM_PUNCH -> 10;
			case CyborgState.ANIM_GRAPPLE -> 14;
			case CyborgState.ANIM_DASH -> 12;
			case CyborgState.ANIM_OVERRIDE -> 18;
			case CyborgState.ANIM_SCAN -> 20;
			case CyborgState.ANIM_BOOM_TUBE -> 20;
			case CyborgState.ANIM_WAYPOINT -> 14;
			case CyborgState.ANIM_REPAIR -> CyborgConfig.REPAIR_TICKS;
			default -> 0;
		};
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
