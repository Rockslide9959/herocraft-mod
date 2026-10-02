package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ironman.sorter.SortCategory;
import com.projecthero.mod.ironman.sorter.SortPlan;
import com.projecthero.mod.ironman.sorter.SorterBotEntity;
import com.projecthero.mod.ironman.sorter.SortingStationBlockEntity;
import com.projecthero.mod.ironman.sorter.StarkSorter;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** v0.14.16: the Stark Sorting Station -- classification, the sort plan, and the Sorter Bot's item custody. */
public class StarkSorterGameTests implements FabricGameTest {

	// ------------------------------------------------------------------ helpers

	private static SortingStationBlockEntity station(GameTestHelper h, BlockPos rel) {
		h.setBlock(rel, StarkSorter.STATION);
		SortingStationBlockEntity be = (SortingStationBlockEntity) h.getBlockEntity(rel);
		AABB bounds = h.getBounds();
		be.setScanFilterForTests(p -> bounds.contains(Vec3.atCenterOf(p)));
		return be;
	}

	private static Container chest(GameTestHelper h, BlockPos rel, ItemStack... contents) {
		h.setBlock(rel, Blocks.CHEST);
		Container c = (Container) h.getBlockEntity(rel);
		for (int i = 0; i < contents.length; i++) {
			c.setItem(i, contents[i]);
		}
		return c;
	}

	private static List<SortPlan.Target> scan(GameTestHelper h, BlockPos stationRel) {
		AABB bounds = h.getBounds();
		return SortPlan.scan(h.getLevel(), h.absolutePos(stationRel), SortingStationBlockEntity.RADIUS,
				p -> bounds.contains(Vec3.atCenterOf(p)));
	}

	private static SortPlan.Target targetAt(GameTestHelper h, List<SortPlan.Target> targets, BlockPos rel) {
		BlockPos abs = h.absolutePos(rel);
		return targets.stream().filter(t -> t.blocks().contains(abs)).findFirst().orElse(null);
	}

	private static ItemStack s(Item item, int count) {
		return new ItemStack(item, count);
	}

	private static void tally(Map<Item, Integer> into, Iterable<ItemStack> stacks) {
		for (ItemStack st : stacks) {
			if (!st.isEmpty()) {
				into.merge(st.getItem(), st.getCount(), Integer::sum);
			}
		}
	}

	private static List<ItemStack> contents(Container c) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) {
			out.add(c.getItem(i));
		}
		return out;
	}

	// ------------------------------------------------------------------ classification

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterClassifiesCommonItems(GameTestHelper h) {
		Object[][] cases = {
				{ Items.COBBLESTONE, SortCategory.BUILDING }, { Items.OAK_LOG, SortCategory.WOOD },
				{ Items.OAK_PLANKS, SortCategory.WOOD }, { Items.IRON_INGOT, SortCategory.ORES },
				{ Items.DIAMOND, SortCategory.ORES }, { Items.IRON_ORE, SortCategory.ORES },
				{ Items.REDSTONE, SortCategory.REDSTONE }, { Items.REPEATER, SortCategory.REDSTONE },
				{ Items.DIAMOND_SWORD, SortCategory.COMBAT }, { Items.IRON_CHESTPLATE, SortCategory.COMBAT },
				{ Items.ARROW, SortCategory.COMBAT }, { Items.IRON_PICKAXE, SortCategory.TOOLS },
				{ Items.IRON_AXE, SortCategory.TOOLS }, { Items.SHEARS, SortCategory.TOOLS },
				{ Items.BREAD, SortCategory.FOOD }, { Items.COOKED_BEEF, SortCategory.FOOD },
				{ Items.WHEAT_SEEDS, SortCategory.FARMING }, { Items.OAK_SAPLING, SortCategory.FARMING },
				{ Items.ROTTEN_FLESH, SortCategory.MOB_DROPS }, { Items.BONE, SortCategory.MOB_DROPS },
				{ Items.STRING, SortCategory.MOB_DROPS }, { Items.POTION, SortCategory.BREWING },
				{ Items.BLAZE_POWDER, SortCategory.BREWING }, { Items.WHITE_WOOL, SortCategory.DECORATION },
				{ Items.RED_DYE, SortCategory.DECORATION }, { StarkSorter.STATION_ITEM, SortCategory.MOD_ITEMS },
				{ Items.PAPER, SortCategory.MISC },
		};
		for (Object[] c : cases) {
			SortCategory got = SortCategory.of(new ItemStack((Item) c[0]));
			h.assertTrue(got == c[1], c[0] + " should be " + c[1] + ", got " + got);
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ granularity

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterFewChestsUseCoarseGroups(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		station(h, st);
		chest(h, new BlockPos(5, 1, 1));
		chest(h, new BlockPos(5, 1, 3));
		chest(h, new BlockPos(5, 1, 5));
		List<SortPlan.Target> targets = scan(h, st);
		h.assertTrue(targets.size() == 3, "three containers in range, got " + targets.size());
		SortPlan plan = SortPlan.build(h.getLevel(), targets, List.of(s(Items.COBBLESTONE, 64), s(Items.OAK_LOG, 64),
				s(Items.DIAMOND_SWORD, 1), s(Items.IRON_PICKAXE, 1), s(Items.BREAD, 10), s(Items.WHEAT_SEEDS, 10),
				s(Items.IRON_INGOT, 5), s(Items.BONE, 5)));
		h.assertTrue(plan.coarse(), "1-3 containers plan in coarse groups");
		h.assertTrue(plan.buckets().size() == 3, "one bucket per container, got " + plan.buckets().size());
		for (SortCategory.Group g : SortCategory.Group.values()) {
			SortPlan.Bucket first = null;
			for (SortCategory c : SortCategory.values()) {
				if (c.group() != g) {
					continue;
				}
				SortPlan.Bucket b = plan.bucketOf(c);
				if (first == null) {
					first = b;
				}
				h.assertTrue(b == first, "a coarse group is never split: " + c + " in " + g);
			}
		}
		for (SortPlan.Bucket b : plan.buckets()) {
			h.assertTrue(b.targets().size() == 1, "each bucket gets exactly one chest");
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterManyChestsUseFineCategories(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		station(h, st);
		for (int x : new int[] { 4, 6 }) {
			for (int z : new int[] { 0, 2, 4, 6 }) {
				chest(h, new BlockPos(x, 1, z));
			}
		}
		List<SortPlan.Target> targets = scan(h, st);
		h.assertTrue(targets.size() == 8, "eight containers, got " + targets.size());
		List<ItemStack> incoming = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			incoming.add(s(Items.COBBLESTONE, 64));
		}
		for (int i = 0; i < 4; i++) {
			incoming.add(s(Items.OAK_LOG, 64));
		}
		incoming.addAll(List.of(s(Items.DIAMOND_SWORD, 1), s(Items.BREAD, 8), s(Items.REDSTONE, 8), s(Items.BONE, 8)));
		SortPlan plan = SortPlan.build(h.getLevel(), targets, incoming);
		h.assertTrue(!plan.coarse(), "4+ containers plan in fine categories");
		h.assertTrue(plan.buckets().size() == 6, "six categories present -> six buckets, got " + plan.buckets().size());
		for (SortPlan.Bucket b : plan.buckets()) {
			h.assertTrue(b.categories().size() == 1, "no merging needed with spare chests");
		}
		h.assertTrue(plan.bucketOf(SortCategory.BUILDING).targets().size() == 2, "the biggest category gets a second chest");
		h.assertTrue(plan.bucketOf(SortCategory.WOOD).targets().size() == 2, "the next biggest gets the other spare");
		int assigned = plan.buckets().stream().mapToInt(b -> b.targets().size()).sum();
		h.assertTrue(assigned == 8, "every container is used, got " + assigned);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterMergesSmallestSiblingCategories(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		station(h, st);
		chest(h, new BlockPos(5, 1, 0));
		chest(h, new BlockPos(5, 1, 2));
		chest(h, new BlockPos(5, 1, 4));
		chest(h, new BlockPos(5, 1, 6));
		SortPlan plan = SortPlan.build(h.getLevel(), scan(h, st), List.of(
				s(Items.COBBLESTONE, 64), s(Items.STONE, 64), s(Items.DIRT, 64), s(Items.OAK_LOG, 64),
				s(Items.DIAMOND_SWORD, 1), s(Items.IRON_SWORD, 1), s(Items.IRON_PICKAXE, 1),
				s(Items.BREAD, 8), s(Items.APPLE, 8), s(Items.REDSTONE, 8)));
		h.assertTrue(plan.buckets().size() == 4, "merged down to the four containers, got " + plan.buckets().size());
		h.assertTrue(plan.bucketOf(SortCategory.WOOD) == plan.bucketOf(SortCategory.BUILDING), "wood merges into building (sibling)");
		h.assertTrue(plan.bucketOf(SortCategory.TOOLS) == plan.bucketOf(SortCategory.COMBAT), "tools merge into combat (sibling)");
		h.assertTrue(plan.bucketOf(SortCategory.FOOD) != plan.bucketOf(SortCategory.REDSTONE), "food and redstone keep their own chests");
		h.succeed();
	}

	// ------------------------------------------------------------------ existing contents

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterKeepsAChestsExistingCategory(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		station(h, st);
		chest(h, new BlockPos(4, 1, 1));
		chest(h, new BlockPos(6, 1, 3), s(Items.BREAD, 5), s(Items.COOKED_BEEF, 3));
		chest(h, new BlockPos(4, 1, 5));
		List<SortPlan.Target> targets = scan(h, st);
		SortPlan plan = SortPlan.build(h.getLevel(), targets, List.of(s(Items.COBBLESTONE, 64),
				s(Items.DIAMOND_SWORD, 1), s(Items.BREAD, 10)));
		SortPlan.Target foodChest = targetAt(h, targets, new BlockPos(6, 1, 3));
		h.assertTrue(plan.bucketOf(SortCategory.FOOD).targets().contains(foodChest), "the chest already holding food stays the food chest");
		h.assertTrue(foodChest.equals(plan.destinationFor(h.getLevel(), s(Items.BREAD, 10))), "bread goes to the food chest");
		h.assertTrue(!foodChest.equals(plan.destinationFor(h.getLevel(), s(Items.COBBLESTONE, 64))), "cobblestone does not");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterSendsIdenticalItemsToTheChestHoldingThem(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		station(h, st);
		chest(h, new BlockPos(3, 1, 1), s(Items.STONE, 10));
		chest(h, new BlockPos(6, 1, 6), s(Items.DIRT, 10));
		List<SortPlan.Target> targets = scan(h, st);
		SortPlan plan = SortPlan.build(h.getLevel(), targets, List.of(s(Items.DIRT, 20), s(Items.STONE, 20)));
		h.assertTrue(targetAt(h, targets, new BlockPos(6, 1, 6)).equals(plan.destinationFor(h.getLevel(), s(Items.DIRT, 20))),
				"dirt goes to the (farther) chest that already holds dirt");
		h.assertTrue(targetAt(h, targets, new BlockPos(3, 1, 1)).equals(plan.destinationFor(h.getLevel(), s(Items.STONE, 20))),
				"stone goes to the chest that already holds stone");
		h.succeed();
	}

	// ------------------------------------------------------------------ overflow

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterOverflowsIntoTheNextChest(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		ItemStack[] nearlyFull = new ItemStack[26];
		for (int i = 0; i < 26; i++) {
			nearlyFull[i] = s(Items.COBBLESTONE, 64);
		}
		Container a = chest(h, new BlockPos(3, 1, 1), nearlyFull);
		Container b = chest(h, new BlockPos(6, 1, 6));
		be.setItem(0, s(Items.DIRT, 64));
		be.setItem(1, s(Items.DIRT, 64));
		be.setItem(2, s(Items.DIRT, 64));
		h.assertTrue(!be.startSort(null).getString().isEmpty(), "sort starts");
		SortPlan plan = be.plan();
		List<SortPlan.Target> targets = plan.targets();
		SortPlan.Target ta = targetAt(h, targets, new BlockPos(3, 1, 1));
		SortPlan.Target tb = targetAt(h, targets, new BlockPos(6, 1, 6));

		SortingStationBlockEntity.Load first = be.takeLoad();
		h.assertTrue(first != null && first.target().equals(ta), "first load fills the last slot of the nearly-full chest");
		h.assertTrue(be.carried().size() == 1, "only what fits is taken, got " + be.carried().size());
		h.assertTrue(be.depositCarried(first.target()), "deposit succeeds");
		SortingStationBlockEntity.Load second = be.takeLoad();
		h.assertTrue(second != null && second.target().equals(tb), "the rest overflows into the next chest");
		h.assertTrue(be.carried().size() == 2, "the two remaining stacks travel together");
		h.assertTrue(be.depositCarried(second.target()), "deposit succeeds");
		h.assertTrue(be.takeLoad() == null, "nothing left");
		h.assertTrue(be.isEmpty(), "station emptied");
		Map<Item, Integer> inA = new HashMap<>();
		tally(inA, contents(a));
		Map<Item, Integer> inB = new HashMap<>();
		tally(inB, contents(b));
		h.assertTrue(inA.getOrDefault(Items.DIRT, 0) == 64 && inB.getOrDefault(Items.DIRT, 0) == 128, "64 + 128 dirt");
		SorterBotEntity bot = (SorterBotEntity) h.getLevel().getEntity(be.botId());
		if (bot != null) {
			bot.abort();
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void sorterLeavesWhatCannotFitInTheStation(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		ItemStack[] full = new ItemStack[27];
		for (int i = 0; i < 27; i++) {
			full[i] = s(Items.COBBLESTONE, 64);
		}
		chest(h, new BlockPos(4, 1, 4), full);
		be.setItem(5, s(Items.DIRT, 40));
		be.startSort(null);
		h.assertTrue(be.isRunning(), "the bot is deployed even if it will find no room");
		h.succeedWhen(() -> {
			h.assertTrue(!be.isRunning(), "job ends");
			h.assertTrue(be.getItem(5).is(Items.DIRT) && be.getItem(5).getCount() == 40, "the dirt is still in the station");
			h.assertTrue(h.getLevel().getEntitiesOfClass(SorterBotEntity.class, h.getBounds().inflate(4)).isEmpty(), "bot gone");
		});
	}

	// ------------------------------------------------------------------ scanning

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterCountsADoubleChestOnce(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		station(h, st);
		h.setBlock(new BlockPos(3, 1, 4), Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		h.setBlock(new BlockPos(4, 1, 4), Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		h.setBlock(new BlockPos(6, 1, 1), Blocks.BARREL);
		chest(h, new BlockPos(1, 1, 6));
		List<SortPlan.Target> targets = scan(h, st);
		h.assertTrue(targets.size() == 3, "double chest + barrel + chest = 3 containers, got " + targets.size());
		SortPlan.Target dbl = targetAt(h, targets, new BlockPos(3, 1, 4));
		h.assertTrue(dbl != null && dbl.blocks().size() == 2, "the double chest is one target with both halves");
		h.assertTrue(dbl.equals(targetAt(h, targets, new BlockPos(4, 1, 4))), "either half resolves to the same target");
		h.assertTrue(dbl.resolve(h.getLevel()).getContainerSize() == 54, "and holds 54 slots");
		h.assertTrue(targetAt(h, targets, new BlockPos(6, 1, 1)) != null, "barrels count");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterOnlyScansTenBlocks(GameTestHelper h) {
		BlockPos st = new BlockPos(0, 1, 0);
		station(h, st);
		chest(h, new BlockPos(7, 1, 7));  // sqrt(98) = 9.9 blocks
		chest(h, new BlockPos(7, 7, 7));  // sqrt(134) = 11.6 blocks
		List<SortPlan.Target> targets = scan(h, st);
		h.assertTrue(targets.size() == 1, "only the chest within 10 blocks, got " + targets.size());
		h.assertTrue(targets.get(0).key().equals(h.absolutePos(new BlockPos(7, 1, 7))), "the near one");
		h.succeed();
	}

	// ------------------------------------------------------------------ the bot, end to end

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2400)
	public void sorterRunConservesEveryItem(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		List<Container> chests = List.of(chest(h, new BlockPos(6, 1, 1)), chest(h, new BlockPos(6, 1, 4)),
				chest(h, new BlockPos(1, 1, 6)), chest(h, new BlockPos(4, 1, 6), s(Items.BREAD, 3)));
		List<ItemStack> load = List.of(s(Items.COBBLESTONE, 64), s(Items.OAK_LOG, 32), s(Items.BREAD, 16),
				s(Items.DIAMOND_SWORD, 1), s(Items.IRON_INGOT, 20), s(Items.BONE, 10), s(Items.WHEAT_SEEDS, 30),
				s(Items.WHITE_WOOL, 12), s(Items.REDSTONE, 40), s(Items.DIRT, 64), s(Items.DIRT, 10), s(Items.COBBLESTONE, 30));
		for (int i = 0; i < load.size(); i++) {
			be.setItem(i * 4, load.get(i).copy());
		}
		Map<Item, Integer> before = new HashMap<>();
		tally(before, load);
		before.merge(Items.BREAD, 3, Integer::sum);

		be.startSort(null);
		h.assertTrue(be.isRunning(), "the bot is deployed");
		h.assertTrue(!h.getLevel().getEntitiesOfClass(SorterBotEntity.class, h.getBounds().inflate(2)).isEmpty(), "a bot spawned");
		h.succeedWhen(() -> {
			h.assertTrue(!be.isRunning(), "still sorting (" + be.done() + "/" + be.total() + ")");
			h.assertTrue(h.getLevel().getEntitiesOfClass(SorterBotEntity.class, h.getBounds().inflate(4)).isEmpty(), "bot gone");
			h.assertTrue(be.isEmpty() && be.carried().isEmpty(), "station emptied");
			Map<Item, Integer> after = new HashMap<>();
			for (Container c : chests) {
				tally(after, contents(c));
			}
			h.assertTrue(after.equals(before), "no item lost or duplicated: " + before + " -> " + after);
			h.assertTrue(be.done() == load.size(), "progress counted every stack, got " + be.done());
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void sorterReturnsCargoWhenTheBotIsLost(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		chest(h, new BlockPos(5, 1, 5));
		be.setItem(0, s(Items.COBBLESTONE, 64));
		be.setItem(1, s(Items.STONE, 64));
		be.startSort(null);
		h.assertTrue(be.takeLoad() != null && !be.carried().isEmpty(), "a load is in transit");
		h.getLevel().getEntity(be.botId()).discard();
		h.succeedWhen(() -> {
			h.assertTrue(!be.isRunning(), "the station gives up on a vanished bot");
			h.assertTrue(be.carried().isEmpty(), "nothing left in transit");
			Map<Item, Integer> now = new HashMap<>();
			tally(now, contents(be));
			h.assertTrue(now.getOrDefault(Items.COBBLESTONE, 0) == 64 && now.getOrDefault(Items.STONE, 0) == 64,
					"everything is back in the station: " + now);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void sorterBreakingTheStationDropsEverything(GameTestHelper h) {
		BlockPos st = new BlockPos(2, 1, 2);
		SortingStationBlockEntity be = station(h, st);
		chest(h, new BlockPos(6, 1, 6));
		be.setItem(0, s(Items.COBBLESTONE, 64));
		be.setItem(1, s(Items.STONE, 64));
		be.setItem(9, s(Items.BREAD, 12));
		be.startSort(null);
		h.assertTrue(be.takeLoad() != null && !be.carried().isEmpty(), "a load is in transit when it breaks");
		h.getLevel().destroyBlock(h.absolutePos(st), true); // (GameTestHelper.destroyBlock does not drop the block)
		h.succeedWhen(() -> {
			Map<Item, Integer> dropped = new HashMap<>();
			for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(4))) {
				dropped.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
			h.assertTrue(dropped.getOrDefault(Items.COBBLESTONE, 0) == 64 && dropped.getOrDefault(Items.STONE, 0) == 64
					&& dropped.getOrDefault(Items.BREAD, 0) == 12, "the store and the cargo both drop: " + dropped);
			h.assertTrue(dropped.getOrDefault(StarkSorter.STATION_ITEM, 0) == 1, "and the station itself");
			h.assertTrue(h.getLevel().getEntitiesOfClass(SorterBotEntity.class, h.getBounds().inflate(4)).isEmpty(), "the bot is recalled");
		});
	}

	// ------------------------------------------------------------------ Tidy (v0.14.20)

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static Map<Item, Integer> tallyAll(Container... cs) {
		Map<Item, Integer> out = new HashMap<>();
		for (Container c : cs) {
			tally(out, contents(c));
		}
		return out;
	}

	private static void recallBot(GameTestHelper h, SortingStationBlockEntity be) {
		if (be.botId() != null && h.getLevel().getEntity(be.botId()) instanceof SorterBotEntity bot) {
			bot.abort();
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600)
	public void sorterTidyMovesJumbledStacksHome(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		be.setItem(0, s(Items.DIRT, 17)); // the station's own store is left alone
		Container blocks = chest(h, new BlockPos(5, 1, 1), s(Items.COBBLESTONE, 64), s(Items.COBBLESTONE, 64),
				s(Items.COBBLESTONE, 64), s(Items.STONE, 30), s(Items.BREAD, 5), s(Items.DIAMOND_SWORD, 1), s(Items.COBBLESTONE, 10));
		Container food = chest(h, new BlockPos(5, 1, 4), s(Items.BREAD, 10), s(Items.COOKED_BEEF, 10), s(Items.APPLE, 5),
				s(Items.COBBLESTONE, 12), s(Items.IRON_PICKAXE, 1));
		Container gear = chest(h, new BlockPos(1, 1, 6), s(Items.IRON_SWORD, 1), s(Items.BOW, 1), s(Items.DIAMOND_PICKAXE, 1),
				s(Items.OAK_LOG, 20), s(Items.CARROT, 8));
		Map<Item, Integer> before = tallyAll(blocks, food, gear);

		Component status = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.tidy_plan".equals(key(status)), "tidy starts, got " + key(status));
		h.assertTrue(be.isTidying(), "the bot is deployed in Tidy mode");
		h.assertTrue(be.total() == 6, "six stacks are out of place, got " + be.total());
		h.succeedWhen(() -> {
			h.assertTrue(!be.isRunning(), "still tidying (" + be.done() + "/" + be.total() + ")");
			h.assertTrue(h.getLevel().getEntitiesOfClass(SorterBotEntity.class, h.getBounds().inflate(4)).isEmpty(), "bot gone");
			h.assertTrue(be.carried().isEmpty(), "nothing left in transit");
			Map<SortCategory.Group, Container> byGroup = Map.of(SortCategory.Group.BLOCKS, blocks,
					SortCategory.Group.FOOD_FARMING, food, SortCategory.Group.GEAR, gear);
			byGroup.forEach((group, c) -> {
				for (ItemStack it : contents(c)) {
					h.assertTrue(it.isEmpty() || SortCategory.of(it).group() == group, it + " is in the " + group + " chest");
				}
				h.assertTrue(!com.projecthero.mod.ironman.sorter.Stash.fragmented(c), "split stacks merged in the " + group + " chest");
			});
			Map<Item, Integer> after = tallyAll(blocks, food, gear);
			h.assertTrue(after.equals(before), "no item lost or duplicated: " + before + " -> " + after);
			h.assertTrue(be.getItem(0).is(Items.DIRT) && be.getItem(0).getCount() == 17, "the station store is untouched");
			h.assertTrue(be.done() == 6, "every misplaced stack counted, got " + be.done());
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
	public void sorterTidyLeavesWhatItsChestCannotTake(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		ItemStack[] a = new ItemStack[22];
		for (int i = 0; i < 20; i++) {
			a[i] = s(Items.COBBLESTONE, 64);
		}
		a[20] = s(Items.BREAD, 64);
		a[21] = s(Items.BREAD, 64);
		ItemStack[] b = new ItemStack[26];
		for (int i = 0; i < 26; i++) {
			b[i] = s(Items.BREAD, 64);
		}
		Container blocks = chest(h, new BlockPos(5, 1, 1), a);
		Container food = chest(h, new BlockPos(5, 1, 5), b); // one free slot: room for one of the two bread stacks
		Map<Item, Integer> before = tallyAll(blocks, food);

		be.startTidy(null);
		h.assertTrue(be.isTidying(), "the bot is deployed");
		h.succeedWhen(() -> {
			h.assertTrue(!be.isRunning(), "still tidying");
			Map<Item, Integer> inBlocks = tallyAll(blocks);
			Map<Item, Integer> inFood = tallyAll(food);
			h.assertTrue(inFood.getOrDefault(Items.BREAD, 0) == 27 * 64, "the food chest filled up, got " + inFood);
			h.assertTrue(inBlocks.getOrDefault(Items.BREAD, 0) == 64, "the bread that did not fit stayed put, got " + inBlocks);
			h.assertTrue(inBlocks.getOrDefault(Items.COBBLESTONE, 0) == 20 * 64, "cobblestone untouched");
			h.assertTrue(tallyAll(blocks, food).equals(before), "no item lost or duplicated");
			h.assertTrue(be.isEmpty() && be.carried().isEmpty(), "nothing ended up in the station");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterTidyWithFullChestsDoesNotStart(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		ItemStack[] a = new ItemStack[27];
		ItemStack[] b = new ItemStack[27];
		for (int i = 0; i < 27; i++) {
			a[i] = s(Items.COBBLESTONE, 64);
			b[i] = s(Items.BREAD, 64);
		}
		a[26] = s(Items.BREAD, 64);
		b[26] = s(Items.COBBLESTONE, 64);
		Container blocks = chest(h, new BlockPos(5, 1, 1), a);
		Container food = chest(h, new BlockPos(5, 1, 5), b);
		Component status = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.tidy_no_room".equals(key(status)), "reports no room, got " + key(status));
		h.assertTrue(!be.isRunning(), "no bot is launched");
		h.assertTrue(blocks.getItem(26).is(Items.BREAD) && food.getItem(26).is(Items.COBBLESTONE), "both stay where they are");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterTidyAlreadyTidyAndRefusedWhileSorting(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		chest(h, new BlockPos(5, 1, 1), s(Items.COBBLESTONE, 64), s(Items.STONE, 20));
		chest(h, new BlockPos(5, 1, 5), s(Items.BREAD, 10));
		Component status = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.already_tidy".equals(key(status)), "already tidy, got " + key(status));
		h.assertTrue(!be.isRunning(), "no bot for a tidy room");

		be.setItem(0, s(Items.DIRT, 10));
		be.startSort(null);
		h.assertTrue(be.isRunning() && !be.isTidying(), "a normal sort is running");
		Component refused = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.already_running".equals(key(refused)), "tidy is refused, got " + key(refused));
		h.assertTrue(!be.isTidying(), "the sort keeps running as a sort");
		recallBot(h, be);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterTidyBrokenDestinationReturnsTheLoad(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		Container blocks = chest(h, new BlockPos(5, 1, 1), s(Items.COBBLESTONE, 64), s(Items.COBBLESTONE, 64), s(Items.BREAD, 20));
		chest(h, new BlockPos(5, 1, 5), s(Items.BREAD, 30), s(Items.APPLE, 4));
		be.startTidy(null);
		h.assertTrue(be.isTidying(), "tidy starts");
		SortingStationBlockEntity.TidyLoad load = be.nextTidyLoad();
		h.assertTrue(load != null && load.dest() != null && load.source().blocks().contains(h.absolutePos(new BlockPos(5, 1, 1))),
				"first trip: from the blocks chest to the food chest");
		h.assertTrue(be.collectTidyLoad(load) == 1, "the bread is picked up");
		h.assertTrue(blocks.getItem(2).isEmpty(), "and has left the blocks chest");
		h.getLevel().removeBlock(h.absolutePos(new BlockPos(5, 1, 5)), false);
		h.assertTrue(!be.depositCarried(load.dest()), "the destination is gone");
		be.returnCarried();
		h.assertTrue(be.carried().isEmpty(), "nothing left in transit");
		Map<Item, Integer> station = new HashMap<>();
		tally(station, contents(be));
		h.assertTrue(station.getOrDefault(Items.BREAD, 0) == 20, "the bread went home to the station: " + station);
		recallBot(h, be);
		h.succeed();
	}
}
