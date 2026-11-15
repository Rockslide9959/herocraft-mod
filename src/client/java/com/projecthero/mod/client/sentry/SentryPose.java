package com.projecthero.mod.client.sentry;

import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.data.SentryState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Sentry's limb poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} after the shared flight pose (the
 * suit layer copies the model, so the suit follows). Negative {@code xRot} swings an arm forward / up.
 * <ul>
 *   <li><b>Sunbeam</b>: both arms straight out ahead at the look, palms together.</li>
 *   <li><b>A Million Suns</b>: the arms spread wide and rise as the light gathers.</li>
 *   <li><b>Molecular Grip</b>: the main arm held out, fingers clawed at the lifted creature.</li>
 *   <li><b>Calm</b>: legs crossed, hands resting on the knees (he floats a little off the ground).</li>
 *   <li><b>The kneel</b> after the Void is thrown out: down on one knee, head bowed.</li>
 *   <li><b>The takeover</b>: arms hanging loose and back, head tipped, the body drifting like a puppet.</li>
 * </ul>
 */
public final class SentryPose {
	private SentryPose() {
	}

	public static void apply(Player player, HumanoidModel<?> m, float age) {
		SentryState s = Sentry.peek(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		long now = player.level().getGameTime();
		float pitch = m.head.xRot;
		if (s.inTakeover()) {
			float sway = Mth.sin(age * 0.08f);
			m.rightArm.xRot = 0.35f + 0.1f * sway;
			m.leftArm.xRot = 0.35f - 0.1f * sway;
			m.rightArm.zRot = 0.35f;
			m.leftArm.zRot = -0.35f;
			m.rightLeg.xRot = 0.15f + 0.05f * sway;
			m.leftLeg.xRot = 0.05f - 0.05f * sway;
			m.head.zRot = 0.12f * sway;
			return;
		}
		if (s.kneelUntil > now) {
			m.head.xRot = 0.7f;
			m.rightLeg.xRot = -1.45f;
			m.rightLeg.yRot = 0.1f;
			m.leftLeg.xRot = 0.0f;
			m.rightArm.xRot = -0.3f;
			m.leftArm.xRot = 0.4f;
			m.rightArm.zRot = 0.1f;
			m.leftArm.zRot = -0.1f;
			return;
		}
		if (s.calming) {
			m.rightLeg.xRot = -1.4f;
			m.leftLeg.xRot = -1.4f;
			m.rightLeg.yRot = 0.6f;
			m.leftLeg.yRot = -0.6f;
			m.rightLeg.zRot = 0.05f;
			m.leftLeg.zRot = -0.05f;
			float breathe = Mth.sin(age * 0.07f) * 0.04f;
			m.rightArm.xRot = -0.65f + breathe;
			m.leftArm.xRot = -0.65f + breathe;
			m.rightArm.zRot = -0.25f;
			m.leftArm.zRot = 0.25f;
			m.head.xRot = 0.25f;
			return;
		}
		if (s.sunsStart != 0L) {
			float k = Mth.clamp((now - s.sunsStart) / (float) SentryConfig.SUNS_CHARGE_TICKS, 0f, 1f);
			m.rightArm.xRot = -0.2f - 0.9f * k;
			m.leftArm.xRot = -0.2f - 0.9f * k;
			m.rightArm.zRot = 1.1f + 0.5f * k;
			m.leftArm.zRot = -1.1f - 0.5f * k;
			m.head.xRot = -0.35f * k;
			return;
		}
		if (s.beamStart != 0L) {
			float aim = -Mth.HALF_PI + pitch;
			m.rightArm.xRot = aim;
			m.leftArm.xRot = aim;
			m.rightArm.yRot = -0.18f;
			m.leftArm.yRot = 0.18f;
			m.rightArm.zRot = 0f;
			m.leftArm.zRot = 0f;
			return;
		}
		if (s.gripTarget >= 0) {
			m.rightArm.xRot = -Mth.HALF_PI + pitch;
			m.rightArm.yRot = -0.1f;
			m.rightArm.zRot = 0.05f * Mth.sin(age * 0.9f);
		}
	}
}
