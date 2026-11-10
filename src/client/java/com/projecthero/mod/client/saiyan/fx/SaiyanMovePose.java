package com.projecthero.mod.client.saiyan.fx;

import com.projecthero.mod.saiyan.moves.SaiyanMoves;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: the Saiyan moves' body poses, set on the vanilla humanoid model from {@code HumanoidModelMixin}. Driven by the
 * ANIM payloads and the beam / orb state in {@link SaiyanFxClient}. Negative {@code xRot} swings a limb forward / up.
 */
public final class SaiyanMovePose {
	private SaiyanMovePose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart main = rightMain ? model.rightArm : model.leftArm;
		ModelPart off = rightMain ? model.leftArm : model.rightArm;
		ModelPart mainLeg = rightMain ? model.rightLeg : model.leftLeg;
		float side = rightMain ? 1f : -1f;
		float pitch = model.head.xRot;

		// held poses first: the beam (both arms out along the look), its charge (cupped at the hip), the orb (arms raised)
		if (SaiyanFxClient.beamFiring(player)) {
			set(model.rightArm, -Mth.HALF_PI + pitch, 0.18f, 0f);
			set(model.leftArm, -Mth.HALF_PI + pitch, -0.18f, 0f);
			return;
		}
		if (SaiyanFxClient.beamCharging(player)) {
			// hands cupped together at the back hip
			set(main, 0.55f, -0.35f * side, 0.25f * side);
			set(off, -0.45f, -0.95f * side, 0.1f * side);
			model.body.yRot = -0.35f * side;
			return;
		}
		if (SaiyanFxClient.orbCharging(player)) {
			set(model.rightArm, -2.95f, -0.12f, 0.18f);
			set(model.leftArm, -2.95f, 0.12f, -0.18f);
			return;
		}
		float[] a = SaiyanFxClient.anim(player, partial);
		if (a == null) {
			return;
		}
		int id = (int) a[0];
		float age = a[1];
		switch (id) {
			case SaiyanMoves.ANIM_JAB_R, SaiyanMoves.ANIM_FEINT_JAB -> {
				if (age < 6f) {
					float k = punch(age, 6f);
					set(main, Mth.lerp(k, -0.3f, -1.62f) + pitch * k, -0.1f * side, 0f);
					set(off, -0.6f, 0.4f * side, 0f);
				}
			}
			case SaiyanMoves.ANIM_JAB_L -> {
				if (age < 6f) {
					float k = punch(age, 6f);
					set(off, Mth.lerp(k, -0.3f, -1.62f) + pitch * k, 0.1f * side, 0f);
					set(main, -0.6f, -0.4f * side, 0f);
				}
			}
			case SaiyanMoves.ANIM_COMBO_KICK, SaiyanMoves.ANIM_FEINT_KICK -> {
				if (age < 9f) {
					float k = punch(age, 9f);
					mainLeg.xRot = Mth.lerp(k, 0f, -1.45f);
					if (id == SaiyanMoves.ANIM_FEINT_KICK) {
						mainLeg.zRot = 0.5f * k * side;
					}
					set(model.rightArm, -0.4f, 0f, 0.5f);
					set(model.leftArm, -0.4f, 0f, -0.5f);
				}
			}
			case SaiyanMoves.ANIM_ORB_THROW -> {
				if (age < 10f) {
					float k = Mth.clamp(age / 6f, 0f, 1f);
					float x = Mth.lerp(k, -2.95f, -1.3f);
					set(model.rightArm, x, -0.12f, 0.1f);
					set(model.leftArm, x, 0.12f, -0.1f);
				}
			}
			case SaiyanMoves.ANIM_BARRAGE -> {
				if (age < 32f) {
					// fists in turn, faster than the eye: each arm punches every 4 ticks, half a beat apart
					float ph = (age % 4f) / 4f;
					float k1 = Mth.sin(ph * Mth.PI), k2 = Mth.sin(((ph + 0.5f) % 1f) * Mth.PI);
					set(main, -0.5f - 1.15f * k1 + pitch, (-0.25f + 0.2f * k1) * side, 0f);
					set(off, -0.5f - 1.15f * k2 + pitch, (0.25f - 0.2f * k2) * side, 0f);
				}
			}
			case SaiyanMoves.ANIM_SKY_KICK -> {
				if (age < 8f) {
					float k = punch(age, 8f);
					mainLeg.xRot = Mth.lerp(k, 0.2f, -2.3f);
					set(model.rightArm, 0.4f, 0f, 0.6f);
					set(model.leftArm, 0.4f, 0f, -0.6f);
				}
			}
			case SaiyanMoves.ANIM_SKY_SMASH -> {
				if (age < 8f) {
					// the double-fist hammer: up over the head, then down
					float k = Mth.clamp(age / 5f, 0f, 1f);
					float x = Mth.lerp(k * k, -3.0f, -0.4f);
					set(model.rightArm, x, -0.3f, 0f);
					set(model.leftArm, x, 0.3f, 0f);
				}
			}
			case SaiyanMoves.ANIM_RUSH_WINDUP -> {
				if (age < 16f) {
					// fist drawn back, the other hand forward to aim
					set(main, 0.9f, 0.25f * side, 0.2f * side);
					set(off, -1.3f + pitch, -0.3f * side, 0f);
					model.body.yRot = 0.45f * side;
				}
			}
			case SaiyanMoves.ANIM_RUSH -> {
				if (age < 14f) {
					set(main, -Mth.HALF_PI + pitch, 0f, 0f);
					set(off, 0.7f, 0f, -0.2f * side);
					model.body.yRot = -0.3f * side;
				}
			}
			default -> {
			}
		}
	}

	/** A punch's reach 0..1..0: snapped out fast, drawn back slower. */
	private static float punch(float age, float len) {
		float t = Mth.clamp(age / len, 0f, 1f);
		return t < 0.35f ? t / 0.35f : 1f - (t - 0.35f) / 0.65f * 0.7f;
	}

	private static void set(ModelPart p, float x, float y, float z) {
		p.xRot = x;
		p.yRot = y;
		p.zRot = z;
	}
}
