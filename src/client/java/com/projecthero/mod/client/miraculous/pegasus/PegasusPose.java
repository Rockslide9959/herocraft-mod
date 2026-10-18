package com.projecthero.mod.client.miraculous.pegasus;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.pegasus.Pegasus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Pegasus's keyframed poses, set on the vanilla humanoid model at the end of {@code setupAnim} (the suit model
 * copies it). Driven by the synced {@code animId / animStart} (his ids 120..139) and his timers, so every viewer sees the
 * same thing. The Horseshoe is in his RIGHT hand. Negative {@code xRot} swings a limb forward / up; positive {@code zRot}
 * lifts the right arm out to the side (the left arm mirrors).
 * <ul>
 *   <li><b>Horseshoe Throw</b>: wound back across the body, flung out flat like a discus; <b>the catch</b>: hand up;</li>
 *   <li><b>Volley</b>: a backhand fling across the body, the fan leaving the fingers;</li>
 *   <li><b>Hoof Kick</b>: hands to the ground, both legs bucking out behind; <b>Trample</b>: low, leaning, arms back;</li>
 *   <li><b>Portal Hop</b>: the Horseshoe held up before him, a step through; <b>Gallop</b>: a rear on the hind legs, then
 *       (while it runs and he moves) leaning in with pumping arms;</li>
 *   <li><b>Portal Trap</b>: pointing the Horseshoe down at the target, then flicking it up; <b>Voyage</b>: both arms
 *       flung wide, the Horseshoe raised;</li>
 *   <li><b>Portal Shield</b>: the hand up and forward holding the shield portal (as long as it stands); <b>Sky
 *       Gallop</b>: knees high, running on the air;</li>
 *   <li><b>Mark Waypoint</b>: a crouch to tap the ground with the Horseshoe; <b>Linked Portals</b>: the Horseshoe drawn
 *       in a ring before him, then thrown out toward the far portal.</li>
 * </ul>
 */
public final class PegasusPose {
	private PegasusPose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case Pegasus.ANIM_THROW -> 10;
			case Pegasus.ANIM_CATCH -> 8;
			case Pegasus.ANIM_VOLLEY -> 12;
			case Pegasus.ANIM_KICK -> 14;
			case Pegasus.ANIM_TRAMPLE -> 12;
			case Pegasus.ANIM_HOP -> 12;
			case Pegasus.ANIM_GALLOP -> 16;
			case Pegasus.ANIM_TRAP -> 16;
			case Pegasus.ANIM_VOYAGE -> 30;
			case Pegasus.ANIM_SHIELD -> 10;
			case Pegasus.ANIM_SKY -> 12;
			case Pegasus.ANIM_WAYPOINT -> 14;
			case Pegasus.ANIM_LINK -> 18;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || s.animId < 120 || s.animId > 139 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Pegasus.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float ageTicks = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		// v0.16.7 (user): Gallop runs with the regular Minecraft running animation (no lean / arm-pump pose of its own)
		boolean standing = PegasusWorldFx.standing(player);
		// Sky Gallop: knees high, running on the air
		if (Pegasus.skyGalloping(player) && !standing) {
			float sw = Mth.sin(ageTicks * 0.75f);
			model.rightLeg.xRot = -0.9f + 0.9f * sw;
			model.leftLeg.xRot = -0.9f - 0.9f * sw;
			pose(r, 1f, 0.6f * sw, 0f, 0.25f);
			pose(l, 1f, -0.6f * sw, 0f, -0.25f);
			model.body.xRot = Mth.lerp(0.7f, model.body.xRot, 0.18f);
		}
		// the Portal Shield up: the hand raised before him, palm out
		if (Pegasus.running(player, Pegasus.V_SHIELD)) {
			pose(r, 1f, -1.65f + model.head.xRot, -0.15f, 0.05f);
			pose(l, 0.7f, -0.6f, 0.3f, -0.1f);
		}
		// the Trample charge: low and driving
		if (Pegasus.running(player, Pegasus.V_TRAMPLE)) {
			model.body.xRot = 0.6f;
			pose(r, 1f, 1.0f, 0f, 0.35f);
			pose(l, 1f, 1.0f, 0f, -0.35f);
			model.head.xRot = -0.55f;
			float sw = Mth.sin(ageTicks * 1.6f);
			model.rightLeg.xRot = 1.0f * sw;
			model.leftLeg.xRot = -1.0f * sw;
			model.rightLeg.z += 3.0f;
			model.leftLeg.z += 3.0f;
			model.rightLeg.y -= 1.0f;
			model.leftLeg.y -= 1.0f;
		}
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
			case Pegasus.ANIM_THROW -> {
				// a flat discus fling: wound back across the chest, then out to the right
				float t = ease(Mth.clamp((age - 2f) / 3f, 0f, 1f));
				pose(r, w, -1.5f, Mth.lerp(t, 1.1f, -0.7f), 0f);
				pose(l, w, -0.5f, 0f, -0.4f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.5f, -0.35f));
			}
			case Pegasus.ANIM_CATCH -> pose(r, w, -2.4f, -0.1f, 0.1f);
			case Pegasus.ANIM_VOLLEY -> {
				// a backhand fling: from the left hip up and out
				float t = ease(Mth.clamp((age - 1f) / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.5f, -1.6f), Mth.lerp(t, 0.9f, -0.6f), Mth.lerp(t, 0f, 0.3f));
				pose(l, w, -0.3f, 0f, -0.5f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.45f, -0.25f));
			}
			case Pegasus.ANIM_KICK -> {
				// the buck: bent over, hands down, both legs out behind
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.85f);
				pose(r, w, -1.25f, 0f, 0.15f);
				pose(l, w, -1.25f, 0f, -0.15f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.7f);
				float kick = w * Mth.lerp(t, 0.2f, 1.25f);
				model.rightLeg.xRot = kick;
				model.leftLeg.xRot = kick * 0.85f;
				model.rightLeg.z += 3.5f * w;
				model.leftLeg.z += 3.5f * w;
				model.rightLeg.y -= 1.5f * w;
				model.leftLeg.y -= 1.5f * w;
			}
			case Pegasus.ANIM_TRAMPLE -> {
				// the first strides (the timer pose above carries on)
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.4f);
			}
			case Pegasus.ANIM_HOP -> {
				// the Horseshoe held up before him, then a stride through
				float t = Mth.clamp(age / 6f, 0f, 1f);
				pose(r, w, -1.9f, -0.25f, 0f);
				pose(l, w, -0.4f, 0f, -0.3f);
				model.rightLeg.xRot = Mth.lerp(w * t, model.rightLeg.xRot, -0.6f);
				model.leftLeg.xRot = Mth.lerp(w * t, model.leftLeg.xRot, 0.5f);
			}
			case Pegasus.ANIM_GALLOP -> {
				// rearing up: arms up and forward like hooves, the body tipped back
				float t = Mth.sin(Mth.clamp(age / 12f, 0f, 1f) * (float) Math.PI);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.25f * t);
				pose(r, w, -2.2f + 0.3f * Mth.sin(age * 1.4f), 0f, 0.2f);
				pose(l, w, -2.2f - 0.3f * Mth.sin(age * 1.4f), 0f, -0.2f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.8f * t);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.35f);
			}
			case Pegasus.ANIM_TRAP -> {
				// pointing the Horseshoe down at it, then a flick up (it drops out of the sky)
				float t = ease(Mth.clamp((age - 7f) / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -1.15f, -2.8f), -0.1f, 0f);
				pose(l, w, -0.35f, 0f, -0.35f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(t, 0.35f, -0.6f));
			}
			case Pegasus.ANIM_VOYAGE -> {
				// both arms flung wide, the Horseshoe high, the head up
				float t = ease(Mth.clamp(age / 8f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.4f, -2.5f), 0f, Mth.lerp(t, 0.2f, 0.9f));
				pose(l, w, Mth.lerp(t, -0.4f, -1.4f), 0f, Mth.lerp(t, -0.2f, -1.35f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.3f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.12f * t);
			}
			case Pegasus.ANIM_SHIELD -> {
				// thrusting it up (the timer pose above holds it)
				float t = ease(Mth.clamp(age / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.6f, -1.75f), 0f, 0f);
			}
			case Pegasus.ANIM_SKY -> {
				// the spring up onto the air
				float t = Mth.clamp(age / 5f, 0f, 1f);
				pose(r, w, -2.6f * t, 0f, 0.3f);
				pose(l, w, -2.6f * t, 0f, -0.3f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -1.0f * t);
			}
			case Pegasus.ANIM_WAYPOINT -> {
				// a dip to tap the ground with the Horseshoe
				float t = Mth.sin(Mth.clamp(age / 12f, 0f, 1f) * (float) Math.PI);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.55f * t);
				pose(r, w, Mth.lerp(t, -0.6f, -0.35f), 0f, 0.1f);
				pose(l, w, -0.3f, 0f, -0.3f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.6f * t);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.5f * t);
			}
			case Pegasus.ANIM_LINK -> {
				// the Horseshoe drawn round in a ring before him, then flung out toward the far portal
				if (age < 10f) {
					float a = age / 10f * (float) Math.PI * 2f;
					pose(r, w, -1.5f + 0.45f * Mth.sin(a), 0.45f * Mth.cos(a), 0f);
				} else {
					float t = ease(Mth.clamp((age - 10f) / 3f, 0f, 1f));
					pose(r, w, Mth.lerp(t, -1.5f, -1.75f), Mth.lerp(t, 0.4f, -0.2f), 0f);
				}
				pose(l, w, -1.2f, 0.5f, 0f);
			}
			default -> {
			}
		}
		model.hat.copyFrom(model.head);
	}

	/** Whether a move has the Horseshoe in his hand now. */
	public static boolean inHand(Player player, float partial) {
		if (Pegasus.running(player, Pegasus.V_SHIELD)) {
			return true;
		}
		float[] p = playing(player, partial);
		if (p == null) {
			return false;
		}
		return switch ((int) p[0]) {
			case Pegasus.ANIM_THROW -> p[1] < 4f;
			case Pegasus.ANIM_CATCH, Pegasus.ANIM_HOP, Pegasus.ANIM_TRAP, Pegasus.ANIM_VOYAGE, Pegasus.ANIM_SHIELD, Pegasus.ANIM_WAYPOINT, Pegasus.ANIM_LINK,
					Pegasus.ANIM_VOLLEY -> true;
			default -> false;
		};
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
