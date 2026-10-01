package com.projecthero.mod.supersoldier;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.supersoldier.entity.SuperSoldierEntities;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;
import com.projecthero.mod.supersoldier.recipe.SuperSoldierSerumRecipe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * One call from {@code ProjectHeroMod#onInitialize} registers the whole Super Soldier power (v0.14.8): the two serums
 * and the shield model item (v0.14.9: plus the Adamantium Shield and the Captain America suit), the potion-trio crafting
 * recipe's serializer, the thrown shield entity and the damage rules.
 */
public final class SuperSoldierSetup {
	private SuperSoldierSetup() {
	}

	public static void initialize() {
		SuperSoldierItems.initialize();
		SuperSoldierSerumRecipe.SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
				ProjectHeroMod.id("super_soldier_serum"), new SimpleCraftingRecipeSerializer<>(SuperSoldierSerumRecipe::new));
		SuperSoldierEntities.initialize();
		SuperSoldierDamage.initialize();
	}
}
