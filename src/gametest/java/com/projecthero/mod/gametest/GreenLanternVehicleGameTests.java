package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.construct.Construct;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.construct.GreenLanternVehicles;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.entity.GreenLanternEntityTypes;
import com.projecthero.mod.greenlantern.entity.HardLightCarEntity;
import com.projecthero.mod.greenlantern.entity.HardLightJetEntity;
import com.projecthero.mod.greenlantern.entity.HardLightVehicleEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Carry Platform's key cycles Platform -> Car -> Jet -> Platform; the vehicles' costs, riders carried over,
 * N / destruction ending them, the Shift-hold dismount rule and the Jet's wing cannons.
 *
 * <p>v0.17.1 round 2: the key only reshapes while you look at the form (or drive it) -- otherwise it is the old follow /
 * park toggle; a reshape never drops riders mid-air; the easier controls (the Car's brake / reverse / steering numbers and
 * mouse assist, the Jet's hover instead of a stall, a parked Jet letting itself down).
 */
public class GreenLanternVehicleGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer lantern(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 1, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		// the platform forms 5 blocks ahead (+Z): centred at x 3.5 there is room for the jet's box too
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 0.5));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE * 0.5f; // no Willpower Surge either way
		GreenLantern.save(player, s);
		return player;
	}

	private static Construct carry(ServerPlayer p) {
		for (Construct c : GreenLanternConstructs.of(p.getUUID())) {
			if (c.type == ConstructType.CARRY_PLATFORM) {
				return c;
			}
		}
		return null;
	}

	/** Crosshair on the current form (the platform's top, or the vehicle's middle). */
	private static void lookAtIt(ServerPlayer p, Construct c) {
		Vec3 target = c.vehicleForm == GreenLanternVehicles.FORM_PLATFORM
				? Vec3.atBottomCenterOf(c.platformBlockCenter).add(0, 1.0, 0)
				: c.vehicle.position().add(0, 0.6, 0);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, target);
	}

	/** Facing back the other way (-Z), away from everything the tests build ahead of the player. */
	private static void lookAway(ServerPlayer p) {
		p.setYRot(180f);
		p.setYHeadRot(180f);
		p.setXRot(0f);
	}

	/** The key, pressed while looking at the form: the next form. */
	private static void reshape(ServerPlayer p) {
		Construct c = carry(p);
		if (c != null && !(p.getVehicle() == c.vehicle && c.vehicle != null)) {
			lookAtIt(p, c);
		}
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void keyCyclesPlatformCarJetPlatform(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		Construct c = carry(p);
		helper.assertTrue(c != null && !c.cells.isEmpty() && c.vehicleForm == GreenLanternVehicles.FORM_PLATFORM, "a platform first");
		float before = GreenLanternEnergy.get(p);
		reshape(p);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_CAR && c.vehicle instanceof HardLightCarEntity, "then a car");
		helper.assertTrue(c.cells.isEmpty(), "the platform's blocks are gone");
		helper.assertTrue(Math.abs(before - GreenLanternEnergy.get(p) - GreenLanternConfig.VEHICLE_CAR_COST) < 0.01f, "the car costs 40");
		helper.assertTrue(Math.abs(GreenLanternVehicles.upkeepPerSec(c) - GreenLanternConfig.VEHICLE_CAR_UPKEEP_PER_SEC) < 1e-4f,
				"car upkeep");
		HardLightVehicleEntity car = (HardLightVehicleEntity) c.vehicle;
		reshape(p);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_JET && c.vehicle instanceof HardLightJetEntity, "then a jet");
		helper.assertTrue(car.isRemoved(), "the car went");
		HardLightVehicleEntity jet = (HardLightVehicleEntity) c.vehicle;
		reshape(p);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_PLATFORM && !c.cells.isEmpty() && c.vehicle == null,
				"and back to the platform");
		helper.assertTrue(jet.isRemoved(), "the jet went");
		helper.assertTrue(!c.toggledOn, "the reshaped platform starts parked");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void keyNotLookingTogglesFollowLookingReshapes(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		Construct c = carry(p);
		// a level gaze over the platform at your feet is not "looking at it" (the padding stops short of eye height)
		helper.assertTrue(!GreenLanternVehicles.lookingAt(p, c), "looking straight ahead over it does not count");
		lookAway(p);
		float before = GreenLanternEnergy.get(p);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.toggledOn && c.vehicleForm == GreenLanternVehicles.FORM_PLATFORM && !c.cells.isEmpty(),
				"not looking at it: it follows (still a platform)");
		helper.assertTrue(Math.abs(before - GreenLanternEnergy.get(p)) < 0.01f, "following is free, like the old toggle");
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(!c.toggledOn && c.vehicleForm == GreenLanternVehicles.FORM_PLATFORM, "again: parked");
		lookAtIt(p, c);
		helper.assertTrue(GreenLanternVehicles.lookingAt(p, c), "the crosshair on it");
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_CAR, "looking at it: reshaped into the car");
		// the car the same way: looking away it follows, looking at it reshapes
		lookAway(p);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.toggledOn && c.vehicleForm == GreenLanternVehicles.FORM_CAR, "the car follows");
		reshape(p);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_JET && !c.toggledOn, "looking at the car: the jet, parked");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void aFollowingPlatformChasesTheAim(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		Construct c = carry(p);
		int x0 = c.platformBlockCenter.getX();
		// turn to face +X: not at the platform (it is at +Z), so the key is the follow toggle
		p.setYRot(-90f);
		p.setYHeadRot(-90f);
		p.setXRot(0f);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.toggledOn, "following");
		helper.succeedWhen(() -> {
			helper.assertTrue(c.platformBlockCenter.getX() >= x0 + 2,
					"it moves toward 5 blocks ahead of the aim (+X), at " + c.platformBlockCenter + " from x " + x0);
			GreenLanternConstructs.dismissAll(p.getUUID());
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aFollowingCarComesToItsOwner(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		Construct c = carry(p);
		HardLightCarEntity car = (HardLightCarEntity) c.vehicle;
		lookAway(p);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.toggledOn, "the car follows");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(car.following(), "it has its owner as its follow target");
			// 7 blocks off, facing the owner: it drives up and stops about VEHICLE_FOLLOW_DISTANCE away
			Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 7.5));
			car.moveTo(at.x, at.y, at.z, 180f, 0f);
			for (int i = 0; i < 60; i++) {
				car.stepForTest(null);
			}
			double d = Math.sqrt(car.distanceToSqr(p.getX(), car.getY(), p.getZ()));
			helper.assertTrue(d < 6.0 && d > 3.0, "it came to about 4.5 blocks away, at " + d);
			// someone gets in: it is theirs to drive, and it is parked from then on
			p.startRiding(car, true);
		});
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!c.toggledOn && !car.following(), "boarding parks it");
			GreenLanternConstructs.dismissAll(p.getUUID());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ridersAreCarriedIntoTheNextForm(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		Construct c = carry(p);
		HardLightVehicleEntity car = (HardLightVehicleEntity) c.vehicle;
		p.startRiding(car, true);
		helper.assertTrue(car.getControllingPassenger() == p, "the owner drives");
		lookAway(p); // inside it, the key reshapes wherever you look
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(p.getVehicle() instanceof HardLightJetEntity jet && jet.getControllingPassenger() == p,
				"the driver is now the pilot");
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(p.getVehicle() != null && c.seatEntityIds.contains(p.getVehicle().getId()), "and sat on the platform");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.assertTrue(p.getVehicle() == null, "N puts everyone down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aReshapeNeverDropsRidersMidAir(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		reshape(p);
		Construct c = carry(p);
		HardLightJetEntity jet = (HardLightJetEntity) c.vehicle;
		p.startRiding(jet, true);
		// up in the air (3.5 over the floor: inside the test cage)
		jet.moveTo(jet.getX(), jet.getY() + 3.5, jet.getZ(), jet.getYRot(), 0f);
		jet.positionRider(p);
		int airY = BlockPos.containing(jet.position()).getY();
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_PLATFORM && !c.cells.isEmpty(), "a jet in mid-air: a platform");
		helper.assertTrue(c.platformBlockCenter.getY() == airY, "hovering where the jet was (" + c.platformBlockCenter + ")");
		helper.assertTrue(p.getVehicle() != null && c.seatEntityIds.contains(p.getVehicle().getId()), "the pilot is seated on it");
		helper.assertTrue(!c.toggledOn, "parked: it holds its place");
		// the airborne platform skips the car (a car would only fall) and goes straight to the jet, rider aboard
		lookAtIt(p, c);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_JET, "no car in mid-air: the jet");
		helper.assertTrue(p.getVehicle() == c.vehicle, "the rider is in it");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dismissRemovesTheVehicle(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		Construct c = carry(p);
		HardLightVehicleEntity car = (HardLightVehicleEntity) c.vehicle;
		helper.assertTrue(!GreenLanternEntityTypes.HARD_LIGHT_CAR.canSerialize() && !GreenLanternEntityTypes.HARD_LIGHT_JET.canSerialize(),
				"vehicles are never saved");
		GreenLanternConstructs.dismissRequested(p);
		helper.assertTrue(car.isRemoved() && carry(p) == null, "N dissolves the car with the construct");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void destroyingTheCarEndsTheConstruct(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		HardLightVehicleEntity car = (HardLightVehicleEntity) carry(p).vehicle;
		helper.assertTrue(Math.abs(car.getMaxHealth() - GreenLanternConfig.VEHICLE_CAR_HP) < 0.01f, "the car has 120 HP");
		// its owner's own hits never chip it
		car.hurt(helper.getLevel().damageSources().playerAttack(p), 50f);
		helper.assertTrue(car.getHealth() == car.getMaxHealth(), "owner fire is ignored");
		car.hurt(helper.getLevel().damageSources().generic(), 1000f);
		helper.succeedWhen(() -> helper.assertTrue(carry(p) == null && car.isRemoved(), "a wrecked car ends the construct"));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void shiftIsTheShiftRModifierNotADismount(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		HardLightVehicleEntity car = (HardLightVehicleEntity) carry(p).vehicle;
		if (!helper.getLevel().isPositionEntityTicking(car.blockPosition())) {
			// the Shift-hold count lives in the car's own tick (see aParkedJetSettlesGently)
			GreenLanternConstructs.dismissAll(p.getUUID());
			helper.succeed();
			return;
		}
		p.startRiding(car, true);
		p.setShiftKeyDown(true);
		helper.assertTrue(!car.passengerWantsOut(p), "a tap of Shift is not a dismount");
		// Shift+R: the horn / boost -- and that Shift hold no longer gets you out
		helper.assertTrue(GreenLanternVehicles.ringKey(p, true), "Shift+R is handled by the car");
		helper.assertTrue(car.boosting(), "boosting");
		helper.runAfterDelay(GreenLanternConfig.VEHICLE_DISMOUNT_HOLD_TICKS + 5, () -> {
			helper.assertTrue(!car.passengerWantsOut(p), "the Shift+R hold does not dismount");
			p.setShiftKeyDown(false);
		});
		helper.runAfterDelay(GreenLanternConfig.VEHICLE_DISMOUNT_HOLD_TICKS + 8, () -> p.setShiftKeyDown(true));
		helper.runAfterDelay(2 * GreenLanternConfig.VEHICLE_DISMOUNT_HOLD_TICKS + 12, () -> {
			helper.assertTrue(car.passengerWantsOut(p) || p.getVehicle() == null, "holding Shift alone gets you out");
			GreenLanternConstructs.dismissAll(p.getUUID());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void jetCannonsHitTheCrosshair(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		reshape(p);
		HardLightJetEntity jet = (HardLightJetEntity) carry(p).vehicle;
		p.startRiding(jet, true);
		Husk h = EntityType.HUSK.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 7.5));
		h.moveTo(at.x, at.y, at.z, 180f, 0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		h.getAttribute(Attributes.ARMOR).setBaseValue(0);
		h.setHealth(200);
		helper.getLevel().addFreshEntity(h);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, h.position().add(0, 1.0, 0));
		float before = GreenLanternEnergy.get(p);
		GreenLanternVehicles.fireCannons(p, jet);
		float dealt = 200f - h.getHealth();
		helper.assertTrue(Math.abs(dealt - 2 * GreenLanternConfig.VEHICLE_JET_CANNON_DAMAGE) < 0.6f, "two 4-damage bolts, dealt " + dealt);
		helper.assertTrue(Math.abs(before - GreenLanternEnergy.get(p) - GreenLanternConfig.VEHICLE_JET_CANNON_COST) < 0.01f, "3 charge a pair");
		// R itself: plain R in the jet is the cannons (held), not the ring bolt
		helper.assertTrue(GreenLanternVehicles.ringKey(p, true) && GreenLanternVehicles.firing(p), "R holds the cannons");
		GreenLanternVehicles.ringKey(p, false);
		helper.assertTrue(!GreenLanternVehicles.firing(p), "and lets go");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theJetHoversInsteadOfStalling(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		reshape(p);
		HardLightJetEntity jet = (HardLightJetEntity) carry(p).vehicle;
		// 3.5 up, nose back toward the middle of the cage; the steps all run inside this one test tick (no parked ticks between)
		jet.moveTo(jet.getX(), jet.getY() + 3.5, jet.getZ(), 180f, 0f);
		double y0 = jet.getY();
		// no airspeed, throttle closed: it used to stall and sink at up to 5 b/s -- now it hovers, holding its height
		jet.setAirspeedForTest(0.0, 0f);
		HardLightVehicleEntity.Controls idle = new HardLightVehicleEntity.Controls(0f, 0f, false, false, 180f, 0f);
		for (int i = 0; i < 20; i++) {
			jet.stepForTest(idle);
		}
		helper.assertTrue(Math.abs(jet.getY() - y0) < 0.05, "a second at no airspeed: it holds its height, moved " + (jet.getY() - y0));
		helper.assertTrue(jet.hovering(), "hovering (the HUD's HOVER tag)");
		// slow flight (8 b/s, well under the old 18 b/s stall): still no sink
		jet.setAirspeedForTest(8.0, (float) (8.0 / GreenLanternConfig.VEHICLE_JET_TOP_BPS));
		for (int i = 0; i < 5; i++) {
			jet.stepForTest(idle);
		}
		helper.assertTrue(Math.abs(jet.getY() - y0) < 0.05, "slow flight holds its height too, moved " + (jet.getY() - y0));
		helper.assertTrue(Math.abs(jet.airspeedBps() - 8.0) < 0.5, "and the throttle holds its speed (" + jet.airspeedBps() + ")");
		// Space rises (and eases the throttle off), Sneak descends
		double y1 = jet.getY();
		HardLightVehicleEntity.Controls up = new HardLightVehicleEntity.Controls(0f, 0f, true, false, 180f, 0f);
		for (int i = 0; i < 6; i++) {
			jet.stepForTest(up); // 6 ticks, so it stays well under the test cage's ceiling
		}
		helper.assertTrue(jet.getY() - y1 > 1.0, "Space climbs at about 7 b/s, rose " + (jet.getY() - y1));
		helper.assertTrue(jet.throttle() < 8.0 / GreenLanternConfig.VEHICLE_JET_TOP_BPS, "and eases the throttle toward a hover");
		double y2 = jet.getY();
		HardLightVehicleEntity.Controls down = new HardLightVehicleEntity.Controls(0f, 0f, false, true, 180f, 0f);
		for (int i = 0; i < 6; i++) {
			jet.stepForTest(down);
		}
		helper.assertTrue(jet.getY() < y2 - 0.5, "Sneak descends");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aParkedJetSettlesGently(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		reshape(p);
		HardLightJetEntity jet = (HardLightJetEntity) carry(p).vehicle;
		// 3.5 up, well inside the test cage: 8 up put it at / in the cage's barrier ceiling, where it could not move at all
		jet.moveTo(jet.getX(), jet.getY() + 3.5, jet.getZ(), jet.getYRot(), 0f);
		// CI places tests at far positions whose chunks may not tick entities (the v0.16.5 frozen-chunk lesson): the jet's
		// own physics only runs when it ticks, so measure against ITS tick count, not the test's clock, and skip a frozen spot
		if (!helper.getLevel().isPositionEntityTicking(jet.blockPosition())) {
			GreenLanternConstructs.dismissAll(p.getUUID());
			helper.succeed();
			return;
		}
		double y0 = jet.getY();
		int t0 = jet.tickCount;
		double descent = GreenLanternConfig.VEHICLE_JET_PARKED_DESCENT_BPS / 20.0;
		helper.succeedWhen(() -> {
			int dt = jet.tickCount - t0;
			helper.assertTrue(dt >= 8, "waiting for 8 jet ticks (" + dt + ")");
			double drop = y0 - jet.getY();
			// nobody flying it: it lets itself down at most 4 b/s (eased in over the first few ticks), never a plummet
			helper.assertTrue(drop > 0.08 * (dt - 3) && drop <= descent * dt + 0.1,
					"it lets itself down gently, dropped " + drop + " in " + dt + " ticks");
			helper.assertTrue(jet.isAlive() && carry(p) != null, "still standing");
			GreenLanternConstructs.dismissAll(p.getUUID());
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carBrakeAndReverseNumbers(GameTestHelper helper) {
		// pure numbers (HardLightCarEntity#longitudinal): from the 50 b/s top speed, S stops it in 15 ticks (0.75 s; was 23)
		double v = GreenLanternConfig.VEHICLE_CAR_TOP_BPS / 20.0;
		int ticks = 0;
		while (v > 0 && ticks < 100) {
			v = HardLightCarEntity.longitudinal(v, -1f, true, false);
			ticks++;
		}
		helper.assertTrue(ticks <= 15, "50 b/s to a stop in " + ticks + " ticks");
		// still holding S: straight on into reverse, top reverse speed (16 b/s) in 11 ticks
		int rev = 0;
		while (v > -GreenLanternConfig.VEHICLE_CAR_REVERSE_BPS / 20.0 + 1e-6 && rev < 100) {
			v = HardLightCarEntity.longitudinal(v, -1f, true, false);
			rev++;
		}
		helper.assertTrue(rev <= 11, "0 to full reverse in " + rev + " ticks");
		helper.assertTrue(Math.abs(v * 20.0 + GreenLanternConfig.VEHICLE_CAR_REVERSE_BPS) < 1e-6, "reverse tops out at 16 b/s");
		// W while reversing brakes first: back to rest in under a quarter second
		int fwd = 0;
		while (v < 0 && fwd < 100) {
			v = HardLightCarEntity.longitudinal(v, 1f, true, false);
			fwd++;
		}
		helper.assertTrue(fwd <= 5, "reverse to rest with W in " + fwd + " ticks");
		// steering: tight at a crawl, calm at top speed
		helper.assertTrue(HardLightCarEntity.steerRate(0.15) > 4.5, "crawl steering " + HardLightCarEntity.steerRate(0.15));
		helper.assertTrue(Math.abs(HardLightCarEntity.steerRate(GreenLanternConfig.VEHICLE_CAR_TOP_BPS / 20.0)
				- GreenLanternConfig.VEHICLE_CAR_STEER_FAST_DEG) < 1e-4, "top-speed steering");
		helper.assertTrue(HardLightCarEntity.steerRate(0.0) == 0.0, "no turning on the spot");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carMouseAssistTurnsTowardTheView(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		HardLightCarEntity car = (HardLightCarEntity) carry(p).vehicle;
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 6.5));
		car.moveTo(at.x, at.y, at.z, 180f, 0f);
		// W held, A/D free, looking 30 degrees right of the nose: it eases round toward the view and never past it
		HardLightVehicleEntity.Controls w = new HardLightVehicleEntity.Controls(0f, 1f, false, false, 210f, 0f);
		for (int i = 0; i < 12; i++) {
			car.stepForTest(w);
		}
		float turned = Mth.wrapDegrees(car.getYRot() - 180f);
		helper.assertTrue(turned > 5f && turned <= 30.01f, "turned toward the view by " + turned);
		helper.assertTrue(car.forwardBps() > 5.0, "while driving forward (" + car.forwardBps() + " b/s)");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aVehicleCannotBeDrivenUntilItIsBuilt(GameTestHelper helper) {
		// v0.17.1: the Car and the Jet build themselves piece by piece, pixel by pixel, over 1-2 s -- the driver's controls
		// only take once they are whole
		helper.assertTrue(GreenLanternConfig.VEHICLE_BUILD_TICKS >= 20 && GreenLanternConfig.VEHICLE_BUILD_TICKS <= 40,
				"the build takes 1-2 s (" + GreenLanternConfig.VEHICLE_BUILD_TICKS + " ticks)");
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		reshape(p);
		HardLightVehicleEntity car = (HardLightVehicleEntity) carry(p).vehicle;
		helper.assertTrue(car instanceof HardLightCarEntity, "the car formed");
		HardLightVehicleEntity.Controls w = new HardLightVehicleEntity.Controls(0f, 1f, false, false, 0f, 0f);
		helper.assertTrue(!car.built() && car.gate(w) == null, "freshly formed: still building, W does nothing");
		car.tickCount = GreenLanternConfig.VEHICLE_BUILD_TICKS - 1;
		helper.assertTrue(car.gate(w) == null, "one tick short: still building");
		car.tickCount = GreenLanternConfig.VEHICLE_BUILD_TICKS;
		helper.assertTrue(car.built() && car.gate(w) == w, "built: the controls take");
		// reshaped into the jet: a new build, so the jet is not flyable straight away either
		reshape(p);
		HardLightVehicleEntity jet = (HardLightVehicleEntity) carry(p).vehicle;
		helper.assertTrue(jet instanceof HardLightJetEntity && !jet.built() && jet.gate(w) == null, "the jet builds afresh");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.succeed();
	}
}
