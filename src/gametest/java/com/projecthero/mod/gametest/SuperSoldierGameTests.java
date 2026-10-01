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
 * refinement, every one of the eleven moves activating and starting its cooldown, the roll's invulnerability and the
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
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		Object[][] moves = {
				{ 1, false, SuperSoldierAbilities.COMBO }, { 1, true, SuperSoldierAbilities.UPPERCUT },
				{ 2, false, SuperSoldierAbilities.FLYING_KICK }, { 5, false, SuperSoldierAbilities.BATTLE_CRY },
				{ 5, true, SuperSoldierAbilities.FOCUS }, { 4, true, SuperSoldierAbilities.ONSLAUGHT },
				{ 2, true, SuperSoldierAbilities.TAKEDOWN }, { 6, false, SuperSoldierAbilities.SHIELD_THROW },
				{ 3, false, SuperSoldierAbilities.ROLL }, { 3, true, SuperSoldierAbilities.HIGH_LEAP },
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
						"the slam hits for 14: " + z.getHealth()))
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
		helper.assertTrue(cd > 0 && cd <= SuperSoldierConfig.SHIELD_THROW_COOLDOWN, "the adamantium cooldown, got " + cd);
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
		helper.assertTrue(cd > SuperSoldierConfig.SHIELD_THROW_COOLDOWN && cd <= SuperSoldierConfig.NORMAL_SHIELD_THROW_COOLDOWN,
				"the longer ordinary-shield cooldown, got " + cd);
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
		helper.assertTrue(armour == 18, "3 + 7 + 6 + 2 armour, got " + armour);
		SuperSoldier.revoke(p);
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
				&& p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "power gone: the suit comes off");
		int helmets = p.getInventory().countItem(SuperSoldierItems.HELMET);
		helper.assertTrue(helmets == 1, "into his inventory, helmets " + helmets);
		helper.succeed();
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
