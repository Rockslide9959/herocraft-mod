package com.projecthero.mod.gametest;

import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.titans.TitansTogether;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** v0.17.3 Titans Together: Titan squadmates within 32 blocks get +10% damage and +20% bar refill. */
public class TitansTogetherGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static Squad squad(GameTestHelper helper, ServerPlayer a, ServerPlayer b) {
		SquadManager m = SquadManager.get(helper.getLevel().getServer());
		Squad s = m.create("titans" + Math.abs(a.getUUID().hashCode() % 100000), a.getUUID());
		m.addMember(s, b.getUUID());
		return s;
	}

	private static void cleanUp(GameTestHelper helper, Squad s, ServerPlayer... players) {
		SquadManager.get(helper.getLevel().getServer()).disband(s);
		for (ServerPlayer p : players) {
			p.discard();
		}
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void titanSquadmatesNearbyTurnItOn(GameTestHelper helper) {
		ServerPlayer star = mock(helper, 1.5, 1.5);
		ServerPlayer raven = mock(helper, 4.5, 4.5);
		helper.assertTrue(com.projecthero.mod.starfire.Starfire.grant(star), "Starfire granted");
		helper.assertTrue(com.projecthero.mod.raven.Raven.grant(raven), "Raven granted");
		helper.assertFalse(TitansTogether.compute(star), "not squadmates yet: off");
		Squad s = squad(helper, star, raven);
		helper.assertTrue(TitansTogether.compute(star) && TitansTogether.compute(raven), "Titan squadmates close by: on for both");
		Vec3 far = raven.position().add(40, 0, 0);
		raven.moveTo(far.x, far.y, far.z);
		helper.assertFalse(TitansTogether.compute(star), "more than 32 blocks apart: off");
		cleanUp(helper, s, star, raven);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aNonTitanSquadmateDoesNotCount(GameTestHelper helper) {
		ServerPlayer star = mock(helper, 1.5, 1.5);
		ServerPlayer plain = mock(helper, 3.5, 3.5);
		helper.assertTrue(com.projecthero.mod.starfire.Starfire.grant(star), "Starfire granted");
		Squad s = squad(helper, star, plain);
		helper.assertFalse(TitansTogether.compute(star), "a squadmate without a Titan power: off");
		cleanUp(helper, s, star, plain);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void activeTitansHitTenPercentHarderAndRefillFaster(GameTestHelper helper) {
		ServerPlayer star = mock(helper, 1.5, 1.5);
		helper.assertTrue(com.projecthero.mod.starfire.Starfire.grant(star), "Starfire granted");
		Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(3.5, 2.0, 3.5));
		var src = helper.getLevel().damageSources().playerAttack(star);
		helper.assertTrue(Math.abs(TitansTogether.scaleOutgoing(husk, src, 10f) - 10f) < 1e-4f, "off: no bonus");
		helper.assertTrue(TitansTogether.regenMult(star) == 1f, "off: normal refill");
		star.setAttached(TitansTogether.ACTIVE, true);
		helper.assertTrue(Math.abs(TitansTogether.scaleOutgoing(husk, src, 10f) - 11f) < 1e-4f, "on: +10%");
		helper.assertTrue(Math.abs(TitansTogether.regenMult(star) - 1.2f) < 1e-4f, "on: +20% refill");
		star.discard();
		helper.succeed();
	}
}
