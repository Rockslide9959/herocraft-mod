package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkAbilities;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkDamage;
import com.projecthero.mod.hulk.WorldBreaker;
import com.projecthero.mod.hulk.data.HulkState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): World Breaker -- only at 100 rage (H), 10 blocks tall, every hit x2 on top of the rage bonus, every move's
 * area x2, 30 s then Burnout (shrunk, Slowness II / Weakness II / Mining Fatigue, rage frozen at 0) and a 5-minute cooldown,
 * and dying in it cleans up. Mock players never tick, so each test drives {@link Hulk#tick} itself. GameTest worlds are
 * Peaceful: pigs, not monsters.
 */
public class HulkWorldBreakerV0164GameTests implements FabricGameTest {
	/** Out as the Hulk, the change over but the 3-minute lock still on (so a refused H can't change him back). */
	private static ServerPlayer hulk(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getAbilities().invulnerable = false;
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		// room for a 10-block Hulk (3.3 wide)
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 13, 3))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		helper.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p) && !Hulk.changing(p), "precondition: the Hulk, ready");
		// nothing of the minute's rampage clock during the test
		edit(p, n -> n.rules.nextRampageAt = now(p) + 20L * 60L * 10L);
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<HulkState> change) {
		HulkState n = Hulk.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.HULK_STATE, n);
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static Pig pig(GameTestHelper helper, Vec3 at, double health) {
		Pig pig = EntityType.PIG.create(helper.getLevel());
		pig.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		pig.setNoAi(true);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		pig.setHealth((float) health);
		helper.getLevel().addFreshEntity(pig);
		return pig;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_wb_activate")
	public void worldBreakerOnlyAtFullRage(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 99.0f);
		helper.assertFalse(WorldBreaker.available(p), "99 rage: not available");
		Hulk.pressH(p);
		helper.assertFalse(WorldBreaker.active(p), "H at 99 rage does not start it");
		helper.assertTrue(Hulk.isHulk(p), "(and the 3-minute lock keeps him the Hulk)");
		Hulk.setRage(p, 100.0f);
		helper.assertTrue(WorldBreaker.available(p), "100 rage: available (the HUD prompt)");
		Hulk.pressH(p);
		helper.assertTrue(WorldBreaker.active(p), "H at 100 rage starts World Breaker");
		helper.assertTrue(Hulk.isHulk(p), "and never changes him back");
		helper.assertTrue(WorldBreaker.ticksLeft(p) == HulkConfig.worldBreaker().durationTicks, "for 30 s, got " + WorldBreaker.ticksLeft(p));
		Hulk.pressH(p);
		helper.assertTrue(WorldBreaker.active(p) && Hulk.isHulk(p), "H while it runs does nothing");
		Hulk.revoke(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "hulk_wb_size")
	public void worldBreakerGrowsHimToTenBlocks(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 100.0f);
		WorldBreaker.activate(p);
		helper.assertTrue(Math.abs(WorldBreaker.scaleBonus() - (10.0 / 1.8 - 1.0)) < 1.0e-6, "the target is 10 blocks (scale 5.56)");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(120, () -> {
			double scale = p.getAttributeValue(Attributes.SCALE);
			helper.assertTrue(Math.abs(scale - 10.0 / 1.8) < 0.02, "World Breaker: 10 blocks tall (scale 5.56), got " + scale);
			p.refreshDimensions();
			helper.assertTrue(Math.abs(p.getBbHeight() - 10.0) < 0.06, "the hitbox follows, got " + p.getBbHeight());
			Hulk.revoke(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_wb_damage")
	public void worldBreakerDoublesEveryHit(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 100.0f);
		Pig a = pig(helper, p.position().add(2.0, 0, 0), 200.0);
		Pig b = pig(helper, p.position().add(-2.0, 0, 0), 200.0);
		helper.assertFalse(HulkDamage.allowDamage(a, p.damageSources().playerAttack(p), 10.0f), "100 rage: re-applied bigger");
		float normal = 200.0f - a.getHealth();
		WorldBreaker.activate(p);
		b.setHealth(200.0f); // the activation shockwave may have reached it
		b.invulnerableTime = 0;
		helper.assertFalse(HulkDamage.allowDamage(b, p.damageSources().playerAttack(p), 10.0f), "World Breaker: re-applied bigger still");
		float wb = 200.0f - b.getHealth();
		helper.assertTrue(Math.abs(normal - 50.0f / 3.0f) < 0.01f, "100 rage: 10 becomes 16.7, got " + normal);
		helper.assertTrue(Math.abs(wb - 2.0f * normal) < 0.01f, "World Breaker: twice that (33.3), got " + wb);
		helper.assertTrue(Math.abs(Hulk.damageMultiplier(p) - 10.0f / 3.0f) < 1.0e-4f, "the multiplier is 1.667 x 2, got " + Hulk.damageMultiplier(p));
		a.discard();
		b.discard();
		Hulk.revoke(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "hulk_wb_aoe")
	public void worldBreakerDoublesAMovesArea(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 100.0f);
		double radius = HulkConfig.abilities().gammaBurstRadius;
		// past the normal Gamma Burst (10) but inside the doubled one (20) -- and past the activation shockwave (14)
		Pig far = pig(helper, p.position().add(0, 0, radius * 1.6), 200.0);
		helper.onEachTick(() -> Hulk.tick(p));
		HulkAbilities.gammaBurst(p);
		helper.runAfterDelay(HulkAbilities.GAMMA_BURST_IMPACT_TICKS + 3, () -> {
			helper.assertTrue(far.getHealth() >= far.getMaxHealth(), "a normal Gamma Burst doesn't reach 16 blocks");
			WorldBreaker.activate(p);
			far.setHealth(200.0f);
			edit(p, n -> n.abilityReadyAt.clear());
			HulkAbilities.gammaBurst(p);
		});
		helper.runAfterDelay(2 * (HulkAbilities.GAMMA_BURST_IMPACT_TICKS + 3), () -> {
			helper.assertTrue(far.getHealth() < far.getMaxHealth(), "World Breaker's doubled burst does");
			far.discard();
			Hulk.revoke(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "hulk_wb_burnout")
	public void worldBreakerEndsInBurnoutAndCoolsDown(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 100.0f);
		WorldBreaker.activate(p);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(WorldBreaker.active(p), "still running");
			Hulk.onHurt(p, 0.0f);
			helper.assertTrue(Hulk.rage(p) >= 100.0f, "rage pinned at 100");
			// fast-forward to the end of the 30 s
			edit(p, n -> n.moves.wbUntil = now(p));
		});
		helper.runAfterDelay(8, () -> {
			helper.assertFalse(WorldBreaker.active(p), "30 s later it is over");
			helper.assertTrue(WorldBreaker.burnout(p), "into Burnout");
			helper.assertTrue(Hulk.rage(p) == 0.0f, "rage dropped to 0, got " + Hulk.rage(p));
			var slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			var weak = p.getEffect(MobEffects.WEAKNESS);
			var tired = p.getEffect(MobEffects.DIG_SLOWDOWN);
			helper.assertTrue(slow != null && slow.getAmplifier() == 1, "Slowness II");
			helper.assertTrue(weak != null && weak.getAmplifier() == 1, "Weakness II");
			helper.assertTrue(tired != null && tired.getAmplifier() == 0, "Mining Fatigue I");
			helper.assertTrue(slow.getDuration() > HulkConfig.worldBreaker().burnoutTicks - 20, "for a minute");
			Hulk.onHurt(p, 30.0f);
			Hulk.onDealt(p, 10.0f);
			helper.assertTrue(Hulk.rage(p) == 0.0f, "rage can't build in Burnout, got " + Hulk.rage(p));
			// even at 100 rage World Breaker is on its cooldown
			Hulk.setRage(p, 100.0f);
			helper.assertFalse(WorldBreaker.available(p), "on cooldown: not available");
			Hulk.pressH(p);
			helper.assertFalse(WorldBreaker.active(p), "H can't start it again");
			// Burnout over, the 5 minutes still running
			edit(p, n -> {
				n.moves.wbBurnoutUntil = now(p);
				n.rage = 100.0f;
			});
			helper.assertTrue(WorldBreaker.coolingDown(p), "the cooldown outlasts the Burnout");
			helper.assertFalse(WorldBreaker.available(p), "still not available");
			long readyIn = Hulk.state(p).moves.wbReadyAt - now(p);
			helper.assertTrue(readyIn > HulkConfig.worldBreaker().cooldownTicks - 30, "5 minutes from the end, got " + readyIn);
			// and once it is over, it is back
			edit(p, n -> n.moves.wbReadyAt = now(p));
			helper.assertTrue(WorldBreaker.available(p), "after the cooldown it is back");
			Hulk.revoke(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "hulk_wb_death")
	public void dyingInWorldBreakerCleansUp(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 100.0f);
		WorldBreaker.activate(p);
		helper.onEachTick(() -> {
			if (p.isAlive()) {
				Hulk.tick(p);
			}
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(Hulk.scaleBonus(p) > 3.0, "he has grown well past 6 blocks, got scale bonus " + Hulk.scaleBonus(p));
			helper.assertTrue(Hulk.tryDeathSave(p, p.damageSources().generic()), "the killing blow is caught");
			helper.assertFalse(WorldBreaker.active(p), "dying ends World Breaker at once");
			helper.assertTrue(WorldBreaker.coolingDown(p), "(its cooldown still counts)");
			helper.assertTrue(p.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "no Burnout debuffs on a dying Hulk");
		});
		helper.runAfterDelay(60 + HulkConfig.DEATH_LIE_TICKS + HulkConfig.DEATH_REVERT_TICKS + 5, () -> {
			helper.assertFalse(Hulk.isHulk(p), "he changed back");
			helper.assertTrue(Hulk.scaleBonus(p) < 0.01, "not stuck at 10 blocks: back to his own size, got " + Hulk.scaleBonus(p));
		});
		helper.runAfterDelay(60 + HulkConfig.DEATH_LIE_TICKS + HulkConfig.DEATH_REVERT_TICKS + HulkConfig.DEATH_FINAL_DELAY_TICKS + 8, () -> {
			helper.assertTrue(p.isDeadOrDying(), "and the death went through");
			helper.assertFalse(WorldBreaker.active(p), "nothing of World Breaker is left running");
			helper.succeed();
		});
	}
}
