package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.DirectionalFlightModel.Tune;
import com.projecthero.mod.flight.FlightAttitude;
import com.projecthero.mod.flight.PhysicsFlightModel;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.power.ThorPowers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: Physics Flight (the {@code ,} Flight Mode menu). The client controllers cannot be gametested, so these check
 * the pure model they run on ({@link PhysicsFlightModel}): momentum, throttle build-up, dive / climb, the unchanged top
 * speeds, the own-axis attitude maths behind banked turns, loops and barrel rolls, and the server's attitude sanitising.
 * Pure maths, no world -- each test finishes in its first tick.
 */
public class PhysicsFlightGameTests implements FabricGameTest {
	private static final double EPS = 1.0e-3;

	private static Vec3 look(float pitch, float yaw) {
		double f = pitch * (Math.PI / 180.0);
		double g = -yaw * (Math.PI / 180.0);
		return new Vec3(Math.sin(g) * Math.cos(f), -Math.sin(f), Math.cos(g) * Math.cos(f));
	}

	private static List<Tune> everyFlight() {
		IronManSuit m6 = IronManSuits.MARK_6;
		return List.of(
				DirectionalFlightModel.greenLantern(GreenLanternConfig.FLIGHT_FLYING_SPEED, true, false),
				DirectionalFlightModel.kryptonian(false, false),
				DirectionalFlightModel.kryptonian(true, true),
				DirectionalFlightModel.thor(ThorPowers.THOR_FLYING_SPEED, true),
				DirectionalFlightModel.nova(false),
				DirectionalFlightModel.ironManSuit(m6.flightSpeed(), m6.flightAcceleration(), m6.maxFlightSpeedMps(),
						m6.flightCruiseFor(true), true, false, 1.0),
				DirectionalFlightModel.maxSteel(0.1f, false),
				DirectionalFlightModel.flightPower(com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED, true, 2, false));
	}

	private static Vec3 fly(Vec3 v, Vec3 look, float throttle, Tune tune, int ticks) {
		for (int i = 0; i < ticks; i++) {
			v = PhysicsFlightModel.step(v, look, throttle, 0, tune, false);
		}
		return v;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void throttleBuildsToTheSameTopSpeed(GameTestHelper helper) {
		for (Tune tune : everyFlight()) {
			Vec3 look = look(0f, 30f);
			Vec3 first = PhysicsFlightModel.step(Vec3.ZERO, look, 1f, 0, tune, false);
			helper.assertTrue(first.length() > 0.0 && first.length() < tune.speed() * 0.5,
					"W builds speed instead of jumping to it (" + tune + ")");
			helper.assertTrue(first.normalize().dot(look) > 0.999, "from a hover the first push goes where you look");
			Vec3 v = Vec3.ZERO;
			double max = 0.0;
			for (int i = 0; i < 400; i++) {
				v = PhysicsFlightModel.step(v, look, 1f, 0, tune, false);
				max = Math.max(max, v.length());
			}
			double top = tune.maxHorizontal() > 0.0 ? Math.min(tune.speed(), tune.maxHorizontal()) : tune.speed();
			helper.assertTrue(Math.abs(v.length() - top) < top * 0.02, "level throttle settles at the flight's own top speed ("
					+ v.length() + " vs " + top + ", " + tune + ")");
			helper.assertTrue(max <= tune.speed() + EPS, "level flight never beats the top speed (" + tune + ")");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void divingGainsAndClimbingLoses(GameTestHelper helper) {
		for (Tune tune : everyFlight()) {
			double start = tune.speed() * 0.5;
			Vec3 dive = fly(look(45f, 0f).scale(start), look(45f, 0f), 0f, tune, 20);
			Vec3 level = fly(look(0f, 0f).scale(start), look(0f, 0f), 0f, tune, 20);
			Vec3 climb = fly(look(-45f, 0f).scale(start), look(-45f, 0f), 0f, tune, 20);
			helper.assertTrue(dive.length() > level.length() && level.length() > climb.length(),
					"a dive gains speed, a climb loses it (" + dive.length() + " / " + level.length() + " / "
							+ climb.length() + ", " + tune + ")");
			helper.assertTrue(level.length() < start && level.length() > start * 0.6,
					"gliding level with no throttle bleeds speed slowly (light drag)");
			Vec3 braked = PhysicsFlightModel.step(look(0f, 0f).scale(start), look(0f, 0f), -1f, 0, tune, false);
			helper.assertTrue(braked.length() < start * 0.9 && braked.normalize().dot(look(0f, 0f)) > 0.99,
					"S is an air brake, never a reverse");
			helper.assertTrue(fly(look(0f, 0f).scale(start), look(0f, 0f), -1f, tune, 60).lengthSqr() == 0.0,
					"braking long enough settles into a dead hover");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void turningCarriesMomentum(GameTestHelper helper) {
		for (Tune tune : everyFlight()) {
			Vec3 east = look(0f, -90f);
			Vec3 south = look(0f, 0f);
			Vec3 v = east.scale(tune.speed());
			Vec3 one = PhysicsFlightModel.step(v, south, 1f, 0, tune, false);
			helper.assertTrue(one.normalize().dot(east) > 0.9, "one tick after a 90-degree look the path has barely swung");
			Vec3 later = fly(v, south, 1f, tune, 60);
			helper.assertTrue(later.normalize().dot(south) > 0.99, "a few seconds later it flies where you look");
			helper.assertTrue(one.length() <= v.length() + EPS, "a hard turn never adds speed");
		}
		helper.succeed();
	}

	private static boolean same(float[] a, float yaw, float pitch, float roll) {
		return Math.abs(PhysicsFlightModel.wrap(a[0] - yaw)) < 0.05 && Math.abs(a[1] - pitch) < 0.05
				&& Math.abs(PhysicsFlightModel.wrap(a[2] - roll)) < 0.05;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ownAxisAttitude(GameTestHelper helper) {
		// a full barrel roll comes back where it started
		float[] a = { 30f, -10f, 0f };
		for (int i = 0; i < 36; i++) {
			a = PhysicsFlightModel.rotate(a[0], a[1], a[2], 0.0, 0.0, 10.0);
		}
		helper.assertTrue(same(a, 30f, -10f, 0f), "a 360-degree roll returns to level: " + java.util.Arrays.toString(a));

		// half a barrel roll: upside down, same heading
		a = new float[] { 30f, 0f, 0f };
		for (int i = 0; i < 18; i++) {
			a = PhysicsFlightModel.rotate(a[0], a[1], a[2], 0.0, 0.0, 10.0);
		}
		helper.assertTrue(same(a, 30f, 0f, 180f), "half a roll is inverted on the same heading: " + java.util.Arrays.toString(a));

		// a full loop (nose up all the way round) comes back level on the same heading
		a = new float[] { -45f, 0f, 0f };
		boolean sawInverted = false;
		for (int i = 0; i < 72; i++) {
			a = PhysicsFlightModel.rotate(a[0], a[1], a[2], -5.0, 0.0, 0.0);
			sawInverted |= Math.abs(Math.abs(a[2]) - 180f) < 1f;
			helper.assertTrue(a[1] >= -90f && a[1] <= 90f, "pitch always stays a Minecraft pitch");
		}
		helper.assertTrue(sawInverted, "over the top of a loop the flier is upside down");
		helper.assertTrue(same(a, -45f, 0f, 0f), "a full loop returns level: " + java.util.Arrays.toString(a));

		// banked 90 degrees right, pulling back turns right instead of climbing
		float[] banked = PhysicsFlightModel.rotate(0f, 0f, 90f, -10.0, 0.0, 0.0);
		helper.assertTrue(banked[0] > 9.0f && Math.abs(banked[1]) < 0.5f, "a banked pull turns: " + java.util.Arrays.toString(banked));

		// level, A / D yaw is a plain heading change
		float[] yawed = PhysicsFlightModel.rotate(10f, 0f, 0f, 0.0, 15.0, 0.0);
		helper.assertTrue(same(yawed, 25f, 0f, 0f), "level yaw only turns the heading");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void attitudeIsSanitised(GameTestHelper helper) {
		helper.assertTrue(FlightAttitude.sanitize(true, Float.NaN) == null, "a NaN roll is refused");
		helper.assertTrue(FlightAttitude.sanitize(true, Float.POSITIVE_INFINITY) == null, "an infinite roll is refused");
		FlightAttitude wrapped = FlightAttitude.sanitize(true, 450f);
		helper.assertTrue(wrapped != null && Math.abs(wrapped.roll() - 90f) < EPS, "rolls wrap into -180..180");
		FlightAttitude regular = FlightAttitude.sanitize(false, 60f);
		helper.assertTrue(regular != null && regular.roll() == 0f && !regular.physics(), "Regular flight never rolls");
		helper.succeed();
	}
}
