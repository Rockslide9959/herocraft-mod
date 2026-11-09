package com.projecthero.mod.client.andy;

import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyStages;
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
		// the arm one-arm moves use: the main one, or the other when the main was gone or used up (FLAG_OFF_ARM)
		int mainArm = s.flag(AndyState.FLAG_OFF_ARM) ? Andy.otherArm(Andy.mainArm(player)) : Andy.mainArm(player);
		ModelPart main = mainArm == AndyState.RIGHT_ARM ? model.rightArm : model.leftArm;
		ModelPart off = mainArm == AndyState.RIGHT_ARM ? model.leftArm : model.rightArm;
		float side = mainArm == AndyState.RIGHT_ARM ? 1f : -1f;
		float aim = model.head.xRot - Mth.HALF_PI;
		float now = player.level().getGameTime() + partial;
		float fly = AndyMotion.flightBlend(player, partial);
		if (fly > 0.001f) {
			flight(player, s, model, fly, now);
			return;
		}
		if (stage(player, s, model, main, off, now, partial)) {
			return;
		}
		if (AndyBoneSwordRender.pose(player, s, model, now)) {
			return; // v0.17.3: the Bone Sword held, its rip / put-away and the sword moves
		}
		if (s.flag(AndyState.FLAG_PUNCH)) {
			// the stretch punch winding up: the arm drawn right back behind him, the body twisted away, a tremor of strain
			float e = Mth.clamp((now - s.ex(AndyState.X_PUNCH_START)) / 6f, 0f, 1f);
			e = e * e * (3f - 2f * e);
			float shake = Mth.sin(now * 3.1f) * 0.04f * e;
			set(main, e, 1.25f + shake, 0.35f * side, 0.42f * side);
			set(off, e, -0.7f, -0.3f * side, -0.15f * side);
			model.body.yRot += 0.38f * side * e;
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
			case AndyState.ANIM_PUNCH -> {
				if (age < 8f) {
					// the throw's follow-through: the body snaps round, the other arm swings back
					float w = Mth.sin(Math.min(1f, age / 8f) * Mth.PI);
					model.body.yRot -= 0.35f * side * w;
					set(off, w, 0.6f, 0f, -0.2f * side);
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
			case AndyState.ANIM_HEAD_CHARGE -> {
				// the Head Launch wind-up: the head tips back, shoulders pulled down and back, a building tremble
				float k = Mth.clamp(age / 15f, 0f, 1f);
				if (age < 30f) {
					float shake = 0.035f * k * Mth.sin(age * 4.1f);
					model.head.xRot = Mth.lerp(k, model.head.xRot, -0.75f) + shake;
					model.body.xRot += 0.12f * k;
					set(model.rightArm, k, 0.45f + shake, 0f, 0.28f);
					set(model.leftArm, k, 0.45f - shake, 0f, -0.28f);
				}
			}
			case AndyState.ANIM_LEG_LAUNCH -> {
				if (age < 14f) {
					// thrown forward: arms swept back along the flight
					float w = Math.min(1f, (14f - age) / 4f);
					set(model.rightArm, w, 0.9f, 0f, 0.35f);
					set(model.leftArm, w, 0.9f, 0f, -0.35f);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * v0.17.3 (user: "make the flight animation better"): the torpedo line on top of {@link AndyMotion}'s body tilt -- arms swept
	 * back and down along the body (the stumps aimed behind, so the jets push along the travel), legs trailing together with a
	 * gentle wave, the head raised to look ahead. Boosting: tighter and stiffer. Blended by {@code fly} both ways.
	 */
	private static void flight(Player player, AndyState s, HumanoidModel<?> model, float fly, float now) {
		float e = fly * fly * (3f - 2f * fly);
		boolean boost = s.flag(AndyState.FLAG_BOOST);
		// (user: "the legs are disconnected from the body") the flight is Shift + X, so he is usually sneaking: vanilla's crouch
		// drops the body 3.2 px, the head 4.2, the arms to 5.2 and pushes the hips 4 px back -- with the body straightened for
		// the torpedo that left the legs hanging off behind it. Every pivot back to the standing skeleton (blended both ways).
		pivot(model.head, e, 0f, 0f);
		pivot(model.hat, e, 0f, 0f);
		pivot(model.body, e, 0f, 0f);
		pivot(model.rightArm, e, 2f, 0f);
		pivot(model.leftArm, e, 2f, 0f);
		pivot(model.rightLeg, e, 12f, 0f);
		pivot(model.leftLeg, e, 12f, 0f);
		float wave = Mth.sin(now * (boost ? 1.9f : 1.1f));
		float tight = boost ? 0.5f : 1f;
		// the head raised to look ahead (just a head: only a little, so the neck stays aimed behind)
		float lift = s.headOnly(now) ? -0.45f : -1.2f;
		set(model.head, e, lift, 0f, 0f);
		set(model.hat, e, lift, 0f, 0f);
		// arms straight back along the sides (the body is already lying along the flight: hanging = trailing), the stumps
		// pointing behind, just off the hips so the jets clear the legs
		set(model.rightArm, e, 0.06f + 0.03f * wave, 0f, 0.13f * tight + 0.02f * wave);
		set(model.leftArm, e, 0.06f - 0.03f * wave, 0f, -0.13f * tight - 0.02f * wave);
		// legs together, trailing straight, a gentle scissor wave from the hips
		set(model.rightLeg, e, 0.04f + 0.07f * wave * tight, 0f, 0.015f);
		set(model.leftLeg, e, 0.04f - 0.07f * wave * tight, 0f, -0.015f);
		model.body.xRot = Mth.lerp(e, model.body.xRot, 0f);
		model.body.yRot = Mth.lerp(e, model.body.yRot, 0f);
		model.body.zRot = Mth.lerp(e, model.body.zRot, 0f);
	}

	/** A part's pivot (model px) eased toward the standing skeleton's. */
	private static void pivot(ModelPart part, float weight, float y, float z) {
		part.y = Mth.lerp(weight, part.y, y);
		part.z = Mth.lerp(weight, part.z, z);
	}

	/**
	 * The head-only regrowth stages' limbs ({@link AndyStages}); true if the stage owns the pose (nothing else to add). The body
	 * placing (lowering, rolling, hopping) is {@link AndyMotion}'s.
	 */
	private static boolean stage(Player player, AndyState s, HumanoidModel<?> model, ModelPart main, ModelPart off, float now, float partial) {
		int stage = AndyStages.stage(s, now);
		float swing = player.walkAnimation.position(partial);
		float amount = Math.min(1f, player.walkAnimation.speed(partial) * 2.2f);
		switch (stage) {
			case AndyStages.ROLL -> {
				// a head on its own looks nowhere: it just rolls
				set(model.head, 1f, 0f, 0f, 0f);
				set(model.hat, 1f, 0f, 0f, 0f);
				return true;
			}
			case AndyStages.DRAG -> {
				// lying flat (vanilla's crawl turns the body face down): the main arm reaches ahead, plants, and drags him on
				int mainPart = main == model.rightArm ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM;
				ModelPart arm = s.has(mainPart, now) || AndyStages.growing(s, mainPart, now) ? main : off;
				float sd = arm == model.rightArm ? 1f : -1f;
				float c = (swing * 0.16f) % 1f;
				float x, z;
				if (amount < 0.05f) {
					x = -2.7f;
					z = 0.25f * sd;
				} else if (c < 0.58f) {
					// the pull: the hand flat on the ground sweeps from ahead to under the chest
					float k = c / 0.58f;
					x = Mth.lerp(k * k * (3f - 2f * k), -3.0f, -1.25f);
					z = 0.18f * sd;
				} else {
					// the reach: lifted out to the side and thrown forward again
					float k = (c - 0.58f) / 0.42f;
					x = Mth.lerp(k, -1.25f, -3.0f);
					z = (0.18f + 0.7f * Mth.sin(k * Mth.PI)) * sd;
				}
				set(arm, 1f, x, 0f, z);
				ModelPart other = arm == main ? off : main;
				set(other, 1f, -2.3f, 0f, -0.35f * sd); // the other one (if it is there yet) trails along
				return true;
			}
			case AndyStages.ARM_WALK -> {
				// on his hands: the arms are his legs now, stepping in turn
				float step = Mth.sin(swing * 0.6662f) * 0.55f * amount;
				set(model.rightArm, 1f, step, 0f, 0.06f);
				set(model.leftArm, 1f, -step, 0f, -0.06f);
				return true;
			}
			case AndyStages.NUB_HOP, AndyStages.LEG_HOP -> {
				// arms out for balance, flapping a little with each hop
				float hop = Mth.sin(swing * (stage == AndyStages.NUB_HOP ? 0.42f : 0.5f) * 2f) * 0.18f * amount;
				set(model.rightArm, 1f, -0.25f, 0f, 0.55f + hop);
				set(model.leftArm, 1f, -0.25f, 0f, -0.55f - hop);
				set(model.rightLeg, 1f, 0.08f * Mth.sin(swing) * amount, 0f, 0.04f);
				set(model.leftLeg, 1f, -0.08f * Mth.sin(swing) * amount, 0f, -0.04f);
				return true;
			}
			case AndyStages.LIMP -> {
				// the short leg barely swings
				int leg = AndyMotion.growingLeg(s, now);
				if (leg >= 0) {
					ModelPart lp = leg == AndyState.RIGHT_LEG ? model.rightLeg : model.leftLeg;
					float len = AndyMotion.legLength(s, leg, now);
					lp.xRot *= 0.3f + 0.7f * len / 12f;
				}
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	private static void set(ModelPart part, float weight, float x, float y, float z) {
		part.xRot = Mth.lerp(weight, part.xRot, x);
		part.yRot = Mth.lerp(weight, part.yRot, y);
		part.zRot = Mth.lerp(weight, part.zRot, z);
	}
}
