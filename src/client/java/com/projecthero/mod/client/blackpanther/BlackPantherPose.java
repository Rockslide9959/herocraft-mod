package com.projecthero.mod.client.blackpanther;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.data.BlackPantherState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Black Panther's poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} (the suit copies it).
 * Driven by the synced state. Negative {@code xRot} swings an arm forward / up.
 */
public final class BlackPantherPose {
	private BlackPantherPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart main = rightMain ? model.rightArm : model.leftArm;
		ModelPart off = rightMain ? model.leftArm : model.rightArm;
		float side = rightMain ? 1f : -1f;

		if (s.suited && s.absorbing) {
			// fists crossed in front of the chest, bracing
			set(model.rightArm, 1f, -1.25f, -0.62f, 0f);
			set(model.leftArm, 1f, -1.25f, 0.62f, 0f);
			return;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f) {
			return;
		}
		switch (s.animId) {
			case BlackPantherState.ANIM_CLAW -> {
				int total = BlackPantherConfig.CLAW_STEP * 3 + 2;
				if (age < total) {
					int slash = Math.min(2, (int) (age / BlackPantherConfig.CLAW_STEP));
					float t = Mth.clamp((age - slash * BlackPantherConfig.CLAW_STEP) / BlackPantherConfig.CLAW_STEP, 0f, 1f);
					ModelPart arm = slash == 1 ? off : main;
					float armSide = slash == 1 ? -side : side;
					set(arm, 1f, Mth.lerp(t, -2.5f, -0.6f), Mth.lerp(t, 0.5f, -0.7f) * armSide, 0f);
				}
			}
			case BlackPantherState.ANIM_PULSE, BlackPantherState.ANIM_OVERLOAD -> {
				float len = s.animId == BlackPantherState.ANIM_PULSE ? 12f : 20f;
				if (age < len) {
					float w = Mth.sin(age / len * Mth.PI);
					set(model.rightArm, w, 0f, 0f, 1.35f);
					set(model.leftArm, w, 0f, 0f, -1.35f);
				}
			}
			case BlackPantherState.ANIM_POUNCE -> {
				if (age < 14f) {
					float w = Mth.sin(Math.min(1f, age / 14f) * Mth.PI);
					set(model.rightArm, w, -2.3f, 0f, 0.2f);
					set(model.leftArm, w, -2.3f, 0f, -0.2f);
				}
			}
			case BlackPantherState.ANIM_DROP -> {
				if (age < 30f && !player.onGround()) {
					set(model.rightArm, 1f, -2.9f, 0f, 0.1f);
					set(model.leftArm, 1f, -2.9f, 0f, -0.1f);
				}
			}
			case BlackPantherState.ANIM_THROW -> {
				if (age < 8f) {
					set(main, 1f, Mth.lerp(age / 8f, -2.7f, -0.8f), 0f, 0f);
				}
			}
			case BlackPantherState.ANIM_RETURN -> {
				if (age < 10f) {
					set(main, Mth.sin(age / 10f * Mth.PI), model.head.xRot - 1.55f, model.head.yRot, 0f);
				}
			}
			default -> {
			}
		}
	}

	private static void set(ModelPart part, float weight, float x, float y, float z) {
		part.xRot = Mth.lerp(weight, part.xRot, x);
		part.yRot = Mth.lerp(weight, part.yRot, y);
		part.zRot = Mth.lerp(weight, part.zRot, z);
	}
}
