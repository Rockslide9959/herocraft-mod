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
 *   <li><b>Solar Gaze</b>: chest out, fists clenched back and down at his sides, the head locked on the look.</li>
 *   <li><b>A Million Suns</b>: the arms spread wide and rise as the light gathers.</li>
 *   <li><b>Disassemble</b>: both hands out at the creature, clawed, pulling apart as it unravels.</li>
 *   <li><b>Calm</b>: legs crossed, hands resting on the knees. <b>The kneel</b> after the Void is thrown out.</li>
 *   <li>Moveset 2 short poses ({@link SentryState#pose}): the Haymaker wind-through, Skyward Hurl (both arms thrown up), the Sunfall leap
 *   (knees tucked, fists up) and dive (both fists leading), Golden Guardian (arms spread, face up), Molecular Shield (arms crossed then
 *   flung open); the Void's Erase (one hand out, fingers spread), Shadow Pool (pointing down), Swallow (a fist closing), Nightmare (arms
 *   wide, head thrown back), Eclipse (arms raised in a V), Blot Out (pulling in), Shadow Clones (palms out low), Impale (a thrust).</li>
 *   <li><b>The takeover</b>: arms hanging loose and back, head tipped, the body drifting like a puppet -- except while the Void attacks
 *   (its move poses play then).</li>
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
		int pose = Sentry.poseNow(player);
		float pk = pose == 0 ? 0f : Mth.clamp((now - s.poseAt + (age - (int) age)) / Math.max(1f, s.poseTicks), 0f, 1f);
		if (s.inTakeover() && pose == 0) {
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
			float tremble = k > 0.75f ? 0.04f * Mth.sin(age * 2.5f) : 0f;
			m.rightArm.xRot = -0.2f - 0.9f * k + tremble;
			m.leftArm.xRot = -0.2f - 0.9f * k - tremble;
			m.rightArm.zRot = 1.1f + 0.5f * k;
			m.leftArm.zRot = -1.1f - 0.5f * k;
			m.head.xRot = -0.35f * k;
			m.rightLeg.xRot = 0.1f * k;
			m.leftLeg.xRot = -0.1f * k;
			return;
		}
		if (s.beamStart != 0L) {
			// Solar Gaze: chest out, fists back and down, the head locked on the look
			m.rightArm.xRot = 0.35f;
			m.leftArm.xRot = 0.35f;
			m.rightArm.zRot = 0.3f;
			m.leftArm.zRot = -0.3f;
			m.rightArm.yRot = 0f;
			m.leftArm.yRot = 0f;
			return;
		}
		if (s.gripTarget >= 0) {
			// Disassemble: both hands out, clawed, pulling apart as the molecules come loose
			float k = Mth.clamp((now - s.gripStart) / (float) SentryConfig.UNMAKE_TEAR_TICKS, 0f, 1f);
			float shake = 0.03f + 0.05f * k;
			m.rightArm.xRot = -Mth.HALF_PI + pitch + shake * Mth.sin(age * 1.7f);
			m.leftArm.xRot = -Mth.HALF_PI + pitch + shake * Mth.sin(age * 1.9f + 1f);
			m.rightArm.yRot = -0.15f - 0.45f * k;
			m.leftArm.yRot = 0.15f + 0.45f * k;
			m.rightArm.zRot = 0f;
			m.leftArm.zRot = 0f;
			return;
		}
		if (pose != 0) {
			applyMovePose(m, pose, pk, pitch, age);
		}
	}

	private static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	/** A move's short pose, {@code k} 0..1 through it. */
	private static void applyMovePose(HumanoidModel<?> m, int pose, float k, float pitch, float age) {
		float in = ease(Math.min(1f, k * 4f)); // snaps in over the first quarter
		float out = k > 0.75f ? ease((1f - k) / 0.25f) : 1f; // and eases back out
		float w = in * out;
		switch (pose) {
			case SentryState.POSE_HAYMAKER -> {
				// the big right hand: cocked back, then driven through
				float drive = k < 0.35f ? -k / 0.35f : (k - 0.35f) / 0.65f;
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -Mth.HALF_PI + pitch + 1.2f * (1f - Math.abs(drive)) * (drive < 0 ? 1f : -0.3f));
				m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, drive < 0 ? 0.6f : -0.3f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0.4f);
			}
			case SentryState.POSE_HURL -> {
				// both arms thrown up to the sky
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -2.9f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -2.9f);
				m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.15f);
				m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.15f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.6f);
			}
			case SentryState.POSE_LEAP -> {
				m.rightLeg.xRot = -1.1f * w;
				m.leftLeg.xRot = -0.6f * w;
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -2.6f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -2.6f);
			}
			case SentryState.POSE_DIVE -> {
				// both fists leading the dive (the body is pitched by the flight pose)
				float a = Math.min(1f, k * 12f);
				m.rightArm.xRot = Mth.lerp(a, m.rightArm.xRot, -3.0f);
				m.leftArm.xRot = Mth.lerp(a, m.leftArm.xRot, -3.0f);
				m.rightArm.zRot = Mth.lerp(a, m.rightArm.zRot, -0.12f);
				m.leftArm.zRot = Mth.lerp(a, m.leftArm.zRot, 0.12f);
				m.rightLeg.xRot = 0.1f;
				m.leftLeg.xRot = 0.2f;
				m.head.xRot = Mth.lerp(a, m.head.xRot, 0.5f);
			}
			case SentryState.POSE_GUARDIAN -> {
				// arms spread, palms up, face to the light
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.5f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -0.5f);
				m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 1.25f);
				m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -1.25f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.5f);
			}
			case SentryState.POSE_SHIELD -> {
				// arms crossed over the chest, then flung open
				boolean open = k > 0.45f;
				float cross = open ? 0f : w;
				float fling = open ? w : 0f;
				m.rightArm.xRot = Mth.lerp(cross + fling, m.rightArm.xRot, open ? -0.4f : -1.3f);
				m.leftArm.xRot = Mth.lerp(cross + fling, m.leftArm.xRot, open ? -0.4f : -1.3f);
				m.rightArm.yRot = Mth.lerp(cross, m.rightArm.yRot, -0.7f);
				m.leftArm.yRot = Mth.lerp(cross, m.leftArm.yRot, 0.7f);
				m.rightArm.zRot = Mth.lerp(fling, m.rightArm.zRot, 1.1f);
				m.leftArm.zRot = Mth.lerp(fling, m.leftArm.zRot, -1.1f);
			}
			case SentryState.POSE_ERASE -> {
				// one hand held out at it, fingers spread, trembling
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -Mth.HALF_PI + pitch + 0.03f * Mth.sin(age * 2.2f));
				m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.1f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0.3f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, pitch + 0.1f);
			}
			case SentryState.POSE_POOL -> {
				// pointing down at the ground
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.7f);
				m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.25f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, 0.5f);
			}
			case SentryState.POSE_SPHERE -> {
				// a hand out, the fist closing on it
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -Mth.HALF_PI + pitch - 0.2f * Math.min(1f, k * 3f));
				m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.25f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -0.5f);
				m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.4f);
			}
			case SentryState.POSE_NIGHTMARE -> {
				// arms flung wide, head thrown back
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.2f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -0.2f);
				m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 1.5f);
				m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -1.5f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.8f);
			}
			case SentryState.POSE_ECLIPSE -> {
				// arms raised in a V to the black sun
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -2.7f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -2.7f);
				m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.55f);
				m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.55f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.7f);
			}
			case SentryState.POSE_BLOT -> {
				// arms out, palms down, drawing it all in
				float pull = 1f - Math.min(1f, k * 1.6f);
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -1.3f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -1.3f);
				m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.9f * pull);
				m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0.9f * pull);
				m.head.xRot = Mth.lerp(w, m.head.xRot, 0.3f);
			}
			case SentryState.POSE_CLONES -> {
				// palms out, low, the shadows rising either side
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.3f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -0.3f);
				m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.7f);
				m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.7f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, 0.35f);
			}
			case SentryState.POSE_IMPALE -> {
				// a thrust: the arm drives straight out
				float drive = Math.min(1f, k * 3f);
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -Mth.HALF_PI + pitch);
				m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.05f);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0.5f);
			}
			default -> {
			}
		}
	}
}
