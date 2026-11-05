package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Wildvine (user: "also create a moveset for wildvine please ... do research on his powers and look at what he does in the
 * show"): hidden on the prototype's dial until found, and every key's move does what its line says -- the vine lash and slash, the
 * seed bomb and mines off the pods on his back, the swing, the burrow, the coils, Overgrowth, Regrowth, the camouflage, the snare and
 * the slam -- plus his passives (walking through trees among them) and his fire / frost weaknesses.
 */
public class OmnitrixWildvineGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** A stone floor, a prototype wearer who has found Wildvine, transformed into him at (3.5, 2, 1.5) looking +Z. */
	private static ServerPlayer wildvine(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertFalse(Omnitrix.select(p, WildvineKit.ID), "Wildvine is hidden until a mistransform finds him");
		Aliens.discover(p, WildvineKit.ID, true);
		helper.assertTrue(Omnitrix.select(p, WildvineKit.ID), "found, he can be dialled");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Wildvine");
		return p;
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static WildvineKit kit() {
		return (WildvineKit) Aliens.kit(WildvineKit.ID);
	}

	private static boolean rooted(Zombie z) {
		MobEffectInstance s = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wildvineIsAHiddenPrototypeAlienWithAMoveOnEveryKey(GameTestHelper helper) {
		helper.assertTrue(Aliens.byId(WildvineKit.ID) != null && !Aliens.byId(WildvineKit.ID).recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.hidden(WildvineKit.ID), "found by a mistransform, like Cannonbolt");
		helper.assertTrue(Aliens.dial(false).contains(Aliens.WILDVINE) && !Aliens.dial(true).contains(Aliens.WILDVINE), "prototype only");
		helper.assertTrue(Aliens.WILDMUTT != Aliens.WILDVINE && Aliens.kit(Aliens.WILDMUTT.id()) != null, "Wildmutt is still his own alien");
		AlienKit k = kit();
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, false, false) != null, "a move on " + s);
			helper.assertTrue(k.moveOn(s, true, false) != null, "a Shift move on " + s);
		}
		helper.assertTrue(k.moveOn(AbilitySlot.SLOT_1, true, false).id().equals(WildvineKit.SLASH), "Shift+R is the Vine Slash");
		helper.assertTrue(k.moveOn(AbilitySlot.SLOT_6, false, false).id().equals(WildvineKit.SNARE), "C is the Vine Snare");
		helper.assertTrue(k.moveOn(AbilitySlot.SLOT_4, true, false).id().equals(WildvineKit.OVERGROWTH), "Shift+Z is Overgrowth");
		helper.assertTrue(k.moveOn(AbilitySlot.SLOT_6, true, false).id().equals(WildvineKit.SLAM), "Shift+C is the Vine Slam");
		helper.assertTrue(k.move(WildvineKit.SEED_BOMB).cooldown() == 20, "Seed Bomb: 1 second");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineLashHitsWithoutPullingAndFindsACreatureJustOffAim(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		// (the test box's barrier walls stop anything further off: the ranges are checked as numbers)
		helper.assertTrue(WildvineKit.LASH_RANGE == 12.0 && WildvineKit.SWING_RANGE == 45.0, "Ladybug's reach");
		Zombie z = zombie(helper, 3.5, 2, 6.5);
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(z.getHealth() < before, "the Vine Lash hits: " + z.getHealth());
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.LASH) == WildvineKit.LASH_COOLDOWN, "R cools down for Ladybug's 0.8 s");
		helper.assertTrue(z.getDeltaMovement().z > 0.1, "it knocks the zombie away, never toward him: " + z.getDeltaMovement());
		z.discard();
		Zombie off = zombie(helper, 4.7, 2, 6.5); // 1.2 across at 5 ahead: off the crosshair, inside the assist
		float offBefore = off.getHealth();
		kit().cooldown(p, WildvineKit.LASH, 0);
		tap(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(off.getHealth() < offBefore, "the slight auto-aim finds it: " + off.getHealth());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void vineSlashCutsACone20BlocksOut(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		Zombie far = zombie(helper, 3.5, 2, 6.5);
		Zombie wide = zombie(helper, 5.5, 2, 6.5); // about 22 degrees off
		Zombie behind = zombie(helper, 3.5, 2, 0.5);
		Zombie beside = zombie(helper, 6.5, 2, 1.5); // 90 degrees off
		// (past the test box's walls the sum is checked on its own: 20 blocks ahead, 35 degrees either side)
		Vec3 ahead = new Vec3(0, 0, 1);
		Zombie range = zombie(helper, 3.5, 2, 1.5);
		range.moveTo(p.getX(), p.getY(), p.getZ() + 19.5);
		helper.assertTrue(WildvineKit.inCone(p.position(), ahead, range), "19.5 ahead is in the cone");
		range.moveTo(p.getX(), p.getY(), p.getZ() + 20.5);
		helper.assertFalse(WildvineKit.inCone(p.position(), ahead, range), "20.5 ahead is not");
		range.moveTo(p.getX() + 15.0, p.getY(), p.getZ() + 15.0);
		helper.assertFalse(WildvineKit.inCone(p.position(), ahead, range), "45 degrees off is not");
		range.discard();
		float bf = far.getHealth(), bw = wide.getHealth(), bb = behind.getHealth(), bs = beside.getHealth();
		tap(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.SLASH) > 0, "Shift+R cools down");
		helper.runAfterDelay(WildvineKit.SLASH_WINDUP + 12, () -> {
			helper.assertTrue(bf - far.getHealth() >= 12f && bw - wide.getHealth() >= 12f, "both in the cone are cut: " + far.getHealth() + " " + wide.getHealth());
			helper.assertTrue(behind.getHealth() == bb && beside.getHealth() == bs, "nothing outside it");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void seedBombArcsAndBursts(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		Zombie z = zombie(helper, 3.5, 2, 6.5);
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.SEED_BOMB) > 0, "G cools down");
		helper.assertTrue(WildvineKit.pods(p) == WildvineKit.PODS - 1, "a pod off his back: " + WildvineKit.pods(p));
		helper.runAfterDelay(WildvineKit.THROW_AT + 1, () -> helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(6),
				e -> e.kind().equals("wildvine_seed")).isEmpty(), "a seed pod is thrown as the arm comes over"));
		helper.succeedWhen(() -> helper.assertTrue(z.getHealth() <= before - 9f, "the pod bursts on the zombie: " + z.getHealth()));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void thePodsOnHisBackAreTheStockAndOneRegrowsEvery3Seconds(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		helper.assertTrue(WildvineKit.pods(p) == 6, "six to start");
		for (int i = 0; i < 6; i++) {
			kit().cooldown(p, WildvineKit.SEED_BOMB, 0);
			tap(p, AbilitySlot.SLOT_2, false);
		}
		helper.assertTrue(WildvineKit.pods(p) == 0, "six throws, none left: " + WildvineKit.pods(p));
		kit().cooldown(p, WildvineKit.SEED_BOMB, 0);
		helper.assertFalse(kit().seedBomb(p), "no pod, no throw");
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.SEED_BOMB) == 0, "and no cooldown spent");
		helper.runAfterDelay(WildvineKit.POD_REGROW + 1, () -> {
			helper.assertTrue(WildvineKit.pods(p) == 1, "one back after 3 s: " + WildvineKit.pods(p));
			helper.runAfterDelay(WildvineKit.POD_REGROW, () -> {
				helper.assertTrue(WildvineKit.pods(p) == 2, "and another 3 s later: " + WildvineKit.pods(p));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void seedMinesArmAndBurstUnderAnEnemy(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		tap(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(WildvineKit.pods(p) == WildvineKit.PODS - WildvineKit.MINES, "three pods off his back");
		helper.runAfterDelay(WildvineKit.PLANT_AT + 1, () -> {
			helper.assertTrue(WildvineKit.mineCount(p) == WildvineKit.MINES, "three pods planted: " + WildvineKit.mineCount(p));
			AlienProjectileEntity mine = WildvineKit.mines(p).get(1);
			helper.runAfterDelay(WildvineKit.MINE_ARM + 4, () -> {
				Zombie z = zombie(helper, 0, 0, 0);
				z.moveTo(mine.getX(), mine.getY(), mine.getZ());
				float before = z.getHealth();
				helper.succeedWhen(() -> {
					helper.assertTrue(z.getHealth() <= before - 8f, "the armed mine bursts under the zombie: " + z.getHealth());
					helper.assertTrue(WildvineKit.mineCount(p) < WildvineKit.MINES, "and is spent");
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void seedMinesBlowUpOnShiftGAgain(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		tap(p, AbilitySlot.SLOT_2, true);
		helper.runAfterDelay(WildvineKit.PLANT_AT + 1, () -> {
			AlienProjectileEntity mine = WildvineKit.mines(p).get(1);
			Zombie z = zombie(helper, 0, 0, 0);
			z.moveTo(mine.getX() + 1.8, mine.getY(), mine.getZ()); // beside it, too far to trip it
			float before = z.getHealth();
			helper.assertTrue(kit().cooldownLeft(p, WildvineKit.SEED_MINES) > 0, "the plant's cooldown is running");
			tap(p, AbilitySlot.SLOT_2, true);
			helper.assertTrue(WildvineKit.mineCount(p) == 0, "all three gone, on cooldown or not");
			helper.assertTrue(z.getHealth() < before, "and they burst: " + z.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineSwingHangsOffAnyBlockUntilLetGo(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		for (int x = 1; x <= 6; x++) {
			for (int z = 3; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 7, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		p.setXRot(-55f); // up and ahead, at the ceiling
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), true);
		helper.assertTrue(WildvineKit.swinging(p) && WildvineKit.hook(p) != null, "X down: a vine to the ceiling");
		helper.assertTrue(kit().keyActive(p, AbilitySlot.SLOT_3), "X lights while he swings");
		helper.assertFalse(kit().allowDamage(p, helper.getLevel().damageSources().fall(), 6f), "no fall damage off his vine");
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), false);
		helper.assertFalse(WildvineKit.swinging(p), "letting go of X lets go of the vine");
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.SWING) == WildvineKit.SWING_COOLDOWN, "the wait starts when he lets go");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void constrictRootsAndSqueezes(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		Zombie z = zombie(helper, 3.5, 2, 7.5);
		helper.onEachTick(() -> Omnitrix.tick(p));
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(WildvineKit.coiling(p) && rooted(z), "Constrict coils and roots the zombie 6 blocks off");
		helper.runAfterDelay(WildvineKit.CONSTRICT_TICKS + 2, () -> {
			helper.assertTrue(before - z.getHealth() >= 16f, "squeezed over 3 s: " + (before - z.getHealth()));
			helper.assertFalse(WildvineKit.coiling(p), "and let go");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void vineSnareAndOvergrowthHitAndRootEverythingAround(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		Zombie a = zombie(helper, 6.5, 2, 3.5);
		Zombie b = zombie(helper, 0.5, 2, 3.5);
		float ba = a.getHealth(), bb = b.getHealth();
		tap(p, AbilitySlot.SLOT_6, false);
		helper.runAfterDelay(WildvineKit.SNARE_AT + 1, () -> {
			helper.assertTrue(a.getHealth() < ba && b.getHealth() < bb && rooted(a) && rooted(b), "the Vine Snare (C) hits and roots both");
			Zombie c = zombie(helper, 3.5, 2, 7.5);
			float bc = c.getHealth();
			tap(p, AbilitySlot.SLOT_4, true);
			helper.assertTrue(kit().cooldownLeft(p, WildvineKit.OVERGROWTH) > 0, "Shift+Z cools down");
			helper.runAfterDelay(WildvineKit.OVERGROWTH_AT + 1, () -> {
				helper.assertTrue(bc - c.getHealth() >= 15f && rooted(c), "Overgrowth hits 6 blocks off hard and roots it: " + (bc - c.getHealth()));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void vineSlamHoistsAndSmashes(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		Zombie z = zombie(helper, 3.5, 2, 6.5);
		Zombie by = zombie(helper, 2.5, 2, 4.5);
		float before = z.getHealth(), byBefore = by.getHealth();
		double y0 = z.getY();
		tap(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(WildvineKit.slamming(p), "a vine snatches it");
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.SLAM) > 0, "Shift+C cools down");
		helper.runAfterDelay(WildvineKit.SLAM_LIFT - 1, () -> {
			helper.assertTrue(z.getY() > y0 + 2.5, "hoisted overhead: " + (z.getY() - y0));
			helper.assertTrue(z.getHealth() == before, "no hurt till it lands");
		});
		helper.runAfterDelay(WildvineKit.SLAM_LIFT + WildvineKit.SLAM_FALL + WildvineKit.SLAM_SETTLE + 2, () -> {
			helper.assertFalse(WildvineKit.slamming(p), "over");
			helper.assertTrue(before - z.getHealth() >= 14f, "smashed down: " + (before - z.getHealth()));
			helper.assertTrue(by.getHealth() < byBefore, "and the one beside the impact feels it: " + by.getHealth());
			helper.assertTrue(z.position().distanceTo(p.position()) < 5.0, "in front of him: " + z.position().distanceTo(p.position()));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineSlamWithNothingToGrabCostsNothing(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.assertFalse(kit().vineSlam(p), "nothing there");
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.SLAM) == 0, "no cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void rootBurrowHasNoTimeLimitAndXBurstsHimUp(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		p.setOnGround(true);
		Zombie z = zombie(helper, 4.5, 2, 3.0);
		z.setTarget(p);
		tap(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(kit().flag(p, WildvineKit.F_BURROW), "Shift+X sinks him into the ground");
		helper.assertTrue(z.getTarget() == null, "the zombie hunting him loses track");
		DamageSource hit = helper.getLevel().damageSources().mobAttack(z);
		helper.assertFalse(kit().allowDamage(p, hit, 6f), "untouchable under the ground");
		helper.assertTrue(kit().allowDamage(p, helper.getLevel().damageSources().onFire(), 1f), "but fire still finds a plant");
		float before = z.getHealth();
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(kit().flag(p, WildvineKit.F_BURROW), "6 s on, twice the old limit, still under");
			tap(p, AbilitySlot.SLOT_3, false);
			helper.assertFalse(kit().flag(p, WildvineKit.F_BURROW), "X brings him up");
			helper.assertTrue(z.getHealth() < before, "the burst hits the zombie beside him: " + z.getHealth());
			helper.assertTrue(kit().cooldownLeft(p, WildvineKit.BURROW) == WildvineKit.BURROW_COOLDOWN, "the cooldown runs from now");
			helper.succeed();
		});
	}

	private static boolean clear(ServerPlayer p, BlockPos at) {
		return p.level().noCollision(p, new AABB(at).deflate(0.05));
	}

	private static void block(GameTestHelper helper, BlockPos relative, net.minecraft.world.level.block.Block b) {
		helper.getLevel().setBlock(helper.absolutePos(relative), b.defaultBlockState(), 3);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heWalksThroughTreesButNotWalls(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		BlockPos log = new BlockPos(3, 2, 2), leaf = new BlockPos(4, 2, 2), stone = new BlockPos(2, 2, 2), planks = new BlockPos(5, 2, 2), dirt = new BlockPos(6, 2, 2);
		block(helper, log, Blocks.OAK_LOG);
		block(helper, leaf, Blocks.OAK_LEAVES);
		block(helper, stone, Blocks.STONE);
		block(helper, planks, Blocks.OAK_PLANKS);
		block(helper, dirt, Blocks.DIRT);
		helper.assertTrue(clear(p, helper.absolutePos(log)) && clear(p, helper.absolutePos(leaf)), "wood and leaves do not stop him");
		helper.assertFalse(clear(p, helper.absolutePos(stone)) || clear(p, helper.absolutePos(planks)) || clear(p, helper.absolutePos(dirt)), "stone, planks and dirt do");
		helper.assertFalse(helper.getLevel().noCollision(new AABB(helper.absolutePos(log)).deflate(0.05)), "and a log is still solid for everything else");
		p.moveTo(p.getX(), helper.absolutePos(log).getY() + 1.0, p.getZ());
		helper.assertFalse(clear(p, helper.absolutePos(log)), "stood on top of it, it is his floor");
		p.setShiftKeyDown(true);
		helper.assertTrue(clear(p, helper.absolutePos(log)), "Sneak drops him through");
		p.setShiftKeyDown(false);
		helper.assertTrue(kit().allowDamage(p, helper.getLevel().damageSources().inWall(), 1f), "out of the tree a wall still chokes him");
		helper.assertFalse(kit().allowDamage(p, helper.getLevel().damageSources().cactus(), 1f), "a cactus does nothing to him");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBurrowSlipsThroughThinBlocksAndShiftPhasesTheGround(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		BlockPos fence = new BlockPos(3, 2, 2), bars = new BlockPos(4, 2, 2), dirt = new BlockPos(2, 2, 2), rock = new BlockPos(5, 2, 2), bricks = new BlockPos(6, 2, 2), slab = new BlockPos(1, 2, 2);
		block(helper, fence, Blocks.OAK_FENCE);
		block(helper, bars, Blocks.IRON_BARS);
		block(helper, dirt, Blocks.DIRT);
		block(helper, rock, Blocks.STONE);
		block(helper, bricks, Blocks.BRICKS);
		block(helper, slab, Blocks.STONE_SLAB);
		helper.assertFalse(clear(p, helper.absolutePos(fence)), "on foot a fence stops him");
		p.setOnGround(true);
		tap(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(clear(p, helper.absolutePos(fence)) && clear(p, helper.absolutePos(bars)), "burrowed, fences and bars do not");
		helper.assertFalse(clear(p, helper.absolutePos(dirt)) || clear(p, helper.absolutePos(rock)), "the ground stops him without Shift");
		helper.assertFalse(p.level().noCollision(p, new AABB(helper.absolutePos(slab)).inflate(-0.01, -0.01, -0.01).setMinY(helper.absolutePos(slab).getY() + 0.01)), "a slab is one he walks up");
		p.setShiftKeyDown(true);
		helper.assertTrue(clear(p, helper.absolutePos(dirt)) && clear(p, helper.absolutePos(rock)), "with Shift he phases earth and rock");
		helper.assertFalse(clear(p, helper.absolutePos(bricks)), "but nothing built");
		BlockPos floor = new BlockPos(3, 1, 3);
		helper.assertFalse(clear(p, helper.absolutePos(floor)), "and the ground under his feet stays under his feet");
		p.setShiftKeyDown(false);
		helper.assertTrue(p.getBbHeight() < 1.0f || kit().flag(p, WildvineKit.F_BURROW), "a low mound");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void lettingGoOfShiftInsideTheGroundBringsHimOutOnTop(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		p.setOnGround(true);
		tap(p, AbilitySlot.SLOT_3, true);
		for (int x = 2; x <= 5; x++) {
			for (int z = 0; z <= 3; z++) {
				for (int y = 2; y <= 4; y++) {
					block(helper, new BlockPos(x, y, z), Blocks.DIRT);
				}
			}
		}
		p.setShiftKeyDown(true);
		helper.assertTrue(p.level().noCollision(p, p.getBoundingBox()), "phasing, he is inside the hill");
		p.setShiftKeyDown(false);
		double y0 = p.getY();
		helper.onEachTick(() -> Omnitrix.tick(p));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(p.getY() >= y0 + 3.0, "Shift let go: up on top of it: " + (p.getY() - y0));
			helper.assertTrue(p.level().noCollision(p, p.getBoundingBox()), "and clear");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void regrowthHealsAndCamouflageLosesHunters(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		p.setHealth(10f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(p.getHealth() >= 19.9f, "Regrowth: 10 back: " + p.getHealth());
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "and the poison is shed");
		Zombie z = zombie(helper, 3.5, 2, 6.5);
		z.setTarget(p);
		tap(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(kit().flag(p, WildvineKit.F_CAMO), "Shift+V: camouflaged");
		helper.assertTrue(z.getTarget() == null, "the zombie hunting him loses track");
		helper.runAfterDelay(WildvineKit.CAMO_AT + 1, () -> {
			helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "the green closes over him");
			tap(p, AbilitySlot.SLOT_1, false);
			helper.assertFalse(kit().flag(p, WildvineKit.F_CAMO), "a move ends the camouflage");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireAndFrostHitAPlantHardAndHeClimbsWalls(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		WildvineKit k = kit();
		helper.assertTrue(Math.abs(k.incomingDamage(p, helper.getLevel().damageSources().onFire(), 4f) - 7f) < 0.01f, "fire: +75%");
		helper.assertTrue(Math.abs(k.incomingDamage(p, helper.getLevel().damageSources().freeze(), 4f) - 7f) < 0.01f, "frost: +75%");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and frost slows him");
		Zombie z = zombie(helper, 6.5, 2, 6.5);
		helper.assertTrue(k.incomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 4f) == 4f, "a plain hit is whole");
		helper.assertTrue(k.incomingDamage(p, helper.getLevel().damageSources().fall(), 4f) == 2f, "falls: half (a pliant body)");
		p.horizontalCollision = true;
		helper.assertTrue(k.climbs(p), "pressed into a wall he climbs it");
		p.horizontalCollision = false;
		helper.assertFalse(k.climbs(p), "not in the open");
		helper.succeed();
	}
}
