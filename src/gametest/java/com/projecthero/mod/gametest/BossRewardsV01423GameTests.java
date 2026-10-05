package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;
import com.projecthero.mod.behemoth.entity.BehemothEntityTypes;
import com.projecthero.mod.grave.item.GraveItems;
import com.projecthero.mod.grave.item.NecroticBladeItem;
import com.projecthero.mod.grave.item.OathboundBladeRecipe;
import com.projecthero.mod.titan.entity.TitanEntity;
import com.projecthero.mod.titan.entity.TitanEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** v0.14.23: the boss-reward fixes -- Titan / Behemoth XP, horde boss loot, the grave chest's book, Oathbound Necrotic Blade. */
public class BossRewardsV01423GameTests implements FabricGameTest {

	private static LootTable table(ServerLevel level, String path) {
		return level.getServer().reloadableRegistries()
				.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id(path)));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hordeBossesHaveLootTables(GameTestHelper helper) {
		for (String boss : new String[] { "entities/bone_tyrant", "entities/brood_queen" }) {
			helper.assertTrue(table(helper.getLevel(), boss) != LootTable.EMPTY, boss + " must have a loot table");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cursedGraveChestBooksAreEnchanted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable chest = table(level, "chests/cursed_grave_chest");
		int books = 0;
		for (int i = 0; i < 200; i++) {
			LootParams params = new LootParams.Builder(level)
					.withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO)).create(LootContextParamSets.CHEST);
			for (ItemStack stack : chest.getRandomItems(params)) {
				helper.assertFalse(stack.is(Items.BOOK), "the chest must never give a plain book");
				if (stack.is(Items.ENCHANTED_BOOK)) {
					books++;
					var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
					helper.assertTrue(stored != null && !stored.isEmpty(), "an enchanted book from the chest must carry enchantments");
				}
			}
		}
		helper.assertTrue(books > 0, "200 rolls should give at least one enchanted book");
		helper.succeed();
	}

	private static int orbsAround(GameTestHelper helper, Vec3 at) {
		return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(16)).stream()
				.mapToInt(ExperienceOrb::getValue).sum();
	}

	private static void killAndCheckXp(GameTestHelper helper, LivingEntity boss, String name) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2, 2, 2));
		boss.moveTo(at);
		helper.getLevel().addFreshEntity(boss);
		boss.hurt(boss.damageSources().playerAttack(player), 1.0f); // a player hurt it recently
		boss.die(boss.damageSources().playerAttack(player));
		int xp = orbsAround(helper, at);
		helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(16)).forEach(o -> o.discard());
		boss.discard();
		helper.assertTrue(xp > 0, name + " must drop its XP reward when a player kills it, got " + xp);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void titanDropsXp(GameTestHelper helper) {
		TitanEntity titan = TitanEntityTypes.TITAN.create(helper.getLevel());
		killAndCheckXp(helper, titan, "the Titan");
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void behemothDropsXp(GameTestHelper helper) {
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(helper.getLevel());
		killAndCheckXp(helper, boss, "the Abyssal Behemoth");
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brokenOathUpgradesNecroticBladeOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack blade = new ItemStack(GraveItems.NECROTIC_BLADE);
		blade.setDamageValue(37);
		SmithingRecipeInput input = new SmithingRecipeInput(new ItemStack(GraveItems.BROKEN_OATH), blade,
				new ItemStack(GraveItems.GRAVEBOUND_INGOT));
		var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level);
		helper.assertTrue(recipe.isPresent() && recipe.get().value() instanceof OathboundBladeRecipe,
				"Broken Oath + Necrotic Blade + Gravebound Ingot must be a smithing recipe");
		ItemStack out = recipe.get().value().assemble(input, level.registryAccess());
		helper.assertTrue(NecroticBladeItem.isOathbound(out), "the result must be Oathbound");
		helper.assertTrue(out.getDamageValue() == 37, "the upgrade must keep the blade's wear (and everything else on it)");
		helper.assertTrue(NecroticBladeItem.maxStacks(out) > NecroticBladeItem.maxStacks(blade), "Oathbound holds more stacks");
		helper.assertTrue(NecroticBladeItem.witherChance(out) > NecroticBladeItem.witherChance(blade), "Oathbound withers more");

		SmithingRecipeInput again = new SmithingRecipeInput(new ItemStack(GraveItems.BROKEN_OATH), out,
				new ItemStack(GraveItems.GRAVEBOUND_INGOT));
		helper.assertFalse(level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, again, level).isPresent(),
				"an Oathbound blade must not be upgradable again (it would eat a Broken Oath for nothing)");
		helper.succeed();
	}
}
