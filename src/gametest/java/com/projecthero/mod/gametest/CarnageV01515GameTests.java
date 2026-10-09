package com.projecthero.mod.gametest;

import com.projecthero.mod.carnage.CarnageConfig;
import com.projecthero.mod.carnage.CarnageEntityTypes;
import com.projecthero.mod.carnage.entity.CarnageAttackEntity;
import com.projecthero.mod.carnage.entity.CarnageEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** v0.15.15: Carnage's 1000 HP / iron armour / 2-per-2s regen (with config migration) and his four new moves. */
public class CarnageV01515GameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void carnageHealthArmourAndConfigMigration(GameTestHelper helper) {
		CarnageConfig fresh = CarnageConfig.SPEC.fresh();
		helper.assertTrue(fresh.boss.health == 1000.0 && fresh.boss.maxHealth == 3500.0, "defaults 1000 / cap 3500");
		helper.assertTrue(fresh.boss.regenAmount == 2.0 && fresh.boss.regenIntervalTicks == 40, "2 HP every 40 ticks");
		CarnageConfig old = CarnageConfig.SPEC.fromJson("{\"configVersion\":1,\"spawnChancePerMinute\":0.05,"
				+ "\"boss\":{\"health\":700.0,\"healthPerExtraFighter\":250.0,\"maxHealth\":3000.0}}");
		helper.assertTrue(old.boss.health == 1000.0, "a v1 file's 700 HP migrates to 1000, got " + old.boss.health);
		helper.assertTrue(old.boss.maxHealth == 3500.0, "and its cap to 3500, got " + old.boss.maxHealth);
		helper.assertTrue(old.boss.regenAmount == 2.0, "regen filled in");
		helper.assertTrue(old.spawnChancePerMinute == 0.05, "the server's own spawn chance is kept");

		CarnageEntity c = CarnageEntityTypes.CARNAGE.create(helper.getLevel());
		c.configure(1);
		helper.assertTrue(Math.abs(c.getMaxHealth() - 1000f) < 0.01f, "1000 for one fighter, got " + c.getMaxHealth());
		c.configure(3);
		helper.assertTrue(Math.abs(c.getMaxHealth() - 1500f) < 0.01f, "+250 per extra fighter, got " + c.getMaxHealth());
		c.configure(40);
		helper.assertTrue(Math.abs(c.getMaxHealth() - 3500f) < 0.01f, "capped at 3500, got " + c.getMaxHealth());
		helper.assertTrue(Math.abs(c.getAttributeValue(Attributes.ARMOR) - 15.0) < 0.01, "a full iron set of armour (15)");
		c.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carnageRegeneratesTwoEveryTwoSeconds(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		CarnageEntity c = spawnCarnage(helper);
		c.setNoAi(false);
		c.setHealth(500f);
		helper.runAfterDelay(81, () -> {
			float gained = c.getHealth() - 500f;
			c.discard();
			// two or three regen ticks in 81 ticks (it fires every 40 ticks of his own age)
			helper.assertTrue(gained >= 3.99f && gained <= 6.01f, "2 HP per 2 s, gained " + gained + " in ~4 s");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void carnageWhipSweepHits(GameTestHelper helper) {
		runMove(helper, "WHIP", new Vec3(0, 0, 4));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void carnageAxeCleaveHits(GameTestHelper helper) {
		runMove(helper, "CLEAVE", new Vec3(0, 0, 2.5));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void carnageSpikeEruptionHits(GameTestHelper helper) {
		runMove(helper, "ERUPT", new Vec3(0, 0, 4.5));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void carnageSnareHitsAndRoots(GameTestHelper helper) {
		Zombie victim = runMove(helper, "SNARE", new Vec3(0, 0, 5));
		helper.onEachTick(() -> {
			if (victim.getHealth() < victim.getMaxHealth()) {
				helper.assertTrue(CarnageAttackEntity.isSnared(victim) || !victim.isAlive(), "the glob roots who it hits");
			}
		});
	}

	private static CarnageEntity spawnCarnage(GameTestHelper helper) {
		CarnageEntity c = CarnageEntityTypes.CARNAGE.create(helper.getLevel());
		c.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)), 0f, 0f);
		c.configure(1);
		c.setNoAi(true);
		// neighbouring tests in the batch can set him burning or hit him, which makes him writhe and cuts the move short
		c.setInvulnerable(true);
		helper.getLevel().addFreshEntity(c);
		return c;
	}

	/** Carnage (NoAI, brain ticked by hand) starts {@code move} on a NoAI zombie at {@code offset}; passes once it is hurt. */
	private static Zombie runMove(GameTestHelper helper, String move, Vec3 offset) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		CarnageEntity c = spawnCarnage(helper);
		Zombie victim = EntityType.ZOMBIE.create(helper.getLevel());
		victim.moveTo(c.position().add(offset), 180f, 0f);
		victim.setNoAi(true);
		victim.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0, false, false));
		helper.getLevel().addFreshEntity(victim);
		c.setTarget(victim);
		helper.assertTrue(c.debugStartMove(move), move + " starts");
		helper.onEachTick(() -> {
			if (c.isAlive()) {
				// v0.15.16: neighbouring tests can shove either mob around (it failed twice in full runs) -- until the move
				// lands, keep the zombie pinned on its spot in front of him
				if (victim.isAlive() && victim.getHealth() >= victim.getMaxHealth()) {
					Vec3 spot = c.position().add(offset);
					victim.teleportTo(spot.x, spot.y, spot.z);
					victim.setDeltaMovement(Vec3.ZERO);
				}
				c.setTarget(victim);
				if (c.debugMoveIdle() && victim.getHealth() >= victim.getMaxHealth()) {
					c.debugStartMove(move);
				}
				c.debugTickBrain();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(victim.getHealth() < victim.getMaxHealth(), move + " has not hurt the zombie yet");
			for (CarnageAttackEntity a : helper.getLevel().getEntitiesOfClass(CarnageAttackEntity.class, new AABB(c.blockPosition()).inflate(16))) {
				a.discard();
			}
			c.discard();
			victim.discard();
		});
		return victim;
	}
}
