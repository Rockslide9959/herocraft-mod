package com.projecthero.mod.gametest;

import java.util.Random;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.spider.SpiderAnim;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSwingStyles;
import com.projecthero.mod.spider.SpiderTricks;
import com.projecthero.mod.spider.SpiderWallRun;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** v0.16.2 Spider-Man freerunning: the Wall Run's angle test, the swing tricks' reward cap / count, the swing styles. */
public class SpiderFreerunGameTests implements FabricGameTest {

	private static void check(GameTestHelper helper, boolean ok, String what) {
		if (!ok) {
			helper.fail(what);
		}
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-6;
	}

	/** Straight into a wall climbs; 50 degrees or more off the normal (up to heading away) wall-runs. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderWallRunAngle(GameTestHelper helper) {
		// face EAST: the wall is on his +X side
		check(helper, near(SpiderWallRun.angleOffNormal(1, 0, Direction.EAST), 0.0), "straight in = 0");
		check(helper, near(SpiderWallRun.angleOffNormal(0, 1, Direction.EAST), 90.0), "along = 90");
		check(helper, near(SpiderWallRun.angleOffNormal(-1, 0, Direction.EAST), 180.0), "away = 180");
		check(helper, !SpiderWallRun.alongWall(1, 0, Direction.EAST), "into the wall climbs");
		check(helper, !SpiderWallRun.alongWall(1, 0.5, Direction.EAST), "27 deg off still climbs");
		check(helper, !SpiderWallRun.alongWall(Math.cos(Math.toRadians(45)), Math.sin(Math.toRadians(45)), Direction.EAST),
				"45 deg climbs");
		check(helper, SpiderWallRun.alongWall(Math.cos(Math.toRadians(52)), Math.sin(Math.toRadians(52)), Direction.EAST),
				"52 deg runs");
		check(helper, SpiderWallRun.alongWall(0, -1, Direction.EAST), "exactly along runs");
		check(helper, SpiderWallRun.alongWall(-0.2, 1, Direction.EAST), "slightly away still runs");
		check(helper, !SpiderWallRun.alongWall(-1, 0.3, Direction.EAST), "heading away does not");
		check(helper, !SpiderWallRun.alongWall(0, 0, Direction.EAST), "standing still does not");
		check(helper, !SpiderWallRun.alongWall(0, 1, Direction.UP), "a ceiling is no wall");
		// every horizontal face, heading along it both ways
		for (Direction d : Direction.Plane.HORIZONTAL) {
			Direction t = d.getClockWise();
			check(helper, SpiderWallRun.alongWall(t.getStepX(), t.getStepZ(), d), "along " + d);
			check(helper, SpiderWallRun.alongWall(-t.getStepX(), -t.getStepZ(), d), "along back " + d);
			Vec3 run = SpiderWallRun.runDirection(t.getStepX(), t.getStepZ(), d);
			check(helper, run != null && near(run.x, t.getStepX()) && near(run.z, t.getStepZ()), "run dir " + d);
			check(helper, SpiderWallRun.runDirection(d.getStepX(), d.getStepZ(), d) == null, "looking into " + d);
		}
		// pitch drift: none near level, up when looking up, down when looking down, capped
		check(helper, SpiderWallRun.drift(10f) == 0.0 && SpiderWallRun.drift(-15f) == 0.0, "deadzone");
		check(helper, SpiderWallRun.drift(-60f) > 0.0 && SpiderWallRun.drift(60f) < 0.0, "drift follows pitch");
		check(helper, near(SpiderWallRun.drift(-90f), SpiderWallRun.MAX_DRIFT), "drift cap");
		// the kick: out from the wall, up, along the look -- never back into the wall
		Vec3 kick = SpiderWallRun.kick(new Vec3(-1, 0, 0), new Vec3(0.3, 0.1, 0.95));
		check(helper, kick.x < -0.5 && kick.y >= SpiderWallRun.KICK_UP - 1.0e-6 && kick.z > 0.3, "kick " + kick);
		Vec3 kick2 = SpiderWallRun.kick(new Vec3(-1, 0, 0), new Vec3(1, 0, 0));
		check(helper, kick2.x < 0.0 && near(kick2.z, 0.0), "kick looking at the wall " + kick2);
		helper.succeed();
	}

	/** +10 % / 5 % / 3.3 % for tricks 1-3, nothing after, never past the cap, never slower; the tap window. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderTrickRewardCap(GameTestHelper helper) {
		check(helper, near(SpiderTricks.rewardFactor(1), 1.10), "1st +10%");
		check(helper, near(SpiderTricks.rewardFactor(2), 1.05), "2nd +5%");
		check(helper, near(SpiderTricks.rewardFactor(3), 1.0 + 0.1 / 3.0), "3rd +3.3%");
		check(helper, near(SpiderTricks.rewardFactor(4), 1.0) && near(SpiderTricks.rewardFactor(0), 1.0), "no 4th");
		double max = 3.0;
		Vec3 v = new Vec3(1.0, -0.2, 0.0);
		Vec3 a = SpiderTricks.reward(v, 1, max);
		check(helper, near(a.x, 1.1) && near(a.y, -0.2), "horizontal +10%, vertical kept " + a);
		// chained to the cap: never over it
		Vec3 fast = new Vec3(2.9, 0.0, 0.0);
		Vec3 b = SpiderTricks.reward(fast, 1, max);
		check(helper, b.length() <= max + 1.0e-9 && b.length() >= fast.length(), "capped at max " + b.length());
		// already at / over the cap: untouched
		Vec3 over = new Vec3(3.5, 0.0, 0.0);
		check(helper, SpiderTricks.reward(over, 1, max).equals(over), "over the cap untouched");
		// three tricks from 1.0 never exceed 1.1 * 1.05 * 1.033
		Vec3 c = new Vec3(1.0, 0, 0);
		for (int n = 1; n <= 5; n++) {
			c = SpiderTricks.reward(c, n, max);
		}
		check(helper, near(c.x, 1.1 * 1.05 * (1.0 + 0.1 / 3.0)), "diminishing returns " + c.x);
		// window: a tap within 20 ticks of the release; not mid-trick; the next opens at the trick's end; max 3
		long rel = 1000L;
		long none = Long.MIN_VALUE / 2;
		check(helper, SpiderTricks.allowed(0, rel + 5, rel, none, 0), "tap after release");
		check(helper, SpiderTricks.allowed(0, rel + 20, rel, none, 0), "tap at 1 s");
		check(helper, !SpiderTricks.allowed(0, rel + 25, rel, none, 0), "too late");
		check(helper, !SpiderTricks.allowed(0, rel - 1, rel, none, 0), "before release");
		check(helper, !SpiderTricks.allowed(1, rel + 8, rel, rel + 5, 0), "mid-trick");
		check(helper, SpiderTricks.allowed(1, rel + 5 + SpiderTricks.LENGTH, rel, rel + 5, 0), "chain at the trick's end");
		check(helper, SpiderTricks.allowed(1, rel + 5 + SpiderTricks.LENGTH + 20, rel, rel + 5, 0), "window reopens");
		check(helper, !SpiderTricks.allowed(3, rel + 100, rel + 90, none, 0), "max 3 per airtime");
		// keys: W / S / A / D, random otherwise (in range)
		check(helper, SpiderTricks.kindForKeys(true, false, false, false, 7) == SpiderTricks.FRONT_FLIP, "W");
		check(helper, SpiderTricks.kindForKeys(false, true, false, false, 7) == SpiderTricks.BACKFLIP, "S");
		check(helper, SpiderTricks.kindForKeys(false, false, true, false, 7) == SpiderTricks.CORKSCREW, "A");
		check(helper, SpiderTricks.kindForKeys(false, false, false, true, 7) == SpiderTricks.TWIST, "D");
		boolean[] seen = new boolean[SpiderTricks.COUNT];
		for (int i = -20; i < 20; i++) {
			int k = SpiderTricks.kindForKeys(false, false, false, false, i);
			check(helper, k >= 0 && k < SpiderTricks.COUNT, "random in range");
			seen[k] = true;
		}
		for (boolean s : seen) {
			check(helper, s, "every trick reachable at random");
		}
		helper.succeed();
	}

	/** The server: a trick after a release plays its synced animation and counts; one mid-trick is refused. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderTrickServerCount(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY));
		SpiderMan.evolveFromAdhesion(p);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 6.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setOnGround(false);
		check(helper, SpiderTricks.tryTrick(p, SpiderTricks.BACKFLIP) == 0, "no trick without a release");
		SpiderTricks.markReleased(p);
		check(helper, SpiderTricks.tryTrick(p, SpiderTricks.BACKFLIP) == 1, "first trick");
		check(helper, SpiderMoves.state(p).animId == SpiderAnim.TRICK_BACKFLIP, "synced backflip animation");
		check(helper, SpiderTricks.tryTrick(p, SpiderTricks.SPLIT) == 0, "refused mid-trick");
		check(helper, SpiderTricks.count(p) == 1, "count 1");
		check(helper, SpiderTricks.tryTrick(p, 99) == 0, "bad kind refused");
		helper.succeed();
	}

	/** The swing style: the same line always gives the same style (every viewer agrees); all six come up evenly. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderSwingStylePicker(GameTestHelper helper) {
		Random rnd = new Random(1234L);
		int[] counts = new int[SpiderSwingStyles.COUNT];
		int n = 12000;
		for (int i = 0; i < n; i++) {
			double x = (rnd.nextDouble() - 0.5) * 4000.0;
			double y = 60.0 + rnd.nextDouble() * 120.0;
			double z = (rnd.nextDouble() - 0.5) * 4000.0;
			double rope = 4.0 + rnd.nextDouble() * 40.0;
			int s = SpiderSwingStyles.pick(x, y, z, rope);
			check(helper, s >= 0 && s < SpiderSwingStyles.COUNT, "in range");
			check(helper, s == SpiderSwingStyles.pick(x, y, z, rope), "deterministic");
			counts[s]++;
		}
		for (int s = 0; s < SpiderSwingStyles.COUNT; s++) {
			double share = counts[s] / (double) n;
			check(helper, share > 0.13 && share < 0.20, "style " + s + " share " + share);
		}
		// lines fired at the same block but a slightly different point / length still vary
		int[] near = new int[SpiderSwingStyles.COUNT];
		for (int i = 0; i < 600; i++) {
			near[SpiderSwingStyles.pick(100.5 + i * 0.001, 90.0, -40.25, 18.0 + (i % 7) * 0.01)]++;
		}
		for (int c : near) {
			check(helper, c > 40, "neighbouring lines vary");
		}
		check(helper, SpiderSwingStyles.twoHanded(SpiderSwingStyles.HANG_PULL)
				&& SpiderSwingStyles.twoHanded(SpiderSwingStyles.CLIMBER)
				&& SpiderSwingStyles.twoHanded(SpiderSwingStyles.TWO_HAND)
				&& !SpiderSwingStyles.twoHanded(SpiderSwingStyles.CLASSIC), "two-handed styles");
		helper.succeed();
	}
}
