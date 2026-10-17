package com.projecthero.mod.client.miraculous.hawkmoth;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothConfig;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Hawk Moth's keyframed poses on the vanilla humanoid model (run at the end of {@code setupAnim}; the suit
 * layer copies it), driven by the synced {@code MiraculousState} (animId / animStart and the kit values), so every
 * viewer sees the same. Negative {@code xRot} swings an arm forward / up.
 * <ul>
 *   <li>Idle: the cane held planted a little in front.</li>
 *   <li>Cane Strike: a diagonal cut, a backhand, an overhead smash. Cane Sword: both hands on the cane, the off hand
 *       pulls the sheath down off the blade, then the thrust and an en-garde.</li>
 *   <li>Akuma: the off hand palm up, lifted as the butterfly leaves it. Recall: the off hand held out, beckoning.</li>
 *   <li>Swarm Flight: arms out and back, carried. Swarm Dash: swept back (he is hidden in the cloud).</li>
 *   <li>Butterfly Shield: the off hand pushed out at the wall. Swarm Blind: both arms flung wide.</li>
 *   <li>RISE, AKUMA!: hands cupped before the chest, head bowed over the butterfly, then opened upward.</li>
 *   <li>Window: the off hand on the brooch, head bowed, still. Emotion Sense / Akuma Link: a hand to the brooch / beside the
 *       face.</li>
 * </ul>
 */
public final class HawkMothPose {
	/** The cane's tilt back from the line of the forearm, radians: held as a cane, and as a sword. */
	public static final float HOLD_CANE = 1.0f;
	public static final float HOLD_SWORD = 0.3f;

	private HawkMothPose() {
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/** The anim's age (ticks), or -1 when {@code s} isn't playing {@code anim}. */
	private static float age(Player player, MiraculousState s, int anim, float partial) {
		if (s.animId != anim) {
			return -1f;
		}
		float a = player.level().getGameTime() - s.animStart + partial;
		return a < 0f && a > -20f ? 0f : a;
	}

	private static final int SWORD_TOTAL = HawkMothConfig.SWORD_DRAW_TICKS + HawkMothConfig.SWORD_LUNGE_TICKS + HawkMothConfig.SWORD_SHOW_TICKS;

	/** How far the blade is out of the shaft, 0..1 (the sheath slides off; back in at the end). */
	public static float slide(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return 0f;
		}
		float a = age(player, s, HawkMothKit.ANIM_SWORD, partial);
		if (a < 0f) {
			return 0f;
		}
		if (a < HawkMothConfig.SWORD_DRAW_TICKS) {
			return a / HawkMothConfig.SWORD_DRAW_TICKS;
		}
		if (a < SWORD_TOTAL) {
			return 1f;
		}
		return Mth.clamp(1f - (a - SWORD_TOTAL) / 5f, 0f, 1f);
	}

	/** The cane's tilt from the forearm right now. */
	public static float tilt(Player player, float partial) {
		float sw = Mth.clamp(slide(player, partial) * 1.5f, 0f, 1f);
		return Mth.lerp(sw, HOLD_CANE, HOLD_SWORD);
	}

	/** Whether the cane is in his hand: transformed (the suit mostly on), main hand empty, not mid-Rise or hidden in a dash. */
	public static boolean caneShown(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.NOOROO.id.equals(s.kwami) || player.isInvisible() || !player.getMainHandItem().isEmpty()) {
			return false;
		}
		if (HawkMothSuitRender.progress(player, partial) < 0.6f) {
			return false;
		}
		float rise = age(player, s, HawkMothKit.ANIM_RISE, partial);
		return rise < 0f || rise >= HawkMothConfig.RISE_CUP_TICKS + 8;
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.NOOROO.id.equals(s.kwami) || player.level() == null || !s.transformed) {
			return;
		}
		float partial = partial();
		long now = player.level().getGameTime();
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart main = rightMain ? model.rightArm : model.leftArm;
		ModelPart off = rightMain ? model.leftArm : model.rightArm;
		boolean anim = s.animId >= 80 && s.animId < 100 && now - s.animStart < length(s.animId) + 2;

		// idle: the cane planted a little in front (vanilla's own swing still plays on top while he punches)
		if (caneShown(player, partial) && model.attackTime <= 0f && !anim) {
			main.xRot = main.xRot * 0.3f - 0.62f;
			main.yRot = in(model, main, 0.12f);
		}
		if (s.val(HawkMothKit.V_FLIGHT_UNTIL) > now) {
			// carried by the swarm: arms out and a little back, legs trailing
			pose(model, model.rightArm, 1f, 0.3f, 0f, out(model, model.rightArm, 0.55f));
			pose(model, model.leftArm, 1f, 0.3f, 0f, out(model, model.leftArm, 0.55f));
			if (caneShown(player, partial)) {
				pose(model, main, 1f, -0.35f, 0f, out(model, main, 0.3f));
			}
			model.rightLeg.xRot = Mth.lerp(0.8f, model.rightLeg.xRot, 0.2f);
			model.leftLeg.xRot = Mth.lerp(0.8f, model.leftLeg.xRot, 0.12f);
			model.body.xRot = Mth.lerp(0.8f, model.body.xRot, 0.12f);
		}
		if (s.val(HawkMothKit.V_SHIELD_AT) > 0L) {
			// the off hand pushed out at the wall of butterflies
			pose(model, off, 1f, -1.5f + model.head.xRot * 0.4f, model.head.yRot * 0.5f + in(model, off, 0.2f), 0f);
			pose(model, main, 1f, -0.7f, in(model, main, 0.15f), 0f);
		}
		if (s.val(HawkMothKit.V_WINDOW_MOB) != 0L) {
			// the window: the off hand on the brooch, head bowed, still
			pose(model, off, 1f, -1.15f, in(model, off, 0.85f), 0f);
			model.head.xRot = Mth.lerp(0.7f, model.head.xRot, 0.35f);
			model.hat.copyFrom(model.head);
		}
		if (!anim) {
			return;
		}
		float age = now - s.animStart + partial;
		int length = length(s.animId);
		if (age < 0f || age >= length) {
			return;
		}
		float w = Math.min(1f, age / 2.5f) * Math.min(1f, (length - age) / 4f);
		switch (s.animId) {
			case HawkMothKit.ANIM_STRIKE_1 -> {
				// a diagonal cut, high outside to low inside
				float t = smooth(age / 5f);
				pose(model, main, w, Mth.lerp(t, -2.7f, -0.9f), Mth.lerp(t, out(model, main, 0.6f), in(model, main, 0.55f)), 0f);
				pose(model, off, w, -0.3f, 0f, out(model, off, 0.25f));
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.25f, -0.3f) * (rightMain ? 1 : -1));
			}
			case HawkMothKit.ANIM_STRIKE_2 -> {
				// the backhand, across and out
				float t = smooth(age / 5f);
				pose(model, main, w, -1.55f, Mth.lerp(t, in(model, main, 0.75f), out(model, main, 0.7f)), 0f);
				pose(model, off, w, -0.2f, 0f, out(model, off, 0.3f));
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, -0.3f, 0.3f) * (rightMain ? 1 : -1));
			}
			case HawkMothKit.ANIM_STRIKE_3 -> {
				// up overhead with both hands, then smashed down
				float t = smooth((age - 2f) / 4f);
				pose(model, main, w, Mth.lerp(t, -3.0f, -0.85f), 0f, 0f);
				pose(model, off, w, Mth.lerp(t, -2.8f, -0.9f), in(model, off, 0.35f), 0f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35f * t);
			}
			case HawkMothKit.ANIM_SWORD -> {
				float draw = HawkMothConfig.SWORD_DRAW_TICKS;
				float lunge = draw + HawkMothConfig.SWORD_LUNGE_TICKS;
				if (age < draw) {
					// both hands on the cane at the waist; the off hand pulls the sheath down off the blade
					float t = age / draw;
					pose(model, main, w, -0.85f, in(model, main, 0.45f), 0f);
					pose(model, off, w, Mth.lerp(t, -0.85f, -0.25f), in(model, off, Mth.lerp(t, 0.55f, 0.2f)), 0f);
				} else if (age < lunge + 2f) {
					// the thrust, along the look
					pose(model, main, 1f, model.head.xRot - 1.87f, model.head.yRot, 0f);
					pose(model, off, 1f, 0.7f, 0f, out(model, off, 0.25f));
					model.body.xRot = Mth.lerp(0.9f, model.body.xRot, 0.3f);
					model.rightLeg.xRot = rightMain ? -0.6f : 0.4f;
					model.leftLeg.xRot = rightMain ? 0.4f : -0.6f;
				} else {
					// en garde: the blade forward-down, the sheath held back
					pose(model, main, w, -1.35f, in(model, main, 0.1f), 0f);
					pose(model, off, w, -0.35f, 0f, out(model, off, 0.3f));
				}
			}
			case HawkMothKit.ANIM_AKUMA -> {
				// the off hand palm up, lifting as the butterfly leaves it
				float t = smooth(age / 10f);
				pose(model, off, w, Mth.lerp(t, -1.15f, -1.75f), in(model, off, 0.2f), 0f);
			}
			case HawkMothKit.ANIM_RECALL -> {
				// the off hand held out, palm up, calling them home
				pose(model, off, w, -1.6f, out(model, off, 0.1f), out(model, off, 0.2f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.1f);
				model.hat.copyFrom(model.head);
			}
			case HawkMothKit.ANIM_DASH -> {
				pose(model, model.rightArm, w, 0.9f, 0f, 0.25f);
				pose(model, model.leftArm, w, 0.9f, 0f, -0.25f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.4f);
			}
			case HawkMothKit.ANIM_RISE -> {
				float cup = HawkMothConfig.RISE_CUP_TICKS;
				if (age < cup) {
					// hands cupped before the chest round the white butterfly, head bowed over it
					float breath = 0.04f * Mth.sin(age * 0.4f);
					pose(model, model.rightArm, w, -1.05f + breath, -0.5f, 0f);
					pose(model, model.leftArm, w, -1.05f + breath, 0.5f, 0f);
					model.head.xRot = Mth.lerp(w, model.head.xRot, 0.45f);
				} else {
					// opened upward: it flies
					float t = smooth((age - cup) / 5f);
					pose(model, model.rightArm, w, Mth.lerp(t, -1.05f, -1.9f), Mth.lerp(t, -0.5f, -0.15f), 0.1f * t);
					pose(model, model.leftArm, w, Mth.lerp(t, -1.05f, -1.9f), Mth.lerp(t, 0.5f, 0.15f), -0.1f * t);
					model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(t, 0.45f, -0.2f));
				}
				model.hat.copyFrom(model.head);
			}
			case HawkMothKit.ANIM_BLIND -> {
				// both arms flung wide, sending the swarm out
				float t = smooth(age / 6f);
				pose(model, model.rightArm, w, Mth.lerp(t, -1.4f, -0.6f), Mth.lerp(t, -0.5f, 0f), Mth.lerp(t, 0f, 1.3f));
				pose(model, model.leftArm, w, Mth.lerp(t, -1.4f, -0.6f), Mth.lerp(t, 0.5f, 0f), Mth.lerp(t, 0f, -1.3f));
			}
			case HawkMothKit.ANIM_SENSE -> {
				// a hand to the brooch, eyes closed, feeling
				pose(model, off, w, -1.15f, in(model, off, 0.85f), 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.2f);
				model.hat.copyFrom(model.head);
			}
			case HawkMothKit.ANIM_LINK -> {
				// the hand raised beside the face (the butterfly sign glows on his Champion's)
				pose(model, off, w, -2.5f, in(model, off, 0.35f), out(model, off, -0.4f));
			}
			default -> {
			}
		}
	}

	public static int length(int animId) {
		return switch (animId) {
			case HawkMothKit.ANIM_STRIKE_1, HawkMothKit.ANIM_STRIKE_2 -> 10;
			case HawkMothKit.ANIM_STRIKE_3 -> 12;
			case HawkMothKit.ANIM_SWORD -> SWORD_TOTAL + 5;
			case HawkMothKit.ANIM_AKUMA -> 16;
			case HawkMothKit.ANIM_RECALL -> 18;
			case HawkMothKit.ANIM_DASH -> HawkMothConfig.DASH_TICKS + HawkMothConfig.DASH_REFORM_TICKS + 2;
			case HawkMothKit.ANIM_RISE -> HawkMothConfig.RISE_CUP_TICKS + 10;
			case HawkMothKit.ANIM_BLIND -> 14;
			case HawkMothKit.ANIM_SENSE -> 22;
			case HawkMothKit.ANIM_LINK -> 24;
			default -> 0;
		};
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** {@code a} as a yRot that swings this arm in across the body. */
	private static float in(HumanoidModel<?> model, ModelPart arm, float a) {
		return arm == model.rightArm ? -a : a;
	}

	/** {@code a} as a zRot that lifts this arm out to its side. */
	private static float out(HumanoidModel<?> model, ModelPart arm, float a) {
		return arm == model.rightArm ? a : -a;
	}

	private static void pose(HumanoidModel<?> model, ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
