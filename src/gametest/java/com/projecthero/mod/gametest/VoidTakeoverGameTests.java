package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryAbilityManager;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.ShameRooms;
import com.projecthero.mod.sentry.VoidMatter;
import com.projecthero.mod.sentry.VoidMatterData;
import com.projecthero.mod.sentry.VoidTakeover;
import com.projecthero.mod.sentry.block.SentryBlocks;
import com.projecthero.mod.sentry.network.ShameRoomPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.monster.Zombie;

/**
 * v0.17.5 the Void's takeover: it begins at 100 Void (not for creative players), the body attacks a nearby mob and may hit any player
 * (a squadmate too), the keys are dead, eaten blocks are recorded (never bedrock or a chest) and come back after it ends, the
 * {@code projectheroVoidGriefing} gamerule makes it permanent, the Shame Rooms are server-authoritative (nothing for the first 30 s,
 * too-quick wins refused, a loss sends you back a room and adds 20 s, three wins throw the Void out), outside help drains the grip,
 * and death ends it at 60 Void. Rework 2: blocks fade (the decay state) before they go, the destruction grows a fixed step a second,
 * the body rises toward its hover height, comes down only for a strong target (a weak mob is consumed instead), decays its way out
 * of a stone cell, and a whole carved patch comes back. Mock players from other tests may linger, so nothing asserts "no player near".
 */
public class VoidTakeoverGameTests implements FabricGameTest {
	private static ServerPlayer takenOver(GameTestHelper helper, double x, double z) {
		ServerPlayer p = SentryGameTests.sentry(helper, x, z);
		Sentry.setVoid(p, SentryConfig.VOID_MAX);
		helper.assertTrue(Sentry.inTakeover(p), "100 Void: the Void takes over");
		p.addTag(VoidTakeover.TEST_NO_DISC); // parallel tests share the world: no disc eating their blocks
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takeoverBeginsAtOneHundred(GameTestHelper helper) {
		ServerPlayer p = SentryGameTests.sentry(helper, 2, 2);
		Sentry.setVoid(p, 99f);
		helper.assertFalse(Sentry.inTakeover(p), "not at 99");
		Sentry.addVoid(p, 2f);
		helper.assertTrue(Sentry.inTakeover(p), "at 100 it does");
		helper.assertTrue(Sentry.voidForm(p) && Sentry.isFlying(p), "the Void Form, flying");
		// the keys are dead
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(Sentry.inTakeover(p), "keys do nothing");
		VoidTakeover.end(p, VoidTakeover.End.COMMAND);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void creativeIsNeverTaken(GameTestHelper helper) {
		ServerPlayer p = SentryGameTests.sentry(helper, 2, 2);
		p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		Sentry.setVoid(p, SentryConfig.VOID_MAX);
		helper.assertFalse(Sentry.inTakeover(p), "creative: no takeover");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBodyAttacksANearbyMob(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		Zombie z = SentryGameTests.zombie(helper, 4, 2, 500);
		final int[] ticks = { 0 };
		helper.onEachTick(() -> {
			if (Sentry.inTakeover(p)) {
				VoidTakeover.tick(p);
			}
			ticks[0]++;
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(z.getHealth() < 500f, "the Void attacked the zombie (health " + z.getHealth() + ")");
			helper.assertTrue(VoidTakeover.hits(p) > 0, "it landed hits");
			helper.assertTrue(VoidTakeover.lastMove(p) != null, "it used its kit");
			VoidTakeover.end(p, VoidTakeover.End.COMMAND);
			helper.succeed();
		});
	}

	/**
	 * Moveset 2 (user: "when the void takes control only make him attack once every 2 seconds with an ability or an M1"): one shared
	 * cooldown for every attack. The body is ticked by the server and again here (a second tick in the same game tick must not attack
	 * again); asserted on the steps between attacks, never on a total.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void theVoidAttacksAtMostOnceEveryTwoSeconds(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		Zombie z = SentryGameTests.zombie(helper, 3.5, 2, 1000);
		helper.onEachTick(() -> {
			if (Sentry.inTakeover(p)) {
				VoidTakeover.tick(p);
				VoidTakeover.tick(p);
			}
		});
		helper.runAfterDelay(200, () -> {
			java.util.List<Long> at = VoidTakeover.attackTimes(p);
			helper.assertTrue(at.size() >= 3, "it kept attacking (" + at.size() + " attacks)");
			for (int i = 1; i < at.size(); i++) {
				long step = at.get(i) - at.get(i - 1);
				helper.assertTrue(step >= SentryConfig.TAKEOVER_ATTACK_INTERVAL, "attack " + i + " came only " + step + " ticks after the last");
			}
			helper.assertTrue(z.getHealth() < 1000f, "and it hurt the zombie");
			VoidTakeover.end(p, VoidTakeover.End.COMMAND);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theVoidMayHitAnyoneEvenASquadmate(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		ServerPlayer friend = SentryGameTests.mock(helper, 4, 4);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("Void" + helper.getLevel().getGameTime() + p.getId(), p.getUUID());
		squads.addMember(squad, friend.getUUID());
		helper.assertTrue(VoidTakeover.canHit(p, friend), "a squadmate is fair game to the Void");
		helper.assertFalse(com.projecthero.mod.squad.Squads.areAllies(p, friend), "the Void is nobody's ally");
		helper.assertFalse(VoidTakeover.canHit(p, p), "never the body itself");
		squads.disband(squad);
		VoidTakeover.end(p, VoidTakeover.End.COMMAND);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the world it eats

	@GameTest(template = EMPTY_STRUCTURE)
	public void blocksFadeToBlackBeforeTheyGoAndComeBack(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		ServerLevel level = helper.getLevel();
		BlockPos stone = helper.absolutePos(new BlockPos(5, 1, 5));
		BlockPos chest = helper.absolutePos(new BlockPos(5, 1, 6));
		BlockPos bedrock = helper.absolutePos(new BlockPos(5, 1, 4));
		BlockPos barrier = helper.absolutePos(new BlockPos(5, 1, 3));
		level.setBlockAndUpdate(stone, Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
		level.setBlockAndUpdate(barrier, Blocks.BARRIER.defaultBlockState());
		helper.assertTrue(VoidMatter.eat(level, p, stone), "stone bricks are eaten");
		helper.assertTrue(level.getBlockState(stone).is(Blocks.STONE_BRICKS), "still there while it fades to black");
		helper.assertTrue(VoidMatter.decaying(level, stone), "in the decay state");
		helper.assertFalse(VoidMatter.eat(level, p, stone), "a fading block isn't started twice");
		helper.assertTrue(VoidMatterData.get(level).has(stone), "the original is recorded");
		helper.assertTrue(VoidMatterData.get(level).entry(stone).state.is(Blocks.STONE_BRICKS), "as stone bricks");
		helper.assertFalse(VoidMatter.eat(level, p, chest), "never a chest");
		helper.assertFalse(VoidMatter.eat(level, p, bedrock), "never bedrock");
		helper.assertFalse(VoidMatter.eat(level, p, barrier), "never a barrier");
		int fade = VoidMatter.decayTicks(stone.asLong(), SentryConfig.DECAY_TICKS);
		helper.assertTrue(fade >= SentryConfig.DECAY_TICKS && fade <= SentryConfig.DECAY_TICKS * 5 / 4 + 1, "fade time by position: " + fade);
		helper.runAfterDelay(fade / 2, () -> {
			helper.assertTrue(level.getBlockState(stone).is(Blocks.STONE_BRICKS), "half way: still fading");
		});
		helper.runAfterDelay(fade + 4, () -> {
			helper.assertTrue(level.getBlockState(stone).isAir(), "the fade is over: gone");
			helper.assertFalse(VoidMatter.decaying(level, stone), "out of the decay state");
			VoidTakeover.end(p, VoidTakeover.End.WON);
			VoidMatterData.Entry e = VoidMatterData.get(level).entry(stone);
			helper.assertTrue(e != null && e.dueAt >= level.getGameTime() + SentryConfig.RESTORE_DELAY_TICKS - 1, "due back in about a minute");
			int back = VoidMatter.restoreNow(level, p.getUUID());
			helper.assertTrue(back >= 1, "restored");
			helper.assertTrue(level.getBlockState(stone).is(Blocks.STONE_BRICKS), "the stone bricks are back");
			helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "the chest never left");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aWholePatchComesBack(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 1, 1);
		ServerLevel level = helper.getLevel();
		java.util.List<BlockPos> patch = new java.util.ArrayList<>();
		java.util.List<net.minecraft.world.level.block.Block> was = new java.util.ArrayList<>();
		for (int x = 3; x <= 5; x++) {
			for (int z = 3; z <= 5; z++) {
				BlockPos at = helper.absolutePos(new BlockPos(x, 1, z));
				net.minecraft.world.level.block.Block block = (x + z) % 2 == 0 ? Blocks.DIRT : Blocks.OAK_PLANKS;
				level.setBlockAndUpdate(at, block.defaultBlockState());
				patch.add(at);
				was.add(block);
				helper.assertTrue(VoidMatter.startDecay(level, p, at, true), "carved");
			}
		}
		helper.runAfterDelay(SentryConfig.CARVE_DECAY_TICKS * 5 / 4 + 4, () -> {
			for (BlockPos at : patch) {
				helper.assertTrue(level.getBlockState(at).isAir(), "carved away " + at);
			}
			VoidTakeover.end(p, VoidTakeover.End.COMMAND);
			VoidMatter.restoreNow(level, p.getUUID());
			for (int i = 0; i < patch.size(); i++) {
				helper.assertTrue(level.getBlockState(patch.get(i)).is(was.get(i)), "back as it was " + patch.get(i));
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theDestructionGrowsStepByStep(GameTestHelper helper) {
		double step = SentryConfig.MATTER_GROWTH_PER_SECOND;
		helper.assertTrue(Math.abs(VoidMatter.radius(0) - SentryConfig.MATTER_START_RADIUS) < 1.0e-9, "starts at " + SentryConfig.MATTER_START_RADIUS);
		helper.assertTrue(SentryConfig.MATTER_START_RADIUS >= 8.0, "starts larger than before (3)");
		for (long t = 0; t < 1200; t += 200) {
			double grew = VoidMatter.radius(t + 20) - VoidMatter.radius(t);
			helper.assertTrue(Math.abs(grew - step) < 1.0e-9, "a second later it is " + step + " wider (" + grew + " at " + t + ")");
		}
		long capAt = (long) Math.ceil((SentryConfig.MATTER_MAX_RADIUS - SentryConfig.MATTER_START_RADIUS) / step * 20.0);
		helper.assertTrue(VoidMatter.radius(capAt - 20) < SentryConfig.MATTER_MAX_RADIUS, "still growing a second before the cap");
		helper.assertTrue(VoidMatter.radius(capAt + 2000) == SentryConfig.MATTER_MAX_RADIUS, "capped at " + SentryConfig.MATTER_MAX_RADIUS);
		helper.assertTrue(VoidMatter.depthAt(0, 30) == SentryConfig.MATTER_DEPTH_CENTRE, "deepest under the centre");
		helper.assertTrue(VoidMatter.depthAt(30, 30) == SentryConfig.MATTER_DEPTH_EDGE, "shallow at the edge");
		helper.assertTrue(VoidMatter.depthAt(10, 30) >= VoidMatter.depthAt(20, 30), "a bowl");
		helper.succeed();
	}

	// ---------------------------------------------------------------- hovering, coming down, never stuck

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBodyRisesTowardItsHoverHeight(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2.5, 2.5);
		ServerLevel level = helper.getLevel();
		VoidTakeover.hoverTick(p); // (strong mobs of neighbouring tests may be in range: drive the hover itself)
		helper.assertTrue(VoidTakeover.mode(p) == VoidTakeover.Mode.HOVER, "hovering");
		double hover = VoidTakeover.hoverY(p);
		helper.assertTrue(hover > p.getY() + 10.0, "the hover height is far above: " + hover + " vs " + p.getY());
		helper.assertTrue(hover <= level.getMaxBuildHeight() - 3.0, "under the build height");
		double vy = p.getDeltaMovement().y;
		helper.assertTrue(vy > 0.0 && vy <= SentryConfig.TAKEOVER_RISE_SPEED + 1.0e-6, "it rises, at most the rise speed a tick: " + vy);
		VoidTakeover.hoverTick(p);
		double vy2 = p.getDeltaMovement().y;
		helper.assertTrue(Math.abs(vy2 - vy) < 0.05, "smoothly (step " + (vy2 - vy) + ")");
		VoidTakeover.end(p, VoidTakeover.End.COMMAND);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void itComesDownForAStrongTarget(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2.5, 2.5);
		p.teleportTo(p.getX(), p.getY() + 4.0, p.getZ());
		Zombie strong = SentryGameTests.zombie(helper, 6.5, 6.5, 500);
		helper.assertTrue(VoidTakeover.isStrong(strong), "500 health is strong");
		VoidTakeover.tick(p);
		helper.assertTrue(VoidTakeover.mode(p) == VoidTakeover.Mode.ENGAGE, "it engages the strong one");
		helper.assertTrue(Sentry.state(p).takeoverTarget == strong.getId(), "targeting it");
		double vy = p.getDeltaMovement().y;
		helper.assertTrue(vy < 0.0 && vy >= -SentryConfig.TAKEOVER_DESCEND_SPEED - 1.0e-6, "drifting down, slowly: " + vy);
		VoidTakeover.end(p, VoidTakeover.End.COMMAND);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void itStaysUpForAWeakMob(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2.5, 2.5);
		p.teleportTo(p.getX(), p.getY() + 4.0, p.getZ());
		Zombie weak = SentryGameTests.zombie(helper, 6.5, 6.5, 20);
		helper.assertFalse(VoidTakeover.isStrong(weak), "a plain zombie is weak");
		VoidTakeover.tick(p);
		helper.assertTrue(Sentry.state(p).takeoverTarget != weak.getId(), "not worth coming down for");
		if (VoidTakeover.mode(p) == VoidTakeover.Mode.HOVER) {
			helper.assertTrue(p.getDeltaMovement().y > 0.0, "it keeps rising to watch");
		}
		// the spreading destruction gets it instead
		float before = weak.getHealth();
		VoidTakeover.consumeWeak(p, 8.0);
		helper.assertTrue(weak.getHealth() < before || !weak.isAlive(), "consumed by the destruction (" + weak.getHealth() + ")");
		VoidTakeover.end(p, VoidTakeover.End.COMMAND);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aBlockedPathIsDecayedNotStalled(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 3.5, 3.5);
		ServerLevel level = helper.getLevel();
		// a stone cell around the body (the mock body never moves on its own: it stays "stuck")
		java.util.List<BlockPos> cell = new java.util.ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 2; y <= 4; y++) {
					if (dx == 0 && dz == 0 && y < 4) {
						continue; // the body
					}
					BlockPos at = helper.absolutePos(new BlockPos(3 + dx, y, 3 + dz));
					level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
					cell.add(at);
				}
			}
		}
		Zombie strong = SentryGameTests.zombie(helper, 6.5, 6.5, 500);
		helper.onEachTick(() -> {
			if (Sentry.inTakeover(p)) {
				VoidTakeover.tick(p);
			}
		});
		helper.runAfterDelay(SentryConfig.TAKEOVER_STUCK_TICKS + SentryConfig.CARVE_DECAY_TICKS * 2 + 10, () -> {
			int opened = 0;
			for (BlockPos at : cell) {
				if (level.getBlockState(at).isAir() || VoidMatter.decaying(level, at)) {
					opened++;
				}
			}
			helper.assertTrue(opened > 0, "it decayed its way out of the cell (" + opened + " of " + cell.size() + ")");
			helper.assertTrue(VoidTakeover.unsticks(p) > 0 || opened >= 2, "the unstick fired (" + VoidTakeover.unsticks(p) + ")");
			for (BlockPos at : cell) {
				helper.assertTrue(VoidMatterData.get(level).has(at) || level.getBlockState(at).is(Blocks.STONE), "every carved block is recorded");
			}
			VoidTakeover.end(p, VoidTakeover.End.COMMAND);
			strong.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void griefingGameruleMakesItPermanent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules.BooleanValue rule = level.getGameRules().getRule(VoidMatter.RULE);
		boolean was = rule.get();
		rule.set(true, level.getServer());
		try {
			ServerPlayer p = takenOver(helper, 2, 2);
			BlockPos stone = helper.absolutePos(new BlockPos(5, 1, 5));
			level.setBlockAndUpdate(stone, Blocks.STONE_BRICKS.defaultBlockState());
			helper.assertTrue(VoidMatter.eat(level, p, stone), "eaten");
			helper.assertTrue(VoidMatter.decaying(level, stone), "fading all the same");
			helper.assertFalse(VoidMatterData.get(level).has(stone), "nothing recorded under the gamerule");
			VoidTakeover.end(p, VoidTakeover.End.WON);
			helper.assertTrue(VoidMatter.restoreNow(level, p.getUUID()) == 0, "nothing to bring back");
		} finally {
			rule.set(was, level.getServer());
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Shame Rooms

	@GameTest(template = EMPTY_STRUCTURE)
	public void shameRoomsAreServerAuthoritative(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(ShameRooms.report(p, new ShameRoomPayload(0, true, 20)) == ShameRooms.Result.IGNORED, "nothing for the first 30 s");
		set(p, s -> {
			s.mindLockedUntil = now - 200;
			s.roomStartedAt = now - 200;
		});
		helper.assertTrue(ShameRooms.report(p, new ShameRoomPayload(1, true, 20)) == ShameRooms.Result.IGNORED, "only the room he is in");
		helper.assertTrue(ShameRooms.report(p, new ShameRoomPayload(0, true, 2)) == ShameRooms.Result.TOO_FAST, "two lights can't fill the bar");
		helper.assertTrue(ShameRooms.report(p, new ShameRoomPayload(0, true, 20)) == ShameRooms.Result.ADVANCED, "room 1 cleared");
		helper.assertTrue(ShameRooms.report(p, new ShameRoomPayload(1, true, 20)) == ShameRooms.Result.TOO_FAST, "room 2 can't be won at once");
		helper.assertTrue(ShameRooms.report(p, new ShameRoomPayload(1, false, 3)) == ShameRooms.Result.FAILED, "room 2 failed");
		helper.assertTrue(Sentry.state(p).room == 0, "back a room");
		helper.assertTrue(Sentry.state(p).mindLockedUntil - now == SentryConfig.ROOM_FAIL_TICKS, "the mind shut for 20 s more (step)");
		// win all three
		for (int r = 0; r < SentryConfig.ROOMS; r++) {
			final int room = r;
			set(p, s -> {
				s.mindLockedUntil = now - 200;
				s.roomStartedAt = now - 200;
				s.room = room;
			});
			ShameRooms.report(p, new ShameRoomPayload(r, true, 20));
		}
		helper.assertFalse(Sentry.inTakeover(p), "three rooms: the Void is thrown out");
		helper.assertTrue(Sentry.state(p).voidLevel == SentryConfig.VOID_AFTER_WIN, "Void 40");
		helper.assertTrue(Sentry.state(p).letOutLockedUntil - now == SentryConfig.LET_OUT_LOCK_TICKS, "Shift+V locked 5 minutes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void helpFromOutsideDrainsTheGrip(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		ServerPlayer friend = SentryGameTests.mock(helper, 3, 3);
		friend.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
		float before = Sentry.state(p).grip;
		helper.assertTrue(VoidTakeover.help(friend, p), "an empty hand reaches him");
		float step = before - Sentry.state(p).grip;
		helper.assertTrue(step > 0f && step <= SentryConfig.HELP_PER_CLICK * 1.2f + 1.0e-3f, "one click drains the grip a step: " + step);
		friend.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
		helper.assertFalse(VoidTakeover.help(friend, p), "not with something in hand");
		set(p, s -> s.grip = 0.5f);
		friend.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
		VoidTakeover.help(friend, p);
		helper.assertTrue(Sentry.state(p).room == 1, "the grip broke: a room breaks open");
		VoidTakeover.end(p, VoidTakeover.End.COMMAND);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deathEndsTheTakeover(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		p.kill();
		helper.assertFalse(Sentry.inTakeover(p), "dead: the Void lets go");
		helper.assertTrue(Sentry.state(p).voidLevel == SentryConfig.VOID_AFTER_DEATH, "Void 60");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void loggingOutEndsItAndTheWorldComesBack(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		ServerLevel level = helper.getLevel();
		BlockPos stone = helper.absolutePos(new BlockPos(6, 1, 6));
		level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
		VoidMatter.eat(level, p, stone);
		VoidTakeover.end(p, VoidTakeover.End.LOGGED_OUT);
		helper.assertFalse(Sentry.inTakeover(p), "ended");
		helper.assertTrue(VoidMatterData.get(level).entry(stone).dueAt > 0L, "scheduled to come back");
		VoidMatter.restoreNow(level, p.getUUID());
		helper.assertTrue(level.getBlockState(stone).is(Blocks.STONE), "back");
		helper.succeed();
	}

	private static void set(ServerPlayer p, java.util.function.Consumer<com.projecthero.mod.sentry.data.SentryState> change) {
		SentryGameTests.edit(p, change);
	}
}
