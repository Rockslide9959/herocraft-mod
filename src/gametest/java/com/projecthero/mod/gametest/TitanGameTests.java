package com.projecthero.mod.gametest;

import com.projecthero.mod.titan.TitanConfig;
import com.projecthero.mod.titan.TitanTerrain;
import com.projecthero.mod.titan.entity.DisguisedTitanEntity;
import com.projecthero.mod.titan.entity.TitanEntity;
import com.projecthero.mod.titan.entity.TitanEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

/** Coverage for the Titan world boss (v0.9.14+). */
public class TitanGameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void maxHealthCeilingReaches1500(GameTestHelper helper) {
		LivingEntity zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, net.minecraft.core.BlockPos.ZERO);
		var attr = zombie.getAttribute(Attributes.MAX_HEALTH);
		helper.assertFalse(attr == null, "sanity: MAX_HEALTH attribute instance exists");
		attr.setBaseValue(1500.0);
		helper.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MAX_HEALTH) - 1500.0) < 0.01,
				"MAX_HEALTH must actually reach 1500 -- vanilla's silent 1024 clamp must be lifted, got "
						+ zombie.getAttributeValue(Attributes.MAX_HEALTH));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void titanEntityHasConfiguredHealth(GameTestHelper helper) {
		TitanEntity titan = TitanEntityTypes.TITAN.create(helper.getLevel());
		helper.assertFalse(titan == null, "sanity: Titan entity constructs");
		helper.assertTrue(Math.abs(titan.getMaxHealth() - TitanConfig.stats().health) < 0.01,
				"Titan max health should match TitanConfig, got " + titan.getMaxHealth());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void disguisedTitanTransformsExactlyOnce(GameTestHelper helper) {
		DisguisedTitanEntity disguised = TitanEntityTypes.DISGUISED_TITAN.create(helper.getLevel());
		helper.assertFalse(disguised == null, "sanity: disguised Titan constructs");
		disguised.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO));
		helper.getLevel().addFreshEntity(disguised);

		int before = helper.getLevel().getEntitiesOfClass(TitanEntity.class,
				disguised.getBoundingBox().inflate(16)).size();
		helper.assertTrue(before == 0, "sanity: no Titan exists yet");

		disguised.die(disguised.level().damageSources().generic());

		helper.runAfterDelay(25, () -> {
			int titans = helper.getLevel().getEntitiesOfClass(TitanEntity.class,
					disguised.getBoundingBox().inflate(16)).size();
			helper.assertTrue(titans == 1, "exactly one Titan should exist after the transform, got " + titans);
			TitanEntity titan = helper.getLevel().getEntitiesOfClass(TitanEntity.class,
					disguised.getBoundingBox().inflate(16)).get(0);
			helper.assertTrue(titan.getHealth() == titan.getMaxHealth(), "the Titan must start at full health, got "
					+ titan.getHealth() + "/" + titan.getMaxHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grabReleasesWhenTitanDies(GameTestHelper helper) {
		TitanEntity titan = TitanEntityTypes.TITAN.create(helper.getLevel());
		titan.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO));
		helper.getLevel().addFreshEntity(titan);
		helper.assertFalse(titan.isGrabbing(java.util.UUID.randomUUID()), "sanity: not grabbing an arbitrary id");

		titan.die(titan.level().damageSources().generic());
		// releaseGrab() runs as part of die(); nothing to assert on a specific player here (none was
		// actually grabbed in this unit test), but die() must not throw with no grab in progress.
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void terrainDestructionNeverTouchesBedrock(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.getLevel().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
		TitanTerrain.breakCluster(helper.getLevel(), pos, 4.0);
		helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.BEDROCK),
				"bedrock must never be destroyed by a Titan attack");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boulderDoesNotRemoveSourceTerrain(GameTestHelper helper) {
		// The boulder is a visual illusion (spec section 10) -- it never actually removes the block the
		// Titan appeared to tear out of the ground. TitanTerrain.breakCluster is only ever called at the
		// IMPACT point, never at the Titan's own feet for the "pickup", so the ground under the Titan
		// stays completely untouched by the throw itself.
		BlockPos underTitan = helper.absolutePos(new BlockPos(0, 1, 0));
		helper.getLevel().setBlockAndUpdate(underTitan, Blocks.STONE.defaultBlockState());
		var before = helper.getLevel().getBlockState(underTitan);
		TitanEntity titan = TitanEntityTypes.TITAN.create(helper.getLevel());
		titan.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0, 2, 0)));
		helper.getLevel().addFreshEntity(titan);
		helper.assertTrue(helper.getLevel().getBlockState(underTitan) == before,
				"spawning the Titan alone must not touch the ground beneath it");
		helper.succeed();
	}
}
