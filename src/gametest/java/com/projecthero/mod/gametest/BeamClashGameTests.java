package com.projecthero.mod.gametest;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianAbilities;

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
 * Beam clashing (v0.17.3; the v0.17.5 rework: forgiving start, locked in until someone wins, no drain, no damage or fire between
 * the two, Escape forfeits), on a mock player and a zombie 5.5 blocks apart, inside the walled test area. Reports are read at the
 * start of the next server tick, so these keep reporting from a per-tick sequence, as a real beam does.
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
		// a husk: a zombie that doesn't burn in the daylight (that would muddle the damage checks)
		Zombie z = helper.spawn(EntityType.HUSK, new BlockPos(1, 2, 6));
		z.setNoAi(true);
		return z;
	}

	private static void fire(ServerPlayer p, Zombie z, float mobPower) {
		BeamClash.report(p, p.getEyePosition(), z.getEyePosition().subtract(p.getEyePosition()), 32, 1f, 0xFF0000);
		zombieFires(p, z, mobPower);
	}

	private static void zombieFires(ServerPlayer p, Zombie z, float mobPower) {
		BeamClash.report(z, z.getEyePosition(), p.getEyePosition().subtract(z.getEyePosition()), 32, mobPower, 0x00FF00);
	}

	/** {@code v} turned {@code deg} degrees about the vertical. */
	private static Vec3 turn(Vec3 v, double deg) {
		double r = Math.toRadians(deg);
		return new Vec3(v.x * Math.cos(r) - v.z * Math.sin(r), v.y, v.x * Math.sin(r) + v.z * Math.cos(r));
	}

	/** Both beams aimed at the other's body, then both turned the same way (so the two beams run side by side, apart). */
	private static void fireOff(ServerPlayer p, Zombie z, double deg) {
		Vec3 pc = p.getBoundingBox().getCenter();
		Vec3 zc = z.getBoundingBox().getCenter();
		BeamClash.report(p, p.getEyePosition(), turn(zc.subtract(p.getEyePosition()), deg), 32, 1f, 0xFF0000);
		BeamClash.report(z, z.getEyePosition(), turn(pc.subtract(z.getEyePosition()), deg), 32, 0.1f, 0x00FF00);
	}

	/** A Kryptonian mock player, ticked by hand (mock players aren't reliably ticked), aimed at the zombie. */
	private static ServerPlayer kryptonian(GameTestHelper helper, Zombie z) {
		ServerPlayer p = player(helper);
		Kryptonian.grant(p);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getBoundingBox().getCenter());
		helper.onEachTick(() -> {
			p.tickCount++;
			Kryptonian.tick(p);
		});
		return p;
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

	/** v0.17.5: 22 degrees off, side by side more than the 2-block touch apart -- each is still aimed at the other, so it clashes. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void beamsAnImperfectAngleApartStillLock(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		helper.onEachTick(() -> {
			if (!BeamClash.inClash(p)) {
				fireOff(p, z, 22);
			}
		});
		whenLocked(helper, p, () -> {
			helper.assertTrue(BeamClash.inClash(z), "both sides lock");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void beamsFortyDegreesOffNeverLock(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		helper.onEachTick(() -> fireOff(p, z, 40));
		helper.runAfterDelay(10, () -> {
			helper.assertFalse(BeamClash.inClash(p), "too far off: no clash");
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

	/** v0.17.5: a short beam (a Saiyan's 1.5 s Kamehameha) that stops reporting stays locked in, and so does the other side. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 160)
	public void aShortBeamStaysOutForTheWholeClash(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		boolean[] firing = { true };
		helper.onEachTick(() -> {
			if (firing[0]) {
				fire(p, z, 0.1f);
			}
		});
		whenLocked(helper, p, () -> {
			firing[0] = false; // both beams' own timers run out / keys come up
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(BeamClash.inClash(p) && BeamClash.inClash(z), "still locked, 3 s after both beams stopped");
				helper.assertTrue(p.getHealth() == p.getMaxHealth(), "nobody hurt yet");
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

	/** The mob leans on the idle player: each tick moves the point by its push (step size, not a total over time). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void anIdlePlayerLosesToTheMob(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		float[] last = { Float.NaN };
		boolean[] stepped = { false };
		helper.onEachTick(() -> {
			if (p.isAlive() && z.isAlive()) {
				fire(p, z, 1.5f);
			}
			BeamClash.Clash c = BeamClash.clashOf(p);
			if (c != null) {
				float b = c.balance();
				if (!Float.isNaN(last[0]) && Math.abs(Math.abs(b - last[0]) - (BeamClash.MOB_PUSH * 1.5f + 0.0015f * 0.5f)) < 1.0e-4f) {
					stepped[0] = true;
				}
				last[0] = b;
			}
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(stepped[0], "the mob pushed by its power every tick");
			helper.assertTrue(p.getHealth() < p.getMaxHealth(), "the backlash hits the player");
			helper.succeed();
		});
	}

	/** v0.17.5: Escape forfeits: the other side wins, the quitter takes no damage, and both are let go. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void forfeitingHandsTheOtherSideTheWin(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Zombie z = zombie(helper);
		float zhp = z.getHealth();
		boolean[] firing = { true };
		helper.onEachTick(() -> {
			if (firing[0]) {
				fire(p, z, 0.1f);
			}
		});
		whenLocked(helper, p, () -> {
			firing[0] = false;
			BeamClash.Clash c = BeamClash.clashOf(p);
			BeamClash.forfeit(p);
			helper.assertTrue(c.over() && c.winner() == z, "the zombie wins");
			helper.assertFalse(BeamClash.inClash(p) || BeamClash.inClash(z), "both let go");
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(p.getHealth() == p.getMaxHealth(), "no finisher on a forfeit");
				helper.assertTrue(z.getHealth() == zhp, "the winner is untouched");
				helper.succeed();
			});
		});
	}

	/**
	 * v0.17.5 (user: "releasing your laser shooting ability keybind shouldnt cancel the beam clash", "it shouldnt drain extra energy"):
	 * real Kryptonian Heat Vision against the zombie's beam. Locked in, no Solar Energy payment lands (no downward step), the key coming
	 * up doesn't end the clash.
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void heatVisionInAClashCostsNothingAndLettingGoKeepsIt(GameTestHelper helper) {
		Zombie z = zombie(helper);
		ServerPlayer p = kryptonian(helper, z);
		KryptonianAbilities.startHeatVision(p);
		helper.assertTrue(Kryptonian.heatVisionActive(p), "the beam is on");
		boolean[] locked = { false };
		float[] last = { Float.NaN };
		boolean[] paid = { false };
		helper.onEachTick(() -> {
			if (z.isAlive()) {
				zombieFires(p, z, 0.05f);
			}
			if (locked[0]) {
				float now = Kryptonian.solar(p);
				if (!Float.isNaN(last[0]) && now < last[0] - 1.0e-4f) {
					paid[0] = true;
				}
				last[0] = now;
			}
		});
		whenLocked(helper, p, () -> {
			locked[0] = true;
			helper.runAfterDelay(40, () -> {
				helper.assertFalse(paid[0], "no Solar Energy paid during the clash (a payment is a -0.5 step every 10 ticks)");
				KryptonianAbilities.stopHeatVision(p); // the key comes up
				helper.runAfterDelay(10, () -> {
					helper.assertTrue(BeamClash.inClash(p), "letting go of the key doesn't end the clash");
					helper.succeed();
				});
			});
		});
	}

	/** v0.17.5 (user: "sentry gets set on fire or I take damage from the laser vision"): the partner takes no beam damage and no fire. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theClashPartnerTakesNoBeamDamageOrFire(GameTestHelper helper) {
		Zombie z = zombie(helper);
		ServerPlayer p = kryptonian(helper, z);
		KryptonianAbilities.startHeatVision(p);
		boolean[] hurt = { false };
		boolean[] locked = { false };
		float[] hp = { 0f };
		helper.onEachTick(() -> {
			if (z.isAlive()) {
				zombieFires(p, z, 0.05f);
			}
			if (locked[0] && BeamClash.inClash(z) && (z.getHealth() < hp[0] || z.isOnFire())) {
				hurt[0] = true;
			}
		});
		whenLocked(helper, p, () -> {
			locked[0] = true;
			hp[0] = z.getHealth(); // a hit can land in the tick before the clash forms
			z.igniteForSeconds(5f); // anything else trying to light a locked shooter
			helper.runAfterDelay(40, () -> {
				helper.assertFalse(hurt[0], "no heat-vision damage or burning on the clash partner");
				helper.succeed();
			});
		});
	}

	/** Outside a clash the same beam still burns and hurts. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void heatVisionStillHurtsOutsideAClash(GameTestHelper helper) {
		Zombie z = zombie(helper);
		ServerPlayer p = kryptonian(helper, z);
		KryptonianAbilities.startHeatVision(p);
		helper.runAfterDelay(30, () -> {
			helper.assertFalse(BeamClash.inClash(p), "no clash: the zombie fires nothing");
			helper.assertTrue(z.getHealth() < z.getMaxHealth() || !z.isAlive(), "heat vision hurts it");
			helper.succeed();
		});
	}
}
