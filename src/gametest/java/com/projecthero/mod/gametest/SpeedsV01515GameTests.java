package com.projecthero.mod.gametest;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.DirectionalFlightModel.Tune;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.power.ThorPowers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15 speeds (explicit user numbers, blocks per second): every flight measured in steady state through the same
 * directional-flight model the client moves the player with, and Super Speed measured by running a real player through
 * vanilla's own ground-movement code ({@code LivingEntity#travel}) with the mode's attributes on.
 */
public class SpeedsV01515GameTests implements FabricGameTest {

	/** Fly level along +Z for 5 s from a standstill, holding W; the distance covered in the last second (blocks/s). */
	static double flown(Tune tune) {
		Vec3 look = new Vec3(0, 0, 1);
		Vec3 v = Vec3.ZERO;
		Vec3 pos = Vec3.ZERO;
		Vec3 at80 = null;
		for (int tick = 1; tick <= 100; tick++) {
			Vec3 wanted = DirectionalFlightModel.wantedVelocity(look, 0f, 1f, 0f, 0, tune);
			v = DirectionalFlightModel.step(v, wanted, true, tune, false);
			pos = pos.add(v); // DirectionalFlight moves the player by exactly this velocity each tick
			if (tick == 80) {
				at80 = pos;
			}
		}
		return pos.subtract(at80).horizontalDistance();
	}

	private static void near(GameTestHelper h, String what, double got, double want) {
		h.assertTrue(Math.abs(got - want) < 0.05, what + ": measured " + got + " b/s, want " + want);
	}

	private static Tune ironMan(IronManSuit suit, boolean sprint) {
		boolean sprintFly = sprint && suit.sprintFlight();
		return DirectionalFlightModel.ironManSuit(suit.flightSpeed(), suit.flightAcceleration(), suit.maxFlightSpeedMps(),
				suit.flightCruiseFor(sprintFly), sprintFly, false, 1.0);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyFlightFliesItsV01515Speed(GameTestHelper h) {
		near(h, "Kryptonian cruise", flown(DirectionalFlightModel.kryptonian(false, false)), 20.0);
		near(h, "Kryptonian sprint", flown(DirectionalFlightModel.kryptonian(true, false)), 45.0);
		h.assertTrue(flown(DirectionalFlightModel.kryptonian(true, true)) > 45.0, "Flight Boost still beats sprint flight");

		near(h, "Thor cruise", flown(DirectionalFlightModel.thor(ThorPowers.THOR_FLYING_SPEED, false)), 18.0);
		near(h, "Thor sprint", flown(DirectionalFlightModel.thor(ThorPowers.THOR_FLYING_SPEED, true)), 36.0);

		near(h, "Max Steel cruise", flown(DirectionalFlightModel.maxSteel(HeroFlight.HERO_FLYING_SPEED, false)), 18.0);
		near(h, "Max Steel sprint", flown(DirectionalFlightModel.maxSteel(HeroFlight.HERO_FLYING_SPEED, true)), 36.0);

		// the server's flying speed (cruise or the Boost value) no longer changes Ring Flight's horizontal speed
		float boostFly = GreenLanternConfig.FLIGHT_FLYING_SPEED
				* (float) (GreenLanternConfig.BOOST_SPEED_BPS / GreenLanternConfig.FLIGHT_CRUISE_SPEED_BPS);
		near(h, "Green Lantern cruise", flown(DirectionalFlightModel.greenLantern(GreenLanternConfig.FLIGHT_FLYING_SPEED, false, false)), 18.0);
		near(h, "Green Lantern sprint", flown(DirectionalFlightModel.greenLantern(GreenLanternConfig.FLIGHT_FLYING_SPEED, true, false)), 40.0);
		near(h, "Green Lantern sprint (stale boost flyingSpeed)", flown(DirectionalFlightModel.greenLantern(boostFly, true, false)), 40.0);
		h.assertTrue(flown(DirectionalFlightModel.greenLantern(boostFly, true, true)) > 40.0, "Boost still beats sprint flight");

		Object[][] marks = {
				{ IronManSuits.MARK_1, 8.0, 8.0 },
				{ IronManSuits.MARK_2, 15.0, 25.0 },
				{ IronManSuits.MARK_III, 17.0, 32.0 },
				{ IronManSuits.MARK_4, 17.0, 32.0 },
				{ IronManSuits.MARK_V, 17.0, 28.0 },
				{ IronManSuits.MARK_6, 18.0, 36.0 },
				{ IronManSuits.MARK_VII, 18.0, 36.0 },
				{ IronManSuits.MARK_8, 18.0, 36.0 } };
		for (Object[] m : marks) {
			IronManSuit suit = (IronManSuit) m[0];
			near(h, suit.id() + " cruise", flown(ironMan(suit, false)), (Double) m[1]);
			near(h, suit.id() + " sprint", flown(ironMan(suit, true)), (Double) m[2]);
		}
		h.succeed();
	}

	// ---- Super Speed: a real player through vanilla's ground movement ----------------------------------------------

	/**
	 * Runs {@code p} straight along +Z on the stone floor for 4 s with W held (vanilla's 0.98 input), putting it back
	 * at the start every tick so the 7-block room is enough (the velocity carries on), and returns the distance covered
	 * per second over the last second.
	 */
	private static double run(ServerPlayer p, Vec3 start, boolean sprint) {
		p.setSprinting(sprint);
		p.setDeltaMovement(Vec3.ZERO);
		p.setOnGround(true);
		double lastSecond = 0.0;
		for (int tick = 1; tick <= 80; tick++) {
			ExperimentalPowers.serverTick(p); // the mode's walk / sprint attribute follows isSprinting
			p.setPos(start.x, start.y, start.z);
			p.setYRot(0f);
			p.travel(new Vec3(0.0, 0.0, 0.98));
			double moved = p.position().subtract(start).horizontalDistance();
			if (tick > 60) {
				lastSecond += moved;
			}
		}
		p.setPos(start.x, start.y, start.z);
		p.setDeltaMovement(Vec3.ZERO);
		return lastSecond;
	}

	private static ServerPlayer speedster(GameTestHelper h, Vec3 start) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 2, 0)), h.absolutePos(new BlockPos(6, 5, 6)))) {
			h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(6, 1, 6)))) {
			h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(start.x, start.y, start.z, 0.0f, 0.0f);
		Power power = Powers.byKey(SuperSpeedHandlers.KEY);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01515_speed_mode")
	public void speedModeRunsTwentyAndForty(GameTestHelper h) {
		Vec3 start = h.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		ServerPlayer p = speedster(h, start);
		ExperimentalPowers.serverTick(p);
		AbilityRouter.handleInput(p, 6, true); // C: Speed Mode on
		AbilityRouter.handleInput(p, 6, false);
		double walk = run(p, start, false);
		double sprint = run(p, start, true);
		h.assertTrue(Math.abs(walk - 20.0) < 0.5, "Speed Mode walking measured " + walk + " b/s, want 20");
		h.assertTrue(Math.abs(sprint - 40.0) < 0.5, "Speed Mode sprinting measured " + sprint + " b/s, want 40");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01515_overdrive")
	public void overdriveRunsThirtyTwoAndOneTwenty(GameTestHelper h) {
		Vec3 start = h.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		ServerPlayer p = speedster(h, start);
		ExperimentalPowers.serverTick(p);
		AbilityRouter.handleInput(p, 5, true); // V: Overdrive
		AbilityRouter.handleInput(p, 5, false);
		double walk = run(p, start, false);
		double sprint = run(p, start, true);
		h.assertTrue(Math.abs(walk - 32.0) < 0.5, "Overdrive walking measured " + walk + " b/s, want 32");
		h.assertTrue(Math.abs(sprint - 120.0) < 1.0, "Overdrive sprinting measured " + sprint + " b/s, want 120");
		h.succeed();
	}
}
