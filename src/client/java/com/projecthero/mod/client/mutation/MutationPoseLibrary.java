package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

/**
 * v0.13.22: the generic move animations every mutation can play by name via
 * {@code MutationVisuals.play(player, "<id>")}. Power-specific poses are registered by each power's own
 * client class ({@code RevampClient*}) with ids namespaced {@code pNN.<name>}.
 *
 * <p>Frame layout (see {@link MutationPose}): {@code {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX,
 * bodyY, rLegX, lLegX, headX}}.
 *
 * <table>
 *   <tr><th>id</th><th>use</th></tr>
 *   <tr><td>punch_right / punch_left / double_punch</td><td>jab, straight</td></tr>
 *   <tr><td>haymaker / uppercut</td><td>big wind-up hits</td></tr>
 *   <tr><td>slam_two_hand / ground_pound</td><td>overhead two-fist slam / one fist into the ground</td></tr>
 *   <tr><td>stomp / kick</td><td>leg moves</td></tr>
 *   <tr><td>clap</td><td>thunderclap</td></tr>
 *   <tr><td>throw_right / whip_right / slash_right</td><td>overhand throw, lash, blade swing</td></tr>
 *   <tr><td>cast_right / cast_left / cast_two_hand / point_right</td><td>projectile from the hand(s), aimed</td></tr>
 *   <tr><td>cast_raise_both / summon_ground</td><td>raise both arms / pull something up out of the ground</td></tr>
 *   <tr><td>grab_pull</td><td>reach out (aimed) and yank back</td></tr>
 *   <tr><td>leap / dash_forward / hero_landing</td><td>movement</td></tr>
 *   <tr><td>flex / power_up</td><td>mode activation</td></tr>
 *   <tr><td>(loop) channel_right / channel_two_hand</td><td>held beam / stream from the hand(s), aimed</td></tr>
 *   <tr><td>(loop) beam_eyes / scream</td><td>eye beam brace / head back, arms back</td></tr>
 *   <tr><td>(loop) guard / shield_brace / crouch_charge</td><td>defensive holds and charging</td></tr>
 *   <tr><td>(loop) float_arms / spin_arms / carry_overhead</td><td>levitating, whirling, holding something above</td></tr>
 * </table>
 */
public final class MutationPoseLibrary {
	private MutationPoseLibrary() {
	}

	private static final float[] Z = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	private static float[] at(int tick, float[] pose) {
		float[] f = pose.clone();
		f[0] = tick;
		return f;
	}

	private static float[] zero(int tick) {
		return at(tick, Z);
	}

	private static float[] p(float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY,
			float rL, float lL, float h) {
		return new float[] { 0, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	public static void init() {
		// ---- strikes ----
		float[] jabBack = p(0.6f, 0.2f, 0, -0.5f, 0.3f, 0, 0.05f, 0.45f, 0.25f, -0.25f, 0);
		float[] jabOut = p(-1.62f, -0.12f, 0, 0.45f, 0.2f, 0, 0.12f, -0.55f, -0.35f, 0.35f, 0);
		MutationPose.register("punch_right", new float[][] { zero(0), at(3, jabBack), at(5, jabOut), at(9, jabOut), zero(13) });
		float[] jabBackL = p(-0.5f, -0.3f, 0, 0.6f, -0.2f, 0, 0.05f, -0.45f, -0.25f, 0.25f, 0);
		float[] jabOutL = p(0.45f, -0.2f, 0, -1.62f, 0.12f, 0, 0.12f, 0.55f, 0.35f, -0.35f, 0);
		MutationPose.register("punch_left", new float[][] { zero(0), at(3, jabBackL), at(5, jabOutL), at(9, jabOutL), zero(13) });
		float[] dblBack = p(0.5f, 0, 0.1f, 0.5f, 0, -0.1f, -0.1f, 0, 0.2f, -0.2f, 0);
		float[] dblOut = p(-1.6f, -0.15f, 0, -1.6f, 0.15f, 0, 0.25f, 0, -0.3f, 0.3f, 0);
		MutationPose.register("double_punch", new float[][] { zero(0), at(4, dblBack), at(6, dblOut), at(10, dblOut), zero(15) });
		float[] hayBack = p(1.05f, 0.35f, 0.25f, -0.9f, 0.4f, 0, 0.12f, 0.8f, 0.35f, -0.35f, 0);
		float[] hayOut = p(-1.7f, -0.45f, 0, 0.5f, 0.2f, 0, 0.3f, -0.8f, -0.45f, 0.45f, 0.05f);
		MutationPose.register("haymaker", new float[][] { zero(0), at(6, hayBack), at(9, hayOut), at(14, hayOut), zero(20) });
		float[] upLow = p(0.55f, 0, 0.25f, -0.4f, 0, -0.2f, 0.35f, 0.3f, 0.35f, -0.35f, 0.2f);
		float[] upHigh = p(-2.7f, -0.15f, 0, 0.3f, 0, -0.1f, -0.2f, -0.25f, -0.2f, 0.2f, -0.35f);
		MutationPose.register("uppercut", new float[][] { zero(0), at(4, upLow), at(7, upHigh), at(11, upHigh), zero(16) });

		// ---- slams ----
		float[] slamUp = p(-3.0f, 0, 0.15f, -3.0f, 0, -0.15f, -0.25f, 0, 0.15f, -0.15f, -0.25f);
		float[] slamDown = p(-0.55f, 0, 0.05f, -0.55f, 0, -0.05f, 0.65f, 0, -0.35f, 0.4f, 0.45f);
		MutationPose.register("slam_two_hand", new float[][] { zero(0), at(6, slamUp), at(9, slamDown), at(16, slamDown), zero(22) });
		float[] poundUp = p(-2.9f, 0, 0.2f, 0.3f, 0, -0.35f, -0.15f, 0.2f, 0.1f, -0.1f, -0.2f);
		float[] poundDown = p(-0.35f, 0, 0.1f, 0.4f, 0, -0.6f, 0.75f, -0.1f, -0.5f, 0.45f, 0.45f);
		MutationPose.register("ground_pound", new float[][] { zero(0), at(5, poundUp), at(8, poundDown), at(15, poundDown), zero(20) });
		float[] stompUp = p(-0.2f, 0, 0.5f, -0.2f, 0, -0.5f, 0.15f, 0, -1.25f, 0.1f, 0.15f);
		float[] stompDown = p(-0.2f, 0, 0.55f, -0.2f, 0, -0.55f, 0.3f, 0, 0.2f, -0.1f, 0.3f);
		MutationPose.register("stomp", new float[][] { zero(0), at(6, stompUp), at(8, stompDown), at(13, stompDown), zero(18) });
		float[] kickBack = p(0.3f, 0, 0.4f, 0.3f, 0, -0.4f, 0.1f, 0, 0.6f, 0, 0);
		float[] kickOut = p(0.4f, 0, 0.6f, 0.4f, 0, -0.6f, -0.25f, 0, -1.65f, 0.1f, 0.1f);
		MutationPose.register("kick", new float[][] { zero(0), at(3, kickBack), at(6, kickOut), at(10, kickOut), zero(15) });

		// ---- clap ----
		float[] clapWide = p(-1.45f, 0.95f, 0, -1.45f, -0.95f, 0, -0.1f, 0, 0, 0, -0.05f);
		float[] clapShut = p(-1.5f, -0.32f, 0, -1.5f, 0.32f, 0, 0.12f, 0, -0.15f, 0.15f, 0.05f);
		MutationPose.register("clap", new float[][] { zero(0), at(5, clapWide), at(7, clapShut), at(12, clapShut), zero(17) });

		// ---- throws / lashes ----
		float[] throwBack = p(-2.9f, 0.35f, 0.3f, -0.6f, 0.3f, 0, -0.2f, 0.6f, 0.3f, -0.3f, -0.1f);
		float[] throwOut = p(-1.0f, -0.35f, 0, 0.3f, 0.1f, 0, 0.35f, -0.6f, -0.4f, 0.4f, 0.1f);
		MutationPose.register("throw_right", new float[][] { zero(0), at(6, throwBack), at(9, throwOut), at(13, throwOut), zero(18) });
		float[] whipBack = p(-2.6f, 0.75f, 0.2f, 0.2f, 0, -0.2f, -0.1f, 0.6f, 0.2f, -0.2f, 0);
		float[] whipOut = p(-0.9f, -0.75f, 0, 0.3f, 0, -0.1f, 0.2f, -0.6f, -0.3f, 0.3f, 0);
		MutationPose.register("whip_right", new float[][] { zero(0), at(5, whipBack), at(8, whipOut), at(12, whipOut), zero(17) });
		float[] slashBack = p(-2.5f, 0.85f, 0.35f, -0.3f, 0.2f, 0, -0.05f, 0.55f, 0.2f, -0.2f, 0);
		float[] slashOut = p(-0.75f, -0.9f, 0, 0.3f, 0.2f, 0, 0.2f, -0.65f, -0.3f, 0.3f, 0.05f);
		MutationPose.register("slash_right", new float[][] { zero(0), at(4, slashBack), at(7, slashOut), at(11, slashOut), zero(15) });

		// ---- casts (aimed) ----
		float[] castR = p(AIM, AIM, 0, 0.2f, 0, -0.15f, 0.05f, -0.25f, -0.1f, 0.1f, 0);
		MutationPose.register("cast_right", new float[][] { zero(0), at(3, castR), at(9, castR), zero(13) });
		float[] castL = p(0.2f, 0, 0.15f, AIM, AIM, 0, 0.05f, 0.25f, 0.1f, -0.1f, 0);
		MutationPose.register("cast_left", new float[][] { zero(0), at(3, castL), at(9, castL), zero(13) });
		float[] castBoth = p(AIM, AIM, 0, AIM, AIM, 0, 0.1f, 0, -0.15f, 0.15f, 0);
		MutationPose.register("cast_two_hand", new float[][] { zero(0), at(4, castBoth), at(11, castBoth), zero(15) });
		float[] point = p(AIM, AIM, 0, 0.1f, 0, -0.1f, 0, -0.15f, 0, 0, 0);
		MutationPose.register("point_right", new float[][] { zero(0), at(2, point), at(8, point), zero(12) });
		float[] raise = p(-2.95f, 0, 0.3f, -2.95f, 0, -0.3f, -0.12f, 0, 0, 0, -0.4f);
		MutationPose.register("cast_raise_both", new float[][] { zero(0), at(6, raise), at(16, raise), zero(22) });
		float[] sumLow = p(-0.35f, 0, 0.35f, -0.35f, 0, -0.35f, 0.35f, 0, -0.2f, 0.2f, 0.35f);
		float[] sumHigh = p(-2.6f, 0, 0.2f, -2.6f, 0, -0.2f, -0.12f, 0, 0.05f, -0.05f, -0.3f);
		MutationPose.register("summon_ground", new float[][] { zero(0), at(5, sumLow), at(10, sumHigh), at(16, sumHigh), zero(22) });
		float[] reach = p(AIM, AIM, 0, 0.25f, 0, -0.2f, 0.2f, -0.2f, -0.2f, 0.2f, 0);
		float[] yank = p(-0.55f, 0.3f, 0.1f, 0.3f, 0, -0.2f, -0.2f, 0.35f, 0.3f, -0.3f, -0.1f);
		MutationPose.register("grab_pull", new float[][] { zero(0), at(3, reach), at(7, reach), at(11, yank), at(14, yank), zero(19) });

		// ---- movement ----
		float[] leapCrouch = p(0.6f, 0, 0.1f, 0.6f, 0, -0.1f, 0.45f, 0, 0.7f, 0.7f, 0.2f);
		float[] leapUp = p(-2.8f, 0, 0.1f, -2.8f, 0, -0.1f, -0.15f, 0, -0.2f, 0.2f, -0.3f);
		float[] leapGlide = p(-2.4f, 0, 0.2f, -2.4f, 0, -0.2f, -0.1f, 0, 0, 0, -0.2f);
		MutationPose.register("leap", new float[][] { zero(0), at(3, leapCrouch), at(6, leapUp), at(14, leapGlide), zero(22) });
		float[] dash = p(0.95f, 0, 0.2f, 0.95f, 0, -0.2f, 0.5f, 0, -0.6f, 0.6f, -0.35f);
		MutationPose.register("dash_forward", new float[][] { zero(0), at(2, dash), at(9, dash), zero(13) });
		float[] land = p(-0.3f, 0, 0.25f, 0.45f, 0, -0.7f, 0.7f, 0.1f, -0.9f, 0.5f, 0.35f);
		MutationPose.register("hero_landing", new float[][] { at(0, land), at(12, land), zero(20) });

		// ---- modes ----
		float[] flexA = p(-0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.1f, 0, 0.05f, -0.05f, 0.1f);
		float[] flexB = p(-0.25f, 0, 1.45f, -0.25f, 0, -1.45f, -0.18f, 0, 0.12f, -0.12f, -0.25f);
		MutationPose.register("flex", new float[][] { zero(0), at(6, flexA), at(12, flexB), at(18, flexB), zero(24) });
		float[] upA = p(-0.5f, 0, 0.35f, -0.5f, 0, -0.35f, 0.3f, 0, 0.1f, -0.1f, 0.3f);
		float[] upB = p(-1.2f, 0.6f, 0.9f, -1.2f, -0.6f, -0.9f, -0.2f, 0, 0, 0, -0.35f);
		MutationPose.register("power_up", new float[][] { zero(0), at(6, upA), at(12, upB), at(18, upB), zero(24) });

		// ---- loops ----
		float[] chanR = p(AIM, AIM, 0, 0.15f, 0, -0.15f, 0.05f, -0.3f, -0.1f, 0.1f, 0);
		float[] chanR2 = p(AIM, AIM, 0, 0.2f, 0, -0.2f, 0.07f, -0.3f, -0.1f, 0.1f, 0);
		MutationPose.registerLoop("channel_right", 3, new float[][] { zero(0), at(3, chanR), at(8, chanR2), at(13, chanR) });
		float[] chan2 = p(AIM, AIM, 0, AIM, AIM, 0, 0.12f, 0, -0.15f, 0.15f, 0);
		float[] chan2b = p(AIM, AIM, 0, AIM, AIM, 0, 0.15f, 0, -0.15f, 0.15f, 0);
		MutationPose.registerLoop("channel_two_hand", 3, new float[][] { zero(0), at(3, chan2), at(8, chan2b), at(13, chan2) });
		float[] eyes = p(0.25f, 0, 0.3f, 0.25f, 0, -0.3f, 0.12f, 0, -0.1f, 0.1f, 0);
		float[] eyes2 = p(0.3f, 0, 0.34f, 0.3f, 0, -0.34f, 0.14f, 0, -0.1f, 0.1f, 0);
		MutationPose.registerLoop("beam_eyes", 3, new float[][] { zero(0), at(3, eyes), at(7, eyes2), at(11, eyes) });
		float[] scream = p(0.75f, 0, 0.45f, 0.75f, 0, -0.45f, -0.18f, 0, 0.1f, -0.1f, -0.25f);
		float[] scream2 = p(0.8f, 0, 0.5f, 0.8f, 0, -0.5f, -0.2f, 0, 0.1f, -0.1f, -0.28f);
		MutationPose.registerLoop("scream", 3, new float[][] { zero(0), at(3, scream), at(5, scream2), at(7, scream) });
		float[] guard = p(-1.4f, -0.65f, 0, -1.4f, 0.65f, 0, 0.12f, 0, -0.1f, 0.1f, 0.1f);
		float[] guard2 = p(-1.45f, -0.68f, 0, -1.45f, 0.68f, 0, 0.14f, 0, -0.1f, 0.1f, 0.1f);
		MutationPose.registerLoop("guard", 3, new float[][] { zero(0), at(3, guard), at(10, guard2), at(17, guard) });
		float[] brace = p(-1.5f, -0.25f, 0, -1.5f, 0.25f, 0, 0.1f, 0, -0.3f, 0.3f, 0);
		float[] brace2 = p(-1.55f, -0.28f, 0, -1.55f, 0.28f, 0, 0.12f, 0, -0.3f, 0.3f, 0);
		MutationPose.registerLoop("shield_brace", 3, new float[][] { zero(0), at(3, brace), at(10, brace2), at(17, brace) });
		float[] crouch = p(0.6f, 0, 0.25f, 0.6f, 0, -0.25f, 0.5f, 0, 0.35f, -0.2f, 0.3f);
		float[] crouch2 = p(0.65f, 0, 0.28f, 0.65f, 0, -0.28f, 0.52f, 0, 0.35f, -0.2f, 0.3f);
		MutationPose.registerLoop("crouch_charge", 3, new float[][] { zero(0), at(3, crouch), at(6, crouch2), at(9, crouch) });
		float[] fl = p(-0.35f, 0, 0.65f, -0.35f, 0, -0.65f, 0.05f, 0, -0.25f, -0.15f, 0.1f);
		float[] fl2 = p(-0.45f, 0, 0.75f, -0.45f, 0, -0.75f, 0.08f, 0, -0.3f, -0.1f, 0.1f);
		MutationPose.registerLoop("float_arms", 4, new float[][] { zero(0), at(4, fl), at(14, fl2), at(24, fl) });
		float[] spin = p(-0.1f, 0, 1.45f, -0.1f, 0, -1.45f, 0, 0, 0.1f, -0.1f, 0);
		float[] spin2 = p(-0.2f, 0, 1.5f, 0, 0, -1.4f, 0, 0, -0.1f, 0.1f, 0);
		MutationPose.registerLoop("spin_arms", 3, new float[][] { zero(0), at(3, spin), at(5, spin2), at(7, spin) });
		float[] carry = p(-2.9f, -0.2f, 0.1f, -2.9f, 0.2f, -0.1f, -0.05f, 0, 0, 0, -0.1f);
		MutationPose.registerLoop("carry_overhead", 4, new float[][] { zero(0), at(4, carry), at(14, carry) });
	}
}
