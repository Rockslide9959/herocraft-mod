package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spin.Spin;
import com.projecthero.mod.spin.SpinAbilityManager;
import com.projecthero.mod.spin.SpinBalls;
import com.projecthero.mod.spin.SpinConfig;
import com.projecthero.mod.spin.SpinDamage;
import com.projecthero.mod.spin.SpinDisguise;
import com.projecthero.mod.spin.SpinItems;
import com.projecthero.mod.spin.SpinLearning;
import com.projecthero.mod.spin.SpinMarks;
import com.projecthero.mod.spin.SpinMoves;
import com.projecthero.mod.spin.SpinValkyrie;
import com.projecthero.mod.spin.data.SpinState;
import com.projecthero.mod.spin.entity.SteelBallEntity;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 the Spin (the Spin): the item and the grant, the stats, the Spin bar, every move's damage and cost, the balls going out
 * and coming back (and the moves greyed meanwhile), the Golden Rectangle's Perfect Spin, Ball Breaker needing one, Valkyrie, Healing
 * Spin, Face Spin and squad safety. Mock players are not ticked by the server, so tests over time drive {@link Spin#tick} themselves
 * (one onEachTick per test, registered at the start; every check is a runAfterDelay registered at the start too). Mob hits are
 * asserted as ratios of the expected damage.
 */
public class SpinGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer spin(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		helper.assertTrue(Spin.grant(p), "the grant works");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<SpinState> change) {
		SpinState n = Spin.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.SPIN_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		return zb;
	}

	private static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	/** Lost health as a share of what was expected (1.0 = exactly). */
	private static float ratio(float before, LivingEntity e, float expected) {
		return (before - e.getHealth()) / expected;
	}

	private static boolean near(float ratio) {
		return ratio > 0.85f && ratio < 1.15f;
	}

	/** Spin.tick for {@code p} once a game tick for the whole test (registered once, at the start). */
	private static void ticker(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> Spin.tick(p));
	}

	// ---------------------------------------------------------------- the power

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void steelBallsTeachTheSpinAsTheOnePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		helper.assertTrue(SpinLearning.item() == SpinItems.ZEPPELI_STEEL_BALLS, "the right-click hook finds ART's item by id");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SpinItems.ZEPPELI_STEEL_BALLS));
		helper.assertTrue(SpinLearning.learn(p, InteractionHand.MAIN_HAND), "right-click learns the Spin");
		helper.assertTrue(Spin.hasPower(p) && HeroTiers.holdsHero(p, Spin.KEY), "Spin is a Hero-Tier power");
		helper.assertTrue(!SpinLearning.isBalls(p.getMainHandItem()), "the loot item is spent");
		helper.assertTrue(SpinBalls.inHand(p, InteractionHand.MAIN_HAND) == 0 && SpinBalls.inHand(p, InteractionHand.OFF_HAND) == 1,
				"it became his two Steel Balls: ball 0 in the main hand, ball 1 in the off hand");
		helper.assertTrue(p.getMainHandItem().is(SpinItems.STEEL_BALL) && SpinBalls.id(p.getMainHandItem()).owner().equals(p.getUUID()),
				"a real steel_ball item, bound to him");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(!PowerGrants.missingHeroTiers(p).contains(Spin.KEY) && PowerGrants.NOT_FROM_SERUMS.contains(Spin.KEY),
				"random serums never give it");
		helper.assertTrue(Spin.spin(p) == SpinConfig.SPIN_MAX && Spin.ballsReady(p) == 2, "a full bar and both balls to start");
		p.getInventory().add(new ItemStack(SpinItems.ZEPPELI_STEEL_BALLS));
		int slot = p.getInventory().findSlotMatchingItem(new ItemStack(SpinItems.ZEPPELI_STEEL_BALLS));
		ItemStack second = p.getInventory().getItem(slot);
		ItemStack ball0 = p.getMainHandItem();
		p.setItemInHand(InteractionHand.MAIN_HAND, second);
		p.getInventory().setItem(slot, ball0);
		helper.assertTrue(!SpinLearning.learn(p, InteractionHand.MAIN_HAND) && SpinLearning.isBalls(p.getMainHandItem()), "a second set does nothing");
		Spin.revoke(p);
		helper.assertTrue(!Spin.hasPower(p), "revoked");
		boolean anyBall = false;
		for (int k = 0; k < p.getInventory().getContainerSize(); k++) {
			anyBall |= p.getInventory().getItem(k).is(SpinItems.STEEL_BALL);
		}
		helper.assertTrue(!anyBall, "revoking took both balls away");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void passiveRegenerationOne(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		p.setHealth(10f);
		helper.onEachTick(() -> Spin.tick(p));
		helper.runAfterDelay(SpinConfig.REGEN_EVERY_TICKS * 2 + 2, () -> {
			float gained = p.getHealth() - 10f;
			helper.assertTrue(gained >= 1.5f && gained <= 3.5f, "Regeneration I: about 1 health per 2.5 s, gained " + gained);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void statsAndDamageResistance(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "no extra hearts (20), got " + p.getMaxHealth());
		double speedBase = p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speedBase * 1.15) < 1.0e-4, "+15% speed");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (p.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() + 4)) < 1.0e-4,
				"+4 melee");
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 10f);
		helper.assertTrue(Math.abs(p.getHealth() - (20f - 8.5f)) < 0.05f, "15% off a hit (10 -> 8.5), health " + p.getHealth());
		Spin.revoke(p);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "still 20 without the power");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spinBarRefillsFourASecond(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		Spin.setSpin(p, 10f);
		float[] last = { -1f };
		boolean[] ok = { true };
		helper.onEachTick(() -> {
			Spin.tick(p);
			float now = Spin.spin(p);
			if (last[0] >= 0f && now < SpinConfig.SPIN_MAX && Math.abs(now - last[0] - SpinConfig.SPIN_REGEN_PER_TICK) > 1.0e-3f) {
				ok[0] = false;
			}
			last[0] = now;
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(ok[0], "every tick adds exactly 0.2 Spin (4 a second)");
			helper.assertTrue(Spin.spin(p) > 10f && Spin.spin(p) <= SpinConfig.SPIN_MAX, "and it's filling");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the balls

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void steelBallHitsSpinsAndComesBack(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		Zombie z = zombie(helper, 5.5, 1.5, 100);
		look(p, z);
		float before = z.getHealth();
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.STEEL_BALL), "R throws");
		helper.assertTrue(Math.abs(Spin.spin(p) - (100f - SpinConfig.BALL_COST)) < 0.01f, "15 Spin");
		helper.assertTrue(Spin.ballsReady(p) == 1 && (Spin.state(p).thrown & 1) != 0, "ball 0 is out");
		helper.assertTrue(!SpinAbilityManager.ballsAllow(p, SpinAbilityManager.TWIN_BALLS)
				&& !SpinAbilityManager.ballsAllow(p, SpinAbilityManager.SPIN_SHIELD), "two-ball moves are greyed while it's out");
		helper.assertTrue(p.getMainHandItem().isEmpty() && SpinBalls.inHand(p, InteractionHand.OFF_HAND) == 1, "the throw emptied the main hand");
		helper.assertTrue(!SpinAbilityManager.ballsAllow(p, SpinAbilityManager.SCAN), "one-ball moves need a ball in the main hand");
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(near(ratio(before, z, SpinConfig.BALL_DAMAGE)), "10 damage, lost " + (before - z.getHealth()));
			helper.assertTrue(SpinMarks.spun(z), "the target spins");
			z.setTarget(p);
			helper.assertTrue(z.getTarget() == null, "a spun mob can't aim (no target)");
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(Spin.ballsReady(p) == 2 && Spin.state(p).thrown == 0, "the ball came back to the hand");
			helper.assertTrue(SpinBalls.inHand(p, InteractionHand.MAIN_HAND) == 0, "into the same (main) hand");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(SteelBallEntity.class, p.getBoundingBox().inflate(40)).isEmpty(), "no ball left out");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void twinBallsTakeBothAndGreyTheRest(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.TWIN_BALLS), "Shift+R throws both");
		helper.assertTrue(Math.abs(Spin.spin(p) - (100f - SpinConfig.TWIN_COST)) < 0.01f, "30 Spin");
		helper.assertTrue(Spin.state(p).thrown == 3 && Spin.ballsReady(p) == 0, "both balls are out");
		helper.assertTrue(!SpinMoves.use(p, SpinAbilityManager.STEEL_BALL), "R is refused with no ball in hand");
		helper.assertTrue(!SpinMoves.startCharge(p, SpinAbilityManager.GOLDEN_SPIN), "and so is the Golden Spin");
		helper.assertTrue(SpinAbilityManager.ballsAllow(p, SpinAbilityManager.SPIN_LEAP), "Spin Leap needs no ball");
		helper.assertTrue(Math.abs(Spin.spin(p) - 70f) < 0.01f, "refused moves cost nothing");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the balls are items he holds

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void movesNeedTheBallsInHand(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		ItemStack b0 = p.getMainHandItem(), b1 = p.getOffhandItem();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.getInventory().setItem(9, b0); // in the backpack, not in hand
		helper.assertTrue(!SpinAbilityManager.ballsAllow(p, SpinAbilityManager.STEEL_BALL) && !SpinAbilityManager.ballsAllow(p, SpinAbilityManager.SPIN_TOUCH),
				"one-ball moves are greyed with no ball in the main hand (the off hand doesn't count)");
		helper.assertTrue(!SpinMoves.use(p, SpinAbilityManager.STEEL_BALL), "R does nothing (no ball is lost, so no recall either)");
		helper.assertTrue(!SpinMoves.use(p, SpinAbilityManager.SPIN_QUAKE) && !SpinMoves.startCharge(p, SpinAbilityManager.GOLDEN_SPIN),
				"nor C, nor Shift+G");
		helper.assertTrue(Math.abs(Spin.spin(p) - SpinConfig.SPIN_MAX) < 0.01f, "nothing paid");
		helper.assertTrue(SpinAbilityManager.ballsAllow(p, SpinAbilityManager.SPIN_LEAP) && SpinAbilityManager.ballsAllow(p, SpinAbilityManager.VALKYRIE),
				"Spin Leap and Valkyrie need no ball");
		// one ball in the main hand only: two-ball moves still refuse
		p.getInventory().setItem(9, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.MAIN_HAND, b0);
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		p.getInventory().setItem(10, b1);
		helper.assertTrue(SpinAbilityManager.ballsAllow(p, SpinAbilityManager.STEEL_BALL), "a ball in the main hand: R is fine");
		helper.assertTrue(!SpinMoves.use(p, SpinAbilityManager.TWIN_BALLS) && !SpinMoves.use(p, SpinAbilityManager.SPIN_SHIELD),
				"Twin Balls and Spin Shield need a ball in each hand");
		// someone else's ball is no use
		ServerPlayer other = mock(helper, 4.5, 4.5);
		Spin.grant(other);
		p.setItemInHand(InteractionHand.OFF_HAND, other.getOffhandItem().copy());
		helper.assertTrue(!SpinAbilityManager.ballsAllow(p, SpinAbilityManager.TWIN_BALLS), "another player's ball doesn't count");
		p.setItemInHand(InteractionHand.OFF_HAND, b1);
		p.getInventory().setItem(10, ItemStack.EMPTY);
		helper.assertTrue(SpinAbilityManager.ballsAllow(p, SpinAbilityManager.TWIN_BALLS), "his own two: Twin Balls is ready");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void twinThrowEmptiesBothHandsAndRefillsThem(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.TWIN_BALLS), "Shift+R");
		helper.assertTrue(p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty(), "both hands emptied: the balls really left");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT)); // he picks something up meanwhile
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(Spin.state(p).thrown == 0, "both came back");
			helper.assertTrue(SpinBalls.inHand(p, InteractionHand.OFF_HAND) == 1, "ball 1 went back into the (empty) off hand");
			helper.assertTrue(p.getMainHandItem().is(Items.DIRT) && SpinBalls.inInventory(p, 0), "ball 0 found the main hand full: a free slot");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void droppedBallOwnerOnlyAndRecalled(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		ServerPlayer stranger = mock(helper, 5.5, 5.5);
		ItemStack b0 = p.getMainHandItem().copy();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		Vec3 at = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
		net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), at.x, at.y, at.z, b0);
		drop.setNoPickUpDelay();
		helper.getLevel().addFreshEntity(drop);
		drop.playerTouch(stranger);
		helper.assertTrue(drop.isAlive() && !drop.getItem().isEmpty(), "nobody but the owner picks it up");
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.STEEL_BALL), "R with an empty main hand recalls it");
		helper.assertTrue(drop.isRemoved() && Spin.state(p).thrown == 1, "it lifted off the ground and is flying home");
		// the other ball "left in a chest": gone from his inventory without a trace
		ItemStack inChest = p.getOffhandItem().copy();
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(SpinBalls.inHand(p, InteractionHand.MAIN_HAND) == 0, "the dropped ball is back in his main hand");
			p.getInventory().setItem(9, p.getMainHandItem());
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.STEEL_BALL), "R again: the chest ball is recalled too");
			helper.assertTrue(SpinBalls.stale(helper.getLevel().getServer(), inChest), "the copy in the chest is no longer his ball");
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(SpinBalls.inHand(p, InteractionHand.MAIN_HAND) == 1, "the chest ball flew into his main hand");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void destroyedBallComesBack(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		ItemStack b1 = p.getOffhandItem().copy();
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), at.x, at.y, at.z, b1);
		helper.getLevel().addFreshEntity(drop);
		helper.runAfterDelay(2, () -> drop.kill()); // lava, the void, /kill: it's gone with the ball inside
		helper.runAfterDelay(5, () -> {
			long due = Spin.state(p).respawn(1);
			helper.assertTrue(due > 0L && due - helper.getLevel().getGameTime() > SpinConfig.BALL_RESPAWN_TICKS - 10,
					"a new ball is due in 30 s, due " + due);
			edit(p, s -> s.respawn1 = 1L); // skip the wait
			Spin.tick(p);
			helper.assertTrue(SpinBalls.inInventory(p, 1), "ball 1 is back in his inventory");
			helper.assertTrue(SpinBalls.stale(helper.getLevel().getServer(), b1), "and the old one would be stale");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- curving after the look
	// These need more room than the 8x8 barrier box, so they run high above it (open sky, nothing to hit but the targets),
	// with the targets floating (no AI, no gravity) and fire-proof (daylight would add burn damage).

	private static final double SKY = 80.0;

	private static ServerPlayer skySpin(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, SKY, 1.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f); // facing +z, level
		p.setNoGravity(true);
		helper.assertTrue(Spin.grant(p), "the grant works");
		return p;
	}

	private static <T extends LivingEntity> T floating(T e) {
		e.setNoGravity(true);
		if (e instanceof net.minecraft.world.entity.Mob m) {
			m.setNoAi(true);
		}
		e.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000));
		return e;
	}

	/** {@code dist} blocks from the player's spot, {@code deg} degrees to his left of straight ahead (+z). */
	private static Vec3 skyAt(double dist, double deg) {
		double r = Math.toRadians(deg);
		return new Vec3(1.5 + dist * Math.sin(r), SKY, 1.5 + dist * Math.cos(r));
	}

	private static SteelBallEntity ballOf(GameTestHelper helper, ServerPlayer p) {
		var balls = helper.getLevel().getEntitiesOfClass(SteelBallEntity.class, p.getBoundingBox().inflate(40), b -> b.getOwner() == p);
		return balls.isEmpty() ? null : balls.get(0);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ballCurvesOntoAMobHeTurnsToFace(GameTestHelper helper) {
		ServerPlayer p = skySpin(helper);
		Zombie z = floating(zombie(helper, skyAt(14, 30).x, skyAt(14, 30).z, 100));
		z.moveTo(helper.absoluteVec(skyAt(14, 30)));
		float before = z.getHealth();
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.STEEL_BALL), "R throws straight ahead, 7 blocks wide of the zombie");
		boolean[] locked = { false };
		helper.onEachTick(() -> {
			SteelBallEntity b = ballOf(helper, p);
			if (b != null && b.homing() == z) {
				locked[0] = true;
			}
		});
		helper.runAfterDelay(2, () -> look(p, z)); // the flick: 30 degrees onto the zombie
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(locked[0], "the ball homed onto the zombie under the crosshair");
			helper.assertTrue(near(ratio(before, z, SpinConfig.BALL_DAMAGE)), "it curved in and hit: lost " + (before - z.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ballWontCurveOntoAMobBehindIt(GameTestHelper helper) {
		ServerPlayer p = skySpin(helper);
		// 60 degrees off and close to him: once the ball is 18 blocks out, it would need a turn of over 160 degrees
		Zombie z = floating(zombie(helper, 1.5, 1.5, 100));
		z.moveTo(helper.absoluteVec(skyAt(5, 60)));
		float before = z.getHealth();
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.STEEL_BALL), "R throws straight ahead");
		boolean[] locked = { false };
		float[] turned = { 0f };
		helper.onEachTick(() -> {
			SteelBallEntity b = ballOf(helper, p);
			if (b != null) {
				locked[0] |= b.homing() == z;
				turned[0] = Math.max(turned[0], b.turned());
			}
		});
		helper.runAfterDelay(15, () -> look(p, z));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(!locked[0], "never homed: the curve it needs is far too sharp");
			helper.assertTrue(turned[0] <= SpinConfig.CURVE_MAX_TOTAL_DEG + 0.5f, "and it never turned more than 120, turned " + turned[0]);
			helper.assertTrue(z.getHealth() == before, "missed, lost " + (before - z.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ballNeverHomesOnSquadmatesOrTheirPets(GameTestHelper helper) {
		ServerPlayer p = skySpin(helper);
		ServerPlayer mate = mock(helper, 1.5, 1.5);
		mate.setNoGravity(true);
		mate.moveTo(helper.absoluteVec(skyAt(10, 25)));
		Wolf wolf = floating(helper.spawnWithNoFreeWill(EntityType.WOLF, new Vec3(1.5, 2.0, 1.5)));
		wolf.moveTo(helper.absoluteVec(skyAt(12, 25)));
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("SpinCurve" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		wolf.tame(mate);
		float m0 = mate.getHealth(), w0 = wolf.getHealth();
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.STEEL_BALL), "R throws");
		boolean[] bad = { false };
		helper.onEachTick(() -> {
			SteelBallEntity b = ballOf(helper, p);
			if (b != null && (b.homing() == mate || b.homing() == wolf)) {
				bad[0] = true;
			}
		});
		helper.runAfterDelay(2, () -> look(p, mate));
		helper.runAfterDelay(14, () -> look(p, wolf));
		helper.runAfterDelay(40, () -> {
			try {
				helper.assertTrue(!bad[0], "the ball never homed onto the squadmate or their wolf");
				helper.assertTrue(mate.getHealth() == m0 && wolf.getHealth() == w0, "both untouched");
			} finally {
				squads.disband(squad);
			}
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void turnNeededMatchesTheTurningCircle(GameTestHelper helper) {
		Vec3 o = Vec3.ZERO, fwd = new Vec3(0, 0, 1);
		helper.assertTrue(Math.abs(SteelBallEntity.turnNeeded(o, fwd, new Vec3(0, 0, 10), 5)) < 1.0e-6, "dead ahead: no turn");
		helper.assertTrue(SteelBallEntity.turnNeeded(o, fwd, new Vec3(3, 0, 1), 5) < 0, "inside the turning circle: can't reach");
		double side = SteelBallEntity.turnNeeded(o, fwd, new Vec3(15, 0, 0), 5); // 90 degrees off, outside the circle
		helper.assertTrue(side > Math.toRadians(90) && side < Math.toRadians(180), "a target square to the side needs more than 90, got "
				+ Math.toDegrees(side));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Golden Rectangle

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void perfectSpinWindowAndGoldenSpinDamage(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		int t = SpinConfig.GOLDEN_CHARGE_TICKS;
		helper.assertTrue(Spin.isPerfect(t, t) && Spin.isPerfect(t - SpinConfig.PERFECT_WINDOW, t) && Spin.isPerfect(t + SpinConfig.PERFECT_WINDOW, t),
				"the window is the close +-3 ticks");
		helper.assertTrue(!Spin.isPerfect(t - SpinConfig.PERFECT_WINDOW - 1, t) && !Spin.isPerfect(t + SpinConfig.PERFECT_WINDOW + 1, t), "outside it isn't");
		Zombie a = zombie(helper, 4.5, 1.5, 200);
		Zombie b = zombie(helper, 6.5, 1.5, 200);
		a.setNoAi(true);
		b.setNoAi(true); // knocked-back zombies wandered off the line now and then
		b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		b.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		b.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		look(p, a);
		float[] b1 = new float[1];
		// a plain release (the armour has had a tick to count)
		helper.runAfterDelay(3, () -> {
			float a0 = a.getHealth(), b0 = b.getHealth();
			helper.assertTrue(SpinMoves.startCharge(p, SpinAbilityManager.GOLDEN_SPIN), "Shift+G starts the rectangle");
			helper.assertTrue(Spin.charging(p) == SpinState.CHARGE_GOLDEN && Spin.chargeTarget(p) == t, "charging, 30 ticks to close");
			helper.assertTrue(SpinMoves.releaseCharge(p, SpinAbilityManager.GOLDEN_SPIN), "released at once: still fires, not Perfect");
			helper.assertTrue(SpinMoves.lastGoldenHits == 2, "the line pierces both zombies, got " + SpinMoves.lastGoldenHits);
			helper.assertTrue(near(ratio(a0, a, SpinConfig.GOLDEN_DAMAGE)), "35 to the first, lost " + (a0 - a.getHealth()));
			helper.assertTrue(b0 - b.getHealth() < SpinConfig.GOLDEN_DAMAGE * 0.9f, "armour softens a plain Golden Spin, lost " + (b0 - b.getHealth()));
			helper.assertTrue(Math.abs(Spin.spin(p) - 50f) < 0.01f && SpinAbilityManager.cooldownRemaining(p, SpinAbilityManager.GOLDEN_SPIN) > 0,
					"50 Spin and the 12 s cooldown");
			// get ready for the Perfect one: the ball home, the bar full, no cooldown
			helper.getLevel().getEntitiesOfClass(SteelBallEntity.class, p.getBoundingBox().inflate(60)).forEach(SteelBallEntity::discard);
			Spin.clearCooldowns(p);
			Spin.setSpin(p, 100f);
			b1[0] = b.getHealth();
			helper.assertTrue(SpinMoves.startCharge(p, SpinAbilityManager.GOLDEN_SPIN), "charging again");
		});
		// released right on the close: a Perfect Spin, through the armour
		helper.runAfterDelay(3 + t, () -> {
			b.invulnerableTime = 0;
			look(p, b);
			helper.assertTrue(SpinMoves.releaseCharge(p, SpinAbilityManager.GOLDEN_SPIN), "released on the close");
			helper.assertTrue(near(ratio(b1[0], b, SpinConfig.GOLDEN_PERFECT_DAMAGE)), "Perfect: 70 straight through diamond, lost " + (b1[0] - b.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ridingHitsHarderAndClosesTwiceAsFast(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		Horse h = helper.spawnWithNoFreeWill(EntityType.HORSE, new Vec3(1.5, 2.0, 1.5));
		p.startRiding(h, true);
		helper.assertTrue(Spin.riding(p), "on a horse");
		helper.assertTrue(Spin.chargeTicksFor(p) == SpinConfig.GOLDEN_CHARGE_TICKS / 2, "the rectangle closes twice as fast");
		Zombie z = zombie(helper, 1.5, 4.5, 100);
		float before = z.getHealth();
		helper.assertTrue(Spin.hit(p, z, 10f, false), "a Spin hit lands");
		helper.assertTrue(near(ratio(before, z, 13f)), "+30% riding (13), lost " + (before - z.getHealth()));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ballBreakerNeedsAPerfectSpin(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		Zombie z = zombie(helper, 5.5, 1.5, 200);
		look(p, z);
		int t = SpinConfig.GOLDEN_CHARGE_TICKS;
		// not Perfect (let go at once): it fails, 20 Spin, no cooldown
		helper.assertTrue(SpinMoves.startCharge(p, SpinAbilityManager.BALL_BREAKER), "Shift+Z starts the rectangle (full bar)");
		helper.assertTrue(!SpinMoves.releaseCharge(p, SpinAbilityManager.BALL_BREAKER), "not Perfect: Ball Breaker won't come out");
		helper.assertTrue(!SpinMoves.strikePending(p) && Math.abs(Spin.spin(p) - 80f) < 0.01f, "the miss costs 20");
		helper.assertTrue(SpinAbilityManager.cooldownRemaining(p, SpinAbilityManager.BALL_BREAKER) == 0, "and no cooldown");
		helper.assertTrue(!SpinMoves.startCharge(p, SpinAbilityManager.BALL_BREAKER), "under 100 Spin it can't even start");
		// Perfect: charge now, let go on the close
		Spin.setSpin(p, 100f);
		helper.assertTrue(SpinMoves.startCharge(p, SpinAbilityManager.BALL_BREAKER), "again at 100");
		float[] before = new float[1];
		ticker(helper, p);
		helper.runAfterDelay(t, () -> {
			look(p, z); // (it may have settled onto the floor meanwhile)
			helper.assertTrue(SpinMoves.aimed(p, SpinConfig.BREAKER_RANGE) == z, "he's aiming at the zombie");
			helper.assertTrue(SpinMoves.releaseCharge(p, SpinAbilityManager.BALL_BREAKER), "Perfect: Ball Breaker bursts out (" + SpinMoves.lastRefusal + ")");
			helper.assertTrue(SpinMoves.strikePending(p) && Spin.spin(p) < 0.01f, "100 Spin, the figure on its way");
			helper.assertTrue(SpinAbilityManager.cooldownRemaining(p, SpinAbilityManager.BALL_BREAKER) > SpinConfig.BREAKER_COOLDOWN - 5, "3 min cooldown");
			before[0] = z.getHealth();
		});
		helper.runAfterDelay(t + SpinConfig.BREAKER_STRIKE_DELAY + 4, () -> {
			helper.assertTrue(!SpinMoves.strikePending(p), "it struck");
			helper.assertTrue(near(ratio(before[0], z, SpinConfig.BREAKER_DAMAGE)), "60 damage, lost " + (before[0] - z.getHealth()));
			helper.assertTrue(SpinMarks.aged(z) && z.hasEffect(MobEffects.WITHER) && z.hasEffect(MobEffects.WEAKNESS)
					&& z.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2, "aged: Weakness II, Slowness III, Wither");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the other moves

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void touchQuakeScanLeapCostsAndDamage(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		Zombie near = zombie(helper, 1.5, 3.5, 100);
		look(p, near);
		float n0 = near.getHealth();
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.SPIN_TOUCH), "G touches");
		helper.assertTrue(near(ratio(n0, near, SpinConfig.TOUCH_DAMAGE)) && SpinMarks.locked(near), "6 damage and its joints lock");
		helper.assertTrue(Math.abs(Spin.spin(p) - 80f) < 0.01f, "20 Spin");
		Spin.setSpin(p, 100f);
		float q0 = near.getHealth();
		near.invulnerableTime = 0;
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.SPIN_QUAKE), "C quakes");
		helper.assertTrue(near(ratio(q0, near, SpinConfig.QUAKE_DAMAGE)) && near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "14 damage, slowed");
		helper.assertTrue(Math.abs(Spin.spin(p) - 75f) < 0.01f && Spin.ballsReady(p) == 1, "25 Spin; the ball is busy a moment");
		Spin.setSpin(p, 100f);
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the Quake took the ball out of his hand");
		edit(p, s -> { s.busy0 = 0L; s.busy1 = 0L; });
		Spin.tick(p);
		helper.assertTrue(SpinBalls.inHand(p, InteractionHand.MAIN_HAND) == 0, "and gave it back once done");
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.SCAN), "Z scans");
		helper.assertTrue(SpinMoves.lastScan >= 1 && Math.abs(Spin.spin(p) - 85f) < 0.01f, "it found the zombie; 15 Spin");
		helper.assertTrue(Spin.ballsReady(p) == 1, "the ball spins on the ground meanwhile");
		Spin.setSpin(p, 100f);
		p.setYRot(0f);
		p.setXRot(0f);
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.SPIN_LEAP), "X leaps");
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(Math.abs(v.y - SpinConfig.LEAP_UP) < 1.0e-3 && Math.abs(Math.hypot(v.x, v.z) - SpinConfig.LEAP_FORWARD) < 1.0e-3,
				"forward and up");
		helper.assertTrue(Math.abs(Spin.spin(p) - 90f) < 0.01f, "10 Spin");
		helper.assertTrue(!SpinDamage.allowDamage(p, p.damageSources().fall(), 10f), "no fall damage after the leap");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spinShieldDeflectsArrows(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.SPIN_SHIELD), "Shift+C");
		helper.assertTrue(Spin.shielding(p) && Spin.ballsReady(p) == 0 && Math.abs(Spin.spin(p) - 70f) < 0.01f, "both balls orbit; 30 Spin");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(p.getX() + 2.0, p.getY() + 1.0, p.getZ());
		arrow.setDeltaMovement(-1.5, 0, 0);
		helper.getLevel().addFreshEntity(arrow);
		int before = SpinMoves.deflected;
		Spin.tick(p);
		helper.assertTrue(SpinMoves.deflected == before + 1 && arrow.getDeltaMovement().x > 0 && arrow.getOwner() == p,
				"the arrow is knocked back out, and it's his now");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void valkyrieComesAndHasADeathCooldown(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.VALKYRIE), "Shift+X whistles");
		AbstractHorse h = SpinValkyrie.find(p);
		helper.assertTrue(h != null, "Valkyrie is in the world (state " + Spin.state(p).valkyrie + ")");
		helper.assertTrue(h.isTamed() && p.getUUID().equals(h.getOwnerUUID()) && SpinValkyrie.isValkyrie(h), "Valkyrie, tamed to him: tamed "
				+ h.isTamed() + " owner " + h.getOwnerUUID() + " tags " + h.getTags());
		helper.assertTrue(h.isSaddled() && h.getBodyArmorItem().is(Items.DIAMOND_HORSE_ARMOR), "saddled and armoured");
		helper.assertTrue(Math.abs(h.getAttributeValue(Attributes.MOVEMENT_SPEED) - SpinConfig.VALKYRIE_SPEED) < 1.0e-4, "very fast");
		helper.assertTrue(SpinValkyrie.galloping(p), "she gallops in");
		ticker(helper, p);
		helper.runAfterDelay(SpinConfig.VALKYRIE_GALLOP_TICKS + 5, () -> {
			helper.assertTrue(p.getVehicle() == h, "he's in the saddle");
			p.stopRiding();
			h.kill();
		});
		helper.runAfterDelay(SpinConfig.VALKYRIE_GALLOP_TICKS + 8, () -> {
			helper.assertTrue(Spin.valkyrieCooldown(p) > SpinConfig.VALKYRIE_DEAD_COOLDOWN - 20, "her death starts the 60 s cooldown");
			helper.assertTrue(!SpinMoves.use(p, SpinAbilityManager.VALKYRIE), "no whistling her back yet");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void healingSpinHealsSelfAndSquadmate(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		ServerPlayer mate = mock(helper, 1.5, 4.5);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("SpinHeal" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		p.setHealth(6f); // 20 max: room for the full 12
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200));
		p.setXRot(90f); // looking at the floor: nobody to heal but himself
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.HEALING_SPIN), "V heals");
		helper.assertTrue(!p.hasEffect(MobEffects.POISON), "poison cleared");
		helper.assertTrue(Math.abs(Spin.spin(p) - 75f) < 0.01f && SpinAbilityManager.cooldownRemaining(p, SpinAbilityManager.HEALING_SPIN) > 0,
				"25 Spin, 15 s cooldown");
		mate.setHealth(6f);
		look(p, mate);
		helper.assertTrue(SpinMoves.healTarget(p) == mate, "aimed at a squadmate: the heal goes to them");
		helper.runAfterDelay(SpinConfig.HEAL_TICKS + 5, () -> {
			helper.assertTrue(p.getHealth() - (6f + SpinConfig.HEAL_TOTAL) > -0.6f && p.getHealth() - (6f + SpinConfig.HEAL_TOTAL) < 2.6f, "6 hearts over 3 s, health " + p.getHealth());
			squads.disband(squad);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void squadmatesAndTheirPetsAreNeverHurt(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		ServerPlayer mate = mock(helper, 3.5, 1.5);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("SpinSafe" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new Vec3(1.5, 2.0, 3.5));
			wolf.tame(mate);
			Zombie z = zombie(helper, 3.5, 3.5, 100);
			float m0 = mate.getHealth(), w0 = wolf.getHealth(), z0 = z.getHealth();
			helper.assertTrue(!Spin.hit(p, mate, 20f, true) && !Spin.hit(p, wolf, 20f, false), "a Spin hit never lands on a squadmate or their pet");
			helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.SPIN_QUAKE), "C quakes with them all in range");
			helper.assertTrue(mate.getHealth() == m0 && wolf.getHealth() == w0, "the squadmate and the wolf are untouched");
			helper.assertTrue(near(ratio(z0, z, SpinConfig.QUAKE_DAMAGE)), "the zombie isn't");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void faceSpinHidesHimFromMobsThatHaventSeenHimAttack(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		Zombie z = zombie(helper, 5.5, 1.5, 100);
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == p, "hunting him");
		helper.assertTrue(SpinMoves.use(p, SpinAbilityManager.FACE_SPIN), "Shift+V");
		helper.assertTrue(Spin.disguised(p) && Math.abs(Spin.spin(p) - 80f) < 0.01f, "disguised, 20 Spin");
		helper.assertTrue(z.getTarget() == null, "the zombie lost him");
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == null, "and can't take him as a target");
		z.invulnerableTime = 0;
		z.hurt(p.damageSources().playerAttack(p), 2f);
		helper.assertTrue(SpinDisguise.isWitness(p, z), "struck: it saw him attack");
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == p, "a witness may come for him");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keysRouteToTheSpinAndHAndNDoNothing(GameTestHelper helper) {
		ServerPlayer p = spin(helper);
		helper.assertTrue(SpinAbilityManager.moveFor(AbilitySlot.SLOT_3, false).equals(SpinAbilityManager.SPIN_LEAP)
				&& SpinAbilityManager.moveFor(AbilitySlot.SLOT_3, true).equals(SpinAbilityManager.VALKYRIE), "X = Spin Leap, Shift+X = Valkyrie");
		helper.assertTrue(SpinAbilityManager.moveFor(AbilitySlot.SLOT_7, false) == null && SpinAbilityManager.moveFor(AbilitySlot.SLOT_8, true) == null,
				"no H or N ability");
		p.setShiftKeyDown(true);
		SpinAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(Spin.charging(p) == SpinState.CHARGE_GOLDEN, "Shift+G held charges");
		p.setShiftKeyDown(false);
		SpinAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(Spin.charging(p) == SpinState.CHARGE_NONE, "letting go fires (even after letting go of Shift)");
		helper.succeed();
	}
}
