package com.projecthero.mod.client.quirk.overhaul;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.overhaul.Overhaul;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Overhaul's keyframed poses, set on the vanilla humanoid model at the end of {@code setupAnim}. Driven by the
 * synced {@code animId / animStart} (his ids 40..59) and the Fusion timer, so every viewer sees the same thing. He
 * touches with his RIGHT hand. Negative {@code xRot} swings a limb forward / up; positive {@code zRot} lifts the right
 * arm out to the side (the left arm mirrors).
 * <ul>
 *   <li><b>Disassemble</b>: a straight open-palm jab; <b>Shrapnel</b>: the jab, then the fingers flicked open;</li>
 *   <li><b>Spike Wave</b>: a crouch, the right palm slapped to the ground; <b>Spike Field</b>: down on one knee, both
 *       palms to the ground;</li>
 *   <li><b>Wall</b>: both palms down, then heaved up; <b>Ramp</b>: a palm to the ground, then thrown forward;</li>
 *   <li><b>Restore</b>: the hand held out, then drawn back to the chest; <b>Reassemble</b>: the palm swept down the
 *       other arm;</li>
 *   <li><b>Field</b>: both palms slammed to the ground; <b>Pit</b>: a point down at the target, the fist closed;</li>
 *   <li><b>Fusion</b>: arms crossed, then flung wide; while fused: a heavy, wide-armed stance; <b>Shard Shot</b>: a
 *       backhand sweep, palm out.</li>
 * </ul>
 */
public final class OverhaulPose {
	private OverhaulPose() {
	}

	public static int length(int animId) {
		if (animId == Overhaul.ANIM_DISASSEMBLE) {
			return 8;
		} else if (animId == Overhaul.ANIM_SHRAPNEL) {
			return 12;
		} else if (animId == Overhaul.ANIM_SPIKE_WAVE) {
			return 14;
		} else if (animId == Overhaul.ANIM_SPIKE_FIELD) {
			return 16;
		} else if (animId == Overhaul.ANIM_WALL) {
			return 14;
		} else if (animId == Overhaul.ANIM_RAMP) {
			return 10;
		} else if (animId == Overhaul.ANIM_RESTORE) {
			return 18;
		} else if (animId == Overhaul.ANIM_REASSEMBLE) {
			return 16;
		} else if (animId == Overhaul.ANIM_FIELD) {
			return 20;
		} else if (animId == Overhaul.ANIM_PIT) {
			return 14;
		} else if (animId == Overhaul.ANIM_FUSION) {
			return 20;
		} else if (animId == Overhaul.ANIM_SHARD) {
			return 10;
		} else if (animId == Overhaul.ANIM_FUSION_END) {
			return 10;
		}
		return 0;
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		QuirkState s = Quirks.peek(player);
		if (s == null || s.animId < Overhaul.ANIM_BASE || s.animId > Overhaul.ANIM_LAST || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Overhaul.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		ModelPart r = model.rightArm, l = model.leftArm;
		if (Overhaul.fused(player)) {
			// the heavy stone stance: arms held out from the plated body, a slow breathing sway
			float sw = Mth.sin((player.tickCount + partial) * 0.08f) * 0.05f;
			r.zRot += 0.32f + sw;
			l.zRot -= 0.32f + sw;
			r.xRot = r.xRot * 0.7f - 0.1f;
			l.xRot = l.xRot * 0.7f - 0.1f;
			model.head.xRot = Mth.lerp(0.3f, model.head.xRot, 0.1f);
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
		if (id == Overhaul.ANIM_DISASSEMBLE) {
			float t = ease(Mth.clamp(age / 2.5f, 0f, 1f));
			pose(r, w, Mth.lerp(t, -0.6f, -1.6f) + model.head.xRot, -0.1f, 0f);
			pose(l, w, 0.25f, 0f, -0.15f);
			model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.2f, -0.3f));
		} else if (id == Overhaul.ANIM_SHRAPNEL) {
			float t = ease(Mth.clamp(age / 2.5f, 0f, 1f));
			float flick = ease(Mth.clamp((age - 5f) / 3f, 0f, 1f));
			pose(r, w, Mth.lerp(t, -0.6f, -1.6f) + model.head.xRot - 0.4f * flick, -0.1f + 0.5f * flick, 0.3f * flick);
			pose(l, w, 0.25f, 0f, -0.15f);
			model.body.yRot = Mth.lerp(w, model.body.yRot, -0.3f + 0.3f * flick);
		} else if (id == Overhaul.ANIM_SPIKE_WAVE) {
			float t = Mth.sin(Mth.clamp(age / 12f, 0f, 1f) * (float) Math.PI);
			model.body.xRot = Mth.lerp(w, model.body.xRot, 0.5f * t);
			pose(r, w, -0.9f - 0.4f * t, -0.1f, 0.15f);
			pose(l, w, 0.4f, 0f, -0.25f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, 0.35f * t);
			crouch(model, w * t);
		} else if (id == Overhaul.ANIM_SPIKE_FIELD) {
			float t = Mth.sin(Mth.clamp(age / 14f, 0f, 1f) * (float) Math.PI);
			model.body.xRot = Mth.lerp(w, model.body.xRot, 0.55f * t);
			pose(r, w, -1.1f * t, -0.2f, 0.25f);
			pose(l, w, -1.1f * t, 0.2f, -0.25f);
			model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -1.2f * t);
			model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.3f * t);
			crouch(model, w * t);
		} else if (id == Overhaul.ANIM_WALL) {
			float down = Mth.clamp(age / 4f, 0f, 1f);
			float heave = ease(Mth.clamp((age - 4f) / 5f, 0f, 1f));
			float a = Mth.lerp(heave, -0.9f * down, -2.3f);
			model.body.xRot = Mth.lerp(w, model.body.xRot, 0.45f * down * (1f - heave));
			pose(r, w, a, -0.15f, 0.1f);
			pose(l, w, a, 0.15f, -0.1f);
			crouch(model, w * down * (1f - heave));
		} else if (id == Overhaul.ANIM_RAMP) {
			float t = ease(Mth.clamp(age / 5f, 0f, 1f));
			pose(r, w, Mth.lerp(t, -0.6f, -2.0f), 0f, 0.1f);
			pose(l, w, Mth.lerp(t, 0.4f, -1.2f), 0f, -0.3f);
			model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, 0.6f * t);
			model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, -0.7f * t);
		} else if (id == Overhaul.ANIM_RESTORE) {
			float t = ease(Mth.clamp((age - 6f) / 6f, 0f, 1f));
			pose(r, w, Mth.lerp(t, -1.45f + model.head.xRot, -0.95f), Mth.lerp(t, 0f, 0.75f), 0f);
			pose(l, w, -0.2f, 0f, -0.1f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, 0.15f);
		} else if (id == Overhaul.ANIM_REASSEMBLE) {
			float t = Mth.clamp(age / 12f, 0f, 1f);
			pose(l, w, -1.2f, 0.35f, 0f);
			pose(r, w, Mth.lerp(t, -1.6f, -0.6f), Mth.lerp(t, -0.7f, -0.3f), 0f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, 0.4f);
		} else if (id == Overhaul.ANIM_FIELD) {
			float down = ease(Mth.clamp(age / 4f, 0f, 1f));
			float hold = Math.min(1f, (len - age) / 6f);
			float t = down * hold;
			model.body.xRot = Mth.lerp(w, model.body.xRot, 0.7f * t);
			pose(r, w, Mth.lerp(down, -2.6f, -1.0f), -0.1f, 0.35f * t);
			pose(l, w, Mth.lerp(down, -2.6f, -1.0f), 0.1f, -0.35f * t);
			model.head.xRot = Mth.lerp(w, model.head.xRot, 0.45f * t);
			crouch(model, w * t);
		} else if (id == Overhaul.ANIM_PIT) {
			float t = ease(Mth.clamp((age - 5f) / 3f, 0f, 1f));
			pose(r, w, -1.0f + 0.25f * t, -0.05f, 0f);
			pose(l, w, 0.2f, 0f, -0.2f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, 0.4f);
		} else if (id == Overhaul.ANIM_FUSION) {
			float open = ease(Mth.clamp((age - 8f) / 5f, 0f, 1f));
			pose(r, w, Mth.lerp(open, -1.3f, -0.9f), Mth.lerp(open, -0.8f, 0.3f), Mth.lerp(open, 0f, 1.0f));
			pose(l, w, Mth.lerp(open, -1.3f, -0.9f), Mth.lerp(open, 0.8f, -0.3f), Mth.lerp(open, 0f, -1.0f));
			model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(open, 0.35f, -0.25f));
			model.body.xRot = Mth.lerp(w, model.body.xRot, Mth.lerp(open, 0.25f, -0.1f));
		} else if (id == Overhaul.ANIM_SHARD) {
			float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
			pose(r, w, Mth.lerp(t, -0.6f, -1.55f) + model.head.xRot, Mth.lerp(t, 0.9f, -0.35f), 0f);
			pose(l, w, 0.2f, 0f, -0.2f);
			model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(t, 0.4f, -0.3f));
		} else if (id == Overhaul.ANIM_FUSION_END) {
			pose(r, w, 0.1f, 0f, 0.6f);
			pose(l, w, 0.1f, 0f, -0.6f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, 0.3f);
		}
		model.hat.copyFrom(model.head);
	}

	/** A crouch: legs bent, body lowered (the model's sneak offsets, weighted). */
	private static void crouch(HumanoidModel<?> model, float w) {
		if (w <= 0f || model.crouching) {
			return;
		}
		model.body.y += 3.2f * w;
		model.head.y += 4.2f * w;
		model.rightArm.y += 3.2f * w;
		model.leftArm.y += 3.2f * w;
		model.rightLeg.z += 4.0f * w;
		model.leftLeg.z += 4.0f * w;
		model.rightLeg.y += 0.2f * w;
		model.leftLeg.y += 0.2f * w;
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
