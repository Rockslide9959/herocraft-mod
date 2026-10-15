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

	/** v0.16.4: like {@link #stuckZombie} but a pig -- the GameTest world is Peaceful, which deletes a zombie at once. */
	private static net.minecraft.world.entity.animal.Pig stuckPig(GameTestHelper helper, ServerPlayer p, WebStructureEntity[] ropeOut) {
		Vec3 a = helper.absoluteVec(new Vec3(0.5, 3.0, 2.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 3.0, 2.5));
		ropeOut[0] = SpiderMoves.buildTightrope(p, a, b);
		Vec3 mid = a.lerp(b, 0.5);
		net.minecraft.world.entity.animal.Pig pig = EntityType.PIG.create(helper.getLevel());
		pig.moveTo(mid.x, mid.y - 0.5, mid.z, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(pig);
		return pig;
	}

	/** v0.16.4 (user): off the web = free -- the web's lingering slowness comes off the moment it stops touching the strand. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aStuckTargetBreaksFreeOnceOffTheWeb(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		WebStructureEntity[] rope = new WebStructureEntity[1];
		net.minecraft.world.entity.animal.Pig zombie = stuckPig(helper, p, rope);
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(WebStructures.stuck(zombie), "held by the tightrope");
			// the web's lingering slowness, as the web lays it on every second
			com.projecthero.mod.hero.power.AbilityHelpers.applyControl(zombie, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
					com.projecthero.mod.spider.SpiderWebs.debuffTicks(zombie), 2);
			// knocked clear of the strand
			zombie.teleportTo(zombie.getX(), zombie.getY() - 2.5, zombie.getZ() + 3.0);
			helper.runAfterDelay(3, () -> {
				helper.assertFalse(WebStructures.stuck(zombie), "no longer held once off the web");
				helper.assertFalse(zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "and the web slowness is gone");
				helper.succeed();
			});
		});
	}

	/** v0.16.4 (user): Shift + right-click a target stuck on his web tears it free. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void shiftRightClickFreesAStuckTarget(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		WebStructureEntity[] rope = new WebStructureEntity[1];
		net.minecraft.world.entity.animal.Pig zombie = stuckPig(helper, p, rope);
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(WebStructures.stuck(zombie), "held by the tightrope");
			p.setShiftKeyDown(true);
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(p, helper.getLevel(),
					net.minecraft.world.InteractionHand.MAIN_HAND, zombie, null);
			helper.assertTrue(WebStructures.pulledFree(zombie), "torn off the web (slipping through it for a moment)");
			helper.assertFalse(zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "and the web slowness is gone");
			helper.succeed();
		});
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
			helper.assertFalse(rope[0].isRemoved(), "v0.16.2: the strand stays up -- only the target comes off");
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
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(SpiderWrap.isWrapped(zombie), "the cocoon holds for as long as its web does");
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
			helper.assertFalse(rope[0].isRemoved(), "v0.16.2: and the strand stays up");
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

	/** v0.16.2: hold C while hanging upside down opens Weave Mode; clicks aimed at the walls place anchors; the web builds. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void weaveModeWorksWhileHangingUpsideDown(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		// a wall along the north side of the cage to weave on
		for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
				helper.absolutePos(new net.minecraft.core.BlockPos(0, 0, 0)), helper.absolutePos(new net.minecraft.core.BlockPos(7, 6, 0)))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 a = helper.absoluteVec(new Vec3(0.5, 6.0, 4.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 6.0, 4.5));
		SpiderMoves.buildTightrope(p, a, b);
		Vec3 mid = a.lerp(b, 0.5);
		p.teleportTo(mid.x, mid.y + 0.03, mid.z);
		helper.assertTrue(SpiderMoves.hangKey(p), "Z on the tightrope: hanging upside down");
		p.teleportTo(mid.x, SpiderMoves.hangBoxBottom(mid, SpiderMoves.HANG_START_LENGTH), mid.z);
		// the bug: he is usually holding Sneak (lowering) when he presses C -- that made it Shift+C (Web Wall), never Weave Mode
		p.setShiftKeyDown(true);
		SpiderMoves.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6, true);
		helper.runAfterDelay(SpiderMoves.holdTicks() + 2, () -> {
			SpiderMoves.tick(p);
			helper.assertTrue(SpiderMoves.hangingUpsideDown(p), "still hanging");
			helper.assertTrue(SpiderMoves.isWeaving(p), "hold C opens Weave Mode while hanging upside down");
			// aim at four points on the wall round a centre in front of him (north) and click each
			Vec3 c = helper.absoluteVec(new Vec3(4.0, 3.5, 1.0));
			int placed = 0;
			for (Vec3 off : new Vec3[] {new Vec3(2.5, 0, 0), new Vec3(-2.5, 0, 0), new Vec3(0, 2.0, 0), new Vec3(0, -2.0, 0)}) {
				p.lookAt(EntityAnchorArgument.Anchor.EYES, c.add(off));
				if (SpiderMoves.weaveStrand(p)) {
					placed++;
				}
			}
			helper.assertTrue(placed == 4, "every click aimed at the wall places an anchor (" + placed + "/4)");
			helper.assertTrue(SpiderMoves.state(p).strands == 4, "four anchors in the preview");
			SpiderMoves.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6, false);
			helper.assertFalse(SpiderMoves.isWeaving(p), "releasing C closes Weave Mode");
			WebStructureEntity web = null;
			for (WebStructureEntity w : WebStructures.in(helper.getLevel())) {
				if (w.geometry().kind == com.projecthero.mod.spider.web.WebGeometry.WEAVE) {
					web = w;
				}
			}
			helper.assertTrue(web != null, "the web is woven");
			helper.assertTrue(web.position().distanceTo(c) < 1.0, "centred where he aimed (" + web.position() + " vs " + c + ")");
			helper.assertTrue(SpiderMoves.hangingUpsideDown(p) && SpiderSwing.isSwinging(p), "and he is still hanging on his line");
			helper.succeed();
		});
	}

	/** v0.16.2: the hang line has no short limit -- he lowers until his head is just above the ground, never into it. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theHangLowersAllTheWayToTheGroundButNotIntoIt(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		helper.assertTrue(SpiderMoves.HANG_MAX_LENGTH >= 64.0, "a very long line is allowed (" + SpiderMoves.HANG_MAX_LENGTH + ")");
		helper.setBlock(new net.minecraft.core.BlockPos(4, 0, 4), net.minecraft.world.level.block.Blocks.STONE);
		Vec3 anchor = helper.absoluteVec(new Vec3(4.5, 7.5, 4.5)); // (inside the test cage)
		double floor = helper.absoluteVec(new Vec3(0, 1.0, 0)).y; // the top of that stone
		double len = SpiderMoves.hangLowerTo(p, anchor, SpiderMoves.HANG_MIN_LENGTH, SpiderMoves.HANG_MAX_LENGTH);
		double bottom = SpiderMoves.hangBoxBottom(anchor, len);
		helper.assertTrue(len > 4.5, "lowered all the way down (" + len + ")");
		helper.assertTrue(bottom > floor && bottom < floor + 0.4, "his head stops just above the floor (box bottom "
				+ bottom + ", floor " + floor + ")");
		// a block in the way stops him above it
		helper.setBlock(new net.minecraft.core.BlockPos(4, 2, 4), net.minecraft.world.level.block.Blocks.STONE);
		double len2 = SpiderMoves.hangLowerTo(p, anchor, SpiderMoves.HANG_MIN_LENGTH, SpiderMoves.HANG_MAX_LENGTH);
		double bottom2 = SpiderMoves.hangBoxBottom(anchor, len2);
		double blockTop = helper.absoluteVec(new Vec3(0, 3.0, 0)).y;
		helper.assertTrue(bottom2 > blockTop && bottom2 < blockTop + 0.4, "stops above a block under him (" + bottom2 + ")");
		helper.succeed();
	}

	/** v0.16.2 (user): the web a cocoon hangs on goes (faded, or the world was left) -- the cocoon breaks open too. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aWebCocoonBreaksOpenWhenItsWebIsGone(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		WebStructureEntity[] rope = new WebStructureEntity[1];
		// a pig (peaceful test worlds remove zombies, and a removed mob still reads as cocooned)
		Vec3 ra = helper.absoluteVec(new Vec3(0.5, 3.0, 2.5));
		Vec3 rb = helper.absoluteVec(new Vec3(7.5, 3.0, 2.5));
		rope[0] = SpiderMoves.buildTightrope(p, ra, rb);
		net.minecraft.world.entity.animal.Pig zombie = EntityType.PIG.create(helper.getLevel());
		Vec3 pm = ra.lerp(rb, 0.5);
		zombie.moveTo(pm.x, pm.y - 0.6, pm.z, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(zombie);
		helper.runAfterDelay(6, () -> {
			SpiderAbilities.webShotHit(p, zombie);
			helper.assertTrue(SpiderWrap.isWrapped(zombie), "cocooned on the tightrope");
			rope[0].dissolve(false);
			helper.runAfterDelay(80, () -> {
				helper.assertTrue(zombie.isAlive(), "precondition: still in the world");
				helper.assertFalse(SpiderWrap.isWrapped(zombie), "its web is gone, so it is no longer left floating");
				helper.succeed();
			});
		});
	}

	/** v0.16.2 (user): three quick Web Shots spin the same full cocoon -- for 25 s, then it breaks open. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void threeWebShotsCocoonForTwentyFiveSeconds(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		zombie.moveTo(at.x, at.y, at.z, 0f, 0f);
		helper.getLevel().addFreshEntity(zombie);
		for (int i = 0; i < 3; i++) {
			zombie.invulnerableTime = 0;
			SpiderAbilities.webShotHit(p, zombie);
		}
		helper.assertTrue(SpiderWrap.isWrapped(zombie), "three shots: the full cocoon");
		long until = zombie.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.SPIDER_WRAP_UNTIL, 0L);
		long left = until - helper.getLevel().getGameTime();
		helper.assertTrue(left > 480 && left <= 500, "for 25 seconds (" + left + " ticks left)");
		helper.assertFalse(p.hurt(helper.getLevel().damageSources().mobAttack(zombie), 4.0f), "and it cannot hurt anyone meanwhile");
		SpiderWrap.release(zombie, false);
		helper.succeed();
	}

	/** v0.16.2 (user): something caught by a Web Bomb cannot fire projectiles while it is webbed up. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aWebBombedShooterCannotFire(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		net.minecraft.world.entity.monster.Skeleton skel = EntityType.SKELETON.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		skel.moveTo(at.x, at.y, at.z, 0f, 0f);
		helper.getLevel().addFreshEntity(skel);
		com.projecthero.mod.spider.SpiderMoves.webBombBurst(p, skel.position().add(0, 1, 0));
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), skel,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
		helper.assertFalse(helper.getLevel().addFreshEntity(arrow), "a web-bombed skeleton's arrow never leaves the bow");
		com.projecthero.mod.spider.SpiderWebs.clearGunk();
		net.minecraft.world.entity.projectile.Arrow arrow2 = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), skel,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
		helper.assertTrue(helper.getLevel().addFreshEntity(arrow2), "once free of the webbing it can shoot again");
		arrow2.discard();
		helper.succeed();
	}


	/** v0.16.2 (user): a full-charge Impact Web that slams a mob into a wall pins it there for good. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fullChargeImpactWebPinsToTheWallForGood(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		for (int y = 1; y <= 3; y++) {
			for (int z = 1; z <= 5; z++) {
				helper.getLevel().setBlockAndUpdate(helper.absolutePos(new net.minecraft.core.BlockPos(6, y, z)),
						net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			}
		}
		net.minecraft.world.entity.animal.Pig zombie = EntityType.PIG.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(5.65, 1.0, 3.5));
		zombie.moveTo(at.x, at.y, at.z, 0f, 0f);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		com.projecthero.mod.spider.SpiderCombat.watchImpact(p, zombie, new Vec3(1, 0, 0), true);
		com.projecthero.mod.spider.SpiderCombat.tick(p);
		helper.assertTrue(SpiderWrap.isWrapped(zombie), "slammed into the wall: cocooned");
		helper.assertTrue(zombie.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.SPIDER_WRAP_UNTIL, 0L)
				== SpiderWrap.WALL_PINNED, "pinned to the wall -- permanent");
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(zombie.isAlive() && SpiderWrap.isWrapped(zombie), "and it stays stuck (no web needed to hold it)");
			SpiderWrap.release(zombie, false);
			helper.succeed();
		});
	}

	/** v0.16.2: a full-charge Impact Web with no wall behind the target still gives the 25 s full cocoon. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void fullChargeImpactWebWithNoWallCocoonsWhereItLands(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		net.minecraft.world.entity.animal.Pig zombie = EntityType.PIG.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
		zombie.moveTo(at.x, at.y, at.z, 0f, 0f);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		com.projecthero.mod.spider.SpiderCombat.watchImpact(p, zombie, new Vec3(1, 0, 0), true);
		helper.runAfterDelay(30, () -> {
			com.projecthero.mod.spider.SpiderCombat.tick(p);
			helper.assertTrue(SpiderWrap.isWrapped(zombie), "no wall: the full cocoon where it came down (alive=" + zombie.isAlive() + " removed=" + zombie.isRemoved() + " pos=" + zombie.position() + ")");
			helper.assertTrue(zombie.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.SPIDER_WRAP_UNTIL, 0L) > 0L,
					"a timed one (25 s), not the wall pin");
			SpiderWrap.release(zombie, false);
			helper.succeed();
		});
	}

	/** v0.16.2 (user): Web Bomb carpets the floor of its whole splash with cobwebs, out to the edge. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void webBombCarpetsTheWholeFloor(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlockAndUpdate(helper.absolutePos(new net.minecraft.core.BlockPos(x, 1, z)),
						net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			}
		}
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		SpiderMoves.webBombBurst(p, at);
		int covered = 0, columns = 0;
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				double dx = x - 4, dz = z - 4;
				if (dx * dx + dz * dz > 9.0) {
					continue; // within 3 blocks of the middle -- well inside the 6-block splash
				}
				columns++;
				if (helper.getLevel().getBlockState(helper.absolutePos(new net.minecraft.core.BlockPos(x, 2, z)))
						.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
					covered++;
				}
			}
		}
		helper.assertTrue(covered == columns, "every floor tile in the splash is webbed (" + covered + "/" + columns + ")");
		helper.succeed();
	}
}
