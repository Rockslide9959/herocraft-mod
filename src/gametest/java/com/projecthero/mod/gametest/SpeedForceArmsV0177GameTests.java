package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.speedforce.SpeedForceAbilities;
import com.projecthero.mod.speedforce.SpeedForceConfig;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user): Tornado Arms aimed at the ground lifts the speedster, hits for 5 a damage tick, and puts out fire anywhere in
 * the cone (it used to look only 3 blocks above / below the arms) -- campfires too.
 */
public class SpeedForceArmsV0177GameTests implements FabricGameTest {

	private static ServerPlayer speedster(GameTestHelper helper, double x, double y, double z, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(PowerGrants.grantHero(p, "speed_force_speedster", true).ok(), "granted the Speed Force");
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0f, pitch);
		p.setYRot(0f);
		p.setXRot(pitch);
		p.setYHeadRot(0f);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aimedAtTheGroundTheWindLiftsHim(GameTestHelper helper) {
		ServerPlayer p = speedster(helper, 3.5, 2.0, 3.5, 90f);
		p.setDeltaMovement(0, -0.2, 0);
		Vec3 look = p.getLookAngle();
		Vec3 apex = p.getEyePosition().subtract(0, 0.5, 0);
		helper.assertTrue(SpeedForceAbilities.armsLift(p, helper.getLevel(), apex, look, SpeedForceConfig.ARMS_RANGE), "aimed straight down at the floor: it lifts");
		helper.assertTrue(p.getDeltaMovement().y >= SpeedForceConfig.ARMS_SELF_RISE - 1.0e-6, "rising at " + p.getDeltaMovement().y);
		p.setXRot(0f);
		p.setDeltaMovement(0, -0.2, 0);
		helper.assertFalse(SpeedForceAbilities.armsLift(p, helper.getLevel(), apex, p.getLookAngle(), SpeedForceConfig.ARMS_RANGE), "aimed level: no lift");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void itHitsForFiveADamageTick(GameTestHelper helper) {
		ServerPlayer p = speedster(helper, 3.5, 2.0, 1.5, 0f);
		Husk h = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 4));
		float full = h.getHealth();
		helper.assertTrue(SpeedForceAbilities.startTornadoArms(p), "Tornado Arms starts");
		p.tickCount = SpeedForceConfig.ARMS_DAMAGE_EVERY * 3; // a damage tick
		h.invulnerableTime = 0;
		SpeedForceAbilities.armsTick(p);
		float lost = full - h.getHealth();
		// 5 base, then the Speed Force's own scaling every move gets (momentum / drain) and any global damage hooks: assert a hit near 5
		float base = SpeedForceConfig.ARMS_DAMAGE * com.projecthero.mod.speedforce.SpeedForceSpeedster.damageScale(p);
		helper.assertTrue(lost >= base - 0.6f && lost <= base * 1.35f, "about 5 on a damage tick (scaled " + base + "), took " + lost);
		float after = h.getHealth();
		p.tickCount++;
		h.invulnerableTime = 0;
		SpeedForceAbilities.armsTick(p);
		helper.assertTrue(h.getHealth() == after, "nothing between damage ticks");
		SpeedForceAbilities.endTornadoArms(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void itPutsOutFireDeepInTheConeAndCampfires(GameTestHelper helper) {
		// standing high, looking steeply down: the fire is 5 blocks below the arms (the old check stopped at 3)
		ServerPlayer p = speedster(helper, 3.5, 6.0, 1.5, 55f);
		BlockPos fireAt = new BlockPos(3, 1, 4);
		BlockPos campAt = new BlockPos(3, 1, 5);
		helper.setBlock(fireAt.below(), Blocks.STONE);
		helper.setBlock(fireAt, Blocks.FIRE);
		helper.setBlock(campAt, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		p.setRemainingFireTicks(100);
		helper.assertTrue(SpeedForceAbilities.startTornadoArms(p), "Tornado Arms starts");
		Vec3 apex = p.getEyePosition().subtract(0, 0.5, 0);
		int out = SpeedForceAbilities.putOutFires(p, helper.getLevel(), apex, SpeedForceConfig.ARMS_RANGE);
		helper.assertTrue(out >= 1, "something went out, " + out);
		helper.assertBlockNotPresent(Blocks.FIRE, fireAt);
		p.tickCount = 7;
		SpeedForceAbilities.armsTick(p);
		helper.assertFalse(p.isOnFire(), "his own flames go out too");
		SpeedForceAbilities.endTornadoArms(p);
		helper.succeed();
	}
}
