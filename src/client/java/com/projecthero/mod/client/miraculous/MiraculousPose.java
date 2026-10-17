package com.projecthero.mod.client.miraculous;

import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: the shared transformation pose, run by {@link MiraculousClient#applyPose} BEFORE the kit's pose (a kit can
 * override it during its own anim ids). Over {@link MiraculousConfig#TRANSFORM_TICKS}: the arms sweep out and up into
 * the light, head tipped back; at the flash the hero drops into a landing pose (one fist forward, the other arm back,
 * a stride, head down); then it eases back to the normal stance over {@link #EASE_OUT} ticks.
 */
public final class MiraculousPose {
	static final int EASE_OUT = 10;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] ARMS_OUT = { -0.35f, -0.25f, 1.95f, -0.25f, -1.95f, 0f, 0.12f, 0f, -0.12f, 0f };
	private static final float[] LANDING = { 0.4f, 0.75f, 0.45f, -1.45f, -0.15f, -0.65f, 0.08f, 0.55f, -0.12f, 0.22f };

	private MiraculousPose() {
	}

	static void apply(Player player, HumanoidModel<?> model, MiraculousState s) {
		if (!s.transformed || s.animId != MiraculousState.ANIM_TRANSFORM) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float t = (float) (player.level().getGameTime() - s.formChangeAt) + mc.getTimer().getGameTimeDeltaPartialTick(false);
		int total = MiraculousConfig.TRANSFORM_TICKS;
		if (t < 0f || t > total + EASE_OUT) {
			return;
		}
		float flash = MiraculousConfig.FLASH_TICK;
		float[] pose;
		float w;
		if (t < flash - 4f) {
			pose = ARMS_OUT;
			w = smooth(Math.min(1f, t / 10f));
		} else if (t < flash + 2f) {
			pose = blend(ARMS_OUT, LANDING, smooth((t - (flash - 4f)) / 6f));
			w = 1f;
		} else if (t <= total) {
			pose = LANDING;
			w = 1f;
		} else {
			pose = LANDING;
			w = 1f - smooth((t - total) / EASE_OUT);
		}
		set(model.head, pose[0], 0f, w, true);
		set(model.rightArm, pose[1], pose[2], w, false);
		set(model.leftArm, pose[3], pose[4], w, false);
		set(model.rightLeg, pose[5], pose[6], w, false);
		set(model.leftLeg, pose[7], pose[8], w, false);
		model.body.xRot = Mth.lerp(w, model.body.xRot, pose[9]);
		model.hat.copyFrom(model.head);
	}

	private static void set(ModelPart part, float x, float z, float w, boolean head) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		if (!head) {
			part.zRot = Mth.lerp(w, part.zRot, z);
			part.yRot = Mth.lerp(w, part.yRot, 0f);
		}
	}

	private static float[] blend(float[] a, float[] b, float f) {
		float[] o = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			o[i] = Mth.lerp(f, a[i], b[i]);
		}
		return o;
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

}
