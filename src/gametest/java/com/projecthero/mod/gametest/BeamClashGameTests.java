package com.projecthero.mod.gametest;

import com.projecthero.mod.beamclash.BeamClash;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 beam clashing, on a mock player and a zombie 6 blocks apart, inside the walled test area. Reports are read at the start of the next server tick,
 * so these keep reporting from a per-tick sequence, as a real beam does.
 */
public class BeamClashGameTests implements FabricGameTest {
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 0.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static Zombie zombie(GameTestHelper helper) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 6));
		z.setNoAi(true);
		return z;
	}

	private static void fire(ServerPlayer p, Zombie z, float mobPower) {
		BeamClash.report(p, p.getEyePosition(), z.getEyePosition().subtract(p.getEyePosition()), 32, 1f, 0xFF0000);
		BeamClash.report(z, z.getEyePosition(), p.getEyePosition().subtract(z.getEyePosition()), 32, mobPower, 0x00FF00);
	}

	/** Runs {@code then} once, the first tick the player is locked; never locking times the test out. */
	private static void whenLocked(GameTestHelper helper, ServerPlayer p, Runnable then) {
		boolean[] done = { false };
		helper.onEachTick(() -> {
			if (!done[0] && BeamClash.inClash(p)) {
				done[0] = true;
				then.run();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void headOnBeamsLockAndCannotHurtEachOther(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		helper.onEachTick(() -> {
			if (z.isAlive() && p.isAlive()) {
				fire(p, z, 0.1f);
			}
		});
		whenLocked(helper, p, () -> {
			helper.assertTrue(BeamClash.inClash(z), "both sides lock");
			float hp = z.getHealth();
			z.hurt(p.damageSources().playerAttack(p), 5f);
			helper.assertTrue(z.getHealth() == hp, "the sides can't hurt each other");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void beamsPointingAwayNeverLock(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		helper.onEachTick(() -> {
			BeamClash.report(p, p.getEyePosition(), new Vec3(1, 0, 0), 32, 1f, 0);
			BeamClash.report(z, z.getEyePosition(), new Vec3(1, 0, 0), 32, 1f, 0);
		});
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!BeamClash.inClash(p), "no clash");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void letGoEndsItWithNoBlast(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		boolean[] firing = { true };
		helper.onEachTick(() -> {
			if (firing[0]) {
				fire(p, z, 0.1f);
			}
		});
		whenLocked(helper, p, () -> {
			firing[0] = false;
			helper.runAfterDelay(9, () -> {
				helper.assertTrue(!BeamClash.inClash(p), "the clash ends");
				helper.assertTrue(p.getHealth() == p.getMaxHealth(), "no backlash");
				helper.succeed();
			});
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aMashingPlayerPushesTheMobBackAndBlowsItUp(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		float hp = z.getHealth();
		boolean[] mash = { false };
		helper.onEachTick(() -> {
			if (z.isAlive()) {
				fire(p, z, 0.1f);
			}
			if (mash[0]) {
				BeamClash.mash(p);
			}
		});
		whenLocked(helper, p, () -> {
			mash[0] = true;
			helper.runAfterDelay(100, () -> {
				helper.assertTrue(!BeamClash.inClash(p), "the clash is over");
				helper.assertTrue(!z.isAlive() || z.getHealth() < hp, "the loser takes the backlash");
				helper.succeed();
			});
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void anIdlePlayerLosesToTheMob(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		helper.onEachTick(() -> {
			if (p.isAlive() && z.isAlive()) {
				fire(p, z, 1.5f);
			}
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(p.getHealth() < p.getMaxHealth(), "the backlash hits the player");
			helper.succeed();
		});
	}
}
