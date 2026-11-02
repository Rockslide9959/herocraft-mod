package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.echoecho.EchoCloneEntity;
import com.projecthero.mod.omnitrix.alien.echoecho.EchoEchoKit;
import com.projecthero.mod.omnitrix.alien.humungousaur.HumungousaurKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 the Recalibrated Omnitrix, group A -- Swampfire (his tests moved to OmnitrixSwampfireGameTests with the revamp), Echo Echo and Humungousaur (basic kits): their bodies on a recalibrated
 * watch, each one's signature (Swampfire's fire immunity, cold weakness and regrowth; Echo Echo's clones, which are cleaned up when
 * he turns back; Humungousaur's size-shift, which comes off on revert), one move's damage + cooldown each, and that turning back
 * leaves no state behind. Mock players are not ticked: tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixRecalAGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, pitch);
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

	/** A recalibrated wearer, transformed into {@code alien}. */
	private static ServerPlayer as(GameTestHelper helper, String alien, float pitch) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5, pitch);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.recalibrated(p), "the watch is recalibrated");
		helper.assertTrue(Aliens.available(p, alien), alien + " is on the recalibrated dial");
		helper.assertTrue(Omnitrix.select(p, alien), "selected " + alien);
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + alien);
		p.setOnGround(true);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** A no-AI villager to hit (not a hostile, so no other test's area sweep lands on it). */
	private static Villager prey(GameTestHelper helper, double x, double y, double z, float health) {
		Villager m = EntityType.VILLAGER.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05f, what + " (expected " + expected + ", got " + actual + ")");
	}

	private static void clearCooldown(ServerPlayer p, String alien, String move) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(alien + "." + move);
		Omnitrix.save(p, n);
	}

	// ================================================================ Echo Echo

	private static List<EchoCloneEntity> clonesNear(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(EchoCloneEntity.class, new AABB(p.blockPosition()).inflate(16), c -> !c.isRemoved());
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void echoEchoSplitsIntoThreeClonesThatGoWhenHeTurnsBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, EchoEchoKit.ID, 0f);
		EchoEchoKit kit = (EchoEchoKit) Aliens.kit(EchoEchoKit.ID);
		near(helper, p.getMaxHealth(), 16f, "16 health");
		near(helper, p.getScale(), 0.6f, "0.6x the size");
		for (int i = 0; i < EchoEchoKit.MAX_CLONES; i++) {
			clearCooldown(p, EchoEchoKit.ID, EchoEchoKit.SPLIT);
			helper.assertTrue(kit.split(p), "clone " + (i + 1) + " splits off");
		}
		helper.assertTrue(EchoEchoKit.clones(p).size() == 3, "three clones (got " + EchoEchoKit.clones(p).size() + ")");
		helper.assertTrue(clonesNear(helper, p).size() == 3, "three clones in the world");
		clearCooldown(p, EchoEchoKit.ID, EchoEchoKit.SPLIT);
		helper.assertFalse(kit.split(p), "no fourth clone");
		helper.assertTrue(kit.cooldownLeft(p, EchoEchoKit.SPLIT) == 0, "a refused split costs no cooldown");
		List<EchoCloneEntity> all = EchoEchoKit.clones(p);
		// a clone breaks after its health is gone; a hit from its owner does nothing
		EchoCloneEntity first = all.get(0);
		first.hurt(helper.getLevel().damageSources().playerAttack(p), 50f);
		helper.assertFalse(first.isRemoved(), "his own swing never breaks a clone");
		first.hurt(helper.getLevel().damageSources().magic(), EchoCloneEntity.MAX_HP);
		helper.assertTrue(first.isRemoved() && EchoEchoKit.clones(p).size() == 2, "a clone breaks after 6 damage");
		// Merge heals him for each clone
		p.setHealth(8f);
		helper.assertTrue(kit.merge(p), "the clones merge back");
		near(helper, p.getHealth(), 8f + 2 * EchoEchoKit.MERGE_HEAL, "+2 health a clone");
		helper.assertTrue(clonesNear(helper, p).isEmpty(), "merged: no clones left");
		clearCooldown(p, EchoEchoKit.ID, EchoEchoKit.SPLIT);
		kit.split(p);
		clearCooldown(p, EchoEchoKit.ID, EchoEchoKit.SPLIT);
		kit.split(p);
		List<EchoCloneEntity> again = EchoEchoKit.clones(p);
		helper.assertTrue(again.size() == 2, "two new clones");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		for (EchoCloneEntity c : again) {
			helper.assertTrue(c.isRemoved(), "turning back removes every clone");
		}
		helper.assertTrue(clonesNear(helper, p).isEmpty(), "none left in the world");
		helper.assertFalse(EchoEchoKit.holds(p), "no Echo Echo state left");
		near(helper, p.getScale(), 1.0f, "back to a player's size");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void echoEchoScreamHitsOnceThenCoolsDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, EchoEchoKit.ID, 0f);
		EchoEchoKit kit = (EchoEchoKit) Aliens.kit(EchoEchoKit.ID);
		Villager v = prey(helper, 3.5, 2, 6.5, 100f);
		helper.assertTrue(kit.scream(p), "the Sonic Scream fires");
		near(helper, v.getHealth(), 100f - EchoEchoKit.SCREAM_DAMAGE, "the scream's damage");
		v.invulnerableTime = 0;
		helper.assertFalse(kit.scream(p), "the scream cools down");
		helper.assertTrue(kit.cooldownLeft(p, EchoEchoKit.SCREAM) > 0, "a cooldown is running");
		near(helper, v.getHealth(), 100f - EchoEchoKit.SCREAM_DAMAGE, "no second wave while it cools down");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}

	// ================================================================ Humungousaur

	@GameTest(template = EMPTY_STRUCTURE)
	public void humungousaurGrowsInStepsAndTheGrowthComesOffOnRevert(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HumungousaurKit.ID, 0f);
		HumungousaurKit kit = (HumungousaurKit) Aliens.kit(HumungousaurKit.ID);
		near(helper, p.getMaxHealth(), 44f, "44 health");
		near(helper, p.getScale(), 1.5f, "1.5x the size");
		near(helper, p.getArmorValue(), 10f, "armour 10");
		double speed0 = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double reach0 = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		helper.assertTrue(kit.grow(p), "V grows him");
		helper.assertTrue(HumungousaurKit.stage(p) == 1, "size step 1");
		near(helper, p.getScale(), 2.2f, "2.2x the size");
		near(helper, p.getArmorValue(), 13f, "armour 13");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed0, "a little slower");
		helper.assertTrue(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) > reach0, "a longer reach");
		near(helper, HumungousaurKit.mult(p), 1.2f, "+20% damage");
		helper.assertFalse(kit.grow(p), "Grow cools down");
		clearCooldown(p, HumungousaurKit.ID, HumungousaurKit.GROW);
		helper.assertTrue(kit.grow(p) && HumungousaurKit.stage(p) == 2, "size step 2");
		near(helper, p.getScale(), 3.0f, "3.0x the size");
		near(helper, p.getArmorValue(), 16f, "armour 16");
		p.refreshDimensions();
		helper.assertTrue(p.getBbHeight() > 5.0f, "the hitbox grew with him (" + p.getBbHeight() + ")");
		clearCooldown(p, HumungousaurKit.ID, HumungousaurKit.GROW);
		helper.assertFalse(kit.grow(p), "no bigger than x3.0");
		helper.assertTrue(kit.shrink(p) && HumungousaurKit.stage(p) == 1, "Shift+V shrinks one step");
		near(helper, p.getScale(), 2.2f, "back to 2.2x");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
		near(helper, p.getScale(), 1.0f, "a player's size again");
		near(helper, p.getArmorValue(), 0f, "no armour left");
		near(helper, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1f, "a player's fist again");
		near(helper, (float) p.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.1f, "a player's speed again");
		near(helper, (float) p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 3f, "a player's reach again");
		helper.assertTrue(HumungousaurKit.stage(p) == 0, "the size step is gone");
		helper.assertFalse(HumungousaurKit.holds(p), "no Humungousaur state left");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void humungousaurHaymakerHitsOnceThenCoolsDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, HumungousaurKit.ID, 25f);
		HumungousaurKit kit = (HumungousaurKit) Aliens.kit(HumungousaurKit.ID);
		Villager v = prey(helper, 3.5, 2, 4.5, 100f);
		helper.assertTrue(kit.punch(p), "the Haymaker swings");
		near(helper, v.getHealth(), 100f - HumungousaurKit.PUNCH_DAMAGE, "the punch's damage");
		v.invulnerableTime = 0;
		helper.assertFalse(kit.punch(p), "the Haymaker cools down");
		helper.assertTrue(kit.cooldownLeft(p, HumungousaurKit.PUNCH) > 0, "a cooldown is running");
		near(helper, v.getHealth(), 100f - HumungousaurKit.PUNCH_DAMAGE, "no second punch while it cools down");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}
}
