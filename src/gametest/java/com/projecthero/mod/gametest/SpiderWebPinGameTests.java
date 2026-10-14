package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderWebPin;
import com.projecthero.mod.spider.SpiderWebReserve;
import com.projecthero.mod.spider.SpiderWrap;
import com.projecthero.mod.spider.web.WebStructureEntity;
import com.projecthero.mod.spider.web.WebStructures;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 (user) Web Pin -- Spider-Man's Shift+V: the first press marks a target, the second picks a surface (a wall,
 * floor, ceiling, or one of his webs) and the target is reeled there and cocooned for 25 s. Also: the mark runs out,
 * another creature re-marks, bosses are refused, and the Black Suit keeps Ink Shroud on Shift+V.
 */
public class SpiderWebPinGameTests implements FabricGameTest {

	private static ServerPlayer spider(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY));
		SpiderMan.evolveFromAdhesion(p);
		p.removeAllEffects();
		SpiderMan.setWebReserve(p, 200.0f);
		return p;
	}

	private static void floor(GameTestHelper helper) {
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(7, 0, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		zombie.moveTo(at.x, at.y, at.z, 0f, 0f);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}

	private static ServerPlayer standingAt(GameTestHelper helper, double x, double z) {
		ServerPlayer p = spider(helper);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static void shiftV(ServerPlayer p) {
		p.setShiftKeyDown(true);
		SpiderMoves.handle(p, AbilitySlot.SLOT_5, true);
		SpiderMoves.handle(p, AbilitySlot.SLOT_5, false);
		p.setShiftKeyDown(false);
	}

	private static void aim(ServerPlayer p, Vec3 at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at);
	}

	/** Run the reel to its end (the server may be ticking it too -- either way it finishes). */
	private static void finishReel(ServerPlayer p) {
		for (int i = 0; i < 60 && SpiderWebPin.reeling(p); i++) {
			SpiderWebPin.tick(p);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void markThenPinToAWallCocoonsItThereFor25Seconds(GameTestHelper helper) {
		floor(helper);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(7, 1, 0)), helper.absolutePos(new BlockPos(7, 5, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		ServerPlayer p = standingAt(helper, 1.5, 1.5);
		Zombie zombie = zombie(helper, 2.5, 5.5);
		aim(p, zombie.position().add(0, 1.0, 0));
		float before = SpiderWebReserve.get(p);
		shiftV(p);
		helper.assertTrue(SpiderWebPin.marked(p) == zombie, "the first Shift+V marks the zombie");
		helper.assertTrue(SpiderMoves.state(p).pinTarget == zombie.getId(), "the mark is synced for the HUD / outline");
		helper.assertFalse(SpiderMan.abilityCooldownRemaining(p, SpiderMoves.WEB_PIN) > 0, "marking costs nothing");
		Vec3 wallSpot = helper.absoluteVec(new Vec3(7.0, 2.6, 3.5));
		aim(p, wallSpot);
		shiftV(p);
		helper.assertTrue(SpiderWebPin.reelSurface(p) == SpiderWebPin.Surface.WALL, "the second Shift+V on the wall reels it in");
		helper.assertTrue(SpiderMan.abilityCooldownRemaining(p, SpiderMoves.WEB_PIN) > 0, "the pin starts the 3 s cooldown");
		helper.assertTrue(Math.abs(before - SpiderWebReserve.get(p) - SpiderMoves.cfg(SpiderMoves.WEB_PIN).cost) < 0.5f,
				"and spends Web Cocoon's webbing");
		helper.runAfterDelay(30, () -> {
			finishReel(p);
			helper.assertTrue(SpiderWrap.isWrapped(zombie), "pinned and cocooned");
			long until = zombie.getAttachedOrElse(ModAttachments.SPIDER_WRAP_UNTIL, 0L);
			long left = until - helper.getLevel().getGameTime();
			helper.assertTrue(left > 500 - 45 && left <= 500, "cocooned for 25 s (" + left + " ticks left)");
			double wallX = helper.absoluteVec(new Vec3(7.0, 0, 0)).x;
			double gap = wallX - zombie.getBoundingBox().maxX;
			helper.assertTrue(gap >= -0.01 && gap < 0.25, "its back against the wall (gap " + gap + ")");
			helper.assertTrue(zombie.getY() > helper.absoluteVec(new Vec3(0, 1.0, 0)).y + 0.2, "held up on the wall, feet off the floor");
			helper.assertTrue(Math.abs(zombie.getZ() - wallSpot.z) < 0.6, "where he aimed");
			SpiderWrap.release(zombie, false);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void pinToATightropeHangsItOnTheStrand(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = standingAt(helper, 4.0, 7.0);
		Vec3 a = helper.absoluteVec(new Vec3(0.5, 4.5, 1.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 4.5, 1.5));
		WebStructureEntity rope = SpiderMoves.buildTightrope(p, a, b);
		Zombie zombie = zombie(helper, 5.5, 4.5);
		aim(p, zombie.position().add(0, 1.0, 0));
		shiftV(p);
		helper.assertTrue(SpiderWebPin.marked(p) == zombie, "marked");
		aim(p, a.lerp(b, 0.45));
		shiftV(p);
		helper.assertTrue(SpiderWebPin.reelSurface(p) == SpiderWebPin.Surface.WEB, "the tightrope on the aim is the surface");
		helper.runAfterDelay(30, () -> {
			finishReel(p);
			helper.assertTrue(SpiderWrap.isWrapped(zombie), "cocooned");
			helper.assertTrue(WebStructures.strandHolding(zombie) == rope, "with the strand through it");
			helper.assertFalse(rope.isRemoved(), "and the rope still stands");
			long left = zombie.getAttachedOrElse(ModAttachments.SPIDER_WRAP_UNTIL, 0L) - helper.getLevel().getGameTime();
			helper.assertTrue(left > 400 && left <= 500, "a 25 s cocoon (" + left + ")");
			SpiderWrap.release(zombie, false);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = SpiderWebPin.MARK_TICKS + 60)
	public void theMarkRunsOutAndShiftVOnNothingDropsIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = standingAt(helper, 1.5, 1.5);
		Zombie zombie = zombie(helper, 2.5, 5.5);
		aim(p, zombie.position().add(0, 1.0, 0));
		shiftV(p);
		helper.assertTrue(SpiderWebPin.marked(p) == zombie, "marked");
		// Shift+V at the open sky: nothing to pin to -- the mark is dropped, nothing spent (high up, clear of anything
		// built over the test area)
		Vec3 home = p.position();
		p.teleportTo(home.x, home.y + 120, home.z);
		p.setXRot(-89.0f);
		shiftV(p);
		p.teleportTo(home.x, home.y, home.z);
		helper.assertTrue(SpiderWebPin.reelSurface(p) == null, "nothing to pin to overhead (got " + SpiderWebPin.reelSurface(p)
				+ ", look " + p.getLookAngle() + ")");
		helper.assertTrue(SpiderWebPin.marked(p) == null, "Shift+V on nothing drops the mark");
		helper.assertFalse(SpiderMan.abilityCooldownRemaining(p, SpiderMoves.WEB_PIN) > 0, "and costs no cooldown");
		helper.assertFalse(SpiderWrap.isWrapped(zombie), "nothing was cocooned");
		aim(p, zombie.position().add(0, 1.0, 0));
		shiftV(p);
		helper.assertTrue(SpiderWebPin.marked(p) == zombie, "marked again");
		helper.runAfterDelay(SpiderWebPin.MARK_TICKS + 4, () -> {
			SpiderWebPin.tick(p);
			helper.assertTrue(SpiderWebPin.marked(p) == null, "after 6 s the mark has run out");
			helper.assertTrue(SpiderMoves.state(p).pinUntil == 0L, "and the HUD cue is gone");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aimingAtAnotherCreatureReMarksIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = standingAt(helper, 3.5, 0.5);
		Zombie first = zombie(helper, 1.5, 5.5);
		Zombie second = zombie(helper, 6.0, 5.5);
		aim(p, first.position().add(0, 1.0, 0));
		shiftV(p);
		helper.assertTrue(SpiderWebPin.marked(p) == first, "the first is marked");
		aim(p, second.position().add(0, 1.0, 0));
		shiftV(p);
		helper.assertTrue(SpiderWebPin.marked(p) == second, "Shift+V on another creature marks that one instead");
		helper.assertFalse(SpiderWebPin.reeling(p), "nothing is reeled");
		helper.assertFalse(SpiderMan.abilityCooldownRemaining(p, SpiderMoves.WEB_PIN) > 0, "and no cooldown");
		SpiderWebPin.clear(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesCannotBeMarked(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = standingAt(helper, 1.5, 1.5);
		Zombie boss = zombie(helper, 2.5, 5.5);
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		boss.setHealth(1000.0f);
		aim(p, boss.position().add(0, 1.0, 0));
		shiftV(p);
		helper.assertTrue(SpiderWebPin.marked(p) == null, "a boss is never marked");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBlackSuitKeepsInkShroudOnShiftV(GameTestHelper helper) {
		floor(helper);
		var blob = com.projecthero.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		blob.moveTo(helper.absoluteVec(new Vec3(1, 1, 1)));
		helper.getLevel().addFreshEntity(blob);
		ServerPlayer p = standingAt(helper, 3.5, 1.5);
		com.projecthero.mod.symbiote.SymbioteBonding.bondNow(p, blob);
		com.projecthero.mod.symbiote.Symbiote.state(p).active = true;
		Zombie zombie = zombie(helper, 3.5, 5.5);
		helper.assertTrue(com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.INK_SHROUD.equals(SpiderMoves.idFor(p, 4, 1)),
				"Black Suit: the V box's Shift move is Ink Shroud");
		aim(p, zombie.position().add(0, 1.0, 0));
		shiftV(p);
		helper.assertTrue(SpiderWebPin.marked(p) == null, "Black Suit: Shift+V never marks a Web Pin");
		helper.assertTrue(SpiderMan.abilityCooldownRemaining(p, com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.INK_SHROUD) > 0,
				"it fires Ink Shroud");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theVBoxShowsWebPin(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		helper.assertTrue(SpiderMoves.WEB_PIN.equals(SpiderMoves.idFor(p, 4, 1)), "Shift+V on the HUD is Web Pin");
		helper.assertTrue(SpiderMoves.maxCooldown(SpiderMoves.WEB_PIN) == 60, "a 3 s cooldown");
		helper.succeed();
	}
}
