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
		lootInfo(registration);
		// v0.17.5 (user): the Target Dummy
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.targetdummy.TargetDummyMod.ITEM),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.devices.target_dummy.body"),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.devices.target_dummy.stats"),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.devices.target_dummy.controls"));
		powerArmourInfo(registration);
	}

	/** v0.17.5: JEI info pages for Power Armour -- the frames, platings, board, cores, charger and every module. */
	private static void powerArmourInfo(IRecipeRegistration registration) {
		for (com.projecthero.mod.powerarmour.ArmourTier t : com.projecthero.mod.powerarmour.ArmourTier.values()) {
			registration.addItemStackInfo(new ItemStack(com.projecthero.mod.powerarmour.PowerArmourMod.frame(t)),
					net.minecraft.network.chat.Component.translatable("item.projecthero.power_armour_frame.tier_hint." + t.id()),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.power_armour"),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.power_armour_upgrade"));
			registration.addItemStackInfo(new ItemStack(com.projecthero.mod.powerarmour.PowerArmourMod.plating(t)),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.power_armour.getting_text"),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.power_armour_upgrade"));
		}
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.powerarmour.PowerArmourMod.CIRCUIT_BOARD),
				net.minecraft.network.chat.Component.translatable("item.projecthero.redstone_circuit_board.hint"),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.power_armour_upgrade"));
		for (com.projecthero.mod.powerarmour.CoreType c : com.projecthero.mod.powerarmour.CoreType.values()) {
			registration.addItemStackInfo(new ItemStack(com.projecthero.mod.powerarmour.PowerArmourMod.core(c)),
					net.minecraft.network.chat.Component.translatable("item.projecthero." + c.id() + ".desc"),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.power_armour.cores_text"));
		}
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.powerarmour.PowerArmourMod.CHARGER_ITEM),
				net.minecraft.network.chat.Component.translatable("item.projecthero.core_charger.hint"));
		for (com.projecthero.mod.powerarmour.ModuleType m : com.projecthero.mod.powerarmour.ModuleType.values()) {
			registration.addItemStackInfo(new ItemStack(com.projecthero.mod.powerarmour.PowerArmourMod.module(m)),
					net.minecraft.network.chat.Component.translatable(m.translationKey() + ".desc"),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.power_armour.modules_text"));
		}
	}

	/** v0.17.5: JEI info pages for the loot framework's items (where they come from, what they do). */
	private static void lootInfo(IRecipeRegistration registration) {
		for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[] { com.projecthero.mod.loot.LootItems.SALVAGED_TECH,
				com.projecthero.mod.loot.LootItems.HERO_ALLOY_SCRAP, com.projecthero.mod.loot.LootItems.HERO_ALLOY_INGOT,
				com.projecthero.mod.loot.LootItems.VIBRANIUM_SHARD, com.projecthero.mod.loot.LootItems.CELESTIAL_CORE,
				com.projecthero.mod.loot.LootItems.ENERGY_CELL, com.projecthero.mod.loot.LootItems.COOLDOWN_STIM,
				com.projecthero.mod.loot.LootItems.SUIT_REPAIR_KIT, com.projecthero.mod.loot.LootItems.MED_KIT }) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.loot_found"));
		}
		java.util.List<ItemStack> vaults = new java.util.ArrayList<>();
		for (int t = 1; t <= 4; t++) {
			vaults.add(com.projecthero.mod.loot.LootItems.vaultStack(t));
		}
		registration.addItemStackInfo(vaults, net.minecraft.network.chat.Component.translatable("projecthero.guide.loot.vaults.body"),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.finds_where")); // v0.17.5 Tier 1 Finds
		java.util.List<ItemStack> locators = new java.util.ArrayList<>();
		for (int t = 2; t <= 4; t++) {
			locators.add(com.projecthero.mod.loot.LocatorItem.forTier(t));
		}
		registration.addItemStackInfo(locators, net.minecraft.network.chat.Component.translatable("projecthero.guide.loot.locators.body"));
		// v0.17.5: gear tiers I-IV -- each item's own hint plus where gear comes from
		for (net.minecraft.world.item.Item i : com.projecthero.mod.gear.GearItems.all()) {
			String hint = i instanceof com.projecthero.mod.gear.GearArmorItem g ? "projecthero.gear.set." + g.set() + ".hint" : i.getDescriptionId() + ".hint";
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(hint),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.gear"));
		}
		// v0.17.5: the villain-hideout artifacts and the core
		for (com.projecthero.mod.hideout.HideoutFaction f : com.projecthero.mod.hideout.HideoutFaction.values()) {
			net.minecraft.world.item.Item artifact = switch (f) {
				case KINGPIN -> com.projecthero.mod.hideout.item.HideoutItems.GILDED_CANE;
				case ULTRON -> com.projecthero.mod.hideout.item.HideoutItems.CORTEX_SHARD;
				case APOKOLIPS -> com.projecthero.mod.hideout.item.HideoutItems.MOTHER_BOX_FRAGMENT;
				case SYMBIOTE -> com.projecthero.mod.hideout.item.HideoutItems.HIVE_HEART;
				case FROST -> com.projecthero.mod.hideout.item.HideoutItems.FROSTBRAND;
			};
			registration.addItemStackInfo(new ItemStack(artifact),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.hideouts.a." + f.getSerializedName()),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.hideouts.artifacts.body"));
		}
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.hideout.block.HideoutBlocks.CORE_ITEM),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.hideouts.clearing.body"));
		// v0.17.5: The Vault (Tier 4) -- its three artifacts and its blocks
		for (net.minecraft.world.item.Item i : com.projecthero.mod.vaultdungeon.item.VaultItems.all()) {
			registration.addItemStackInfo(new ItemStack(i),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.hideouts.vault.a." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath()),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.vault_artifact"));
		}
		for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[] { com.projecthero.mod.vaultdungeon.block.VaultBlocks.TURRET_ITEM,
				com.projecthero.mod.vaultdungeon.block.VaultBlocks.ALARM_ITEM, com.projecthero.mod.vaultdungeon.block.VaultBlocks.CONSOLE_ITEM,
				com.projecthero.mod.vaultdungeon.block.VaultBlocks.TRACK_ITEM }) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"));
		}
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
		if (!com.projecthero.mod.omnitrix.OmnitrixFeature.enabled()) {
			// v0.17.1: the Omnitrix is not public yet -- its items stay out of JEI's list
			java.util.List<ItemStack> hidden = new java.util.ArrayList<>();
			for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[] { com.projecthero.mod.omnitrix.item.OmnitrixItems.OMNITRIX,
					com.projecthero.mod.omnitrix.item.OmnitrixItems.METEOR_SCANNER, com.projecthero.mod.omnitrix.item.OmnitrixItems.PLUMBER_ALLOY,
					com.projecthero.mod.omnitrix.item.OmnitrixItems.CONTAINMENT_CORE, com.projecthero.mod.omnitrix.pod.OmnitrixPodBlocks.POD_ITEM,
					com.projecthero.mod.omnitrix.item.OmnitrixItems.RECALIBRATION_CHIP, com.projecthero.mod.omnitrix.item.OmnitrixItems.RECALIBRATED_OMNITRIX }) {
				hidden.add(new ItemStack(i));
			}
			jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, hidden);
		}
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
	}
}
