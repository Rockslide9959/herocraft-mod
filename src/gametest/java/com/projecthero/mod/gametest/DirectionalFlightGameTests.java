package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.DirectionalFlightModel.Tune;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManFlightLook;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.kryptonian.KryptonianConfig;
import com.projecthero.mod.power.ThorPowers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: every flight is directional (W / S forward / back along the look, A / D strafe, Space / Sneak up / down, hover
 * with no input). The client controller itself cannot be gametested, so these check the pure model it runs on
 * ({@link DirectionalFlightModel}) and that each flight kept its speeds.
 */
public class DirectionalFlightGameTests implements FabricGameTest {
	private static final double EPS = 1.0e-6;

	/** The same look vector {@code Entity#calculateViewVector} gives for this pitch / yaw. */
	private static Vec3 look(float pitch, float yaw) {
		double f = pitch * (Math.PI / 180.0);
		double g = -yaw * (Math.PI / 180.0);
		return new Vec3(Math.sin(g) * Math.cos(f), -Math.sin(f), Math.cos(g) * Math.cos(f));
	}

	private static List<Tune> everyFlight() {
		IronManSuit m2 = IronManSuits.MARK_2;
		return List.of(
				DirectionalFlightModel.greenLantern(GreenLanternConfig.FLIGHT_FLYING_SPEED, false, false),
				DirectionalFlightModel.kryptonian(false, false),
				DirectionalFlightModel.thor(ThorPowers.THOR_FLYING_SPEED, false),
				DirectionalFlightModel.ironMan(m2.flightSpeed(), m2.flightAcceleration(), m2.maxFlightSpeedMps(), false, false),
				DirectionalFlightModel.repulsorBoots(false),
				DirectionalFlightModel.vanilla(com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED, false),
				DirectionalFlightModel.flightPower(com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED, false, 0, false));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sFliesBackwardAlongTheLook(GameTestHelper helper) {
		for (Tune tune : everyFlight()) {
			for (float pitch : new float[] { -50f, 0f, 35f }) {
				float yaw = 37f;
				Vec3 look = look(pitch, yaw);
				Vec3 wanted = DirectionalFlightModel.wantedVelocity(look, yaw, -1f, 0f, 0, tune);
				helper.assertTrue(wanted.dot(look) < 0.0, "S must fly against the look, not brake (" + tune + ")");
				helper.assertTrue(wanted.add(look.scale(tune.speed())).length() < EPS,
						"S is exactly the cruise speed straight backward along the look (" + tune + ")");
				Vec3 fwd = DirectionalFlightModel.wantedVelocity(look, yaw, 1f, 0f, 0, tune);
				helper.assertTrue(fwd.add(wanted).length() < EPS, "W and S are mirror images");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pitchClimbsAndDives(GameTestHelper helper) {
		for (Tune tune : everyFlight()) {
			Vec3 up = DirectionalFlightModel.wantedVelocity(look(-45f, 0f), 0f, 1f, 0f, 0, tune);
			Vec3 down = DirectionalFlightModel.wantedVelocity(look(45f, 0f), 0f, 1f, 0f, 0, tune);
			Vec3 level = DirectionalFlightModel.wantedVelocity(look(0f, 0f), 0f, 1f, 0f, 0, tune);
			Vec3 backUp = DirectionalFlightModel.wantedVelocity(look(-45f, 0f), 0f, -1f, 0f, 0, tune);
			helper.assertTrue(up.y > 0.0 && down.y < 0.0 && Math.abs(level.y) < EPS,
					"W along a raised look climbs, a lowered one dives (" + tune + ")");
			helper.assertTrue(backUp.y < 0.0, "backing away while looking up sinks (" + tune + ")");
			Vec3 strafe = DirectionalFlightModel.wantedVelocity(look(-60f, 20f), 20f, 0f, 1f, 0, tune);
			helper.assertTrue(Math.abs(strafe.y) < EPS && strafe.length() > 0.0, "A / D strafe stays horizontal");
			Vec3 rise = DirectionalFlightModel.wantedVelocity(look(30f, 0f), 0f, 0f, 0f, 1, tune);
			helper.assertTrue(Math.abs(rise.y - tune.verticalSpeed()) < EPS && rise.horizontalDistance() < EPS,
					"Space rises straight up at the flight's vertical speed");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noInputEasesToADeadHover(GameTestHelper helper) {
		for (Tune tune : everyFlight()) {
			Vec3 v = new Vec3(0.8, 0.3, -0.5);
			double last = v.length();
			for (int i = 0; i < 300; i++) {
				v = DirectionalFlightModel.step(v, Vec3.ZERO, false, tune, false);
				helper.assertTrue(v.length() <= last + EPS, "the hover only ever slows (" + tune + ")");
				last = v.length();
			}
			helper.assertTrue(v.equals(Vec3.ZERO), "and settles to a dead stop -- no gravity, no drift (" + tune + ")");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void kryptonianTurnsAroundInsteadOfBraking(GameTestHelper helper) {
		Tune tune = DirectionalFlightModel.kryptonian(false, false);
		Vec3 look = look(0f, 90f);
		Vec3 v = look.scale(KryptonianConfig.FLIGHT_SPRINT_SPEED);
		Vec3 wanted = DirectionalFlightModel.wantedVelocity(look, 90f, -1f, 0f, 0, tune);
		helper.assertTrue(Math.abs(DirectionalFlightModel.ease(v, wanted, true, tune) - KryptonianConfig.FLIGHT_BRAKE) < EPS,
				"reversing uses the old hard-brake rate");
		for (int i = 0; i < 80; i++) {
			v = DirectionalFlightModel.step(v, wanted, true, tune, false);
		}
		helper.assertTrue(v.subtract(look.scale(-KryptonianConfig.FLIGHT_SPEED)).length() < 1.0e-3,
				"holding S ends up flying backward at cruise speed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyFlightKeepsItsSpeeds(GameTestHelper helper) {
		// vanilla creative flight: +a per tick, 91% kept -> settles at a / 0.09 per tick
		float a = 0.069f;
		double disp = 0.0;
		double vel = 0.0;
		for (int i = 0; i < 400; i++) {
			disp = vel + a;
			vel = disp * 0.91;
		}
		helper.assertTrue(Math.abs(disp - a * DirectionalFlightModel.VANILLA_CRUISE_FACTOR) < 1.0e-4,
				"the vanilla tune cruises at exactly vanilla creative flight's terminal speed");

		Tune gl = DirectionalFlightModel.greenLantern(0.06f, false, false);
		Tune glSprint = DirectionalFlightModel.greenLantern(0.06f, true, false);
		Tune glBoost = DirectionalFlightModel.greenLantern(0.06f, true, true);
		helper.assertTrue(Math.abs(gl.speed() - 0.6) < EPS && Math.abs(glSprint.speed() - 1.2) < EPS,
				"Green Lantern: flyingSpeed x 10, doubled sprinting");
		helper.assertTrue(Math.abs(gl.verticalSpeed() - GreenLanternConfig.FLIGHT_VERTICAL_SPEED_BPS / 20.0) < EPS
				&& Math.abs(glBoost.verticalSpeed() - GreenLanternConfig.BOOST_VERTICAL_SPEED_BPS / 20.0) < EPS
				&& gl.sneakBoosts(), "Green Lantern: 8 b/s climb, 15 boosting, Sneak is the Boost modifier");
		helper.assertTrue(gl.acceleration() == GreenLanternConfig.FLIGHT_ACCELERATION && gl.idle() == GreenLanternConfig.FLIGHT_BRAKING,
				"Green Lantern keeps its handling");
		helper.assertTrue(DirectionalFlightModel.verticalInput(false, true, true) == 0
				&& DirectionalFlightModel.verticalInput(false, true, false) == -1, "boosting Sneak does not descend");

		helper.assertTrue(DirectionalFlightModel.kryptonian(false, false).speed() == KryptonianConfig.FLIGHT_SPEED
				&& DirectionalFlightModel.kryptonian(true, false).speed() == KryptonianConfig.FLIGHT_SPRINT_SPEED
				&& DirectionalFlightModel.kryptonian(true, true).speed() == KryptonianConfig.FLIGHT_BOOST_SPEED
				&& DirectionalFlightModel.kryptonian(false, false).verticalSpeed() == KryptonianConfig.FLIGHT_VERTICAL_SPEED
				&& DirectionalFlightModel.kryptonian(false, false).strafeScale() == 0.7, "Kryptonian speeds unchanged");

		Tune thor = DirectionalFlightModel.thor(ThorPowers.THOR_FLYING_SPEED, false);
		Tune thorSprint = DirectionalFlightModel.thor(ThorPowers.THOR_FLYING_SPEED, true);
		helper.assertTrue(Math.abs(thor.speed() - ThorPowers.THOR_FLYING_SPEED / 0.09) < EPS
				&& Math.abs(thorSprint.speed() - 2.0 * thor.speed()) < EPS
				&& Math.abs(thor.verticalSpeed() - ThorPowers.THOR_FLYING_SPEED * 7.5) < EPS,
				"Thor flies at the speed vanilla gave his flying speed (~15 b/s, ~31 sprinting)");

		IronManSuit m2 = IronManSuits.MARK_2;
		IronManSuit m7 = IronManSuits.MARK_VII;
		Tune ironMan = DirectionalFlightModel.ironMan(m2.flightSpeed(), m2.flightAcceleration(), m2.maxFlightSpeedMps(), false, false);
		helper.assertTrue(Math.abs(ironMan.speed() - 0.05 / 0.09) < EPS, "a Mark 2 cruises at plain creative-flight speed");
		Tune m7Sprint = DirectionalFlightModel.ironMan(m7.flightSpeed(), m7.flightAcceleration(), m7.maxFlightSpeedMps(), true, false);
		Vec3 fast = Vec3.ZERO;
		for (int i = 0; i < 100; i++) {
			fast = DirectionalFlightModel.step(fast, new Vec3(0, 0, m7Sprint.speed()), true, m7Sprint, false);
		}
		helper.assertTrue(Math.abs(fast.horizontalDistance() - m7.maxFlightSpeedMps() / 20.0) < EPS,
				"a Mark 7 is held to its 30 m/s ceiling");
		Tune supersonic = DirectionalFlightModel.ironMan(m7.flightSpeed(), m7.flightAcceleration(), m7.maxFlightSpeedMps(), false, true);
		Vec3 burst = DirectionalFlightModel.wantedVelocity(look(10f, 0f), 0f, -1f, 0f, 0, supersonic);
		helper.assertTrue(burst.dot(look(10f, 0f)) > 0.0
				&& Math.abs(burst.length() - DirectionalFlightModel.IRON_MAN_SUPERSONIC_SPEED) < EPS,
				"supersonic drives along the look whatever W / S say");

		Tune boots = DirectionalFlightModel.repulsorBoots(true);
		helper.assertTrue(Math.abs(DirectionalFlightModel.repulsorBoots(false).speed() - ironMan.speed()) < EPS
				&& boots.maxHorizontal() == 0.0,
				"Repulsor Boots: never slower than the creative-flight speed they always had, no extra ceiling");

		Tune cruise = DirectionalFlightModel.flightPower(0.06f, false, 0, false);
		Tune tier3 = DirectionalFlightModel.flightPower(0.105f, true, 3, false);
		Tune sonic = DirectionalFlightModel.flightPower(0.06f, false, 0, true);
		helper.assertTrue(Math.abs(cruise.maxHorizontal() - 15.0 / 20.0) < EPS
				&& Math.abs(tier3.maxHorizontal() - 46.0 / 20.0) < EPS
				&& Math.abs(sonic.forcedForward() - 50.0 / 20.0) < EPS,
				"Flight power: 15 b/s cruise, 46 b/s top tier, 50 b/s Sonic Flight");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void outsidePushesAndBackwardPose(GameTestHelper helper) {
		Vec3 recorded = new Vec3(0.5, 0.0, 0.0);
		helper.assertTrue(!DirectionalFlightModel.outsidePush(recorded, recorded.add(0, 0.18, 0), 0.18),
				"vanilla's own Space nudge is not an outside push");
		helper.assertTrue(DirectionalFlightModel.outsidePush(recorded, new Vec3(2.6, 0.2, 0.0), 0.0),
				"a dash is");
		helper.assertTrue(DirectionalFlightModel.adoptedPush(new Vec3(40, 0, 0)).length() <= DirectionalFlightModel.MAX_ADOPTED_PUSH + EPS,
				"a stale runaway velocity is never adopted whole");
		Tune tune = DirectionalFlightModel.vanilla(0.06f, false);
		Vec3 carried = DirectionalFlightModel.step(new Vec3(2.0, 0, 0), Vec3.ZERO, false, tune, true);
		helper.assertTrue(Math.abs(carried.x - 2.0 * (1.0 - DirectionalFlightModel.PUSH_EASE)) < EPS,
				"an adopted push bleeds off at vanilla's air drag");
		helper.assertTrue(DirectionalFlightModel.backward(0.3, -0.3) && !DirectionalFlightModel.backward(0.3, 0.3)
				&& !DirectionalFlightModel.backward(0.3, -0.05), "the pose reads mostly-backward motion as flying backward");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.14.21 Iron Man flight revamp

	/**
	 * v0.14.21: the look-and-feel numbers the client flight FX run on (IronManFlightLook) -- jets grow hover < forward <
	 * sprint < supersonic, a plain Sneak descent is a soft landing while diving in / coming in fast is a superhero
	 * landing, switching off in mid-air is never a landing, and the pose / sound timings hold their shape.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManFlightLookJetsAndLandings(GameTestHelper helper) {
		IronManFlightLook.JetState hover = IronManFlightLook.jetState(false, false, false, false);
		IronManFlightLook.JetState fwd = IronManFlightLook.jetState(true, false, false, false);
		IronManFlightLook.JetState back = IronManFlightLook.jetState(true, true, false, false);
		IronManFlightLook.JetState sprint = IronManFlightLook.jetState(true, false, true, false);
		IronManFlightLook.JetState sonic = IronManFlightLook.jetState(false, false, false, true);
		helper.assertTrue(hover == IronManFlightLook.JetState.HOVER && fwd == IronManFlightLook.JetState.FORWARD
				&& back == IronManFlightLook.JetState.BACK && sprint == IronManFlightLook.JetState.SPRINT
				&& sonic == IronManFlightLook.JetState.SUPERSONIC, "jet states read the flight state");
		helper.assertTrue(IronManFlightLook.jetLength(hover) < IronManFlightLook.jetLength(fwd)
				&& IronManFlightLook.jetLength(fwd) < IronManFlightLook.jetLength(sprint)
				&& IronManFlightLook.jetLength(sprint) < IronManFlightLook.jetLength(sonic)
				&& IronManFlightLook.jetParticles(hover) < IronManFlightLook.jetParticles(sprint)
				&& IronManFlightLook.jetSpeed(hover) < IronManFlightLook.jetSpeed(sprint),
				"short stabilising jets hovering, long strong ones sprinting");

		helper.assertTrue(!IronManFlightLook.isHardLanding(0.375, 0.375, true), "a plain Sneak descent settles softly");
		helper.assertTrue(!IronManFlightLook.isHardLanding(0.05, 0.0, true), "touching down from a hover settles softly");
		helper.assertTrue(IronManFlightLook.isHardLanding(0.6, 0.55, true), "diving in along the look is a superhero landing");
		helper.assertTrue(IronManFlightLook.isHardLanding(1.2, 0.2, true), "coming in fast and low is a superhero landing");
		helper.assertTrue(!IronManFlightLook.isHardLanding(1.5, 0.05, true), "skimming the floor flat is not");
		helper.assertTrue(!IronManFlightLook.isHardLanding(2.0, 1.0, false), "switching flight off in mid-air is never a landing");

		float in = IronManFlightLook.LANDING_IN;
		float hold = IronManFlightLook.LANDING_HOLD_END;
		helper.assertTrue(IronManFlightLook.landingWeight(-1f) == 0f && IronManFlightLook.landingWeight(in) == 1f
				&& IronManFlightLook.landingWeight(hold - 0.01f) == 1f
				&& IronManFlightLook.landingWeight(IronManFlightLook.LANDING_END) == 0f,
				"the landing slams down, holds, then rises");
		float held = (hold - in) / 20f;
		helper.assertTrue(held >= 0.5f && held <= 0.7f, "the superhero landing is held ~0.6 s, got " + held);
		helper.assertTrue(IronManFlightLook.takeoffCrouch(0f) == 0f
				&& IronManFlightLook.takeoffCrouch(IronManFlightLook.TAKEOFF_CROUCH_PEAK) == 1f
				&& IronManFlightLook.takeoffCrouch(IronManFlightLook.TAKEOFF_END) == 0f,
				"take-off is a brief crouch released into flight");

		helper.assertTrue(IronManFlightLook.thrusterVolume(0.0, false) > 0f
				&& IronManFlightLook.thrusterVolume(0.0, false) < IronManFlightLook.thrusterVolume(1.5, false)
				&& IronManFlightLook.thrusterVolume(3.0, true) <= 1f
				&& IronManFlightLook.thrusterPitch(0.0, false) < IronManFlightLook.thrusterPitch(2.0, false),
				"the thruster loop is audible hovering and louder / higher with speed");
		helper.assertTrue(IronManFlightLook.windVolume(0.0) == 0f && IronManFlightLook.windVolume(2.0) > 0.5f,
				"no wind rush hovering");
		helper.succeed();
	}

	/**
	 * v0.14.21: the server flight tick lost its feet-centre particle spam and its take-off / landing sounds (all client
	 * FX now) -- but the flight itself is untouched: a hovering Mark 2 still drains exactly 10/s x its multiplier, stays
	 * up, and a sprinting one 30/s.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManFlightTickKeepsItsDrain(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			var item = com.projecthero.mod.ironman.item.IronManItems.armor("mark_2", t);
			if (item != null) {
				player.setItemSlot(com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotFor(t), new ItemStack(item));
			}
		}
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_2", 5000f);
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(player, "mark_2", 250f);
		IronManFlight.setFlying(player, true);
		helper.assertTrue(IronManFlight.isFlying(player) && player.getAbilities().flying, "flight engages");
		player.setOnGround(false);
		float mult = IronManSuits.MARK_2.flightDrainMultiplier();

		float before = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_2");
		IronManFlight.tick(player);
		float hoverSpent = before - com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_2");
		helper.assertTrue(Math.abs(hoverSpent - 10f / 20f * mult) < 1.0e-3f, "hover drain unchanged, spent " + hoverSpent);
		helper.assertTrue(IronManFlight.isFlying(player), "still flying after a tick in the air");

		player.setSprinting(true);
		before = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_2");
		IronManFlight.tick(player);
		float sprintSpent = before - com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_2");
		helper.assertTrue(Math.abs(sprintSpent - 30f / 20f * mult) < 1.0e-3f, "sprint drain unchanged, spent " + sprintSpent);

		player.setOnGround(true);
		IronManFlight.tick(player);
		helper.assertTrue(!IronManFlight.isFlying(player) && !player.getAbilities().flying, "touching the ground still lands");
		helper.succeed();
	}
}
