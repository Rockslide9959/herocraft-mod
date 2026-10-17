package com.projecthero.mod.client.miraculous.renarouge;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.renarouge.RenaRouge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Rena Rouge's keyframed poses, set on the vanilla humanoid model at the end of {@code setupAnim} (the suit
 * model copies it). Driven by the synced {@code animId / animStart} (her ids 60..79) and her timers, so every viewer
 * sees the same thing. The Flute is in her RIGHT hand. Negative {@code xRot} swings a limb forward / up; positive
 * {@code zRot} lifts the right arm out to the side (the left arm mirrors).
 * <ul>
 *   <li><b>Flute Strike</b>: a forehand swipe, a backhand, an overhead two-handed chop (the combo);</li>
 *   <li><b>Flute Sweep</b>: the staff held out, the whole body turning a full circle;</li>
 *   <li><b>Flute Throw</b>: wound back over the shoulder, flung forward; <b>the catch</b>: the hand up;</li>
 *   <li><b>Melody Lure / Mirage</b>: the Flute at her lips, both hands on it, head tilted; the Mirage ends with the
 *       hand raised after the bubble;</li>
 *   <li><b>Fox Dash</b>: leaning into it, arms swept back; <b>Illusion Swap</b>: arms crossing;</li>
 *   <li><b>Illusion Double</b>: the Flute raised high, the other hand flung out; <b>Fake Wall</b>: both hands sweeping
 *       up and out; <b>Fox Camouflage</b>: hunched, arms drawn in, the head bowed;</li>
 *   <li><b>Fox Pounce</b>: crouch, then both arms reaching down and forward through the air; <b>Fox Feint</b>: a lean
 *       to the side.</li>
 * </ul>
 */
public final class RenaRougePose {
	private RenaRougePose() {
	}

	/** Ticks each pose plays for. */
	public static int length(int animId) {
		return switch (animId) {
			case RenaRouge.ANIM_STRIKE_1, RenaRouge.ANIM_STRIKE_2 -> 9;
			case RenaRouge.ANIM_STRIKE_3 -> 12;
			case RenaRouge.ANIM_SWEEP -> 15;
			case RenaRouge.ANIM_THROW -> 10;
			case RenaRouge.ANIM_CATCH -> 10;
			case RenaRouge.ANIM_LURE -> 40;
			case RenaRouge.ANIM_DASH -> 10;
			case RenaRouge.ANIM_SWAP -> 10;
			case RenaRouge.ANIM_DOUBLE -> 18;
			case RenaRouge.ANIM_MIRAGE -> 44;
			case RenaRouge.ANIM_CAMO -> 16;
			case RenaRouge.ANIM_WALL -> 18;
			case RenaRouge.ANIM_POUNCE -> 12;
			case RenaRouge.ANIM_FEINT -> 10;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || s.animId < 60 || s.animId > 79 || player.level() == null) {
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
		if (!RenaRouge.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		ModelPart r = model.rightArm, l = model.leftArm;
		// the Flute to her lips while she plays (Lure, the Mirage's melody)
		boolean playing = RenaRouge.running(player, RenaRouge.V_PLAY);
		if (playing) {
			pose(r, 1f, -1.75f, -0.15f, 0.35f);
			pose(l, 1f, -1.85f, 0.75f, 0f);
			model.head.zRot = Mth.lerp(0.8f, model.head.zRot, 0.18f);
			model.head.xRot = Mth.lerp(0.5f, model.head.xRot, 0.05f);
			model.hat.copyFrom(model.head);
		}
		// in the air on a pounce: reaching down and forward
		if (RenaRouge.val(player, RenaRouge.V_POUNCE) != 0L && !player.onGround()) {
			float dy = (float) player.getDeltaMovement().y;
			float down = Mth.clamp(-dy * 2f + 0.3f, 0f, 1f);
			pose(r, 1f, Mth.lerp(down, -2.8f, -1.2f), 0f, 0.15f);
			pose(l, 1f, Mth.lerp(down, -2.8f, -1.2f), 0f, -0.15f);
			model.rightLeg.xRot = Mth.lerp(down, 0.6f, -0.4f);
			model.leftLeg.xRot = Mth.lerp(down, 0.9f, -0.2f);
			model.body.xRot = Mth.lerp(down, 0f, 0.35f);
		}
		float[] p = playing(player, partial);
		if (p == null) {
			return;
		}
		int id = (int) p[0];
		float age = p[1];
		int len = length(id);
		float w = Math.min(1f, age / 2f) * Math.min(1f, (len - age) / 3f);
		switch (id) {
			case RenaRouge.ANIM_STRIKE_1 -> {
				// forehand: from out on the right, across the body
				float t = ease(Mth.clamp((age - 1f) / 4f, 0f, 1f));
				pose(r, w, -1.45f, Mth.lerp(t, -1.0f, 0.75f), 0f);
				pose(l, w, -0.4f, 0f, -0.3f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, -0.35f, 0.35f));
			}
			case RenaRouge.ANIM_STRIKE_2 -> {
				// backhand: from across the body out to the right
				float t = ease(Mth.clamp((age - 1f) / 4f, 0f, 1f));
				pose(r, w, -1.5f, Mth.lerp(t, 0.8f, -0.9f), 0f);
				pose(l, w, -0.4f, 0f, -0.3f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.4f, -0.35f));
			}
			case RenaRouge.ANIM_STRIKE_3 -> {
				// overhead, both hands: raised, then chopped down
				float t = ease(Mth.clamp((age - 2f) / 4f, 0f, 1f));
				float x = Mth.lerp(t, -3.0f, -0.95f);
				pose(r, w, x, -0.2f, 0f);
				pose(l, w, x, 0.45f, 0f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3f * t);
			}
			case RenaRouge.ANIM_SWEEP -> {
				// staff held out level to the right, the whole body turning a circle
				float t = Mth.clamp((age - 1f) / 11f, 0f, 1f);
				float spin = (float) (Math.PI * 2 * ease(t));
				pose(r, w, -0.1f, 0f, 1.45f);
				pose(l, w, -0.2f, 0f, -0.9f);
				spinAll(model, spin * w);
			}
			case RenaRouge.ANIM_THROW -> {
				float t = ease(Mth.clamp((age - 2f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -2.9f, -1.35f), Mth.lerp(t, 0.35f, -0.2f), 0f);
				pose(l, w, -0.6f, 0f, -0.35f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, -0.4f, 0.25f));
			}
			case RenaRouge.ANIM_CATCH -> {
				pose(r, w, -2.3f, -0.15f, 0.1f);
			}
			case RenaRouge.ANIM_LURE -> {
				// (the flute at the lips is set above) a gentle sway to the tune
				model.body.zRot = Mth.lerp(w, model.body.zRot, 0.06f * Mth.sin(age * 0.35f));
			}
			case RenaRouge.ANIM_MIRAGE -> {
				if (age > 24f) {
					// the bubble away: the Flute raised after it
					float t = Mth.clamp((age - 24f) / 4f, 0f, 1f);
					pose(r, w * t, -2.8f, 0f, 0.2f);
					pose(l, w * t, -0.5f, 0f, -0.5f);
					model.head.xRot = Mth.lerp(w * t, model.head.xRot, -0.5f);
					model.hat.copyFrom(model.head);
				} else {
					model.body.zRot = Mth.lerp(w, model.body.zRot, 0.08f * Mth.sin(age * 0.5f));
				}
			}
			case RenaRouge.ANIM_DASH -> {
				pose(r, w, 1.1f, 0f, 0.25f);
				pose(l, w, 1.1f, 0f, -0.25f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.5f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.7f);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.6f);
			}
			case RenaRouge.ANIM_SWAP -> {
				pose(r, w, -1.3f, 0.7f, 0f);
				pose(l, w, -1.3f, -0.7f, 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.25f);
				model.hat.copyFrom(model.head);
			}
			case RenaRouge.ANIM_DOUBLE -> {
				pose(r, w, -2.95f, 0f, 0.15f);
				pose(l, w, -0.35f, 0f, -1.3f);
			}
			case RenaRouge.ANIM_CAMO -> {
				pose(r, w, -0.9f, 0.65f, 0f);
				pose(l, w, -0.9f, -0.65f, 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.45f);
				model.hat.copyFrom(model.head);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.25f);
			}
			case RenaRouge.ANIM_WALL -> {
				float t = ease(Mth.clamp((age - 1f) / 8f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.4f, -2.5f), 0f, Mth.lerp(t, 0.2f, 0.75f));
				pose(l, w, Mth.lerp(t, -0.4f, -2.5f), 0f, Mth.lerp(t, -0.2f, -0.75f));
			}
			case RenaRouge.ANIM_POUNCE -> {
				// the crouch before the spring
				float t = Mth.clamp(age / 3f, 0f, 1f);
				if (age < 4f) {
					pose(r, w * t, 0.6f, 0f, 0.2f);
					pose(l, w * t, 0.6f, 0f, -0.2f);
					model.body.xRot = Mth.lerp(w * t, model.body.xRot, 0.45f);
				}
			}
			case RenaRouge.ANIM_FEINT -> {
				float side = ((int) (Miraculous.peek(player).animStart & 1L)) == 0 ? 1f : -1f;
				model.body.zRot = Mth.lerp(w, model.body.zRot, 0.3f * side);
				pose(r, w, -0.3f, 0f, 0.9f);
				pose(l, w, -0.3f, 0f, -0.9f);
			}
			default -> {
			}
		}
	}

	private static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	/** Turns the whole body by {@code a} about its middle (each part's pivot and its yaw). */
	private static void spinAll(HumanoidModel<?> m, float a) {
		ModelPart[] parts = { m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg };
		float c = Mth.cos(a), s = Mth.sin(a);
		for (ModelPart part : parts) {
			float x = part.x, z = part.z;
			part.x = x * c + z * s;
			part.z = -x * s + z * c;
			part.yRot += a;
		}
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
