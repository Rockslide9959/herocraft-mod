package com.projecthero.mod.gametest;

import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorPassives;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.spider.SpiderCombat;
import com.projecthero.mod.spider.SpiderWebs;
import com.projecthero.mod.thorarmor.ThorArmor;
import com.projecthero.mod.thorarmor.ThorArmorItems;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.12.32 Thor changes: Thor's Armour (H), the 80% damage reduction + permanent Regeneration I that replaced
 * Resistance IV, the +11 melee bonus; plus the Spider-Man Impact Web cocoon duration.
 */
public class ThorArmorGameTests implements FabricGameTest {
	private static ServerPlayer boundThor(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		ItemStack hammer = new ItemStack(ModItems.MJOLNIR);
		p.setItemInHand(InteractionHand.MAIN_HAND, hammer);
		ThorPowers.toggleBinding(p, hammer);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hTogglesTheArmourOnAndOff(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper);
		helper.assertTrue(ThorPassives.hasPowerOfThor(p), "bound Thor");
		ThorArmor.toggle(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(ThorArmorItems.CHESTPLATE), "chestplate formed");
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(ThorArmorItems.LEGGINGS), "leggings formed");
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).is(ThorArmorItems.BOOTS), "boots formed");
		p.getCooldowns().removeCooldown(ThorArmorItems.CHESTPLATE);
		ThorArmor.toggle(p);
		helper.assertFalse(ThorArmor.hasAnyPiece(p), "H again dismisses every piece");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anUnboundPlayerCannotSummonIt(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ThorArmor.toggle(p);
		helper.assertFalse(ThorArmor.hasAnyPiece(p), "no Power of Thor, no armour");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, setupTicks = 1)
	public void aDroppedPieceDespawns(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper);
		ItemEntity dropped = p.drop(new ItemStack(ThorArmorItems.CHESTPLATE), false);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(dropped == null || dropped.isRemoved(), "the dropped armour must vanish");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dyingStripsTheArmour(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper);
		ThorArmor.toggle(p);
		helper.assertTrue(ThorArmor.isWearing(p), "worn");
		ThorArmor.onDeath(p);
		helper.assertFalse(ThorArmor.hasAnyPiece(p), "death takes every piece, worn or carried");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void losingTheHammerLosesTheArmour(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper);
		ThorArmor.toggle(p);
		ThorPassives.unbind(p);
		p.setGameMode(GameType.SURVIVAL); // (the audit leaves Creative players -- who may /give themselves the set -- alone)
		p.tickCount = 20; // the audit runs once a second
		ThorArmor.audit(p);
		helper.assertFalse(ThorArmor.hasAnyPiece(p), "no longer Thor -> the armour is gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thorTakesEightyPercentLessAndRegeneratesButHasNoResistance(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper);
		ThorPassives.reconcile(p);
		MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 0 && regen.isInfiniteDuration(), "permanent Regeneration I");
		helper.assertFalse(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "the base Resistance is gone");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 12.0) < 1e-6,
				"bare-handed melee is 1 + 11, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		// player.hurt() on a GameTest mock player never reaches ALLOW_DAMAGE (see HeroPackGameTests), so drive the listener directly:
		helper.assertTrue(ThorPassives.DAMAGE_TAKEN_FACTOR == 0.2f, "Thor takes 20% of the damage");
		helper.assertFalse(ThorPassives.onAllowDamage(p, helper.getLevel().damageSources().generic(), 10.0f),
				"an ordinary hit is cancelled and re-issued at 20%");
		helper.assertTrue(ThorPassives.onAllowDamage(p, helper.getLevel().damageSources().genericKill(), 10.0f),
				"/kill-style damage is never reduced");
		helper.assertFalse(ThorPassives.onAllowDamage(p, helper.getLevel().damageSources().lightningBolt(), 10.0f),
				"lightning is still fully cancelled");
		ThorPassives.unbind(p);
		helper.assertFalse(p.hasEffect(MobEffects.REGENERATION), "unbinding takes the Regeneration with it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void impactWebCocoonsForTwelveSeconds(GameTestHelper helper) {
		helper.assertTrue(SpiderCombat.IMPACT_PIN_TICKS == 12 * 20, "12 seconds, got " + SpiderCombat.IMPACT_PIN_TICKS / 20);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		helper.getLevel().addFreshEntity(z);
		int ticks = SpiderWebs.cocoonFor(owner, z, SpiderCombat.IMPACT_PIN_TICKS);
		helper.assertTrue(ticks == 240, "an ordinary mob is held for the full 240 ticks, got " + ticks);
		helper.assertTrue(SpiderWebs.isCocooned(z), "and is cocooned");
		helper.succeed();
	}
}
