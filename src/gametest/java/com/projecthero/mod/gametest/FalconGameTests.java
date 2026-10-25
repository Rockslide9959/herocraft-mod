package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.FalconAbilities;
import com.projecthero.mod.falcon.FalconConfig;
import com.projecthero.mod.falcon.data.FalconState;
import com.projecthero.mod.falcon.item.FalconItems;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 the Falcon: the gear is the power (the full set grants it, any piece off removes it, a replaced Falcon stays off
 * until re-geared), Thrust drain / refill, no fall damage, and a few moves' damage / cost / cooldown. Mock players are not
 * ticked by the server, so tests over time drive {@link Falcon#tick} ({@link #pump}).
 */
public class FalconGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** A geared Falcon on the ground, facing +Z. */
	private static ServerPlayer falcon(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		FalconItems.equipFullSet(p);
		Falcon.forgetTick(p);
		Falcon.tick(p);
		p.setOnGround(true);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Falcon.forgetTick(p);
			Falcon.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fullSetGrantsAndAnyPieceOffRemoves(GameTestHelper helper) {
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("falcon") && PowerGrants.HERO_TIER_KEYS.contains("falcon"), "falcon is a Hero-Tier key");
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FalconItems.WING_PACK));
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FalconItems.GOGGLES));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FalconItems.LEGGINGS));
		Falcon.forgetTick(p);
		Falcon.tick(p);
		helper.assertFalse(Falcon.hasPower(p), "three pieces are not enough");
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FalconItems.BOOTS));
		Falcon.forgetTick(p);
		Falcon.tick(p);
		helper.assertTrue(Falcon.hasPower(p), "the full set makes him the Falcon");
		helper.assertTrue(HeroTiers.heroCount(p) == 1 && HeroTiers.holdsHero(p, "falcon"), "one Primary: the Falcon");
		helper.assertTrue(Falcon.thrust(p) == FalconConfig.THRUST_MAX, "starts with full Thrust");
		p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		Falcon.forgetTick(p);
		Falcon.tick(p);
		helper.assertFalse(Falcon.hasPower(p), "a boot off: no longer the Falcon");
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FalconItems.BOOTS));
		Falcon.forgetTick(p);
		Falcon.tick(p);
		helper.assertTrue(Falcon.hasPower(p), "back on: the Falcon again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void replacedFalconStaysOffUntilRegeared(GameTestHelper helper) {
		ServerPlayer p = falcon(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(Falcon.hasPower(p), "geared");
		Kryptonian.grant(p);
		helper.assertTrue(Kryptonian.hasPower(p) && !Falcon.hasPower(p), "the Kryptonian replaced the Falcon");
		Falcon.forgetTick(p);
		Falcon.tick(p);
		helper.assertTrue(!Falcon.hasPower(p) && Kryptonian.hasPower(p), "the worn gear does not fight the new power for the slot");
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		Falcon.forgetTick(p);
		Falcon.tick(p);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FalconItems.GOGGLES));
		Falcon.forgetTick(p);
		Falcon.tick(p);
		helper.assertTrue(Falcon.hasPower(p) && !Kryptonian.hasPower(p), "re-geared: the Falcon replaces the Kryptonian");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantCommandPutsTheGearOn(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		PowerGrants.Result r = PowerGrants.grantHero(p, "falcon");
		helper.assertTrue(r.ok() && Falcon.hasPower(p) && FalconItems.fullSet(p), "granted with the full set on");
		helper.assertTrue(p.getInventory().contains(new ItemStack(Items.DIAMOND_CHESTPLATE)), "the old chestplate went into the inventory");
		PowerGrants.revokeHeroTier(p, "falcon");
		helper.assertFalse(Falcon.hasPower(p), "revoked");
		Falcon.forgetTick(p);
		Falcon.tick(p);
		helper.assertFalse(Falcon.hasPower(p), "and it stays revoked while the gear is still on");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noFallDamageInTheGear(GameTestHelper helper) {
		ServerPlayer p = falcon(helper, 2.5, 2.0, 2.5);
		float hp = p.getHealth();
		p.hurt(p.damageSources().fall(), 10f);
		helper.assertTrue(p.getHealth() == hp, "no fall damage geared");
		p.hurt(p.damageSources().flyIntoWall(), 6f);
		helper.assertTrue(p.getHealth() == hp, "no wall damage geared");
		p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		Falcon.forgetTick(p);
		Falcon.tick(p);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 4f);
		helper.assertTrue(p.getHealth() < hp, "without the full set it hurts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void thrustBurnsWhileBoostingAndRefillsOnTheGround(GameTestHelper helper) {
		ServerPlayer p = falcon(helper, 2.5, 6.0, 2.5);
		p.setOnGround(false);
		FalconState n = Falcon.state(p).copy();
		n.thrust = 60f;
		n.flying = true;
		n.boosting = true;
		p.setAttached(ModAttachments.FALCON_STATE, n);
		pump(helper, p);
		helper.runAfterDelay(21, () -> {
			float t = Falcon.thrust(p);
			helper.assertTrue(t < 60f - FalconConfig.BOOST_COST_PER_SECOND + 4f && t > 60f - FalconConfig.BOOST_COST_PER_SECOND - 4f,
					"about 14 Thrust burnt in a second, now " + t);
			Falcon.setBoost(p, false);
			Falcon.stopFlying(p);
			p.setOnGround(true);
			float before = Falcon.thrust(p);
			helper.runAfterDelay(21, () -> {
				float after = Falcon.thrust(p);
				helper.assertTrue(after - before > FalconConfig.THRUST_REGEN_GROUND - 4f, "refills about 12 a second on the ground, got " + (after - before));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boostTurnsOffWithNoThrust(GameTestHelper helper) {
		ServerPlayer p = falcon(helper, 2.5, 6.0, 2.5);
		FalconState n = Falcon.state(p).copy();
		n.flying = true;
		n.thrust = 0f;
		p.setAttached(ModAttachments.FALCON_STATE, n);
		Falcon.setBoost(p, true);
		helper.assertFalse(Falcon.state(p).boosting, "no Thrust, no boost");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wingSlashHitsTheArcForNine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = falcon(helper, 3.5, 2.0, 1.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.0, 100);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 0.0, 100);
		FalconAbilities.slash(p);
		helper.assertTrue(Math.abs((100f - front.getHealth()) - FalconConfig.SLASH_DAMAGE) < EPS, "9 in front, took " + (100f - front.getHealth()));
		helper.assertTrue(behind.getHealth() == 100f, "behind him: untouched");
		helper.assertTrue(Falcon.cooldownRemaining(p, FalconAbilities.SLASH) == FalconConfig.SLASH_COOLDOWN, "2.5s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void missileVolleyCostsThrust(GameTestHelper helper) {
		ServerPlayer p = falcon(helper, 2.5, 2.0, 2.5);
		FalconAbilities.missiles(p);
		helper.assertTrue(Math.abs(Falcon.thrust(p) - (FalconConfig.THRUST_MAX - FalconConfig.MISSILE_COST)) < 0.01f, "20 Thrust");
		helper.assertTrue(Falcon.cooldownRemaining(p, FalconAbilities.MISSILES) == FalconConfig.MISSILE_COOLDOWN, "8s cooldown");
		helper.assertTrue(FalconAbilities.running(p, FalconAbilities.MISSILES), "the missiles are in the air");
		Falcon.setThrust(p, 5f);
		FalconAbilities.barrelRoll(p);
		helper.assertTrue(Falcon.cooldownRemaining(p, FalconAbilities.ROLL) == 0 && !Falcon.rolling(p), "no Thrust for the roll: refused");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void barrelRollIsInvulnerable(GameTestHelper helper) {
		ServerPlayer p = falcon(helper, 2.5, 2.0, 2.5);
		FalconAbilities.barrelRoll(p);
		helper.assertTrue(Falcon.rolling(p), "rolling");
		helper.assertTrue(Math.abs(Falcon.thrust(p) - (FalconConfig.THRUST_MAX - FalconConfig.ROLL_COST)) < 0.01f, "10 Thrust");
		float hp = p.getHealth();
		p.hurt(p.damageSources().generic(), 6f);
		helper.assertTrue(p.getHealth() == hp, "no damage mid-roll");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void afterburnerFromTheGroundTakesOff(GameTestHelper helper) {
		ServerPlayer p = falcon(helper, 2.5, 2.0, 2.5);
		FalconAbilities.afterburner(p);
		helper.assertTrue(Falcon.state(p).flying, "the leap becomes flight");
		helper.assertTrue(Math.abs(Falcon.thrust(p) - (FalconConfig.THRUST_MAX - FalconConfig.AFTERBURNER_COST)) < 0.01f, "15 Thrust");
		helper.assertTrue(Falcon.cooldownRemaining(p, FalconAbilities.AFTERBURNER) == FalconConfig.AFTERBURNER_COOLDOWN, "2s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void diveBombDamageScalesWithTheDrop(GameTestHelper helper) {
		helper.assertTrue(FalconAbilities.diveDamage(0) == FalconConfig.DIVE_MIN_DAMAGE, "6 from no height");
		helper.assertTrue(Math.abs(FalconAbilities.diveDamage(10) - 12f) < 0.01f, "12 from 10 blocks");
		helper.assertTrue(FalconAbilities.diveDamage(60) == FalconConfig.DIVE_MAX_DAMAGE, "capped at 18");
		ServerPlayer p = falcon(helper, 2.5, 2.0, 2.5);
		FalconAbilities.diveBomb(p);
		helper.assertTrue(Falcon.cooldownRemaining(p, FalconAbilities.DIVE) == 0 && Falcon.thrust(p) == FalconConfig.THRUST_MAX,
				"on the ground: refused, nothing spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void talonDropNeverGrabsABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = falcon(helper, 3.5, 2.0, 1.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 400);
		FalconAbilities.talonDrop(p);
		helper.assertTrue(Falcon.state(p).carryId == -1 && Falcon.thrust(p) == FalconConfig.THRUST_MAX, "a boss can't be snatched");
		boss.discard();
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 40);
		FalconAbilities.talonDrop(p);
		helper.assertTrue(Falcon.state(p).carryId == z.getId(), "a zombie can");
		helper.assertTrue(Math.abs(Falcon.thrust(p) - (FalconConfig.THRUST_MAX - FalconConfig.TALON_COST)) < 0.01f, "20 Thrust");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gogglesToggleAndRedwingRidesAlong(GameTestHelper helper) {
		ServerPlayer p = falcon(helper, 2.5, 2.0, 2.5);
		FalconAbilities.gogglesPress(p);
		helper.assertTrue(Falcon.goggles(p), "V: goggles on");
		FalconAbilities.tick(p);
		helper.assertTrue(FalconAbilities.redwingOf(p) != null, "Redwing rides along");
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		Falcon.forgetTick(p);
		Falcon.tick(p);
		helper.assertTrue(FalconAbilities.redwingOf(p) == null && !Falcon.goggles(p), "gear off: Redwing gone, goggles off");
		helper.succeed();
	}
}
