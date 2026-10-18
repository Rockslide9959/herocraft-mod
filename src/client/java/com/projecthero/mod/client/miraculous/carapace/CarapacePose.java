package com.projecthero.mod.client.miraculous.carapace;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.carapace.Carapace;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Carapace's keyframed poses, set on the vanilla humanoid model at the end of {@code setupAnim} (the suit model
 * copies it). Driven by the synced {@code animId / animStart} (his ids 100..119) and his timers, so every viewer sees
 * the same thing. The Shield is on his LEFT arm. Negative {@code xRot} swings a limb forward / up; positive {@code zRot}
 * lifts the right arm out to the side, negative the left.
 * <ul>
 *   <li><b>Shield Bash</b>: leaning into the charge, the shield arm forward, the other arm back (held while charging);</li>
 *   <li><b>Shield Throw</b>: wound across the body, flung out backhand like a discus; <b>the catch</b>: the arm up;</li>
 *   <li><b>Shield Slam</b>: the shield raised high, then driven down into the ground, knees bent;</li>
 *   <li><b>Taunt</b>: the shield held out to the side, the free hand beckoning, chin up;</li>
 *   <li><b>Shield Surf</b>: a surfer's crouch on the board, arms out for balance (held while it is on);</li>
 *   <li><b>Shell Roll</b>: curled into a ball behind the shell, the whole body tumbling forward;</li>
 *   <li><b>Shell Guard</b>: braced behind the shield held square in front (held while Z is);</li>
 *   <li><b>Shelter</b>: both arms thrown up, then spread wide as the dome rises;</li>
 *   <li><b>Shell Barrier</b>: shield and open hand pushed forward; <b>Protect</b>: pointing at whoever gets the bubble;</li>
 *   <li><b>Steadfast</b>: a wide, planted stance with a stamp; <b>Tidal Swim</b>: a breaststroke sweep.</li>
 * </ul>
 */
public final class CarapacePose {
	private CarapacePose() {
	}

	/** Ticks each pose plays for. */
	public static int length(int animId) {
		return switch (animId) {
			case Carapace.ANIM_BASH -> 8;
			case Carapace.ANIM_THROW -> 10;
			case Carapace.ANIM_CATCH -> 8;
			case Carapace.ANIM_SLAM -> 14;
			case Carapace.ANIM_TAUNT -> 30;
			case Carapace.ANIM_SURF -> 8;
			case Carapace.ANIM_ROLL -> 6;
			case Carapace.ANIM_SHELTER -> 30;
			case Carapace.ANIM_BARRIER -> 16;
			case Carapace.ANIM_PROTECT -> 20;
			case Carapace.ANIM_STEADFAST -> 20;
			case Carapace.ANIM_TIDAL -> 20;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || s.animId < 100 || s.animId > 119 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		int len = length(s.animId);
		if (age < 0f || age >= len) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Carapace.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float time = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		// ---- the held states
		if (Carapace.rolling(player)) {
			curl(model, time * 0.75f);
			return;
		}
		if (Carapace.surfing(player)) {
			float sway = Mth.sin(time * 0.15f) * 0.12f;
			model.body.xRot = 0.4f;
			model.body.yRot = 0.35f;
			pose(r, 1f, -0.2f, 0f, 1.05f + sway);
			pose(l, 1f, -0.6f, 0f, -0.9f + sway);
			model.rightLeg.xRot = -0.55f;
			model.leftLeg.xRot = 0.35f;
			model.rightLeg.zRot = 0.08f;
			model.leftLeg.zRot = -0.08f;
			model.rightLeg.yRot = 0f;
			model.leftLeg.yRot = 0f;
			lower(model, 2.2f);
			model.head.xRot = Mth.lerp(0.5f, model.head.xRot, -0.15f);
			model.hat.copyFrom(model.head);
			return;
		}
		if (Carapace.guarding(player)) {
			pose(l, 1f, -1.25f, 0.45f, 0.05f);
			pose(r, 1f, -0.8f, -0.45f, 0f);
			model.body.xRot = 0.18f;
			model.rightLeg.xRot = Mth.lerp(0.5f, model.rightLeg.xRot, 0.3f);
			model.leftLeg.xRot = Mth.lerp(0.5f, model.leftLeg.xRot, -0.35f);
			lower(model, 0.8f);
			return;
		}
		boolean charging = Carapace.running(player, Carapace.V_BASH);
		float[] p = playing(player, partial);
		if (p == null && charging) {
			p = new float[] { Carapace.ANIM_BASH, 4f };
		}
		if (p == null) {
			return;
		}
		int id = (int) p[0];
		float age = p[1];
		int len = length(id);
		float w = Math.min(1f, age / 2f) * (id == Carapace.ANIM_BASH && charging ? 1f : Math.min(1f, (len - age) / 3f));
		switch (id) {
			case Carapace.ANIM_BASH -> {
				pose(l, w, -1.35f, 0.75f, 0f);
				pose(r, w, 0.7f, 0f, 0.25f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.45f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.35f);
				model.hat.copyFrom(model.head);
				float run = Mth.sin(time * 1.4f) * 0.9f;
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, run);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, -run);
			}
			case Carapace.ANIM_THROW -> {
				// a backhand discus throw with the left arm: wound across the chest, flung out to the left
				float t = ease(Mth.clamp((age - 2f) / 3f, 0f, 1f));
				pose(l, w, -1.45f, Mth.lerp(t, 0.9f, -0.5f), Mth.lerp(t, 0.35f, -0.45f));
				pose(r, w, -0.35f, 0f, 0.45f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.55f, -0.4f));
			}
			case Carapace.ANIM_CATCH -> pose(l, w, -2.2f, 0f, -0.2f);
			case Carapace.ANIM_SLAM -> {
				if (age < 5f) {
					float t = ease(age / 5f);
					pose(l, w, Mth.lerp(t, 0f, -2.9f), 0f, -0.1f);
					pose(r, w, Mth.lerp(t, 0f, -2.6f), 0f, 0.1f);
					model.head.xRot = Mth.lerp(w * t, model.head.xRot, -0.4f);
				} else {
					float t = ease(Mth.clamp((age - 5f) / 2f, 0f, 1f));
					pose(l, w, Mth.lerp(t, -2.9f, -0.75f), 0.2f, -0.1f);
					pose(r, w, Mth.lerp(t, -2.6f, -0.65f), -0.2f, 0.1f);
					model.body.xRot = Mth.lerp(w * t, model.body.xRot, 0.6f);
					model.head.xRot = Mth.lerp(w * t, model.head.xRot, 0.2f);
					model.rightLeg.xRot = Mth.lerp(w * t, model.rightLeg.xRot, -0.55f);
					model.leftLeg.xRot = Mth.lerp(w * t, model.leftLeg.xRot, 0.35f);
					lower(model, 2.5f * w * t);
				}
				model.hat.copyFrom(model.head);
			}
			case Carapace.ANIM_TAUNT -> {
				float beckon = Mth.sin(age * 0.7f) * 0.35f;
				pose(l, w, -0.3f, 0f, -1.25f);
				pose(r, w, -1.6f + beckon, -0.35f, 0.05f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.3f);
				model.hat.copyFrom(model.head);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.08f);
			}
			case Carapace.ANIM_SURF -> {
				// hopping on: the crouch is set above once V_SURF is on; this is the jump onto it
				float t = Mth.sin(Mth.clamp(age / len, 0f, 1f) * (float) Math.PI);
				pose(r, w * t, -0.6f, 0f, 0.6f);
				pose(l, w * t, -0.6f, 0f, -0.6f);
			}
			case Carapace.ANIM_SHELTER -> {
				if (age < 12f) {
					float t = ease(age / 12f);
					pose(l, w, Mth.lerp(t, -0.5f, -3.05f), 0f, Mth.lerp(t, -0.2f, -0.35f));
					pose(r, w, Mth.lerp(t, -0.5f, -3.05f), 0f, Mth.lerp(t, 0.2f, 0.35f));
					model.head.xRot = Mth.lerp(w * t, model.head.xRot, -0.55f);
				} else {
					float t = ease(Mth.clamp((age - 12f) / 6f, 0f, 1f));
					pose(l, w, Mth.lerp(t, -3.05f, -0.2f), 0f, Mth.lerp(t, -0.35f, -1.75f));
					pose(r, w, Mth.lerp(t, -3.05f, -0.2f), 0f, Mth.lerp(t, 0.35f, 1.75f));
					model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(t, -0.55f, -0.2f));
					model.rightLeg.zRot = Mth.lerp(w * t, model.rightLeg.zRot, 0.18f);
					model.leftLeg.zRot = Mth.lerp(w * t, model.leftLeg.zRot, -0.18f);
				}
				model.hat.copyFrom(model.head);
			}
			case Carapace.ANIM_BARRIER -> {
				float t = ease(Mth.clamp((age - 1f) / 4f, 0f, 1f));
				pose(l, w, Mth.lerp(t, -0.6f, -1.55f), 0.3f, 0f);
				pose(r, w, Mth.lerp(t, -0.6f, -1.6f), -0.25f, 0f);
				model.body.xRot = Mth.lerp(w * t, model.body.xRot, 0.2f);
				model.rightLeg.xRot = Mth.lerp(w * t, model.rightLeg.xRot, 0.35f);
				model.leftLeg.xRot = Mth.lerp(w * t, model.leftLeg.xRot, -0.4f);
			}
			case Carapace.ANIM_PROTECT -> {
				// pointing along his look at whoever gets the bubble
				pose(r, w, model.head.xRot - (float) Math.PI / 2f, model.head.yRot, 0f);
				pose(l, w, -0.9f, 0.6f, 0f);
			}
			case Carapace.ANIM_STEADFAST -> {
				float stamp = age < 6f ? Mth.sin(age / 6f * (float) Math.PI) : 0f;
				model.rightLeg.zRot = Mth.lerp(w, model.rightLeg.zRot, 0.32f);
				model.leftLeg.zRot = Mth.lerp(w, model.leftLeg.zRot, -0.32f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.5f * stamp);
				pose(l, w, -1.1f, 0.5f, -0.1f);
				pose(r, w, -0.35f, 0f, 0.6f);
				lower(model, 1.2f * w);
			}
			case Carapace.ANIM_TIDAL -> {
				float t = ease(Mth.clamp(age / 14f, 0f, 1f));
				pose(l, w, -2.9f + 1.0f * t, 0f, Mth.lerp(t, -0.15f, -1.45f));
				pose(r, w, -2.9f + 1.0f * t, 0f, Mth.lerp(t, 0.15f, 1.45f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.25f);
				model.hat.copyFrom(model.head);
			}
			default -> {
			}
		}
	}

	/** Bends the knees: everything above the legs drops by {@code px} (model pixels), the legs stay on the ground. */
	private static void lower(HumanoidModel<?> m, float px) {
		m.head.y += px;
		m.hat.y += px;
		m.body.y += px;
		m.rightArm.y += px;
		m.leftArm.y += px;
	}

	/**
	 * Shell Roll: curled up into a ball round the hips -- knees to the chest, head tucked, arms round the knees -- and the
	 * whole ball tumbling forward by {@code spin} (each part's pivot and its pitch turned round the ball's middle).
	 */
	private static void curl(HumanoidModel<?> m, float spin) {
		ModelPart[] parts = { m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg };
		for (ModelPart part : parts) {
			part.yRot = 0f;
			part.zRot = 0f;
		}
		m.body.xRot = 0.9f;
		m.body.y = 4f;
		m.body.z = -1f;
		m.head.xRot = 1.0f;
		m.head.y = 6.5f;
		m.head.z = -4.5f;
		m.rightArm.xRot = -0.9f;
		m.leftArm.xRot = -0.9f;
		m.rightArm.y = 7f;
		m.leftArm.y = 7f;
		m.rightArm.z = -3f;
		m.leftArm.z = -3f;
		m.rightLeg.xRot = -2.1f;
		m.leftLeg.xRot = -2.1f;
		m.rightLeg.y = 13f;
		m.leftLeg.y = 13f;
		m.rightLeg.z = 1.5f;
		m.leftLeg.z = 1.5f;
		m.hat.copyFrom(m.head);
		// the tumble, about (y 12, z 0)
		float c = Mth.cos(spin), s = Mth.sin(spin);
		for (ModelPart part : parts) {
			float y = part.y - 12f, z = part.z;
			part.y = 12f + y * c - z * s;
			part.z = y * s + z * c;
			part.xRot += spin;
		}
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
