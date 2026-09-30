package com.projecthero.mod.client.mutation.v0145;

import com.projecthero.mod.client.ProjectHeroModClient;
import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.client.mutation.MutationPose;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;

/**
 * v0.14.5 Super Strength rework: client registration (HUD theme and bars, poses, overlays, renderers).
 *
 * <p>HUD: the black-and-gray theme, and every Strength timer is a Hairline bar above the key row, stacked
 * bottom-up -- Charged Punch (fills over the 1 s wind-up, then the same bar drains over the 2 s cooldown), Power
 * Leap charge, Bull Rush (fills over the 5 s charge, then drains over the 8 s rush) and Maximum Effort (drains over
 * its 30 s).
 */
public final class SuperStrengthClientV0145 {
	private static final String PK = SuperStrengthHandlers.KEY + "/";

	/** Grays only: the HUD is black and gray for this power. */
	private static final int FILL_CHARGE = 0xFFBDBDBD;
	private static final int FILL_READY = 0xFFFFFFFF;
	private static final int FILL_DRAIN = 0xFF8C8C8C;
	private static final int TEXT_READY = 0xFFFFFFFF;

	private SuperStrengthClientV0145() {
	}

	public static void init() {
		AbilityHudExtras.mono(SuperStrengthHandlers.KEY);
		AbilityHudExtras.registerAbove(SuperStrengthHandlers.KEY, (client, state, gameTime, out) -> {
			// ---- Charged Punch: charge fills, cooldown drains the same bar (the two never overlap) ----
			float punchCd = state.resources.getOrDefault(PK + "charged_cd", 0.0f);
			if (punchCd > 0.5f) {
				out.add(new AbilityHudExtras.AboveBar("Charged Punch", Math.min(1f, punchCd / SuperStrengthHandlers.CHARGED_CD_TICKS),
						FILL_DRAIN, 0));
			} else if (ProjectHeroModClient.chargedPunchReady()) {
				out.add(new AbilityHudExtras.AboveBar("Charged Punch — RELEASE", 1f, FILL_READY, TEXT_READY));
			} else if (ProjectHeroModClient.chargedPunchProgress() > 0.01f) {
				out.add(new AbilityHudExtras.AboveBar("Charged Punch", ProjectHeroModClient.chargedPunchProgress(), FILL_CHARGE, 0));
			}
			// ---- Power Leap charge ----
			float leap = ProjectHeroModClient.leapChargeProgress();
			if (leap > 0.01f) {
				out.add(new AbilityHudExtras.AboveBar("Power Leap", leap, leap >= 1f ? FILL_READY : FILL_CHARGE, 0));
			}
			// ---- Bull Rush: fills while charging, then drains over the rush ----
			float zCharge = state.resources.getOrDefault(PK + "z_charge", 0.0f);
			float zRunEnd = state.resources.getOrDefault(PK + "z_run_end", 0.0f);
			if (zCharge > 0.5f) {
				float held = Math.max(0f, gameTime - zCharge);
				out.add(new AbilityHudExtras.AboveBar("Bull Rush — charging", Math.min(1f, held / SuperStrengthHandlers.RUSH_CHARGE),
						FILL_CHARGE, 0));
			} else if (zRunEnd > 0.5f) {
				float left = Math.max(0f, zRunEnd - gameTime);
				out.add(new AbilityHudExtras.AboveBar("Bull Rush", Math.min(1f, left / SuperStrengthHandlers.RUSH_RUN), FILL_READY, 0));
			}
			// ---- Maximum Effort: drains over its 30 s ----
			float effort = state.resources.getOrDefault(PK + "effort_left", 0.0f);
			if (effort > 0.5f) {
				out.add(new AbilityHudExtras.AboveBar("Maximum Effort  " + (int) Math.ceil(effort / 20.0f) + "s",
						Math.min(1f, effort / SuperStrengthHandlers.EFFORT_TICKS), FILL_READY, 0));
			}
		});

		// The superhero landing: StrengthLandingPose does the kneel itself (hip drop, lean from the hips); these frames
		// are the rotations underneath it so the pose id is known to MutationPose and fades like every other one-shot.
		float[] land = { 0, -0.12f, 0, 0.08f, -0.95f, 0.2f, -0.1f, 0.95f, 0.1f, 1.05f, -0.85f, 0.55f };
		float[] hold = land.clone();
		hold[0] = StrengthLandingPose.HOLD_END;
		float[] end = new float[12];
		end[0] = StrengthLandingPose.END;
		MutationPose.register(SuperStrengthHandlers.LANDING_POSE, new float[][] { land, hold, end });
	}
}
