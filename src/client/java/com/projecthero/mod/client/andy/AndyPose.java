package com.projecthero.mod.client.andy;

import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Andy's poses on the vanilla humanoid model, from {@code HumanoidModelMixin} (setupAnim TAIL). Driven by the synced
 * state. Negative {@code xRot} swings an arm forward / up.
 */
public final class AndyPose {
	private AndyPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		AndyState s = Andy.peek(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		int mainArm = Andy.mainArm(player);
		ModelPart main = mainArm == AndyState.RIGHT_ARM ? model.rightArm : model.leftArm;
		ModelPart off = mainArm == AndyState.RIGHT_ARM ? model.leftArm : model.rightArm;
		float side = mainArm == AndyState.RIGHT_ARM ? 1f : -1f;
		float aim = model.head.xRot - Mth.HALF_PI;
		float now = player.level().getGameTime() + partial;
		if (s.reformUntil > now) {
			// re-forming: arms out a little, still
			set(model.rightArm, 1f, 0f, 0f, 0.35f);
			set(model.leftArm, 1f, 0f, 0f, -0.35f);
			set(model.rightLeg, 1f, 0f, 0f, 0.05f);
			set(model.leftLeg, 1f, 0f, 0f, -0.05f);
			return;
		}
		if (s.flag(AndyState.FLAG_PROPELLING)) {
			// jets from the wrists: arms back and down, legs trailing together
			set(model.rightArm, 1f, 0.75f, 0f, 0.38f);
			set(model.leftArm, 1f, 0.75f, 0f, -0.38f);
			set(model.rightLeg, 1f, 0.25f, 0f, 0.04f);
			set(model.leftLeg, 1f, 0.25f, 0f, -0.04f);
			return;
		}
		if (s.flag(AndyState.FLAG_GLIDING)) {
			float spin = now * 1.2f;
			set(model.rightArm, 1f, -0.2f, 0f, 1.45f + 0.1f * Mth.sin(spin));
			set(model.leftArm, 1f, -0.2f, 0f, -1.45f - 0.1f * Mth.sin(spin));
			set(model.rightLeg, 1f, -0.9f, 0f, 0.1f);
			set(model.leftLeg, 1f, 0.5f, 0f, -0.1f);
			return;
		}
		if (s.flag(AndyState.FLAG_DEAD_LINE) || s.flag(AndyState.FLAG_DEAD_ROAD)) {
			set(main, 1f, aim, model.head.yRot, 0f);
			if (s.flag(AndyState.FLAG_DEAD_ROAD)) {
				set(off, 1f, aim + 0.1f, model.head.yRot + 0.35f * side, 0f); // the other hand braces the wrist
			}
			return;
		}
		float age = now - s.animStart;
		if (age < 0f) {
			return;
		}
		switch (s.animId) {
			case AndyState.ANIM_FINGER, AndyState.ANIM_CRIMSON, AndyState.ANIM_DIVISION -> {
				float len = s.animId == AndyState.ANIM_CRIMSON ? 14f : 8f;
				if (age < len) {
					float w = Math.min(1f, (len - age) / 4f);
					set(main, w, aim, model.head.yRot, 0f);
				}
			}
			case AndyState.ANIM_VULCAN -> {
				if (age < 24f) {
					set(main, 1f, aim + 0.05f * Mth.sin(age * 3f), model.head.yRot, 0f);
				}
			}
			case AndyState.ANIM_CRESCENT -> {
				if (age < 12f) {
					// the quick draw: from across the body out to the side in one flat cut
					float t = Mth.clamp(age / 6f, 0f, 1f);
					float e = 1f - (1f - t) * (1f - t);
					set(main, Math.min(1f, (12f - age) / 3f), -1.55f, Mth.lerp(e, 1.0f * side, -1.2f * side), 0f);
				}
			}
			case AndyState.ANIM_GREAT_CRESCENT -> {
				if (age < 16f) {
					// both hands up over the head, then down hard
					float x = age < 5f ? Mth.lerp(age / 5f, 0f, -3.0f) : Mth.lerp(Mth.clamp((age - 5f) / 4f, 0f, 1f), -3.0f, -0.35f);
					float w = Math.min(1f, (16f - age) / 3f);
					set(main, w, x, -0.25f * side, 0f);
					set(off, w, x, 0.45f * side, 0f);
				}
			}
			case AndyState.ANIM_DEAD_BLADE -> {
				if (age < 16f) {
					float t = Mth.clamp(age / 7f, 0f, 1f);
					float e = 1f - (1f - t) * (1f - t);
					float w = Math.min(1f, (16f - age) / 3f);
					set(main, w, Mth.lerp(e, -2.9f, -1.1f), Mth.lerp(e, 0.6f * side, -1.0f * side), 0f);
					set(off, w, Mth.lerp(e, -2.7f, -1.0f), Mth.lerp(e, 0.9f * side, -0.6f * side), 0f);
				}
			}
			case AndyState.ANIM_VORTEX -> {
				if (age < 8f) {
					set(off, 1f, -0.8f, 0.4f * side, 0f); // the other hand twisted it off
				}
			}
			case AndyState.ANIM_HEAD -> {
				if (age < 10f) {
					set(model.rightArm, 1f, -0.6f, 0f, 0.5f);
					set(model.leftArm, 1f, -0.6f, 0f, -0.5f);
				}
			}
			case AndyState.ANIM_HEADBUTT -> {
				if (age < 8f) {
					float w = Mth.sin(age / 8f * Mth.PI);
					model.head.xRot += 0.7f * w;
					model.body.xRot += 0.25f * w;
				}
			}
			case AndyState.ANIM_CARD -> {
				if (age < 14f) {
					float w = Mth.sin(Math.min(1f, age / 14f) * Mth.PI);
					set(main, w, -2.75f, -0.55f * side, 0f);
				}
			}
			case AndyState.ANIM_CARD_BACK -> {
				if (age < 10f) {
					float w = Mth.sin(age / 10f * Mth.PI);
					set(main, w, -2.75f, -0.55f * side, 0f);
				}
			}
			case AndyState.ANIM_QUARDEAD -> {
				if (age < 14f) {
					float w = Mth.sin(age / 14f * Mth.PI);
					set(model.rightArm, w, -0.3f, 0f, 1.3f);
					set(model.leftArm, w, -0.3f, 0f, -1.3f);
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
