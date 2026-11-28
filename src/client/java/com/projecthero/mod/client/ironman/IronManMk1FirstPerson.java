package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * v0.17.7 (user) Mk I Cave-Forged moveset, first person: both empty hands play each new move ({@code mixin.ItemInHandRendererIronManMk1Mixin},
 * the Black Panther pattern) -- the Gauntlet Smash wind-up (fists raised out of view at the top, trembling) and the slam (both
 * hammered down into the bottom of the screen), the Arm Rocket (the right forearm up on the aim, the left hand under it, a
 * kick), the Scrap Shotgun (both palms out, a big recoil), the Fire Nova (crossed low, then flung out to the sides), the Iron
 * Shell (forearms crossed over the view), the Scrap Repair (the left forearm across, the right fist hammering it) and the
 * Reactor Overload charge (the right hand pulled in to the chest). Driven by the same server pose clock as the third-person
 * model ({@link IronManAbilityPose}), only while the Mk I is worn and the main hand is empty.
 */
public final class IronManMk1FirstPerson {
	private IronManMk1FirstPerson() {
	}

	/** {dx, dy, dz, pitch, yaw, roll} for the lead (main, right) hand and the off hand. */
	private static float[] f(float dx, float dy, float dz, float rx, float ry, float rz) {
		return new float[] { dx, dy, dz, rx, ry, rz };
	}

	private static final float[] REST = f(0, 0, 0, 0, 0, 0);

	/** How much a Mk I move owns the hands right now (0 = none plays). */
	public static float weight(AbstractClientPlayer player) {
		IronManAbilityPose.Play play = play(player);
		if (play == null) {
			return 0f;
		}
		float t = IronManAbilityPose.age(play);
		return Mth.clamp(Math.min(t / 2f, (play.ticks() - t) / 3f), 0f, 1f);
	}

	private static IronManAbilityPose.Play play(AbstractClientPlayer player) {
		if (!"mark_1".equals(IronManArmor.wornSuitId(player))) {
			return null;
		}
		IronManAbilityPose.Play play = IronManAbilityPose.current(player);
		return play != null && IronManAbilityFx.isMkOnePose(play.anim()) ? play : null;
	}

	/** Moves the first-person hand {@code arm} for the Mk I move playing now; true if anything was applied. */
	public static boolean apply(AbstractClientPlayer player, HumanoidArm arm, float partial, PoseStack pose) {
		IronManAbilityPose.Play play = play(player);
		if (play == null) {
			return false;
		}
		float t = IronManAbilityPose.age(play);
		float w = Mth.clamp(Math.min(t / 2f, (play.ticks() - t) / 3f), 0f, 1f);
		if (w <= 0.001f) {
			return false;
		}
		boolean lead = arm == HumanoidArm.RIGHT;
		float side = lead ? 1f : -1f;
		float[] v = sample(play.anim(), t, lead, player.tickCount + partial);
		pose.translate(side * v[0] * w, v[1] * w, v[2] * w);
		float px = side * 0.45f;
		pose.translate(px, -0.45f, -0.7f);
		pose.mulPose(Axis.ZP.rotationDegrees(side * v[5] * w));
		pose.mulPose(Axis.YP.rotationDegrees(side * v[4] * w));
		pose.mulPose(Axis.XP.rotationDegrees(v[3] * w));
		pose.translate(-px, 0.45f, 0.7f);
		return true;
	}

	private static float[] mix(float[] a, float[] b, float k) {
		float[] o = new float[6];
		for (int i = 0; i < 6; i++) {
			o[i] = Mth.lerp(k, a[i], b[i]);
		}
		return o;
	}

	private static float ease(float k) {
		k = Mth.clamp(k, 0f, 1f);
		return k * k * (3f - 2f * k);
	}

	/** The hand's offset for move {@code anim} at {@code t} ticks in (mirrored for the left hand by {@link #apply}). */
	static float[] sample(int anim, float t, boolean lead, float clock) {
		switch (anim) {
			case IronManAbilityFx.MK1_SMASH_WINDUP -> {
				// both fists raised up out of the top of the view, drawn in together, trembling as it charges
				float k = ease(t / 4f);
				float tremble = Mth.sin(clock * 2.3f) * 0.012f * Mth.clamp(t / 20f, 0.3f, 1f);
				return mix(REST, f(-0.2f, 0.62f + tremble, 0.15f, 70f, 8f, -12f), k);
			}
			case IronManAbilityFx.MK1_GAUNTLET_SMASH -> {
				float[] up = f(-0.2f, 0.62f, 0.15f, 70f, 8f, -12f);
				float[] down = f(-0.18f, -0.5f, -0.35f, -45f, 6f, -6f);
				if (t < 2f) {
					return up;
				}
				if (t < 4f) {
					float k = (t - 2f) / 2f;
					return mix(up, down, k * k);
				}
				// the jolt of the ground, then settling back
				float bounce = t < 8f ? Mth.sin((t - 4f) / 4f * Mth.PI) * 0.05f : 0f;
				return mix(f(down[0], down[1] + bounce, down[2], down[3], down[4], down[5]), REST, ease((t - 9f) / 6f));
			}
			case IronManAbilityFx.MK1_ARM_ROCKET -> {
				if (lead) {
					float kick = t < 5 ? (5 - t) / 5f : 0f;
					return f(-0.12f, 0.12f - kick * 0.05f, -0.15f + kick * 0.22f, 12f + kick * 18f, 4f, 0f);
				}
				// the off hand braced under the right wrist
				return f(-0.42f, 0.02f, -0.2f, 18f, 30f, 0f);
			}
			case IronManAbilityFx.MK1_SCRAP_SHOTGUN -> {
				float kick = t < 6 ? (6 - t) / 6f : 0f;
				float k = kick * kick;
				return f(-0.2f, 0.1f + k * 0.08f, -0.3f + k * 0.35f, 10f + k * 30f, 6f, 0f);
			}
			case IronManAbilityFx.MK1_FIRE_NOVA -> {
				float[] curl = f(-0.28f, -0.12f, 0.05f, -20f, 25f, 0f);
				float[] open = f(0.32f, -0.05f, -0.05f, 10f, -30f, 50f);
				if (t < 4f) {
					return mix(REST, curl, ease(t / 3f));
				}
				return mix(mix(curl, open, ease((t - 4f) / 2f)), REST, ease((t - 11f) / 6f));
			}
			case IronManAbilityFx.MK1_IRON_SHELL -> {
				// both forearms crossed in front of the view
				return mix(REST, f(-0.32f, 0.28f, 0.1f, 55f, lead ? 30f : 34f, -25f), ease(t / 3f));
			}
			case IronManAbilityFx.MK1_SCRAP_REPAIR -> {
				if (!lead) {
					// the off forearm held across, low in the view
					return mix(REST, f(-0.34f, 0.0f, -0.05f, 20f, 40f, -20f), ease(t / 3f));
				}
				// the right fist hammering down on it
				float hammer = Mth.abs(Mth.sin(t * 0.55f));
				return mix(REST, f(-0.3f, 0.06f + 0.12f * hammer, -0.05f, 20f + 30f * hammer, 15f, 0f), ease(t / 3f));
			}
			case IronManAbilityFx.MK1_OVERLOAD_CHARGE -> {
				if (lead) {
					// the right hand pulled in and down to the chest reactor
					float tremble = Mth.sin(clock * 2.7f) * 0.008f;
					return mix(REST, f(-0.3f, -0.18f + tremble, 0.2f, -10f, 35f, 0f), ease(t / 4f));
				}
				// the left hand swept out and down out of view
				return mix(REST, f(0.25f, -0.35f, 0.1f, -30f, -20f, 20f), ease(t / 4f));
			}
			default -> {
				return REST;
			}
		}
	}
}
