package com.projecthero.mod.client.quirk.hellflame;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.hellflame.Hellflame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Endeavor's poses on the vanilla humanoid model, set at the end of {@code setupAnim}, from the synced
 * {@code animId / animStart} (ids 20..39) and his running moves, so every viewer sees the same. Negative {@code xRot}
 * swings a limb forward / up; positive {@code zRot} lifts the right arm out to the side (the left mirrors).
 * <ul>
 *   <li><b>held</b>: the beam (right arm locked along the look, the left gripping the forearm), the jets (arms angled
 *       down and back like rudders, legs together, leaning into the flight), the Prominence charge (a low, flexed
 *       crouch), the Flashfire dash (the fist driven forward);</li>
 *   <li><b>Hell Spider</b>: the open hand swept across; <b>Spider Net</b>: both hands thrown forward then apart;
 *       <b>Jet Burn</b>: a palm thrust; <b>Burst Dodge</b>: arms flung back; <b>Uppercut</b>: the fist from the hip to
 *       the sky; <b>Prominence Burn</b>: arms flung wide and up; <b>Sun</b>: the right arm raised; <b>Cloak</b>: a flex;
 *       <b>Vent</b>: arms out level, head back; <b>Overheat</b>: bent over, hands on knees.</li>
 * </ul>
 */
public final class HellflamePose {
	private HellflamePose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case Hellflame.ANIM_SPIDER -> 12;
			case Hellflame.ANIM_NET -> 16;
			case Hellflame.ANIM_JET -> 10;
			case Hellflame.ANIM_BEAM -> 6;
			case Hellflame.ANIM_BOOST -> 8;
			case Hellflame.ANIM_DODGE -> 12;
			case Hellflame.ANIM_FIST -> 14;
			case Hellflame.ANIM_UPPERCUT -> 14;
			case Hellflame.ANIM_CHARGE -> 6;
			case Hellflame.ANIM_PROMINENCE -> 22;
			case Hellflame.ANIM_SUN -> 20;
			case Hellflame.ANIM_CLOAK -> 14;
			case Hellflame.ANIM_VENT -> 20;
			case Hellflame.ANIM_OVERHEAT -> 40;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		QuirkState s = Quirks.peek(player);
		if (s == null || s.animId < 20 || s.animId > 39 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Hellflame.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float ageTicks = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;

		// the jets: arms down and back, legs together, leaning into the way he flies
		if (Hellflame.boosting(player) && !player.onGround()) {
			float speed = (float) Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
			float lean = Mth.clamp(speed * 1.4f, 0f, 1.0f);
			model.body.xRot = Mth.lerp(0.8f, model.body.xRot, 0.15f + 0.5f * lean);
			pose(r, 1f, 0.35f + 0.4f * lean, 0f, 0.35f);
			pose(l, 1f, 0.35f + 0.4f * lean, 0f, -0.35f);
			float sw = Mth.sin(ageTicks * 0.3f) * 0.05f;
			model.rightLeg.xRot = 0.1f + 0.35f * lean + sw;
			model.leftLeg.xRot = 0.05f + 0.35f * lean - sw;
			model.rightLeg.yRot = 0f;
			model.leftLeg.yRot = 0f;
			model.rightLeg.z += 2.5f * lean;
			model.leftLeg.z += 2.5f * lean;
			model.rightLeg.y -= 1.0f * lean;
			model.leftLeg.y -= 1.0f * lean;
			model.head.xRot = Mth.lerp(lean * 0.6f, model.head.xRot, -0.6f);
		}
		// the Prominence charge: a low, flexed crouch, fists balled at the sides
		if (Hellflame.charging(player)) {
			float c = Hellflame.charge(player, partial);
			float shake = c >= 1f ? Mth.sin(ageTicks * 2.6f) * 0.04f : 0f;
			model.body.xRot = Mth.lerp(0.8f, model.body.xRot, 0.25f);
			pose(r, 1f, -0.5f + shake, 0.2f, 0.75f + 0.15f * c);
			pose(l, 1f, -0.5f - shake, -0.2f, -0.75f - 0.15f * c);
			model.rightLeg.xRot = -0.3f;
			model.leftLeg.xRot = -0.3f;
			model.rightLeg.zRot = 0.18f;
			model.leftLeg.zRot = -0.18f;
			model.rightLeg.z += 1.5f;
			model.leftLeg.z += 1.5f;
			model.head.xRot = Mth.lerp(0.5f, model.head.xRot, 0.15f);
		}
		// the beam: right arm locked along the look, the left hand gripping the forearm
		if (Hellflame.beaming(player)) {
			pose(r, 1f, -1.5708f + model.head.xRot, model.head.yRot, 0f);
			pose(l, 1f, -1.35f + model.head.xRot, model.head.yRot + 0.65f, 0f);
			model.body.yRot = Mth.lerp(0.5f, model.body.yRot, -0.15f);
		}
		// the Flashfire dash: the fist driven forward, the body behind it
		if (Hellflame.dashing(player)) {
			pose(r, 1f, -1.6f + model.head.xRot, -0.1f, 0f);
			pose(l, 1f, 0.6f, 0f, -0.25f);
			model.body.xRot = Mth.lerp(0.8f, model.body.xRot, 0.3f);
			model.body.yRot = -0.25f;
			model.rightLeg.xRot = 0.6f;
			model.leftLeg.xRot = -0.4f;
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
			case Hellflame.ANIM_SPIDER -> {
				// the open hand swept across: from the left shoulder out to the right
				float t = ease(Mth.clamp((age - 1f) / 5f, 0f, 1f));
				pose(r, w, -1.5f + model.head.xRot * 0.5f, Mth.lerp(t, 0.7f, -0.6f), 0f);
				pose(l, w, -0.4f, 0f, -0.3f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.35f, -0.3f));
			}
			case Hellflame.ANIM_NET -> {
				// both hands thrown forward, then spread wide (the web opens)
				float t = ease(Mth.clamp((age - 4f) / 5f, 0f, 1f));
				pose(r, w, -1.5f, Mth.lerp(t, 0.15f, -0.7f), 0f);
				pose(l, w, -1.5f, Mth.lerp(t, -0.15f, 0.7f), 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.1f);
			}
			case Hellflame.ANIM_JET -> {
				// a palm thrust, the other hand back for balance
				float t = ease(Mth.clamp(age / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.8f, -1.6f) + model.head.xRot, -0.05f, 0f);
				pose(l, w, 0.5f, 0f, -0.3f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, -0.3f * t);
			}
			case Hellflame.ANIM_BEAM -> pose(r, w, -1.2f, 0f, 0f);
			case Hellflame.ANIM_BOOST -> {
				// the lift-off: knees bent, arms driving down
				float t = Mth.clamp(age / 4f, 0f, 1f);
				pose(r, w, 0.3f, 0f, 0.5f * t);
				pose(l, w, 0.3f, 0f, -0.5f * t);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.4f * (1f - t));
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, -0.4f * (1f - t));
			}
			case Hellflame.ANIM_DODGE -> {
				// thrown by the blast: arms flung back, legs trailing
				pose(r, w, 0.9f, 0f, 0.6f);
				pose(l, w, 0.9f, 0f, -0.6f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.25f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.5f);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.3f);
			}
			case Hellflame.ANIM_FIST -> {
				// wound back, then the straight right
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, 0.6f, -1.65f) + model.head.xRot * t, -0.1f, Mth.lerp(t, 0.3f, 0f));
				pose(l, w, Mth.lerp(t, -0.9f, 0.5f), 0.2f, -0.2f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.5f, -0.35f));
			}
			case Hellflame.ANIM_UPPERCUT -> {
				// low at the hip, then up to the sky
				float t = ease(Mth.clamp((age - 2f) / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, 0.5f, -2.9f), 0f, Mth.lerp(t, 0.25f, 0.05f));
				pose(l, w, -0.5f, 0f, -0.35f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, Mth.lerp(t, 0.35f, -0.15f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(t, 0.2f, -0.45f));
			}
			case Hellflame.ANIM_PROMINENCE -> {
				// the release: arms flung wide and up, head back
				float t = ease(Mth.clamp(age / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.4f, -0.2f), 0f, Mth.lerp(t, 0.5f, 2.1f));
				pose(l, w, Mth.lerp(t, -0.4f, -0.2f), 0f, Mth.lerp(t, -0.5f, -2.1f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.55f * t);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.12f * t);
				model.rightLeg.zRot = 0.2f * w;
				model.leftLeg.zRot = -0.2f * w;
			}
			case Hellflame.ANIM_SUN -> {
				// the right arm thrust up at the sky, palm open
				float t = ease(Mth.clamp(age / 5f, 0f, 1f));
				pose(r, w, -2.9f * t, 0f, 0.15f);
				pose(l, w, -0.3f, 0f, -0.4f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.5f);
			}
			case Hellflame.ANIM_CLOAK -> {
				// a flex: the flames roar up
				float t = Mth.sin(Mth.clamp(age / 10f, 0f, 1f) * (float) Math.PI);
				pose(r, w, -0.35f, 0.3f, 0.6f + 0.3f * t);
				pose(l, w, -0.35f, -0.3f, -0.6f - 0.3f * t);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.1f * t);
			}
			case Hellflame.ANIM_VENT -> {
				// arms out level, chest open, head thrown back
				float t = ease(Mth.clamp(age / 3f, 0f, 1f));
				pose(r, w, 0f, 0f, 1.45f * t);
				pose(l, w, 0f, 0f, -1.45f * t);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.6f * t);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.15f * t);
			}
			case Hellflame.ANIM_OVERHEAT -> {
				// spent: bent over, hands on the knees, breathing hard
				float breath = Mth.sin(age * 0.5f) * 0.04f;
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.55f + breath);
				pose(r, w, -0.85f, 0f, 0.1f);
				pose(l, w, -0.85f, 0f, -0.1f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.2f);
				if (!player.isCrouching()) {
					// the vanilla crouch's offsets, so the bent body stays joined up
					model.rightLeg.z += 4.0f * w;
					model.leftLeg.z += 4.0f * w;
					model.head.y += 4.2f * w;
					model.body.y += 3.2f * w;
					model.rightArm.y += 3.2f * w;
					model.leftArm.y += 3.2f * w;
				}
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
