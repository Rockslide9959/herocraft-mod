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
import com.projecthero.mod.ironman.sorter.Stash;
import com.projecthero.mod.ironman.sorter.SorterSupply;

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
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
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
			h.assertTrue(b.targets().size() == 1, "nothing needs a second chest yet: " + b.categories());
		}
		h.assertTrue(plan.spares().size() == 2, "v0.14.21: the two chests nobody needs are spares, got " + plan.spares().size());

		// a category whose items need more than one chest gets a second one up front
		for (int i = 0; i < 26; i++) {
			incoming.add(s(Items.COBBLESTONE, 64));
		}
		SortPlan big = SortPlan.build(h.getLevel(), targets, incoming);
		h.assertTrue(big.bucketOf(SortCategory.BUILDING).targets().size() == 2, "31 stacks of cobblestone need two chests");
		h.assertTrue(big.spares().size() == 1, "the other chest stays spare, got " + big.spares().size());
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
		// 26 stacks of stone: Blocks needs 28 slots, so both chests are Blocks chests (v0.14.21: otherwise the dirt
		// chest would be a spare and Tidy would consolidate it)
		ItemStack[] stone = new ItemStack[26];
		for (int i = 0; i < 26; i++) {
			stone[i] = s(Items.STONE, 64);
		}
		chest(h, new BlockPos(3, 1, 1), stone);
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
		be.supply().setItem(0, s(Items.OAK_SIGN, 5));
		be.supply().setItem(3, s(Items.CHEST, 2));
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
			h.assertTrue(dropped.getOrDefault(Items.OAK_SIGN, 0) == 5 && dropped.getOrDefault(Items.CHEST, 0) == 2,
					"and the sign / chest supply: " + dropped);
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

	// ------------------------------------------------------------------ v0.14.21: repack, strict homes, supplies

	/** A stone floor under the whole structure (the bot only places chests on solid ground). */
	private static void floor(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/**
	 * Run the job that was just started to the end right now, exactly as the bot would (Sort loads, then the
	 * chest-to-chest pass), then recall the bot and finish. Capped so a bug can never hang the test.
	 */
	private static void driveJob(GameTestHelper h, SortingStationBlockEntity be) {
		h.assertTrue(be.isRunning(), "a job is running");
		int guard = 0;
		if (!be.isTidying()) {
			SortingStationBlockEntity.Load load;
			while ((load = be.takeLoad()) != null && guard++ < 400) {
				be.depositCarried(load.target());
			}
			h.assertTrue(be.beginFinishingPass(), "the sort switches to its finishing pass");
		}
		SortingStationBlockEntity.TidyLoad trip;
		while ((trip = be.nextTidyLoad()) != null && guard++ < 800) {
			be.collectTidyLoad(trip);
			if (trip.dest() != null) {
				be.depositCarried(trip.dest());
			}
		}
		h.assertTrue(guard < 800, "the job ends");
		recallBot(h, be);
		be.finish();
		h.assertTrue(!be.isRunning() && be.carried().isEmpty(), "job finished, nothing in transit");
	}

	/** Every slot of every container, for before/after comparisons. */
	private static List<String> snapshot(Container... cs) {
		List<String> out = new ArrayList<>();
		for (Container c : cs) {
			for (ItemStack st : contents(c)) {
				out.add(st.isEmpty() ? "-" : st.getItem() + "x" + st.getCount());
			}
			out.add("|");
		}
		return out;
	}

	/** The repack contract: no gaps before the last stack, no two partial stacks of one item, in Stash order. */
	private static void assertPacked(GameTestHelper h, Container c, String what) {
		List<ItemStack> all = contents(c);
		int last = -1;
		for (int i = 0; i < all.size(); i++) {
			if (!all.get(i).isEmpty()) {
				last = i;
			}
		}
		for (int i = 0; i <= last; i++) {
			h.assertTrue(!all.get(i).isEmpty(), what + ": gap at slot " + i);
		}
		for (int i = 0; i <= last; i++) {
			ItemStack a = all.get(i);
			if (i < last) {
				h.assertTrue(Stash.ORDER.compare(a, all.get(i + 1)) <= 0, what + ": out of order at slot " + i + " (" + a + ", " + all.get(i + 1) + ")");
			}
			for (int j = i + 1; j <= last; j++) {
				ItemStack b = all.get(j);
				if (ItemStack.isSameItemSameComponents(a, b)) {
					h.assertTrue(a.getCount() == a.getMaxStackSize() || b.getCount() == b.getMaxStackSize(),
							what + ": two partial stacks of " + a.getItem() + " at " + i + " and " + j);
				}
			}
		}
	}

	private static SignBlockEntity signAt(GameTestHelper h, BlockPos rel) {
		return h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof SignBlockEntity sign ? sign : null;
	}

	private static String line(SignBlockEntity sign, int i) {
		return key(sign.getFrontText().getMessage(i, false));
	}

	/** The user's v0.14.20 screenshot: a large ore chest after Tidy -- gaps everywhere and split partial stacks. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
	public void sorterTidyRepacksAGappyChest(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		h.setBlock(new BlockPos(3, 1, 5), Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		h.setBlock(new BlockPos(4, 1, 5), Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		Container big = targetAt(h, scan(h, st), new BlockPos(3, 1, 5)).resolve(h.getLevel());
		h.assertTrue(big.getContainerSize() == 54, "a large chest");
		Object[][] layout = {
				{ Items.COAL, 64 }, { Items.GOLD_INGOT, 64 }, { Items.FLINT, 64 }, { Items.IRON_INGOT, 64 }, { Items.COPPER_INGOT, 64 },
				{ Items.GOLD_BLOCK, 64 }, { Items.EMERALD, 64 }, { Items.IRON_INGOT, 64 }, { Items.IRON_INGOT, 64 },
				{ Items.RAW_IRON, 9 }, { Items.QUARTZ, 64 }, { Items.COPPER_INGOT, 64 }, { Items.DIAMOND, 64 }, { Items.EMERALD_BLOCK, 12 },
				{ Items.IRON_INGOT, 64 }, { Items.EMERALD, 64 }, null, { Items.COAL, 64 },
				{ Items.COPPER_INGOT, 64 }, { Items.IRON_INGOT, 64 }, { Items.RAW_IRON, 64 }, { Items.GOLD_INGOT, 64 }, { Items.COPPER_ORE, 64 },
				{ Items.COAL, 64 }, { Items.COAL, 11 }, { Items.EMERALD, 64 }, { Items.COAL, 6 },
				null, { Items.IRON_INGOT, 25 }, { Items.BONE_BLOCK, 64 }, { Items.EMERALD, 64 }, { Items.LAPIS_LAZULI, 64 },
				{ Items.DIAMOND, 29 }, { Items.GOLD_NUGGET, 4 }, { Items.DIAMOND_BLOCK, 2 }, null,
				null, { Items.EMERALD, 44 }, { Items.RAW_GOLD, 8 }, { Items.GOLD_INGOT, 4 }, { Items.NETHERITE_SCRAP, 3 },
				{ Items.LAPIS_LAZULI, 64 }, { Items.LAPIS_LAZULI, 34 }, null, null,
		};
		for (int i = 0; i < layout.length; i++) {
			if (layout[i] != null) {
				big.setItem(i, s((Item) layout[i][0], (Integer) layout[i][1]));
			}
		}
		Map<Item, Integer> before = tallyAll(big);

		Component status = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.tidy_plan".equals(key(status)), "tidy starts to repack, got " + key(status));
		h.succeedWhen(() -> {
			h.assertTrue(!be.isRunning(), "still tidying");
			h.assertTrue(tallyAll(big).equals(before), "no item lost or duplicated");
			assertPacked(h, big, "the large chest");
			int used = 0;
			for (ItemStack it : contents(big)) {
				used += it.isEmpty() ? 0 : 1;
			}
			h.assertTrue(used == 38, "39 stacks, the coal 11 + 6 merge: 38, got " + used);
			Component again = be.startTidy(null);
			h.assertTrue("message.projecthero.stark_sorting_station.already_tidy".equals(key(again)),
					"a second Tidy finds nothing to do, got " + key(again));
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterRepackIsStableAndConserves(GameTestHelper h) {
		Container c = chest(h, new BlockPos(2, 1, 2), ItemStack.EMPTY, s(Items.DIRT, 10), ItemStack.EMPTY, s(Items.BREAD, 3),
				s(Items.DIRT, 60), s(Items.DIAMOND_SWORD, 1), ItemStack.EMPTY, s(Items.DIRT, 1), s(Items.COBBLESTONE, 64));
		Map<Item, Integer> before = tallyAll(c);
		h.assertTrue(Stash.needsRepack(c, Stash.ORDER), "a gappy chest needs a repack");
		h.assertTrue(Stash.repack(c), "it repacks");
		assertPacked(h, c, "the chest");
		h.assertTrue(tallyAll(c).equals(before), "nothing lost");
		h.assertTrue(c.getItem(0).is(Items.COBBLESTONE) && c.getItem(1).is(Items.DIRT) && c.getItem(1).getCount() == 64
				&& c.getItem(2).is(Items.DIRT) && c.getItem(2).getCount() == 7, "building first, dirt merged 64 + 7");
		h.assertTrue(c.getItem(3).is(Items.DIAMOND_SWORD) && c.getItem(4).is(Items.BREAD) && c.getItem(5).isEmpty(),
				"then combat, then food, then nothing");
		List<String> once = snapshot(c);
		h.assertTrue(!Stash.repack(c) && snapshot(c).equals(once), "repacking a packed chest changes nothing");
		h.succeed();
	}

	/** A lone stack in a chest of its own is carried to its category's chest; the emptied chest becomes a spare. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterTidyConsolidatesALoneStack(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		Container lone = chest(h, new BlockPos(3, 1, 1), s(Items.IRON_INGOT, 10)); // nearest -- but holds the least
		Container ores = chest(h, new BlockPos(6, 1, 6), s(Items.IRON_INGOT, 64), s(Items.IRON_INGOT, 64),
				s(Items.GOLD_INGOT, 30), s(Items.DIAMOND, 5));
		Container food = chest(h, new BlockPos(1, 1, 5), s(Items.BREAD, 20));
		Container wood = chest(h, new BlockPos(5, 1, 3), s(Items.OAK_LOG, 20));
		Map<Item, Integer> before = tallyAll(lone, ores, food, wood);

		SortPlan plan = SortPlan.build(h.getLevel(), scan(h, st), List.of());
		SortPlan.Target loneT = targetAt(h, plan.targets(), new BlockPos(3, 1, 1));
		SortPlan.Target oresT = targetAt(h, plan.targets(), new BlockPos(6, 1, 6));
		h.assertTrue(plan.bucketOf(SortCategory.ORES).targets().equals(List.of(oresT)), "the Ores chest is the one holding the most ores");
		h.assertTrue(plan.isSpare(loneT), "a chest with one lone stack is not a second Ores chest");

		Component status = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.tidy_plan".equals(key(status)), "tidy starts, got " + key(status));
		h.assertTrue(be.total() == 1, "one stack to move, got " + be.total());
		driveJob(h, be);
		h.assertTrue(lone.isEmpty(), "the lone iron left its chest");
		h.assertTrue(tallyAll(ores).getOrDefault(Items.IRON_INGOT, 0) == 138, "and joined the rest: " + tallyAll(ores));
		h.assertTrue(tallyAll(lone, ores, food, wood).equals(before), "no item lost or duplicated");
		assertPacked(h, ores, "the ores chest");
		h.succeed();
	}

	/** A category that needs two chests keeps both; items may be split across them, and only across them. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterOverflowChestIsAllowed(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		ItemStack[] full = new ItemStack[27];
		for (int i = 0; i < 27; i++) {
			full[i] = s(Items.COBBLESTONE, 64);
		}
		Container first = chest(h, new BlockPos(3, 1, 1), full);
		Container second = chest(h, new BlockPos(6, 1, 6), s(Items.COBBLESTONE, 64), s(Items.COBBLESTONE, 64), s(Items.STONE, 20));
		Container food = chest(h, new BlockPos(1, 1, 5), s(Items.BREAD, 20));
		Container wood = chest(h, new BlockPos(5, 1, 3), s(Items.OAK_LOG, 20));
		List<String> before = snapshot(first, second, food, wood);

		SortPlan plan = SortPlan.build(h.getLevel(), scan(h, st), List.of());
		h.assertTrue(plan.bucketOf(SortCategory.BUILDING).targets().size() == 2, "Building needs 30 slots: two chests");
		Component status = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.already_tidy".equals(key(status)), "nothing to move, got " + key(status));
		h.assertTrue(snapshot(first, second, food, wood).equals(before), "the overflow chest keeps its share");

		be.setItem(0, s(Items.DIRT, 64));
		be.startSort(null);
		driveJob(h, be);
		h.assertTrue(tallyAll(second).getOrDefault(Items.DIRT, 0) == 64, "the full chest's overflow takes the dirt: " + tallyAll(second));
		h.assertTrue(!tallyAll(food).containsKey(Items.DIRT) && !tallyAll(wood).containsKey(Items.DIRT), "never another category's chest");
		h.succeed();
	}

	/** Strict homes: with its category's chests full and no supply, a stack stays in the station -- and is reported. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterNeverFilesIntoAnotherCategorysChest(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		ItemStack[] full = new ItemStack[27];
		for (int i = 0; i < 27; i++) {
			full[i] = s(Items.COBBLESTONE, 64);
		}
		chest(h, new BlockPos(3, 1, 1), full);
		Container food = chest(h, new BlockPos(6, 1, 6), s(Items.BREAD, 20));
		be.setItem(0, s(Items.DIRT, 64));
		SortingStationBlockEntity.Needs needs = be.refreshNeeds();
		h.assertTrue(needs.chests() == 1, "one more chest needed, got " + needs.chests());
		h.assertTrue(be.data.get(5) == 1, "and it is short (synced), got " + be.data.get(5));
		h.assertTrue(be.needsMessages().stream().anyMatch(c -> key(c).endsWith(".needs_chests")), "the player is told");
		be.startSort(null);
		driveJob(h, be);
		h.assertTrue(be.getItem(0).is(Items.DIRT), "the dirt stayed in the station");
		h.assertTrue(!tallyAll(food).containsKey(Items.DIRT), "and did not go into the food chest");
		h.succeed();
	}

	/** Tidy twice: the second run changes nothing (and uses no more signs). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterTidyIsIdempotent(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		be.supply().setItem(0, s(Items.OAK_SIGN, 16));
		Container a = chest(h, new BlockPos(3, 1, 3), s(Items.COBBLESTONE, 30), s(Items.BREAD, 5), ItemStack.EMPTY,
				s(Items.COBBLESTONE, 30), s(Items.IRON_INGOT, 3), s(Items.OAK_LOG, 9));
		Container b = chest(h, new BlockPos(6, 1, 3), s(Items.BREAD, 10), s(Items.COBBLESTONE, 12), s(Items.CARROT, 7),
				ItemStack.EMPTY, s(Items.BREAD, 50));
		Container c = chest(h, new BlockPos(3, 1, 6), s(Items.IRON_INGOT, 20), s(Items.DIAMOND_SWORD, 1), s(Items.GOLD_INGOT, 2));
		Container d = chest(h, new BlockPos(6, 1, 6), s(Items.OAK_LOG, 40), s(Items.IRON_PICKAXE, 1));
		Map<Item, Integer> before = tallyAll(a, b, c, d);
		be.startTidy(null);
		driveJob(h, be);
		h.assertTrue(tallyAll(a, b, c, d).equals(before), "no item lost or duplicated");
		for (Container x : List.of(a, b, c, d)) {
			h.assertTrue(!Stash.fragmented(x), "merged");
		}
		List<String> once = snapshot(a, b, c, d);
		int signs = be.signsInSupply();
		Component again = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.already_tidy".equals(key(again)), "second tidy: already tidy, got " + key(again));
		h.assertTrue(!be.isRunning() && snapshot(a, b, c, d).equals(once), "nothing moved");
		h.assertTrue(be.signsInSupply() == signs, "no more signs used");
		h.succeed();
	}

	/** Sort (which ends with a tidy pass) followed by Tidy: the Tidy has nothing left to do. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterSortThenTidyIsStable(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		Container a = chest(h, new BlockPos(3, 1, 3), s(Items.BREAD, 5), s(Items.COBBLESTONE, 40), ItemStack.EMPTY, s(Items.COBBLESTONE, 3));
		Container b = chest(h, new BlockPos(6, 1, 3), s(Items.IRON_INGOT, 5));
		Container c = chest(h, new BlockPos(3, 1, 6));
		Container d = chest(h, new BlockPos(6, 1, 6), s(Items.OAK_LOG, 5), s(Items.APPLE, 2));
		List<ItemStack> load = List.of(s(Items.COBBLESTONE, 64), s(Items.BREAD, 30), s(Items.GOLD_INGOT, 12),
				s(Items.OAK_PLANKS, 40), s(Items.DIAMOND_SWORD, 1), s(Items.REDSTONE, 9), s(Items.BONE, 4));
		for (int i = 0; i < load.size(); i++) {
			be.setItem(i * 3, load.get(i).copy());
		}
		Map<Item, Integer> before = tallyAll(a, b, c, d);
		tally(before, load);
		be.startSort(null);
		driveJob(h, be);
		h.assertTrue(be.isEmpty(), "the station emptied");
		h.assertTrue(tallyAll(a, b, c, d).equals(before), "no item lost or duplicated");
		List<String> sorted = snapshot(a, b, c, d);
		Component tidy = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.already_tidy".equals(key(tidy)), "the sort left it tidy, got " + key(tidy));
		h.assertTrue(snapshot(a, b, c, d).equals(sorted), "nothing moved");
		h.succeed();
	}

	/** Labels: one Stark sign per chest (one per double chest), from the supply; player signs are left alone. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterLabelsChestsWithSigns(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		be.supply().setItem(1, s(Items.SPRUCE_SIGN, 5));
		chest(h, new BlockPos(3, 1, 3), s(Items.COBBLESTONE, 20));
		h.setBlock(new BlockPos(5, 1, 3), Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		h.setBlock(new BlockPos(6, 1, 3), Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		((Container) h.getBlockEntity(new BlockPos(5, 1, 3))).setItem(0, s(Items.BREAD, 20));
		chest(h, new BlockPos(3, 1, 6), s(Items.DIAMOND_SWORD, 1));
		// the player's own sign on the gear chest
		h.setBlock(new BlockPos(3, 1, 5), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));

		SortingStationBlockEntity.Needs needs = be.refreshNeeds();
		h.assertTrue(needs.signs() == 2, "two chests need signs (the gear chest has the player's), got " + needs.signs());
		h.assertTrue(be.data.get(6) == 0, "five signs cover it");

		Component status = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.tidy_plan".equals(key(status)), "tidy launches to label, got " + key(status));
		driveJob(h, be);
		SignBlockEntity blocks = signAt(h, new BlockPos(3, 1, 2));
		h.assertTrue(blocks != null, "a sign on the front of the blocks chest");
		h.assertTrue(SorterSupply.MARKER.equals(line(blocks, 0)) && "sort_group.projecthero.blocks.sign".equals(line(blocks, 1)),
				"it says [Stark] / Blocks: " + line(blocks, 0) + " / " + line(blocks, 1));
		h.assertTrue(blocks.isWaxed(), "and is waxed");
		h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(3, 1, 2))).is(Blocks.SPRUCE_WALL_SIGN), "made of the supplied wood");
		SignBlockEntity food = signAt(h, new BlockPos(5, 1, 2));
		h.assertTrue(food != null && "sort_group.projecthero.food_farming.sign".equals(line(food, 1)), "the double chest gets one sign");
		h.assertTrue(signAt(h, new BlockPos(6, 1, 2)) == null, "only one");
		SignBlockEntity mine = signAt(h, new BlockPos(3, 1, 5));
		h.assertTrue(mine != null && line(mine, 0).isEmpty() && !mine.isWaxed(), "the player's sign is untouched");
		h.assertTrue(be.signsInSupply() == 3 && be.signsPlaced() == 2, "two signs used, got " + be.signsInSupply());

		// a Stark sign is re-written when its chest's category changes
		((Container) h.getBlockEntity(new BlockPos(3, 1, 3))).setItem(0, ItemStack.EMPTY);
		((Container) h.getBlockEntity(new BlockPos(3, 1, 3))).setItem(0, s(Items.CARROT, 64));
		((Container) h.getBlockEntity(new BlockPos(3, 1, 3))).setItem(1, s(Items.CARROT, 64));
		((Container) h.getBlockEntity(new BlockPos(5, 1, 3))).setItem(0, s(Items.COBBLESTONE, 64));
		be.startTidy(null);
		driveJob(h, be);
		String relabelled = line(signAt(h, new BlockPos(3, 1, 2)), 1);
		h.assertTrue("sort_group.projecthero.food_farming.sign".equals(relabelled), "the old blocks chest is now labelled Food, got " + relabelled);
		h.assertTrue(be.signsInSupply() == 3, "re-labelling uses no signs");
		h.succeed();
	}

	/** Missing signs and chests with no free face are counted and reported. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterReportsMissingSigns(GameTestHelper h) {
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		chest(h, new BlockPos(3, 1, 2), s(Items.COBBLESTONE, 20));
		chest(h, new BlockPos(6, 1, 2), s(Items.BREAD, 20));
		chest(h, new BlockPos(4, 1, 5), s(Items.DIAMOND_SWORD, 1));
		for (Direction d : Direction.Plane.HORIZONTAL) { // boxed in: no face for a sign
			h.setBlock(new BlockPos(4, 1, 5).relative(d), Blocks.STONE);
		}
		SortingStationBlockEntity.Needs needs = be.refreshNeeds();
		h.assertTrue(needs.signs() == 2 && needs.noFace() == 1, "two need signs, one has no room: " + needs);
		h.assertTrue(be.data.get(6) == 2 && be.data.get(9) == 1, "synced: 2 short, 1 without a face");
		h.assertTrue(be.needsMessages().stream().anyMatch(c -> key(c).endsWith(".needs_signs")), "the player is told to add signs");
		h.assertTrue(be.needsMessages().stream().anyMatch(c -> key(c).endsWith(".no_sign_face")), "and about the boxed-in chest");
		Component status = be.startTidy(null);
		h.assertTrue("message.projecthero.stark_sorting_station.already_tidy".equals(key(status)), "no signs, no work, got " + key(status));
		be.supply().setItem(0, s(Items.OAK_SIGN, 1));
		be.refreshNeeds();
		h.assertTrue(be.data.get(6) == 1, "one sign added: one short, got " + be.data.get(6));
		h.succeed();
	}

	/** A full category gets a new chest from the supply, placed in line with its chest; requirements reflect it. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterPlacesAChestFromTheSupply(GameTestHelper h) {
		floor(h);
		BlockPos st = new BlockPos(1, 1, 1);
		SortingStationBlockEntity be = station(h, st);
		ItemStack[] full = new ItemStack[27];
		for (int i = 0; i < 27; i++) {
			full[i] = s(Items.COBBLESTONE, 64);
		}
		Container first = chest(h, new BlockPos(4, 1, 4), full);
		be.setItem(0, s(Items.DIRT, 64));
		be.setItem(1, s(Items.STONE, 30));
		Map<Item, Integer> before = tallyAll(first, be);

		h.assertTrue(be.refreshNeeds().chests() == 1 && be.data.get(5) == 1, "one chest needed and short");
		be.supply().setItem(3, s(Items.CHEST, 2));
		be.refreshNeeds();
		h.assertTrue(be.data.get(5) == 0, "covered by the supply");

		be.startSort(null);
		driveJob(h, be);
		h.assertTrue(be.chestsPlaced() == 1 && be.chestsInSupply() == 1, "one chest placed, one left");
		BlockPos[] row = { new BlockPos(3, 1, 4), new BlockPos(5, 1, 4) };
		Container placed = null;
		for (BlockPos p : row) {
			if (h.getLevel().getBlockState(h.absolutePos(p)).is(Blocks.CHEST)) {
				placed = (Container) h.getBlockEntity(p);
				h.assertTrue(h.getLevel().getBlockState(h.absolutePos(p)).getValue(ChestBlock.TYPE) == ChestType.SINGLE, "a single chest");
			}
		}
		h.assertTrue(placed != null, "the new chest continues the full chest's row");
		Map<Item, Integer> after = tallyAll(first, placed, be);
		h.assertTrue(after.equals(before), "no item lost or duplicated: " + before + " -> " + after);
		h.assertTrue(be.isEmpty(), "everything was filed");
		h.assertTrue(be.designations().size() == 2, "both chests are designated Blocks now");
		h.succeed();
	}

	/** With nothing in range, a chest in the supply goes down beside the station and the Sort proceeds. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sorterPlacesAFirstChestBesideTheStation(GameTestHelper h) {
		floor(h);
		BlockPos st = new BlockPos(3, 1, 3);
		SortingStationBlockEntity be = station(h, st);
		be.setItem(0, s(Items.BREAD, 10));
		Component none = be.startSort(null);
		h.assertTrue("message.projecthero.stark_sorting_station.no_chests".equals(key(none)), "no chests, no supply: refused");
		be.supply().setItem(3, s(Items.CHEST, 1));
		be.startSort(null);
		h.assertTrue(be.isRunning() && be.chestsPlaced() == 1, "a chest was placed and the sort started");
		driveJob(h, be);
		h.assertTrue(be.isEmpty(), "the bread was filed into it");
		h.succeed();
	}
}
