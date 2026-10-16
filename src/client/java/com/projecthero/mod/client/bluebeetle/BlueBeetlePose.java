package com.projecthero.mod.client.bluebeetle;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.5: Blue Beetle's keyframed poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} (the armour
 * layer copies it). Driven by the synced {@link BlueBeetleState}. Negative {@code xRot} swings an arm forward / up.
 */
public final class BlueBeetlePose {
	/** How far each arm turns in for the cannon so the fists meet in front of him. */
	private static final float CANNON_IN = 0.38f;

	private BlueBeetlePose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		BlueBeetleState s = player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart main = rightMain ? model.rightArm : model.leftArm;
		ModelPart off = rightMain ? model.leftArm : model.rightArm;
		float side = rightMain ? 1f : -1f;

		if (s.suited) {
			if (s.flying && !player.onGround()) {
				// arms by his sides, legs trailing (the wings do the flying)
				pose(model.rightArm, 1f, 0.35f, 0f, 0.25f);
				pose(model.leftArm, 1f, 0.35f, 0f, -0.25f);
				pose(model.rightLeg, 1f, 0.2f, 0f, 0.05f);
				pose(model.leftLeg, 1f, 0.25f, 0f, -0.05f);
			}
			if (s.fx.shield) {
				off.xRot = model.head.xRot - (float) Math.PI / 2f + 0.15f;
				off.yRot = model.head.yRot + 0.3f * side;
				off.zRot = 0f;
			}
			if (s.fx.chargeStart != 0L) {
				// v0.16.5 (user): both arms out in front, together -- the twin cannon forms over both forearms
				pose(model.rightArm, 1f, model.head.xRot - 1.55f, -CANNON_IN, 0f);
				pose(model.leftArm, 1f, model.head.xRot - 1.55f, CANNON_IN, 0f);
			}
		}

		if (s.animId == BlueBeetleState.ANIM_NONE) {
			return;
		}
		float age = (now - s.animStart) + partial;
		int length = length(s.animId);
		if (age < 0f || age >= length) {
			return;
		}
		float w = Math.min(1f, age / 2f) * Math.min(1f, (length - age) / 3f);
		switch (s.animId) {
			case BlueBeetleState.ANIM_BOND -> {
				// arched back, arms flung down and out as the scarab bites in
				float shake = 0.07f * Mth.sin(age * 2.4f);
				pose(model.rightArm, w, 0.4f + shake, 0f, 0.7f);
				pose(model.leftArm, w, 0.4f - shake, 0f, -0.7f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.6f);
				model.hat.copyFrom(model.head);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.25f);
			}
			case BlueBeetleState.ANIM_SUIT_UP, BlueBeetleState.ANIM_SUIT_DOWN -> {
				pose(model.rightArm, w, 0.1f, 0f, 0.45f);
				pose(model.leftArm, w, 0.1f, 0f, -0.45f);
			}
			case BlueBeetleState.ANIM_BLAST -> {
				main.xRot = model.head.xRot - (float) Math.PI / 2f;
				main.yRot = model.head.yRot;
				main.zRot = 0f;
			}
			case BlueBeetleState.ANIM_CANNON -> {
				if (s.fx.chargeStart == 0L) {
					// the recoil
					float t = Mth.clamp(age / 8f, 0f, 1f);
					pose(model.rightArm, w, model.head.xRot - 1.55f - 0.4f * (1f - t), -CANNON_IN, 0f);
					pose(model.leftArm, w, model.head.xRot - 1.55f - 0.4f * (1f - t), CANNON_IN, 0f);
				}
			}
			case BlueBeetleState.ANIM_BLADE -> {
				// the blade forms: the fist brought up in front, a flick out
				float t = Mth.clamp(age / 5f, 0f, 1f);
				pose(main, w, Mth.lerp(t, -1.9f, -1.2f), -0.3f * side, 0f);
			}
			case BlueBeetleState.ANIM_HAMMER -> {
				// overhead (and a little back) as the hammer forms, then the smash down in front of him
				float hit = BlueBeetleConfig.HAMMER_HIT_TICK;
				if (age < hit - 1f) {
					float t = Mth.clamp(age / (hit - 1f), 0f, 1f);
					pose(main, w, Mth.lerp(t, -1.2f, -3.0f), 0.1f * side, 0f);
					pose(off, w, -0.6f, 0f, 0.3f * side);
					model.body.xRot = Mth.lerp(w, model.body.xRot, -0.15f * t);
				} else {
					float t = Mth.clamp((age - (hit - 1f)) / 2f, 0f, 1f);
					pose(main, w, Mth.lerp(t, -3.0f, -0.55f), 0f, 0f);
					pose(off, w, 0.3f, 0f, 0.4f * side);
					model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35f * t);
				}
			}
			case BlueBeetleState.ANIM_SPIKES -> {
				// a fist raised, then slammed into the ground
				float slam = BlueBeetleConfig.SPIKE_SLAM_TICK;
				if (age < slam - 1f) {
					float t = Mth.clamp(age / (slam - 1f), 0f, 1f);
					pose(main, w, Mth.lerp(t, -0.6f, -2.8f), 0f, 0.15f * side);
				} else {
					float t = Mth.clamp((age - (slam - 1f)) / 1.5f, 0f, 1f);
					pose(main, w, Mth.lerp(t, -2.8f, -0.25f), 0f, 0.1f * side);
					model.body.xRot = Mth.lerp(w, model.body.xRot, 0.45f * t);
					pose(off, w, -0.5f, 0f, 0.5f * side);
				}
			}
			case BlueBeetleState.ANIM_GRAB -> {
				// chest out, arms back: the tentacles come out of his back
				pose(model.rightArm, w, 0.45f, 0f, 0.45f);
				pose(model.leftArm, w, 0.45f, 0f, -0.45f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.12f);
			}
			case BlueBeetleState.ANIM_PULSE -> {
				pose(model.rightArm, w, -0.3f, 0f, 1.4f);
				pose(model.leftArm, w, -0.3f, 0f, -1.4f);
			}
			case BlueBeetleState.ANIM_SWARM -> {
				pose(main, w, model.head.xRot - 1.6f, model.head.yRot, 0f);
			}
			case BlueBeetleState.ANIM_BUBBLE -> {
				pose(model.rightArm, w, model.head.xRot - 1.5f, -0.25f, 0f);
				pose(model.leftArm, w, model.head.xRot - 1.5f, 0.25f, 0f);
			}
			case BlueBeetleState.ANIM_CAMO -> {
				pose(model.rightArm, w, -0.2f, 0f, 0.3f);
				pose(model.leftArm, w, -0.2f, 0f, -0.3f);
			}
			case BlueBeetleState.ANIM_UNLEASH -> {
				pose(model.rightArm, w, -0.4f, 0f, 1.5f);
				pose(model.leftArm, w, -0.4f, 0f, -1.5f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.7f);
				model.hat.copyFrom(model.head);
			}
			default -> {
			}
		}
	}

	private static int length(int animId) {
		return switch (animId) {
			case BlueBeetleState.ANIM_BOND -> BlueBeetleConfig.BOND_TICKS;
			case BlueBeetleState.ANIM_SUIT_UP -> BlueBeetleConfig.SUIT_UP_TICKS;
			case BlueBeetleState.ANIM_SUIT_DOWN -> BlueBeetleConfig.SUIT_DOWN_TICKS;
			case BlueBeetleState.ANIM_BLAST -> 12;
			case BlueBeetleState.ANIM_CANNON -> BlueBeetleConfig.CANNON_CHARGE_TICKS + 12;
			case BlueBeetleState.ANIM_BLADE -> 10;
			case BlueBeetleState.ANIM_HAMMER -> BlueBeetleConfig.HAMMER_HIT_TICK + 8;
			case BlueBeetleState.ANIM_SPIKES -> BlueBeetleConfig.SPIKE_SLAM_TICK + 10;
			case BlueBeetleState.ANIM_GRAB -> BlueBeetleConfig.GRAB_TICKS;
			case BlueBeetleState.ANIM_PULSE -> 12;
			case BlueBeetleState.ANIM_SWARM -> 14;
			case BlueBeetleState.ANIM_BUBBLE -> 14;
			case BlueBeetleState.ANIM_CAMO -> 12;
			case BlueBeetleState.ANIM_UNLEASH -> 24;
			default -> 0;
		};
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
