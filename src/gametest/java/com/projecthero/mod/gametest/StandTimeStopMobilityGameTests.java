package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.StarPlatinum;
import com.projecthero.mod.stand.TimeStop;
import com.projecthero.mod.stand.TimeStop.Mobility;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 (user: "players with super speed and star platinum and xlr8 can move during time stop, regular super speed can move at
 * the speed of walking, xlr8 can move at the speed of regular player sprinting and same for speed force speedster, other star
 * platinum users can move completely free and use abilities in time stop" / "make stand stamina only regenerate 1 second after
 * not using stamina"): who the stop freezes, the speed caps as step sizes of one self-move, the speedsters' refused actions, a free
 * Star Platinum user's queued hit, and the Stamina refill's 1 s wait. Mock players are not ticked by the server, so the tests drive
 * {@code Entity#move} and {@link Stands#tick} themselves. The stop runs alone in its own batch with a small radius.
 */
public class StandTimeStopMobilityGameTests implements FabricGameTest {
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

	private static ServerPlayer standUser(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		Stands.grant(p, StandType.STAR_PLATINUM);
		Stands.summon(p);
		return p;
	}

	/** One self-move of {@code ask} blocks along +X: how far it really went. */
	private static double step(ServerPlayer p, double ask) {
		double x0 = p.getX();
		p.setDeltaMovement(Vec3.ZERO);
		p.move(MoverType.SELF, new Vec3(ask, 0.0, 0.0));
		return p.getX() - x0;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "stand_time_stop_mobility")
	public void speedstersAndStandUsersMoveThroughStoppedTime(GameTestHelper helper) {
		TimeStop.clear();
		TimeStop.testRadius = 8.0;
		ServerPlayer caster = standUser(helper, 1.5, 2.0, 1.5);
		ServerPlayer rival = standUser(helper, 3.5, 2.0, 1.5);
		ServerPlayer flash = mock(helper, 1.5, 2.0, 5.5);
		SpeedForceSpeedster.grant(flash);
		ServerPlayer xlr = mock(helper, 3.5, 2.0, 5.5);
		helper.assertTrue(Omnitrix.grant(xlr) && Omnitrix.select(xlr, Aliens.XLR8.id()) && Omnitrix.transform(xlr), "XLR8 on");
		ServerPlayer speedy = mock(helper, 5.5, 2.0, 5.5);
		Power speed = Powers.byKey(SuperSpeedHandlers.KEY);
		ExperimentalPowers.grant(speedy, speed);
		ExperimentalPowers.setActive(speedy, speed);
		ServerPlayer plain = mock(helper, 5.5, 2.0, 1.5);

		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 zAt = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		z.moveTo(zAt.x, zAt.y, zAt.z, 180.0f, 0.0f);
		z.setNoAi(true);
		z.setPersistenceRequired();
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0);
		z.setHealth(60f);
		helper.getLevel().addFreshEntity(z);

		helper.assertTrue(TimeStop.classify(rival) == Mobility.FREE, "another Star Platinum user is free: " + TimeStop.classify(rival));
		helper.assertTrue(TimeStop.classify(flash) == Mobility.SPRINT, "a Speed Force speedster sprints: " + TimeStop.classify(flash));
		helper.assertTrue(TimeStop.classify(xlr) == Mobility.SPRINT, "XLR8 sprints: " + TimeStop.classify(xlr));
		helper.assertTrue(TimeStop.classify(speedy) == Mobility.WALK, "the Super Speed mutation walks: " + TimeStop.classify(speedy));
		helper.assertTrue(TimeStop.classify(plain) == Mobility.FROZEN, "anyone else is frozen");
		helper.assertTrue(step(speedy, 1.0) > 0.99, "no cap with no stop running");

		helper.assertTrue(TimeStop.begin(caster), "Shift+Z");
		TimeStop.testRadius = 0.0;
		TimeStop.skipWindup(helper.getLevel().getServer());
		helper.assertTrue(TimeStop.active() && TimeStop.isCaster(caster), "time stopped");

		// who is frozen
		helper.assertTrue(TimeStop.frozen(plain) && TimeStop.frozen(z), "the plain player and the zombie are frozen");
		for (ServerPlayer p : new ServerPlayer[] { caster, rival, flash, xlr, speedy }) {
			helper.assertFalse(TimeStop.frozen(p), p.getScoreboardName() + " still ticks");
		}
		// who may act
		helper.assertTrue(TimeStop.actionsLocked(plain) && TimeStop.actionsLocked(flash) && TimeStop.actionsLocked(xlr)
				&& TimeStop.actionsLocked(speedy), "the frozen player and the speedsters can't act");
		helper.assertFalse(TimeStop.actionsLocked(rival) || TimeStop.actionsLocked(caster), "the Star Platinum users can");
		helper.assertTrue(AttackEntityCallback.EVENT.invoker().interact(flash, helper.getLevel(), InteractionHand.MAIN_HAND, z, null)
				== InteractionResult.FAIL, "a speedster's punch is refused");
		helper.assertTrue(TimeStop.unbound(rival) && !TimeStop.unbound(flash), "the rival acts on the frozen world, the speedster doesn't");

		// the caps, as the step size of one self-move
		double walk = step(speedy, 1.0);
		helper.assertTrue(Math.abs(walk - TimeStop.WALK_CAP) < 1.0e-3, "Super Speed: walking pace " + walk);
		double sprintF = step(flash, 1.0);
		helper.assertTrue(Math.abs(sprintF - TimeStop.SPRINT_CAP) < 1.0e-3, "Speed Force: sprint pace " + sprintF);
		double sprintX = step(xlr, 1.0);
		helper.assertTrue(Math.abs(sprintX - TimeStop.SPRINT_CAP) < 1.0e-3, "XLR8: sprint pace " + sprintX);
		helper.assertTrue(Math.abs(step(speedy, 0.1) - 0.1) < 1.0e-3, "a step under the cap is left alone");
		flash.setDeltaMovement(2.0, 0.0, 0.0);
		flash.move(MoverType.SELF, new Vec3(-2.0, 0.0, 0.0));
		helper.assertTrue(flash.getDeltaMovement().horizontalDistance() <= TimeStop.SPRINT_CAP + 1.0e-6, "velocity capped too: " + flash.getDeltaMovement());
		double free = step(rival, 0.6);
		helper.assertTrue(Math.abs(free - 0.6) < 1.0e-3, "the rival Stand user moves freely: " + free);
		step(rival, -0.6);

		// a free Star Platinum user fights: the hit is queued (no +30%, that is the caster's) and costs its Stamina
		float stBefore = Stands.stamina(rival);
		StarPlatinum.uppercut(rival);
		float queued = TimeStop.queued(z);
		helper.assertTrue(Math.abs(z.getHealth() - 60f) < 0.01f, "the frozen zombie isn't hurt yet");
		helper.assertTrue(Math.abs(queued - StandConfig.UPPERCUT_DAMAGE) < 0.05f, "queued without the caster's bonus: " + queued);
		helper.assertTrue(Stands.stamina(rival) < stBefore, "the rival pays Stamina as usual");

		helper.runAfterDelay(2, () -> {
			TimeStop.endEarly(caster);
			helper.assertFalse(TimeStop.active(), "time resumes");
			helper.assertTrue(z.getHealth() < 60f - 1f, "the rival's queued hit landed: " + z.getHealth());
			helper.assertTrue(step(speedy, -1.0) < -0.99, "the cap goes with the stop"); // (back the way it came: the barrier wall is ahead)
			helper.succeed();
		});
	}

	/** v0.17.4: the bar holds still for 1 s after any spend, then refills 3 a second (0.75 every 5 ticks). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void staminaWaitsASecondAfterSpending(GameTestHelper helper) {
		ServerPlayer p = standUser(helper, 2.5, 2.0, 2.5);
		Stands.setStamina(p, 50f);
		helper.onEachTick(() -> Stands.tick(p));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(Math.abs(Stands.stamina(p) - 50f) < 1.0e-3f, "no refill in the first second: " + Stands.stamina(p));
			helper.assertTrue(Stands.spendStamina(p, 5f), "spend 5 more");
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(Stands.regenWaiting(p), "still waiting 15 ticks after the second spend");
			helper.assertTrue(Math.abs(Stands.stamina(p) - 45f) < 1.0e-3f, "the spend restarted the wait: " + Stands.stamina(p));
		});
		float[] from = { 0f };
		helper.runAfterDelay(42, () -> {
			helper.assertFalse(Stands.regenWaiting(p), "a second has passed");
			from[0] = Stands.stamina(p);
			helper.assertTrue(from[0] > 45f, "refilling: " + from[0]);
		});
		helper.runAfterDelay(47, () -> {
			float stepSize = Stands.stamina(p) - from[0];
			float expect = StandConfig.STAMINA_REGEN / 4f;
			helper.assertTrue(Math.abs(stepSize - expect) < 0.02f, "one refill step of " + expect + ": " + stepSize);
			helper.succeed();
		});
	}
}
