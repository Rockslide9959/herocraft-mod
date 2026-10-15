package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkAbilities;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkDamage;
import com.projecthero.mod.hulk.HulkGrab;
import com.projecthero.mod.hulk.WorldBreaker;
import com.projecthero.mod.hulk.data.HulkState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): the Hulk tuning pass -- health by rage (2 bars = 40 HP at 0 rage, 3 bars = 60 at 100), the damage nerf
 * (punch 15 at 0 rage, 25 at 100; every move x0.75 on the same curve), the mid-air Thunderclap (stops him dead, pushes him
 * along his look, a Super Leap stops holding its speed), the long throw's crater (none under 100 blocks, bigger the further
 * past 100) and the World Breaker shout. Mock players never tick, so each test drives what it needs itself. GameTest worlds are
 * Peaceful: pigs, not monsters.
 */
public class HulkTuningV0164GameTests implements FabricGameTest {
	/** Out as the Hulk, the change over, abilities allowed, no rampage clock. Room cleared round him. */
	private static ServerPlayer hulk(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getAbilities().invulnerable = false;
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 7, 3))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		helper.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p) && !Hulk.changing(p), "precondition: the Hulk, ready");
		HulkState n = Hulk.state(p).copy();
		n.formChangedAt = p.level().getGameTime() - 20 * 60 * 5;
		n.rules.nextRampageAt = p.level().getGameTime() + 20L * 60L * 10L;
		p.setAttached(ModAttachments.HULK_STATE, n);
		return p;
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

	// ---------------------------------------------------------------- health by rage

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_tuning_hp")
	public void healthIsTwoBarsAtNoRageAndThreeAtFull(GameTestHelper helper) {
		ServerPlayer p = hulk(helper, new Vec3(2.5, 2.0, 2.5));
		Hulk.setRage(p, 0.0f);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 40.0f) < 0.01f, "0 rage: 40 HP (2 bars), got " + p.getMaxHealth());
		p.setHealth(20.0f); // half
		Hulk.setRage(p, 100.0f);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 60.0f) < 0.01f, "100 rage: 60 HP (3 bars), got " + p.getMaxHealth());
		helper.assertTrue(Math.abs(p.getHealth() - 30.0f) < 0.01f, "his health keeps its share (half of 60), got " + p.getHealth());
		Hulk.setRage(p, 50.0f);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 50.0f) < 0.01f, "smoothly in between, got " + p.getMaxHealth());
		p.setHealth(0.5f);
		Hulk.setRage(p, 0.0f);
		helper.assertTrue(p.isAlive() && p.getHealth() >= 1.0f - 1.0e-4f, "a falling max never kills him, health " + p.getHealth());
		helper.assertTrue(Math.abs(HulkConfig.healthBonusForRage(0.0f) - 20.0) < 1.0e-9
				&& Math.abs(HulkConfig.healthBonusForRage(100.0f) - 40.0) < 1.0e-9, "the bonus runs 20 -> 40");
		Hulk.revoke(p);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 0.01f, "and it all goes when the power does");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the damage nerf

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_tuning_m1")
	public void punchIsFifteenAtNoRageAndTwentyFiveAtFull(GameTestHelper helper) {
		ServerPlayer p = hulk(helper, new Vec3(2.5, 2.0, 2.5));
		Hulk.setRage(p, 0.0f);
		float m1 = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(Math.abs(m1 - 15.0f) < 0.01f, "his bare-hand punch is 15, got " + m1);
		Pig a = pig(helper, p.position().add(2.0, 0, 0), 200.0);
		// 0 rage: HulkDamage lets the 15 land as it is
		helper.assertTrue(HulkDamage.allowDamage(a, p.damageSources().playerAttack(p), m1), "0 rage: lands unchanged");
		a.hurt(p.damageSources().playerAttack(p), m1); // what vanilla does once ALLOW_DAMAGE says yes
		helper.assertTrue(Math.abs((200.0f - a.getHealth()) - 15.0f) < 0.01f, "0 rage: 15 damage, got " + (200.0f - a.getHealth()));
		Hulk.setRage(p, 100.0f);
		Pig b = pig(helper, p.position().add(-2.0, 0, 0), 200.0);
		helper.assertFalse(HulkDamage.allowDamage(b, p.damageSources().playerAttack(p), m1), "100 rage: re-applied bigger");
		helper.assertTrue(Math.abs((200.0f - b.getHealth()) - 25.0f) < 0.01f, "100 rage: 25 damage, got " + (200.0f - b.getHealth()));
		a.discard();
		b.discard();
		Hulk.revoke(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_tuning_move")
	public void everyMoveIsScaledByTheSameRatio(GameTestHelper helper) {
		HulkConfig.Abilities c = HulkConfig.abilities();
		// old 28 / 30 / 12, x0.75 (the new 15 punch over the old 20)
		helper.assertTrue(Math.abs(c.superPunchDamage - 28.0f * HulkConfig.DAMAGE_NERF) < 1.0e-4f, "Super Punch 28 -> 21, got " + c.superPunchDamage);
		helper.assertTrue(Math.abs(c.gammaBurstDamage - 30.0f * HulkConfig.DAMAGE_NERF) < 1.0e-4f, "Gamma Burst 30 -> 22.5");
		helper.assertTrue(Math.abs(c.clapDamage - 12.0f * HulkConfig.DAMAGE_NERF) < 1.0e-4f, "Thunderclap 12 -> 9");
		helper.assertTrue(Math.abs(HulkConfig.gladiator().axeCleaveDamage - 34.0f * HulkConfig.DAMAGE_NERF) < 1.0e-4f, "Axe Cleave 34 -> 25.5");
		// and on the same rage curve: a Super Punch at 100 rage is 21 x 5/3 = 35
		ServerPlayer p = hulk(helper, new Vec3(2.5, 2.0, 2.5));
		Hulk.setRage(p, 100.0f);
		Pig pig = pig(helper, p.position().add(2.0, 0, 0), 200.0);
		helper.assertFalse(HulkDamage.allowDamage(pig, p.damageSources().playerAttack(p), c.superPunchDamage), "re-applied bigger");
		helper.assertTrue(Math.abs((200.0f - pig.getHealth()) - 35.0f) < 0.01f, "Super Punch at 100 rage: 35, got " + (200.0f - pig.getHealth()));
		pig.discard();
		Hulk.revoke(p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the air clap

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_tuning_air_clap")
	public void airClapStopsHimAndPushesHimAlongHisLook(GameTestHelper helper) {
		ServerPlayer p = hulk(helper, new Vec3(2.5, 6.0, 2.5));
		p.setOnGround(false);
		// mid Super Leap, flying fast along +Z and dropping
		HulkState n = Hulk.state(p).copy();
		n.leaping = true;
		n.moves.leapVx = 0.0;
		n.moves.leapVy = -1.0;
		n.moves.leapVz = 3.0;
		p.setAttached(ModAttachments.HULK_STATE, n);
		p.setDeltaMovement(0.0, -1.0, 3.0);
		// looking back over his shoulder and up: yaw 90 (-X), pitch -30
		p.setYRot(90.0f);
		p.setYHeadRot(90.0f);
		p.setXRot(-30.0f);
		helper.assertTrue(HulkAbilities.airborne(p), "precondition: in the air");
		HulkAbilities.thunderclap(p);
		Vec3 want = p.getLookAngle().normalize().scale(HulkConfig.abilities().airClapPush);
		Vec3 got = p.getDeltaMovement();
		helper.assertTrue(got.distanceTo(want) < 1.0e-6, "momentum gone, pushed along the look: want " + want + ", got " + got);
		helper.assertTrue(got.x < -0.5 && got.y > 0.3 && Math.abs(got.z) < 1.0e-6, "back the way he came and up, got " + got);
		HulkState s = Hulk.state(p);
		helper.assertTrue(s.moves.leapVx == 0.0 && s.moves.leapVz == 0.0, "the leap stops holding its speed (the client lets go)");
		helper.assertTrue(s.leaping, "the leap stays open, so the landing still counts and no fall is felt");
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, HulkAbilities.THUNDERCLAP) > 0, "the normal cooldown starts");
		helper.assertTrue(s.animId == HulkState.ANIM_THUNDERCLAP, "with the clap animation");
		Hulk.revoke(p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the long throw's crater

	/** An 11 x 11 x 4 slab of stone; returns the relative feet position on top of it. */
	private static BlockPos slab(GameTestHelper helper) {
		BlockPos low = helper.absolutePos(new BlockPos(0, 1, 0));
		for (BlockPos pos : BlockPos.betweenClosed(low.offset(-3, 0, -3), low.offset(7, 3, 7))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		return low.offset(2, 4, 2);
	}

	private static int stone(GameTestHelper helper) {
		BlockPos low = helper.absolutePos(new BlockPos(0, 1, 0));
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(low.offset(-3, 0, -3), low.offset(7, 3, 7))) {
			if (helper.getLevel().getBlockState(pos).is(Blocks.STONE)) {
				n++;
			}
		}
		return n;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_tuning_throw")
	public void longThrowsCraterMoreTheFurtherPastAHundred(GameTestHelper helper) {
		ServerPlayer p = hulk(helper, new Vec3(2.5, 12.0, 2.5));
		int full = 11 * 11 * 4;
		BlockPos feet = slab(helper);
		Vec3 at = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
		helper.assertTrue(stone(helper) == full, "precondition: a full slab");
		helper.assertTrue(HulkGrab.longThrowImpact(p, at, 90.0, null) == 0, "a 90-block throw breaks nothing");
		helper.assertTrue(stone(helper) == full, "not a block gone after a 90-block landing");
		helper.assertTrue(HulkGrab.longThrowDamageScale(90.0) == 1.0f, "nor hits any harder");
		int at120 = HulkGrab.longThrowImpact(p, at, 120.0, null);
		helper.assertTrue(at120 > 0 && full - stone(helper) == at120, "a 120-block throw craters (" + at120 + ")");
		slab(helper);
		int at200 = HulkGrab.longThrowImpact(p, at, 200.0, null);
		helper.assertTrue(at200 > at120, "a 200-block throw craters more: " + at200 + " vs " + at120);
		helper.assertTrue(Math.abs(HulkGrab.longThrowT(200.0) - 1.0) < 1.0e-9 && Math.abs(HulkGrab.longThrowT(150.0) - 0.5) < 1.0e-9,
				"t = (planned - 100) / 100");
		helper.assertTrue(HulkGrab.longThrowDamageScale(200.0) > 1.4f, "and a full throw hits harder");
		Hulk.revoke(p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the World Breaker shout

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_tuning_shout")
	public void worldBreakerShoutsHulkIsStrongest(GameTestHelper helper) {
		ServerPlayer p = hulk(helper, new Vec3(2.5, 2.0, 2.5));
		helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(com.projecthero.mod.ProjectHeroMod.id("hulk.strongest")),
				"projecthero:hulk.strongest is registered");
		helper.assertTrue(WorldBreaker.shout(p) >= 1, "the title goes out to everyone near (himself included)");
		Hulk.setRage(p, 100.0f);
		WorldBreaker.activate(p);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 60.0f) < 0.01f, "World Breaker: 3 bars, got " + p.getMaxHealth());
		Hulk.revoke(p);
		helper.succeed();
	}
}
