package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding;
import com.projecthero.mod.greenlantern.bind.GreenLanternBindings;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.recon.GreenLanternDrone;
import com.projecthero.mod.greenlantern.recon.GreenLanternGateway;
import com.projecthero.mod.greenlantern.recon.GreenLanternReconConfig;
import com.projecthero.mod.greenlantern.recon.GreenLanternWaypoints;
import com.projecthero.mod.greenlantern.recon.HardLightDroneEntity;
import com.projecthero.mod.greenlantern.recon.HardLightGatewayEntity;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Hard-Light Drone (deploy cost, upkeep, dismiss on a second press, fetching a dropped item, being shot down)
 * and the Hard-Light Gateway with its ring waypoints (the five-waypoint limit, rename / delete, refusing another dimension,
 * damage breaking the channel with nothing spent, the bearer and a squadmate going through while a stranger can't, the
 * cooldown). Rings run at half charge (no Willpower Surge). The gateway's far end is a stone pad 20 blocks up.
 */
public class GreenLanternReconGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer lantern(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 1, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE * 0.5f;
		GreenLantern.save(player, s);
		return player;
	}

	/** A stone pad 20 blocks over the cage and a waypoint on it, facing south. */
	private static GreenLanternWaypoints.Waypoint highPad(GameTestHelper helper, ServerPlayer p) {
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 21, 2)), helper.absolutePos(new BlockPos(4, 21, 8)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 22.0, 5.5));
		return new GreenLanternWaypoints.Waypoint("Pad", GreenLanternGateway.dimensionId(helper.getLevel()), at.x, at.y, at.z, 0f);
	}

	private static void setWaypoints(ServerPlayer p, GreenLanternWaypoints.Waypoint... w) {
		p.setAttached(ModAttachments.GREEN_LANTERN_WAYPOINTS, new GreenLanternWaypoints(java.util.List.of(w)));
	}

	// ---------------------------------------------------------------- Drone

	@GameTest(template = EMPTY_STRUCTURE)
	public void droneAndGatewayAreInTheMenuAsKeyPairs(GameTestHelper helper) {
		GreenLanternBinding.Entry drone = GreenLanternBinding.forConstruct(ConstructType.HARD_LIGHT_DRONE);
		GreenLanternBinding.Entry gate = GreenLanternBinding.forConstruct(ConstructType.HARD_LIGHT_GATEWAY);
		helper.assertTrue(drone.atlas() == GreenLanternBinding.ATLAS_RING_TECH && drone.cell() == 0, "the drone's HUD icon");
		helper.assertTrue(gate.atlas() == GreenLanternBinding.ATLAS_RING_TECH && gate.cell() == 1, "the gateway's HUD icon");
		helper.assertTrue(ConstructType.HARD_LIGHT_DRONE.category() == ConstructType.Category.UTILITY, "the drone is Utility");
		helper.assertTrue(ConstructType.HARD_LIGHT_GATEWAY.category() == ConstructType.Category.MOBILITY, "the gateway is Mobility");
		helper.assertFalse(GreenLanternBinding.allowed("H", drone), "never on H");
		helper.assertFalse(GreenLanternBinding.allowed("SH", gate), "never on Shift+H");
		ServerPlayer p = lantern(helper);
		GreenLanternBindings.bind(p, "V", drone.id());
		GreenLanternState s = GreenLantern.state(p);
		helper.assertTrue(drone.equals(GreenLanternBinding.resolve(s, "V")) && drone.equals(GreenLanternBinding.resolve(s, "SV")),
				"binding the drone to V binds Shift+V too");
		GreenLanternBindings.bind(p, "H", gate.id());
		helper.assertFalse(gate.equals(GreenLanternBinding.resolve(GreenLantern.state(p), "H")), "the server refuses H as well");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void droneDeploysPaysUpkeepAndDismisses(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		float before = GreenLanternEnergy.get(p);
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_DRONE);
		HardLightDroneEntity d = GreenLanternDrone.droneOf(p.getUUID());
		helper.assertTrue(d != null && d.isAlive(), "the drone is out");
		helper.assertTrue(Math.abs(before - GreenLanternEnergy.get(p) - GreenLanternReconConfig.DRONE_COST) < 0.01f, "it cost 80");
		helper.assertTrue(d.position().distanceTo(p.getEyePosition()) < 2.0, "at the shoulder");
		float afterCost = GreenLanternEnergy.get(p);
		for (int i = 0; i < 40; i++) {
			GreenLanternDrone.tick(p);
		}
		helper.assertTrue(GreenLanternEnergy.get(p) <= afterCost - GreenLanternReconConfig.DRONE_UPKEEP_PER_SEC * 1.9f, "upkeep drains 2 a second");
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_DRONE); // the key again
		helper.assertTrue(GreenLanternDrone.droneOf(p.getUUID()) == null && d.isRemoved(), "a second press dismisses it");
		// N dismisses one too
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_DRONE);
		helper.assertTrue(GreenLanternDrone.droneOf(p.getUUID()) != null, "out again");
		GreenLanternAbilityManager.clearConstructs(p);
		helper.assertTrue(GreenLanternDrone.droneOf(p.getUUID()) == null, "N dismisses it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void droneFetchesADroppedItem(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		p.getInventory().clearContent();
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_DRONE);
		Vec3 spot = helper.absoluteVec(new Vec3(6.5, 2.1, 5.5));
		ItemEntity item = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z, new ItemStack(Items.DIAMOND, 4));
		item.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(item);
		helper.succeedWhen(() -> {
			helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 4, "the drone brought the diamonds");
			GreenLanternDrone.dismiss(p.getUUID(), false);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void droneCanBeShotDownButNotByItsOwner(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_DRONE);
		HardLightDroneEntity d = GreenLanternDrone.droneOf(p.getUUID());
		helper.assertFalse(d.hurt(helper.getLevel().damageSources().playerAttack(p), 100f), "its own Lantern can't hurt it");
		helper.assertTrue(d.hp() == GreenLanternReconConfig.DRONE_HP, "untouched");
		net.minecraft.world.entity.monster.Husk husk = net.minecraft.world.entity.EntityType.HUSK.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
		husk.moveTo(at.x, at.y, at.z, 0f, 0f);
		husk.setNoAi(true);
		helper.getLevel().addFreshEntity(husk);
		helper.assertTrue(d.hurt(helper.getLevel().damageSources().mobAttack(husk), 15f), "a mob's hit lands");
		helper.assertTrue(Math.abs(d.hp() - (GreenLanternReconConfig.DRONE_HP - 15f)) < 0.01f, "it lost 15 HP");
		d.hurt(helper.getLevel().damageSources().mobAttack(husk), 30f);
		helper.assertTrue(d.isRemoved() && GreenLanternDrone.droneOf(p.getUUID()) == null, "shot down at 0");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Waypoints

	@GameTest(template = EMPTY_STRUCTURE)
	public void waypointsHoldFiveAndCanBeRenamedAndDeleted(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		for (int i = 0; i < GreenLanternReconConfig.MAX_WAYPOINTS; i++) {
			helper.assertTrue(GreenLanternGateway.addWaypoint(p, "Place " + i), "waypoint " + i + " saved");
		}
		helper.assertFalse(GreenLanternGateway.addWaypoint(p, "One too many"), "a sixth is refused");
		GreenLanternWaypoints w = GreenLanternGateway.waypoints(p);
		helper.assertTrue(w.size() == 5 && w.full(), "the ring holds five");
		helper.assertTrue(w.get(0).dimension().equals(GreenLanternGateway.dimensionId(helper.getLevel())), "saved with its world");
		helper.assertTrue(Math.abs(w.get(0).x() - p.getX()) < 0.01 && Math.abs(w.get(0).z() - p.getZ()) < 0.01, "where the Lantern stands");
		helper.assertTrue(GreenLanternGateway.renameWaypoint(p, 2, "  Base Camp§c "), "renamed");
		helper.assertTrue(GreenLanternGateway.waypoints(p).get(2).name().equals("Base Campc"), "cleaned: trimmed, no colour codes");
		helper.assertFalse(GreenLanternGateway.renameWaypoint(p, 1, "   "), "a blank name is refused");
		helper.assertTrue(GreenLanternGateway.deleteWaypoint(p, 0), "deleted");
		helper.assertTrue(GreenLanternGateway.waypoints(p).size() == 4 && GreenLanternGateway.waypoints(p).get(0).name().equals("Place 1"),
				"the rest moved up");
		helper.assertTrue(GreenLanternGateway.addWaypoint(p, ""), "room again (an empty name gets a default)");
		helper.assertTrue(GreenLanternWaypoints.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, GreenLanternGateway.waypoints(p))
				.flatMap(j -> GreenLanternWaypoints.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, j)).result()
				.map(x -> x.equals(GreenLanternGateway.waypoints(p))).orElse(false), "they save and load");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Gateway

	@GameTest(template = EMPTY_STRUCTURE)
	public void gatewayRefusesAnotherDimension(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		setWaypoints(p, new GreenLanternWaypoints.Waypoint("Fortress", "minecraft:the_nether", p.getX() + 50, 70, p.getZ(), 0f));
		float before = GreenLanternEnergy.get(p);
		helper.assertFalse(GreenLanternGateway.start(p, 0), "refused");
		helper.assertTrue(GreenLanternGateway.channel(p.getUUID()) == null, "no channel");
		helper.assertTrue(GreenLanternEnergy.get(p) == before, "nothing spent");
		helper.assertTrue(GreenLantern.cooldownRemaining(p, GreenLanternReconConfig.GATEWAY_COOLDOWN_ID) == 0, "no cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void damageBreaksTheChannelForFree(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		setWaypoints(p, highPad(helper, p));
		float before = GreenLanternEnergy.get(p);
		helper.assertTrue(GreenLanternGateway.start(p, 0), "the channel starts");
		GreenLanternGateway.Channel ch = GreenLanternGateway.channel(p.getUUID());
		helper.assertTrue(ch != null && ch.door().state() == HardLightGatewayEntity.TRACE, "the frame is tracing itself");
		helper.runAfterDelay(10, () -> {
			GreenLanternGateway.onDamaged(p); // what the AFTER_DAMAGE hook calls (mock players shrug off real hits)
			helper.assertTrue(GreenLanternGateway.channel(p.getUUID()) == null, "a hit breaks it off");
			helper.assertTrue(ch.door().isRemoved(), "the outline scatters");
			helper.assertTrue(GreenLanternEnergy.get(p) == before, "nothing spent");
			helper.assertTrue(GreenLantern.cooldownRemaining(p, GreenLanternReconConfig.GATEWAY_COOLDOWN_ID) == 0, "no cooldown");
			helper.assertTrue(GreenLanternGateway.doors(p.getUUID()) == null, "no doors");
			helper.succeed();
		});
	}

	@SuppressWarnings("removal")
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void gatewayTakesTheBearerAndASquadmateButNotAStranger(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("gw" + p.getUUID().toString().substring(0, 8), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		GreenLanternWaypoints.Waypoint wp = highPad(helper, p);
		setWaypoints(p, wp);
		float before = GreenLanternEnergy.get(p);
		helper.assertTrue(GreenLanternGateway.start(p, 0), "the channel starts");
		helper.onEachTick(() -> GreenLanternGateway.tick(p));
		helper.runAfterDelay(GreenLanternReconConfig.GATEWAY_CHANNEL_TICKS + 4, () -> {
			GreenLanternGateway.Doors doors = GreenLanternGateway.doors(p.getUUID());
			helper.assertTrue(doors != null, "both doors stand");
			helper.assertTrue(doors.near().state() == HardLightGatewayEntity.OPEN && doors.far().state() == HardLightGatewayEntity.OPEN, "open");
			helper.assertTrue(doors.far().position().distanceTo(new Vec3(wp.x(), wp.y(), wp.z())) < 2.5, "the twin is at the waypoint");
			double dist = wp.distanceTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)).x, helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)).y,
					helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)).z);
			helper.assertTrue(Math.abs(before - GreenLanternEnergy.get(p) - GreenLanternReconConfig.gatewayCost(dist)) < 0.5f,
					"it cost 100 + 0.2 a block");
			int cd = GreenLantern.cooldownRemaining(p, GreenLanternReconConfig.GATEWAY_COOLDOWN_ID);
			helper.assertTrue(cd > GreenLanternReconConfig.GATEWAY_COOLDOWN_TICKS - 40, "the 5-minute cooldown is on");
			helper.assertFalse(GreenLanternGateway.start(p, 0), "and another is refused");
		});
		helper.runAfterDelay(GreenLanternReconConfig.GATEWAY_CHANNEL_TICKS + 4 + GreenLanternReconConfig.GATEWAY_UNFOLD_TICKS + 2, () -> {
			GreenLanternGateway.Doors doors = GreenLanternGateway.doors(p.getUUID());
			Vec3 in = doors.near().position();
			p.teleportTo(in.x, in.y, in.z);
			mate.teleportTo(in.x + 0.4, in.y, in.z);
			stranger.teleportTo(in.x - 0.4, in.y, in.z);
		});
		helper.runAfterDelay(GreenLanternReconConfig.GATEWAY_CHANNEL_TICKS + 4 + GreenLanternReconConfig.GATEWAY_UNFOLD_TICKS + 8, () -> {
			GreenLanternGateway.Doors doors = GreenLanternGateway.doors(p.getUUID());
			Vec3 far = doors.far().position();
			helper.assertTrue(p.position().distanceTo(far) < 3.0, "the bearer stepped out of the far door");
			helper.assertTrue(mate.position().distanceTo(far) < 3.0, "the squadmate followed");
			helper.assertTrue(stranger.position().distanceTo(doors.near().position()) < 1.0, "a stranger stays put");
			helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot() - 0f)) < 1f, "facing out of the twin (south)");
			squads.disband(squad);
			GreenLanternGateway.dismiss(p.getUUID());
			helper.succeed();
		});
	}
}
