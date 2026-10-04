package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.raid.DarkseidRaidRewards;
import com.projecthero.mod.event.raid.SupervillainRaid;
import com.projecthero.mod.event.raid.SupervillainRaidItems;
import com.projecthero.mod.event.raid.SupervillainRaidRewards;
import com.projecthero.mod.event.raid.ZombieRaidRewards;
import com.projecthero.mod.event.reward.Valuables;
import com.projecthero.mod.grave.item.GraveItems;
import com.projecthero.mod.horde.HordeKind;
import com.projecthero.mod.horde.HordeRewards;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.AABB;

/**
 * v0.14.21: every horde and raid reward pool pays lapis and other valuables, scaled by party size / waves, while the
 * mod-specific rewards are untouched. Rolls use seeded randoms so a failure reproduces.
 */
public class RewardValuablesV01421GameTests implements FabricGameTest {
	private static final int SEEDS = 12;

	private static int count(List<ItemStack> loot, Item item) {
		int n = 0;
		for (ItemStack s : loot) {
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Lapis plus at least one other valuable (not lapis). */
	private static void assertValuables(GameTestHelper helper, List<ItemStack> loot, String what) {
		helper.assertTrue(count(loot, Items.LAPIS_LAZULI) > 0, what + ": has lapis lazuli");
		boolean other = loot.stream().anyMatch(s -> Valuables.ALL.contains(s.getItem())
				&& !s.is(Items.LAPIS_LAZULI) && !s.is(Items.LAPIS_BLOCK));
		helper.assertTrue(other, what + ": has another valuable besides lapis");
	}

	private static List<ItemStack> rollTable(ServerLevel level, String path, long seed) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
				ResourceLocation.fromNamespaceAndPath("projecthero", path)));
		return new ArrayList<>(table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY), seed));
	}

	// ---------------------------------------------------------------- hordes

	@GameTest(template = EMPTY_STRUCTURE)
	public void hordeChestsPayLapisAndKeepTheirExtras(GameTestHelper helper) {
		RegistryAccess ra = helper.getLevel().registryAccess();
		for (HordeKind kind : HordeKind.values()) {
			for (int seed = 0; seed < SEEDS; seed++) {
				List<ItemStack> loot = HordeRewards.roll(RandomSource.create(seed), ra, kind, 1);
				assertValuables(helper, loot, kind + " seed " + seed);
				helper.assertTrue(count(loot, Items.REDSTONE) > 0 && count(loot, Items.AMETHYST_SHARD) > 0,
						kind + ": redstone and amethyst");
				helper.assertTrue(count(loot, Items.DIAMOND) > 0 && count(loot, Items.ENCHANTED_BOOK) > 0, kind + ": diamonds and a book still");
				switch (kind) {
					case SKELETON -> helper.assertTrue(count(loot, Items.BOW) == 1 && count(loot, Items.ARROW) == 64,
							"skeleton: enchanted bow and arrows still");
					case SPIDER -> {
						helper.assertTrue(count(loot, Items.ENCHANTED_GOLDEN_APPLE) >= 1, "spider: enchanted golden apple still");
						helper.assertTrue(count(loot, Items.NETHERITE_SCRAP) >= 1, "spider: netherite scrap still");
						helper.assertTrue(count(loot, Items.LAPIS_BLOCK) >= 2, "spider: lapis blocks");
					}
					default -> {
					}
				}
				helper.assertTrue(HordeRewards.roll(RandomSource.create(seed), ra, kind, 4).size() <= 27, kind + ": fits a chest with four winners");
			}
		}
		// four fighters get more: the solo maximum is below the four-fighter minimum for the spider's lapis
		int soloMax = 0;
		int partyMin = Integer.MAX_VALUE;
		for (int seed = 0; seed < SEEDS; seed++) {
			soloMax = Math.max(soloMax, count(HordeRewards.roll(RandomSource.create(seed), ra, HordeKind.SPIDER, 1), Items.LAPIS_LAZULI));
			partyMin = Math.min(partyMin, count(HordeRewards.roll(RandomSource.create(seed), ra, HordeKind.SPIDER, 4), Items.LAPIS_LAZULI));
		}
		helper.assertTrue(partyMin > soloMax, "four fighters get more lapis: " + partyMin + " vs solo max " + soloMax);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Zombie Raid

	@GameTest(template = EMPTY_STRUCTURE)
	public void zombieRaidBossesPayMoreOnLaterWaves(GameTestHelper helper) {
		RegistryAccess ra = helper.getLevel().registryAccess();
		int earlyMax = 0;
		int lateMin = Integer.MAX_VALUE;
		for (int seed = 0; seed < SEEDS; seed++) {
			List<ItemStack> early = ZombieRaidRewards.bossValuables(RandomSource.create(seed), ra, 2, 12, false);
			List<ItemStack> last = ZombieRaidRewards.bossValuables(RandomSource.create(seed), ra, 12, 12, true);
			assertValuables(helper, early, "wave-2 boss");
			assertValuables(helper, last, "final boss");
			helper.assertTrue(count(last, Items.GOLDEN_APPLE) >= 1 && count(last, Items.ENCHANTED_BOOK) == 1
					&& count(last, Items.LAPIS_BLOCK) >= 1, "the final boss adds golden apples, a book and a lapis block");
			earlyMax = Math.max(earlyMax, count(early, Items.LAPIS_LAZULI));
			lateMin = Math.min(lateMin, count(last, Items.LAPIS_LAZULI));
		}
		helper.assertTrue(lateMin > earlyMax, "later waves drop more lapis: " + lateMin + " vs " + earlyMax);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cursedGraveChestKeepsItsLootAndGainsValuables(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess ra = level.registryAccess();
		boolean special = false;
		for (int seed = 0; seed < 60; seed++) {
			// the chest as placeChest builds it: loot table, then the essence, then the valuables on top
			SimpleContainer chest = new SimpleContainer(27);
			List<ItemStack> table = rollTable(level, "chests/cursed_grave_chest", seed);
			for (int i = 0; i < table.size(); i++) {
				chest.setItem(i, table.get(i));
			}
			chest.addItem(new ItemStack(GraveItems.GRAVE_ESSENCE, 15));
			HordeRewards.fill(chest, ZombieRaidRewards.completionValuables(RandomSource.create(seed), ra, 4), RandomSource.create(seed));
			List<ItemStack> got = new ArrayList<>();
			for (int i = 0; i < chest.getContainerSize(); i++) {
				got.add(chest.getItem(i));
			}
			if (seed < SEEDS) {
				assertValuables(helper, got, "cursed grave chest seed " + seed);
				helper.assertTrue(count(got, Items.DIAMOND) >= 3, "diamonds");
			}
			helper.assertTrue(count(got, GraveItems.GRAVE_ESSENCE) == 15, "the Grave Essence bundle survives the valuables");
			special |= table.stream().anyMatch(s -> s.is(GraveItems.GRAVEBOUND_INGOT) || s.is(GraveItems.GRAVEKEEPER_SHIELD)
					|| s.is(GraveItems.NECROTIC_BLADE) || s.is(GraveItems.BOSS_TROPHY));
		}
		helper.assertTrue(special, "the loot table still rolls its Gravebound specials");
		int soloMax = 0;
		int partyMin = Integer.MAX_VALUE;
		for (int seed = 0; seed < SEEDS; seed++) {
			soloMax = Math.max(soloMax, count(ZombieRaidRewards.completionValuables(RandomSource.create(seed), ra, 1), Items.LAPIS_LAZULI));
			partyMin = Math.min(partyMin, count(ZombieRaidRewards.completionValuables(RandomSource.create(seed), ra, 4), Items.LAPIS_LAZULI));
		}
		helper.assertTrue(partyMin > soloMax, "a party of four gets more lapis: " + partyMin + " vs " + soloMax);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Supervillain Raid

	@GameTest(template = EMPTY_STRUCTURE)
	public void supervillainVictoryDropsValuablesAndItsCache(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess ra = level.registryAccess();
		for (int seed = 0; seed < SEEDS; seed++) {
			List<ItemStack> loot = SupervillainRaidRewards.victoryValuables(RandomSource.create(seed), ra, 1);
			assertValuables(helper, loot, "supervillain seed " + seed);
			helper.assertTrue(count(loot, Items.DIAMOND) >= 2 && count(loot, Items.ENCHANTED_BOOK) == 1, "diamonds and a book");
		}
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		SupervillainRaidRewards.grantVictory(level, new SupervillainRaid(UUID.randomUUID()), at);
		List<ItemStack> dropped = new ArrayList<>();
		List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3));
		for (ItemEntity e : entities) {
			dropped.add(e.getItem());
		}
		assertValuables(helper, dropped, "supervillain victory drop");
		helper.assertTrue(count(dropped, SupervillainRaidItems.VILLAIN_CACHE) >= 1, "the Villain Cache still drops");
		helper.assertTrue(count(dropped, Items.EMERALD) >= 12, "the 12-24 emeralds still drop");
		entities.forEach(ItemEntity::discard);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Apokolips Invasion

	@GameTest(template = EMPTY_STRUCTURE)
	public void darkseidPlunderScalesWithWavesAndKeepsOmegaLoot(GameTestHelper helper) {
		RegistryAccess ra = helper.getLevel().registryAccess();
		for (int seed = 0; seed < SEEDS; seed++) {
			List<ItemStack> five = DarkseidRaidRewards.valuables(RandomSource.create(seed), ra, 5, 1.0);
			assertValuables(helper, five, "darkseid seed " + seed);
			helper.assertTrue(count(five, Items.LAPIS_BLOCK) >= 2 && count(five, Items.DIAMOND) >= 6
					&& count(five, Items.ENCHANTED_BOOK) == 2, "lapis blocks, diamonds and two books");
			// same seed, same roll -- only the wave scale differs
			int one = count(DarkseidRaidRewards.valuables(RandomSource.create(seed), ra, 1, 1.0), Items.LAPIS_LAZULI);
			helper.assertTrue(count(five, Items.LAPIS_LAZULI) > one, "five waves pay more than one: "
					+ count(five, Items.LAPIS_LAZULI) + " vs " + one);
		}
		helper.assertTrue(DarkseidRaidRewards.valuables(RandomSource.create(1), ra, 5, 0.0).isEmpty(), "multiplier 0 turns it off");
		DarkseidConfig.Rewards d = new DarkseidConfig.Rewards();
		helper.assertTrue(d.omegaCoreCount == 1 && d.omegaShardChance == 0.60 && d.motherBoxChance == 0.10 && d.omegaRelicChance == 0.03
				&& d.experiencePoints == 1500 && d.valuablesMultiplier == 1.0, "the Omega loot defaults are unchanged");
		DarkseidConfig c = DarkseidConfig.migrateForTest("{\"configVersion\":3,\"rewards\":{\"omegaShardChance\":0.9}}");
		helper.assertTrue(c.configVersion == 4 && c.rewards.omegaShardChance == 0.9 && c.rewards.valuablesMultiplier == 1.0,
				"a v3 file moves to v4, keeps its tuning and gains the multiplier");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Titan (horde boss)

	@GameTest(template = EMPTY_STRUCTURE)
	public void titanDropsLapisWithItsOldLoot(GameTestHelper helper) {
		for (int seed = 0; seed < SEEDS; seed++) {
			List<ItemStack> loot = rollTable(helper.getLevel(), "entities/titan", seed);
			assertValuables(helper, loot, "titan seed " + seed);
			helper.assertTrue(count(loot, Items.EMERALD) >= 8 && count(loot, Items.DIAMOND) >= 3 && count(loot, Items.IRON_INGOT) >= 10,
					"the Titan's emeralds, diamonds and iron are unchanged");
		}
		helper.succeed();
	}
}
