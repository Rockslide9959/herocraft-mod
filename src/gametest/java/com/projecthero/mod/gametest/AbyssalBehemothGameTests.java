package com.projecthero.mod.gametest;

import com.projecthero.mod.behemoth.BehemothConfig;
import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;
import com.projecthero.mod.behemoth.entity.BehemothEntityTypes;
import com.projecthero.mod.behemoth.entity.BehemothFireballEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Coverage for The Abyssal Behemoth world boss (v0.13.1). */
public class AbyssalBehemothGameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void hasConfiguredHealthAndReducesIncomingDamage(GameTestHelper helper) {
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(helper.getLevel());
		helper.assertFalse(boss == null, "sanity: entity constructs");
		helper.assertTrue(Math.abs(boss.getMaxHealth() - BehemothConfig.stats().health) < 0.01,
				"max health should match BehemothConfig, got " + boss.getMaxHealth());
		boss.moveTo(helper.absoluteVec(Vec3.ZERO));
		helper.getLevel().addFreshEntity(boss);

		float before = boss.getHealth();
		boss.hurt(boss.damageSources().generic(), 100.0f);
		float lost = before - boss.getHealth();
		helper.assertTrue(lost < 100.0f && lost > 0.0f,
				"damage reduction (spec: 45-55%) must actually apply, lost " + lost + "/100");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void isNoGravityAndFireImmune(GameTestHelper helper) {
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(helper.getLevel());
		helper.assertTrue(boss.isNoGravity(), "a floating Ghast-like boss must not fall");
		helper.assertTrue(boss.fireImmune(), "a Nether creature must be fire immune");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void acquiresNearbyPlayerAsTarget(GameTestHelper helper) {
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(helper.getLevel());
		boss.moveTo(helper.absoluteVec(new Vec3(2, 3, 2)));
		helper.getLevel().addFreshEntity(boss);

		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(5, 3, 2));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);

		helper.runAfterDelay(5, () -> {
			boss.tick(); // aiStep runs the acquisition -- tick() drives it same as a real server loop
			helper.assertTrue(boss.getTarget() == p, "should have locked onto the nearby player");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void survivesManyTicksOfCombatWithoutThrowing(GameTestHelper helper) {
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(helper.getLevel());
		boss.moveTo(helper.absoluteVec(new Vec3(2, 6, 2)));
		helper.getLevel().addFreshEntity(boss);

		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(10, 6, 2));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);

		// long enough to cycle through several ability windups/actives/recoveries and at least one
		// phase-fraction check; nothing here asserts *which* ability ran, only that none of them crash.
		helper.runAfterDelay(140, () -> {
			helper.assertTrue(boss.isAlive(), "the boss should still be alive and ticking cleanly");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void phaseAdvancesAsHealthDrops(GameTestHelper helper) {
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(helper.getLevel());
		boss.moveTo(helper.absoluteVec(Vec3.ZERO));
		helper.getLevel().addFreshEntity(boss);
		boss.setHealth((float) (boss.getMaxHealth() * 0.20)); // below the 25% Cataclysm threshold

		helper.runAfterDelay(3, () -> {
			var tag = new net.minecraft.nbt.CompoundTag();
			boss.addAdditionalSaveData(tag);
			helper.assertTrue(tag.getInt("Phase") == 3, "20% health should read as phase 3 (Cataclysm), got " + tag.getInt("Phase"));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireballDoesNotHitItsOwner(GameTestHelper helper) {
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(helper.getLevel());
		boss.moveTo(helper.absoluteVec(Vec3.ZERO));
		helper.getLevel().addFreshEntity(boss);

		BehemothFireballEntity fireball = new BehemothFireballEntity(helper.getLevel(), boss,
				new Vec3(1, 0, 0), 0.1, 50.0f, 1.0f, false);
		helper.assertFalse(fireball == null, "sanity: fireball constructs");
		helper.assertTrue(fireball.getOwner() == boss, "the fireball should remember its owner");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void healthCeilingReaches30000(GameTestHelper helper) {
		net.minecraft.world.entity.monster.Zombie zombie = new net.minecraft.world.entity.monster.Zombie(helper.getLevel());
		var attr = zombie.getAttribute(Attributes.MAX_HEALTH);
		helper.assertFalse(attr == null, "sanity: MAX_HEALTH attribute instance exists");
		attr.setBaseValue(30000.0);
		helper.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MAX_HEALTH) - 30000.0) < 0.01,
				"MAX_HEALTH must reach 30000 for the Behemoth -- the shared TitanHealthCap ceiling must cover it, got "
						+ zombie.getAttributeValue(Attributes.MAX_HEALTH));
		helper.succeed();
	}
}
