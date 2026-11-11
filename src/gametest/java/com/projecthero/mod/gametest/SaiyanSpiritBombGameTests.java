package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.data.SaiyanState;
import com.projecthero.mod.saiyan.kit.SaiyanSpiritBomb;
import com.projecthero.mod.saiyan.kit.SpiritBombState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Saiyan revamp: the Spirit Bomb (Shift+Z). The rise, the charge's even growth and Ki draw, damage at full and partial
 * charge, the caster spared, the crater only with mobGriefing (and never through bedrock), the cooldown, the fizzle. Mock
 * players are not ticked by the server, so every test drives {@link SaiyanSpiritBomb#tick} (a second call in a tick is a
 * no-op). Explosions use {@link SaiyanSpiritBomb#detonateForTest} with a small radius so nothing reaches the next test.
 */
public class SaiyanSpiritBombGameTests implements FabricGameTest {
	private static ServerPlayer saiyan(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		SaiyanState s = new SaiyanState();
		s.hasPower = true;
		s.ki = 100f;
		p.setAttached(ModAttachments.SAIYAN_STATE, s);
		p.setAttached(SpiritBombState.TYPE, SpiritBombState.NONE);
		helper.onEachTick(() -> SaiyanSpiritBomb.tick(p));
		return p;
	}

	private static ServerPlayer saiyan(GameTestHelper helper) {
		return saiyan(helper, new Vec3(2.5, 2.0, 2.5));
	}

	/** A still, armourless zombie with 1024 health whose middle is at {@code rel}. */
	private static Zombie target(GameTestHelper helper, Vec3 rel) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		z.setNoAi(true);
		z.setNoGravity(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0);
		z.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
		z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		z.setHealth(1024);
		z.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0, false, false));
		Vec3 at = helper.absoluteVec(rel);
		z.moveTo(at.x, at.y - z.getBbHeight() * 0.5, at.z, 0f, 0f);
		return z;
	}

	private static Vec3 middle(Zombie z) {
		return z.getBoundingBox().getCenter();
	}

	private static void griefing(GameTestHelper helper, boolean on) {
		helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(on, helper.getLevel().getServer());
	}

	private static boolean griefing(GameTestHelper helper) {
		return helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 120)
	public void spiritbombRisesTenBlocksThenCharges(GameTestHelper helper) {
		helper.assertTrue(SaiyanSpiritBomb.riseShare(0) == 0.0 && SaiyanSpiritBomb.riseShare(SaiyanSpiritBomb.RISE_TICKS) == 1.0, "the rise curve ends at 0 and 1");
		for (int t = 1; t <= SaiyanSpiritBomb.RISE_TICKS; t++) {
			helper.assertTrue(SaiyanSpiritBomb.riseShare(t) > SaiyanSpiritBomb.riseShare(t - 1), "the rise only goes up");
		}
		ServerPlayer p = saiyan(helper);
		double startY = p.getY();
		SaiyanSpiritBomb.press(p, true);
		helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.RISE, "Shift+Z starts the rise");
		helper.assertTrue(Math.abs(SaiyanSpiritBomb.hoverY(p) - (startY + SaiyanSpiritBomb.RISE_HEIGHT)) < 1.0e-6, "the hover is 10 up: " + SaiyanSpiritBomb.hoverY(p));
		helper.assertTrue(SaiyanSpiritBomb.busy(p), "rooted while rising");
		helper.assertTrue(p.getAbilities().mayfly, "held in the air without a flight kick");
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS / 2, () -> {
			double up = p.getY() - startY;
			helper.assertTrue(up > 2.0 && up < 8.5, "half way up mid-rise: " + up);
		});
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS + 3, () -> {
			double up = p.getY() - startY;
			helper.assertTrue(Math.abs(up - SaiyanSpiritBomb.RISE_HEIGHT) < 0.7, "10 blocks up: " + up);
			helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.CHARGE, "then charging");
			SaiyanSpiritBomb.press(p, false); // too little gathered: nothing thrown
		});
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS + 5, () -> {
			helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.NONE, "letting go that early ends it");
			helper.assertTrue(!p.getAbilities().mayfly, "and his borrowed flight is gone");
			helper.assertTrue(SaiyanSpiritBomb.cooldownRemaining(p) <= SaiyanSpiritBomb.CANCEL_COOLDOWN, "only a short cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 120)
	public void spiritbombGrowsInEvenStepsAndDrawsKi(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		SaiyanSpiritBomb.press(p, true);
		int from = SaiyanSpiritBomb.RISE_TICKS + 3;
		int steps = 8;
		float[] radius = new float[steps + 1];
		float[] ki = new float[steps + 1];
		long start = helper.getLevel().getGameTime();
		helper.onEachTick(() -> {
			// tick first (a no-op if already ticked this tick), then read: the order of test callbacks must not matter
			SaiyanSpiritBomb.tick(p);
			int k = (int) (helper.getLevel().getGameTime() - start) - from;
			if (k >= 0 && k <= steps) {
				radius[k] = SaiyanSpiritBomb.heldRadius(p);
				ki[k] = Saiyan.ki(p);
			}
		});
		helper.runAfterDelay(from + steps + 3, () -> {
			for (int i = 1; i <= steps; i++) {
				float dr = radius[i] - radius[i - 1];
				float dk = ki[i - 1] - ki[i];
				helper.assertTrue(Math.abs(dr - SaiyanSpiritBomb.RADIUS_PER_TICK) < 1.0e-3, "step " + i + " grows by " + dr + ", not " + SaiyanSpiritBomb.RADIUS_PER_TICK);
				// the core's Base-form trickle may run alongside (the server ticks Saiyans), so allow for one tick of it
				float regen = com.projecthero.mod.saiyan.SaiyanConfig.KI_REGEN_PER_TICK;
				helper.assertTrue(dk > SaiyanSpiritBomb.KI_PER_TICK - regen - 1.0e-3 && dk < SaiyanSpiritBomb.KI_PER_TICK + 1.0e-3,
						"step " + i + " draws " + dk + " Ki, not " + SaiyanSpiritBomb.KI_PER_TICK);
				if (i > 1) {
					float prev = ki[i - 2] - ki[i - 1];
					helper.assertTrue(Math.abs(dk - prev) < 1.0e-3, "even Ki steps: " + prev + " then " + dk);
				}
			}
			helper.assertTrue(Math.abs(SaiyanSpiritBomb.RADIUS_PER_TICK * SaiyanSpiritBomb.CHARGE_TICKS - (SaiyanSpiritBomb.RADIUS_MAX - SaiyanSpiritBomb.RADIUS_MIN)) < 1.0e-3,
					"10 seconds from tiny to the full 20-block radius");
			helper.assertTrue(Math.abs(SaiyanSpiritBomb.KI_PER_TICK * SaiyanSpiritBomb.CHARGE_TICKS - 100f) < 1.0e-3, "100 Ki for a full charge");
			SpiritBombState s = SaiyanSpiritBomb.state(p);
			helper.assertTrue(s.rate() == SaiyanSpiritBomb.RADIUS_PER_TICK, "clients are told it is growing");
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(Math.abs(s.radiusAt(now) - SaiyanSpiritBomb.heldRadius(p)) < 0.15f,
					"the synced size matches the server's: " + s.radiusAt(now) + " vs " + SaiyanSpiritBomb.heldRadius(p));
			// out of Ki: it stops growing
			Saiyan.setKi(p, 0.2f);
		});
		float[] stalled = new float[2];
		helper.runAfterDelay(from + steps + 6, () -> stalled[0] = SaiyanSpiritBomb.heldRadius(p));
		helper.runAfterDelay(from + steps + 10, () -> {
			stalled[1] = SaiyanSpiritBomb.heldRadius(p);
			// out of Ki it only creeps on what the core's trickle gives back: at most a step in these 4 ticks, not 4
			float grew = stalled[1] - stalled[0];
			helper.assertTrue(stalled[0] > 0f && grew <= SaiyanSpiritBomb.RADIUS_PER_TICK * 1.5f,
					"no Ki, (almost) no growth: " + stalled[0] + " -> " + stalled[1]);
			SaiyanSpiritBomb.press(p, false);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spiritbombDamageScalesWithCharge(GameTestHelper helper) {
		helper.assertTrue(SaiyanSpiritBomb.damageAt(1f, 0.0) == 1000f, "1000 at the centre of a full charge");
		helper.assertTrue(Math.abs(SaiyanSpiritBomb.damageAt(0.5f, 0.0) - 500f) < 1.0e-3, "half a charge, half the damage");
		helper.assertTrue(Math.abs(SaiyanSpiritBomb.damageAt(1f, 1.0) - 1000f * SaiyanSpiritBomb.EDGE_DAMAGE) < 1.0e-3, "less at the edge");
		helper.assertTrue(SaiyanSpiritBomb.damageAt(1f, 1.01) == 0f, "nothing outside");
		helper.assertTrue(SaiyanSpiritBomb.radiusFor(1f) == 20f && SaiyanSpiritBomb.radiusFor(0f) == SaiyanSpiritBomb.RADIUS_MIN, "20-block radius at full");
		ServerPlayer p = saiyan(helper, new Vec3(0.5, 2.0, 0.5));
		Zombie full = target(helper, new Vec3(3.5, 3.0, 2.5));
		Zombie part = target(helper, new Vec3(5.5, 3.0, 6.5));
		boolean was = griefing(helper);
		griefing(helper, false);
		SaiyanSpiritBomb.detonateForTest(p, middle(full), 1.5f, 1f);
		Vec3 c = middle(part).add(1.0, 0.0, 0.0);
		SaiyanSpiritBomb.detonateForTest(p, c, 2.0f, 0.4f);
		griefing(helper, was);
		float takenFull = 1024f - full.getHealth();
		helper.assertTrue(Math.abs(takenFull - 1000f) < 1.0f, "a full charge does 1000: " + takenFull);
		double d = Math.max(0.0, middle(part).distanceTo(c) - part.getBbWidth() * 0.5);
		float want = SaiyanSpiritBomb.damageAt(0.4f, d / 2.0);
		float takenPart = 1024f - part.getHealth();
		helper.assertTrue(want > 100f && want < 400f, "a partial hit off-centre: " + want);
		helper.assertTrue(Math.abs(takenPart - want) < 1.0f, "a 40% charge off-centre does " + takenPart + ", not " + want);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spiritbombSparesItsCaster(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper, new Vec3(3.5, 2.0, 3.5));
		Zombie z = target(helper, new Vec3(5.0, 3.0, 3.5));
		float hp = p.getHealth();
		boolean was = griefing(helper);
		griefing(helper, false);
		SaiyanSpiritBomb.detonateForTest(p, p.getBoundingBox().getCenter(), 3f, 1f);
		griefing(helper, was);
		helper.assertTrue(p.getHealth() == hp && p.isAlive(), "the caster takes nothing: " + p.getHealth());
		helper.assertTrue(z.getHealth() < 1024f - 300f, "the zombie beside him does: " + z.getHealth());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spiritbombCratersOnlyWithMobGriefing(GameTestHelper helper) {
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.BEDROCK);
		ServerPlayer p = saiyan(helper, new Vec3(6.5, 2.0, 6.5));
		Vec3 hit = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		boolean was = griefing(helper);
		griefing(helper, false);
		SaiyanSpiritBomb.detonateForTest(p, hit, 2.5f, 0.1f);
		griefing(helper, was);
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 3));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
		griefing(helper, true);
		SaiyanSpiritBomb.detonateForTest(p, hit, 2.5f, 0.1f);
		griefing(helper, was);
		helper.runAfterDelay(2, () -> {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 3));
			helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(4, 1, 4));
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, 1, 6));
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 6));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 120)
	public void spiritbombThrowStartsTheCooldown(GameTestHelper helper) {
		helper.assertTrue(SaiyanSpiritBomb.maxCooldown() == 6000, "five minutes");
		ServerPlayer p = saiyan(helper);
		p.setXRot(-60f); // up and away: out over the sky, nowhere near another test
		SaiyanSpiritBomb.press(p, true);
		int flying = SaiyanSpiritBomb.flightsInAir();
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS + 2, () -> {
			SaiyanSpiritBomb.forceChargeForTest(p, 0.1f);
			SaiyanSpiritBomb.press(p, false);
			helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.THROW, "thrown");
			helper.assertTrue(SaiyanSpiritBomb.flightsInAir() > flying, "a bomb is in the air");
			int cd = SaiyanSpiritBomb.cooldownRemaining(p);
			helper.assertTrue(Math.abs(cd - SaiyanSpiritBomb.COOLDOWN) <= 1, "the full cooldown: " + cd);
		});
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS + SaiyanSpiritBomb.THROW_TICKS + 5, () -> {
			helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.NONE, "the throw ends");
			helper.assertTrue(!SaiyanSpiritBomb.busy(p), "free again");
			SaiyanSpiritBomb.press(p, true);
			helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.NONE, "on cooldown: a second press does nothing");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 120)
	public void spiritbombFizzlesWhenHitHard(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		SaiyanSpiritBomb.press(p, true);
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS + 6, () -> {
			helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.CHARGE, "charging");
			// the check is on health lost in a tick (a fresh mock player is still spawn-invulnerable to hurt())
			p.setHealth(p.getHealth() - 10f);
		});
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS + 8, () -> {
			helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.NONE, "a hard hit breaks the charge");
			int cd = SaiyanSpiritBomb.cooldownRemaining(p);
			helper.assertTrue(Math.abs(cd - SaiyanSpiritBomb.FIZZLE_COOLDOWN) <= 3, "half the cooldown: " + cd);
			helper.assertTrue(Saiyan.ki(p) < 100f - 1f, "the Ki already drawn is lost: " + Saiyan.ki(p));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 120)
	public void spiritbombFizzlesWhenThePowerIsLost(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		SaiyanSpiritBomb.press(p, true);
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS + 4, () -> {
			SaiyanState s = Saiyan.state(p).copy();
			s.hasPower = false;
			p.setAttached(ModAttachments.SAIYAN_STATE, s);
		});
		helper.runAfterDelay(SaiyanSpiritBomb.RISE_TICKS + 6, () -> {
			helper.assertTrue(SaiyanSpiritBomb.phase(p) == SaiyanSpiritBomb.NONE, "losing the power ends it");
			int cd = SaiyanSpiritBomb.cooldownRemaining(p);
			helper.assertTrue(Math.abs(cd - SaiyanSpiritBomb.FIZZLE_COOLDOWN) <= 3, "half the cooldown: " + cd);
			helper.assertTrue(!p.getAbilities().mayfly, "no flight left over");
			helper.succeed();
		});
	}
}
