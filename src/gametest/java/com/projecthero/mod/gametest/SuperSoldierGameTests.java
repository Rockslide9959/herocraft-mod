package com.projecthero.mod.gametest;

import java.util.ArrayList;
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
import com.projecthero.mod.supersoldier.recipe.SuperSoldierSerumRecipe;

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
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for the Super Soldier (v0.14.8): the serums (grant / rejection with an injected roll, creative
 * included), the passives and their removal, the 30% damage reduction, the potion-trio crafting recipe and the blasting
 * refinement, every one of the ten moves activating and starting its cooldown, the shield's ricochet, the roll's
 * invulnerability and the one-Primary-power rule. Mock players are not reliably ticked by the server, so tests that need
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

	private static ItemStack potion(Holder<Potion> potion) {
		return PotionContents.createItemStack(Items.POTION, potion);
	}

	private static List<ItemStack> grid(ItemStack... stacks) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : stacks) {
			out.add(s);
		}
		while (out.size() < 9) {
			out.add(ItemStack.EMPTY);
		}
		return out;
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
		helper.assertFalse(hasModifier(p, Attributes.ATTACK_DAMAGE, "super_soldier_unarmed"), "holding something: no unarmed bonus");
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
		helper.assertTrue(SuperSoldier.hasPower(p), "the refined serum item grants the power");
		helper.assertTrue(result.is(Items.GLASS_BOTTLE), "and leaves a glass bottle, got " + result);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void damageTakenIsReducedThirtyPercent(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		// a fresh (mock) player is spawn-invulnerable for 60 ticks
		helper.runAfterDelay(70, () -> {
			p.setHealth(p.getMaxHealth());
			float before = p.getHealth();
			p.hurt(p.damageSources().generic(), 10.0f);
			float taken = before - p.getHealth();
			helper.assertTrue(Math.abs(taken - 7.0f) < 0.05f, "10 damage -> 7 taken, got " + taken);
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void craftingRecipeMatchesOnlyTheRightPotionTrio(GameTestHelper helper) {
		ItemStack str = potion(Potions.STRENGTH);
		ItemStack spd = potion(Potions.SWIFTNESS);
		ItemStack jmp = potion(Potions.LEAPING);
		helper.assertTrue(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, jmp)), "strength + swiftness + leaping");
		helper.assertTrue(SuperSoldierSerumRecipe.matchesItems(grid(ItemStack.EMPTY, jmp, ItemStack.EMPTY, str, ItemStack.EMPTY, spd)), "any slots");
		helper.assertTrue(SuperSoldierSerumRecipe.matchesItems(grid(potion(Potions.STRONG_STRENGTH), potion(Potions.LONG_SWIFTNESS),
				potion(Potions.STRONG_LEAPING))), "long / strong variants count");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, str, jmp)), "two strength, no swiftness");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd)), "only two potions");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, jmp, potion(Potions.LEAPING))), "four potions");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, jmp, new ItemStack(Items.DIRT))), "an extra item");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, potion(Potions.AWKWARD))), "an awkward potion");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd,
				PotionContents.createItemStack(Items.SPLASH_POTION, Potions.LEAPING))), "a splash potion");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, new ItemStack(Items.POTION))), "a water bottle");

		// the real recipe manager finds it from the JSON (projecthero:super_soldier_serum)
		var level = helper.getLevel();
		var hit = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid(jmp, str, spd)), level);
		helper.assertTrue(hit.isPresent(), "the crafting table knows the recipe");
		helper.assertTrue(hit.get().value().assemble(CraftingInput.of(3, 3, grid(jmp, str, spd)), level.registryAccess())
				.is(SuperSoldierItems.UNREFINED_SERUM), "it makes the unrefined serum");
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid(str, str, spd)), level)
				.map(h -> !(h.value() instanceof SuperSoldierSerumRecipe)).orElse(true), "a wrong trio is not our recipe");

		var blast = level.getRecipeManager().getRecipeFor(RecipeType.BLASTING,
				new SingleRecipeInput(new ItemStack(SuperSoldierItems.UNREFINED_SERUM)), level);
		helper.assertTrue(blast.isPresent(), "the blast furnace refines it");
		helper.assertTrue(blast.get().value().getResultItem(level.registryAccess()).is(SuperSoldierItems.REFINED_SERUM), "into the refined serum");
		helper.assertTrue(blast.get().value().getCookingTime() == 12000, "in 10 minutes, got " + blast.get().value().getCookingTime());
		helper.succeed();
	}

	// ---------------------------------------------------------------- moves

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void everyMoveActivatesAndStartsItsCooldown(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie z = zombieAt(helper, p, 2.2, 0.0);
		pump(helper, p);
		Object[][] moves = {
				{ 1, false, SuperSoldierAbilities.COMBO }, { 1, true, SuperSoldierAbilities.UPPERCUT },
				{ 2, false, SuperSoldierAbilities.SHIELD_THROW }, { 5, false, SuperSoldierAbilities.BATTLE_CRY },
				{ 5, true, SuperSoldierAbilities.FOCUS }, { 4, true, SuperSoldierAbilities.ONSLAUGHT },
				{ 2, true, SuperSoldierAbilities.SHIELD_BASH }, { 3, false, SuperSoldierAbilities.ROLL },
				{ 3, true, SuperSoldierAbilities.HIGH_LEAP }, { 4, false, SuperSoldierAbilities.SLAM } };
		for (Object[] m : moves) {
			unlock(p);
			p.setShiftKeyDown((Boolean) m[1]);
			if ((Integer) m[0] == 1) {
				// the melee moves need their target right in front
				Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
				p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
				z.moveTo(at.x, at.y, at.z + 2.2, 180.0f, 0.0f);
			}
			AbilityRouter.handleInput(p, (Integer) m[0], true);
			String id = (String) m[2];
			helper.assertTrue(SuperSoldier.cooldownRemaining(p, id) > 0, id + " started its cooldown");
			helper.assertTrue(SuperSoldier.cooldownRemaining(p, id) <= SuperSoldierAbilityManager.maxCooldown(id), id + " cooldown in range");
		}
		p.setShiftKeyDown(false);
		helper.assertTrue(z.getHealth() < 200.0f, "the attacks hurt the zombie");
		helper.assertTrue(SuperSoldier.onslaughtActive(p), "Onslaught is running");
		helper.assertTrue(hasModifier(p, Attributes.ATTACK_DAMAGE, "super_soldier_onslaught"), "with its melee bonus");
		helper.assertTrue(SuperSoldier.focusActive(p) && SuperSoldierAbilities.markCount(p) >= 1, "Focus marked the zombie");
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "the marked zombie glows");
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "Battle Cry weakened it");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST), "and gave him Strength");
		// a cooling-down move does nothing
		unlock(p);
		long ready = SuperSoldier.state(p).abilityReadyAt.get(SuperSoldierAbilities.BATTLE_CRY);
		AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(SuperSoldier.state(p).abilityReadyAt.get(SuperSoldierAbilities.BATTLE_CRY) == ready, "no restart while cooling down");
		// the multi-tick moves (combo punches, bash, slam landing) run their course without errors
		helper.runAfterDelay(80, () -> {
			helper.assertFalse(SuperSoldierAbilities.slamming(p), "the slam has landed");
			helper.assertFalse(SuperSoldierAbilities.bashing(p), "the bash has ended");
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
				.thenWaitUntil(() -> helper.assertTrue(z.getHealth() <= 200.0f - (2 * SuperSoldierConfig.COMBO_HIT_DAMAGE
						+ SuperSoldierConfig.COMBO_FINISHER_DAMAGE) + 0.01f, "all three punches landed, health " + z.getHealth()))
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

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void thrownShieldRicochetsIntoASecondEnemyAndComesBack(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie first = zombieAt(helper, p, 3.0, 0.0);
		Zombie second = zombieAt(helper, p, 3.0, 2.5);
		pump(helper, p);
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(first.getHealth() < 200.0f && second.getHealth() < 200.0f,
						"both zombies hit: " + first.getHealth() + " / " + second.getHealth()))
				.thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(SoldierShieldEntity.class,
						new AABB(p.blockPosition()).inflate(40)).isEmpty(), "the shield came back and was caught"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void tacticalRollGivesInvulnerabilityFrames(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		helper.runAfterDelay(70, () -> { // past the spawn invulnerability, so only the roll can protect him
			p.setHealth(p.getMaxHealth());
			helper.assertTrue(SuperSoldierAbilities.tacticalRoll(p), "rolled");
			helper.assertTrue(SuperSoldierAbilities.rolling(p), "in the roll's frames");
			p.hurt(p.damageSources().generic(), 10.0f);
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "nothing hurts him mid-roll");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void focusMakesPunchesOnMarkedEnemiesCritical(GameTestHelper helper) {
		ServerPlayer p = soldier(helper);
		Zombie z = zombieAt(helper, p, 2.0, 0.0);
		helper.assertTrue(SuperSoldierAbilities.tacticalFocus(p), "focus");
		helper.assertTrue(SuperSoldierAbilities.isMarked(p, z), "the zombie is marked");
		z.hurt(p.damageSources().playerAttack(p), 10.0f);
		helper.assertTrue(Math.abs((200.0f - z.getHealth()) - 15.0f) < 0.6f, "10 -> 15 on a marked enemy, took " + (200.0f - z.getHealth()));
		helper.succeed();
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
}
