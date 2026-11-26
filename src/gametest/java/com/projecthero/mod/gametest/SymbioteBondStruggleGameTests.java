package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteBondGame;
import com.projecthero.mod.symbiote.SymbioteBondGame.State;
import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;
import com.projecthero.mod.symbiote.entity.SymbioteEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the bonding struggle (three phases, a draining Bond meter, strikes, lockout -- all replayed on the server) and
 * wild Symbiotes despawning / the crowd cap on new ones.
 */
public class SymbioteBondStruggleGameTests implements FabricGameTest {

	/** A bot that waits for the marker to be near the zone's centre and strikes exactly then (a flawless player). */
	static List<Integer> flawlessRun(int seed) {
		List<Integer> presses = new ArrayList<>();
		double now = 0.0;
		while (now < 20 * 90) {
			State st = SymbioteBondGame.simulate(seed, presses, now);
			if (!st.playing()) {
				return st.outcome() == SymbioteBondGame.WON ? presses : null;
			}
			if (st.accepts(now)) {
				double t = now - st.phaseStart();
				double d = Math.abs(SymbioteBondGame.marker(seed, st.phase(), t) - SymbioteBondGame.zoneCenter(seed, st.phase(), t));
				if (d <= SymbioteBondGame.halfZone(st.phase(), st.meter()) * 0.15) {
					int stamp = (int) Math.ceil(now * SymbioteBondGame.SUBTICKS);
					presses.add(stamp);
					now = stamp / (double) SymbioteBondGame.SUBTICKS + 0.05;
					continue;
				}
			}
			now += 0.05;
		}
		return null;
	}

	private static double seconds(List<Integer> presses) {
		return presses.get(presses.size() - 1) / (double) SymbioteBondGame.SUBTICKS / 20.0;
	}

	// ---------------------------------------------------------------- the struggle (pure)

	@GameTest(template = EMPTY_STRUCTURE)
	public void aFlawlessPlayerWinsAllThreePhasesInFairTime(GameTestHelper helper) {
		java.util.Random r = new java.util.Random(77);
		for (int i = 0; i < 24; i++) {
			int seed = r.nextInt();
			List<Integer> run = flawlessRun(seed);
			helper.assertTrue(run != null, "a flawless player always wins, seed " + seed);
			double s = seconds(run);
			helper.assertTrue(s >= 6.0 && s <= 40.0, "a flawless run takes a fair time (6-40 s), got " + s);
			helper.assertTrue(run.size() >= 9, "it takes several strikes per phase, not three presses (" + run.size() + ")");
			long elapsed = (long) Math.ceil(run.get(run.size() - 1) / (double) SymbioteBondGame.SUBTICKS);
			helper.assertTrue(SymbioteBondGame.verifyWin(seed, run, elapsed), "the server accepts a real win");
			State st = SymbioteBondGame.simulate(seed, run, elapsed);
			helper.assertTrue(st.strikes() == 0 && st.phase() == SymbioteBondGame.PHASES, "won clean through every phase");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMeterDrainsWhenYouDoNothing(GameTestHelper helper) {
		int seed = 0x5EED0001;
		State early = SymbioteBondGame.simulate(seed, List.of(), SymbioteBondGame.GRACE);
		helper.assertTrue(early.playing() && Math.abs(early.meter() - SymbioteBondGame.START[0]) < 1.0e-9, "no drain during the grace");
		State a = SymbioteBondGame.simulate(seed, List.of(), SymbioteBondGame.GRACE + 20);
		State b = SymbioteBondGame.simulate(seed, List.of(), SymbioteBondGame.GRACE + 40);
		double step1 = early.meter() - a.meter();
		double step2 = a.meter() - b.meter();
		helper.assertTrue(step1 > 0.0 && step2 > 0.0, "the Symbiote drags the meter down");
		helper.assertTrue(step2 < step1, "and drags harder the fuller it is (step " + step1 + " then " + step2 + ")");
		State idle = SymbioteBondGame.simulate(seed, List.of(), 20 * 60);
		helper.assertTrue(idle.outcome() == SymbioteBondGame.FAILED && idle.reason() == SymbioteBondGame.REASON_DRAINED,
				"waiting it out loses");
		helper.assertTrue(idle.endAt() > 20 * 5 && idle.endAt() < 20 * 15, "after a fair 5-15 s, got " + idle.endAt() / 20.0);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mashingLosesOnStrikes(GameTestHelper helper) {
		java.util.Random r = new java.util.Random(5);
		int lost = 0;
		for (int i = 0; i < 20; i++) {
			int seed = r.nextInt();
			List<Integer> presses = new ArrayList<>();
			State st = null;
			for (int k = 0; k < SymbioteBondGame.MAX_PRESSES; k++) {
				double at = SymbioteBondGame.LOCKOUT * (k + 1);
				st = SymbioteBondGame.simulate(seed, presses, at);
				if (!st.playing()) {
					break;
				}
				if (!st.accepts(at)) {
					continue;
				}
				presses.add((int) Math.round(at * SymbioteBondGame.SUBTICKS));
				st = SymbioteBondGame.simulate(seed, presses, at);
				if (!st.playing()) {
					break;
				}
			}
			if (st != null && st.outcome() == SymbioteBondGame.FAILED) {
				lost++;
			}
		}
		helper.assertTrue(lost >= 18, "mashing as fast as the lockout allows nearly always loses, lost " + lost + "/20");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theServerRejectsForgedResults(GameTestHelper helper) {
		int seed = 0x0BADF00D;
		List<Integer> run = flawlessRun(seed);
		helper.assertTrue(run != null, "bot run");
		long elapsed = (long) Math.ceil(seconds(run) * 20.0) + 1;
		helper.assertTrue(SymbioteBondGame.verifyWin(seed, run, elapsed), "the honest run passes");

		helper.assertFalse(SymbioteBondGame.verifyWin(seed ^ 1, run, elapsed), "the same presses against another seed fail");
		helper.assertFalse(SymbioteBondGame.verifyWin(seed, run.subList(0, run.size() - 1), elapsed), "one strike short is no win");
		List<Integer> extra = new ArrayList<>(run);
		extra.add(run.get(run.size() - 1) + 2000);
		helper.assertFalse(SymbioteBondGame.verifyWin(seed, extra, elapsed + 40), "presses after the win are junk");
		List<Integer> gaveUp = new ArrayList<>(run);
		gaveUp.add(SymbioteBondGame.GAVE_UP);
		helper.assertFalse(SymbioteBondGame.verifyWin(seed, gaveUp, elapsed), "a give-up marker is never a win");
		List<Integer> mash = new ArrayList<>(run);
		mash.add(1, run.get(0) + 200); // 2 ticks after the first: inside the lockout
		mash.sort(Integer::compare);
		helper.assertFalse(SymbioteBondGame.verifyWin(seed, mash, elapsed), "a strike inside the lockout is impossible");
		helper.assertFalse(SymbioteBondGame.verifyWin(seed, run, 5), "a win claimed long before it could have happened fails");
		helper.assertFalse(SymbioteBondGame.verifyWin(seed, run, 20 * 200), "a session far past its time fails");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the server flow

	private static SymbioteEntity blob(GameTestHelper helper, Vec3 rel) {
		SymbioteEntity blob = SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		blob.moveTo(helper.absoluteVec(rel));
		blob.setHuntDelay(100000);
		helper.getLevel().addFreshEntity(blob);
		return blob;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anInstantWinClaimIsRejectedAndPunished(GameTestHelper helper) {
		floor(helper);
		SymbioteEntity blob = blob(helper, new Vec3(2.5, 1.0, 2.5));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		int seed = 0x13579BDF;
		helper.assertTrue(blob.claimBond(player, 2000), "claim");
		SymbioteBondGame.openSession(player, blob, seed);
		List<Integer> run = flawlessRun(seed);
		SymbioteBondGame.handleResult(player, run); // sent the instant the game opened
		helper.assertFalse(Symbiote.hasSymbiote(player), "a win that could not have happened yet never bonds");
		helper.assertTrue(SymbioteBondGame.onRetryCooldown(player), "it counts as a failure");
		helper.assertFalse(blob.isRemoved(), "the Symbiote is kept");
		helper.assertFalse(blob.isHeld(), "and let go");
		SymbioteBondGame.handleResult(player, run);
		helper.assertFalse(Symbiote.hasSymbiote(player), "an unprompted second answer does nothing");
		blob.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1000)
	public void aRealWinBondsAfterItHadTimeToHappen(GameTestHelper helper) {
		floor(helper);
		SymbioteEntity blob = blob(helper, new Vec3(2.5, 1.0, 2.5));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
		int seed = 0x2468ACE0;
		List<Integer> run = flawlessRun(seed);
		helper.assertTrue(run != null, "bot run");
		helper.assertTrue(blob.claimBond(player, 2000), "claim");
		SymbioteBondGame.openSession(player, blob, seed);
		int wait = (int) Math.ceil(seconds(run) * 20.0) + 2;
		helper.runAfterDelay(wait, () -> {
			SymbioteBondGame.handleResult(player, run);
			helper.assertTrue(Symbiote.hasSymbiote(player), "the replayed win bonds");
			helper.assertTrue(blob.isRemoved(), "and the Symbiote is used up");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void backingOutIsFreeOnlyAtTheStart(GameTestHelper helper) {
		ServerPlayer early = helper.makeMockServerPlayerInLevel();
		ServerPlayer late = helper.makeMockServerPlayerInLevel();
		SymbioteBondGame.openSession(early, null, 1);
		SymbioteBondGame.handleResult(early, List.of());
		helper.assertFalse(SymbioteBondGame.onRetryCooldown(early), "Esc straight away costs nothing");
		helper.assertFalse(SymbioteBondGame.hasSession(early), "and closes the game");
		SymbioteBondGame.openSession(late, null, 2);
		helper.runAfterDelay(SymbioteBondGame.FREE_BACK_OUT + 60, () -> {
			SymbioteBondGame.handleResult(late, List.of());
			helper.assertTrue(SymbioteBondGame.onRetryCooldown(late), "walking away mid-struggle is a failure");
			helper.assertFalse(Symbiote.hasSymbiote(late), "and never bonds");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- wild Symbiotes despawn

	@GameTest(template = EMPTY_STRUCTURE)
	public void aWildSymbioteDespawnsAfterFiveLonelyMinutes(GameTestHelper helper) {
		helper.assertTrue(HeroConfig.get().freeSymbioteDespawnTicks == 20 * 60 * 5, "default: 5 minutes");
		SymbioteEntity blob = blob(helper, new Vec3(2.5, 1.0, 2.5));
		int limit = 20 * 60 * 5;
		for (int t = SymbioteEntity.DESPAWN_CHECK; t < limit; t += SymbioteEntity.DESPAWN_CHECK) {
			helper.assertFalse(blob.tickDespawnClock(false, SymbioteEntity.DESPAWN_CHECK, limit), "still here at " + t);
		}
		helper.assertTrue(blob.idleTicks() == limit - SymbioteEntity.DESPAWN_CHECK, "the clock counted every check");
		// a player wandering by restarts the clock
		helper.assertFalse(blob.tickDespawnClock(true, SymbioteEntity.DESPAWN_CHECK, limit), "a player near keeps it");
		helper.assertTrue(blob.idleTicks() == 0, "and resets the clock");
		for (int t = 0; t < limit - SymbioteEntity.DESPAWN_CHECK; t += SymbioteEntity.DESPAWN_CHECK) {
			blob.tickDespawnClock(false, SymbioteEntity.DESPAWN_CHECK, limit);
		}
		// the clock is saved with the entity
		CompoundTag tag = new CompoundTag();
		blob.saveWithoutId(tag);
		SymbioteEntity copy = SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		copy.load(tag);
		helper.assertTrue(copy.idleTicks() == blob.idleTicks(), "a reload keeps the clock");
		helper.assertTrue(blob.tickDespawnClock(false, SymbioteEntity.DESPAWN_CHECK, limit), "five lonely minutes: it goes");
		blob.dissolveAway(helper.getLevel());
		helper.assertTrue(blob.isRemoved(), "dissolved");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void labNamedAndOperatorSymbiotesStay(GameTestHelper helper) {
		int limit = 100;
		SymbioteEntity lab = blob(helper, new Vec3(1.5, 1.0, 1.5));
		lab.setConfined(lab.position(), SymbioteEntity.LAB_HOME_RADIUS);
		SymbioteEntity named = blob(helper, new Vec3(2.5, 1.0, 2.5));
		named.setCustomName(net.minecraft.network.chat.Component.literal("Klyntar"));
		SymbioteEntity op = blob(helper, new Vec3(3.5, 1.0, 3.5));
		op.setPersistent(true);
		SymbioteEntity claimed = blob(helper, new Vec3(1.5, 1.0, 3.5));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(claimed.claimBond(player, 1000), "claim");
		for (SymbioteEntity e : List.of(lab, named, op, claimed)) {
			helper.assertTrue(e.mustPersist(), "persistent");
			helper.assertFalse(e.tickDespawnClock(false, 1000, limit), "never despawns");
			helper.assertTrue(e.idleTicks() == 0, "its clock never runs");
		}
		CompoundTag tag = new CompoundTag();
		op.saveWithoutId(tag);
		SymbioteEntity copy = SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		copy.load(tag);
		helper.assertTrue(copy.isPersistent(), "the persistent flag survives a reload");
		SymbioteEntity wild = blob(helper, new Vec3(3.5, 1.0, 1.5));
		helper.assertFalse(wild.mustPersist(), "a plain wild one does not");
		helper.assertFalse(wild.tickDespawnClock(false, 1000, 0), "a limit of 0 turns despawning off");
		for (SymbioteEntity e : List.of(lab, named, op, claimed, wild)) {
			e.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aCrowdOfWildSymbiotesStopsNewOnesFromHosts(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		int cap = HeroConfig.get().freeSymbioteMaxNearby;
		helper.assertTrue(cap == 3, "default cap: 3 within 32 blocks");
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		List<SymbioteEntity> mine = new ArrayList<>();
		// lab Symbiotes never count toward the crowd
		int before = SymbioteEntity.countWild(level, at.x, at.y, at.z, SymbioteEntity.CROWD_RANGE);
		SymbioteEntity lab = blob(helper, new Vec3(1.5, 1.0, 1.5));
		lab.setConfined(lab.position(), SymbioteEntity.LAB_HOME_RADIUS);
		mine.add(lab);
		helper.assertTrue(SymbioteEntity.countWild(level, at.x, at.y, at.z, SymbioteEntity.CROWD_RANGE) == before,
				"a confined lab Symbiote is not part of the crowd");
		// fill the area up to the cap (counting any a neighbouring test left)
		for (int i = before; i < cap; i++) {
			mine.add(blob(helper, new Vec3(1.5 + i, 1.0, 3.5)));
		}
		int crowd = SymbioteEntity.countWild(level, at.x, at.y, at.z, SymbioteEntity.CROWD_RANGE);
		helper.assertTrue(crowd >= cap, "the crowd is full");
		helper.assertTrue(SymbioteEntity.spawnReleased(level, at.x, at.y, at.z) == null, "no new Symbiote is released");

		// a host dying in the crowd takes its Symbiote with it
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(3, 1, 1));
		SymbioteHost.mark(husk);
		Husk wildHusk = EntityType.HUSK.create(level); // helper.spawn forces persistence, so judge mark() on a plain one
		SymbioteHost.mark(wildHusk);
		helper.assertFalse(wildHusk.isPersistenceRequired(), "a natural host follows normal despawn rules");
		husk.kill();
		helper.assertTrue(SymbioteEntity.countWild(level, at.x, at.y, at.z, SymbioteEntity.CROWD_RANGE) == crowd,
				"the dead host freed nothing");
		for (SymbioteEntity e : mine) {
			e.discard();
		}
		helper.succeed();
	}
}
