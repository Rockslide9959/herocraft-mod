package com.projecthero.mod.client.undead;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadStages;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Undead's poses on the vanilla humanoid model, from {@code HumanoidModelMixin} (setupAnim TAIL). Driven by the synced
 * state. Negative {@code xRot} swings an arm forward / up.
 */
public final class UndeadPose {
	private UndeadPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		UndeadHeadThrowRender.resetModel(model); // v0.17.3: the head moved into a hand for the last player drawn goes back
		UndeadState s = Undead.peek(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		// the arm one-arm moves use: the main one, or the other when the main was gone or used up (FLAG_OFF_ARM)
		int mainArm = s.flag(UndeadState.FLAG_OFF_ARM) ? Undead.otherArm(Undead.mainArm(player)) : Undead.mainArm(player);
		ModelPart main = mainArm == UndeadState.RIGHT_ARM ? model.rightArm : model.leftArm;
		ModelPart off = mainArm == UndeadState.RIGHT_ARM ? model.leftArm : model.rightArm;
		float side = mainArm == UndeadState.RIGHT_ARM ? 1f : -1f;
		float aim = model.head.xRot - Mth.HALF_PI;
		float now = player.level().getGameTime() + partial;
		float fly = UndeadMotion.flightBlend(player, partial);
		if (fly > 0.001f) {
			flight(player, s, model, fly, now);
			// v0.17.3: spraying / firing the Deadline in the air -- the arms come forward to the aim over the flight pose
			if (s.flag(UndeadState.FLAG_BLOOD_SPRAY)) {
				set(model.rightArm, 1f, aim, model.head.yRot + 0.1f, 0f);
				set(model.leftArm, 1f, aim, model.head.yRot - 0.1f, 0f);
			} else if (s.flag(UndeadState.FLAG_DEAD_LINE)) {
				set(main, 1f, aim, model.head.yRot, 0f);
			}
			return;
		}
		if (eject(player, s, model, main, off, side, aim, now)) {
			return; // v0.17.3: the ejection moves (Whirlwind, Eject Feet, Combustion Punch, Blood Spray)
		}
		if (stage(player, s, model, main, off, now, partial)) {
			return;
		}
		if (UndeadHeadThrowRender.pose(player, s, model, now)) {
			return; // v0.17.3 hold X: wrenching the head off, then holding it back to throw
		}
		if (UndeadBoneSwordRender.pose(player, s, model, now)) {
			return; // v0.17.3: the Bone Sword held, its rip / put-away and the sword moves
		}
		if (s.flag(UndeadState.FLAG_PUNCH)) {
			// the stretch punch winding up: the arm drawn right back behind him, the body twisted away, a tremor of strain
			float e = Mth.clamp((now - s.ex(UndeadState.X_PUNCH_START)) / 6f, 0f, 1f);
			e = e * e * (3f - 2f * e);
			float shake = Mth.sin(now * 3.1f) * 0.04f * e;
			set(main, e, 1.25f + shake, 0.35f * side, 0.42f * side);
			set(off, e, -0.7f, -0.3f * side, -0.15f * side);
			model.body.yRot += 0.38f * side * e;
			return;
		}
		if (s.flag(UndeadState.FLAG_DEAD_LINE) || s.flag(UndeadState.FLAG_DEAD_ROAD)) {
			set(main, 1f, aim, model.head.yRot, 0f);
			if (s.flag(UndeadState.FLAG_DEAD_ROAD)) {
				set(off, 1f, aim + 0.1f, model.head.yRot + 0.35f * side, 0f); // the other hand braces the wrist
			}
			return;
		}
		float age = now - s.animStart;
		if (age < 0f) {
			return;
		}
		switch (s.animId) {
			case UndeadState.ANIM_FINGER, UndeadState.ANIM_CRIMSON, UndeadState.ANIM_DIVISION -> {
				float len = s.animId == UndeadState.ANIM_CRIMSON ? 14f : 8f;
				if (age < len) {
					float w = Math.min(1f, (len - age) / 4f);
					set(main, w, aim, model.head.yRot, 0f);
				}
			}
			case UndeadState.ANIM_VULCAN -> {
				if (age < 24f) {
					set(main, 1f, aim + 0.05f * Mth.sin(age * 3f), model.head.yRot, 0f);
				}
			}
			case UndeadState.ANIM_CRESCENT -> {
				if (age < 12f) {
					// the quick draw: from across the body out to the side in one flat cut
					float t = Mth.clamp(age / 6f, 0f, 1f);
					float e = 1f - (1f - t) * (1f - t);
					set(main, Math.min(1f, (12f - age) / 3f), -1.55f, Mth.lerp(e, 1.0f * side, -1.2f * side), 0f);
				}
			}
			case UndeadState.ANIM_GREAT_CRESCENT -> {
				if (age < 16f) {
					// both hands up over the head, then down hard
					float x = age < 5f ? Mth.lerp(age / 5f, 0f, -3.0f) : Mth.lerp(Mth.clamp((age - 5f) / 4f, 0f, 1f), -3.0f, -0.35f);
					float w = Math.min(1f, (16f - age) / 3f);
					set(main, w, x, -0.25f * side, 0f);
					set(off, w, x, 0.45f * side, 0f);
				}
			}
			case UndeadState.ANIM_DEAD_BLADE -> {
				if (age < 16f) {
					float t = Mth.clamp(age / 7f, 0f, 1f);
					float e = 1f - (1f - t) * (1f - t);
					float w = Math.min(1f, (16f - age) / 3f);
					set(main, w, Mth.lerp(e, -2.9f, -1.1f), Mth.lerp(e, 0.6f * side, -1.0f * side), 0f);
					set(off, w, Mth.lerp(e, -2.7f, -1.0f), Mth.lerp(e, 0.9f * side, -0.6f * side), 0f);
				}
			}
			case UndeadState.ANIM_VORTEX -> {
				if (age < 8f) {
					set(off, 1f, -0.8f, 0.4f * side, 0f); // the other hand twisted it off
				}
			}
			case UndeadState.ANIM_HEAD -> {
				if (age < 10f) {
					set(model.rightArm, 1f, -0.6f, 0f, 0.5f);
					set(model.leftArm, 1f, -0.6f, 0f, -0.5f);
				}
			}
			case UndeadState.ANIM_PUNCH -> {
				if (age < 8f) {
					// the throw's follow-through: the body snaps round, the other arm swings back
					float w = Mth.sin(Math.min(1f, age / 8f) * Mth.PI);
					model.body.yRot -= 0.35f * side * w;
					set(off, w, 0.6f, 0f, -0.2f * side);
				}
			}
			case UndeadState.ANIM_HEADBUTT -> {
				if (age < 8f) {
					float w = Mth.sin(age / 8f * Mth.PI);
					model.head.xRot += 0.7f * w;
					model.body.xRot += 0.25f * w;
				}
			}
			case UndeadState.ANIM_CARD -> {
				if (age < 14f) {
					float w = Mth.sin(Math.min(1f, age / 14f) * Mth.PI);
					set(main, w, -2.75f, -0.55f * side, 0f);
				}
			}
			case UndeadState.ANIM_CARD_BACK -> {
				if (age < 10f) {
					float w = Mth.sin(age / 10f * Mth.PI);
					set(main, w, -2.75f, -0.55f * side, 0f);
				}
			}
			case UndeadState.ANIM_QUARDEAD -> {
				if (age < 14f) {
					float w = Mth.sin(age / 14f * Mth.PI);
					set(model.rightArm, w, -0.3f, 0f, 1.3f);
					set(model.leftArm, w, -0.3f, 0f, -1.3f);
				}
			}
			case UndeadState.ANIM_HEAD_CHARGE -> {
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
			case UndeadState.ANIM_LEG_LAUNCH -> {
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
	 * v0.17.3 ejection moves' poses (true if one owns the pose):
	 * <ul>
	 * <li>Blood Whirlwind: rising, the arms swing out to the sides and the legs dangle; spinning ({@link UndeadMotion} turns the
	 * body), arms straight out level with the shoulders, head tipped back a little, the legs trailing with the spin;</li>
	 * <li>Eject Feet: thrown -- arms swept back along the flight, the calf stumps trailing, easing out on the way down;</li>
	 * <li>Combustion Punch: what is left of him rockets at the target, the one arm out in front (fist leading); the punch drives it
	 * through;</li>
	 * <li>Blood Spray: both forearm stumps held out along the aim, a little apart, braced and shaking with the pressure.</li>
	 * </ul>
	 */
	private static boolean eject(Player player, UndeadState s, HumanoidModel<?> model, ModelPart main, ModelPart off, float side, float aim, float now) {
		float age = now - s.animStart;
		if (s.animId == UndeadState.ANIM_BLOOD_WHIRL && age >= 0f && age < com.projecthero.mod.undead.UndeadEjectConfig.WHIRL_TICKS + 6f) {
			float in = Mth.clamp(age / 6f, 0f, 1f);
			float outFade = Mth.clamp((com.projecthero.mod.undead.UndeadEjectConfig.WHIRL_TICKS + 6f - age) / 6f, 0f, 1f);
			float w = in * in * (3f - 2f * in) * outFade;
			float shake = Mth.sin(now * 2.7f) * 0.03f;
			set(model.rightArm, w, -0.05f + shake, 0f, 1.5f);
			set(model.leftArm, w, -0.05f - shake, 0f, -1.5f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, -0.4f);
			model.hat.xRot = model.head.xRot;
			model.head.yRot = Mth.lerp(w, model.head.yRot, 0f);
			model.hat.yRot = model.head.yRot;
			float drift = Mth.sin(now * 0.8f) * 0.08f;
			set(model.rightLeg, w, 0.18f + drift, 0f, 0.16f);
			set(model.leftLeg, w, 0.12f - drift, 0f, -0.16f);
			model.body.xRot = Mth.lerp(w, model.body.xRot, -0.08f);
			return true;
		}
		if (s.animId == UndeadState.ANIM_EJECT_FEET && age >= 0f && age < 22f) {
			float w = Math.min(1f, (22f - age) / 6f) * Mth.clamp(age / 2f + 0.3f, 0f, 1f);
			set(model.rightArm, w, 1.05f, 0f, 0.3f);
			set(model.leftArm, w, 1.05f, 0f, -0.3f);
			set(model.rightLeg, w, 0.55f, 0f, 0.06f);
			set(model.leftLeg, w, 0.35f, 0f, -0.06f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot - 0.35f);
			model.hat.xRot = model.head.xRot;
			return true;
		}
		if (s.animId == UndeadState.ANIM_COMBUSTION && age >= 0f) {
			// flying fist first (vanilla's crawl lays the body flat along the travel: up over the head is straight ahead)
			float e = Mth.clamp(age / 3f, 0f, 1f);
			float shake = Mth.sin(now * 3.3f) * 0.04f;
			set(main, e, -3.05f + shake, 0f, 0.05f * side);
			model.head.xRot = Mth.lerp(e, model.head.xRot, -0.9f);
			model.hat.xRot = model.head.xRot;
			return true;
		}
		if (s.animId == UndeadState.ANIM_COMBUSTION_PUNCH && age >= 0f && age < 14f) {
			// the punch: the fist driven through, the shoulder rolled into it, then easing off
			float hit = age < 2f ? age / 2f : Math.max(0f, 1f - (age - 2f) / 12f);
			set(main, Math.max(hit, 0.4f), -3.14f, 0.15f * side * hit, 0f);
			model.body.yRot += 0.35f * side * hit;
			return true;
		}
		if (s.flag(UndeadState.FLAG_BLOOD_SPRAY)) {
			float shake = Mth.sin(now * 3.7f) * 0.025f;
			set(model.rightArm, 1f, aim + shake, model.head.yRot + 0.12f, 0f);
			set(model.leftArm, 1f, aim - shake, model.head.yRot - 0.12f, 0f);
			return true;
		}
		return false;
	}

	/**
	 * v0.17.3 (user: "give it good animations"): the flight on top of {@link UndeadMotion}'s body tilt, blended between a hover
	 * and a torpedo by how fast he goes ({@link UndeadMotion#flightSpeed}). Hovering: upright, the forearm stumps angled down and
	 * a little out and the calf stumps hanging (the jets hold him up), a slow tread of the legs. Flying: lying along the travel,
	 * the arms swept back along the sides (the stumps aimed behind, so the jets push along the travel), the legs trailing
	 * together with a gentle scissor wave, the head raised to look ahead. Blended by {@code fly} both ways.
	 */
	private static void flight(Player player, UndeadState s, HumanoidModel<?> model, float fly, float now) {
		float e = fly * fly * (3f - 2f * fly);
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float m = UndeadMotion.flightSpeed(player, partial);
		// (user: "the legs are disconnected from the body") a sneak would have dropped the body 3.2 px, the head 4.2, the arms to
		// 5.2 and pushed the hips 4 px back -- every pivot back to the standing skeleton (blended both ways)
		pivot(model.head, e, 0f, 0f);
		pivot(model.hat, e, 0f, 0f);
		pivot(model.body, e, 0f, 0f);
		pivot(model.rightArm, e, 2f, 0f);
		pivot(model.leftArm, e, 2f, 0f);
		pivot(model.rightLeg, e, 12f, 0f);
		pivot(model.leftLeg, e, 12f, 0f);
		float wave = Mth.sin(now * Mth.lerp(m, 0.5f, 1.1f));
		// the head: level while hovering (the look is his own), raised to look ahead once he is lying along the flight
		if (s.headOnly(now)) {
			set(model.head, e, -0.45f * m, 0f, 0f);
			set(model.hat, e, -0.45f * m, 0f, 0f);
		} else if (m > 0.01f) {
			model.head.xRot = Mth.lerp(e * m, model.head.xRot, -1.2f);
			model.hat.xRot = model.head.xRot;
		}
		// arms: hovering, angled down and out (the jets straight down); flying, straight back along the sides
		float ax = Mth.lerp(m, 0.18f, 0.06f), az = Mth.lerp(m, 0.42f, 0.13f);
		set(model.rightArm, e, ax + 0.04f * wave, 0f, az + 0.03f * wave);
		set(model.leftArm, e, ax - 0.04f * wave, 0f, -az - 0.03f * wave);
		// legs: hovering, a slow tread; flying, together and trailing with a gentle scissor wave
		float tread = 0.16f * (1f - m);
		set(model.rightLeg, e, 0.04f + (0.07f * m + tread) * wave, 0f, Mth.lerp(m, 0.07f, 0.015f));
		set(model.leftLeg, e, 0.04f - (0.07f * m + tread) * wave, 0f, -Mth.lerp(m, 0.07f, 0.015f));
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
	 * The head-only regrowth stages' limbs ({@link UndeadStages}); true if the stage owns the pose (nothing else to add). The body
	 * placing (lowering, rolling, hopping) is {@link UndeadMotion}'s.
	 */
	private static boolean stage(Player player, UndeadState s, HumanoidModel<?> model, ModelPart main, ModelPart off, float now, float partial) {
		int stage = UndeadStages.stage(s, now);
		float swing = player.walkAnimation.position(partial);
		float amount = Math.min(1f, player.walkAnimation.speed(partial) * 2.2f);
		switch (stage) {
			case UndeadStages.ROLL -> {
				// a head on its own looks nowhere: it just rolls
				set(model.head, 1f, 0f, 0f, 0f);
				set(model.hat, 1f, 0f, 0f, 0f);
				return true;
			}
			case UndeadStages.CRAWL -> {
				// a belly crawl (vanilla's crawl turns the body face down): each arm reaches ahead, plants and pulls him on; with
				// both arms there or growing (they grow back together) they take turns, half a stroke apart
				int mainPart = main == model.rightArm ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
				int offPart = Undead.otherArm(mainPart);
				boolean hasMain = s.has(mainPart, now) || UndeadStages.growing(s, mainPart, now);
				boolean hasOff = s.has(offPart, now) || UndeadStages.growing(s, offPart, now);
				float c = (swing * 0.16f) % 1f;
				if (hasMain && hasOff) {
					crawlStroke(main, main == model.rightArm ? 1f : -1f, c, amount);
					crawlStroke(off, off == model.rightArm ? 1f : -1f, (c + 0.5f) % 1f, amount);
					return true;
				}
				ModelPart arm = hasMain ? main : off;
				float sd = arm == model.rightArm ? 1f : -1f;
				crawlStroke(arm, sd, c, amount);
				ModelPart other = arm == main ? off : main;
				set(other, 1f, -2.3f, 0f, -0.35f * sd); // the other one (if it is there yet) trails along
				return true;
			}
			case UndeadStages.ARM_WALK -> {
				// on his hands: the arms are his legs now, stepping in turn
				float step = Mth.sin(swing * 0.6662f) * 0.55f * amount;
				set(model.rightArm, 1f, step, 0f, 0.06f);
				set(model.leftArm, 1f, -step, 0f, -0.06f);
				return true;
			}
			case UndeadStages.SHUFFLE -> {
				// on both stumps, knees bent back, tiny alternating steps; the arms out a little to steady the waddle
				float step = Mth.sin(swing * 0.75f) * 0.22f * amount;
				set(model.rightLeg, 1f, UndeadMotion.KNEEL + step, 0f, 0.1f);
				set(model.leftLeg, 1f, UndeadMotion.KNEEL - step, 0f, -0.1f);
				float flap = Mth.sin(swing * 0.75f) * 0.12f * amount;
				set(model.rightArm, 1f, -0.35f, 0f, 0.45f + flap);
				set(model.leftArm, 1f, -0.35f, 0f, -0.45f + flap);
				return true;
			}
			case UndeadStages.WOBBLE -> {
				// standing on legs not steady yet: short steps, feet a little apart, arms held out and trembling for balance
				float steady = Math.min(1f, Math.max(0f, (Math.min(s.regrowth(UndeadState.RIGHT_LEG, now), s.regrowth(UndeadState.LEFT_LEG, now))
						- UndeadStages.LEG_STAND) / (1f - UndeadStages.LEG_STAND)));
				float shake = 1f - 0.6f * steady;
				float tremble = Mth.sin(now * 2.3f) * 0.05f * shake;
				float step = Mth.sin(swing * 0.6662f) * 0.45f * amount;
				set(model.rightLeg, 1f, step, 0f, 0.09f * shake + tremble * 0.5f);
				set(model.leftLeg, 1f, -step, 0f, -0.09f * shake - tremble * 0.5f);
				set(model.rightArm, shake, -0.3f + tremble, 0f, 0.6f + tremble);
				set(model.leftArm, shake, -0.3f - tremble, 0f, -0.6f + tremble);
				return true;
			}
			case UndeadStages.LIMP -> {
				// the short leg barely swings
				int leg = UndeadMotion.shortLeg(s, now);
				if (leg >= 0) {
					ModelPart lp = leg == UndeadState.RIGHT_LEG ? model.rightLeg : model.leftLeg;
					float len = UndeadMotion.legLength(s, leg, now);
					lp.xRot *= 0.3f + 0.7f * len / 12f;
				}
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	/** One arm's crawl stroke at phase {@code c} (0..1): the pull along the ground, then the reach forward again. */
	private static void crawlStroke(ModelPart arm, float sd, float c, float amount) {
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
	}

	private static void set(ModelPart part, float weight, float x, float y, float z) {
		part.xRot = Mth.lerp(weight, part.xRot, x);
		part.yRot = Mth.lerp(weight, part.yRot, y);
		part.zRot = Mth.lerp(weight, part.zRot, z);
	}
}
