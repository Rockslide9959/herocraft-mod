package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.network.TimeSlowStatePayload;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.TimeStop;
import com.projecthero.mod.time.LevelTimeScale;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user: "can you change all the time stops and time slows so that they still work worldwide but not across
 * dimensions please"): the Time Slow scales only its own dimension ({@link LevelTimeScale}) and the Star Platinum time stop
 * runs one per dimension. Creatures in the slowed / stopped dimension crawl or freeze while one in the Nether keeps moving at
 * full speed, the caster is never held, ending puts the level back, a second caster in another dimension isn't refused, and
 * what a player is told on a dimension change is the state of the dimension they land in.
 * <p>
 * The Overworld tests use the gametest radius hooks (only the entities near the caster are held), so the parallel tests around
 * them keep running; the level-wide hold is checked on the End, where no test lives. Assertions are on tick-count steps over a
 * fixed window, with slack for the phase the window starts on.
 */
public class LevelTimeScaleV0177GameTests implements FabricGameTest {
	private static ServerPlayer speedster(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(6, 1, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Power power = Powers.byKey(SuperSpeedHandlers.KEY);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	/** A floating, invulnerable pig in a force-loaded chunk of {@code level} (it ticks whether anyone is there or not). */
	private static Pig pigIn(ServerLevel level, double x, double y, double z) {
		int cx = BlockPos.containing(x, y, z).getX() >> 4;
		int cz = BlockPos.containing(x, y, z).getZ() >> 4;
		level.setChunkForced(cx, cz, true);
		level.getChunk(cx, cz);
		Pig pig = EntityType.PIG.create(level);
		pig.moveTo(x, y, z, 0f, 0f);
		pig.setNoGravity(true);
		pig.setInvulnerable(true);
		pig.setPersistenceRequired();
		level.addFreshEntity(pig);
		return pig;
	}

	/**
	 * Force-loads the chunk at {@code at} in {@code level} (and keeps its neighbours loaded) and runs {@code body} once it is
	 * entity-ticking -- a fresh Nether / End chunk takes a while to generate and come up to ticking.
	 */
	private static void whenTicking(GameTestHelper helper, ServerLevel level, Vec3 at, Runnable body) {
		BlockPos b = BlockPos.containing(at);
		level.setChunkForced(b.getX() >> 4, b.getZ() >> 4, true);
		level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL, new net.minecraft.world.level.ChunkPos(b), 3, b);
		// generate them now (the test server doesn't wait between ticks, so async worldgen would lag far behind the tick count)
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				level.getChunk((b.getX() >> 4) + dx, (b.getZ() >> 4) + dz);
			}
		}
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(b), "the far chunk comes up to entity-ticking"))
				.thenExecute(body);
	}

	private static final Vec3 NETHER_A = new Vec3(4000.5, 90.0, 4000.5);
	private static final Vec3 NETHER_B = new Vec3(4100.5, 90.0, 4100.5);
	private static final Vec3 END_A = new Vec3(3000.5, 90.0, 3000.5);

	private static String diag(ServerLevel level, Pig pig) {
		return " [removed " + pig.isRemoved() + ", in level " + (level.getEntity(pig.getId()) == pig) + ", entity-ticking "
				+ level.isPositionEntityTicking(pig.blockPosition()) + ", forced " + level.getForcedChunks().size() + ", tick "
				+ pig.tickCount + "]";
	}

	private static void unforce(ServerLevel level, Pig pig) {
		BlockPos b = pig.blockPosition();
		pig.discard();
		level.setChunkForced(b.getX() >> 4, b.getZ() >> 4, false);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aScaledDimensionRunsOneTickInTwenty(GameTestHelper helper) {
		int runs = 0;
		for (long phase = 1; phase <= 200; phase++) {
			if (LevelTimeScale.phaseRuns(phase, SuperSpeedTimeSlow.TICK_DIVISOR)) {
				runs++;
			}
		}
		helper.assertTrue(runs == 10, "200 server ticks, 10 of them run (" + runs + ")");
		helper.assertTrue(LevelTimeScale.phaseRuns(7, 1), "an unscaled divisor runs every tick");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "v0177_level_time_scale_slow")
	public void timeSlowHoldsItsDimensionButNotTheNether(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertFalse(nether == null, "the test server has a Nether");
		whenTicking(helper, nether, NETHER_A, () -> timeSlowBody(helper, nether));
	}

	private static void timeSlowBody(GameTestHelper helper, ServerLevel nether) {
		MinecraftServer server = helper.getLevel().getServer();
		float rate = server.tickRateManager().tickrate();
		ServerPlayer p = speedster(helper);
		Zombie z = zombie(helper, 2.5, 3.5);
		Pig far = pigIn(nether, 4000.5, 90.0, 4000.5);

		SuperSpeedTimeSlow.testRadius = 5.0;
		SuperSpeedTimeSlow.start(p, 2000);
		SuperSpeedTimeSlow.testRadius = 0.0;
		helper.assertTrue(SuperSpeedTimeSlow.isCasting(p), "Time Slow starts");
		helper.assertTrue(SuperSpeedTimeSlow.activeIn(helper.getLevel()) && !SuperSpeedTimeSlow.activeIn(nether),
				"it holds the Overworld, not the Nether");
		helper.assertTrue(server.tickRateManager().tickrate() == rate, "the server's own tick rate is left alone (" + rate + ")");
		helper.assertTrue(LevelTimeScale.exempt(p) && !LevelTimeScale.exempt(z) && LevelTimeScale.exempt(far),
				"the caster and the Nether pig run, the zombie beside the caster is held");
		// what a player is told when they change dimension: the state of the dimension they land in
		TimeSlowStatePayload here = SuperSpeedTimeSlow.payloadFor(Level.OVERWORLD);
		TimeSlowStatePayload there = SuperSpeedTimeSlow.payloadFor(Level.NETHER);
		helper.assertTrue(here.active() && p.getUUID().equals(here.caster()) && here.dimension().equals("minecraft:overworld"),
				"walking into the Overworld: the slow, cast by the speedster");
		helper.assertTrue(!there.active() && there.dimension().equals("minecraft:the_nether"), "walking into the Nether: no slow");

		int z0 = z.tickCount;
		int n0 = far.tickCount;
		int left0 = SuperSpeedTimeSlow.ticksLeft(p);
		helper.runAfterDelay(40, () -> {
			int dz = z.tickCount - z0;
			int dn = far.tickCount - n0;
			int lived = left0 - SuperSpeedTimeSlow.ticksLeft(p);
			try {
				helper.assertTrue(dz >= 1 && dz <= 4, "the slowed zombie lived 1 tick in 20 (" + dz + " in 40)");
				helper.assertTrue(dn >= 30, "the Nether pig kept full speed (" + dn + " in 40)" + diag(nether, far));
				helper.assertTrue(lived >= 36 && lived <= 44, "the caster lives at full speed: their clock ran " + lived + " in 40");
			} catch (RuntimeException e) {
				SuperSpeedTimeSlow.end(p, false);
				unforce(nether, far);
				throw e;
			}
			SuperSpeedTimeSlow.end(p, false);
			helper.assertFalse(SuperSpeedTimeSlow.isCasting(p) || SuperSpeedTimeSlow.activeIn(helper.getLevel()), "Z again: it ends");
			helper.assertTrue(LevelTimeScale.scale(Level.OVERWORLD) == null, "and the Overworld is unscaled");
			int z1 = z.tickCount;
			helper.runAfterDelay(20, () -> {
				int back = z.tickCount - z1;
				unforce(nether, far);
				helper.assertTrue(back >= 16, "the zombie runs at full speed again (" + back + " in 20)");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "v0177_level_time_scale_end")
	public void aScaledLevelHoldsItsWholeSelfAndNothingElse(GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		helper.assertFalse(end == null, "the test server has an End");
		whenTicking(helper, end, END_A, () -> endBody(helper, end));
	}

	private static void endBody(GameTestHelper helper, ServerLevel end) {
		MinecraftServer server = helper.getLevel().getServer();
		float rate = server.tickRateManager().tickrate();
		Pig held = pigIn(end, 3000.5, 90.0, 3000.5);
		Zombie z = zombie(helper, 3.5, 3.5);
		LevelTimeScale.set(server, Level.END, SuperSpeedTimeSlow.TICK_DIVISOR, null, 0.0, Vec3.ZERO);
		float expected = Math.max(1.0f, rate / SuperSpeedTimeSlow.TICK_DIVISOR);
		helper.assertTrue(LevelTimeScale.clientRate(server, Level.END) == expected,
				"a client in the End is told " + expected + " ticks a second (" + LevelTimeScale.clientRate(server, Level.END) + ")");
		helper.assertTrue(LevelTimeScale.clientRate(server, Level.OVERWORLD) == rate, "a client in the Overworld keeps " + rate);

		int e0 = held.tickCount;
		int z0 = z.tickCount;
		long t0 = helper.getLevel().getGameTime();
		int[] runs = { 0 };
		int[] heldTicks = { 0 };
		helper.onEachTick(() -> {
			if (LevelTimeScale.scale(Level.END) != null) {
				if (LevelTimeScale.heldNow(end)) {
					heldTicks[0]++;
				} else {
					runs[0]++;
				}
			}
		});
		helper.runAfterDelay(40, () -> {
			int de = held.tickCount - e0;
			int dz = z.tickCount - z0;
			long dt = helper.getLevel().getGameTime() - t0;
			String d = diag(end, held);
			LevelTimeScale.clear(server, Level.END);
			unforce(end, held);
			helper.assertTrue(LevelTimeScale.clientRate(server, Level.END) == rate, "cleared: the End's clients get " + rate + " again");
			helper.assertTrue(de >= 1 && de <= 4, "the End pig lived 1 tick in 20 (" + de + " in 40)" + d);
			helper.assertTrue(dz >= 36, "the Overworld zombie kept full speed (" + dz + " in 40)");
			helper.assertTrue(dt >= 36, "the shared game clock never slowed (" + dt + " in 40)");
			helper.assertTrue(runs[0] >= 1 && runs[0] <= 4 && heldTicks[0] >= 34,
					"the End ran " + runs[0] + " ticks and held " + heldTicks[0]);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "v0177_level_time_stop_dims")
	public void timeStopFreezesItsDimensionButNotTheNether(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertFalse(nether == null, "the test server has a Nether");
		whenTicking(helper, nether, NETHER_B, () -> timeStopBody(helper, nether));
	}

	private static void timeStopBody(GameTestHelper helper, ServerLevel nether) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Stands.grant(p, StandType.STAR_PLATINUM);
		Stands.summon(p);
		Zombie z = zombie(helper, 2.5, 3.5);
		Pig far = pigIn(nether, 4100.5, 90.0, 4100.5);

		TimeStop.clear();
		TimeStop.testRadius = 5.0;
		helper.assertTrue(TimeStop.begin(p), "Shift+Z");
		TimeStop.testRadius = 0.0;
		TimeStop.skipWindup(server);
		helper.assertTrue(TimeStop.active(helper.getLevel()) && !TimeStop.active(nether), "time stopped in the Overworld only");
		helper.assertTrue(TimeStop.frozen(z) && !TimeStop.frozen(far) && !TimeStop.frozen(p),
				"the zombie is frozen; the caster and the Nether pig are not");
		helper.assertTrue(TimeStop.payload(server, Level.OVERWORLD).end() > 0L && TimeStop.payload(server, Level.NETHER).end() == 0L,
				"a player landing in the Overworld is told of the stop, one landing in the Nether of none");

		// a second Star Platinum user in the Nether is not refused (checked within this tick: the mock player only borrows the Nether)
		ServerPlayer q = helper.makeMockServerPlayerInLevel();
		q.setGameMode(GameType.SURVIVAL);
		Stands.grant(q, StandType.STAR_PLATINUM);
		Stands.summon(q);
		ServerLevel home = q.serverLevel();
		q.setServerLevel(nether);
		try {
			q.moveTo(4100.5, 90.0, 4102.5, 0f, 0f);
			helper.assertTrue(TimeStop.begin(q), "a Star Platinum user in the Nether can stop time there too");
			TimeStop.skipWindup(server);
			helper.assertTrue(TimeStop.active(nether) && TimeStop.isCaster(q) && TimeStop.isCaster(p), "two stops, one per dimension");
			TimeStop.endEarly(q);
			helper.assertTrue(!TimeStop.active(nether) && TimeStop.active(helper.getLevel()), "ending the Nether one leaves the Overworld one");
		} finally {
			TimeStop.onCasterGone(q);
			q.setServerLevel(home);
			q.moveTo(at.x, at.y, at.z, 0f, 0f);
		}

		int z0 = z.tickCount;
		int n0 = far.tickCount;
		helper.runAfterDelay(40, () -> {
			int dz = z.tickCount - z0;
			int dn = far.tickCount - n0;
			TimeStop.endEarly(p);
			unforce(nether, far);
			helper.assertFalse(TimeStop.active(), "time resumes");
			helper.assertTrue(dz == 0, "the frozen zombie didn't tick (" + dz + ")");
			helper.assertTrue(dn >= 30, "the Nether pig kept moving (" + dn + " in 40)");
			helper.succeed();
		});
	}
}
