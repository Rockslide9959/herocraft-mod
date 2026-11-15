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
 * and death ends it at 60 Void. Mock players from other tests may linger, so nothing asserts "no player near".
 */
public class VoidTakeoverGameTests implements FabricGameTest {
	private static ServerPlayer takenOver(GameTestHelper helper, double x, double z) {
		ServerPlayer p = SentryGameTests.sentry(helper, x, z);
		Sentry.setVoid(p, SentryConfig.VOID_MAX);
		helper.assertTrue(Sentry.inTakeover(p), "100 Void: the Void takes over");
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
	public void eatenBlocksAreRecordedAndComeBack(GameTestHelper helper) {
		ServerPlayer p = takenOver(helper, 2, 2);
		ServerLevel level = helper.getLevel();
		BlockPos stone = helper.absolutePos(new BlockPos(5, 1, 5));
		BlockPos chest = helper.absolutePos(new BlockPos(5, 1, 6));
		BlockPos bedrock = helper.absolutePos(new BlockPos(5, 1, 4));
		level.setBlockAndUpdate(stone, Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
		helper.assertTrue(VoidMatter.eat(level, p, stone), "stone bricks are eaten");
		helper.assertTrue(level.getBlockState(stone).is(SentryBlocks.VOID_MATTER), "turned to void matter");
		helper.assertTrue(VoidMatterData.get(level).has(stone), "the original is recorded");
		helper.assertTrue(VoidMatterData.get(level).entry(stone).state.is(Blocks.STONE_BRICKS), "as stone bricks");
		helper.assertFalse(VoidMatter.eat(level, p, chest), "never a chest");
		helper.assertFalse(VoidMatter.eat(level, p, bedrock), "never bedrock");
		int ring = VoidMatter.tickRing(p, 0);
		helper.assertTrue(ring >= 0, "the ring ticks");
		VoidTakeover.end(p, VoidTakeover.End.WON);
		VoidMatterData.Entry e = VoidMatterData.get(level).entry(stone);
		helper.assertTrue(e != null && e.dueAt >= level.getGameTime() + SentryConfig.RESTORE_DELAY_TICKS - 1, "due back in about a minute");
		helper.runAfterDelay(SentryConfig.MATTER_DISSOLVE_TICKS + 15, () -> {
			helper.assertTrue(level.getBlockState(stone).isAir(), "void matter dissolved into nothing");
			int back = VoidMatter.restoreNow(level, p.getUUID());
			helper.assertTrue(back >= 1, "restored");
			helper.assertTrue(level.getBlockState(stone).is(Blocks.STONE_BRICKS), "the stone bricks are back");
			helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "the chest never left");
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
			helper.assertFalse(VoidMatterData.get(level).has(stone), "nothing recorded under the gamerule");
			VoidTakeover.end(p, VoidTakeover.End.WON);
			VoidMatter.restoreNow(level, p.getUUID());
			helper.assertFalse(level.getBlockState(stone).is(Blocks.STONE_BRICKS), "it never comes back");
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
