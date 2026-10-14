package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderStealth;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** v0.16.2: Spider-Man stealth -- detection range by stance, exposure, the boss / Warden exceptions, the ambush. */
public class SpiderStealthGameTests implements FabricGameTest {

	private static ServerPlayer spider(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY));
		SpiderMan.evolveFromAdhesion(p);
		SpiderStealth.clearFor(p);
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, Vec3 rel) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(rel);
		z.moveTo(at.x, at.y, at.z, 0f, 0f);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stealthShrinksDetectionBySneakAndExposureUndoesIt(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Vec3 me = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		p.moveTo(me.x, me.y, me.z, 0f, 0f);
		p.setOnGround(true);
		Zombie z = zombie(helper, new Vec3(6.5, 1.0, 6.5));
		p.setShiftKeyDown(false);
		double walking = SpiderStealth.factor(p, z);
		p.setShiftKeyDown(true);
		double sneaking = SpiderStealth.factor(p, z) * 0.8; // vanilla's own crouch 0.8 on top = the total
		helper.assertTrue(walking < 1.0 && sneaking < walking, "walking (" + walking + ") hides some, sneaking (" + sneaking + ") more");
		SpiderStealth.expose(p);
		helper.assertTrue(SpiderStealth.factor(p, z) * 0.8 >= 0.79, "fighting gives him away: back to vanilla");
		SpiderStealth.clearFor(p);
		Warden w = EntityType.WARDEN.create(helper.getLevel());
		w.moveTo(z.getX(), z.getY(), z.getZ(), 0f, 0f);
		helper.assertTrue(SpiderStealth.factor(p, w) == 1.0, "the Warden is never fooled");
		Zombie close = zombie(helper, new Vec3(2.2, 1.0, 1.5));
		helper.assertTrue(SpiderStealth.factor(p, close) == 1.0, "nor is anything right next to him");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anAmbushOnAnUnawareMobHitsHarderAndStuns(GameTestHelper helper) {
		ServerPlayer p = spider(helper);
		Vec3 me = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		p.moveTo(me.x, me.y, me.z, 0f, 0f);
		Zombie z = zombie(helper, new Vec3(3.5, 1.0, 1.5));
		float before = z.getHealth();
		AbilityHelpers.hurt(p, z, 4.0f);
		helper.assertTrue(before - z.getHealth() > 4.5f, "the ambush adds 50% (took " + (before - z.getHealth()) + ")");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and stuns it");
		Zombie z2 = zombie(helper, new Vec3(1.5, 1.0, 3.5));
		float before2 = z2.getHealth();
		AbilityHelpers.hurt(p, z2, 4.0f);
		helper.assertTrue(before2 - z2.getHealth() < 4.5f, "on cooldown: a second ambush right away is a plain hit");
		SpiderStealth.clearFor(p);
		helper.succeed();
	}
}
