package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 (user: "update the green lantern flying animation and make it look better, make him put the arm with the ring in
 * front of him when he sprint flies"): Ring Flight's own limb pose, three looks blended smoothly by the shared flight
 * state ({@link FlightPoseHelper#cruise} / {@link FlightPoseHelper#sprint}, eased every tick) and faded in at take-off and
 * out on landing by {@link FlightPoseHelper#airborne}. The body lean underneath is still the shared one.
 * <ul>
 *   <li><b>Hover</b>: levitating calmly -- the ring fist held a little forward and out, ready; the free hand loose and a
 *   touch away from the body; one leg a little ahead of the other, both drifting slowly.</li>
 *   <li><b>Cruise</b>: leaning into the flight, both arms swept back along the sides, legs trailing together.</li>
 *   <li><b>Sprint-flight</b>: the ring arm thrust straight out ahead along the line of flight (Superman-style, ring
 *   leading), the free arm laid back along his side, legs straight and together.</li>
 * </ul>
 * The ring is always on the right hand ({@code PowerRingLayer} draws it on {@code rightArm}), so the right arm leads.
 * Applied before {@link GreenLanternPose}, so a move made mid-air plays over it.
 * Each array is {@code {rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, rLegX, rLegZ, lLegX, lLegZ}} in radians.
 */
public final class GreenLanternFlightPose {
	static final float[] HOVER = { -0.32f, -0.08f, 0.12f, 0.06f, 0.0f, -0.2f, -0.14f, 0.05f, 0.1f, -0.05f };
	static final float[] CRUISE = { 0.38f, 0.0f, 0.2f, 0.42f, 0.0f, -0.2f, 0.16f, 0.03f, 0.3f, -0.03f };
	/** -PI along the body's up axis, which the flat sprint lean lays dead ahead along the flight. */
	static final float[] SPRINT = { -3.08f, 0.02f, -0.04f, 0.14f, 0.0f, -0.07f, 0.04f, 0.02f, 0.1f, -0.02f };

	/** Harness hook: forces the pose weights (air, cruise, sprint) when non-null. */
	public static volatile float[] debugForce;
	/** Harness hook: off for before / after comparison films. */
	public static volatile boolean ENABLED = true;

	private GreenLanternFlightPose() {
	}

	/** How far into the sprint-flight pose the player is (0..1); 0 for anyone not a flying Green Lantern. */
	public static float sprintWeight(Player player, float partial) {
		if (!ENABLED || !isLantern(player)) {
			return 0f;
		}
		float[] force = debugForce;
		if (force != null) {
			return force[0] * force[2];
		}
		return FlightPoseHelper.airborne(player, partial) * FlightPoseHelper.sprint(player, partial)
				* com.projecthero.mod.client.flight.HeroLandingClient.flightPoseFactor(player, partial);
	}

	private static boolean isLantern(Player player) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		return s != null && s.hasPower;
	}

	public static void apply(Player player, HumanoidModel<?> m, float ageInTicks) {
		if (!ENABLED || !isLantern(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean flying = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
		float air;
		float cruise;
		float sprint;
		float[] force = debugForce;
		if (force != null) {
			air = force[0];
			cruise = force[1];
			sprint = force[2];
		} else {
			air = FlightPoseHelper.airborne(player, partial);
			if (!flying && air <= 0.001f) {
				return;
			}
			air *= com.projecthero.mod.client.flight.HeroLandingClient.flightPoseFactor(player, partial);
			cruise = FlightPoseHelper.cruise(player, partial);
			sprint = FlightPoseHelper.sprint(player, partial);
		}
		if (air <= 0.001f) {
			return;
		}

		float[] t = new float[10];
		for (int i = 0; i < 10; i++) {
			t[i] = Mth.lerp(sprint, Mth.lerp(cruise, HOVER[i], CRUISE[i]), SPRINT[i]);
		}

		// life on top: a slow levitating drift in a hover, a wind flutter cruising, a faint strain in the leading arm at speed
		float hover = (1f - cruise) * (1f - sprint);
		float slow = Mth.sin(ageInTicks * 0.07f);
		float slow2 = Mth.sin(ageInTicks * 0.05f + 1.3f);
		float flutter = Mth.sin(ageInTicks * 0.45f);
		float cruiseOnly = cruise * (1f - sprint);
		t[0] += hover * 0.05f * slow + sprint * 0.015f * Mth.sin(ageInTicks * 0.9f);
		t[2] += hover * 0.03f * slow2 + cruiseOnly * 0.03f * flutter;
		t[3] -= hover * 0.04f * slow2;
		t[5] -= hover * 0.03f * slow + cruiseOnly * 0.03f * flutter;
		t[6] += hover * 0.07f * slow + cruiseOnly * 0.05f * flutter + sprint * 0.03f * Mth.sin(ageInTicks * 0.3f);
		t[8] -= hover * 0.06f * slow2 + cruiseOnly * 0.05f * flutter + sprint * 0.03f * Mth.sin(ageInTicks * 0.3f);

		pose(m.rightArm, air, t[0], t[1], t[2]);
		pose(m.leftArm, air, t[3], t[4], t[5]);
		pose(m.rightLeg, air, t[6], 0f, t[7]);
		pose(m.leftLeg, air, t[8], 0f, t[9]);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
