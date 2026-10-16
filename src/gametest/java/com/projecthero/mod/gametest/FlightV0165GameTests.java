package com.projecthero.mod.gametest;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.p03.FlightHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Flight rework (docs/V0165_POWERS_PROPOSAL.md section 7): six keys (+ Shift versions) on a 0-100 Lift meter --
 * the meter (spends, refills, sprint-flying / Afterburner / Slipstream drains, refusals), the damage numbers (Flying
 * Punch 14, Dive Bomb 12-30, Sonic Boom 16), the grabs, the dodge, Hover, Updraft, Takeoff Blast and the flight speeds
 * (20 / 50 b/s). Mock players are not ticked by the server and never move themselves, so the tests drive
 * {@link ExperimentalPowers#serverTick} by hand and the dashes are checked by their state, not by distance flown. Target
 * dummies are no-AI iron golems (100 HP, no armour, so the damage numbers come through exactly).
 */
public class FlightV0165GameTests implements FabricGameTest {
	private static final String K = FlightHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock flier at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x5x9 room. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -1), base.offset(2, 4, 7))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -1), base.offset(2, -1, 7))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Power power = Powers.byKey(K);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		FlightHandlers.lift(p); // seeds the meter full, as the first upkeep tick does
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zb = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zb.setPersistenceRequired();
		zb.setNoAi(true);
		return zb;
	}

	private static void assertNear(GameTestHelper helper, float expected, float got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05f, what + ": expected " + expected + ", got " + got);
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysLiftMeterAndEnabled(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(Powers.isEnabled(power) && Powers.isMutation(power), "Flight is an enabled mutation again");
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		String[] ids = { "flying_punch", "grab_carry", "air_dash", "sonic_boom", "hover", "afterburner" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		var lift = MutationMeters.get(K, "lift");
		helper.assertTrue(lift != null && lift.always() && lift.style() == MutationMeters.Style.HAIRLINE,
				"Lift is an always-on Hairline");
		helper.assertTrue(MutationMeters.get(K, "orbital_cd") != null, "the Orbital Drop timer");
		for (String flag : new String[] { "p03.hover", "p03.slip", "p03.burn", "p03.wind" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), flag + " is synced");
		}
		helper.assertTrue(HeroFlight.hasFlight(power) && HeroFlight.infinite(power), "flight itself is free");
		helper.succeed();
	}

	// ---------------- flight / speeds ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void doubleTapTogglesFlight(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(FlightHandlers.toggleFlight(p) && HeroFlight.isFlying(p), "the double-tap takes off");
		helper.assertTrue(p.getAbilities().mayfly, "with hero flight");
		FlightHandlers.toggleFlight(p);
		helper.assertFalse(HeroFlight.isFlying(p), "and again lands");
		ExperimentalPowers.setActive(p, null);
		helper.assertFalse(FlightHandlers.toggleFlight(p), "only while Flight is the selected mutation");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightSpeeds(GameTestHelper helper) {
		assertNear(helper, 20f, (float) DirectionalFlightModel.flightPowerBps(false, true, false, false), "cruise");
		assertNear(helper, 50f, (float) DirectionalFlightModel.flightPowerBps(true, true, false, false), "sprint-flying");
		assertNear(helper, 20f, (float) DirectionalFlightModel.flightPowerBps(true, false, false, false), "sprint with no Lift cruises");
		assertNear(helper, 80f, (float) DirectionalFlightModel.flightPowerBps(true, true, true, false), "Afterburner +60%");
		assertNear(helper, 30f, (float) DirectionalFlightModel.flightPowerBps(false, true, false, true), "Slipstream +50%");
		var cruise = DirectionalFlightModel.flightPower(false, true, false, false, false);
		assertNear(helper, 1.0f, (float) cruise.speed(), "cruise tune (blocks a tick)");
		var hover = DirectionalFlightModel.flightPower(true, true, true, false, true);
		helper.assertTrue(hover.speed() == 0.0 && hover.verticalSpeed() == 0.0, "Hover hangs dead still");
		helper.succeed();
	}

	// ---------------- Lift ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void liftSpendsAndRefills(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(FlightHandlers.lift(p) >= 99.9f, "the meter starts full");
		press(p, 3); // Air Dash
		assertNear(helper, 90f, res(p, "lift"), "Air Dash costs 10");
		tick(p, 6); // the dash runs out
		FlightHandlers.setLift(p, 50);
		tick(p, 20);
		assertNear(helper, 62f, res(p, "lift"), "12 a second back on the ground");
		FlightHandlers.setLift(p, 5);
		press(p, 3);
		assertNear(helper, 5f, res(p, "lift"), "not enough Lift: refused, nothing spent");
		helper.assertTrue(res(p, "drive_ticks") < 0.5f, "and no dash");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sprintFlyingAfterburnerAndSlipstreamDrain(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		FlightHandlers.toggleFlight(p);
		FlightHandlers.setLift(p, 60);
		tick(p, 20);
		assertNear(helper, 72f, res(p, "lift"), "cruising refills 12 a second");
		p.setSprinting(true);
		tick(p, 20);
		assertNear(helper, 66f, res(p, "lift"), "sprint-flying spends 6 a second");
		AbilityRouter.handleInput(p, 6, true); // hold C
		helper.assertTrue(FlightHandlers.afterburner(p), "Afterburner on while C is held");
		tick(p, 20);
		assertNear(helper, 52f, res(p, "lift"), "sprint + Afterburner spend 6 + 8 a second");
		AbilityRouter.handleInput(p, 6, false);
		helper.assertFalse(FlightHandlers.afterburner(p), "off on release");
		p.setSprinting(false);
		shiftPress(p, 5); // Slipstream on
		helper.assertTrue(FlightHandlers.slipstream(p), "Slipstream toggles on");
		tick(p, 20);
		assertNear(helper, 48f, res(p, "lift"), "Slipstream spends 4 a second (no refill meanwhile)");
		FlightHandlers.setLift(p, 0.1f);
		tick(p, 2);
		helper.assertFalse(FlightHandlers.slipstream(p), "an empty meter ends it");
		helper.succeed();
	}

	// ---------------- R: Flying Punch / Dive Bomb ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "flight_v0165_punch")
	public void flyingPunchHits14WithKnockback(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 3);
		press(p, 1);
		assertNear(helper, 85f, res(p, "lift"), "15 Lift");
		helper.assertTrue(FlightHandlers.driveKind(p) == FlightHandlers.DRIVE_PUNCH, "he shoots in");
		tick(p, 1);
		assertNear(helper, 86f, g.getHealth(), "14 damage");
		helper.assertTrue(g.getDeltaMovement().z > 1.0, "and a big knockback along the dash, got " + g.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void diveBombDamageScalesWithHeight(GameTestHelper helper) {
		assertNear(helper, 12f, FlightHandlers.diveDamage(0), "12 from no height");
		assertNear(helper, 21f, FlightHandlers.diveDamage(15), "half way at 15 blocks");
		assertNear(helper, 30f, FlightHandlers.diveDamage(30), "30 at 30 blocks");
		assertNear(helper, 30f, FlightHandlers.diveDamage(90), "capped at 30");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "flight_v0165_dive")
	public void diveBombCratersTheRing(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem near = dummy(helper, 2, 4);
		IronGolem far = dummy(helper, 2, 8);
		shiftPress(p, 1);
		assertNear(helper, 100f, res(p, "lift"), "on the ground: refused");
		p.setOnGround(false);
		shiftPress(p, 1);
		assertNear(helper, 75f, res(p, "lift"), "25 Lift in the air");
		helper.assertTrue(FlightHandlers.driveKind(p) == FlightHandlers.DRIVE_DIVE, "rocketing down");
		// as if from 30 blocks up, now landed
		ExperimentalPowers.setResource(p, Powers.byKey(K), "dive_from_y", (float) p.getY() + 1030f, 1e9f);
		p.setOnGround(true);
		tick(p, 2);
		helper.assertTrue(FlightHandlers.driveKind(p) == 0, "the landing ends the dive");
		assertNear(helper, 70f, near.getHealth(), "30 inside the 4-block ring");
		assertNear(helper, 100f, far.getHealth(), "nothing outside it");
		helper.succeed();
	}

	// ---------------- G: Grab & Carry / Orbital Drop ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "flight_v0165_grab")
	public void grabCarryThenThrow(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 3);
		press(p, 2);
		helper.assertTrue(res(p, "carry_id") == z.getId(), "grabbed the zombie in front");
		assertNear(helper, 85f, res(p, "lift"), "15 Lift");
		tick(p, 2);
		helper.assertTrue(z.position().distanceTo(FlightHandlers.carryPoint(p, z)) < 0.05, "held out in front");
		press(p, 2);
		helper.assertTrue(res(p, "carry_id") < 0.5f, "let go");
		helper.assertTrue(z.getDeltaMovement().z > 1.5, "and thrown where he looks, got " + z.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "flight_v0165_orbit")
	public void orbitalDropLiftsThenLetsGo(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 3);
		shiftPress(p, 2);
		helper.assertTrue(res(p, "orbit_id") == z.getId(), "grabbed for the Orbital Drop");
		assertNear(helper, 60f, res(p, "lift"), "40 Lift");
		helper.assertTrue(FlightHandlers.driveKind(p) == FlightHandlers.DRIVE_RISE, "rocketing straight up");
		helper.assertTrue(res(p, "orbital_cd") > 299f, "a 15 s cooldown");
		// 40 blocks at 3 a tick (the mock player never really moves): the lift ends and lets go
		tick(p, 15);
		helper.assertTrue(res(p, "orbit_id") < 0.5f && z.fallDistance == 0f, "let go at the top, falling from there");
		helper.assertTrue(HeroFlight.isFlying(p), "and he stays up flying");
		FlightHandlers.setLift(p, 100);
		Zombie z2 = zombie(helper, 2, 3);
		shiftPress(p, 2);
		helper.assertTrue(res(p, "orbit_id") < 0.5f && res(p, "lift") > 99f, "not again on cooldown");
		z2.discard();
		helper.succeed();
	}

	// ---------------- X: Air Dash / Barrel Roll ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void barrelRollDodgesProjectilesOnly(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		shiftPress(p, 3);
		assertNear(helper, 90f, res(p, "lift"), "10 Lift");
		helper.assertTrue(FlightHandlers.rolling(p), "the roll opens the dodge window");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		var shot = p.level().damageSources().arrow(arrow, null);
		helper.assertTrue(FlightHandlers.damageFactor(p, shot) < 0f, "arrows miss mid-roll");
		helper.assertTrue(FlightHandlers.damageFactor(p, p.level().damageSources().generic()) == 1f, "other damage still lands");
		tick(p, 12);
		helper.assertFalse(FlightHandlers.rolling(p), "the window closes after 0.5 s");
		helper.assertTrue(FlightHandlers.damageFactor(p, shot) == 1f, "arrows hit again");
		helper.assertTrue(FlightHandlers.damageFactor(p, p.level().damageSources().fall()) < 0f, "never any fall damage");
		helper.succeed();
	}

	// ---------------- Z: Sonic Boom / Takeoff Blast ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "flight_v0165_boom")
	public void sonicBoomConeHits16(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem ahead = dummy(helper, 2, 6);
		IronGolem behind = dummy(helper, 2, 0);
		press(p, 4);
		assertNear(helper, 70f, res(p, "lift"), "30 Lift");
		assertNear(helper, 84f, ahead.getHealth(), "16 to the golem in the cone");
		helper.assertTrue(ahead.getDeltaMovement().z > 0.5 && ahead.getDeltaMovement().y > 0.3, "and a launch");
		assertNear(helper, 100f, behind.getHealth(), "nothing behind him");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "flight_v0165_takeoff")
	public void takeoffBlastLaunchesFromTheGround(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 4);
		shiftPress(p, 4);
		assertNear(helper, 80f, res(p, "lift"), "20 Lift");
		helper.assertTrue(HeroFlight.isFlying(p) && FlightHandlers.driveKind(p) == FlightHandlers.DRIVE_RISE, "blasting up into flight");
		helper.assertTrue(z.getDeltaMovement().z > 0.3, "the zombie within 5 is blown away, got " + z.getDeltaMovement());
		tick(p, 10);
		p.setOnGround(false);
		FlightHandlers.setLift(p, 100);
		shiftPress(p, 4);
		assertNear(helper, 100f, res(p, "lift"), "only from the ground");
		helper.succeed();
	}

	// ---------------- V: Hover / Slipstream, C: Updraft ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void hoverHangsStillWithNoKnockback(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 5);
		helper.assertTrue(FlightHandlers.hovering(p) && HeroFlight.isFlying(p), "V hovers (taking off)");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "no knockback");
		assertNear(helper, 100f, res(p, "lift"), "free");
		press(p, 3); // a dash ends it
		helper.assertFalse(FlightHandlers.hovering(p), "a dash ends the hover");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0, "knockback back to normal");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "flight_v0165_updraft")
	public void updraftLiftsEnemies(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 4);
		shiftPress(p, 6);
		assertNear(helper, 75f, res(p, "lift"), "25 Lift");
		helper.assertTrue(z.getDeltaMovement().y > 1.0, "the zombie within 6 is thrown up, got " + z.getDeltaMovement());
		helper.succeed();
	}
}
