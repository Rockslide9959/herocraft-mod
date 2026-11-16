package com.projecthero.mod.client.powerarmour;

import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.kit.PowerArmourAbilities;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Power Armour: the suit's body language on the wearer's model (the suit rides the posed model, so every viewer sees
 * it) -- the punch wind-up and the jab, the stomp (knee up, slam), the grip (arms out holding) and throw, the shoulder charge,
 * the overload (arms spreading as it builds, flung wide at the blast), the arm weapon aimed where you look, the Power Fist's
 * overhead piledriver, the jets. Each clip fades in and out over vanilla's own pose. Radians; negative xRot raises an arm forward.
 */
public final class PowerArmourPose {
	private PowerArmourPose() {
	}

	public static void apply(Player player, HumanoidModel<?> m, float ageInTicks) {
		PowerArmourState s = PowerArmour.state(player);
		if (s == null || !s.sealed()) {
			return;
		}
		float now = player.level().getGameTime() + Mth.frac(ageInTicks);
		float t = now - s.animStart;
		// held keys first
		if (s.charge == PowerArmourAbilities.CHARGE_PUNCH) {
			float k = Mth.clamp((now - s.chargeStart) / PowerArmourConfig.PUNCH_CHARGE_TICKS, 0f, 1f);
			m.rightArm.xRot = Mth.lerp(0.4f + 0.6f * k, m.rightArm.xRot, 0.7f);
			m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.25f);
			m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -0.6f);
			m.rightArm.yRot = 0.3f * k;
			return;
		}
		if (s.charge == PowerArmourAbilities.CHARGE_FIRE || s.charge == PowerArmourAbilities.CHARGE_RAIL || s.grabbed < 0 && aimClip(s, t)) {
			m.rightArm.xRot = m.head.xRot - Mth.HALF_PI;
			m.rightArm.yRot = m.head.yRot;
			m.rightArm.zRot = 0f;
			if (s.charge == PowerArmourAbilities.CHARGE_RAIL) {
				m.leftArm.xRot = m.head.xRot - Mth.HALF_PI + 0.3f;
				m.leftArm.yRot = m.head.yRot + 0.5f;
			}
			return;
		}
		if (s.grabbed >= 0) {
			m.rightArm.xRot = -1.9f;
			m.leftArm.xRot = -1.9f;
			m.rightArm.zRot = 0.18f;
			m.leftArm.zRot = -0.18f;
			m.rightArm.yRot = -0.15f;
			m.leftArm.yRot = 0.15f;
			return;
		}
		if (s.overloadAt > 0 && s.overloadAt + 1 >= now) {
			float k = Mth.clamp(1f - (s.overloadAt - now) / PowerArmourConfig.OVERLOAD_WINDUP, 0f, 1f);
			m.rightArm.zRot = 0.2f + 1.1f * k;
			m.leftArm.zRot = -0.2f - 1.1f * k;
			m.rightArm.xRot = -0.3f * k;
			m.leftArm.xRot = -0.3f * k;
			m.head.xRot = Mth.lerp(k, m.head.xRot, -0.25f);
			return;
		}
		switch (s.anim) {
			case PowerArmourAbilities.ANIM_PUNCH, PowerArmourAbilities.ANIM_PUNCH_HEAVY -> {
				float len = s.anim == PowerArmourAbilities.ANIM_PUNCH_HEAVY ? 10f : 7f;
				float w = weight(t, len, 1f, 4f);
				if (w > 0f) {
					float jab = t < 2f ? t / 2f : 1f;
					m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, Mth.lerp(jab, 0.7f, -1.6f));
					m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.2f);
					m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0.4f);
				}
			}
			case PowerArmourAbilities.ANIM_STOMP -> {
				float w = weight(t, 14f, 0.5f, 5f);
				if (w > 0f) {
					float knee = t < 5f ? t / 5f : t < 7f ? 1f - (t - 5f) / 2f : 0f;
					m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, -1.25f * knee);
					m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.5f);
					m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.5f);
				}
			}
			case PowerArmourAbilities.ANIM_THROW -> {
				float w = weight(t, 9f, 0f, 4f);
				if (w > 0f) {
					float k = Mth.clamp(t / 3f, 0f, 1f);
					float x = Mth.lerp(k, -2.7f, -1.1f);
					m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, x);
					m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, x);
				}
			}
			case PowerArmourAbilities.ANIM_CHARGE -> {
				float w = weight(t, PowerArmourConfig.CHARGE_TICKS + 3f, 1f, 3f);
				if (w > 0f) {
					m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.9f);
					m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, -0.5f);
					m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0.6f);
					m.head.xRot = Mth.lerp(w, m.head.xRot, 0.35f);
				}
			}
			case PowerArmourAbilities.ANIM_BLAST -> {
				float w = weight(t, 12f, 0f, 6f);
				if (w > 0f) {
					m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 1.6f);
					m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -1.6f);
					m.head.xRot = Mth.lerp(w, m.head.xRot, -0.4f);
				}
			}
			case PowerArmourAbilities.ANIM_PILEDRIVER -> {
				float w = weight(t, 12f, 0.5f, 4f);
				if (w > 0f) {
					float x = t < 4f ? Mth.lerp(t / 4f, -1f, -2.9f) : Mth.lerp(Mth.clamp((t - 4f) / 2f, 0f, 1f), -2.9f, -0.7f);
					m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, x);
					m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, x);
					m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, -0.15f);
					m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, 0.15f);
				}
			}
			case PowerArmourAbilities.ANIM_JETS -> {
				float w = weight(t, 12f, 0.5f, 5f);
				if (w > 0f) {
					m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, 0.45f);
					m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0.45f);
					m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.25f);
					m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.25f);
					m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, 0.15f);
					m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, -0.1f);
				}
			}
			case PowerArmourAbilities.ANIM_MISSILES, PowerArmourAbilities.ANIM_DEPLOY -> {
				float w = weight(t, 10f, 1f, 4f);
				if (w > 0f) {
					m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.35f);
					m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.35f);
					m.head.xRot = Mth.lerp(w, m.head.xRot, 0.2f);
				}
			}
			default -> {
			}
		}
		if (s.thrusting) {
			m.rightArm.xRot = Mth.lerp(0.7f, m.rightArm.xRot, 0.35f);
			m.leftArm.xRot = Mth.lerp(0.7f, m.leftArm.xRot, 0.35f);
			m.rightLeg.xRot = Mth.lerp(0.8f, m.rightLeg.xRot, 0.1f);
			m.leftLeg.xRot = Mth.lerp(0.8f, m.leftLeg.xRot, 0.05f);
		}
	}

	private static boolean aimClip(PowerArmourState s, float t) {
		return (s.anim == PowerArmourAbilities.ANIM_TESLA || s.anim == PowerArmourAbilities.ANIM_RAIL) && t < 10f;
	}

	/** A clip's weight at {@code t}: fades in over {@code in}, holds, fades out over the last {@code out} ticks of {@code len}. */
	private static float weight(float t, float len, float in, float out) {
		if (t < 0f || t > len) {
			return 0f;
		}
		float a = in <= 0f ? 1f : Mth.clamp(t / in, 0f, 1f);
		float b = Mth.clamp((len - t) / out, 0f, 1f);
		return Math.min(a, b);
	}
}
