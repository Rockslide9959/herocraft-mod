package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.spider.SpiderAbilities;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.data.SpiderMoveState;
import com.projecthero.mod.spider.web.WebStructureEntity;
import com.projecthero.mod.spider.web.WebStructures;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1 Spider-Man revamp: right-click swinging, the tap / Shift / hold routing of the 18 moves (each with its own
 * cooldown), the web structures (ally-only footing, strangers stuck, the web alarm, expiry, never a block changed) and
 * Grab &amp; Hurl's block-grab switch.
 */
public class SpiderManRevampGameTests implements FabricGameTest {

	private static ServerPlayer spider(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY));
		SpiderMan.evolveFromAdhesion(p);
		p.removeAllEffects();
		SpiderMan.setWebReserve(p, 200.0f);
		return p;
	}

	/** A floor, a back wall and a still zombie 3 blocks in front; the player stands at the south end, aimed at it. */
	private static Zombie arena(GameTestHelper helper, ServerPlayer p) {
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(4, 0, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 7)), helper.absolutePos(new BlockPos(4, 4, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(at.x, at.y, at.z + 3.0, 180.0f, 0.0f);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
		return zombie;
	}

	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		SpiderMoves.handle(p, slot, true);
	}

	private static void release(ServerPlayer p, AbilitySlot slot) {
		SpiderMoves.handle(p, slot, false);
		p.setShiftKeyDown(false);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot) {
		press(p, slot, false);
		release(p, slot);
	}

	private static void shift(ServerPlayer p, AbilitySlot slot) {
		press(p, slot, true);
		release(p, slot);
	}

	private static boolean cd(ServerPlayer p, String id) {
		return SpiderMan.abilityCooldownRemaining(p, id) > 0;
	}

	// ---------------- right-click swinging ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void rightClickWithAnEmptyHandSwings(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(SpiderMoves.swingStart(p), "right-click with an empty hand fires a swing web");
		helper.assertTrue(SpiderSwing.isSwinging(p), "and he is on the line");
		SpiderMoves.swingStop(p);
		helper.assertFalse(SpiderSwing.isSwinging(p), "releasing right-click lets go");

		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		helper.assertFalse(SpiderMoves.swingStart(p), "with something in hand right-click is that item's, not a swing");
		helper.assertFalse(SpiderSwing.isSwinging(p), "so no line");
		helper.succeed();
	}

	// ---------------- the 18 moves: ids, cooldowns, routing ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMoveHasItsOwnKeyAndCooldown(GameTestHelper helper) {
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < 6; i++) {
			char key = AbilitySlot.byNumber(i + 1).defaultKey();
			for (String id : new String[] {SpiderMoves.TAP[i], SpiderMoves.SHIFT[i], SpiderMoves.HOLD[i]}) {
				helper.assertTrue(ids.add(id), "two moves share the id " + id);
				helper.assertTrue(SpiderAbilities.slotKeyOf(id) == key, id + " should be on " + key);
				helper.assertTrue(SpiderMoves.cfg(id).cooldown > 0, id + " needs its own cooldown");
			}
		}
		helper.assertTrue(ids.size() == 18, "18 moves, got " + ids.size());
		helper.assertTrue("R".equals(String.valueOf(SpiderAbilities.slotKeyOf(SpiderMoves.WEB_SHOT))), "R = Web Shot");
		helper.assertTrue(SpiderAbilities.slotKeyOf(SpiderMoves.WEAVE) == 'C', "hold C = Weave Mode");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rKeyTapShiftAndHoldRouteSeparately(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		arena(helper, p);
		helper.startSequence().thenExecute(() -> {
			tap(p, AbilitySlot.SLOT_1);
			helper.assertTrue(cd(p, SpiderMoves.WEB_SHOT), "R tap = Web Shot");
			helper.assertFalse(cd(p, SpiderMoves.WEB_BOMB) || cd(p, SpiderMoves.IMPACT_WEB), "a tap never cools the Shift / hold moves");
			shift(p, AbilitySlot.SLOT_1);
			helper.assertTrue(cd(p, SpiderMoves.WEB_BOMB), "Shift+R = Web Bomb");
			helper.assertFalse(cd(p, SpiderMoves.IMPACT_WEB), "Web Bomb has its own cooldown");
			press(p, AbilitySlot.SLOT_1, false);
		}).thenIdle(SpiderMoves.holdTicks() + 2).thenExecute(() -> {
			SpiderMoves.tick(p);
			helper.assertTrue(SpiderMoves.state(p).has(SpiderMoveState.FLAG_CHARGING_IMPACT), "holding R charges an Impact Web");
			release(p, AbilitySlot.SLOT_1);
			helper.assertTrue(cd(p, SpiderMoves.IMPACT_WEB), "and the release fires it");
		}).thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gKeyTapShiftAndHoldRouteSeparately(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Zombie zombie = arena(helper, p);
		helper.startSequence().thenExecute(() -> {
			shift(p, AbilitySlot.SLOT_2);
			helper.assertTrue(cd(p, SpiderMoves.WEB_YANK), "Shift+G = Web Yank");
			helper.assertFalse(cd(p, SpiderMoves.POINT_ZIP) || cd(p, SpiderMoves.ZIP_KICK), "Web Yank's cooldown is its own");
			p.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
			press(p, AbilitySlot.SLOT_2, false);
		}).thenIdle(SpiderMoves.holdTicks() + 2).thenExecute(() -> {
			SpiderMoves.tick(p);
			release(p, AbilitySlot.SLOT_2);
			helper.assertTrue(cd(p, SpiderMoves.ZIP_KICK), "hold G = Zip Kick");
			helper.assertFalse(cd(p, SpiderMoves.POINT_ZIP), "the Zip Kick does not cool Point Zip");
			p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 2.5, 7.0)));
			tap(p, AbilitySlot.SLOT_2);
			helper.assertTrue(cd(p, SpiderMoves.POINT_ZIP), "G tap = Point Zip at the wall");
		}).thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void xKeyTapShiftAndHoldRouteSeparately(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Zombie zombie = arena(helper, p);
		helper.startSequence().thenExecute(() -> {
			shift(p, AbilitySlot.SLOT_3);
			helper.assertTrue(cd(p, SpiderMoves.UPPERCUT), "Shift+X = Uppercut Launch");
			p.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
			tap(p, AbilitySlot.SLOT_3);
			helper.assertTrue(cd(p, SpiderMoves.WEB_STRIKE), "X tap = Web Strike");
			helper.assertFalse(cd(p, SpiderMoves.SPIDER_COUNTER), "neither cools the counter");
			press(p, AbilitySlot.SLOT_3, false);
		}).thenIdle(SpiderMoves.holdTicks() + 2).thenExecute(() -> {
			SpiderMoves.tick(p);
			helper.assertTrue(SpiderMoves.inCounterStance(p), "hold X = the Spider-Sense Counter stance");
			release(p, AbilitySlot.SLOT_3);
		}).thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zvAndCKeysRouteSeparately(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Zombie zombie = arena(helper, p);
		helper.startSequence().thenExecute(() -> {
			tap(p, AbilitySlot.SLOT_4);
			helper.assertTrue(cd(p, SpiderMoves.WEB_WHIP), "Z tap = Web Whip");
			helper.assertFalse(cd(p, SpiderMoves.GRAB_HURL) || cd(p, SpiderMoves.SWING_THROW), "Z's other moves stay ready");
			tap(p, AbilitySlot.SLOT_5);
			helper.assertTrue(cd(p, SpiderMoves.SPIDER_PULSE), "V tap = Spider-Sense Pulse");
			helper.assertTrue(SpiderMoves.inDodgeWindow(p), "with its dodge window");
			zombie.setHealth(2.0f);
			p.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
			shift(p, AbilitySlot.SLOT_5);
			helper.assertTrue(cd(p, SpiderMoves.WEB_COCOON), "Shift+V = Web Cocoon on a weakened enemy");
			// (v0.16.1: the cocoon is a thrown web ball now -- it wraps the zombie when it lands, checked below)
			shift(p, AbilitySlot.SLOT_6);
			helper.assertTrue(cd(p, SpiderMoves.WEB_WALL), "Shift+C = Web Wall");
			helper.assertFalse(cd(p, SpiderMoves.TIGHTROPE) || cd(p, SpiderMoves.WEAVE), "C's other moves stay ready");
			p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(1.5, 2.5, 7.0)));
			tap(p, AbilitySlot.SLOT_6);
			helper.assertTrue(SpiderMoves.state(p).has(SpiderMoveState.FLAG_ANCHOR_A), "C tap sets the tightrope's anchor A");
			press(p, AbilitySlot.SLOT_6, false);
		}).thenIdle(SpiderMoves.holdTicks() + 2).thenExecute(() -> {
			SpiderMoves.tick(p);
			helper.assertTrue(SpiderMoves.isWeaving(p), "hold C opens Weave Mode");
			helper.assertTrue(com.projecthero.mod.spider.SpiderWebs.isDeepCocooned(zombie), "the Web Cocoon ball reached the zombie and wrapped it");
			release(p, AbilitySlot.SLOT_6);
			helper.assertFalse(SpiderMoves.isWeaving(p), "and the release closes it");
		}).thenSucceed();
	}

	// ---------------- web structures ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void tightropeHoldsSpiderManAndHisSquadButNotAMob(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		ServerPlayer ally = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel());
		var squad = squads.create("Webheads" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, ally.getUUID());

		Vec3 a = helper.absoluteVec(new Vec3(0.5, 3.0, 2.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 3.0, 2.5));
		WebStructureEntity rope = SpiderMoves.buildTightrope(p, a, b);
		Vec3 mid = a.lerp(b, 0.5);

		for (ServerPlayer who : new ServerPlayer[] {p, ally}) {
			who.teleportTo(mid.x, mid.y + 0.03, mid.z);
			AABB below = who.getBoundingBox().expandTowards(0, -0.3, 0);
			helper.assertFalse(WebStructures.collisionShapes(who, below).isEmpty(),
					(who == p ? "Spider-Man" : "his squadmate") + " can stand on the tightrope");
			helper.assertTrue(WebStructures.trappingWeb(who) == null, "and it never traps them");
		}
		stranger.teleportTo(mid.x, mid.y + 0.03, mid.z);
		helper.assertTrue(WebStructures.collisionShapes(stranger, stranger.getBoundingBox().expandTowards(0, -0.3, 0)).isEmpty(),
				"a stranger finds no footing on it");
		stranger.teleportTo(mid.x + 30, mid.y + 10, mid.z);

		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(mid.x + 1.0, mid.y - 1.0, mid.z, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(zombie);
		p.teleportTo(a.x, a.y + 6.0, a.z);
		helper.assertTrue(WebStructures.collisionShapes(zombie, zombie.getBoundingBox()).isEmpty(), "a mob has no footing");
		double startY = zombie.getY();
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(WebStructures.trappingWeb(zombie) != null, "the mob is caught in the line");
			helper.assertTrue(Math.abs(zombie.getY() - startY) < 1.0 && zombie.position().distanceTo(new Vec3(mid.x + 1.0, startY, mid.z)) < 1.0,
					"and stuck fast -- it barely moves (moved to " + zombie.position() + ")");
			helper.assertTrue(rope.lastAlarmAt != null, "the web alarm pinged Spider-Man");
			squads.disband(squad);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void webAlarmFiresOnAStrangerButNotAnAlly(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Vec3 base = helper.absoluteVec(new Vec3(3.0, 1.0, 3.0));
		WebStructureEntity wall = SpiderMoves.buildWall(p, base, 0);
		p.teleportTo(base.x - 0.4, base.y, base.z); // touching his own wall
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(wall.lastAlarmAt == null, "his own touch never raises the alarm");
			Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
			zombie.moveTo(base.x + 0.5, base.y, base.z, 0.0f, 0.0f);
			zombie.setNoAi(true);
			helper.getLevel().addFreshEntity(zombie);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(wall.lastAlarmAt != null, "a mob touching the wall raises the web alarm");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void websExpire(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		WebStructureEntity rope = SpiderMoves.buildTightrope(p, helper.absoluteVec(new Vec3(0.5, 2.0, 0.5)),
				helper.absoluteVec(new Vec3(5.5, 2.0, 0.5)));
		long now = helper.getLevel().getGameTime();
		rope.setLifetime(now, now + 5);
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(rope.isRemoved(), "a web fades when its time is up");
			helper.assertTrue(WebStructures.ownedBy(p.getUUID()).isEmpty(), "and leaves nothing tracked");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void wovenWebNeverChangesABlock(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		BlockPos lo = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos hi = helper.absolutePos(new BlockPos(7, 7, 7));
		for (BlockPos pos : BlockPos.betweenClosed(lo, hi.atY(lo.getY()))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		List<BlockState> before = snapshot(helper, lo, hi);
		Vec3 c = helper.absoluteVec(new Vec3(4.0, 4.0, 4.0));
		SpiderMoves.openWeave(p, new Vec3(0, 1, 0));
		for (Vec3 off : new Vec3[] {new Vec3(3.5, 0, 0), new Vec3(-3.5, 0, 0.5), new Vec3(0, 0, 3.5), new Vec3(0.5, 0, -3.5),
				new Vec3(2.5, 0, 2.5)}) {
			helper.assertTrue(SpiderMoves.weaveAnchor(p, c.add(off)), "an anchor is placed");
		}
		WebStructureEntity web = SpiderMoves.finishWeave(p);
		helper.assertTrue(web != null && web.geometry().segmentCount() > 5, "the web is finished with its spiral");
		helper.assertTrue(snapshot(helper, lo, hi).equals(before), "weaving placed or removed no block");
		long now = helper.getLevel().getGameTime();
		web.setLifetime(now, now + 4);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(web.isRemoved(), "the web faded");
			helper.assertTrue(snapshot(helper, lo, hi).equals(before), "and its fading changed no block either");
			helper.succeed();
		});
	}

	private static List<BlockState> snapshot(GameTestHelper helper, BlockPos lo, BlockPos hi) {
		List<BlockState> out = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(lo, hi)) {
			out.add(helper.getLevel().getBlockState(pos));
		}
		return out;
	}

	// ---------------- the ledge perch (v0.16.2: restored, jointed limbs) ----------------
	@GameTest(template = EMPTY_STRUCTURE)
	public void perchNeedsADropOrAThinPost(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 4, 1)), helper.absolutePos(new BlockPos(5, 4, 3)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 middle = helper.absoluteVec(new Vec3(3.5, 5.0, 2.0));
		p.moveTo(middle.x, middle.y, middle.z, 0.0f, 0.0f); // facing +Z, the ledge 1.5 blocks ahead
		helper.assertFalse(SpiderMoves.perchSpot(p), "no perch in the middle of a floor");
		Vec3 edge = helper.absoluteVec(new Vec3(3.5, 5.0, 3.6));
		p.moveTo(edge.x, edge.y, edge.z, 0.0f, 0.0f);
		helper.assertTrue(SpiderMoves.perchSpot(p), "at the edge, facing a 4-block drop, he perches");
		p.moveTo(edge.x, edge.y, edge.z, 180.0f, 0.0f);
		helper.assertFalse(SpiderMoves.perchSpot(p), "but not facing back over the floor");
		// v0.16.2: the middle of a 1-block pillar top is a perch too
		for (int y = 1; y <= 4; y++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(6, y, 0)), Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 pillar = helper.absoluteVec(new Vec3(6.5, 5.0, 0.5));
		p.moveTo(pillar.x, pillar.y, pillar.z, 0.0f, 0.0f);
		helper.assertTrue(SpiderMoves.perchSpot(p), "in the middle of a 1-block pillar top he perches");
		BlockPos post = helper.absolutePos(new BlockPos(7, 1, 6));
		helper.getLevel().setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), 2);
		p.moveTo(post.getX() + 0.5, post.getY() + 1.5, post.getZ() + 0.5, 90.0f, 0.0f);
		helper.assertTrue(SpiderMoves.perchSpot(p), "on top of a fence post he perches whichever way he faces");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void perchNeedsSneakingStillOnTheGround(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		BlockPos post = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.getLevel().setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), 2);
		p.moveTo(post.getX() + 0.5, post.getY() + 1.5, post.getZ() + 0.5, 0.0f, 0.0f);
		p.setOnGround(true);
		p.setShiftKeyDown(false);
		helper.assertFalse(SpiderMoves.perchAllowed(p, false, 0.0), "standing up on the post: no perch");
		p.setShiftKeyDown(true);
		helper.assertTrue(SpiderMoves.perchAllowed(p, false, 0.0), "sneaking still on the post: the perch");
		helper.assertFalse(SpiderMoves.perchAllowed(p, false, 0.1), "sneak-walking onto it does not start one");
		helper.assertTrue(SpiderMoves.perchAllowed(p, true, 0.1), "but a small shuffle once perched keeps it");
		helper.assertFalse(SpiderMoves.perchAllowed(p, true, 0.3), "and walking off ends it");
		p.setOnGround(false);
		helper.assertFalse(SpiderMoves.perchAllowed(p, true, 0.0), "in the air: no perch");
		helper.succeed();
	}

	// ---------------- Grab & Hurl ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void grabAndHurlRespectsTheBlockGrabSwitch(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 4));
		helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(stone));
		boolean was = SpiderManConfig.webs().blockGrabbing;
		try {
			SpiderManConfig.webs().blockGrabbing = false;
			helper.assertFalse(SpiderMoves.canGrabBlock(p, stone), "with block grabbing off no block may be grabbed");
			SpiderMoves.grabHurl(p);
			helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "so the block stays put");
			helper.assertFalse(cd(p, SpiderMoves.GRAB_HURL), "and nothing is spent");
			SpiderManConfig.webs().blockGrabbing = true;
			if (com.projecthero.mod.hero.HeroConfig.get().abilityTerrainDamage) {
				helper.assertTrue(SpiderMoves.canGrabBlock(p, stone), "switched on, plain stone can be grabbed");
			}
			helper.assertFalse(SpiderMoves.canGrabBlock(p, stone.below(10)), "air never can");
		} finally {
			SpiderManConfig.webs().blockGrabbing = was;
		}
		helper.succeed();
	}



	// ---------------- N wall-crawl toggle, rope networks, zip-to-rope ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void nToggleStopsAutoAttach(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		var local = p.getAttachedOrCreate(com.projecthero.mod.attachment.ModAttachments.SPIDER_CLIMB_LOCAL);
		helper.assertFalse(com.projecthero.mod.spider.SpiderClimb.crawlOff(p), "wall crawl starts ON");
		com.projecthero.mod.spider.SpiderClimb.requestGrab(p);
		helper.assertTrue(local.grabIntent(), "ON: running into a wall arms the grab");
		helper.assertFalse(SpiderMoves.toggleWallCrawl(p), "N switches it OFF");
		helper.assertTrue(com.projecthero.mod.spider.SpiderClimb.crawlOff(p), "and it stays off");
		local.setGrabIntent(false);
		com.projecthero.mod.spider.SpiderClimb.requestGrab(p);
		helper.assertFalse(local.grabIntent(), "OFF: nothing arms a grab");
		p.setOnGround(false);
		local.setGrabIntent(true);
		helper.assertTrue(com.projecthero.mod.spider.SpiderClimb.updateAttachment(p, com.projecthero.mod.spider.SpiderClimb.Profile.SPIDER_MAN, local) == null,
				"OFF: the climb engine never sticks him");
		helper.assertTrue(SpiderMoves.toggleWallCrawl(p), "N again switches it back ON");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aTightropeCanAnchorOnAnotherRope(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 7)), helper.absolutePos(new BlockPos(7, 5, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 a = helper.absoluteVec(new Vec3(0.5, 3.5, 2.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 3.5, 2.5));
		WebStructureEntity first = SpiderMoves.buildTightrope(p, a, b);
		Vec3 at = helper.absoluteVec(new Vec3(4.0, 1.0, 0.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Vec3 onRope = a.lerp(b, 0.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, onRope);
		SpiderMoves.tightrope(p);
		helper.assertTrue(SpiderMoves.state(p).has(SpiderMoveState.FLAG_ANCHOR_A), "anchor A set on the rope");
		SpiderMoveState st = SpiderMoves.state(p);
		Vec3 anchorA = new Vec3(st.ax, st.ay, st.az);
		helper.assertTrue(com.projecthero.mod.spider.web.WebGeometry.closestOnSegment(a, b, anchorA).distanceTo(anchorA) < 0.05,
				"anchor A sits exactly on the strand (" + anchorA + ")");
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.0, 3.0, 7.0)));
		SpiderMoves.tightrope(p);
		WebStructureEntity second = null;
		for (WebStructureEntity w : WebStructures.ownedBy(p.getUUID())) {
			if (w != first) {
				second = w;
			}
		}
		helper.assertTrue(second != null, "the second rope was spun");
		helper.assertTrue(second.parents.contains(first.getUUID()), "and it hangs from the first (a junction)");
		first.dissolve(true);
		helper.assertTrue(second.isRemoved(), "cutting the parent rope takes the ropes anchored on it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void pointZipOntoARopeLandsStandingOnIt(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Vec3 a = helper.absoluteVec(new Vec3(0.5, 5.0, 4.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 5.0, 4.5));
		SpiderMoves.buildTightrope(p, a, b);
		Vec3 at = helper.absoluteVec(new Vec3(4.0, 1.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Vec3 target = a.lerp(b, 0.55);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, target); // from below
		com.projecthero.mod.spider.SpiderAbilities.webZip(p);
		helper.assertTrue(cd(p, SpiderMoves.POINT_ZIP), "G tap aimed at the rope is a Point Zip");
		// the haul runs on the server tick; a mock player never moves by itself, so the haul stalls and he is set down
		for (int i = 0; i < 10; i++) {
			com.projecthero.mod.spider.SpiderCombat.tick(p);
		}
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(Math.abs(p.getY() - (a.y + 0.03)) < 0.1, "he lands with his feet on the rope (y " + p.getY() + ")");
			helper.assertTrue(WebStructures.ropeUnderfoot(p) != null, "standing on top of it");
			double yaw = net.minecraft.util.Mth.wrapDegrees(p.getYRot());
			helper.assertTrue(Math.abs(Math.abs(yaw) - 90.0) < 5.0, "facing along the rope (yaw " + yaw + ")");
			helper.succeed();
		});
	}

	// ---------------- real cobwebs, Shift+N mute ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void webBombCobwebsAreTemporaryAndOnlyFillAir(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		if (!com.projecthero.mod.spider.SpiderCobwebs.allowed(helper.getLevel())
				|| !helper.getLevel().mayInteract(p, helper.absolutePos(new BlockPos(3, 3, 3)))) {
			helper.succeed(); // terrain damage / mobGriefing switched off on this server: nothing may be placed
			return;
		}
		BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
		int before = com.projecthero.mod.spider.SpiderCobwebs.trackedCount(helper.getLevel().getServer());
		int was = SpiderManConfig.webs().cobwebTicks;
		SpiderManConfig.webs().cobwebTicks = 20;
		try {
			int n = com.projecthero.mod.spider.SpiderCobwebs.fillAround(p, Vec3.atCenterOf(stone), 1.8, 10);
			helper.assertTrue(n > 0 && n <= 10, "cobwebs spun into the air round the splash (" + n + ")");
			helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "a solid block is never replaced");
			helper.assertTrue(com.projecthero.mod.spider.SpiderCobwebs.trackedCount(helper.getLevel().getServer()) >= before + n,
					"every one is in the ledger");
		} finally {
			SpiderManConfig.webs().cobwebTicks = was;
		}
		helper.runAfterDelay(40, () -> {
			int left = 0;
			for (BlockPos pos : BlockPos.betweenClosed(stone.offset(-2, -2, -2), stone.offset(2, 2, 2))) {
				if (helper.getLevel().getBlockState(pos).is(Blocks.COBWEB)) {
					left++;
				}
			}
			helper.assertTrue(left == 0, "every cobweb is taken away again when its time is up (" + left + " left)");
			helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "and the stone is untouched");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftNMutesHisNotificationSounds(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		helper.assertFalse(SpiderMoves.soundsOff(p), "sounds start ON");
		helper.assertFalse(SpiderMoves.toggleSounds(p), "Shift+N mutes them");
		helper.assertTrue(SpiderMoves.soundsOff(p), "and they stay muted");
		helper.assertTrue(SpiderMoves.toggleSounds(p), "Shift+N again turns them back on");
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(SpiderMoves.toggleSounds(plain), "only a Spider-Man has the switch");
		helper.assertFalse(SpiderMoves.soundsOff(plain), "so nothing changes for anyone else");
		helper.succeed();
	}

	/** Runs the server's per-tick swing upkeep {@code ticks} times (advancing tickCount so the periodic checks run). */
	private static void upkeep(ServerPlayer p, int ticks) {
		for (int i = 0; i < ticks; i++) {
			p.tickCount++;
			SpiderSwing.serverTick(p);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aSwingFiredFromTheGroundIsHeld(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		arena(helper, p);
		helper.assertTrue(SpiderSwing.fire(p), "the line goes out from the ground");
		upkeep(p, 30);
		helper.assertTrue(SpiderSwing.isSwinging(p), "and is still held a second and a half later (nothing on the ground lets it go)");
		SpiderSwing.detach(p, false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aSwingFiredHighAboveAFabricatedRunIsNotCutAtOnce(GameTestHelper helper) {
		// the bug: a fabricated line fired while already above the running sequence's ceiling was cut by the altitude
		// backstop on the very next tick -- "it shoots a web and immediately releases"
		ServerPlayer p = spider(helper);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 6.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, -10.0f);
		var s = SpiderMan.state(p).copy();
		s.airSwingBaselineY = p.getY() - (SpiderSwing.AIR_SWING_CEILING + 14.0);
		s.lastWebUseTick = helper.getLevel().getGameTime(); // a fabricated sequence is running (he just let go)
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.SPIDER_MAN_STATE, s);
		helper.assertTrue(SpiderSwing.fire(p), "the line goes out");
		upkeep(p, 20);
		helper.assertTrue(SpiderSwing.isSwinging(p), "and is held, not cut on the next tick");
		SpiderSwing.detach(p, false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aRefusedSwingShowsNoLine(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		arena(helper, p);
		SpiderMan.setWebReserve(p, 0.0f);
		helper.assertFalse(SpiderSwing.fire(p), "no webbing: the server refuses the line");
		helper.assertFalse(SpiderSwing.isSwinging(p), "and nothing is attached -- no line to show and drop");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void webBombIsALobbedGlobThatBurstsWhereItLands(GameTestHelper helper) {
		// the lob lands on its target point (gravity taken off before each move, as the entity does)
		Vec3 from = new Vec3(0, 2, 0);
		Vec3 to = new Vec3(9, 0, 17);
		Vec3 v = com.projecthero.mod.spider.entity.SpiderWebBombEntity.launchVelocity(from, to);
		Vec3 pos = from;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < 60; i++) {
			v = v.add(0, -com.projecthero.mod.spider.entity.SpiderWebBombEntity.GRAVITY, 0);
			pos = pos.add(v);
			best = Math.min(best, pos.distanceTo(to));
		}
		helper.assertTrue(best < 0.05, "the arc comes down on the aimed point (closest " + best + ")");
		// Shift+R throws the glob (no splash yet); the burst roots what it lands on
		ServerPlayer p = spider(helper);
		Zombie zombie = arena(helper, p);
		int before = helper.getLevel().getEntitiesOfClass(com.projecthero.mod.spider.entity.SpiderWebBombEntity.class,
				p.getBoundingBox().inflate(8)).size();
		SpiderMoves.webBomb(p);
		int after = helper.getLevel().getEntitiesOfClass(com.projecthero.mod.spider.entity.SpiderWebBombEntity.class,
				p.getBoundingBox().inflate(8)).size();
		helper.assertTrue(after == before + 1, "Web Bomb launches one glob");
		helper.assertTrue(SpiderMoves.webBombBurst(p, zombie.position().add(0, 1, 0)) >= 1, "its burst catches the zombie");
		for (var e : helper.getLevel().getEntitiesOfClass(com.projecthero.mod.spider.entity.SpiderWebBombEntity.class,
				p.getBoundingBox().inflate(8))) {
			e.discard();
		}
		helper.succeed();
	}



	@GameTest(template = EMPTY_STRUCTURE)
	public void aWovenWebIsCentredOnItsAnchorsAndFlat(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Vec3 c = helper.absoluteVec(new Vec3(4.0, 5.0, 4.0));
		// clicked in a scrambled order round a flat (horizontal) gap
		Vec3[] offs = {new Vec3(4, 0, 0.3), new Vec3(-3, 0, -3), new Vec3(0.4, 0, 4), new Vec3(-4, 0, 0.6), new Vec3(3, 0, -3.2),
				new Vec3(-2.6, 0, 3)};
		List<Vec3> anchors = new ArrayList<>();
		SpiderMoves.openWeave(p, new Vec3(0, 0.3, 1));
		for (Vec3 o : offs) {
			anchors.add(c.add(o));
			helper.assertTrue(SpiderMoves.weaveAnchor(p, c.add(o)), "anchor placed");
		}
		Vec3 centre = SpiderMoves.weaveCentroid(anchors);
		Vec3 mean = Vec3.ZERO;
		for (Vec3 a : anchors) {
			mean = mean.add(a.scale(1.0 / anchors.size()));
		}
		helper.assertTrue(centre.distanceTo(mean) < 1.0e-6, "the centre is the average of the anchors");
		Vec3 n = SpiderMoves.weaveNormal(anchors, new Vec3(0, 0.3, 1));
		helper.assertTrue(Math.abs(n.y) > 0.99, "the web lies in the anchors' (flat) plane, got normal " + n);
		List<Vec3> ordered = SpiderMoves.orderAroundCentre(anchors, centre, n);
		double last = -Double.MAX_VALUE;
		Vec3 u = n.cross(new Vec3(1, 0, 0)).normalize();
		Vec3 v = n.cross(u).normalize();
		for (Vec3 a : ordered) {
			double ang = Math.atan2(a.subtract(centre).dot(v), a.subtract(centre).dot(u));
			helper.assertTrue(ang >= last, "the radials run in order round the centre");
			last = ang;
		}
		WebStructureEntity web = SpiderMoves.finishWeave(p);
		helper.assertTrue(web != null && web.geometry().segmentCount() > anchors.size(), "the web is finished with its spiral");
		helper.assertTrue(cd(p, SpiderMoves.WEAVE), "and Weave Mode cools down");
		web.dissolve(false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aWeaveWithFewerThanThreeAnchorsIsCancelledAndRefunded(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Vec3 c = helper.absoluteVec(new Vec3(4.0, 5.0, 4.0));
		float before = com.projecthero.mod.spider.SpiderWebReserve.get(p);
		SpiderMoves.openWeave(p, new Vec3(0, 1, 0));
		helper.assertTrue(SpiderMoves.weaveAnchor(p, c.add(4, 0, 0)) && SpiderMoves.weaveAnchor(p, c.add(-4, 0, 0)), "two anchors");
		helper.assertTrue(com.projecthero.mod.spider.SpiderWebReserve.get(p) < before, "they cost webbing");
		helper.assertTrue(SpiderMoves.finishWeave(p) == null, "two anchors make no web");
		helper.assertTrue(Math.abs(com.projecthero.mod.spider.SpiderWebReserve.get(p) - before) < 0.01f, "the webbing is refunded");
		helper.assertFalse(cd(p, SpiderMoves.WEAVE), "and there is no cooldown");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(WebStructureEntity.class, new AABB(c, c).inflate(8)).stream()
				.noneMatch(e -> !e.isRemoved()), "the preview is taken down");
		helper.succeed();
	}

	/** A snowball thrown by {@code shooter}, mid-flight 2 blocks in front of {@code p} and heading at him. */
	private static net.minecraft.world.entity.projectile.Snowball incoming(GameTestHelper helper, ServerPlayer p, Zombie shooter) {
		net.minecraft.world.entity.projectile.Snowball ball = new net.minecraft.world.entity.projectile.Snowball(helper.getLevel(), shooter);
		ball.setPos(p.getX(), p.getEyeY() - 0.3, p.getZ() + 2.0);
		ball.setDeltaMovement(0.0, 0.0, -1.5);
		return ball;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderSenseAlwaysDeflectsADodgedProjectile(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Zombie shooter = arena(helper, p);
		SpiderManConfig.Moves cfg = SpiderManConfig.moves();
		float saved = cfg.deflectReturnChance;
		try {
			net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(16_1L);
			int returned = 0;
			int n = 400;
			for (int i = 0; i < n; i++) {
				var ball = incoming(helper, p, shooter);
				Vec3 before = ball.getDeltaMovement().normalize();
				com.projecthero.mod.spider.SpiderSense.Deflect d = com.projecthero.mod.spider.SpiderSense.deflect(p, ball, rand);
				helper.assertTrue(d != com.projecthero.mod.spider.SpiderSense.Deflect.NONE, "a dodged projectile is always dealt with");
				Vec3 after = ball.getDeltaMovement();
				helper.assertTrue(after.lengthSqr() > 1.0e-4 && after.normalize().dot(before) < 0.6,
						"and it no longer flies on at him (" + d + ")");
				if (d == com.projecthero.mod.spider.SpiderSense.Deflect.RETURNED) {
					returned++;
					helper.assertTrue(ball.getOwner() == p, "a returned projectile is now his");
					Vec3 toShooter = shooter.position().add(0, shooter.getBbHeight() * 0.5, 0).subtract(ball.position()).normalize();
					helper.assertTrue(after.normalize().dot(toShooter) > 0.95, "and is sent straight back at the shooter");
				}
				ball.discard();
			}
			double ratio = returned / (double) n;
			helper.assertTrue(ratio > 0.32 && ratio < 0.48, "about 40% are returned (got " + ratio + ")");

			cfg.deflectReturnChance = 0.0f;
			for (int i = 0; i < 20; i++) {
				var ball = incoming(helper, p, shooter);
				helper.assertTrue(com.projecthero.mod.spider.SpiderSense.deflect(p, ball, rand) == com.projecthero.mod.spider.SpiderSense.Deflect.SWATTED,
						"with the chance at 0 a snowball is only ever swatted aside");
				ball.discard();
			}
			cfg.deflectReturnChance = 1.0f;
			var ball = incoming(helper, p, shooter);
			helper.assertTrue(com.projecthero.mod.spider.SpiderSense.deflect(p, ball, rand) == com.projecthero.mod.spider.SpiderSense.Deflect.RETURNED,
					"with the chance at 1 it always goes back");
			ball.discard();
			// no shooter to send it back to: still never passes through
			// built without an owner (in 1.21.1 setOwner(null) leaves the old owner in place)
			var orphan = new net.minecraft.world.entity.projectile.Snowball(net.minecraft.world.entity.EntityType.SNOWBALL, helper.getLevel());
			orphan.setPos(p.getX(), p.getEyeY() - 0.3, p.getZ() + 2.0);
			orphan.setDeltaMovement(0.0, 0.0, -1.5);
			helper.assertTrue(com.projecthero.mod.spider.SpiderSense.deflect(p, orphan, rand) == com.projecthero.mod.spider.SpiderSense.Deflect.SWATTED,
					"an ownerless projectile is swatted aside");
			orphan.discard();
		} finally {
			cfg.deflectReturnChance = saved;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderSenseWarningsAreClientOnly(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Zombie zombie = arena(helper, p);
		zombie.moveTo(p.getX(), p.getY(), p.getZ() + 1.8, 180.0f, 0.0f);
		zombie.setTarget(p);
		zombie.swinging = true;
		p.tickCount = 0;
		com.projecthero.mod.spider.SpiderSense.serverTick(p);
		helper.assertTrue(com.projecthero.mod.spider.SpiderSense.scanFlagged(zombie), "the wind-up is sensed (his private threat set)");
		helper.assertFalse(zombie.hasGlowingTag(), "but no glowing tag is ever set on the mob");
		helper.assertFalse(zombie.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "nor a Glowing effect");
		helper.assertFalse(zombie.isCurrentlyGlowing(), "so no other player can see an outline");
		helper.assertFalse(p.hasGlowingTag(), "and nothing is set on him either");
		SpiderMoveState ms = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.SPIDER_MOVE_STATE, null);
		helper.assertTrue(ms != null && ms.tingleUntil > helper.getLevel().getGameTime(), "the tingle lines are on (the one synced, cosmetic part)");
		helper.succeed();
	}
}
