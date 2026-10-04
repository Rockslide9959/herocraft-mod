package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SpeedLeaves;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedMoves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * v0.14.21 Super Speed: Speed Sweep goes home the moment its last target is done (no hop-timer wait, no 5 s freeze on
 * an unreachable stray), and Speed Mode / Overdrive run through leaves (but leaves still hold you up from above).
 * Mock players are not reliably ticked, so the sweep is driven tick by tick and asserted on step counts, not clocks.
 * Each sweep test has a batch of its own and clears every other mob in range first, so the 50-block snapshot holds
 * only the test's own zombies.
 */
public class SpeedV01421GameTests implements FabricGameTest {

	/** A survival speedster at (2.5, 2, 1.5) facing +Z, in a cleared 7 x 4 x 7 room with a stone floor. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(6, 5, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(6, 1, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 37.0f, 12.0f);
		Power power = power();
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static Power power() {
		return Powers.byKey(SuperSpeedHandlers.KEY);
	}

	/** Every other mob within the sweep's reach goes (this test runs alone in its batch). */
	private static void clearStrays(GameTestHelper helper, ServerPlayer p) {
		for (Mob m : helper.getLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(SuperSpeedMoves.SWEEP_RANGE + 12))) {
			m.discard();
		}
	}

	/** A no-AI, armourless 100 HP zombie at relative {@code (x, 2, z)}. */
	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		m.setHealth(100);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void sweep(ServerPlayer p) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 3, true); // Shift+X
		p.setShiftKeyDown(false);
	}

	/** Ticks the sweep until it ends (at most {@code max} ticks); returns how many ticks that took. */
	private static int ticksToFinish(ServerPlayer p, int max) {
		int ticks = 0;
		while (SuperSpeedMoves.sweeping(p) && ticks < max) {
			SuperSpeedMoves.sweepTick(p);
			ticks++;
		}
		return ticks;
	}

	private static void assertHome(GameTestHelper helper, ServerPlayer p, Vec3 home) {
		helper.assertTrue(p.position().distanceTo(home) < 0.01, "back exactly where it started (" + p.position() + " vs " + home + ")");
		helper.assertTrue(Math.abs(p.getYRot() - 37f) < 0.01f && Math.abs(p.getXRot() - 12f) < 0.01f, "facing the same way");
	}

	// ---- Super Speed is an obtainable mutation; nothing else was re-enabled ----------------------------------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlySuperSpeedChangedTierAndItIsObtainable(GameTestHelper helper) {
		Power speed = power();
		java.util.Set<String> mutations = new java.util.HashSet<>();
		for (Power p : Powers.mutations()) {
			mutations.add(p.key());
		}
		helper.assertTrue(mutations.equals(java.util.Set.of("power_01_super_strength", "power_02_laser_vision",
				"power_04_super_speed", "power_12_super_regeneration")), "exactly the four remade mutations: " + mutations);
		helper.assertTrue(Powers.isEnabled(speed) && Powers.isMutation(speed) && !Powers.isHeroTier(speed), "Super Speed: enabled mutation");
		for (Power p : Powers.all()) {
			if (!mutations.contains(p.key())) {
				helper.assertFalse(p.enabled() || Powers.isMutation(p), p.key() + " must stay disabled");
			}
		}
		ServerPlayer q = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(com.projecthero.mod.hero.PowerGrants.grantExperimental(q, Powers.byKey("power_03_flight")),
				"a disabled mutation is still never granted");
		// obtainable: random-serum roll pool, a mutation grant, brewing and the reagent recipe
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(com.projecthero.mod.hero.PowerGrants.missingExperimental(p).contains(speed), "random serums can roll it");
		helper.assertTrue(com.projecthero.mod.hero.PowerGrants.grantExperimental(p, speed), "a mutation grant gives it");
		helper.assertTrue(ExperimentalPowers.owns(p, speed), "owned");
		var brewing = helper.getLevel().potionBrewing();
		var base = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
				com.projecthero.mod.hero.mutation.ModSerums.basePotion(speed.serum().basePotion()));
		var reagent = new net.minecraft.world.item.ItemStack(com.projecthero.mod.hero.item.HeroPackItems.reagent(speed));
		helper.assertTrue(brewing.hasMix(base, reagent), "its serum brews");
		String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(com.projecthero.mod.hero.item.HeroPackItems.reagent(speed)).getPath();
		helper.assertTrue(helper.getLevel().getRecipeManager()
				.byKey(com.projecthero.mod.ProjectHeroMod.id(path)).isPresent(), "its reagent recipe loads");
		helper.succeed();
	}

	// ---- Speed Sweep: home at once ---------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01421_sweep_last_hit")
	public void sweepGoesHomeTheTickAfterTheLastHit(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		clearStrays(helper, p);
		Vec3 home = p.position();
		Zombie only = zombie(helper, 4.5, 4.5);
		sweep(p);
		helper.assertTrue(SuperSpeedMoves.sweepHit(p, only), "the first hop hits the only target at once");
		helper.assertTrue(SuperSpeedMoves.sweeping(p) && SuperSpeedMoves.sweepPending(p) == 0, "nothing left to hit");
		int ticks = ticksToFinish(p, 10);
		helper.assertFalse(SuperSpeedMoves.sweeping(p), "the sweep ends");
		helper.assertTrue(ticks == 1, "one tick after the last hit, not a hop later (took " + ticks + ")");
		assertHome(helper, p, home);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01421_sweep_killed")
	public void sweepGoesHomeAtOnceWhenTheRestDieOrVanish(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		clearStrays(helper, p);
		Vec3 home = p.position();
		Zombie[] all = { zombie(helper, 0.8, 4.5), zombie(helper, 4.5, 4.5), zombie(helper, 5.2, 1.5) };
		sweep(p);
		helper.assertTrue(SuperSpeedMoves.sweeping(p) && SuperSpeedMoves.sweepPending(p) == 2, "one hit, two still to go ("
				+ SuperSpeedMoves.sweepPending(p) + ")");
		boolean killed = false;
		for (Zombie z : all) {
			if (SuperSpeedMoves.sweepHit(p, z)) {
				continue;
			}
			if (!killed) {
				z.kill(); // dies to something else
				killed = true;
			} else {
				z.discard(); // despawns
			}
		}
		int ticks = ticksToFinish(p, 10);
		helper.assertFalse(SuperSpeedMoves.sweeping(p), "the sweep ends");
		helper.assertTrue(ticks == 1, "on the very next tick (took " + ticks + ")");
		assertHome(helper, p, home);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01421_sweep_unreachable")
	public void anUnreachableStrayNoLongerHoldsTheSweepFor5s(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		clearStrays(helper, p);
		Vec3 home = p.position();
		ServerLevel level = helper.getLevel();
		// a zombie sealed in a stone box: no free spot beside it anywhere (it used to freeze the sweep for 5 s)
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(3, 2, 3)), helper.absolutePos(new BlockPos(5, 4, 5)))) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		level.setBlock(helper.absolutePos(new BlockPos(4, 2, 4)), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(helper.absolutePos(new BlockPos(4, 3, 4)), Blocks.AIR.defaultBlockState(), 2);
		Zombie sealed = zombie(helper, 4.5, 4.5);
		Zombie open = zombie(helper, 0.8, 4.5);
		sweep(p);
		helper.assertTrue(SuperSpeedMoves.sweepHit(p, open), "the reachable one is hit first");
		int ticks = ticksToFinish(p, SuperSpeedMoves.SWEEP_UNREACHABLE_TICKS + 20);
		helper.assertFalse(SuperSpeedMoves.sweeping(p), "the sweep ends");
		helper.assertTrue(ticks <= SuperSpeedMoves.SWEEP_HOP_TICKS + 1, "within a hop, not after the 5 s timeout (took " + ticks + ")");
		helper.assertTrue(sealed.getHealth() == 100f, "the sealed one was never reached");
		assertHome(helper, p, home);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sweepTargetsKnowWhenNothingReachableIsLeft(GameTestHelper helper) {
		SuperSpeedMoves.SweepTargets t = new SuperSpeedMoves.SweepTargets(java.util.List.of(1, 2, 3));
		helper.assertFalse(t.allUnreachable(), "nothing tried yet");
		t.unreachable(1, 4);
		helper.assertTrue(t.isUnreachable(1) && !t.isUnreachable(2), "only 1 is marked");
		helper.assertFalse(t.allUnreachable(), "2 and 3 are still worth a try");
		t.hit(2);
		t.unreachable(3, 6);
		helper.assertTrue(t.allUnreachable(), "everything left has nowhere to land: go home");
		t.drop(1);
		t.drop(3);
		helper.assertFalse(t.allUnreachable(), "an empty list is 'done', not 'unreachable'");
		helper.assertTrue(t.done(), "done");
		helper.succeed();
	}

	// ---- Speed Mode / Overdrive: through leaves --------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01421_leaves")
	public void speedModeRunsThroughLeavesButNotFromAbove(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ServerLevel level = helper.getLevel();
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		BlockPos feet = BlockPos.containing(p.position());
		BlockPos ahead = feet.offset(0, 0, 1);
		level.setBlock(ahead, leaves, 2);
		level.setBlock(ahead.above(), leaves, 2);
		var into = p.getBoundingBox().move(0, 0, 1.0);

		helper.assertFalse(SpeedLeaves.active(p), "precondition: no Speed Mode");
		helper.assertFalse(leaves.getCollisionShape(level, ahead, CollisionContext.of(p)).isEmpty(), "leaves are solid without it");
		helper.assertFalse(level.noCollision(p, into), "and block the way");

		AbilityRouter.handleInput(p, 6, true); // C: Speed Mode
		AbilityRouter.handleInput(p, 6, false);
		helper.assertTrue(SuperSpeedHandlers.speedMode(p) && SpeedLeaves.active(p), "Speed Mode on");
		CollisionContext ctx = CollisionContext.of(p);
		helper.assertTrue(leaves.getCollisionShape(level, ahead, ctx).isEmpty(), "leaves at body height: passable");
		helper.assertTrue(leaves.getCollisionShape(level, ahead.above(), ctx).isEmpty(), "and at head height");
		helper.assertTrue(leaves.getCollisionShape(level, feet.above(2), ctx).isEmpty(), "and above you (jump up through a canopy)");
		helper.assertTrue(level.noCollision(p, into), "so you run straight into them");
		helper.assertFalse(leaves.getCollisionShape(level, feet.below(), ctx).isEmpty(), "but a leaf block under your feet holds you up");
		helper.assertFalse(Blocks.STONE.defaultBlockState().getCollisionShape(level, ahead, ctx).isEmpty(), "only leaves -- stone is stone");
		Zombie z = zombie(helper, 2.5, 4.5);
		helper.assertFalse(leaves.getCollisionShape(level, ahead, CollisionContext.of(z)).isEmpty(), "everyone else still bumps into them");
		helper.assertFalse(leaves.getCollisionShape(level, ahead, CollisionContext.empty()).isEmpty(), "the context-free shape is untouched");
		helper.assertTrue(level.getBlockState(ahead).is(Blocks.OAK_LEAVES), "and nothing is broken");

		AbilityRouter.handleInput(p, 6, true); // C again: Speed Mode off
		AbilityRouter.handleInput(p, 6, false);
		helper.assertFalse(SuperSpeedHandlers.speedMode(p), "Speed Mode off");
		helper.assertFalse(leaves.getCollisionShape(level, ahead, CollisionContext.of(p)).isEmpty(), "solid again");

		AbilityRouter.handleInput(p, 5, true); // V: Overdrive
		AbilityRouter.handleInput(p, 5, false);
		helper.assertTrue(SuperSpeedHandlers.overdrive(p), "Overdrive on");
		helper.assertTrue(leaves.getCollisionShape(level, ahead, CollisionContext.of(p)).isEmpty(), "Overdrive runs through them too");
		helper.succeed();
	}
}
