package com.projecthero.mod.gametest;

import com.mojang.serialization.JsonOps;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkDamage;
import com.projecthero.mod.hulk.data.HulkState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for the Hulk, Phase 1 (v0.13.11): the grant, rage from damage, the 75-rage manual change
 * and the forced change at 100, the stats going on and coming off cleanly, the exhausted reversion at 0, the
 * fists-only rule, respawn and persistence. Mock players are not reliably ticked by the server, so each test
 * drives {@link Hulk#tick} itself.
 */
public class HulkGameTests implements FabricGameTest {
	private static ServerPlayer gamma(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		// the Hulk grows to 3.2 blocks: open a room so the growth is never blocked by the test's barrier walls
		var base = net.minecraft.core.BlockPos.containing(at);
		for (var pos : net.minecraft.core.BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 5, 2))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		helper.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantGivesTheGammaPowerAsAHeroTierPower(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		helper.assertTrue(Hulk.hasPower(p), "has the Gamma power");
		helper.assertFalse(Hulk.isHulk(p), "but starts as Banner");
		helper.assertTrue(Hulk.rage(p) == 0.0f, "with no rage");
		helper.assertTrue(HeroTiers.holdsHero(p, Hulk.KEY), "it is a Hero-Tier power");
		helper.assertFalse(Hulk.grant(p), "granting twice does nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takingDamageBuildsRage(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.onDealt(p, 5.0f);
		helper.assertTrue(Math.abs(Hulk.rage(p) - 5.0f * HulkConfig.RAGE_PER_DAMAGE_DEALT) < 0.01f, "fighting builds rage too");
		Hulk.setRage(p, 0.0f);
		// player.hurt() on a mock player never reaches the damage events (see HeroPackGameTests): drive the hook directly
		Hulk.onHurt(p, 4.0f);
		float expected = 4.0f * HulkConfig.RAGE_PER_DAMAGE_TAKEN;
		helper.assertTrue(Math.abs(Hulk.rage(p) - expected) < 0.01f, "4 damage = " + expected + " rage, got " + Hulk.rage(p));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void manualChangeNeedsSeventyFiveRage(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.setRage(p, 50.0f);
		Hulk.tryTransform(p);
		helper.assertFalse(Hulk.isHulk(p), "50 rage is not enough");
		Hulk.setRage(p, 80.0f);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p), "80 rage lets the Hulk out");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void fullRageForcesTheChangeAndTheStatsGoOn(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.setRage(p, HulkConfig.RAGE_MAX);
		Hulk.tick(p);
		helper.assertTrue(Hulk.isHulk(p), "100 rage forces the change");
		helper.assertTrue(Math.abs(p.getMaxHealth() - (20.0f + HulkConfig.HEALTH_BONUS)) < 0.01f, "max health 60, got " + p.getMaxHealth());
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 1.0 + HulkConfig.ATTACK_BONUS - 0.01, "attack bonus on");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= HulkConfig.KNOCKBACK_RESISTANCE - 0.01, "knockback resistance on");
		// reconcile is idempotent: running it again must not stack anything
		Hulk.reconcile(p);
		Hulk.reconcile(p);
		helper.assertTrue(Math.abs(p.getMaxHealth() - (20.0f + HulkConfig.HEALTH_BONUS)) < 0.01f, "no stacking");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(HulkConfig.GROWTH_TICKS + 10, () -> {
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.SCALE) - (1.0 + HulkConfig.SCALE_BONUS)) < 0.01,
					"grown to 1.8x, got " + p.getAttributeValue(Attributes.SCALE));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroRageRevertsExhaustedAndClearsTheStats(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.setRage(p, 90.0f);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p), "precondition: Hulk");
		Hulk.setRage(p, 0.0f);
		Hulk.tick(p);
		helper.assertFalse(Hulk.isHulk(p), "0 rage shrinks him back");
		helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS) && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "exhausted: Weakness + Slowness");
		helper.assertTrue(Hulk.exhausted(p), "and marked exhausted");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 0.01f, "max health back to 20");
		// (Weakness itself lowers attack, so only check the Hulk bonus is gone)
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 1.0 + 0.01, "attack bonus gone");
		// an exhausted Banner builds no rage and cannot change
		Hulk.onHurt(p, 2.0f);
		helper.assertTrue(Hulk.rage(p) == 0.0f, "no rage while exhausted");
		Hulk.setRage(p, 100.0f);
		Hulk.tick(p);
		helper.assertFalse(Hulk.isHulk(p), "and no forced change while exhausted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHulkUsesHisFists(GameTestHelper helper) {
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.DIAMOND_SWORD)), "no swords");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.IRON_PICKAXE)), "no tools");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.BOW)), "no bows");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.CROSSBOW)), "no crossbows");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.TRIDENT)), "no tridents");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(com.projecthero.mod.firearm.item.FirearmItems.PUNISHER_PISTOL)), "no guns");
		helper.assertFalse(HulkDamage.isForbidden(new ItemStack(Items.BREAD)), "food is fine");
		helper.assertFalse(HulkDamage.isForbidden(ItemStack.EMPTY), "bare hands are the point");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void respawnAndRevokeLeaveNothingBehind(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.setRage(p, 100.0f);
		Hulk.tick(p);
		Hulk.onPlayerRespawn(p);
		helper.assertFalse(Hulk.isHulk(p), "a respawn is a calm Banner");
		helper.assertTrue(Hulk.rage(p) == 0.0f, "with no rage");
		helper.assertTrue(Hulk.hasPower(p), "but keeps the Gamma power");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 0.01f, "stats cleared");

		Hulk.setRage(p, 100.0f);
		Hulk.tick(p);
		Hulk.revoke(p);
		helper.assertFalse(Hulk.hasPower(p), "revoked");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.SCALE) - 1.0) < 0.001, "normal size at once");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 0.01f, "no health bonus left");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stateSurvivesSaveAndLoad(GameTestHelper helper) {
		HulkState s = new HulkState(true, true, 42.5f, 10L, 20L, 30L);
		var json = HulkState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		HulkState back = HulkState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.hasPower && back.hulk && back.rage == 42.5f && back.exhaustedUntil == 20L, "round-trips");
		helper.assertTrue(ModAttachments.HULK_STATE != null, "the attachment is registered");
		helper.succeed();
	}
}
