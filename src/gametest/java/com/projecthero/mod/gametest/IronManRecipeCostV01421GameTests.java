package com.projecthero.mod.gametest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.item.IronManItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * v0.14.21 Iron Man recipe cost pass: every Fabricator armour recipe exists, every component's
 * crafting-table recipe mirrors its Fabricator recipe, and the raw cost of a whole suit rises with the
 * mark (Mark 1 &lt; Mark 2 &lt; III &lt; 4 &lt; V &lt; 6 &lt; VII) while staying well under the old prices.
 */
public class IronManRecipeCostV01421GameTests implements FabricGameTest {

	private static final ArmorItem.Type[] PIECES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
			ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	/** Table-only basic components, expanded through their crafting-table recipe. */
	private static final String[] TABLE_COMPONENTS = { "copper_wiring", "metal_plating", "basic_circuit", "mechanical_parts" };

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyIronManArmourPieceHasAFabricatorRecipe(GameTestHelper helper) {
		for (String suit : FabricatorRecipes.ARMOR_BUILD_ORDER) {
			for (ArmorItem.Type type : PIECES) {
				String id = "iron_man_" + suit + "_" + type.getName();
				FabricationRecipe recipe = FabricatorRecipes.byId(id);
				helper.assertTrue(recipe != null, "missing Fabricator recipe " + id);
				helper.assertTrue(recipe.result().is(IronManItems.armor(suit, type)), id + " makes the right piece");
				helper.assertTrue(recipe.energyCost() == FabricatorRecipes.MAX_ENERGY, id + " costs the full buffer");
				helper.assertTrue(recipe.inputs().size() <= 9, id + " fits the 9 input slots");
				Set<Item> seen = new HashSet<>();
				for (FabricationRecipe.Input input : recipe.inputs()) {
					helper.assertTrue(seen.add(input.item()), id + " lists " + input.item() + " twice");
					helper.assertTrue(input.count() > 0 && input.count() <= 64, id + " input count in range");
				}
			}
		}
		for (ArmorItem.Type type : PIECES) {
			helper.assertTrue(helper.getLevel().getRecipeManager()
					.byKey(ProjectHeroMod.id("iron_man_mark_1_" + type.getName())).isPresent(),
					"the Mark 1 " + type.getName() + " has a crafting-table recipe");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void componentTableRecipesMirrorTheFabricator(GameTestHelper helper) {
		for (FabricationRecipe fab : FabricatorRecipes.all()) {
			if (fab.result().getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				continue;
			}
			Recipe<?> table = tableRecipe(helper, fab.id());
			helper.assertTrue(table != null, "component " + fab.id() + " has a crafting-table recipe");
			ItemStack out = table.getResultItem(helper.getLevel().registryAccess());
			helper.assertTrue(out.is(fab.result().getItem()) && out.getCount() == fab.result().getCount(),
					fab.id() + ": table yield " + out.getCount() + " vs Fabricator " + fab.result().getCount());
			Map<Item, Integer> tableIn = ingredientCounts(table);
			Map<Item, Integer> fabIn = new HashMap<>();
			for (FabricationRecipe.Input input : fab.inputs()) {
				fabIn.merge(input.item(), input.count(), Integer::sum);
			}
			helper.assertTrue(tableIn.equals(fabIn), fab.id() + ": table inputs " + tableIn + " vs Fabricator " + fabIn);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitRawCostRisesWithTheMarkAndStaysCheap(GameTestHelper helper) {
		Map<Item, Recipe<?>> table = new HashMap<>();
		for (String id : TABLE_COMPONENTS) {
			Recipe<?> r = tableRecipe(helper, id);
			helper.assertTrue(r != null, "basic component " + id + " has a table recipe");
			table.put(r.getResultItem(helper.getLevel().registryAccess()).getItem(), r);
		}

		double previous = 0;
		String previousSuit = "nothing";
		double markOne = 0;
		for (ArmorItem.Type type : PIECES) {
			Recipe<?> r = tableRecipe(helper, "iron_man_mark_1_" + type.getName());
			for (Map.Entry<Item, Integer> e : ingredientCounts(r).entrySet()) {
				markOne += weighted(helper, table, e.getKey(), e.getValue(), 0);
			}
		}
		// v0.15.11: + the four iron armour pieces (24 iron) the Mark 1 is now built around
		helper.assertTrue(markOne > 24 && markOne < 40, "Mark 1 raw cost " + markOne + " (plating ~12 + 24 iron of armour)");
		previous = markOne;
		previousSuit = "mark_1";

		for (String suit : FabricatorRecipes.ARMOR_BUILD_ORDER) {
			double cost = 0;
			for (ArmorItem.Type type : PIECES) {
				for (FabricationRecipe.Input input : FabricatorRecipes.byId("iron_man_" + suit + "_" + type.getName()).inputs()) {
					cost += weighted(helper, table, input.item(), input.count(), 0);
				}
			}
			helper.assertTrue(cost >= previous, suit + " (" + cost + ") must not be cheaper than " + previousSuit + " (" + previous + ")");
			// The pre-v0.14.21 Mark III-VII cost ~258-268 in these units; the pass targets roughly half.
			// v0.15.9: the Mark 8 is deliberately the priciest suit (user decision) -- capped a little higher
			double cap = "mark_8".equals(suit) ? 185 : 160;
			helper.assertTrue(cost < cap, suit + " raw cost " + cost + " should be about half the old ~260");
			previous = cost;
			previousSuit = suit;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------------------------------------

	/** Rough value of one unit of a raw material, so a whole suit's cost collapses to one number. */
	private static double rawWeight(Item item) {
		if (item == Items.IRON_INGOT) return 1;
		if (item == Items.COPPER_INGOT) return 0.5;
		if (item == Items.REDSTONE) return 0.25;
		if (item == Items.REDSTONE_BLOCK) return 9 * 0.25;
		if (item == Items.GOLD_INGOT) return 2;
		if (item == Items.QUARTZ) return 0.5;
		if (item == Items.DIAMOND) return 8;
		if (item == Items.DIAMOND_BLOCK) return 72;
		if (item == Items.BLAZE_POWDER) return 2;
		if (item == Items.AMETHYST_SHARD) return 1;
		if (item == Items.ENDER_EYE) return 4;
		if (item == Items.GLOWSTONE) return 1.5;
		if (item == Items.GUNPOWDER) return 0.5;
		if (item == Items.NETHERITE_INGOT) return 40;
		// v0.15.11: each Mark 1 piece is built around the matching vanilla iron armour piece (its ingot count)
		if (item == Items.IRON_HELMET) return 5;
		if (item == Items.IRON_CHESTPLATE) return 8;
		if (item == Items.IRON_LEGGINGS) return 7;
		if (item == Items.IRON_BOOTS) return 4;
		return 1;
	}

	private static double weighted(GameTestHelper helper, Map<Item, Recipe<?>> table, Item item, double count, int depth) {
		helper.assertTrue(depth < 16, "component recipe loop at " + item);
		Recipe<?> tableRecipe = table.get(item);
		if (tableRecipe != null) {
			double yield = tableRecipe.getResultItem(helper.getLevel().registryAccess()).getCount();
			double sum = 0;
			for (Map.Entry<Item, Integer> e : ingredientCounts(tableRecipe).entrySet()) {
				sum += weighted(helper, table, e.getKey(), count * e.getValue() / yield, depth + 1);
			}
			return sum;
		}
		FabricationRecipe fab = FabricatorRecipes.byId(BuiltInRegistries.ITEM.getKey(item).getPath());
		if (fab != null && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ProjectHeroMod.MOD_ID)) {
			double yield = fab.result().getCount();
			double sum = 0;
			for (FabricationRecipe.Input input : fab.inputs()) {
				sum += weighted(helper, table, input.item(), count * input.count() / yield, depth + 1);
			}
			return sum;
		}
		return count * rawWeight(item);
	}

	private static Recipe<?> tableRecipe(GameTestHelper helper, String path) {
		return helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(path))
				.<Recipe<?>>map(h -> h.value()).orElse(null);
	}

	private static Map<Item, Integer> ingredientCounts(Recipe<?> recipe) {
		Map<Item, Integer> counts = new HashMap<>();
		for (Ingredient ingredient : recipe.getIngredients()) {
			ItemStack[] options = ingredient.getItems();
			if (options.length > 0) {
				counts.merge(options[0].getItem(), 1, Integer::sum);
			}
		}
		return counts;
	}
}
