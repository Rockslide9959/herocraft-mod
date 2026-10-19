package com.projecthero.mod.client.miraculous.viperion;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.viperion.Viperion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Viperion's keyframed poses, set on the vanilla humanoid model at the end of {@code setupAnim} (the suit model
 * copies it). Driven by the synced {@code animId / animStart} (his ids 140..159) and his timers, so every viewer sees the
 * same thing. The Lyre is in his RIGHT hand. Negative {@code xRot} swings a limb forward / up; positive {@code zRot} lifts
 * the right arm out to the side (the left mirrors).
 * <ul>
 *   <li><b>Lyre Strike</b>: three swings, forehand, backhand, overhead; <b>Lyre Sweep</b>: one wide swing right to left;</li>
 *   <li><b>Sonic Chord</b>: the Lyre held out, the left hand strumming across it; <b>Resonance</b>: raised high, then struck down;</li>
 *   <li><b>Serpent Dash</b>: the swim / crawl pose (the slither, see {@code ViperionDashPoseMixin}); <b>Coil Leap</b>: a deep
 *       crouch, then arms flung forward and up;</li>
 *   <li><b>Venom Strike</b>: a quick lunging jab; <b>Second Chance</b>: the Lyre raised, both hands on the strings (the
 *       checkpoint), arms flung wide, head back (the rewind);</li>
 *   <li><b>Constrict</b>: the Lyre thrust out, then a yank back; <b>String Shield</b>: held out before him while it spins;</li>
 *   <li><b>Heat Sense</b>: two fingers to the temple; <b>Hiss</b>: leaning in, arms low and wide.</li>
 * </ul>
 */
public final class ViperionPose {
	private ViperionPose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case Viperion.ANIM_STRIKE -> 16;
			case Viperion.ANIM_SWEEP -> 12;
			case Viperion.ANIM_CHORD -> 14;
			case Viperion.ANIM_RESONANCE -> 16;
			case Viperion.ANIM_DASH -> 6;
			case Viperion.ANIM_COIL -> 12;
			case Viperion.ANIM_VENOM -> 10;
			case Viperion.ANIM_CHECKPOINT -> 26;
			case Viperion.ANIM_REWIND -> 22;
			case Viperion.ANIM_CONSTRICT -> 16;
			case Viperion.ANIM_SHIELD -> 8;
			case Viperion.ANIM_HEAT -> 16;
			case Viperion.ANIM_HISS -> 18;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || s.animId < 140 || s.animId > 159 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	/** Whether the Lyre is in his hand now (else it is on his back). */
	public static boolean inHand(Player player, float partial) {
		if (Viperion.shielding(player) || Viperion.running(player, Viperion.V_CONSTRICT) || Viperion.running(player, Viperion.V_COMBO)) {
			return true;
		}
		float[] p = playing(player, partial);
		if (p == null) {
			return false;
		}
		return switch ((int) p[0]) {
			case Viperion.ANIM_DASH, Viperion.ANIM_COIL, Viperion.ANIM_HEAT, Viperion.ANIM_HISS -> false;
			default -> true;
		};
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Viperion.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		ModelPart r = model.rightArm, l = model.leftArm;
		if (Viperion.shielding(player)) {
			// the Lyre held out before him, spinning
			pose(r, 1f, -1.45f + model.head.xRot * 0.5f, -0.25f, 0f);
			pose(l, 0.6f, -0.9f, 0.5f, 0f);
		}
		if (Viperion.running(player, Viperion.V_CONSTRICT)) {
			pose(r, 1f, -1.5f + model.head.xRot, -0.05f, 0f);
			pose(l, 1f, -1.1f, 0.6f, 0f);
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
			case Viperion.ANIM_STRIKE -> {
				// three blows five ticks apart: forehand, backhand, overhead
				int k = Math.min(2, (int) (age / 5f));
				float t = ease(Mth.clamp((age - k * 5f) / 3f, 0f, 1f));
				if (k == 0) {
					pose(r, w, -1.6f, Mth.lerp(t, 0.9f, -0.7f), 0f);
					model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.35f, -0.3f));
				} else if (k == 1) {
					pose(r, w, -1.4f, Mth.lerp(t, -0.7f, 0.8f), 0f);
					model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, -0.3f, 0.35f));
				} else {
					pose(r, w, Mth.lerp(t, -2.9f, -0.9f), 0f, 0f);
					model.body.xRot = Mth.lerp(w, model.body.xRot, 0.25f * t);
				}
				pose(l, w, -0.4f, 0f, -0.3f);
			}
			case Viperion.ANIM_SWEEP -> {
				float t = ease(Mth.clamp((age - 2f) / 5f, 0f, 1f));
				pose(r, w, -1.55f, Mth.lerp(t, -1.2f, 1.2f), 0f);
				pose(l, w, -0.3f, 0f, -0.6f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, -0.55f, 0.55f));
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.35f);
			}
			case Viperion.ANIM_CHORD -> {
				// the Lyre held out, the left hand strums across it
				float strum = Mth.sin(Mth.clamp((age - 3f) / 4f, 0f, 1f) * (float) Math.PI);
				pose(r, w, -1.35f + model.head.xRot * 0.5f, -0.35f, 0f);
				pose(l, w, -1.2f + model.head.xRot * 0.5f, 0.7f - 0.6f * strum, 0f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, 0.12f);
			}
			case Viperion.ANIM_RESONANCE -> {
				float t = ease(Mth.clamp((age - 6f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -2.9f, -1.0f), 0f, 0.15f);
				pose(l, w, Mth.lerp(t, -2.7f, -0.9f), 0f, -0.15f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(t, -0.5f, 0.3f));
			}
			case Viperion.ANIM_COIL -> {
				float t = Mth.clamp(age / 4f, 0f, 1f);
				pose(r, w, Mth.lerp(t, 0.6f, -2.6f), 0f, 0.3f);
				pose(l, w, Mth.lerp(t, 0.6f, -2.6f), 0f, -0.3f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, Mth.lerp(t, 0.5f, -0.1f));
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.6f * t);
			}
			case Viperion.ANIM_VENOM -> {
				// the fang: a quick jab forward with the Lyre's point
				float t = ease(Mth.clamp((age - 1f) / 2f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.8f, -1.65f) + model.head.xRot * 0.5f, 0.1f, 0f);
				pose(l, w, -0.2f, 0f, -0.4f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.2f * t);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, -0.45f * t);
			}
			case Viperion.ANIM_CHECKPOINT -> {
				// the Lyre raised before his face, both hands on the strings, head up
				pose(r, w, -2.3f, -0.3f, 0f);
				pose(l, w, -2.1f, 0.5f + 0.15f * Mth.sin(age * 0.9f), 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.35f);
			}
			case Viperion.ANIM_REWIND -> {
				// time pours back: arms flung wide, head back
				float t = ease(Mth.clamp(age / 6f, 0f, 1f));
				pose(r, w, -0.5f, 0f, Mth.lerp(t, 0.2f, 1.4f));
				pose(l, w, -0.5f, 0f, Mth.lerp(t, -0.2f, -1.4f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.6f * t);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.15f * t);
			}
			case Viperion.ANIM_CONSTRICT -> {
				float t = ease(Mth.clamp((age - 8f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -1.7f, -1.1f), -0.05f, 0f);
				pose(l, w, Mth.lerp(t, -1.4f, -0.4f), 0.6f, 0f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, -0.2f * t);
			}
			case Viperion.ANIM_SHIELD -> pose(r, w, -1.45f, -0.25f, 0f);
			case Viperion.ANIM_HEAT -> {
				pose(r, w, -2.3f, -0.6f, 0.2f);
				model.head.yRot = Mth.lerp(w, model.head.yRot, model.head.yRot + 0.2f * Mth.sin(age * 0.5f));
			}
			case Viperion.ANIM_HISS -> {
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.3f);
				model.head.z -= 1.5f * w;
				pose(r, w, -0.6f, 0f, 0.9f);
				pose(l, w, -0.6f, 0f, -0.9f);
			}
			default -> {
			}
		}
		model.hat.copyFrom(model.head);
	}

	static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
