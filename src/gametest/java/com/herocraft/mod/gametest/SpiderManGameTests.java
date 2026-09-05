package com.herocraft.mod.gametest;

import com.herocraft.mod.hero.AbilityRouter;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.spider.SpiderAbilities;
import com.herocraft.mod.spider.SpiderAnchorSearch;
import com.herocraft.mod.spider.SpiderMan;
import com.herocraft.mod.spider.SpiderManAbilityManager;
import com.herocraft.mod.spider.SpiderSense;
import com.herocraft.mod.spider.SpiderSwing;
import com.herocraft.mod.spider.SpiderWebReserve;
import com.herocraft.mod.spider.SpiderWebs;
import com.herocraft.mod.spider.data.SpiderManState;
import com.herocraft.mod.spider.item.SpiderItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Coverage for the Spider-Man Hero Class (0.6.3): the evolution from Spider Adhesion, the Web
 * Reserve, Spider Sense's dodge and arrow catching, the double jump, the artificial-anchor altitude
 * rule, and cleanup of the temporary webbing.
 *
 * <p>Everything here is server-side logic, which is the half that can actually be asserted from a
 * headless gametest server. The movement itself -- the adhesion engine's velocity and the rope
 * physics -- is simulated on the owning client, so it is exercised by the manual test plan rather
 * than from here; what <em>is</em> checked below is every rule the server enforces around it.
 */
public class SpiderManGameTests implements FabricGameTest {

	private static ServerPlayer survivalMockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Power adhesion() {
		return Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY);
	}

	// ---------------- the evolution ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void mutagenRefusesWithoutSpiderAdhesion(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ItemStack mutagen = new ItemStack(SpiderItems.ARACHNID_MUTAGEN);
		player.setItemInHand(InteractionHand.MAIN_HAND, mutagen);

		SpiderItems.ARACHNID_MUTAGEN.use(player.level(), player, InteractionHand.MAIN_HAND);

		helper.assertFalse(SpiderMan.hasPower(player),
				"a player with no arachnid adaptation must not become Spider-Man");
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
				"a failed mutation must not consume the Arachnid Mutagen");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mutagenEvolvesSpiderAdhesionAndIsConsumed(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ExperimentalPowers.grant(player, adhesion());
		helper.assertTrue(SpiderMan.hasSpiderAdhesion(player), "the prerequisite power should be owned");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SpiderItems.ARACHNID_MUTAGEN));
		SpiderItems.ARACHNID_MUTAGEN.use(player.level(), player, InteractionHand.MAIN_HAND);

		helper.assertTrue(SpiderMan.hasPower(player), "using the mutagen should grant the Hero Class");
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
				"a successful mutation consumes the Arachnid Mutagen");
		helper.assertFalse(SpiderMan.hasSpiderAdhesion(player),
				"the evolution must consume Spider Adhesion, not run alongside it");
		helper.assertTrue(ExperimentalPowers.getActive(player) == null,
				"the consumed power must not still be the active one");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void evolutionFreesTheMutationSlot(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ExperimentalPowers.grant(player, adhesion());
		int before = ExperimentalPowers.ownedCount(player);

		helper.assertTrue(SpiderMan.evolveFromAdhesion(player), "evolution should succeed");

		helper.assertTrue(ExperimentalPowers.ownedCount(player) == before - 1,
				"evolving must give the mutation slot back, not leave a ghost entry");
		helper.assertFalse(SpiderMan.evolveFromAdhesion(player),
				"a second evolution is a no-op -- there is nothing left to evolve");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void existingAdhesionSavesKeepWorking(GameTestHelper helper) {
		// The 0.6.3 upgrade path for a world that already has Spider Adhesion players: they keep the
		// power exactly as saved, its toggles still resolve, and it is still evolvable later.
		ServerPlayer player = survivalMockPlayer(helper);
		ExperimentalPowers.grant(player, adhesion());
		ExperimentalPowers.setActive(player, adhesion());
		AbilityRouter.handleInput(player, 6, true); // C -- adhesion_mode toggle

		helper.assertTrue(ExperimentalPowers.isToggled(player, adhesion(),
				adhesion().ability(com.herocraft.mod.hero.AbilitySlot.SLOT_6)),
				"Adhesion Mode should still toggle on an existing save");
		helper.assertTrue(com.herocraft.mod.spider.SpiderClimb.profile(player)
				== com.herocraft.mod.spider.SpiderClimb.Profile.ADHESION,
				"a toggled Spider Adhesion player should get the Adhesion climbing profile");
		helper.assertFalse(SpiderMan.hasPower(player), "and must not have silently become Spider-Man");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderManGetsStrongerClimbingThanAdhesion(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ExperimentalPowers.grant(player, adhesion());
		SpiderMan.evolveFromAdhesion(player);

		com.herocraft.mod.spider.SpiderClimb.Profile p = com.herocraft.mod.spider.SpiderClimb.profile(player);
		helper.assertTrue(p == com.herocraft.mod.spider.SpiderClimb.Profile.SPIDER_MAN,
				"Spider-Man should always have the full climbing profile, with no toggle needed");
		helper.assertTrue(p.climbSpeed() > com.herocraft.mod.spider.SpiderClimb.Profile.ADHESION.climbSpeed(),
				"Spider-Man should climb faster than basic Spider Adhesion");
		helper.assertTrue(p.graceTicks() > com.herocraft.mod.spider.SpiderClimb.Profile.ADHESION.graceTicks(),
				"Spider-Man should hold on around corners more stubbornly than Spider Adhesion");
		helper.succeed();
	}

	// ---------------- web reserve ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void webReserveSpendsAndCannotGoNegative(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		SpiderMan.setWebReserve(player, SpiderWebReserve.MAX);

		helper.assertTrue(SpiderWebReserve.spend(player, SpiderWebReserve.COST_WEB_YANK, true),
				"a full reserve can pay for a web ability");
		helper.assertTrue(Math.abs(SpiderMan.state(player).webReserve
				- (SpiderWebReserve.MAX - SpiderWebReserve.COST_WEB_YANK)) < 0.01f,
				"spending should subtract exactly the ability's cost");

		SpiderMan.setWebReserve(player, 2.0f);
		helper.assertFalse(SpiderWebReserve.spend(player, SpiderWebReserve.COST_WEB_NET, true),
				"an ability must not fire when the reserve cannot cover it");
		helper.assertTrue(SpiderMan.state(player).webReserve == 2.0f,
				"a refused spend must change nothing");

		SpiderWebReserve.spend(player, 2.0f, true);
		helper.assertTrue(SpiderMan.state(player).webReserve >= 0.0f, "the reserve can never go negative");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void webReserveRegeneratesUpToTheCap(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		// setWebReserve also lifts the post-ability regeneration hold, so this starts refilling at once.
		SpiderMan.setWebReserve(player, 10.0f);

		for (int i = 0; i < 200; i++) {
			SpiderWebReserve.tick(player);
		}
		helper.assertTrue(SpiderMan.state(player).webReserve > 10.0f, "the reserve should regenerate");

		SpiderMan.setWebReserve(player, SpiderWebReserve.MAX);
		for (int i = 0; i < 40; i++) {
			SpiderWebReserve.tick(player);
		}
		helper.assertTrue(SpiderMan.state(player).webReserve <= SpiderWebReserve.MAX,
				"the reserve must never exceed its maximum");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void regenerationWaitsAfterAMajorAbility(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		SpiderMan.setWebReserve(player, 50.0f);
		SpiderWebReserve.spend(player, 10.0f, true); // major -- stamps the delay
		float after = SpiderMan.state(player).webReserve;

		SpiderWebReserve.tick(player);
		helper.assertTrue(SpiderMan.state(player).webReserve == after,
				"regeneration must pause briefly after a major web ability");
		helper.succeed();
	}

	// ---------------- spider sense ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void dodgeRollIsFiftyPercent(GameTestHelper helper) {
		// Driven by a seeded generator rather than by hoping: the rule under test is "half the time",
		// and a fixed seed makes that a deterministic assertion instead of a flaky one.
		RandomSource random = RandomSource.create(20240613L);
		int dodges = 0;
		int trials = 20000;
		for (int i = 0; i < trials; i++) {
			if (SpiderSense.roll(random)) {
				dodges++;
			}
		}
		double rate = (double) dodges / trials;
		helper.assertTrue(Math.abs(rate - 0.5) < 0.02,
				"the dodge rate should sit at 50%, measured " + rate);
		helper.assertTrue(SpiderSense.DODGE_CHANCE == 0.5f, "the documented chance is 50%");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderSenseIgnoresDamageItCannotPlausiblyDodge(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		var sources = player.level().damageSources();

		helper.assertFalse(SpiderSense.eligible(sources.fellOutOfWorld()), "the void is not dodgeable");
		helper.assertFalse(SpiderSense.eligible(sources.genericKill()), "/kill is not dodgeable");
		helper.assertFalse(SpiderSense.eligible(sources.starve()), "starvation is not dodgeable");
		helper.assertFalse(SpiderSense.eligible(sources.drown()), "drowning is not dodgeable");
		helper.assertFalse(SpiderSense.eligible(sources.fall()), "a fall is not dodgeable");
		helper.assertFalse(SpiderSense.eligible(sources.inFire()), "standing in fire is not dodgeable");
		helper.assertFalse(SpiderSense.eligible(sources.cactus()), "a cactus is not an attack");

		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		DamageSource attack = sources.mobAttack(zombie);
		helper.assertTrue(SpiderSense.eligible(attack), "a mob's melee attack is exactly what this dodges");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void caughtArrowProducesExactlyOneItem(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		player.getInventory().clearContent();

		net.minecraft.world.entity.projectile.Arrow arrow =
				new net.minecraft.world.entity.projectile.Arrow(player.level(), player.getX(), player.getY(),
						player.getZ(), new ItemStack(Items.ARROW), null);
		player.level().addFreshEntity(arrow);

		// The interception path the dodge takes: the projectile is removed and one item is handed over.
		DamageSource source = player.level().damageSources().arrow(arrow, null);
		boolean allowed = simulateDamage(player, source);

		if (!allowed) {
			helper.assertFalse(arrow.isAlive(), "a dodged arrow must be removed from the world");
			helper.assertTrue(player.getInventory().countItem(Items.ARROW) == 1,
					"catching an arrow yields exactly one arrow, never two");
		} else {
			// The roll failed this time; the arrow must be untouched and nothing duplicated.
			helper.assertTrue(player.getInventory().countItem(Items.ARROW) == 0,
					"an arrow that was not dodged must not put an item in the inventory");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void caughtArrowDropsWhenTheInventoryIsFull(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		player.getInventory().clearContent();
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
		}
		int itemsBefore = player.level().getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(6)).size();

		net.minecraft.world.entity.projectile.Arrow arrow =
				new net.minecraft.world.entity.projectile.Arrow(player.level(), player.getX(), player.getY(),
						player.getZ(), new ItemStack(Items.ARROW), null);
		player.level().addFreshEntity(arrow);
		boolean allowed = simulateDamage(player, player.level().damageSources().arrow(arrow, null));

		if (!allowed) {
			int itemsAfter = player.level().getEntitiesOfClass(
					net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(6)).size();
			helper.assertTrue(itemsAfter == itemsBefore + 1,
					"with a full inventory the caught arrow must be dropped, not destroyed");
		}
		helper.succeed();
	}

	// ---------------- double jump ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void doubleJumpWorksOnceThenRespectsItsCooldown(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		// airborne, well clear of the floor
		Vec3 air = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 6, 2)));
		player.setPos(air.x, air.y, air.z);
		player.setOnGround(false);

		helper.assertTrue(SpiderAbilities.doubleJump(player), "the second jump should fire while airborne");
		helper.assertFalse(SpiderAbilities.doubleJump(player),
				"a third jump must not fire -- the cooldown has just started");

		long readyAt = SpiderMan.state(player).doubleJumpReadyAt;
		long now = player.level().getGameTime();
		helper.assertTrue(readyAt - now == SpiderAbilities.CD_DOUBLE_JUMP,
				"the double-jump cooldown must be exactly 2 seconds (40 ticks), got " + (readyAt - now));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void doubleJumpRefusesOnTheGround(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		player.setOnGround(true);
		helper.assertFalse(SpiderAbilities.doubleJump(player),
				"a grounded player has an ordinary jump; the second one is for the air");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void doubleJumpRefusesWithoutThePower(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		player.setOnGround(false);
		helper.assertFalse(SpiderAbilities.doubleJump(player),
				"the server must not honour a double-jump request from a player without the power");
		helper.succeed();
	}

	// ---------------- swing altitude control ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void artificialAnchorsCannotLiftIndefinitely(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 4, 2)));
		player.setPos(start.x, start.y, start.z);

		double baseline = player.getY();
		Vec3 anchor = new Vec3(player.getX(), player.getY() + 15.0, player.getZ() + 8.0);
		Vec3 rising = new Vec3(0.4, 0.9, 0.4);

		// Well below the ceiling, an artificial swing is free to lift.
		Vec3 low = SpiderSwing.applyRope(player, anchor, 12.0, rising, 0, true, baseline);
		helper.assertTrue(low.y > 0.0, "an artificial swing should still climb early in the run");

		// Once the player is above the ceiling, lift stops -- but horizontal travel does not.
		Vec3 high = SpiderSwing.applyRope(player, anchor, 12.0, rising, 0,
				true, baseline - (SpiderSwing.AIR_SWING_CEILING + 5.0));
		helper.assertTrue(high.y <= 0.0001,
				"artificial anchors must stop adding height past the ceiling, got " + high.y);
		helper.assertTrue(Math.abs(high.x) > 0.05 || Math.abs(high.z) > 0.05,
				"the altitude limit must never stop the player travelling horizontally");

		// A real anchor is not subject to the rule at all -- that is the reward for real terrain.
		Vec3 real = SpiderSwing.applyRope(player, anchor, 12.0, rising, 0,
				false, baseline - (SpiderSwing.AIR_SWING_CEILING + 5.0));
		helper.assertTrue(real.y > 0.0, "a real block anchor may take the player as high as the terrain does");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swingSpeedIsBounded(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Vec3 anchor = player.getEyePosition().add(0, 14, 6);
		Vec3 absurd = new Vec3(40.0, 40.0, 40.0);
		Vec3 out = SpiderSwing.applyRope(player, anchor, 12.0, absurd, 0, false, player.getY());
		helper.assertTrue(out.length() <= 3.2,
				"the rope model must clamp swing speed, got " + out.length());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anchorSearchAlwaysReturnsSomethingToSwingFrom(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Vec3 open = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
		player.setPos(open.x, open.y, open.z);

		SpiderAnchorSearch.Anchor anchor = SpiderAnchorSearch.find(player);
		helper.assertTrue(anchor != null && anchor.pos() != null, "the search must always produce an anchor");
		helper.assertTrue(anchor.pos().y > player.getY(),
				"an anchor must be above the player -- you cannot swing from below yourself");
		helper.succeed();
	}

	// ---------------- swing lifecycle ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void swingChargesTheReserveAndReleasesCleanly(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		SpiderMan.setWebReserve(player, SpiderWebReserve.MAX);

		helper.assertTrue(SpiderSwing.fire(player), "firing a web should succeed with a full reserve");
		helper.assertTrue(SpiderSwing.isSwinging(player), "the line should now be attached");
		helper.assertTrue(SpiderMan.state(player).webReserve < SpiderWebReserve.MAX,
				"firing a web costs reserve");

		Vec3 momentum = new Vec3(1.2, 0.4, -0.6);
		player.setDeltaMovement(momentum);
		SpiderSwing.detach(player, false);

		helper.assertFalse(SpiderSwing.isSwinging(player), "releasing should drop the line");
		helper.assertTrue(player.getDeltaMovement().distanceToSqr(momentum) < 1.0E-6,
				"releasing a swing must preserve velocity, not reset it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swingRefusesWithAnEmptyReserve(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		SpiderMan.setWebReserve(player, 0.0f);
		helper.assertFalse(SpiderSwing.fire(player), "no webbing, no swing");
		helper.assertFalse(SpiderSwing.isSwinging(player), "and no line attached");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void transientStateIsClearedOnDeathAndDimensionChange(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		SpiderSwing.fire(player);
		helper.assertTrue(SpiderSwing.isSwinging(player), "a line should be attached to begin with");

		SpiderMan.clearTransient(player);

		SpiderManState s = SpiderMan.state(player);
		helper.assertFalse(s.swinging, "the swing must be dropped");
		helper.assertTrue(s.climbState == 0, "the surface attachment must be dropped");
		helper.assertTrue(s.hasPower, "but the power itself must survive -- it is permanent");
		helper.succeed();
	}

	// ---------------- temporary webbing ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void cocoonHoldsMobsButNotBosses(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));

		int mobTicks = SpiderWebs.cocoon(player, zombie);
		helper.assertTrue(mobTicks == SpiderWebs.COCOON_TICKS,
				"an ordinary mob is held for the full duration");
		helper.assertTrue(SpiderWebs.isCocooned(zombie), "and is marked as cocooned");
		helper.assertFalse(SpiderWebs.isBoss(zombie), "a zombie is not a boss");

		net.minecraft.world.entity.boss.wither.WitherBoss wither =
				helper.spawn(EntityType.WITHER, new BlockPos(4, 2, 4));
		helper.assertTrue(SpiderWebs.isBoss(wither), "a Wither is a boss");
		int bossTicks = SpiderWebs.cocoon(player, wither);
		helper.assertTrue(bossTicks < SpiderWebs.COCOON_TICKS,
				"a boss must never be fully disabled for the ordinary duration");
		wither.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cocoonMarkersExpireOnTheirOwn(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		SpiderWebs.cocoon(player, zombie);
		helper.assertTrue(SpiderWebs.activeCocoonCount() > 0, "there should be a live cocoon");

		// The unconditional sweep ServerStateReset runs -- the one that owns markers nothing else
		// prunes -- must clear them without anybody holding the power or standing nearby.
		SpiderWebs.pruneExpired(player.level().getGameTime() + 10_000L);
		helper.assertTrue(SpiderWebs.activeCocoonCount() == 0,
				"cocoon markers must expire on their own, or they would accumulate forever");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sessionResetDropsEveryTemporaryWeb(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		SpiderWebs.cocoon(player, zombie);
		SpiderWebs.weaveNet(player, helper.getLevel(),
				Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))), 2);

		SpiderWebs.clearSessionState();

		helper.assertTrue(SpiderWebs.activeNetCount() == 0 && SpiderWebs.activeCocoonCount() == 0,
				"a server stop must leave no web bookkeeping behind to pin the dead world");
		helper.succeed();
	}

	// ---------------- routing ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderManHoldsTheSlotsUntilAMutationIsSelected(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		helper.assertTrue(SpiderManAbilityManager.hasContext(player),
				"Spider-Man should hold the six slots by default");

		Power flight = Powers.byKey("power_03_flight");
		ExperimentalPowers.grant(player, flight);
		ExperimentalPowers.setActive(player, flight);
		helper.assertFalse(SpiderManAbilityManager.hasContext(player),
				"deliberately selecting a mutation should give it the slots");

		ExperimentalPowers.setActive(player, null);
		helper.assertTrue(SpiderManAbilityManager.hasContext(player),
				"selecting no mutation should hand the slots back to Spider-Man");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void abilityCooldownsMatchTheDocumentedValues(GameTestHelper helper) {
		helper.assertTrue(SpiderAbilities.CD_DOUBLE_JUMP == 20, "double jump keeps its 1s gate");
		helper.assertTrue(SpiderAbilities.CD_WEB_ZIP == 0, "Web Zip: no cooldown (v0.6.22)");
		helper.assertTrue(SpiderAbilities.CD_WEB_SHOT == 0, "Web Shot: no cooldown");
		helper.assertTrue(SpiderAbilities.CD_WEB_YANK == 0, "Web Yank: no cooldown (v0.6.22)");
		helper.assertTrue(SpiderAbilities.CD_WEB_NET == 0, "Web Net: no cooldown (v0.6.22)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everySlotIsMappedToADistinctAbility(GameTestHelper helper) {
		String[] ids = { SpiderAbilities.WEB_SWING, SpiderAbilities.WEB_ZIP, SpiderAbilities.WEB_SHOT,
				SpiderAbilities.WEB_YANK, SpiderAbilities.WALL_CRAWL, SpiderAbilities.WEB_NET };
		java.util.Set<Character> keys = new java.util.HashSet<>();
		for (String id : ids) {
			char key = SpiderAbilities.slotKeyOf(id);
			helper.assertTrue(key != '-', id + " must be bound to one of the six slots");
			helper.assertTrue(keys.add(key), "two web abilities are fighting over key " + key);
		}
		// The exact layout the design asks for.
		helper.assertTrue(SpiderAbilities.slotKeyOf(SpiderAbilities.WEB_SWING) == 'R', "R = Web Swing");
		helper.assertTrue(SpiderAbilities.slotKeyOf(SpiderAbilities.WEB_ZIP) == 'G', "G = Web Zip");
		helper.assertTrue(SpiderAbilities.slotKeyOf(SpiderAbilities.WEB_SHOT) == 'Z', "Z = Web Shot");
		helper.assertTrue(SpiderAbilities.slotKeyOf(SpiderAbilities.WEB_YANK) == 'X', "X = Web Yank");
		helper.assertTrue(SpiderAbilities.slotKeyOf(SpiderAbilities.WALL_CRAWL) == 'C', "C = Wall Crawl");
		helper.assertTrue(SpiderAbilities.slotKeyOf(SpiderAbilities.WEB_NET) == 'V', "V = Web Net");
		helper.succeed();
	}

	// ---------------- passives ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void passivesApplyAndAreRemovedWithThePower(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		double baseAttack = player.getAttributeValue(
				net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);

		ExperimentalPowers.grant(player, adhesion());
		SpiderMan.evolveFromAdhesion(player);
		// The evolution plays a brief Strength pulse; this test is about the permanent passive, so
		// clear it rather than measuring the celebration.
		player.removeAllEffects();
		double withPower = player.getAttributeValue(
				net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		helper.assertTrue(withPower > baseAttack + 8.5,
				"Spider-Man's melee bonus is +9 -> 10-damage unarmed (v0.9.4), got " + (withPower - baseAttack));

		SpiderMan.revoke(player);
		helper.assertTrue(Math.abs(player.getAttributeValue(
				net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - baseAttack) < 0.001,
				"losing the power must take its modifiers with it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerSuppressorStripsSpiderMan(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ExperimentalPowers.grant(player, adhesion());
		SpiderMan.evolveFromAdhesion(player);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND,
				new ItemStack(com.herocraft.mod.item.ModItems.POWER_SUPPRESSOR));

		com.herocraft.mod.item.ModItems.POWER_SUPPRESSOR.use(player.level(), player, InteractionHand.MAIN_HAND);

		helper.assertFalse(SpiderMan.hasPower(player),
				"the Power Suppressor gives up every power, Spider-Man included");
		helper.succeed();
	}

	// ---------------- helpers ----------------

	private static ServerPlayer spiderMan(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ExperimentalPowers.grant(player, adhesion());
		SpiderMan.evolveFromAdhesion(player);
		return player;
	}

	/**
	 * Push one hit through the same {@code ALLOW_DAMAGE} listeners the game uses, and report whether it
	 * was allowed through. Returns false when something -- Spider Sense, in these tests -- vetoed it.
	 */
	private static boolean simulateDamage(ServerPlayer player, DamageSource source) {
		return net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE
				.invoker().allowDamage(player, source, 4.0f);
	}
}
