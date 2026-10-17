package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p24.WindBladeEntity;
import com.projecthero.mod.hero.power.p24.WindFxEntity;
import com.projecthero.mod.hero.power.p24.WindHandlers;
import com.projecthero.mod.hero.power.p24.WindTornadoEntity;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Wind Manipulation rework (docs/V0165_POWERS_PROPOSAL.md, "Wind Manipulation Rework"): six keys (+ Shift
 * versions) on a 0-100 Gust meter -- the meter (spend, refill, the high-air rate, refusal when short), the damage
 * numbers (Wind Blade through a line of mobs, the Flurry, Wind Push + the wall slam, the Tornado spin), the Vacuum
 * hold, projectile deflection (Barrier, Hurricane and the 30% veer passive), Glide / Updraft / the rideable tornado /
 * Air Bubble and the passives. Mock players are not reliably ticked by the server, so the tests drive
 * {@link ExperimentalPowers#serverTick} themselves. Target dummies are no-AI iron golems (100 HP, no armour, so the
 * damage numbers come through exactly).
 */
public class WindV0165GameTests implements FabricGameTest {
	private static final String K = WindHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock wind hero at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x4x9 room. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -1), base.offset(2, 4, 6))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -1), base.offset(2, -1, 6))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Power power = Powers.byKey(K);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zb = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zb.setPersistenceRequired();
		zb.setNoAi(true);
		return zb;
	}

	private static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 v, net.minecraft.world.entity.Entity owner) {
		Arrow a = new Arrow(EntityType.ARROW, helper.getLevel());
		a.setPos(at.x, at.y, at.z);
		a.setOwner(owner);
		a.setDeltaMovement(v);
		helper.getLevel().addFreshEntity(a);
		return a;
	}

	private static List<WindBladeEntity> blades(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(WindBladeEntity.class, p.getBoundingBox().inflate(40));
	}

	private static void assertNear(GameTestHelper helper, float expected, float got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05f, what + ": expected " + expected + ", got " + got);
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysGustMeterAndEnabled(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(Powers.isEnabled(power) && Powers.isMutation(power), "Wind Manipulation is an enabled mutation again");
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		String[] ids = { "wind_blade", "vacuum", "tailwind", "tornado", "wind_barrier", "rideable_tornado" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		var gust = MutationMeters.get(K, "gust");
		helper.assertTrue(gust != null && gust.always() && gust.style() == MutationMeters.Style.HAIRLINE,
				"Gust is an always-on Hairline");
		helper.assertTrue(MutationMeters.get(K, "hurricane") != null && MutationMeters.get(K, "bubble") != null, "the timers");
		for (String flag : new String[] { "p24.barrier", "p24.hurricane", "p24.bubble", "p24.glide", "p24.ride", "p24.flurry" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), flag + " is synced");
		}
		helper.succeed();
	}

	// ---------------- Gust ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void gustSpendsAndRefills(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(WindHandlers.gust(p) >= 99.9f, "the meter starts full");
		press(p, 1);
		assertNear(helper, 100 - WindHandlers.COST_BLADE, WindHandlers.gust(p), "a Wind Blade costs 8");
		WindHandlers.setGust(p, 50);
		tick(p, 20);
		assertNear(helper, 65, WindHandlers.gust(p), "15 a second");
		assertNear(helper, 25.0f / 20.0f, WindHandlers.regenPerTick(101), "25 a second above y 100");
		assertNear(helper, 15.0f / 20.0f, WindHandlers.regenPerTick(99), "15 below");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gustRefillsFasterHighUp(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		WindHandlers.setGust(p, 20);
		p.setPos(p.getX(), WindHandlers.HIGH_AIR_Y + 10, p.getZ());
		tick(p, 20);
		assertNear(helper, 45, WindHandlers.gust(p), "25 a second above y 100");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void notEnoughGustRefuses(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		WindHandlers.setGust(p, 5);
		press(p, 1);
		helper.assertTrue(blades(helper, p).isEmpty(), "no blade on 5 Gust");
		shiftPress(p, 4);
		helper.assertTrue(res(p, "hurricane") < 0.5f, "no Hurricane either");
		assertNear(helper, 5, WindHandlers.gust(p), "nothing spent");
		helper.succeed();
	}

	// ---------------- R: Wind Blade / Blade Flurry ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void windBladeCutsThroughEveryEnemy(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem near = dummy(helper, 2, 4);
		IronGolem far = dummy(helper, 2, 6);
		WindBladeEntity b = WindHandlers.windBlade(p);
		helper.assertTrue(b.damage() == WindHandlers.BLADE_DAMAGE, "12 a creature");
		helper.succeedWhen(() -> {
			assertNear(helper, 100 - WindHandlers.BLADE_DAMAGE, near.getHealth(), "the first golem is cut for 12");
			assertNear(helper, 100 - WindHandlers.BLADE_DAMAGE, far.getHealth(), "and the blade passes on through the second");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bladeFlurryFiresFourASecond(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 1, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(WindHandlers.flurrying(p), "Shift+R starts the flurry");
		tick(p, 20);
		List<WindBladeEntity> bs = blades(helper, p);
		helper.assertTrue(bs.size() >= 4 && bs.size() <= 5, "4 blades a second, got " + bs.size());
		helper.assertTrue(bs.stream().allMatch(x -> x.damage() == WindHandlers.FLURRY_DAMAGE), "6 each");
		assertNear(helper, 100 - WindHandlers.COST_FLURRY_PER_SECOND, WindHandlers.gust(p), "12 Gust a second, no refill while held");
		AbilityRouter.handleInput(p, 1, false);
		helper.assertTrue(!WindHandlers.flurrying(p), "release stops it");
		helper.succeed();
	}

	// ---------------- G: Vacuum / Wind Push ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void vacuumPullsInAndHolds(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem aimed = dummy(helper, 2, 6);
		Zombie side = zombie(helper, 0, 3);
		WindFxEntity fx = WindHandlers.vacuum(p);
		assertNear(helper, 100 - WindHandlers.COST_VACUUM, WindHandlers.gust(p), "20 Gust");
		helper.assertTrue(fx.caught().contains(aimed) && fx.caught().contains(side), "everything within 10 is caught");
		Vec3 eye = fx.position();
		helper.runAfterDelay(WindFxEntity.VACUUM_PULL_TICKS + 4, () -> {
			helper.assertTrue(side.position().distanceTo(eye) < 2.6, "dragged into the eye, " + side.position().distanceTo(eye));
			helper.assertTrue(aimed.position().distanceTo(eye) < 2.6, "both of them");
		});
		helper.runAfterDelay(WindFxEntity.VACUUM_PULL_TICKS + WindFxEntity.VACUUM_HOLD_TICKS - 2, () -> {
			helper.assertTrue(side.position().distanceTo(eye) < 2.6, "and held there for a second");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void windPushDamageShoveAndWallSlam(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 4);
		Zombie behind = zombie(helper, 0, 0); // behind and to the side, outside point-blank range
		int hit = WindHandlers.windPush(p);
		helper.assertTrue(hit == 1, "only what is in the cone, got " + hit);
		assertNear(helper, 100 - WindHandlers.PUSH_DAMAGE, g.getHealth(), "6 damage");
		helper.assertTrue(g.getDeltaMovement().horizontalDistance() > 1.5, "a huge shove, " + g.getDeltaMovement());
		helper.assertTrue(behind.getHealth() >= behind.getMaxHealth(), "nothing behind you");
		helper.assertTrue(WindHandlers.slamPending(g), "watched for a wall slam");
		g.invulnerableTime = 0;
		WindHandlers.wallSlam(p, g);
		assertNear(helper, 100 - WindHandlers.PUSH_DAMAGE - WindHandlers.WALL_SLAM_DAMAGE, g.getHealth(), "+8 on the wall");
		assertNear(helper, 100 - WindHandlers.COST_PUSH, WindHandlers.gust(p), "15 Gust");
		helper.succeed();
	}

	// ---------------- X: Tailwind / Updraft ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void tailwindDashesFourteenBlocks(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 3);
		assertNear(helper, 100 - WindHandlers.COST_DASH, WindHandlers.gust(p), "10 Gust");
		Vec3 dir = WindHandlers.dashDir(p);
		helper.assertTrue(dir != null && dir.z > 0.95, "along the look, " + dir);
		helper.assertTrue(Math.abs(p.getDeltaMovement().horizontalDistance() * WindHandlers.DASH_TICKS - WindHandlers.DASH_BLOCKS) < 0.1,
				"2 blocks a tick for 7 ticks = 14 blocks");
		tick(p, WindHandlers.DASH_TICKS + 1);
		helper.assertTrue(WindHandlers.dashDir(p) == null, "and done");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void updraftLaunchesAndOpensTheGlide(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		shiftPress(p, 3);
		assertNear(helper, 100 - WindHandlers.COST_UPDRAFT, WindHandlers.gust(p), "15 Gust");
		helper.assertTrue(Math.abs(p.getDeltaMovement().y - WindHandlers.UPDRAFT_SPEED) < 1.0e-3, "the 12-block launch");
		// vanilla airborne physics from 1.5 up: the peak is ~12 blocks
		double y = 0, vy = WindHandlers.UPDRAFT_SPEED, top = 0;
		for (int i = 0; i < 60; i++) {
			y += vy;
			vy = (vy - 0.08) * 0.98;
			top = Math.max(top, y);
		}
		helper.assertTrue(top > 11.5 && top < 12.8, "rises ~12 blocks, " + top);
		p.setOnGround(false);
		tick(p, 17);
		helper.assertTrue(WindHandlers.gliding(p), "the glide opens at the top");
		helper.succeed();
	}

	// ---------------- Z: Tornado / Hurricane ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void tornadoSpinsSixSecondsThenFlings(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		WindTornadoEntity t = WindHandlers.tornado(p);
		assertNear(helper, 100 - WindHandlers.COST_TORNADO, WindHandlers.gust(p), "35 Gust");
		helper.assertTrue(t.mode() == WindTornadoEntity.TRAVEL && t.life() == WindTornadoEntity.TRAVEL_LIFE, "a travelling tornado, 8 s");
		t.discard();
		// a stationary one for the numbers
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 4.5));
		WindTornadoEntity still = new WindTornadoEntity(helper.getLevel(), p, WindTornadoEntity.TRAVEL, at, Vec3.ZERO);
		helper.getLevel().addFreshEntity(still);
		IronGolem g = dummy(helper, 3, 4);
		helper.runAfterDelay(3, () -> helper.assertTrue(still.isSpinning(g), "caught by the funnel"));
		helper.runAfterDelay(WindHandlers.TORNADO_SPIN_TICKS + 8, () -> {
			helper.assertTrue(!still.isSpinning(g), "flung out after 6 s");
			assertNear(helper, 100 - 6 * WindHandlers.TORNADO_DPS, g.getHealth(), "4 a second for 6 s");
			still.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hurricaneFlingsProjectilesAndPushesMobsOut(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie shooter = zombie(helper, 2, 6);
		Zombie inside = zombie(helper, 3, 3);
		shiftPress(p, 4);
		assertNear(helper, 100 - WindHandlers.COST_HURRICANE, WindHandlers.gust(p), "30 Gust");
		assertNear(helper, WindHandlers.HURRICANE_TICKS, res(p, "hurricane"), "5 s");
		Arrow a = arrow(helper, p.position().add(0, 1.2, 3.0), new Vec3(0, 0, -1.6), shooter);
		int turned = WindHandlers.hurricanePulse(p, helper.getLevel());
		helper.assertTrue(turned >= 1 && a.getDeltaMovement().z > 0, "the arrow is flung away, " + a.getDeltaMovement());
		Vec3 out = inside.position().subtract(p.position());
		helper.assertTrue(inside.getDeltaMovement().dot(new Vec3(out.x, 0, out.z)) > 0, "the mob is pushed out");
		tick(p, WindHandlers.HURRICANE_TICKS + 1);
		helper.assertTrue(res(p, "hurricane") < 0.5f, "over after 5 s");
		helper.succeed();
	}

	// ---------------- V: Wind Barrier / Glide ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void windBarrierBlowsBackWhatComesFromTheFront(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie shooter = zombie(helper, 2, 6);
		AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(WindHandlers.barrierUp(p), "hold V: the barrier is up");
		Arrow front = arrow(helper, p.position().add(0, 1.4, 3.0), new Vec3(0, 0, -1.5), shooter);
		Arrow back = arrow(helper, p.position().add(0, 1.4, -2.5), new Vec3(0, 0, 1.5), shooter);
		int turned = WindHandlers.barrierPulse(p);
		helper.assertTrue(turned == 1, "only the one in front, got " + turned);
		helper.assertTrue(front.getDeltaMovement().z > 0 && front.getOwner() == p, "blown back at the shooter");
		helper.assertTrue(back.getDeltaMovement().z > 0 && back.getOwner() == shooter, "the one behind is untouched");
		front.discard();
		back.discard();
		WindHandlers.setGust(p, 100);
		tick(p, 20);
		assertNear(helper, 100 - WindHandlers.COST_BARRIER_PER_SECOND, WindHandlers.gust(p), "6 Gust a second, no refill while held");
		AbilityRouter.handleInput(p, 5, false);
		helper.assertTrue(!WindHandlers.barrierUp(p), "down on release");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void glideTogglesAndIsFree(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setOnGround(false);
		shiftPress(p, 5);
		helper.assertTrue(WindHandlers.gliding(p), "Shift+V opens the glide");
		helper.assertTrue(p.isFallFlying(), "as elytra flight");
		assertNear(helper, 100, WindHandlers.gust(p), "free");
		shiftPress(p, 5);
		helper.assertTrue(!WindHandlers.gliding(p), "and again closes it");
		helper.succeed();
	}

	// ---------------- C: Rideable Tornado / Air Bubble ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void rideableTornadoCarriesAndBowls(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 6);
		helper.assertTrue(p.getVehicle() instanceof WindTornadoEntity t && t.mode() == WindTornadoEntity.RIDE,
				"C summons a tornado and stands you on it");
		WindTornadoEntity t = (WindTornadoEntity) p.getVehicle();
		helper.assertTrue(WindHandlers.riding(p), "the toggle is on");
		tick(p, 20);
		assertNear(helper, 100 - WindHandlers.COST_RIDE_PER_SECOND, WindHandlers.gust(p), "8 Gust a second");
		IronGolem g = dummy(helper, 2, 1);
		g.moveTo(t.getX() + 1.0, t.getY(), t.getZ());
		helper.runAfterDelay(3, () -> {
			assertNear(helper, 100 - WindHandlers.RIDE_BOWL_DAMAGE, g.getHealth(), "bowled over for 6");
			press(p, 6);
			helper.assertTrue(p.getVehicle() == null && t.isRemoved() && !WindHandlers.riding(p), "C again: off, and it blows out");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void airBubbleBreathesAndCleans(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		shiftPress(p, 6);
		assertNear(helper, 100 - WindHandlers.COST_BUBBLE, WindHandlers.gust(p), "20 Gust");
		assertNear(helper, WindHandlers.BUBBLE_TICKS, res(p, "bubble"), "20 s");
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
		p.setAirSupply(0);
		p.setRemainingFireTicks(100);
		net.minecraft.world.entity.AreaEffectCloud gas = new net.minecraft.world.entity.AreaEffectCloud(helper.getLevel(), p.getX() + 1,
				p.getY(), p.getZ());
		gas.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
		helper.getLevel().addFreshEntity(gas);
		tick(p, 10);
		helper.assertTrue(!p.hasEffect(MobEffects.POISON), "harmful effects are cleared");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "good ones stay");
		helper.assertTrue(p.getAirSupply() == p.getMaxAirSupply(), "you can breathe");
		helper.assertTrue(!p.isOnFire(), "fire goes out");
		helper.assertTrue(gas.isRemoved(), "the gas cloud is blown away");
		helper.succeed();
	}

	// ---------------- passives ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void arrowsVeerOffThirtyPercentOfTheTime(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie shooter = zombie(helper, 2, 6);
		helper.assertTrue(Math.abs(WindHandlers.ARROW_VEER_CHANCE - 0.3f) < 1.0e-6, "30%");
		Arrow lucky = arrow(helper, p.position().add(0, 1.2, 4.0), new Vec3(0, 0, -1.5), shooter);
		helper.assertTrue(WindHandlers.considerProjectile(p, lucky, 0.29f), "a roll under 0.3 veers");
		Vec3 v = lucky.getDeltaMovement();
		helper.assertTrue(Math.abs(v.x) > 0.8 && Math.abs(v.length() - 1.5) < 0.01, "turned well aside at the same speed, " + v);
		Arrow straight = arrow(helper, p.position().add(0.5, 1.2, 4.0), new Vec3(0, 0, -1.5), shooter);
		helper.assertTrue(!WindHandlers.considerProjectile(p, straight, 0.3f), "a roll of 0.3 or more does not");
		helper.assertTrue(straight.getDeltaMovement().z < -1.49, "it flies on");
		helper.assertTrue(WindHandlers.projectileChecked(straight), "and is never rolled for again");
		// the passive rolls each incoming projectile exactly once
		Arrow live = arrow(helper, p.position().add(0, 1.2, 4.5), new Vec3(0, 0, -1.5), shooter);
		tick(p, 1);
		helper.assertTrue(WindHandlers.projectileChecked(live), "an incoming arrow gets its roll");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void veerRateIsAboutThirtyPercent(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie shooter = zombie(helper, 2, 6);
		java.util.Random r = new java.util.Random(24);
		int veered = 0;
		for (int i = 0; i < 400; i++) {
			Arrow a = arrow(helper, p.position().add(0, 1.2, 4.0), new Vec3(0, 0, -1.5), shooter);
			if (WindHandlers.considerProjectile(p, a, r.nextFloat())) {
				veered++;
			}
			a.discard();
		}
		helper.assertTrue(veered > 90 && veered < 150, "about 120 of 400, got " + veered);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noFallDamageAndNoCobwebSlow(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		float h = p.getHealth();
		p.fallDistance = 30.0f;
		p.hurt(p.damageSources().fall(), 18.0f);
		helper.assertTrue(p.getHealth() >= h - 0.01f, "no fall damage, took " + (h - p.getHealth()));
		helper.assertTrue(WindHandlers.ignoresWebs(p), "cobwebs do not slow a wind user");
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!WindHandlers.ignoresWebs(plain), "but they do slow everyone else");
		// the mixin path itself: makeStuckInBlock leaves the wind user's motion alone
		p.setDeltaMovement(0.5, 0, 0.5);
		p.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), new Vec3(0.25, 0.05, 0.25));
		helper.assertTrue(p.getDeltaMovement().x > 0.49, "not stuck in the web");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void obtainableAgain(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(com.projecthero.mod.hero.PowerGrants.grantExperimental(p, Powers.byKey(K)), "the serums / triggers grant it");
		helper.assertTrue(Powers.mutations().contains(Powers.byKey(K)), "a mutation (power wheel, random serums)");
		helper.succeed();
	}


	@GameTest(template = EMPTY_STRUCTURE)
	public void xWhileGlidingIsAGustBoost(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.moveTo(p.getX(), p.getY() + 3, p.getZ());
		p.setOnGround(false);
		WindHandlers.startGlide(p);
		helper.assertTrue(p.isFallFlying(), "gliding (elytra flight)");
		WindHandlers.setGust(p, 80f);
		press(p, 3);
		helper.assertTrue(WindHandlers.boosting(p), "v0.16.5: X while gliding is a Gust Boost, not the dash");
		helper.assertTrue(WindHandlers.gust(p) < 0.01f, "it spends all the Gust");
		assertNear(helper, WindHandlers.boostTicks(80f), res(p, "boost"), "pushing longer for more Gust");
		helper.assertTrue(p.getDeltaMovement().length() > 0.7, "a firework kick along the look, got " + p.getDeltaMovement().length());
		WindHandlers.setGust(p, 5f);
		WindHandlers.endGlide(p);
		helper.assertFalse(WindHandlers.boosting(p), "landing / closing the glide ends the boost");
		helper.succeed();
	}
}
