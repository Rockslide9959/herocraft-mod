package com.projecthero.mod.client.compat.jei;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.1 (user: "Gilded plating recipe doesnt show up on jei ... make sure all the crafting recipe materials do show up"):
 * JEI only knows vanilla recipe types, and every Stark Fabricator recipe (the Iron Man components -- Gilded Plating, the
 * plates, circuits, reactors, blueprints -- and the suit pieces) lives in code ({@link FabricatorRecipes}), so none of
 * them showed. This registers them as their own "Stark Fabricator" recipe category, with the Fabricator as the catalyst.
 * Loaded through the {@code jei_mod_plugin} entrypoint, so only when JEI is installed (the API is compile-only).
 */
public final class ProjectHeroJeiPlugin implements IModPlugin {
	public static final RecipeType<FabricationRecipe> FABRICATOR = RecipeType.create(ProjectHeroMod.MOD_ID, "stark_fabricator",
			FabricationRecipe.class);

	@Override
	public ResourceLocation getPluginUid() {
		return ProjectHeroMod.id("jei_plugin");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new StarkFabricatorCategory(registration.getJeiHelpers().getGuiHelper()));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addRecipes(FABRICATOR, FabricatorRecipes.all());
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(new ItemStack(IronManBlocks.STARK_FABRICATOR_ITEM), FABRICATOR);
	}

	/** JEI's runtime once it is up (null without JEI) -- for opening a recipe page from our own screens / harnesses. */
	public static mezz.jei.api.runtime.IJeiRuntime runtime;

	@Override
	public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
	}
}
