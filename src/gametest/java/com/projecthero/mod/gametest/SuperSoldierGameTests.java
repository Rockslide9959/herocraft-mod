package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.SuperSoldierAbilityManager;
import com.projecthero.mod.supersoldier.SuperSoldierConfig;
import com.projecthero.mod.supersoldier.SuperSoldierSerum;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;
import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for the Super Soldier (v0.14.8): the serums (grant / rejection with an injected roll, creative
 * included), the passives and their removal, the 30% damage reduction, the crafting recipe and the refinement (v0.17.7: moved to
 * SuperSoldierSerumV0177GameTests), every one of the eleven moves activating and starting its cooldown and the
 * one-Primary-power rule. v0.14.9: the Flying Kick and Judo Takedown, the C shield throw with the Adamantium Shield vs an
 * ordinary one (three zombies each, 9 vs 6, the same stack back intact, never duplicated, dropped if the thrower dies),
 * the shield / suit recipes and the suit's wear restriction. Mock players are not reliably ticked by the server, so tests that need
 * the power's tick drive {@link SuperSoldierAbilityManager#serverTick} themselves.
 */
public class SuperSoldierGameTests implements FabricGameTest {
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static ServerPlayer soldier(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		helper.assertTrue(SuperSoldierSerum.drink(p, true, 0.99f) == SuperSoldierSerum.Outcome.GRANTED, "the refined serum always takes");
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> SuperSoldierAbilityManager.serverTick(p));
	}

	private static void unlock(ServerPlayer p) {
		SuperSoldierState s = SuperSoldier.state(p).copy();
		s.busyUntil = 0L;
		p.setAttached(ModAttachments.SUPER_SOLDIER_STATE, s);
	}

	private static Zombie zombieAt(GameTestHelper helper, ServerPlayer p, double forward, double side) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(p.getX() + side, p.getY(), p.getZ() + forward, 180.0f, 0.0f);
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		z.setHealth(200.0f);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 2000, 0, false, false));
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static boolean hasModifier(ServerPlayer p, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String id) {
		var inst = p.getAttribute(attr);
		return inst != null && inst.getModifier(ProjectHeroMod.id(id)) != null;
	}

	// ---------------------------------------------------------------- serums / passives

	@GameTest(template = EMPTY_STRUCTURE)
	public void refinedSerumGrantsThePowerAndEveryPassive(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		helper.assertTrue(SuperSoldier.hasPower(p), "has the power");
		helper.assertTrue(HeroTiers.holdsHero(p, "super_soldier") && HeroTiers.hasHeroTier(p), "it is a Hero-Tier power");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 30.0f) < 1.0e-3f, "+5 hearts, max health " + p.getMaxHealth());
		helper.assertTrue(hasModifier(p, Attributes.MOVEMENT_SPEED, "super_soldier_speed"), "+50% speed modifier");
		double base = p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - base * 1.5) < 1.0e-6, "speed is 150% of base");
		helper.assertTrue(hasModifier(p, Attributes.JUMP_STRENGTH, "super_soldier_jump"), "jump modifier");
		helper.assertTrue(hasModifier(p, Attributes.SAFE_FALL_DISTANCE, "super_soldier_safe_fall"), "safe fall modifier");
		helper.assertTrue(hasModifier(p, Attributes.KNOCKBACK_RESISTANCE, "super_soldier_knockback"), "knockback resistance");
		helper.assertTrue(hasModifier(p, Attributes.ATTACK_SPEED, "super_soldier_attack_speed"), "attack speed");
		helper.assertTrue(hasModifier(p, Attributes.ATTACK_DAMAGE, "super_soldier_unarmed"), "empty hand: +7 unarmed");
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		SuperSoldier.reconcile(p);
		helper.assertTrue(hasModifier(p, Attributes.ATTACK_DAMAGE, "super_soldier_unarmed"), "v0.14.11: holding something keeps a bonus");
		// v0.17.7 (Street tier): bare hands +7, holding something +2
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + SuperSoldierConfig.ARMED_BONUS)) < 1.0e-6,
				"holding a stick: fist 1 + 2 = " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		SuperSoldier.reconcile(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + SuperSoldierConfig.UNARMED_BONUS)) < 1.0e-6,
				"bare hands: fist 1 + 7 = " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		// the jump modifier really carries a player about 2.25 blocks
		double v = p.getAttributeValue(Attributes.JUMP_STRENGTH);
		double y = 0.0;
		double max = 0.0;
		for (int t = 0; t < 60; t++) {
			y += v;
			max = Math.max(max, y);
			v = (v - 0.08) * 0.98;
		}
		helper.assertTrue(max > 2.0 && max < 2.5, "jump apex ~2.25 blocks, got " + max);
		helper.assertTrue(SuperSoldierSerum.drink(p, true, 0.0f) == SuperSoldierSerum.Outcome.ALREADY, "a second serum does nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void revokeRemovesEveryModifier(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		SuperSoldier.revoke(p);
		helper.assertFalse(SuperSoldier.hasPower(p), "power gone");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 1.0e-3f, "back to 10 hearts");
		helper.assertTrue(p.getHealth() <= p.getMaxHealth(), "health clamped");
		for (var pair : List.of(
				new Object[] { Attributes.MOVEMENT_SPEED, "super_soldier_speed" },
				new Object[] { Attributes.MAX_HEALTH, "super_soldier_health" },
				new Object[] { Attributes.ATTACK_DAMAGE, "super_soldier_unarmed" },
				new Object[] { Attributes.ATTACK_DAMAGE, "super_soldier_onslaught" },
				new Object[] { Attributes.JUMP_STRENGTH, "super_soldier_jump" },
				new Object[] { Attributes.SAFE_FALL_DISTANCE, "super_soldier_safe_fall" },
				new Object[] { Attributes.KNOCKBACK_RESISTANCE, "super_soldier_knockback" },
				new Object[] { Attributes.ATTACK_SPEED, "super_soldier_attack_speed" })) {
			@SuppressWarnings("unchecked")
			Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = (Holder<net.minecraft.world.entity.ai.attributes.Attribute>) pair[0];
			helper.assertFalse(hasModifier(p, attr, (String) pair[1]), "modifier " + pair[1] + " removed");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unrefinedSerumRollDecidesLifeOrDeath(GameTestHelper helper) {
		helper.assertTrue(SuperSoldierSerum.succeeds(false, 0.05f), "a roll under 10% takes");
		helper.assertFalse(SuperSoldierSerum.succeeds(false, 0.10f), "10% and over is rejected");
		helper.assertFalse(SuperSoldierSerum.succeeds(false, 0.5f), "a bad roll is rejected");
		helper.assertTrue(SuperSoldierSerum.succeeds(true, 0.999f), "the refined serum always takes");

		ServerPlayer lucky = player(helper);
		helper.assertTrue(SuperSoldierSerum.drink(lucky, false, 0.05f) == SuperSoldierSerum.Outcome.GRANTED, "lucky roll grants");
		helper.assertTrue(SuperSoldier.hasPower(lucky) && lucky.isAlive(), "and he lives");

		ServerPlayer unlucky = player(helper);
		helper.assertTrue(SuperSoldierSerum.drink(unlucky, false, 0.5f) == SuperSoldierSerum.Outcome.REJECTED, "bad roll rejects");
		helper.assertFalse(SuperSoldier.hasPower(unlucky), "no power");
		helper.assertTrue(unlucky.isDeadOrDying(), "the rejection kills");

		ServerPlayer creative = player(helper);
		creative.setGameMode(GameType.CREATIVE);
		helper.assertTrue(SuperSoldierSerum.drink(creative, false, 0.9f) == SuperSoldierSerum.Outcome.REJECTED, "creative rolls too");
		helper.assertTrue(creative.isDeadOrDying(), "creative is no protection from the rejection");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void drinkingTheItemLeavesABottle(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		ItemStack serum = new ItemStack(SuperSoldierItems.REFINED_SERUM);
		ItemStack result = serum.finishUsingItem(helper.getLevel(), p);
		// v0.17.7: the item starts the one-minute transformation; the power comes at its end (SuperSoldierSerumV0177GameTests)
		helper.assertTrue(com.projecthero.mod.supersoldier.SerumTransformation.active(p) && !SuperSoldier.hasPower(p),
				"the refined serum item starts the transformation");
		helper.assertTrue(result.is(Items.GLASS_BOTTLE), "and leaves a glass bottle, got " + result);
		com.projecthero.mod.supersoldier.SerumTransformation.cancel(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void damageTakenIsReducedThirtyPercent(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		// a fresh (mock) player is spawn-invulnerable for 60 ticks
		helper.runAfterDelay(70, () -> {
			p.setHealth(p.getMaxHealth());
			SuperSoldierAbilityManager.serverTick(p); // v0.17.7: the passive Resistance I is on
			float before = p.getHealth();
			p.hurt(p.damageSources().generic(), 10.0f);
			float taken = before - p.getHealth();
			// v0.17.7: x0.7 and Resistance I's x0.8 stack -> 5.6
			helper.assertTrue(Math.abs(taken - 5.6f) < 0.05f, "10 damage -> 5.6 taken (30% less, then Resistance I), got " + taken);
			helper.assertTrue(Math.abs(SuperSoldier.damageTakenFactor(p) - 0.7f) < 1.0e-6f, "factor 0.7");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantingReplacesTheOldPrimaryPower(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		com.projecthero.mod.allmight.AllMight.grant(p);
		helper.assertTrue(com.projecthero.mod.allmight.AllMight.hasPower(p), "starts as All Might");
		SuperSoldierSerum.drink(p, true, 0.0f);
		helper.assertTrue(SuperSoldier.hasPower(p), "now a Super Soldier");
		helper.assertFalse(com.projecthero.mod.allmight.AllMight.hasPower(p), "All Might was replaced (one Primary power)");
		helper.assertTrue(HeroTiers.heroCount(p) == 1, "exactly one hero power");
		helper.succeed();
	}

	// ---------------------------------------------------------------- recipes

	// v0.17.7: the five-ingredient crafting recipe and the Furnace refinement are covered by SuperSoldierSerumV0177GameTests

	// ---------------------------------------------------------------- moves

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void everyMoveActivatesAndStartsItsCooldown(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie z = zombieAt(helper, p, 2.2, 0.0);
		pump(helper, p);
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		Object[][] moves = {
				{ 1, false, SuperSoldierAbilities.COMBO }, { 1, true, SuperSoldierAbilities.UPPERCUT },
				{ 2, false, SuperSoldierAbilities.FLYING_KICK }, { 5, false, SuperSoldierAbilities.ROUNDHOUSE },
				{ 5, true, SuperSoldierAbilities.ASSEMBLE }, { 4, true, SuperSoldierAbilities.SHIELD_STORM },
				{ 2, true, SuperSoldierAbilities.TAKEDOWN }, { 6, false, SuperSoldierAbilities.SHIELD_THROW },
				{ 3, false, SuperSoldierAbilities.DASH }, { 3, true, SuperSoldierAbilities.SHIELD_BASH },
				{ 4, false, SuperSoldierAbilities.SLAM } };
		for (Object[] m : moves) {
			unlock(p);
			p.setShiftKeyDown((Boolean) m[1]);
			int slot = (Integer) m[0];
			if (slot == 1 || slot == 2 || slot == 6) {
				// the targeted moves need their target right in front
				Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
				p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
				z.moveTo(at.x, at.y, at.z + 2.2, 180.0f, 0.0f);
			}
			AbilityRouter.handleInput(p, (Integer) m[0], true);
			String id = (String) m[2];
			// v0.14.11: the shield throw has no cooldown any more
			helper.assertTrue(SuperSoldierAbilities.SHIELD_THROW.equals(id) || SuperSoldier.cooldownRemaining(p, id) > 0, id + " started its cooldown");
			helper.assertTrue(SuperSoldier.cooldownRemaining(p, id) <= SuperSoldierAbilityManager.maxCooldown(id), id + " cooldown in range");
		}
		p.setShiftKeyDown(false);
		helper.assertTrue(z.getHealth() < 200.0f, "the attacks hurt the zombie");
		helper.assertTrue(SuperSoldier.assembleActive(p), "Assemble is running");
		helper.assertFalse(hasModifier(p, Attributes.ATTACK_DAMAGE, "super_soldier_onslaught"), "the old Onslaught bonus is never given");
		// v0.17.7: Assemble is buffs only (Strength II ...), no marks or debuffs on enemies
		helper.assertFalse(z.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "no marks any more");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST)
				&& p.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST).getAmplifier() == 1, "and gave him Strength II");
		// a cooling-down move does nothing
		unlock(p);
		p.setShiftKeyDown(true);
		long ready = SuperSoldier.state(p).abilityReadyAt.get(SuperSoldierAbilities.ASSEMBLE);
		AbilityRouter.handleInput(p, 5, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(SuperSoldier.state(p).abilityReadyAt.get(SuperSoldierAbilities.ASSEMBLE) == ready, "no restart while cooling down");
		// the multi-tick moves (combo punches, kick, takedown, slam landing) run their course without errors
		helper.runAfterDelay(80, () -> {
			helper.assertFalse(SuperSoldierAbilities.slamming(p), "the slam has landed");
			helper.assertFalse(SuperSoldierAbilities.kicking(p), "the kick has ended");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void comboStrikeLandsThreePunches(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie z = zombieAt(helper, p, 2.2, 0.0);
		pump(helper, p);
		AbilityRouter.handleInput(p, 1, true);
		float afterFirst = z.getHealth();
		helper.assertTrue(afterFirst < 200.0f, "the first punch lands at once");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(z.getHealth() <= 200.0f - (SuperSoldierConfig.COMBO_HIT_1_DAMAGE
						+ SuperSoldierConfig.COMBO_HIT_2_DAMAGE + SuperSoldierConfig.COMBO_FINISHER_DAMAGE) + 0.01f,
						"all three punches landed (5 + 7 + 10), health " + z.getHealth()))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void leapingSlamLandingHitsEverythingAround(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie a = zombieAt(helper, p, 2.0, 0.0);
		Zombie b = zombieAt(helper, p, 0.0, 2.0);
		int hit = SuperSoldierAbilities.slamImpact(p);
		helper.assertTrue(hit == 2, "both zombies in range were hit, got " + hit);
		helper.assertTrue(a.getHealth() < 200.0f && b.getHealth() < 200.0f, "and hurt");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.14.9: G / Shift+G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void flyingKickLandsAtOnceUpCloseAndAfterALunge(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie z = zombieAt(helper, p, 2.0, 0.0);
		pump(helper, p);
		helper.assertTrue(SuperSoldierAbilities.flyingKick(p), "kicked");
		helper.assertTrue(Math.abs(z.getHealth() - (200.0f - SuperSoldierConfig.KICK_DAMAGE)) < 0.05f, "12 up close, at once: " + z.getHealth());
		helper.assertFalse(SuperSoldierAbilities.kicking(p), "no lunge needed");
		helper.assertTrue(SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.FLYING_KICK) > 0, "cooldown started");

		// a far target: he lunges (a mock player is not moved by his velocity, so the test carries him) and it lands on contact
		z.discard();
		ServerPlayer q = soldier(helper);
		Zombie far = zombieAt(helper, q, 5.0, 0.0);
		pump(helper, q);
		helper.assertTrue(SuperSoldierAbilities.flyingKick(q), "lunging kick");
		helper.assertTrue(SuperSoldierAbilities.kicking(q), "in the air");
		helper.assertTrue(far.getHealth() == 200.0f, "not hit yet");
		q.moveTo(far.getX(), far.getY(), far.getZ() - 1.5, 0.0f, 0.0f);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Math.abs(far.getHealth() - (200.0f - SuperSoldierConfig.KICK_DAMAGE)) < 0.05f,
						"the lunge lands for 12: " + far.getHealth()))
				.thenExecute(() -> helper.assertFalse(SuperSoldierAbilities.kicking(q), "the lunge is over"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flyingKickNeedsATarget(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		helper.assertFalse(SuperSoldierAbilities.flyingKick(p), "nothing in front");
		helper.assertTrue(SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.FLYING_KICK) == 0, "no cooldown wasted");
		helper.assertFalse(SuperSoldierAbilities.takedown(p), "nothing to grab");
		helper.assertTrue(SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.TAKEDOWN) == 0, "no cooldown wasted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void takedownSlamsAndStuns(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie z = zombieAt(helper, p, 2.0, 0.0);
		pump(helper, p);
		Vec3 start = z.position();
		helper.assertTrue(SuperSoldierAbilities.takedown(p), "grabbed");
		helper.assertTrue(z.getHealth() == 200.0f, "the hit comes with the slam, not the grab");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Math.abs(z.getHealth() - (200.0f - SuperSoldierConfig.TAKEDOWN_DAMAGE)) < 0.05f,
						"the slam hits for 15: " + z.getHealth()))
				.thenExecute(() -> {
					var slow = z.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
					helper.assertTrue(slow != null && slow.getAmplifier() == 3, "stunned (Slowness IV)");
					helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "and weakened");
					helper.assertTrue(z.position().distanceTo(start) > 1.0, "thrown over his shoulder, not left where it stood");
				})
				.thenSucceed();
	}

	// ---------------------------------------------------------------- v0.14.9: C, the shield throw

	/** Three zombies in a row in front of the player (who stands at (2.5, 2, 1.5) facing +Z). */
	private static Zombie[] threeZombies(GameTestHelper helper, ServerPlayer p) {
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return new Zombie[] { zombieAt(helper, p, 3.5, 0.0), zombieAt(helper, p, 3.0, -1.6), zombieAt(helper, p, 3.0, 1.6) };
	}

	private static int shieldsAround(GameTestHelper helper, ServerPlayer p) {
		// only this player's shields: neighbouring tests throw theirs too
		return helper.getLevel().getEntitiesOfClass(SoldierShieldEntity.class, new AABB(p.blockPosition()).inflate(40),
				s -> p.getUUID().equals(s.ownerId())).size();
	}

	private static int looseShields(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(1.0),
				ie -> ie.getItem().getItem() instanceof net.minecraft.world.item.ShieldItem).size();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void adamantiumShieldHitsThreeZombiesForNineAndComesBackIntact(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie[] zs = threeZombies(helper, p);
		ItemStack shield = new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD);
		shield.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Sentinel"));
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, shield);
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the shield left his hand");
		helper.assertTrue(shieldsAround(helper, p) == 1, "one shield in flight");
		int cd = SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.SHIELD_THROW);
		helper.assertTrue(cd == 0, "v0.14.11: no cooldown on the throw, got " + cd);
		helper.startSequence()
				.thenWaitUntil(() -> {
					for (Zombie z : zs) {
						helper.assertTrue(Math.abs(z.getHealth() - (200.0f - SuperSoldierConfig.SHIELD_DAMAGE)) < 0.05f,
								"every zombie hit once for 9: " + zs[0].getHealth() + " / " + zs[1].getHealth() + " / " + zs[2].getHealth());
					}
				})
				.thenWaitUntil(() -> helper.assertTrue(shieldsAround(helper, p) == 0, "the shield came back and was caught"))
				.thenExecute(() -> {
					helper.assertTrue(p.getMainHandItem() == shield, "the very same stack is back in his hand");
					helper.assertTrue("Sentinel".equals(p.getMainHandItem().getHoverName().getString()), "with its name intact");
					helper.assertTrue(p.getInventory().countItem(SuperSoldierItems.ADAMANTIUM_SHIELD) == 1, "exactly one shield (no copy)");
					helper.assertTrue(looseShields(helper, p) == 0, "and none dropped");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void ordinaryShieldAlsoHitsThreeZombiesButWeaker(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie[] zs = threeZombies(helper, p);
		ItemStack shield = new ItemStack(Items.SHIELD);
		shield.setDamageValue(57);
		shield.set(net.minecraft.core.component.DataComponents.BASE_COLOR, net.minecraft.world.item.DyeColor.RED);
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shield);
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown from the off hand");
		helper.assertTrue(p.getOffhandItem().isEmpty(), "the shield left his hand");
		int cd = SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.SHIELD_THROW);
		helper.assertTrue(cd == 0, "v0.14.11: no cooldown on the throw, got " + cd);
		helper.startSequence()
				.thenWaitUntil(() -> {
					for (Zombie z : zs) {
						helper.assertTrue(Math.abs(z.getHealth() - (200.0f - SuperSoldierConfig.NORMAL_SHIELD_DAMAGE)) < 0.05f,
								"every zombie hit once for 6: " + zs[0].getHealth() + " / " + zs[1].getHealth() + " / " + zs[2].getHealth());
					}
				})
				.thenWaitUntil(() -> helper.assertTrue(shieldsAround(helper, p) == 0, "the shield came back and was caught"))
				.thenExecute(() -> {
					helper.assertTrue(p.getOffhandItem() == shield, "the very same stack is back in his off hand");
					helper.assertTrue(shield.getDamageValue() == 57, "durability untouched, got " + shield.getDamageValue());
					helper.assertTrue(shield.get(net.minecraft.core.component.DataComponents.BASE_COLOR) == net.minecraft.world.item.DyeColor.RED,
							"banner colour intact");
					helper.assertTrue(p.getInventory().countItem(Items.SHIELD) == 1 && looseShields(helper, p) == 0, "exactly one shield");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void caughtShieldGoesToTheInventoryWhenTheHandIsFull(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		zombieAt(helper, p, 3.0, 0.0);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(shieldsAround(helper, p) == 0, "caught"))
				.thenExecute(() -> {
					helper.assertTrue(p.getMainHandItem().is(Items.STICK), "the stick stays in his hand");
					helper.assertTrue(p.getInventory().countItem(SuperSoldierItems.ADAMANTIUM_SHIELD) == 1, "the shield went into the inventory");
					helper.assertTrue(looseShields(helper, p) == 0, "nothing dropped");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shieldDropsWhereItIsIfTheThrowerDies(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		p.kill();
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(shieldsAround(helper, p) == 0, "the flying shield is gone"))
				.thenExecute(() -> helper.assertTrue(looseShields(helper, p) == 1, "and lies on the ground as exactly one item"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noShieldNoThrow(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		helper.assertFalse(SuperSoldierAbilities.shieldThrow(p), "nothing to throw");
		helper.assertTrue(SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.SHIELD_THROW) == 0, "no cooldown");
		helper.assertTrue(shieldsAround(helper, p) == 0, "no shield entity");
		// a non-Super-Soldier with a shield does nothing on C
		ServerPlayer plain = player(helper);
		plain.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
		AbilityRouter.handleInput(plain, 6, true);
		helper.assertTrue(plain.getMainHandItem().is(Items.SHIELD), "an ordinary player keeps his shield");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.14.9: shield item, suit, recipes

	@GameTest(template = EMPTY_STRUCTURE)
	public void adamantiumShieldIsARealShield(GameTestHelper helper) {
		ItemStack s = new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD);
		helper.assertTrue(s.getItem() instanceof net.minecraft.world.item.ShieldItem, "extends the vanilla shield");
		helper.assertTrue(s.getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK, "raises to block");
		helper.assertFalse(s.isDamageableItem(), "unbreakable");
		helper.assertTrue(s.getMaxStackSize() == 1, "one per slot");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void newRecipesExist(GameTestHelper helper) {
		var level = helper.getLevel();
		Object[][] recipes = {
				{ "adamantium_shield", SuperSoldierItems.ADAMANTIUM_SHIELD }, { "captain_america_helmet", SuperSoldierItems.HELMET },
				{ "captain_america_chestplate", SuperSoldierItems.CHESTPLATE }, { "captain_america_leggings", SuperSoldierItems.LEGGINGS },
				{ "captain_america_boots", SuperSoldierItems.BOOTS } };
		for (Object[] r : recipes) {
			var holder = level.getRecipeManager().byKey(ProjectHeroMod.id((String) r[0]));
			helper.assertTrue(holder.isPresent(), "recipe " + r[0] + " is loaded");
			helper.assertTrue(holder.get().value().getResultItem(level.registryAccess()).is((net.minecraft.world.item.Item) r[1]),
					"recipe " + r[0] + " makes the right item");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyASuperSoldierCanWearTheSuit(GameTestHelper helper) {
		// an ordinary player: right-click equip refused, a piece forced on pops off into the inventory
		ServerPlayer plain = player(helper);
		plain.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.HELMET));
		var result = SuperSoldierItems.HELMET.use(helper.getLevel(), plain, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertFalse(result.getResult().consumesAction(), "right-click equip refused");
		helper.assertTrue(plain.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(), "not worn");
		plain.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(SuperSoldierItems.CHESTPLATE));
		SuperSoldierAbilityManager.serverTick(plain);
		helper.assertTrue(plain.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "the chestplate popped off");
		helper.assertTrue(plain.getInventory().countItem(SuperSoldierItems.CHESTPLATE) == 1, "into his inventory");

		// a Super Soldier wears all four; losing the power takes them off
		ServerPlayer p = soldier(helper);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.HELMET));
		var worn = SuperSoldierItems.HELMET.use(helper.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(worn.getResult().consumesAction(), "he can right-click it on");
		// (a mock player may count as creative here, which keeps a copy in the hand: clear it so only the worn one is counted)
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(SuperSoldierItems.CHESTPLATE));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(SuperSoldierItems.LEGGINGS));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(SuperSoldierItems.BOOTS));
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(SuperSoldierItems.HELMET), "helmet stays on");
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(SuperSoldierItems.BOOTS), "boots stay on");
		int armour = SuperSoldierItems.HELMET.getDefense() + SuperSoldierItems.CHESTPLATE.getDefense()
				+ SuperSoldierItems.LEGGINGS.getDefense() + SuperSoldierItems.BOOTS.getDefense();
		helper.assertTrue(armour == 20, "diamond: 3 + 8 + 6 + 3 armour, got " + armour);
		helper.assertTrue(SuperSoldierItems.CHESTPLATE.getToughness() == 2.0f, "diamond toughness 2");
		SuperSoldier.revoke(p);
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
				&& p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "power gone: the suit comes off");
		int helmets = p.getInventory().countItem(SuperSoldierItems.HELMET);
		helper.assertTrue(helmets == 1, "into his inventory, helmets " + helmets);
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.7 rework: the two ultimates (Assemble, X / Shift+X and V since the
	// user's later list: SuperSoldierKitV0177GameTests)

	private static boolean has(net.minecraft.world.entity.LivingEntity e, Holder<net.minecraft.world.effect.MobEffect> effect) {
		return e.hasEffect(effect);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void barragePicksWithoutAShieldAndHitsForSixFlurryPlusTheHaymaker(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie z = zombieAt(helper, p, 1.5, 0.0);
		Zombie by = zombieAt(helper, p, 1.5, 2.0); // within 4 of the target: the haymaker's burst
		pump(helper, p);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 4, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(SuperSoldierAbilities.barrageRunning(p), "bare-handed Shift+Z is the Barrage");
		helper.assertTrue(SuperSoldierAbilities.stormPhase(p) == -1, "not the Storm");
		int cd = SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.BARRAGE);
		helper.assertTrue(cd > 0 && cd == SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.SHIELD_STORM), "one shared cooldown");
		helper.assertTrue(SuperSoldier.state(p).animId == SuperSoldierState.ANIM_BARRAGE, "already arrived: the flurry clip plays");
		float expected = SuperSoldierConfig.BARRAGE_HITS * SuperSoldierConfig.BARRAGE_HIT_DAMAGE + SuperSoldierConfig.BARRAGE_HAYMAKER_DAMAGE;
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(SuperSoldierAbilities.barrageRunning(p), "the Barrage is over"))
				.thenExecute(() -> {
					helper.assertTrue(Math.abs((200.0f - z.getHealth()) - expected) < 0.1f,
							"6 x 7.5 + 20 = " + expected + ", took " + (200.0f - z.getHealth()));
					helper.assertTrue(Math.abs((200.0f - by.getHealth()) - SuperSoldierConfig.BARRAGE_BURST_DAMAGE) < 0.1f,
							"the burst hit the one beside it for 10, took " + (200.0f - by.getHealth()));
					// with a shield now, Shift+Z is the Storm -- but the shared cooldown holds it
					unlock(p);
					p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
					p.setShiftKeyDown(true);
					AbilityRouter.handleInput(p, 4, true);
					p.setShiftKeyDown(false);
					helper.assertTrue(SuperSoldierAbilities.stormPhase(p) == -1, "the Storm waits on the shared cooldown");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void shieldStormPicksWithAShieldRicochetsThenStarSlams(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie[] zs = { zombieAt(helper, p, 3.0, 0.0), zombieAt(helper, p, 3.5, 2.0), zombieAt(helper, p, 2.5, -2.0) };
		ItemStack shield = new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD);
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shield);
		pump(helper, p);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 4, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(SuperSoldierAbilities.stormPhase(p) == 0, "a shield in hand: Shift+Z is the Shield Storm (rising)");
		helper.assertFalse(SuperSoldierAbilities.barrageRunning(p), "not the Barrage");
		helper.assertTrue(SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.SHIELD_STORM) > 0, "cooldown started");
		helper.assertTrue(SuperSoldier.state(p).animOff, "the off-hand shield arm leads the clip");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(SuperSoldierAbilities.stormPhase(p) >= 1, "thrown"))
				.thenExecute(() -> helper.assertTrue(p.getOffhandItem().isEmpty(), "the shield left his hand"))
				.thenWaitUntil(() -> helper.assertTrue(SuperSoldierAbilities.stormPhase(p) == -1, "caught, dived and slammed"))
				.thenExecute(() -> {
					helper.assertTrue(p.getOffhandItem() == shield, "the same shield is back in his off hand");
					helper.assertTrue(SuperSoldier.state(p).animId == SuperSoldierState.ANIM_STORM_SLAM, "the slam clip");
					for (Zombie z : zs) {
						float took = 200.0f - z.getHealth();
						// one ricochet hit (15) and the slam (20 at the centre, 12 at the 6-block edge); nothing per hit over 20
						helper.assertTrue(took >= SuperSoldierConfig.STORM_HIT_DAMAGE + 12.0f - 0.1f
								&& took <= SuperSoldierConfig.STORM_HIT_DAMAGE + SuperSoldierConfig.STORM_SLAM_DAMAGE + 0.1f,
								"15 + 12..20, took " + took);
					}
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void bossCapHoldsForTheUltimatesAndBossesAreNeverLaunched(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie boss = zombieAt(helper, p, 1.5, 0.0);
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0); // a "boss": 6% cap = 18 per hit
		boss.setHealth(300.0f);
		helper.assertTrue(SuperSoldierAbilities.isBoss(boss), "counts as a boss");
		pump(helper, p);
		helper.assertTrue(SuperSoldierAbilities.barrage(p), "Barrage on the boss");
		float expected = SuperSoldierConfig.BARRAGE_HITS * SuperSoldierConfig.BARRAGE_HIT_DAMAGE + 300.0f * SuperSoldierConfig.BOSS_MAX_FRACTION_PER_HIT;
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(SuperSoldierAbilities.barrageRunning(p), "over"))
				.thenExecute(() -> {
					helper.assertTrue(Math.abs((300.0f - boss.getHealth()) - expected) < 0.1f,
							"the haymaker capped at 18: expected " + expected + ", took " + (300.0f - boss.getHealth()));
					helper.assertTrue(boss.getDeltaMovement().y < 0.3, "never launched, vy " + boss.getDeltaMovement().y);
					// the star slam on a boss: capped too
					float before = boss.getHealth();
					SuperSoldierAbilities.stormSlam(p);
					helper.assertTrue(before - boss.getHealth() <= 18.05f, "slam capped at 18, took " + (before - boss.getHealth()));
					helper.assertTrue(boss.getDeltaMovement().y < 0.3, "and not knocked up");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ownPetsAreNeverTargets(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		net.minecraft.world.entity.animal.Wolf wolf = EntityType.WOLF.create(helper.getLevel());
		wolf.moveTo(p.getX(), p.getY(), p.getZ() + 2.0);
		wolf.tame(p);
		helper.getLevel().addFreshEntity(wolf);
		helper.assertFalse(SuperSoldierAbilities.canTarget(p, wolf), "his own pet is safe");
		helper.assertFalse(SuperSoldierAbilities.canTarget(p, p), "never himself");
		helper.succeed();
	}
	// ---------------------------------------------------------------- v0.17.5: the shield on the back, the synced thrower

	@GameTest(template = EMPTY_STRUCTURE)
	public void carriedShieldIsSlungOnTheBackOnlyWhileNeitherHandHoldsOne(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		p.getInventory().clearContent();
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldStow.stowed(p).isEmpty(), "nothing carried: nothing on the back");
		ItemStack vib = new ItemStack(com.projecthero.mod.ultron.contagion.item.ContagionItems.VIBRANIUM_SHIELD);
		p.getInventory().setItem(4, vib);
		p.getInventory().selected = 0;
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldStow.stowed(p) == vib, "a shield in the hotbar, empty hands: on the back");
		com.projecthero.mod.supersoldier.CapShieldStow.update(p);
		ItemStack synced = p.getAttached(com.projecthero.mod.supersoldier.CapShieldStow.STOWED);
		helper.assertTrue(synced != null && synced.is(vib.getItem()) && synced != vib, "a copy is attached for the clients (the stack stays put)");
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldStow.stowed(p).isEmpty(), "one in a hand: none on the back");
		com.projecthero.mod.supersoldier.CapShieldStow.update(p);
		helper.assertTrue(p.getAttached(com.projecthero.mod.supersoldier.CapShieldStow.STOWED) == null, "and the attachment is cleared");
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
		p.getInventory().setItem(4, ItemStack.EMPTY);
		p.getInventory().setItem(20, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldStow.stowed(p).isEmpty(), "only the hotbar counts");
		p.getInventory().setItem(2, new ItemStack(Items.SHIELD));
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldStow.stowed(p).isEmpty(), "a vanilla shield is never slung");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void thrownShieldCarriesItsThrowerAndHandForTheClient(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown from the off hand");
		SoldierShieldEntity s = helper.getLevel().getEntitiesOfClass(SoldierShieldEntity.class, new AABB(p.blockPosition()).inflate(4),
				e -> p.getUUID().equals(e.ownerId())).stream().findFirst().orElse(null);
		helper.assertTrue(s != null, "in flight");
		helper.assertTrue(s.ownerEntityId() == p.getId(), "synced thrower id");
		helper.assertTrue(s.thrownFromOffHand(), "synced off hand");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(shieldsAround(helper, p) == 0, "came back"))
				.thenExecute(() -> helper.assertTrue(p.getOffhandItem().is(SuperSoldierItems.ADAMANTIUM_SHIELD), "back in the off hand"))
				.thenSucceed();
	}

	// ---------------------------------------------------------------- v0.17.5: the Captain America Battle Suit's shield slot (N)

	private static ItemStack battleTop(ServerPlayer p) {
		ItemStack top = new ItemStack(com.projecthero.mod.supersoldier.item.CapBattleSuitItem.TOP);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, top);
		return top;
	}

	private static ItemStack named(net.minecraft.world.item.Item item, String name) {
		ItemStack s = new ItemStack(item);
		s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
		return s;
	}

	private static boolean isNamed(ItemStack s, String name) {
		return !s.isEmpty() && name.equals(s.getHoverName().getString());
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void battleSuitNStoresRetrievesAndSwapsTheShield(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		battleTop(p);
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldSlot.hasSlot(p), "a Super Soldier in the top has the slot");
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, named(SuperSoldierItems.ADAMANTIUM_SHIELD, "Sentinel"));
		AbilityRouter.handleInput(p, 8, true);
		AbilityRouter.handleInput(p, 8, false);
		helper.assertTrue(p.getMainHandItem().isEmpty(), "N: the hand is free");
		helper.assertTrue(isNamed(com.projecthero.mod.supersoldier.CapShieldSlot.slotted(p), "Sentinel"), "N: the shield is in the slot");
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldSlot.hasThrowable(p), "a slotted shield counts as held");
		// a second shield in hand: N swaps them
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				named(com.projecthero.mod.ultron.contagion.item.ContagionItems.VIBRANIUM_SHIELD, "Vib"));
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(isNamed(p.getMainHandItem(), "Sentinel") && isNamed(com.projecthero.mod.supersoldier.CapShieldSlot.slotted(p), "Vib"),
				"N with a shield in hand swaps it with the slotted one");
		// empty hand: N takes it out
		p.getInventory().add(p.getMainHandItem().copy());
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(isNamed(p.getMainHandItem(), "Vib"), "N with an empty hand takes the shield back out");
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldSlot.slotted(p).isEmpty(), "and the slot is empty");
		// a vanilla shield is not a Captain America shield: it stays in hand
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(p.getMainHandItem().is(Items.SHIELD) && com.projecthero.mod.supersoldier.CapShieldSlot.slotted(p).isEmpty(),
				"a vanilla shield doesn't go in the slot");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void slottedShieldThrowsWithCAndFliesHomeIntoTheSlot(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie[] zs = threeZombies(helper, p);
		battleTop(p);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, named(SuperSoldierItems.ADAMANTIUM_SHIELD, "Sentinel"));
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldSlot.toggle(p), "stored");
		helper.assertTrue(SuperSoldierAbilities.shieldHand(p) == null, "nothing in either hand");
		AbilityRouter.handleInput(p, 6, true); // C
		helper.assertTrue(shieldsAround(helper, p) == 1, "C threw the slotted shield");
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldSlot.slotted(p).isEmpty(), "the slot is empty while it flies");
		helper.startSequence()
				.thenWaitUntil(() -> {
					for (Zombie z : zs) {
						helper.assertTrue(Math.abs(z.getHealth() - (200.0f - SuperSoldierConfig.SHIELD_DAMAGE)) < 0.05f,
								"every zombie hit once for 9 (full Adamantium numbers from the slot)");
					}
				})
				.thenWaitUntil(() -> helper.assertTrue(shieldsAround(helper, p) == 0, "it came back"))
				.thenExecute(() -> {
					helper.assertTrue(isNamed(com.projecthero.mod.supersoldier.CapShieldSlot.slotted(p), "Sentinel"), "back in the slot, name intact");
					helper.assertTrue(p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty(), "not in either hand");
					helper.assertTrue(p.getInventory().countItem(SuperSoldierItems.ADAMANTIUM_SHIELD) == 0 && looseShields(helper, p) == 0,
							"no copy in the inventory or on the ground");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void slottedShieldRaisedWithAnEmptyHandBlocksFromTheFront(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		battleTop(p);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		com.projecthero.mod.supersoldier.CapShieldSlot.toggle(p);
		p.setYRot(0f);
		p.setYHeadRot(0f);
		Zombie front = zombieAt(helper, p, 2.0, 0.0);
		Zombie behind = zombieAt(helper, p, -2.0, 0.0);
		helper.startSequence()
				.thenIdle(70) // a fresh (mock) player is spawn-invulnerable for 60 ticks
				.thenExecute(() -> {
					com.projecthero.mod.supersoldier.CapShieldSlot.guardStart(p);
					helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldSlot.isGuarding(p), "raised");
				})
				.thenIdle(com.projecthero.mod.supersoldier.CapShieldSlot.GUARD_DELAY + 2)
				.thenExecute(() -> {
					p.setHealth(p.getMaxHealth());
					float hp = p.getHealth();
					p.hurt(helper.getLevel().damageSources().mobAttack(front), 6f);
					helper.assertTrue(p.getHealth() == hp, "a hit from the front is blocked, health " + hp + " -> " + p.getHealth());
					p.hurt(helper.getLevel().damageSources().mobAttack(behind), 6f);
					helper.assertTrue(p.getHealth() < hp, "a hit from behind is not");
					com.projecthero.mod.supersoldier.CapShieldSlot.guardStop(p);
					helper.assertFalse(com.projecthero.mod.supersoldier.CapShieldSlot.isGuarding(p), "lowered");
					// holding something in the main hand: no guard
					p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
					com.projecthero.mod.supersoldier.CapShieldSlot.guardStart(p);
					helper.assertFalse(com.projecthero.mod.supersoldier.CapShieldSlot.isGuarding(p), "no guard with something in the main hand");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void takingTheBattleTopOffPutsTheShieldInTheInventory(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		ItemStack top = battleTop(p);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, named(SuperSoldierItems.ADAMANTIUM_SHIELD, "Sentinel"));
		com.projecthero.mod.supersoldier.CapShieldSlot.toggle(p);
		// the slot is saved on the top (death / relog / dropped): a save round trip keeps it
		net.minecraft.nbt.Tag saved = top.save(helper.getLevel().registryAccess());
		ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow();
		helper.assertTrue(isNamed(com.projecthero.mod.supersoldier.CapShieldSlot.get(loaded), "Sentinel"), "the slot survives a save / load");
		// off it comes, into the inventory
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
		p.getInventory().setItem(5, top); // (setItem keeps this very stack; Inventory.add would move it into a copy)
		pump(helper, p);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldSlot.get(top).isEmpty(), "the top let go of it"))
				.thenExecute(() -> {
					helper.assertTrue(p.getInventory().countItem(SuperSoldierItems.ADAMANTIUM_SHIELD) == 1, "exactly one shield, in the inventory");
					helper.assertTrue(looseShields(helper, p) == 0, "nothing dropped");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void nonSuperSoldierInTheBattleSuitGetsNoSlot(GameTestHelper helper) {
		ServerPlayer plain = player(helper);
		ItemStack top = battleTop(plain);
		helper.assertTrue(plain.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST) == top, "anyone can wear the Battle Suit");
		helper.assertFalse(com.projecthero.mod.supersoldier.CapShieldSlot.hasSlot(plain), "but only a Super Soldier gets the slot");
		plain.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		AbilityRouter.handleInput(plain, 8, true);
		helper.assertTrue(plain.getMainHandItem().is(SuperSoldierItems.ADAMANTIUM_SHIELD), "N does nothing: the shield stays in hand");
		helper.assertFalse(com.projecthero.mod.supersoldier.CapShieldSlot.toggle(plain), "no slot to toggle");
		// a top that already holds a shield (from a Super Soldier) gives it up to a wearer without the slot
		ItemStack carried = new ItemStack(com.projecthero.mod.supersoldier.item.CapBattleSuitItem.TOP);
		carried.set(com.projecthero.mod.supersoldier.CapShieldSlot.SLOT,
				net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD))));
		plain.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		plain.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, carried);
		com.projecthero.mod.supersoldier.CapShieldSlot.tick(plain);
		helper.assertTrue(com.projecthero.mod.supersoldier.CapShieldSlot.get(carried).isEmpty(), "the slot was emptied");
		helper.assertTrue(plain.getInventory().countItem(SuperSoldierItems.ADAMANTIUM_SHIELD) == 1, "into his inventory");
		helper.succeed();
	}
}
