package com.projecthero.mod.hero.mutation;

import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.item.HeroPackItems;

import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;

import net.minecraft.world.item.crafting.Ingredient;

/**
 * Registers one brewing-stand recipe per power: {@code <base vanilla potion> + <power reagent> ->
 * <experimental serum>}.
 *
 * <p><b>Deviation note:</b> the design lists multiple additives and a non-standard "brewing fuel"
 * per serum. Vanilla brewing accepts a single ingredient and its fuel is always blaze powder, so the
 * extra additives + the thematic fuel are folded into the crafted <em>reagent</em> recipe instead
 * (see {@code data/projecthero/recipe/*_reagent.json}); the brewing step itself takes that reagent.
 */
public final class ModBrewing {
	private ModBrewing() {
	}

	public static void initialize() {
		FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
			for (Power power : Powers.all()) {
				builder.registerPotionRecipe(
						ModSerums.basePotion(power.serum().basePotion()),
						Ingredient.of(HeroPackItems.reagent(power)),
						ModSerums.serum(power));
			}
		});
	}
}
