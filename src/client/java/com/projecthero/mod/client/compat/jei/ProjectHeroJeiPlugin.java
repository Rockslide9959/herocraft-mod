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
		superSoldierSerum(registration);
		// v0.17.7: Thor acquisition -- the Star Forge, the Living Branch, the Stormbreaker Head, the Destroyer's egg
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.thoracquire.ThorAcquire.STAR_FORGE_ITEM),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.star_forge"));
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.thoracquire.ThorAcquire.LIVING_BRANCH),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.living_branch"));
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.item.ModItems.UNFORGED_STORMBREAKER),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.stormbreaker_head"));
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.thoracquire.ThorAcquire.DESTROYER_SPAWN_EGG),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.destroyer"));
		// v0.17.7: Shazam acquisition -- the Wizard Shazam egg (creative / admin)
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.shazam.journey.ShazamJourney.WIZARD_SPAWN_EGG),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.wizard_shazam"));
		// v0.17.5: the Omnitrix Data Fragment (Hero Vault loot; reveals a Master Control digit)
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.omnitrix.item.OmnitrixItems.DATA_FRAGMENT),
				net.minecraft.network.chat.Component.translatable("item.projecthero.omnitrix_data_fragment.use"),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.loot_found"));
		// v0.17.5 (user): the Target Dummy
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.targetdummy.TargetDummyMod.ITEM),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.devices.target_dummy.body"),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.devices.target_dummy.stats"),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.devices.target_dummy.controls"));
		powerArmourInfo(registration);
		// v0.17.5 (user): backpacks -- each tier's upgrade step plus how they work
		for (net.minecraft.world.item.Item i : com.projecthero.mod.backpack.BackpackMod.all()) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable("projecthero.jei.backpack"),
					net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"));
		}
		// v0.17.5: the Falcon gear is a Secondary power (a suit you wear on top of your Primary): one info page for the four pieces
		registration.addItemStackInfo(java.util.List.of(new ItemStack(com.projecthero.mod.falcon.item.FalconItems.GOGGLES),
				new ItemStack(com.projecthero.mod.falcon.item.FalconItems.WING_PACK), new ItemStack(com.projecthero.mod.falcon.item.FalconItems.LEGGINGS),
				new ItemStack(com.projecthero.mod.falcon.item.FalconItems.BOOTS)),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.falcon_gear"),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.falcon.stacking.body"));
	}

	/**
	 * v0.17.7: the Unrefined Super Soldier Serum is a special recipe (the potions are told apart by a component), which JEI's crafting
	 * category skips like vanilla's special recipes -- so a display-only shapeless copy of it (every Strength / Swiftness / Regeneration
	 * potion it takes cycling in its slot, the Golden Apple, the Glass Bottle) goes into the crafting category, plus an info page on
	 * both serums. The Furnace refinement is a plain smelting recipe and shows on its own.
	 */
	private static void superSoldierSerum(IRecipeRegistration registration) {
		net.minecraft.core.NonNullList<net.minecraft.world.item.crafting.Ingredient> in = net.minecraft.core.NonNullList.create();
		for (java.util.List<ItemStack> slot : com.projecthero.mod.supersoldier.recipe.SuperSoldierSerumRecipe.displayIngredients()) {
			in.add(net.minecraft.world.item.crafting.Ingredient.of(slot.stream()));
		}
		net.minecraft.world.item.crafting.ShapelessRecipe shown = new net.minecraft.world.item.crafting.ShapelessRecipe("",
				net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
				new ItemStack(com.projecthero.mod.supersoldier.item.SuperSoldierItems.UNREFINED_SERUM), in);
		registration.addRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, java.util.List.of(
				new net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>(
						ProjectHeroMod.id("jei/unrefined_super_soldier_serum"), shown)));
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.supersoldier.item.SuperSoldierItems.UNREFINED_SERUM),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.unrefined_super_soldier_serum"));
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.supersoldier.item.SuperSoldierItems.REFINED_SERUM),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.refined_super_soldier_serum"));
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
		// v0.17.5: the Symbiote host's suit pieces -- how to recolour them
		registration.addItemStackInfo(java.util.List.of(new ItemStack(com.projecthero.mod.symbiote.item.SymbioteHostItems.HELMET),
				new ItemStack(com.projecthero.mod.symbiote.item.SymbioteHostItems.CHESTPLATE),
				new ItemStack(com.projecthero.mod.symbiote.item.SymbioteHostItems.LEGGINGS),
				new ItemStack(com.projecthero.mod.symbiote.item.SymbioteHostItems.BOOTS)),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.symbiote_host_suit"),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.symbiote_host_moves")); // v0.17.5: moveset v2
		// v0.17.7: the crafted Black Panther Necklace -- the whole suit (its recipe shows in the crafting category on its own)
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.blackpanther.item.BlackPantherItems.NECKLACE),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.black_panther_necklace"),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.black_panther_necklace2"));
		// v0.17.5: the villain-hideout artifacts and the core
		for (com.projecthero.mod.hideout.HideoutFaction f : com.projecthero.mod.hideout.HideoutFaction.values()) {
			net.minecraft.world.item.Item artifact = switch (f) {
				case KINGPIN -> com.projecthero.mod.hideout.item.HideoutItems.GILDED_CANE;
				case ULTRON -> com.projecthero.mod.hideout.item.HideoutItems.CORTEX_SHARD;
				case APOKOLIPS -> com.projecthero.mod.hideout.item.HideoutItems.MOTHER_BOX_FRAGMENT;
				case SYMBIOTE -> com.projecthero.mod.hideout.item.HideoutItems.HIVE_HEART;
				case FROST -> com.projecthero.mod.hideout.item.HideoutItems.FROSTBRAND;
				case GRAVE -> com.projecthero.mod.hideout.item.HideoutItems.GRAVECALLER_BELL;
				case CARNAGE -> com.projecthero.mod.hideout.item.HideoutItems.BROOD_FANG;
				case OATH -> com.projecthero.mod.hideout.item.HideoutItems.OATH_SIGIL;
			};
			registration.addItemStackInfo(new ItemStack(artifact),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.hideouts.a." + f.getSerializedName()),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.hideouts.artifacts.body"));
		}
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.hideout.block.HideoutBlocks.CORE_ITEM),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.hideouts.clearing.body"));
		// v0.17.7: the new hideout blocks (ballista, Carnage's biomass and egg walls)
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.hideout.block.HideoutBlocks.BALLISTA_ITEM),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.oath_ballista"));
		registration.addItemStackInfo(java.util.List.of(new ItemStack(com.projecthero.mod.hideout.block.HideoutBlocks.CRIMSON_BIOMASS_ITEM),
				new ItemStack(com.projecthero.mod.hideout.block.HideoutBlocks.CRIMSON_EGG_ITEM)),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.crimson_biomass"));
		// v0.17.7: the alarm & stealth blocks and the Keycard
		for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[] { com.projecthero.mod.alarm.block.AlarmBlocks.PANEL_ITEM,
				com.projecthero.mod.alarm.block.AlarmBlocks.LAMP_ITEM, com.projecthero.mod.alarm.block.AlarmBlocks.BLAST_DOOR_ITEM,
				com.projecthero.mod.alarm.block.AlarmBlocks.CAMERA_ITEM, com.projecthero.mod.alarm.block.AlarmBlocks.LASER_ITEM,
				com.projecthero.mod.alarm.block.AlarmBlocks.SPAWN_POINT_ITEM, com.projecthero.mod.alarm.AlarmKeycards.KEYCARD }) {
			registration.addItemStackInfo(new ItemStack(i),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.alarm." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath()),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.alarm.system"));
		}
		// v0.17.5: The Vault (Tier 4) -- its three artifacts and its blocks
		for (net.minecraft.world.item.Item i : com.projecthero.mod.vaultdungeon.item.VaultItems.all()) {
			registration.addItemStackInfo(new ItemStack(i),
					net.minecraft.network.chat.Component.translatable("projecthero.guide.hideouts.vault.a." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath()),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.vault_artifact"));
		}
		for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[] { com.projecthero.mod.vaultdungeon.block.VaultBlocks.TURRET_ITEM,
				com.projecthero.mod.vaultdungeon.block.VaultBlocks.ALARM_ITEM, com.projecthero.mod.vaultdungeon.block.VaultBlocks.CONSOLE_ITEM,
				com.projecthero.mod.vaultdungeon.block.VaultBlocks.TRACK_ITEM,
				// v0.17.7: the security field, the cell-block breaker, the Tier 2 sites' ward stone
				com.projecthero.mod.vaultdungeon.block.VaultBlocks.BARRIER_ITEM, com.projecthero.mod.vaultdungeon.block.VaultBlocks.BREAKER_ITEM,
				com.projecthero.mod.loot.sites.SiteUpgrade.WARD_ITEM }) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"));
		}
		// v0.17.7: the Sky Fortress -- the pass, the parachute, Kestrel's Thruster and its blocks
		for (net.minecraft.world.item.Item i : com.projecthero.mod.skyfortress.item.SkyFortressItems.all()) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.sky_fortress"));
		}
		for (net.minecraft.world.item.Item i : com.projecthero.mod.skyfortress.block.SkyFortressBlocks.all()) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.sky_fortress"));
		}
		// v0.17.7: the Ancient Temple -- its blocks, the four weights and the Bound King's Ankh
		for (net.minecraft.world.item.Item i : com.projecthero.mod.temple.block.TempleBlocks.infoItems()) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"),
					net.minecraft.network.chat.Component.translatable("projecthero.jei.temple"));
		}
		for (net.minecraft.world.item.Item i : com.projecthero.mod.temple.item.TempleItems.weights()) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable("item.projecthero.temple_weight.hint",
					com.projecthero.mod.temple.block.WeightAltarBlockEntity.TARGET), net.minecraft.network.chat.Component.translatable("projecthero.jei.temple"));
		}
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.temple.item.TempleItems.BOUND_KINGS_ANKH),
				net.minecraft.network.chat.Component.translatable("item.projecthero.bound_kings_ankh.hint"),
				net.minecraft.network.chat.Component.translatable("projecthero.jei.temple"));
		// v0.17.5: Sentry -- the serum (only from The Vault's O.X.E. vat) and the vat itself
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.sentry.item.SentryItems.SENTRY_SERUM),
				net.minecraft.network.chat.Component.translatable("item.projecthero.sentry_serum.hint"));
		registration.addItemStackInfo(new ItemStack(com.projecthero.mod.sentry.block.SentryBlocks.SERUM_VAT_ITEM),
				net.minecraft.network.chat.Component.translatable("block.projecthero.oxe_serum_vat.hint"));
		// v0.17.5 rework: the craftable Sentry Suit (self-repairing; slows the Void, speeds the Sun)
		for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[] { com.projecthero.mod.sentry.item.SentrySuitItem.TOP,
				com.projecthero.mod.sentry.item.SentrySuitItem.TROUSERS }) {
			registration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable("item.projecthero.sentry_suit.hint"));
		}
		// v0.17.5: the Captain America Battle Suit (the top's shield slot: N, right-click to block, C throws from it)
		registration.addItemStackInfo(java.util.List.of(new ItemStack(com.projecthero.mod.supersoldier.item.CapBattleSuitItem.COWL),
				new ItemStack(com.projecthero.mod.supersoldier.item.CapBattleSuitItem.TOP),
				new ItemStack(com.projecthero.mod.supersoldier.item.CapBattleSuitItem.TROUSERS),
				new ItemStack(com.projecthero.mod.supersoldier.item.CapBattleSuitItem.BOOTS)),
				net.minecraft.network.chat.Component.translatable("item.projecthero.cap_battle_suit.hint"),
				net.minecraft.network.chat.Component.translatable("projecthero.guide.super_soldier.battle_suit.body"));
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
		// v0.17.7: the Black Panther's legacy suit items (the four armour pieces of the first v0.17.7 build, the Panther Habit) have
		// no recipe and turn into the necklace -- they stay out of JEI's list
		java.util.List<ItemStack> legacy = new java.util.ArrayList<>();
		for (net.minecraft.world.item.Item i : com.projecthero.mod.blackpanther.item.BlackPantherItems.legacyItems()) {
			legacy.add(new ItemStack(i));
		}
		jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, legacy);
		if (!com.projecthero.mod.omnitrix.OmnitrixFeature.enabled()) {
			// v0.17.1: the Omnitrix is not public yet -- its items stay out of JEI's list
			java.util.List<ItemStack> hidden = new java.util.ArrayList<>();
			for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[] { com.projecthero.mod.omnitrix.item.OmnitrixItems.OMNITRIX,
					com.projecthero.mod.omnitrix.item.OmnitrixItems.METEOR_SCANNER, com.projecthero.mod.omnitrix.item.OmnitrixItems.PLUMBER_ALLOY,
					com.projecthero.mod.omnitrix.item.OmnitrixItems.CONTAINMENT_CORE, com.projecthero.mod.omnitrix.pod.OmnitrixPodBlocks.POD_ITEM,
					com.projecthero.mod.omnitrix.item.OmnitrixItems.RECALIBRATION_CHIP, com.projecthero.mod.omnitrix.item.OmnitrixItems.RECALIBRATED_OMNITRIX,
					com.projecthero.mod.omnitrix.item.OmnitrixItems.DATA_FRAGMENT }) {
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
