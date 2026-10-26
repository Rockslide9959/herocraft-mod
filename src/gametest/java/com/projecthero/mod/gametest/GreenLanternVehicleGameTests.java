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

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Carry Platform's key cycles Platform -> Car -> Jet -> Platform; the vehicles' costs, riders carried over,
 * N / destruction ending them, the Shift-hold dismount rule, the Jet's wing cannons and its gentle stall sink.
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void keyCyclesPlatformCarJetPlatform(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		Construct c = carry(p);
		helper.assertTrue(c != null && !c.cells.isEmpty() && c.vehicleForm == GreenLanternVehicles.FORM_PLATFORM, "a platform first");
		float before = GreenLanternEnergy.get(p);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_CAR && c.vehicle instanceof HardLightCarEntity, "then a car");
		helper.assertTrue(c.cells.isEmpty(), "the platform's blocks are gone");
		helper.assertTrue(Math.abs(before - GreenLanternEnergy.get(p) - GreenLanternConfig.VEHICLE_CAR_COST) < 0.01f, "the car costs 40");
		helper.assertTrue(Math.abs(GreenLanternVehicles.upkeepPerSec(c) - GreenLanternConfig.VEHICLE_CAR_UPKEEP_PER_SEC) < 1e-4f,
				"car upkeep");
		HardLightVehicleEntity car = (HardLightVehicleEntity) c.vehicle;
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_JET && c.vehicle instanceof HardLightJetEntity, "then a jet");
		helper.assertTrue(car.isRemoved(), "the car went");
		HardLightVehicleEntity jet = (HardLightVehicleEntity) c.vehicle;
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		helper.assertTrue(c.vehicleForm == GreenLanternVehicles.FORM_PLATFORM && !c.cells.isEmpty() && c.vehicle == null,
				"and back to the platform");
		helper.assertTrue(jet.isRemoved(), "the jet went");
		helper.assertTrue(!c.toggledOn, "the reshaped platform starts parked");
		GreenLanternConstructs.dismissAll(p.getUUID());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ridersAreCarriedIntoTheNextForm(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		Construct c = carry(p);
		HardLightVehicleEntity car = (HardLightVehicleEntity) c.vehicle;
		p.startRiding(car, true);
		helper.assertTrue(car.getControllingPassenger() == p, "the owner drives");
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
	public void dismissRemovesTheVehicle(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
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
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
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
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		HardLightVehicleEntity car = (HardLightVehicleEntity) carry(p).vehicle;
		if (!helper.getLevel().isPositionEntityTicking(car.blockPosition())) {
			// the Shift-hold count lives in the car's own tick (see aStalledJetSinksGently)
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
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
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
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, h.position().add(0, 1.0, 0));
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

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aStalledJetSinksGently(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		GreenLanternConstructs.deploy(p, ConstructType.CARRY_PLATFORM);
		HardLightJetEntity jet = (HardLightJetEntity) carry(p).vehicle;
		jet.moveTo(jet.getX(), jet.getY() + 8.0, jet.getZ(), jet.getYRot(), 0f);
		// CI places tests at far positions whose chunks may not tick entities (the v0.16.5 frozen-chunk lesson): the jet's
		// own physics only runs when it ticks, so measure against ITS tick count, not the test's clock, and skip a frozen spot
		if (!helper.getLevel().isPositionEntityTicking(jet.blockPosition())) {
			GreenLanternConstructs.dismissAll(p.getUUID());
			helper.succeed();
			return;
		}
		double y0 = jet.getY();
		int t0 = jet.tickCount;
		double sink = GreenLanternConfig.VEHICLE_JET_SINK_BPS / 20.0;
		helper.succeedWhen(() -> {
			int dt = jet.tickCount - t0;
			helper.assertTrue(dt >= 10, "waiting for 10 jet ticks (" + dt + ")");
			double drop = y0 - jet.getY();
			// the first tick it still thinks it is grounded (taxi: 0.05 down), then up to 0.25 a tick
			helper.assertTrue(drop > 0.15 * (dt - 1) && drop <= sink * dt + 0.1,
					"no airspeed: it sinks at most 5 b/s, dropped " + drop + " in " + dt + " ticks");
			helper.assertTrue(jet.isAlive() && carry(p) != null, "still standing");
			GreenLanternConstructs.dismissAll(p.getUUID());
		});
	}
}
