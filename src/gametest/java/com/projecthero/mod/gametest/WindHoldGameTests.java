package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HoldTap;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p24.WindBladeEntity;
import com.projecthero.mod.hero.power.p24.WindFxEntity;
import com.projecthero.mod.hero.power.p24.WindHandlers;
import com.projecthero.mod.hero.power.p24.WindTornadoEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Wind Manipulation hold moves (HoldTap: hold a key 0.6 s): Cyclone Blade (R), Suffocate (G), Sky Step (X),
 * Twin Tornadoes (Z), Eye of the Storm (V) and Downburst (C) -- each one's server effect, its Gust cost and own
 * cooldown -- plus a quick tap still doing the old move and Wind Barrier now being a toggle. The keys are driven through
 * {@link AbilityRouter#dispatchExperimental} with {@link AbilityRouter#serverTick} run every game tick (mock players are
 * not reliably ticked by the server). Dummies are no-AI iron golems (100 HP, no armour: the numbers come through exactly).
 */
public class WindHoldGameTests implements FabricGameTest {
	private static final String K = WindHandlers.KEY;
	/** Released this many ticks after the press: a hold. */
	private static final int HELD = HoldTap.HOLD_TICKS + 2;

	// ---------------- helpers ----------------

	/** A survival mock wind hero at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x4x9 room; ticked every tick. */
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
		// a wall at the far end: the blades break up on it instead of flying on into the neighbouring tests
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, 7), base.offset(2, 4, 7))) {
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
		// the server ticks a mock player only when it made it into the player list: drive it ourselves otherwise
		helper.onEachTick(() -> {
			if (helper.getLevel().getServer().getPlayerList().getPlayer(p.getUUID()) != p) {
				AbilityRouter.serverTick(p);
			}
		});
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void down(ServerPlayer p, int slot) {
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), true);
	}

	private static void up(ServerPlayer p, int slot) {
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), false);
	}

	/** Holds {@code slot} (press now, release {@link #HELD} ticks later), then runs {@code after}. */
	private static void hold(GameTestHelper helper, ServerPlayer p, int slot, Runnable after) {
		down(p, slot);
		helper.runAfterDelay(HELD, () -> {
			up(p, slot);
			after.run();
		});
	}

	/** Taps {@code slot}: press now, release 2 ticks later, then {@code after}. */
	private static void tap(GameTestHelper helper, ServerPlayer p, int slot, Runnable after) {
		down(p, slot);
		helper.runAfterDelay(2, () -> {
			up(p, slot);
			after.run();
		});
	}

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static List<WindBladeEntity> blades(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(WindBladeEntity.class, p.getBoundingBox().inflate(50),
				b -> p.getUUID().equals(b.ownerId())); // not a neighbouring test's
	}

	private static List<WindFxEntity> fx(GameTestHelper helper, ServerPlayer p, int kind) {
		return helper.getLevel().getEntitiesOfClass(WindFxEntity.class, p.getBoundingBox().inflate(40),
				e -> e.kind() == kind && p.getUUID().equals(e.ownerId()));
	}

	/** Gust after spending {@code cost} once, give or take the refill of the ticks since. */
	private static void assertSpent(GameTestHelper helper, ServerPlayer p, float cost, String what) {
		float g = WindHandlers.gust(p);
		helper.assertTrue(g <= 100 - cost + 3.5f && g >= 100 - cost - 0.05f, what + ": " + cost + " Gust spent, meter at " + g);
	}

	// ---------------- registry ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAHoldMove(GameTestHelper helper) {
		for (String id : new String[] { "wind_blade", "vacuum", "tailwind", "tornado", "wind_barrier", "rideable_tornado" }) {
			helper.assertTrue(HoldTap.hasHold(K, id), id + " has a hold move");
		}
		helper.succeed();
	}

	// ---------------- R: a tap is still Wind Blade; the hold is Cyclone Blade ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void tapRStillThrowsAWindBlade(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tap(helper, p, 1, () -> {
			List<WindBladeEntity> bs = blades(helper, p);
			helper.assertTrue(bs.size() == 1 && !bs.get(0).cyclone() && bs.get(0).damage() == WindHandlers.BLADE_DAMAGE,
					"a tap throws the old Wind Blade (12), got " + bs.size());
			assertSpent(helper, p, WindHandlers.COST_BLADE, "Wind Blade");
			helper.assertTrue(WindHandlers.holdCooldown(p, "cyclone") == 0, "no hold cooldown from a tap");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void holdRHurlsACycloneBlade(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem near = dummy(helper, 2, 4);
		IronGolem far = dummy(helper, 2, 6);
		hold(helper, p, 1, () -> {
			List<WindBladeEntity> bs = blades(helper, p);
			helper.assertTrue(bs.size() == 1 && bs.get(0).cyclone(), "one Cyclone Blade, got " + bs.size());
			WindBladeEntity b = bs.get(0);
			helper.assertTrue(b.damage() == WindHandlers.CYCLONE_DAMAGE && Math.abs(b.halfWidth() - 1.5) < 1.0e-6, "22 a creature, 3 wide");
			assertSpent(helper, p, WindHandlers.COST_CYCLONE, "Cyclone Blade");
			helper.assertTrue(WindHandlers.holdCooldown(p, "cyclone") > WindHandlers.CYCLONE_COOLDOWN - 5, "its own 5 s cooldown");
			// a second hold during the cooldown does nothing
			WindHandlers.setGust(p, 100);
			down(p, 1);
		});
		helper.runAfterDelay(2 * HELD + 1, () -> {
			up(p, 1);
			helper.assertTrue(WindHandlers.gust(p) > 99.9f, "a hold during the cooldown spends nothing, " + WindHandlers.gust(p));
			helper.assertTrue(Math.abs(100 - WindHandlers.CYCLONE_DAMAGE - near.getHealth()) < 0.05f, "the first golem takes 22, " + near.getHealth());
			helper.assertTrue(Math.abs(100 - WindHandlers.CYCLONE_DAMAGE - far.getHealth()) < 0.05f, "and the second, " + far.getHealth());
			helper.succeed();
		});
	}

	// ---------------- G hold: Suffocate ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void holdGSuffocatesTheTarget(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5);
		double baseJump = g.getAttributeValue(Attributes.JUMP_STRENGTH);
		double baseSpeed = g.getAttributeValue(Attributes.MOVEMENT_SPEED);
		hold(helper, p, 2, () -> {
			List<WindFxEntity> s = fx(helper, p, WindFxEntity.SUFFOCATE);
			helper.assertTrue(s.size() == 1 && s.get(0).victim() == g, "the air round the golem is stolen");
			helper.assertTrue(fx(helper, p, WindFxEntity.VACUUM).isEmpty(), "not a Vacuum");
			assertSpent(helper, p, WindHandlers.COST_SUFFOCATE, "Suffocate");
			helper.assertTrue(g.getAttributeValue(Attributes.JUMP_STRENGTH) < 1.0e-6, "no jumping");
			helper.assertTrue(g.getAttributeValue(Attributes.MOVEMENT_SPEED) < baseSpeed, "slowed (no sprinting)");
			helper.assertTrue(WindFxEntity.isSuffocated(g), "flagged");
		});
		helper.runAfterDelay(HELD + WindHandlers.SUFFOCATE_TICKS + 6, () -> {
			helper.assertTrue(Math.abs(100 - 5 * WindHandlers.SUFFOCATE_DPS - g.getHealth()) < 0.05f, "3 a second for 5 s, " + g.getHealth());
			helper.assertTrue(fx(helper, p, WindFxEntity.SUFFOCATE).isEmpty(), "over after 5 s");
			helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.JUMP_STRENGTH) - baseJump) < 1.0e-6, "it can jump again");
			helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 1.0e-6, "and run");
			helper.assertTrue(!WindFxEntity.isSuffocated(g), "unflagged");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suffocateOnlySlowsABoss(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem big = dummy(helper, 2, 5);
		big.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); // too big for the wind to carry: boss rules
		big.setHealth(400);
		double baseJump = big.getAttributeValue(Attributes.JUMP_STRENGTH);
		double baseSpeed = big.getAttributeValue(Attributes.MOVEMENT_SPEED);
		WindFxEntity s = WindHandlers.suffocate(p, big);
		helper.assertTrue(s.victim() == big, "caught");
		helper.assertTrue(Math.abs(big.getAttributeValue(Attributes.JUMP_STRENGTH) - baseJump) < 1.0e-6, "a boss keeps its jump");
		helper.assertTrue(big.getAttributeValue(Attributes.MOVEMENT_SPEED) < baseSpeed * 0.7, "but is slowed");
		s.discard();
		helper.assertTrue(Math.abs(big.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 1.0e-6, "until it ends");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suffocateNeedsATarget(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		hold(helper, p, 2, () -> {
			helper.assertTrue(fx(helper, p, WindFxEntity.SUFFOCATE).isEmpty(), "nothing aimed at: nothing happens");
			helper.assertTrue(WindHandlers.gust(p) > 99.9f && WindHandlers.holdCooldown(p, "suffocate") == 0, "nothing spent");
			helper.succeed();
		});
	}

	// ---------------- X hold: Sky Step ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void holdXSkyStepsFiveTimes(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		hold(helper, p, 3, () -> {
			helper.assertTrue(WindHandlers.skyStepping(p), "Sky Step is on");
			helper.assertTrue(WindHandlers.dashDir(p) == null, "not the Tailwind dash");
			helper.assertTrue(WindHandlers.skyJumpsLeft(p) == WindHandlers.SKYSTEP_JUMPS, "5 jumps");
			helper.assertTrue(res(p, "skystep") > WindHandlers.SKYSTEP_TICKS - 5, "10 s");
			assertSpent(helper, p, WindHandlers.COST_SKYSTEP, "Sky Step");
			int rings = fx(helper, p, WindFxEntity.STEP).size();
			// a mid-air jump as the server sees it: off the ground, falling, then thrown up 0.62 in one tick
			double y = p.getY() + 3;
			p.setOnGround(false);
			p.setPos(p.getX(), y, p.getZ());
			ExperimentalPowers.serverTick(p);
			p.setOnGround(false);
			p.setPos(p.getX(), y - 0.1, p.getZ());
			ExperimentalPowers.serverTick(p);
			p.setOnGround(false);
			p.setPos(p.getX(), y - 0.1 + WindHandlers.SKYSTEP_JUMP_SPEED, p.getZ());
			ExperimentalPowers.serverTick(p);
			helper.assertTrue(WindHandlers.skyJumpsLeft(p) == WindHandlers.SKYSTEP_JUMPS - 1, "one jump spent, " + WindHandlers.skyJumpsLeft(p));
			helper.assertTrue(fx(helper, p, WindFxEntity.STEP).size() == rings + 1, "a ring of air under the feet");
			for (int i = 0; i < 6; i++) {
				WindHandlers.skyJump(p);
			}
			helper.assertTrue(WindHandlers.skyJumpsLeft(p) == 0, "never fewer than none");
		});
		helper.runAfterDelay(HELD + WindHandlers.SKYSTEP_TICKS + 4, () -> {
			helper.assertTrue(!WindHandlers.skyStepping(p), "over after 10 s");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tapXStillDashes(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		down(p, 3);
		up(p, 3);
		helper.assertTrue(WindHandlers.dashDir(p) != null && !WindHandlers.skyStepping(p), "a quick tap is still the Tailwind dash");
		helper.succeed();
	}

	// ---------------- Z hold: Twin Tornadoes ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void holdZTwinTornadoesOrbitAndShred(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5); // on the orbit (3.5 ahead)
		hold(helper, p, 4, () -> {
			List<WindTornadoEntity> ts = helper.getLevel().getEntitiesOfClass(WindTornadoEntity.class, p.getBoundingBox().inflate(10),
					t -> t.mode() == WindTornadoEntity.ORBIT && p.getUUID().equals(t.owner()));
			helper.assertTrue(ts.size() == 2, "two tornadoes, got " + ts.size());
			for (WindTornadoEntity t : ts) {
				double d = Math.hypot(t.getX() - p.getX(), t.getZ() - p.getZ());
				helper.assertTrue(Math.abs(d - WindHandlers.TWIN_ORBIT_RADIUS) < 0.6, "circling at 3.5, " + d);
			}
			helper.assertTrue(WindHandlers.twinActive(p), "running");
			assertSpent(helper, p, WindHandlers.COST_TWIN, "Twin Tornadoes");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(WindTornadoEntity.class, p.getBoundingBox().inflate(10),
					t -> t.mode() == WindTornadoEntity.TRAVEL && p.getUUID().equals(t.owner())).isEmpty(), "not the travelling Tornado");
		});
		helper.runAfterDelay(HELD + 40, () -> {
			float lost = 100 - g.getHealth();
			helper.assertTrue(lost >= WindHandlers.TWIN_DPS - 0.05f && Math.abs(lost % WindHandlers.TWIN_DPS) < 0.05f,
					"shredded in 4s, lost " + lost);
		});
		helper.runAfterDelay(HELD + WindHandlers.TWIN_TICKS + 6, () -> {
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(WindTornadoEntity.class, p.getBoundingBox().inflate(30),
					t -> p.getUUID().equals(t.owner())).isEmpty(),
					"both blow out after 8 s");
			helper.assertTrue(!WindHandlers.twinActive(p), "and the timer is done");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void twinTornadoesEndWithTheirOwnerMoves(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		WindTornadoEntity[] ts = WindHandlers.twinTornadoes(p);
		WindHandlers.endHoldMoves(p); // power switch / removal / logout
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(ts[0].isRemoved() && ts[1].isRemoved(), "the funnels blow out");
			helper.succeed();
		});
	}

	// ---------------- V: Wind Barrier is a toggle; the hold is Eye of the Storm ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void tapVTogglesTheBarrier(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tap(helper, p, 5, () -> {
			helper.assertTrue(WindHandlers.barrierUp(p), "a tap raises the barrier");
			helper.assertTrue(!WindHandlers.eyeOfStorm(p), "not the Eye");
			WindHandlers.setGust(p, 100);
		});
		helper.runAfterDelay(2 + 21, () -> {
			helper.assertTrue(WindHandlers.barrierUp(p), "it stays up after the key is let go");
			float g = WindHandlers.gust(p);
			helper.assertTrue(g < 100 - WindHandlers.COST_BARRIER_PER_SECOND + 0.5f && g > 100 - 1.2f * WindHandlers.COST_BARRIER_PER_SECOND,
					"6 Gust a second, no refill, got " + g);
			tap(helper, p, 5, () -> {
				helper.assertTrue(!WindHandlers.barrierUp(p), "the next tap drops it");
				// and it drops on its own when Gust runs out
				tap(helper, p, 5, () -> {
					helper.assertTrue(WindHandlers.barrierUp(p), "up again");
					WindHandlers.setGust(p, 0.1f);
					helper.runAfterDelay(2, () -> {
						helper.assertTrue(!WindHandlers.barrierUp(p), "out of Gust: down");
						helper.succeed();
					});
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
	public void holdVEyeOfTheStormStopsProjectiles(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie shooter = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 6));
		shooter.setNoAi(true);
		hold(helper, p, 5, () -> {
			helper.assertTrue(WindHandlers.eyeOfStorm(p), "the Eye is up");
			helper.assertTrue(!WindHandlers.barrierUp(p), "the hold does not toggle the barrier");
			assertSpent(helper, p, WindHandlers.COST_EYE, "Eye of the Storm");
			Arrow a = new Arrow(EntityType.ARROW, helper.getLevel());
			a.setPos(p.getX(), p.getY() + 1.2, p.getZ() + 3.0);
			a.setOwner(shooter);
			a.setDeltaMovement(0, 0, -1.6);
			helper.getLevel().addFreshEntity(a);
			int stopped = WindHandlers.eyePulse(p, helper.getLevel());
			helper.assertTrue(stopped >= 1 && a.getDeltaMovement().horizontalDistance() < 1.0e-6, "the arrow stops dead, " + a.getDeltaMovement());
			Vec3 out = shooter.position().subtract(p.position());
			helper.assertTrue(shooter.getDeltaMovement().dot(new Vec3(out.x, 0, out.z)) > 0, "the mob is pushed out");
		});
		helper.runAfterDelay(HELD + WindHandlers.EYE_TICKS + 4, () -> {
			helper.assertTrue(!WindHandlers.eyeOfStorm(p), "over after 8 s");
			helper.succeed();
		});
	}

	// ---------------- C hold: Downburst ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void holdCDownburstSlamsTheTarget(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5);
		IronGolem far = dummy(helper, 4, 1); // beside the hero, outside the 6-block circle
		hold(helper, p, 6, () -> {
			helper.assertTrue(p.getVehicle() == null && !WindHandlers.riding(p), "not the rideable tornado");
			List<WindFxEntity> d = fx(helper, p, WindFxEntity.DOWNBURST);
			helper.assertTrue(d.size() == 1, "a downburst comes down");
			helper.assertTrue(Math.hypot(d.get(0).getX() - g.getX(), d.get(0).getZ() - g.getZ()) < 0.3, "on the golem");
			assertSpent(helper, p, WindHandlers.COST_DOWNBURST, "Downburst");
		});
		helper.runAfterDelay(HELD + WindFxEntity.DOWNBURST_DROP_TICKS + 3, () -> {
			helper.assertTrue(Math.abs(100 - WindHandlers.DOWNBURST_DAMAGE - g.getHealth()) < 0.05f, "18 damage, " + g.getHealth());
			helper.assertTrue(far.getHealth() >= 100 - 0.01f, "nothing outside it");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tapCStillRidesATornado(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		down(p, 6);
		up(p, 6);
		helper.assertTrue(p.getVehicle() instanceof WindTornadoEntity t && t.mode() == WindTornadoEntity.RIDE, "a tap still rides");
		helper.assertTrue(fx(helper, p, WindFxEntity.DOWNBURST).isEmpty(), "no downburst");
		WindHandlers.endRide(p);
		helper.succeed();
	}
}
