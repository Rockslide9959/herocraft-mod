package com.projecthero.mod.client.miraculous.multimouse;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.multimouse.Multimouse;
import com.projecthero.mod.miraculous.multimouse.MultimouseConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: Multimouse's keyframed poses, set on the vanilla humanoid model at the end of {@code setupAnim} (the suit model
 * copies it). Driven by the synced {@code animId / animStart} (her ids 160..179) and her timers, so every viewer sees the
 * same thing. The rope is in her RIGHT hand. Negative {@code xRot} swings a limb forward / up; positive {@code zRot} lifts
 * the right arm out to the side.
 */
public final class MultimousePose {
	private MultimousePose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case Multimouse.ANIM_LASH, Multimouse.ANIM_LASH_2, Multimouse.ANIM_LASH_3 -> MultimouseConfig.LASH_SWING_TICKS;
			case Multimouse.ANIM_LASSO -> 16;
			case Multimouse.ANIM_TWIRL -> 8;
			case Multimouse.ANIM_TRIP -> 14;
			case Multimouse.ANIM_SWING -> 8;
			case Multimouse.ANIM_SCURRY -> 10;
			case Multimouse.ANIM_SWARM -> 12;
			case Multimouse.ANIM_MULTITUDE -> 24;
			case Multimouse.ANIM_RECOMBINE -> 20;
			case Multimouse.ANIM_HEARING -> 18;
			case Multimouse.ANIM_GNAW -> 14;
			case Multimouse.ANIM_BIND -> 14;
			case Multimouse.ANIM_TRAP -> 14;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || s.animId < 160 || s.animId > 179 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Multimouse.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float time = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		if (Multimouse.twirling(player)) {
			// the rope spun overhead: the right hand circling above her head
			pose(r, 1f, -2.7f + 0.15f * Mth.sin(time * 0.9f), 0.25f * Mth.cos(time * 0.9f), 0.2f);
		}
		if (Multimouse.running(player, Multimouse.V_SWING)) {
			// hanging off the rope: right arm straight up toward the hook, the left out for balance
			pose(r, 1f, -2.9f, 0f, 0.1f);
			pose(l, 1f, -0.4f, 0f, -0.5f);
			model.rightLeg.xRot = -0.35f;
			model.leftLeg.xRot = 0.25f;
		}
		if (Multimouse.running(player, Multimouse.V_BIND)) {
			pose(r, 1f, -1.4f + model.head.xRot, -0.05f, 0f);
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
			case Multimouse.ANIM_LASH -> {
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, -1.5f, Mth.lerp(t, 1.0f, -0.7f), 0.15f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.35f, -0.3f));
			}
			case Multimouse.ANIM_LASH_2 -> {
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, -1.8f, Mth.lerp(t, -0.7f, 0.9f), -0.1f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, -0.3f, 0.35f));
			}
			case Multimouse.ANIM_LASH_3 -> {
				// the overhead crack
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -3.0f, -0.9f), -0.1f, 0f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3f * t);
			}
			case Multimouse.ANIM_LASSO -> {
				// the throw, then a hard yank back
				float t = ease(Mth.clamp((age - 6f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -1.7f, -0.6f), 0f, 0.1f);
				pose(l, w, Mth.lerp(t, -1.2f, -0.5f), 0.3f, 0f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, -0.15f * t);
			}
			case Multimouse.ANIM_TRIP -> {
				// a low sweep: crouched, the arm whipping across close to the ground
				float t = ease(Mth.clamp((age - 2f) / 5f, 0f, 1f));
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.5f);
				pose(r, w, -0.6f, Mth.lerp(t, -1.0f, 1.0f), 0.6f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.5f);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.4f);
			}
			case Multimouse.ANIM_SWING -> pose(r, w, -2.9f, 0f, 0.1f);
			case Multimouse.ANIM_SCURRY, Multimouse.ANIM_MULTITUDE -> {
				// arms flung wide in a burst of sparkle
				float t = ease(Mth.clamp(age / 5f, 0f, 1f));
				pose(r, w, -0.4f, 0f, Mth.lerp(t, 0.2f, 1.6f));
				pose(l, w, -0.4f, 0f, Mth.lerp(t, -0.2f, -1.6f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.3f * t);
			}
			case Multimouse.ANIM_RECOMBINE -> {
				pose(r, w, -1.2f, -0.6f, 0f);
				pose(l, w, -1.2f, 0.6f, 0f);
			}
			case Multimouse.ANIM_SWARM -> pose(r, w, -1.55f + model.head.xRot, -0.1f, 0f);
			case Multimouse.ANIM_HEARING -> {
				// a hand cupped to the ear, the head tilted
				pose(r, w, -2.5f, -0.5f, 0.3f);
				model.head.zRot = Mth.lerp(w, model.head.zRot, -0.25f);
			}
			case Multimouse.ANIM_GNAW -> {
				model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot + 0.25f * Mth.sin(age * 2.2f));
				pose(r, w, -1.3f, -0.3f, 0f);
				pose(l, w, -1.3f, 0.3f, 0f);
			}
			case Multimouse.ANIM_BIND -> {
				float t = ease(Mth.clamp((age - 2f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -2.6f, -1.5f), 0f, 0.3f * (1f - t));
			}
			case Multimouse.ANIM_TRAP -> {
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.6f);
				pose(r, w, -1.0f, 0f, 0.1f);
				pose(l, w, -1.0f, 0f, -0.1f);
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
