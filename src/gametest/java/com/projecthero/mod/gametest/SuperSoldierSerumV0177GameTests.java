package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.supersoldier.SerumTransformation;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierSerum;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;
import com.projecthero.mod.supersoldier.recipe.SuperSoldierSerumRecipe;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Super Soldier Serum (the user's acquisition spec): the unrefined serum from exactly a Potion of Strength, Swiftness and
 * Regeneration, a Golden Apple and a Glass Bottle (Leaping, splash potions, an enchanted apple or a missing apple / bottle refused);
 * the refinement is a Furnace recipe of 12000 ticks (no Blast Furnace one any more); drinking the unrefined serum is 10% the
 * transformation / 90% death (injected roll); the transformation lasts exactly 60 s and then grants, keeps Nausea / Weakness /
 * Slowness on (milk does not skip it), never takes him below half a heart, grants nothing if he dies, survives a save / load (relog),
 * and a second serum during it is refused and kept. Mock players are driven through {@link SerumTransformation#tick} directly.
 */
public class SuperSoldierSerumV0177GameTests implements FabricGameTest {
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static ItemStack potion(Holder<Potion> potion) {
		return PotionContents.createItemStack(Items.POTION, potion);
	}

	private static List<ItemStack> grid(ItemStack... stacks) {
		List<ItemStack> out = new ArrayList<>(List.of(stacks));
		while (out.size() < 9) {
			out.add(ItemStack.EMPTY);
		}
		return out;
	}

	/** {@code n} transformation ticks, checking each one moves it on by exactly one tick. */
	private static void advance(GameTestHelper helper, ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			int before = SerumTransformation.elapsed(p);
			SerumTransformation.tick(p);
			int after = SerumTransformation.elapsed(p);
			if (after >= 0) {
				helper.assertTrue(after - before == 1, "one tick moves it on by one, " + before + " -> " + after);
			}
		}
	}

	private static boolean sick(ServerPlayer p) {
		return p.hasEffect(MobEffects.CONFUSION) && p.hasEffect(MobEffects.WEAKNESS) && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
	}

	private static boolean anySick(ServerPlayer p) {
		return p.hasEffect(MobEffects.CONFUSION) || p.hasEffect(MobEffects.WEAKNESS) || p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
	}

	// ---------------------------------------------------------------- recipes

	@GameTest(template = EMPTY_STRUCTURE)
	public void recipeTakesExactlyTheFiveIngredients(GameTestHelper helper) {
		ItemStack str = potion(Potions.STRENGTH);
		ItemStack spd = potion(Potions.SWIFTNESS);
		ItemStack reg = potion(Potions.REGENERATION);
		ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		helper.assertTrue(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, reg, apple, bottle)), "the five ingredients");
		helper.assertTrue(SuperSoldierSerumRecipe.matchesItems(grid(bottle, ItemStack.EMPTY, reg, ItemStack.EMPTY, apple, str,
				ItemStack.EMPTY, spd)), "in any slots");
		helper.assertTrue(SuperSoldierSerumRecipe.matchesItems(grid(potion(Potions.STRONG_STRENGTH), potion(Potions.LONG_SWIFTNESS),
				potion(Potions.STRONG_REGENERATION), apple, bottle)), "long / strong variants count");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, potion(Potions.LEAPING), apple, bottle)),
				"Leaping instead of Regeneration is refused (no longer part of it)");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, reg, potion(Potions.LEAPING), apple, bottle)),
				"an extra Leaping potion is refused");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRENGTH),
				spd, reg, apple, bottle)), "a splash potion is refused");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, PotionContents.createItemStack(Items.LINGERING_POTION,
				Potions.REGENERATION), apple, bottle)), "a lingering potion is refused");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, reg, bottle)), "no golden apple");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, reg, apple)), "no glass bottle");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, reg, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), bottle)),
				"an enchanted golden apple is not a golden apple");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, reg, apple, bottle, bottle)), "two bottles");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, reg, apple, new ItemStack(Items.POTION))),
				"a water bottle is not an empty glass bottle");
		helper.assertFalse(SuperSoldierSerumRecipe.matchesItems(grid(str, spd, reg, apple, bottle, new ItemStack(Items.DIRT))), "an extra item");

		// the real recipe manager finds it from the JSON (projecthero:super_soldier_serum)
		var level = helper.getLevel();
		CraftingInput good = CraftingInput.of(3, 3, grid(apple, str, bottle, spd, reg));
		var hit = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, good, level);
		helper.assertTrue(hit.isPresent() && hit.get().value() instanceof SuperSoldierSerumRecipe, "the crafting table knows the recipe");
		helper.assertTrue(hit.get().value().assemble(good, level.registryAccess()).is(SuperSoldierItems.UNREFINED_SERUM), "it makes the unrefined serum");
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid(str, spd, potion(Potions.LEAPING))),
				level).map(h -> !(h.value() instanceof SuperSoldierSerumRecipe)).orElse(true), "the old potion trio no longer makes it");
		helper.assertFalse(hit.get().value().canCraftInDimensions(2, 2), "five items: the 2x2 inventory grid is too small");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aFurnaceNotABlastFurnaceRefinesItInTenMinutes(GameTestHelper helper) {
		var level = helper.getLevel();
		SingleRecipeInput in = new SingleRecipeInput(new ItemStack(SuperSoldierItems.UNREFINED_SERUM));
		var smelt = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, in, level);
		helper.assertTrue(smelt.isPresent(), "the furnace refines it");
		helper.assertTrue(smelt.get().value().getResultItem(level.registryAccess()).is(SuperSoldierItems.REFINED_SERUM), "into the Super Soldier Serum");
		helper.assertTrue(smelt.get().value().getCookingTime() == 12000, "in 10 minutes (12000 ticks), got " + smelt.get().value().getCookingTime());
		helper.assertFalse(level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, in, level).isPresent(), "no blast furnace recipe any more");
		helper.succeed();
	}

	// ---------------------------------------------------------------- drinking

	@GameTest(template = EMPTY_STRUCTURE)
	public void unrefinedIsTenPercentTransformationNinetyPercentDeath(GameTestHelper helper) {
		ServerPlayer lucky = player(helper);
		helper.assertTrue(SuperSoldierSerum.take(lucky, false, 0.05f) == SuperSoldierSerum.Outcome.TRANSFORMING, "a roll under 10% takes");
		helper.assertTrue(SerumTransformation.active(lucky) && SerumTransformation.elapsed(lucky) == 0, "and starts the transformation");
		helper.assertFalse(SuperSoldier.hasPower(lucky), "the power is not there yet");
		helper.assertTrue(lucky.isAlive() && sick(lucky), "he lives, sick, weak and slow");
		helper.assertTrue(SuperSoldierSerum.take(lucky, false, 0.05f) == SuperSoldierSerum.Outcome.BUSY, "a second serum does nothing");

		ServerPlayer unlucky = player(helper);
		helper.assertTrue(SuperSoldierSerum.take(unlucky, false, 0.10f) == SuperSoldierSerum.Outcome.REJECTED, "10% and over is rejected");
		helper.assertTrue(unlucky.isDeadOrDying(), "the rejection kills");
		helper.assertFalse(SerumTransformation.active(unlucky) || SuperSoldier.hasPower(unlucky), "no transformation, no power");

		ServerPlayer refined = player(helper);
		helper.assertTrue(SuperSoldierSerum.take(refined, true, 0.99f) == SuperSoldierSerum.Outcome.TRANSFORMING, "the refined serum always takes");
		helper.assertTrue(SerumTransformation.active(refined) && !SuperSoldier.hasPower(refined), "into the same transformation");
		SerumTransformation.cancel(lucky);
		SerumTransformation.cancel(refined);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTransformationLastsSixtySecondsThenGrants(GameTestHelper helper) {
		helper.assertTrue(SerumTransformation.DURATION == 1200, "one minute");
		ServerPlayer p = player(helper);
		helper.assertTrue(SerumTransformation.start(p), "started");
		helper.assertFalse(SerumTransformation.start(p), "never two at once");
		advance(helper, p, SerumTransformation.DURATION / 2);
		helper.assertTrue(Math.abs(SerumTransformation.progress(p) - 0.5f) < 1.0e-4f, "half way, got " + SerumTransformation.progress(p));
		helper.assertTrue(sick(p) && !SuperSoldier.hasPower(p), "still changing at 30 s (no passives yet)");
		advance(helper, p, SerumTransformation.DURATION / 2 - 1);
		helper.assertTrue(SerumTransformation.elapsed(p) == SerumTransformation.DURATION - 1, "one tick to go, got " + SerumTransformation.elapsed(p));
		helper.assertTrue(SerumTransformation.active(p) && !SuperSoldier.hasPower(p), "no power one tick before the minute");
		SerumTransformation.tick(p);
		helper.assertTrue(SuperSoldier.hasPower(p), "the minute is up: a Super Soldier");
		helper.assertFalse(SerumTransformation.active(p), "the transformation (and its bar) is over");
		helper.assertFalse(anySick(p), "Nausea, Weakness and Slowness are gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void healthNeverDropsBelowHalfAHeartAndMilkDoesNotSkipIt(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		// a fresh (mock) player is spawn-invulnerable for 60 ticks
		helper.runAfterDelay(70, () -> {
			p.setHealth(3.5f);
			helper.assertTrue(SerumTransformation.start(p), "started");
			float min = p.getHealth();
			float last = p.getHealth();
			int stings = 0;
			for (int i = 0; i < SerumTransformation.DURATION - 1; i++) {
				p.invulnerableTime = 0; // the mock is not ticked inside this loop
				if (i == 300) {
					p.removeAllEffects(); // a bucket of milk
					helper.assertFalse(anySick(p), "milk cleared them");
				}
				SerumTransformation.tick(p);
				if (i == 300) {
					helper.assertTrue(sick(p), "the next tick puts them back");
				}
				float h = p.getHealth();
				helper.assertTrue(last - h <= SerumTransformation.STING + 1.0e-3f, "never more than half a heart a sting, " + last + " -> " + h);
				if (h < last) {
					stings++;
				}
				min = Math.min(min, h);
				last = h;
				helper.assertTrue(p.isAlive(), "alive at tick " + i);
			}
			helper.assertTrue(min >= SerumTransformation.FLOOR - 1.0e-3f, "never below half a heart, got " + min);
			helper.assertTrue(Math.abs(min - SerumTransformation.FLOOR) < 1.0e-3f, "it does wear him down to half a heart, got " + min);
			helper.assertTrue(stings == 3, "3.5 -> 2.5 -> 1.5 -> 1.0 and no further, got " + stings + " stings");
			helper.assertTrue(sick(p) && !SuperSoldier.hasPower(p), "still changing");
			// even a big hit of the transformation's own damage cannot kill him
			p.invulnerableTime = 0;
			p.hurt(SerumTransformation.source(helper.getLevel()), 50.0f);
			helper.assertTrue(p.isAlive() && p.getHealth() >= SerumTransformation.FLOOR - 1.0e-3f, "its damage never kills, health " + p.getHealth());
			SerumTransformation.tick(p);
			helper.assertTrue(SuperSoldier.hasPower(p), "and at the minute the power comes");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dyingMidTransformationGrantsNothing(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		SerumTransformation.start(p);
		advance(helper, p, 600);
		p.kill();
		helper.assertTrue(p.isDeadOrDying(), "dead");
		helper.assertFalse(SerumTransformation.active(p), "death ends the transformation");
		for (int i = 0; i < SerumTransformation.DURATION; i++) {
			SerumTransformation.tick(p);
		}
		helper.assertFalse(SuperSoldier.hasPower(p), "and nothing is granted");
		helper.assertFalse(SerumTransformation.TICKS.copyOnDeath(), "the transformation is not carried through a respawn");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTransformationSurvivesARelog(GameTestHelper helper) {
		helper.assertTrue(SerumTransformation.TICKS.isPersistent(), "the transformation attachment is saved");
		ServerPlayer p = player(helper);
		SerumTransformation.start(p);
		advance(helper, p, 400);
		CompoundTag tag = p.saveWithoutId(new CompoundTag());
		SerumTransformation.cancel(p);
		ServerPlayer back = player(helper);
		back.load(tag);
		helper.assertTrue(SerumTransformation.elapsed(back) == 400, "a save / load picks up at 20 s, got " + SerumTransformation.elapsed(back));
		helper.assertFalse(SuperSoldier.hasPower(back), "no power yet");
		advance(helper, back, SerumTransformation.DURATION - 400 - 1);
		helper.assertFalse(SuperSoldier.hasPower(back), "only the remaining 40 s are left -- not yet");
		SerumTransformation.tick(back);
		helper.assertTrue(SuperSoldier.hasPower(back), "the minute completes after the relog");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aSecondSerumDuringItIsNotConsumed(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		SerumTransformation.start(p);
		advance(helper, p, 100);
		for (var item : new net.minecraft.world.item.Item[] { SuperSoldierItems.REFINED_SERUM, SuperSoldierItems.UNREFINED_SERUM }) {
			ItemStack serum = new ItemStack(item, 2);
			p.setItemInHand(InteractionHand.MAIN_HAND, serum);
			InteractionResultHolder<ItemStack> use = serum.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
			helper.assertFalse(use.getResult().consumesAction(), item + ": drinking is refused during the transformation");
			helper.assertFalse(p.isUsingItem(), item + ": he does not start drinking");
			ItemStack after = serum.finishUsingItem(helper.getLevel(), p);
			helper.assertTrue(after == serum && serum.getCount() == 2 && serum.is(item), item + ": the serum is kept, got " + after);
			helper.assertTrue(p.isAlive(), item + ": no roll, no rejection");
		}
		helper.assertTrue(SerumTransformation.elapsed(p) == 100, "the running transformation is untouched");
		SerumTransformation.cancel(p);
		helper.assertFalse(SerumTransformation.active(p) || anySick(p), "cancel clears it");
		helper.succeed();
	}
}
