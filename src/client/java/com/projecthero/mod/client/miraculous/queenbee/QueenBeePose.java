package com.projecthero.mod.client.miraculous.queenbee;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.queenbee.QueenBee;
import com.projecthero.mod.miraculous.queenbee.QueenBeeConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2: Queen Bee's keyframed poses, set on the vanilla humanoid model at the end of {@code setupAnim} (the suit model
 * copies it). Driven by the synced {@code animId / animStart} (her ids 180..199) and her timers, so every viewer sees the
 * same thing. The top is in her RIGHT hand. Negative {@code xRot} swings a limb forward / up; positive {@code zRot} lifts
 * the right arm out to the side.
 */
public final class QueenBeePose {
	private QueenBeePose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case QueenBee.ANIM_STRIKE, QueenBee.ANIM_STRIKE_2, QueenBee.ANIM_STRIKE_3 -> QueenBeeConfig.STRIKE_SWING_TICKS;
			case QueenBee.ANIM_THROW -> 12;
			case QueenBee.ANIM_SHIELD -> 8;
			case QueenBee.ANIM_TANGLE -> 14;
			case QueenBee.ANIM_SWING -> 8;
			case QueenBee.ANIM_COPTER -> 10;
			case QueenBee.ANIM_DIVE -> 12;
			case QueenBee.ANIM_VENOM -> 30;
			case QueenBee.ANIM_VENOM_TOUCH -> 10;
			case QueenBee.ANIM_HIVE -> 18;
			case QueenBee.ANIM_COMMAND -> 24;
			case QueenBee.ANIM_STING -> 10;
			case QueenBee.ANIM_VOLLEY -> 12;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || s.animId < 180 || s.animId > 199 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	/** Is the top in her right hand right now (a move with it plays, or a held move runs)? */
	public static boolean inHand(Player player, float partial) {
		if (QueenBee.topAway(player)) {
			return false;
		}
		if (QueenBee.copter(player) || QueenBee.shielding(player) || QueenBee.running(player, QueenBee.V_SWING) || QueenBee.running(player, QueenBee.V_TANGLE)
				|| QueenBee.venomArmed(player) || QueenBee.val(player, QueenBee.V_DIVE) != 0L) {
			return true;
		}
		float[] p = playing(player, partial);
		return p != null && p[0] != QueenBee.ANIM_HIVE && p[0] != QueenBee.ANIM_COMMAND && p[0] != QueenBee.ANIM_VOLLEY;
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!QueenBee.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float time = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		if (QueenBee.copter(player)) {
			// the top spun overhead like a rotor: right arm straight up, legs trailing
			pose(r, 1f, -3.05f, 0f, -0.05f);
			pose(l, 1f, -0.2f + 0.05f * Mth.sin(time * 0.4f), 0f, -0.35f);
			model.rightLeg.xRot = 0.15f + 0.08f * Mth.sin(time * 0.3f);
			model.leftLeg.xRot = 0.3f - 0.08f * Mth.sin(time * 0.3f);
		}
		if (QueenBee.shielding(player)) {
			// the top spinning before her on a short string
			pose(r, 1f, -1.45f + model.head.xRot * 0.5f, -0.15f, 0f);
		}
		if (QueenBee.running(player, QueenBee.V_SWING)) {
			pose(r, 1f, -2.9f, 0f, 0.1f);
			pose(l, 1f, -0.4f, 0f, -0.5f);
			model.rightLeg.xRot = -0.35f;
			model.leftLeg.xRot = 0.25f;
		}
		if (QueenBee.running(player, QueenBee.V_TANGLE)) {
			pose(r, 1f, -1.4f + model.head.xRot, -0.05f, 0f);
		}
		long dive = QueenBee.val(player, QueenBee.V_DIVE);
		if (dive == 2L) {
			// point-first: body pitched down, the top held below in both hands
			model.body.xRot = 0.5f;
			pose(r, 1f, -0.5f, 0.2f, 0f);
			pose(l, 1f, -0.5f, -0.2f, 0f);
			model.rightLeg.xRot = 0.2f;
			model.leftLeg.xRot = 0.2f;
		} else if (QueenBee.venomArmed(player)) {
			// the glowing top held at the ready
			pose(r, 1f, -1.1f, -0.2f, 0.1f);
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
			case QueenBee.ANIM_STRIKE -> {
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, -1.5f, Mth.lerp(t, 1.0f, -0.7f), 0.15f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.35f, -0.3f));
			}
			case QueenBee.ANIM_STRIKE_2 -> {
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, -1.8f, Mth.lerp(t, -0.7f, 0.9f), -0.1f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, -0.3f, 0.35f));
			}
			case QueenBee.ANIM_STRIKE_3 -> {
				// the overhead smash
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -3.0f, -0.9f), -0.1f, 0f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3f * t);
			}
			case QueenBee.ANIM_THROW -> {
				// wound back, then flung forward
				float t = ease(Mth.clamp((age - 2f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -2.6f, -1.3f), 0f, Mth.lerp(t, 0.4f, 0f));
				pose(l, w, -0.8f, 0.4f, 0f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.4f, -0.25f));
			}
			case QueenBee.ANIM_TANGLE -> {
				float t = ease(Mth.clamp((age - 2f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -2.6f, -1.5f), 0f, 0.3f * (1f - t));
			}
			case QueenBee.ANIM_SWING -> pose(r, w, -2.9f, 0f, 0.1f);
			case QueenBee.ANIM_COPTER -> {
				float t = ease(Mth.clamp(age / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -1.5f, -3.05f), 0f, 0f);
			}
			case QueenBee.ANIM_DIVE -> {
				// the leap: arms flung up
				pose(r, w, -2.8f, 0f, 0.25f);
				pose(l, w, -2.8f, 0f, -0.25f);
			}
			case QueenBee.ANIM_VENOM -> {
				// "Venom!": the top raised high, glowing, head back
				float t = ease(Mth.clamp(age / 6f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -1.2f, -2.95f), -0.1f, 0.15f);
				pose(l, w, -0.3f, 0f, -0.45f * t);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.35f * t);
			}
			case QueenBee.ANIM_VENOM_TOUCH, QueenBee.ANIM_STING -> {
				// the point thrust straight out
				float t = ease(Mth.clamp((age - 1f) / 2f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -0.6f, -1.6f) + model.head.xRot * 0.5f, -0.1f, 0f);
				pose(l, w, 0.4f * t, 0f, -0.2f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, -0.25f * t);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.45f * t);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.35f * t);
			}
			case QueenBee.ANIM_HIVE -> {
				// two fingers to the temple
				pose(r, w, -2.3f, -0.6f, 0.35f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.15f);
			}
			case QueenBee.ANIM_COMMAND -> {
				// "Bow before your queen!": the arm sweeps out and points, the other hand on her hip
				float t = ease(Mth.clamp((age - 2f) / 6f, 0f, 1f));
				pose(r, w, -1.6f, Mth.lerp(t, 0.6f, -0.4f), 0.0f);
				pose(l, w, 0.2f, 0f, -0.8f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.25f);
			}
			case QueenBee.ANIM_VOLLEY -> pose(l, w, -1.55f + model.head.xRot, 0.1f, 0f);
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
