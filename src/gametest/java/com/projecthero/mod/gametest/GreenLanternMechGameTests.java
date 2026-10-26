package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.mech.GreenLanternMech;
import com.projecthero.mod.greenlantern.mech.GreenLanternMechConfig;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
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
 * v0.17.1: the Hard-Light Mech -- forming it (cost, the pilot seated, the build), the GL keys driving the mech's moves
 * (punch, Ground Slam), the pilot protected inside, shattering at 0 integrity, N climbing out, the refusals and the
 * cleanup. The cage is 8 blocks wide, so the mech (3.2 wide) stands in the middle.
 */
public class GreenLanternMechGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer lantern(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 1, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(4.0, 2.0, 2.0));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE * 0.5f; // no Willpower Surge either way
		GreenLantern.save(player, s);
		return player;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		h.moveTo(at.x, at.y, at.z, 180f, 0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		h.getAttribute(Attributes.ARMOR).setBaseValue(0);
		h.setHealth(200);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	private static HardLightMechEntity pilotReady(GameTestHelper helper, ServerPlayer p) {
		GreenLanternMech.deploy(p);
		HardLightMechEntity m = GreenLanternMech.mechOf(p);
		helper.assertTrue(m != null, "the mech formed and the pilot sits in it");
		m.finishAssembly();
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMechIsInTheConstructMenu(GameTestHelper helper) {
		GreenLanternBinding.Entry e = GreenLanternBinding.forConstruct(ConstructType.HARD_LIGHT_MECH);
		helper.assertTrue(e != null && e.section() == GreenLanternBinding.Section.ATTACK, "an Attack entry");
		helper.assertTrue(e.atlas() == GreenLanternBinding.ATLAS_MECH && !e.hold(), "its own icon, a tap entry");
		helper.assertTrue(ConstructType.HARD_LIGHT_MECH.initialCost() == GreenLanternMechConfig.FORM_COST, "costs 1500");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void formingCostsChargeSeatsThePilotAndBuilds(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		float before = GreenLantern.state(p).ringCharge;
		GreenLanternConstructs.deploy(p, ConstructType.HARD_LIGHT_MECH); // through the construct path, like the menu / a key
		HardLightMechEntity m = GreenLanternMech.mechOf(p);
		helper.assertTrue(m != null && p.getVehicle() == m, "the pilot is seated");
		float spent = before - GreenLantern.state(p).ringCharge;
		helper.assertTrue(Math.abs(spent - GreenLanternMechConfig.FORM_COST) < 1f, "1500 charge, spent " + spent);
		helper.assertTrue(m.state() == HardLightMechEntity.STATE_ASSEMBLING, "it starts by assembling");
		helper.assertTrue(Math.abs(m.getMaxHealth() - GreenLanternMechConfig.BASE_INTEGRITY) < 0.5f, "300 integrity at rank 1");
		helper.succeedWhen(() -> helper.assertTrue(m.isActive(), "active after the 2 s build"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void refusedWithoutEnoughCharge(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternState s = GreenLantern.state(p).copy();
		s.ringCharge = 1000f;
		GreenLantern.save(p, s);
		GreenLanternMech.deploy(p);
		helper.assertTrue(GreenLanternMech.mechOf(p) == null && !p.isPassenger(), "no mech on 1000 charge");
		helper.assertTrue(Math.abs(GreenLantern.state(p).ringCharge - 1000f) < 0.01f, "nothing spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void rPunchesThroughTheMech(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		Husk h = husk(helper, 4.0, 6.0);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, h.getEyePosition());
		GreenLanternAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		GreenLanternAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		helper.runAfterDelay(GreenLanternMechConfig.PUNCH_IMPACT_TICKS + 3, () -> {
			float dealt = 200f - h.getHealth();
			helper.assertTrue(Math.abs(dealt - GreenLanternMechConfig.PUNCH_DAMAGE) < 0.6f, "a 20 punch, dealt " + dealt);
			helper.assertTrue(m.cooldownLeft(HardLightMechEntity.CD_PUNCH) == 0 || m.isAlive(), "the mech still stands");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void gGroundSlamsARing(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		Husk front = husk(helper, 4.0, 6.5);
		Husk side = husk(helper, 7.5, 3.6);
		GreenLanternAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		GreenLanternAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(!m.ready(HardLightMechEntity.CD_SLAM), "the slam is cooling down");
		helper.runAfterDelay(GreenLanternMechConfig.SLAM_IMPACT_TICKS + GreenLanternMechConfig.SLAM_WAVE_TICKS + 3, () -> {
			helper.assertTrue(front.getHealth() < 200f && side.getHealth() < 200f,
					"the shockwave hit both, " + front.getHealth() + " / " + side.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thePilotIsProtectedInside(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		float hp = p.getHealth();
		// not a mob attack: Player#hurt zeroes difficulty-scaled damage (mob attacks) on Peaceful -- the gametest server's
		// difficulty -- and returns before any ALLOW_DAMAGE listener runs; a non-scaling source reaches the redirect
		// mock players can come up creative (invulnerable: Player#hurt returns before ALLOW_DAMAGE), and a hit a moment ago
		// leaves either side in its hurt cooldown -- pin all of that so the redirect is what is measured
		p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		p.getAbilities().invulnerable = false;
		p.invulnerableTime = 0;
		m.invulnerableTime = 0;
		// the difficulty is global and other tests change it: pin Normal, then hit the pilot the way play does -- a mob's blow
		helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		Husk h = husk(helper, 4.0, 6.0);
		float mechHp = m.getHealth();
		p.hurt(helper.getLevel().damageSources().mobAttack(h), 10f);
		h.discard();
		helper.assertTrue(p.getHealth() >= hp, "the pilot took nothing");
		helper.assertTrue(m.getHealth() < mechHp, "the mech took the hit (" + mechHp + " -> " + m.getHealth() + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroIntegrityShattersAndDropsThePilotSafely(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		Husk h = husk(helper, 4.0, 6.0);
		m.hurt(helper.getLevel().damageSources().mobAttack(h), 5000f);
		helper.assertTrue(m.isRemoved(), "shattered");
		helper.assertTrue(!p.isPassenger() && p.isAlive() && p.getHealth() >= p.getMaxHealth() - 0.01f, "the pilot is down, unhurt");
		helper.assertTrue(!GreenLantern.abilityReady(p, GreenLanternMechConfig.COOLDOWN_ID), "a cooldown before the next mech");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void nClimbsOut(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		GreenLanternAbilityManager.clearConstructs(p); // N
		helper.assertTrue(m.state() == HardLightMechEntity.STATE_DISSOLVING, "it dissolves");
		helper.runAfterDelay(GreenLanternMechConfig.DISSOLVE_TICKS + 3, () -> {
			helper.assertTrue(m.isRemoved() && !p.isPassenger(), "gone, the pilot set down");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cleanupRemovesTheMech(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		GreenLantern.clearTransient(p); // death / logout / dimension change
		helper.assertTrue(m.isRemoved() && !p.isPassenger(), "removed with the pilot's transient state");
		GreenLanternMech.clearSessionState();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void cFiresTheMissilePods(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		HardLightMechEntity m = pilotReady(helper, p);
		float before = GreenLantern.state(p).ringCharge;
		GreenLanternAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		GreenLanternAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(!m.ready(HardLightMechEntity.CD_MISSILES), "the pods are reloading");
		helper.assertTrue(before - GreenLantern.state(p).ringCharge >= GreenLanternMechConfig.MISSILE_COST - 0.5f, "120 charge");
		helper.runAfterDelay(8, () -> {
			long missiles = helper.getLevel().getEntitiesOfClass(com.projecthero.mod.greenlantern.entity.HardLightConstructEntity.class,
					m.getBoundingBox().inflate(16), e -> e.shape() == com.projecthero.mod.greenlantern.entity.HardLightConstructEntity.Shape.MISSILE)
					.size();
			helper.assertTrue(missiles > 0, "missiles in the air");
			helper.succeed();
		});
	}
}
