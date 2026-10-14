package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.spider.SpiderAbilities;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderWrap;
import com.projecthero.mod.spider.web.WebStructureEntity;
import com.projecthero.mod.spider.web.WebStructures;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1 Spider-Man web rules: a pull tears a stuck target off its web (and the web snaps), a Web Shot cocoons a target
 * stuck on a tightrope / woven web until it is cut free, a thrown body flying through a woven web sticks to it, and the
 * upside-down hang (Z on a strand / ceiling, Z again zips back, Shift+Z lets go).
 */
public class SpiderWebPullGameTests implements FabricGameTest {

	private static ServerPlayer spider(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY));
		SpiderMan.evolveFromAdhesion(p);
		p.removeAllEffects();
		SpiderMan.setWebReserve(p, 200.0f);
		return p;
	}

	/** A tightrope 3 blocks up across the cage and a zombie spawned inside it (it gets stuck). */
	private static Zombie stuckZombie(GameTestHelper helper, ServerPlayer p, WebStructureEntity[] ropeOut) {
		Vec3 a = helper.absoluteVec(new Vec3(0.5, 3.0, 2.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 3.0, 2.5));
		ropeOut[0] = SpiderMoves.buildTightrope(p, a, b);
		Vec3 mid = a.lerp(b, 0.5);
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(mid.x, mid.y - 1.0, mid.z, 0.0f, 0.0f);
		// (AI on: a NoAI mob ignores velocity, and the pull has to move it)
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void yankingAStuckTargetTearsItOffAndSnapsTheWeb(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		WebStructureEntity[] rope = new WebStructureEntity[1];
		Zombie zombie = stuckZombie(helper, p, rope);
		Vec3 me = helper.absoluteVec(new Vec3(4.0, 1.0, 7.5));
		p.moveTo(me.x, me.y, me.z, 180.0f, 0.0f);
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(WebStructures.trappingWeb(zombie) != null, "the zombie is stuck on the tightrope first");
			double before = zombie.distanceTo(p);
			p.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position().add(0, zombie.getBbHeight() * 0.5, 0));
			SpiderAbilities.webYank(p);
			helper.assertTrue(rope[0].isRemoved(), "the strand it was stuck on snapped");
			helper.assertTrue(com.projecthero.mod.spider.SpiderCombat.hasTether(p), "and the real web line holds the target");
			helper.runAfterDelay(4, () -> {
				helper.assertTrue(zombie.distanceTo(p) < before - 1.0,
						"the target came off and moved with the pull (" + before + " -> " + zombie.distanceTo(p) + ")");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aWebShotCocoonsAStuckTargetUntilItIsCutFree(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		WebStructureEntity[] rope = new WebStructureEntity[1];
		Zombie zombie = stuckZombie(helper, p, rope);
		helper.runAfterDelay(6, () -> {
			SpiderAbilities.webShotHit(p, zombie);
			helper.assertTrue(SpiderWrap.isWrapped(zombie), "a Web Shot on a stuck target cocoons it");
			Vec3 at = zombie.position();
			helper.assertFalse(p.hurt(helper.getLevel().damageSources().mobAttack(zombie), 4.0f),
					"a cocooned mob cannot hurt anyone");
			rope[0].dissolve(false);
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(SpiderWrap.isWrapped(zombie), "the cocoon is permanent -- it outlives the web");
				helper.assertTrue(zombie.position().distanceTo(at) < 0.3, "and it hangs exactly where it was (did not fall)");
				helper.assertTrue(SpiderWrap.release(zombie, true), "cut free (sneak + right-click)");
				helper.assertFalse(SpiderWrap.isWrapped(zombie), "no longer cocooned");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aPullFreesACocoonedTarget(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		WebStructureEntity[] rope = new WebStructureEntity[1];
		Zombie zombie = stuckZombie(helper, p, rope);
		helper.runAfterDelay(6, () -> {
			SpiderAbilities.webShotHit(p, zombie);
			helper.assertTrue(SpiderWrap.isWrapped(zombie), "cocooned");
			helper.assertTrue(WebStructures.tearFree(zombie), "a deliberate pull tears it free");
			helper.assertFalse(SpiderWrap.isWrapped(zombie), "un-cocooned");
			helper.assertTrue(rope[0].isRemoved(), "and the strand snapped");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aThrownBodyFlyingThroughAWovenWebSticksToIt(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Vec3 c = helper.absoluteVec(new Vec3(4.0, 4.0, 4.0));
		SpiderMoves.openWeave(p, new Vec3(0, 0, 1));
		for (Vec3 off : new Vec3[] {new Vec3(3.0, 0, 0), new Vec3(-3.0, 0, 0), new Vec3(0, 3.0, 0), new Vec3(0, -3.0, 0)}) {
			helper.assertTrue(SpiderMoves.weaveAnchor(p, c.add(off)), "anchor placed");
		}
		WebStructureEntity web = SpiderMoves.finishWeave(p);
		helper.assertTrue(web != null, "the web is woven");
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(c.x, c.y - 1.0, c.z - 3.0, 0.0f, 0.0f);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		Vec3 from = c.subtract(0, 0, 3.0);
		Vec3 to = c.add(0, 0, 3.0);
		helper.assertTrue(WebStructures.catchOnWeave(zombie, from, to) == web, "a body thrown through the web is caught on it");
		helper.assertTrue(zombie.position().add(0, zombie.getBbHeight() * 0.5, 0).distanceTo(c) < 1.0,
				"set on the web where it crossed it");
		Vec3 v = com.projecthero.mod.spider.SpiderCombat.throwOnto(from, c);
		helper.assertTrue(v.lengthSqr() > 0.0 && v.z > 0.0, "the aim-assisted throw heads for the web");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void zHangsUpsideDownFromATightropeAndZipsBack(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Vec3 a = helper.absoluteVec(new Vec3(0.5, 4.0, 2.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 4.0, 2.5));
		SpiderMoves.buildTightrope(p, a, b);
		Vec3 mid = a.lerp(b, 0.5);
		p.teleportTo(mid.x, mid.y + 0.03, mid.z);
		helper.assertTrue(SpiderMoves.hangKey(p), "Z on his tightrope drops him into the hang");
		helper.assertTrue(SpiderMoves.hangingUpsideDown(p) && SpiderSwing.isSwinging(p), "hanging upside down on a line");
		helper.assertTrue(SpiderMoves.hangKey(p), "Z again zips him back up");
		helper.assertFalse(SpiderMoves.hangingUpsideDown(p) || SpiderSwing.isSwinging(p), "off the line, back on the rope");
		p.teleportTo(mid.x, mid.y + 0.03, mid.z);
		helper.assertTrue(SpiderMoves.hangKey(p), "hang again");
		helper.assertTrue(SpiderMoves.endUpsideHang(p), "Shift+Z lets go");
		helper.assertFalse(SpiderMoves.hangingUpsideDown(p) || SpiderSwing.isSwinging(p), "and he drops");
		helper.assertFalse(SpiderMoves.endUpsideHang(p), "Shift+Z does nothing special when not hanging");
		helper.succeed();
	}
}
