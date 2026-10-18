package com.projecthero.mod.client.quirk.zerogravity;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.zerogravity.ZeroGravity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Zero Gravity's keyframed poses, set on the humanoid model at the end of {@code setupAnim}, driven by the
 * synced {@code animId / animStart} (ids 140..159) and her timers so every viewer sees the same thing. Negative
 * {@code xRot} swings a limb forward / up; positive {@code zRot} lifts the right arm out to the side.
 * <ul>
 *   <li><b>Zero G Self</b>: weightless -- arms drifting out from the sides, legs loose, a slow bob;</li>
 *   <li><b>Float Leap</b>: crouched spring, then arms swept up;</li>
 *   <li><b>Float Touch</b>: the right palm thrust out, fingers spread; <b>Float Fling</b>: the palm, then a sweep;</li>
 *   <li><b>Release</b>: both hands brought together before her chest, fingertips pressed (her signature);</li>
 *   <li><b>Meteor Slam</b>: both arms raised, then hauled down;</li>
 *   <li><b>Meteor Shower</b>: arms rising, palms up, then one arm thrown at the target;
 *       <b>Debris Shield</b>: arms circling out;</li>
 *   <li><b>Comet</b>: both palms slapped to the ground; <b>Uravity Meteor</b>: a heave up, then a two-handed throw down;</li>
 *   <li><b>Gunhead Throw</b>: grab and over-the-hip throw; <b>Gunhead Combo</b>: left jab, right jab, a palm strike.</li>
 * </ul>
 */
public final class ZeroGravityPose {
	private ZeroGravityPose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case ZeroGravity.ANIM_TOUCH -> 8;
			case ZeroGravity.ANIM_FLING -> 12;
			case ZeroGravity.ANIM_RELEASE -> 16;
			case ZeroGravity.ANIM_SLAM -> 14;
			case ZeroGravity.ANIM_SHOWER -> 30;
			case ZeroGravity.ANIM_SHIELD -> 16;
			case ZeroGravity.ANIM_SELF -> 10;
			case ZeroGravity.ANIM_LEAP -> 14;
			case ZeroGravity.ANIM_COMET -> 16;
			case ZeroGravity.ANIM_METEOR -> 34;
			case ZeroGravity.ANIM_THROW -> 12;
			case ZeroGravity.ANIM_COMBO -> 16;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		QuirkState s = Quirks.peek(player);
		if (s == null || s.animId < 140 || s.animId > 159 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!ZeroGravity.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float ageTicks = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		boolean airborne = !player.onGround();
		// Zero G Self: weightless, the limbs drifting
		if (ZeroGravity.selfFloating(player) && airborne) {
			float b = Mth.sin(ageTicks * 0.09f);
			float c = Mth.cos(ageTicks * 0.07f);
			pose(r, 0.85f, -0.35f + 0.15f * b, 0f, 0.75f + 0.12f * c);
			pose(l, 0.85f, -0.35f - 0.15f * b, 0f, -0.75f - 0.12f * c);
			model.rightLeg.xRot = Mth.lerp(0.8f, model.rightLeg.xRot, -0.35f + 0.12f * c);
			model.leftLeg.xRot = Mth.lerp(0.8f, model.leftLeg.xRot, 0.15f - 0.12f * c);
			model.rightLeg.zRot = Mth.lerp(0.8f, model.rightLeg.zRot, 0.12f);
			model.leftLeg.zRot = Mth.lerp(0.8f, model.leftLeg.zRot, -0.12f);
			model.body.xRot = Mth.lerp(0.6f, model.body.xRot, 0.05f * b);
		}
		// the leap: arms up, legs tucked
		if (ZeroGravity.leaping(player) && airborne) {
			pose(r, 0.8f, -2.6f, 0f, 0.35f);
			pose(l, 0.8f, -2.6f, 0f, -0.35f);
			model.rightLeg.xRot = Mth.lerp(0.7f, model.rightLeg.xRot, -0.6f);
			model.leftLeg.xRot = Mth.lerp(0.7f, model.leftLeg.xRot, 0.3f);
		}
		// fingertips held together while she floats things (the "ready to release" hands), only standing still
		float[] p = playing(player, partial);
		if (p == null) {
			model.hat.copyFrom(model.head);
			return;
		}
		int id = (int) p[0];
		float age = p[1];
		int len = length(id);
		float w = Math.min(1f, age / 2f) * Math.min(1f, (len - age) / 3f);
		switch (id) {
			case ZeroGravity.ANIM_TOUCH -> {
				// the right palm thrust out, the body turning into it
				float t = ease(Mth.clamp(age / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.6f, -1.55f) + model.head.xRot * 0.5f, Mth.lerp(t, 0.3f, -0.1f), 0f);
				pose(l, w, -0.3f, 0f, -0.3f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.25f, -0.2f));
			}
			case ZeroGravity.ANIM_FLING -> {
				// the touch, then a sweeping push up and out
				float t = ease(Mth.clamp((age - 3f) / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -1.5f, -2.3f), Mth.lerp(t, -0.1f, -0.5f), Mth.lerp(t, 0f, 0.3f));
				pose(l, w, -0.4f, 0f, -0.4f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, -0.2f, -0.45f));
			}
			case ZeroGravity.ANIM_RELEASE -> {
				// both hands before the chest, the five fingertips of each pressed together
				float t = ease(Mth.clamp(age / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.4f, -1.25f), Mth.lerp(t, 0f, -0.62f), 0f);
				pose(l, w, Mth.lerp(t, -0.4f, -1.25f), Mth.lerp(t, 0f, 0.62f), 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.15f);
			}
			case ZeroGravity.ANIM_SLAM -> {
				// arms up, then hauled down hard
				float t = ease(Mth.clamp((age - 3f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -2.8f, -0.2f), 0f, 0.2f);
				pose(l, w, Mth.lerp(t, -2.8f, -0.2f), 0f, -0.2f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35f * t);
			}
			case ZeroGravity.ANIM_SHOWER -> {
				// palms up, the arms rising with the rubble, then the right arm thrown at the target
				if (age < 18f) {
					float t = ease(Mth.clamp(age / 10f, 0f, 1f));
					pose(r, w, Mth.lerp(t, -0.5f, -2.0f), 0f, 0.55f);
					pose(l, w, Mth.lerp(t, -0.5f, -2.0f), 0f, -0.55f);
					model.head.xRot = Mth.lerp(w, model.head.xRot, -0.35f * t);
				} else {
					float t = ease(Mth.clamp((age - 18f) / 4f, 0f, 1f));
					pose(r, w, Mth.lerp(t, -2.4f, -1.45f) + model.head.xRot * 0.5f, 0f, 0.1f);
					pose(l, w, -0.5f, 0f, -0.5f);
				}
			}
			case ZeroGravity.ANIM_SHIELD -> {
				// the arms circle out as the rubble begins to orbit
				float a = Mth.clamp(age / 12f, 0f, 1f) * (float) Math.PI * 2f;
				pose(r, w, -1.3f + 0.4f * Mth.sin(a), 0.4f * Mth.cos(a), 0.6f);
				pose(l, w, -1.3f + 0.4f * Mth.sin(a + (float) Math.PI), -0.4f * Mth.cos(a), -0.6f);
			}
			case ZeroGravity.ANIM_SELF -> {
				// a tap of the fingertips to her own chest
				pose(r, w, -1.0f, -0.7f, 0f);
				pose(l, w, -1.0f, 0.7f, 0f);
			}
			case ZeroGravity.ANIM_LEAP -> {
				// the crouched spring (the timer pose above carries on in the air)
				float t = Mth.clamp(age / 4f, 0f, 1f);
				pose(r, w, Mth.lerp(t, 0.6f, -2.6f), 0f, 0.3f);
				pose(l, w, Mth.lerp(t, 0.6f, -2.6f), 0f, -0.3f);
			}
			case ZeroGravity.ANIM_COMET -> {
				// down on one knee, both palms to the ground
				float t = ease(Mth.clamp(age / 4f, 0f, 1f));
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.65f * t);
				pose(r, w, Mth.lerp(t, -2.4f, -0.9f), 0f, 0.15f);
				pose(l, w, Mth.lerp(t, -2.4f, -0.9f), 0f, -0.15f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.3f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -1.1f * t);
				model.rightLeg.z -= 2.0f * w * t;
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.5f * t);
				model.leftLeg.z += 1.5f * w * t;
			}
			case ZeroGravity.ANIM_METEOR -> {
				// hands to the ground, a heave up over her head, then a two-handed throw down at the target
				if (age < 8f) {
					float t = ease(Mth.clamp(age / 4f, 0f, 1f));
					model.body.xRot = Mth.lerp(w, model.body.xRot, 0.5f * t);
					pose(r, w, -0.9f, 0f, 0.2f);
					pose(l, w, -0.9f, 0f, -0.2f);
				} else if (age < 26f) {
					float t = ease(Mth.clamp((age - 8f) / 6f, 0f, 1f));
					pose(r, w, Mth.lerp(t, -0.9f, -2.9f), 0f, 0.25f);
					pose(l, w, Mth.lerp(t, -0.9f, -2.9f), 0f, -0.25f);
					model.body.xRot = Mth.lerp(w, model.body.xRot, -0.15f * t);
					model.head.xRot = Mth.lerp(w, model.head.xRot, -0.5f * t);
				} else {
					float t = ease(Mth.clamp((age - 26f) / 3f, 0f, 1f));
					pose(r, w, Mth.lerp(t, -2.9f, -1.2f), 0f, 0.2f);
					pose(l, w, Mth.lerp(t, -2.9f, -1.2f), 0f, -0.2f);
					model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3f * t);
				}
			}
			case ZeroGravity.ANIM_THROW -> {
				// the grab (both hands out), then a turn and an over-the-hip throw
				float t = ease(Mth.clamp((age - 3f) / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -1.5f, -0.6f), Mth.lerp(t, -0.2f, 0.6f), 0f);
				pose(l, w, Mth.lerp(t, -1.5f, -2.2f), Mth.lerp(t, 0.2f, 0.5f), 0f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0f, 0.7f));
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.25f * t);
			}
			case ZeroGravity.ANIM_COMBO -> {
				// left jab, right jab, a palm strike
				int beat = Math.min(2, (int) (age / 5f));
				float t = Mth.sin(Mth.clamp((age - beat * 5f) / 5f, 0f, 1f) * (float) Math.PI);
				if (beat == 1) {
					pose(r, w, -0.6f - 0.95f * t, 0f, 0f);
					pose(l, w, -1.2f, 0.3f, 0f);
				} else if (beat == 0) {
					pose(l, w, -0.6f - 0.95f * t, 0f, 0f);
					pose(r, w, -1.2f, -0.3f, 0f);
				} else {
					pose(r, w, -0.6f - 1.0f * t, -0.2f, 0f);
					pose(l, w, -0.6f - 1.0f * t, 0.2f, 0f);
				}
				model.body.yRot = Mth.lerp(w, model.body.yRot, beat == 0 ? 0.3f * t : beat == 1 ? -0.3f * t : 0f);
			}
			default -> {
			}
		}
		model.hat.copyFrom(model.head);
	}

	private static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
