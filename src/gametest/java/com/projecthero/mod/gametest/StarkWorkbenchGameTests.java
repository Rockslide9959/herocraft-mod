package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlock;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorMenu;
import com.projecthero.mod.ironman.fabricator.StarkWorkbench;
import com.projecthero.mod.ironman.fabricator.WorkbenchPart;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): the Stark Workbench -- the two-block desk (placement claims the block to the placer's right and is
 * refused when that is blocked; breaking either half removes both and drops ONE item plus the contents; the side half
 * forwards the menu and the energy items to the main half), old one-block Fabricators (load as SINGLE, still work, claim
 * the side half once it is air, never replace anything), and crafting through the new menu (the Build button loads the
 * blueprint + components from the inventory, the unchanged machine logic fabricates the piece).
 */
public class StarkWorkbenchGameTests implements FabricGameTest {

	private static ServerPlayer player(GameTestHelper helper, float yaw) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.setYRot(yaw);
		p.yRotO = yaw;
		return p;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** Places the workbench item at {@code rel} (on the floor) as {@code p} would. */
	private static InteractionResult place(GameTestHelper helper, ServerPlayer p, BlockPos rel) {
		BlockPos abs = helper.absolutePos(rel);
		ItemStack stack = new ItemStack(IronManBlocks.STARK_FABRICATOR_ITEM);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPlaceContext ctx = new BlockPlaceContext(helper.getLevel(), p, InteractionHand.MAIN_HAND, stack,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
		return ((BlockItem) IronManBlocks.STARK_FABRICATOR_ITEM).place(ctx);
	}

	private static int deskItemsAround(GameTestHelper helper, BlockPos rel) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(rel)).inflate(4))) {
			if (e.getItem().is(IronManBlocks.STARK_FABRICATOR_ITEM)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void placingClaimsTheBlockToThePlacersRight(GameTestHelper helper) {
		floor(helper);
		// yaw 180 = looking north: the desk faces south (back at the player) and their right hand is east
		ServerPlayer p = player(helper, 180f);
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.assertTrue(place(helper, p, rel).consumesAction(), "the workbench places");
		BlockState main = helper.getBlockState(rel);
		BlockState side = helper.getBlockState(rel.east());
		helper.assertTrue(main.is(IronManBlocks.STARK_FABRICATOR) && main.getValue(StarkFabricatorBlock.PART) == WorkbenchPart.MAIN,
				"the clicked block is the main half, got " + main);
		helper.assertTrue(main.getValue(StarkFabricatorBlock.FACING) == Direction.SOUTH, "the desk faces the placer");
		helper.assertTrue(side.is(IronManBlocks.STARK_FABRICATOR) && side.getValue(StarkFabricatorBlock.PART) == WorkbenchPart.SIDE
				&& side.getValue(StarkFabricatorBlock.FACING) == Direction.SOUTH, "the block to the placer's right is the side half, got " + side);
		helper.assertTrue(helper.getBlockEntity(rel) instanceof StarkFabricatorBlockEntity, "the main half owns the block entity");
		helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(rel.east())) == null, "the side half has no block entity of its own");
		helper.assertTrue(StarkFabricatorBlock.blockEntity(helper.getLevel(), helper.absolutePos(rel.east())) == helper.getBlockEntity(rel),
				"the side half resolves to the main half's block entity");
		// the four facings: the side half is always on the placer's right
		float[] yaws = { 0f, 90f, 180f, 270f };
		Direction[] right = { Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH };
		for (int i = 0; i < 4; i++) {
			Direction front = Direction.fromYRot(yaws[i]).getOpposite();
			helper.assertTrue(StarkFabricatorBlock.sideDirection(front) == right[i],
					"looking at yaw " + yaws[i] + " the side half goes " + right[i] + ", got " + StarkFabricatorBlock.sideDirection(front));
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void placingIsRefusedWhenTheRightBlockIsTaken(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = player(helper, 180f);
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel.east(), Blocks.STONE);
		helper.assertFalse(place(helper, p, rel).consumesAction(), "no room on the right = no desk");
		helper.assertBlockPresent(Blocks.AIR, rel);
		helper.assertBlockPresent(Blocks.STONE, rel.east());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void breakingTheSideHalfRemovesBothAndDropsOneItem(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = player(helper, 180f);
		BlockPos rel = new BlockPos(2, 1, 2);
		place(helper, p, rel);
		((StarkFabricatorBlockEntity) helper.getBlockEntity(rel)).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		helper.assertTrue(p.gameMode.destroyBlock(helper.absolutePos(rel.east())), "the player breaks the side half");
		helper.assertBlockPresent(Blocks.AIR, rel);
		helper.assertBlockPresent(Blocks.AIR, rel.east());
		helper.assertTrue(deskItemsAround(helper, rel) == 1, "exactly one workbench item drops, got " + deskItemsAround(helper, rel));
		int gold = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(rel)).inflate(4))) {
			if (e.getItem().is(Items.GOLD_INGOT)) {
				gold += e.getItem().getCount();
			}
		}
		helper.assertTrue(gold == 5, "the machine's contents drop too, got " + gold);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void breakingTheMainHalfRemovesBothAndDropsOneItem(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = player(helper, 0f);
		BlockPos rel = new BlockPos(3, 1, 3);
		place(helper, p, rel);
		BlockPos sideRel = rel.west(); // yaw 0 looks south: right hand = west
		helper.assertTrue(helper.getBlockState(sideRel).is(IronManBlocks.STARK_FABRICATOR), "side half placed to the west");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		helper.assertTrue(p.gameMode.destroyBlock(helper.absolutePos(rel)), "the player breaks the main half");
		helper.assertBlockPresent(Blocks.AIR, rel);
		helper.assertBlockPresent(Blocks.AIR, sideRel);
		helper.assertTrue(deskItemsAround(helper, rel) == 1, "exactly one workbench item drops, got " + deskItemsAround(helper, rel));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSideHalfForwardsTheMenuAndEnergy(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = player(helper, 180f);
		BlockPos rel = new BlockPos(2, 1, 2);
		place(helper, p, rel);
		TonyStark.grant(p);
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		BlockPos sideAbs = helper.absolutePos(rel.east());
		p.moveTo(sideAbs.getX() + 0.5, sideAbs.getY(), sideAbs.getZ() + 1.8); // in reach, in front of the side half
		BlockState side = helper.getLevel().getBlockState(sideAbs);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		side.useWithoutItem(helper.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(sideAbs), Direction.UP, sideAbs, false));
		helper.assertTrue(p.containerMenu instanceof StarkFabricatorMenu, "right-clicking the side half opens the workbench menu");
		helper.assertTrue(p.containerMenu.getSlot(0).container == be, "the menu drives the main half's block entity");
		helper.assertTrue(p.containerMenu.stillValid(p), "the menu stays valid (the main half is the menu's block)");
		p.closeContainer();
		int before = be.energy();
		ItemStack core = new ItemStack(IronManItems.REACTOR_CORE);
		p.setItemInHand(InteractionHand.MAIN_HAND, core);
		side.useItemOn(core, helper.getLevel(), p, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(sideAbs), Direction.UP, sideAbs, false));
		helper.assertTrue(be.energy() > before, "a Reactor Core used on the side half charges the main half");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anOldOneBlockFabricatorClaimsItsSideHalfWhenFree(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, IronManBlocks.STARK_FABRICATOR.defaultBlockState()); // what an old world loads: SINGLE, north
		helper.assertTrue(helper.getBlockState(rel).getValue(StarkFabricatorBlock.PART) == WorkbenchPart.SINGLE,
				"a state without the part property loads as SINGLE");
		BlockPos abs = helper.absolutePos(rel);
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		StarkFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
		helper.assertTrue(helper.getBlockState(rel).getValue(StarkFabricatorBlock.PART) == WorkbenchPart.MAIN, "it becomes the main half");
		BlockState side = helper.getBlockState(rel.west()); // north-facing: the right-hand side is west
		helper.assertTrue(side.is(IronManBlocks.STARK_FABRICATOR) && side.getValue(StarkFabricatorBlock.PART) == WorkbenchPart.SIDE,
				"and grows its side half to the west, got " + side);
		helper.assertTrue(helper.getBlockEntity(rel) == be, "the same block entity (inventory, energy) is kept");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aBlockedOldFabricatorStaysSingleAndStillWorks(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel.west(), Blocks.CHEST);
		helper.setBlock(rel, IronManBlocks.STARK_FABRICATOR.defaultBlockState());
		BlockPos abs = helper.absolutePos(rel);
		ServerPlayer tony = player(helper, 0f);
		tony.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() - 1.5);
		TonyStark.grant(tony);
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		be.addEnergy(FabricatorRecipes.MAX_ENERGY);
		be.setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
		be.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
		be.setItem(2, new ItemStack(IronManItems.METAL_PLATING, 1));
		FabricationRecipe alloy = FabricatorRecipes.byId("titanium_gold_alloy");
		for (int t = 0; t < alloy.timeTicks() + 2; t++) {
			StarkFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
		}
		helper.assertTrue(helper.getBlockState(rel).getValue(StarkFabricatorBlock.PART) == WorkbenchPart.SINGLE,
				"a blocked side never replaces what is there");
		helper.assertBlockPresent(Blocks.CHEST, rel.west());
		helper.assertTrue(be.getItem(StarkFabricatorBlockEntity.OUTPUT_SLOT).is(IronManItems.TITANIUM_GOLD_ALLOY),
				"the one-block desk still fabricates");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void losingTheSideHalfLeavesAWorkingSingleDesk(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = player(helper, 180f);
		BlockPos rel = new BlockPos(2, 1, 2);
		place(helper, p, rel);
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		be.setItem(3, new ItemStack(Items.DIAMOND, 2));
		helper.getLevel().setBlock(helper.absolutePos(rel.east()), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		helper.assertTrue(helper.getBlockState(rel).getValue(StarkFabricatorBlock.PART) == WorkbenchPart.SINGLE,
				"the main half falls back to a one-block desk");
		helper.assertTrue(helper.getBlockEntity(rel) == be && be.getItem(3).getCount() == 2, "nothing inside is lost");
		helper.assertTrue(deskItemsAround(helper, rel) == 0, "and nothing dropped");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theBuildButtonLoadsThePieceAndTheMachineFabricatesIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer tony = player(helper, 180f);
		BlockPos rel = new BlockPos(2, 1, 2);
		place(helper, tony, rel);
		BlockPos abs = helper.absolutePos(rel);
		tony.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 1.5);
		TonyStark.grant(tony);
		// the first Era I suit (in progression order) that the Workbench builds
		String suitId = null;
		for (String id : StarkWorkbench.eraSuits(1)) {
			if (StarkWorkbench.recipe(id, 1) != null) {
				suitId = id;
				break;
			}
		}
		helper.assertTrue(suitId != null, "an Era I suit is built at the Workbench");
		String prereq = IronManItems.prerequisiteSuit(suitId);
		if (prereq != null) {
			TonyStark.markBuilt(tony, IronManSuits.byId(prereq));
		}
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		be.addEnergy(FabricatorRecipes.MAX_ENERGY);
		FabricationRecipe recipe = StarkWorkbench.recipe(suitId, 1);
		tony.getInventory().clearContent();
		tony.getInventory().add(new ItemStack(IronManItems.BLANK_BLUEPRINT)); // Build stamps it (the suit is unlocked)
		for (FabricationRecipe.Input in : recipe.inputs()) {
			tony.getInventory().add(new ItemStack(in.item(), in.count()));
		}
		StarkFabricatorMenu menu = (StarkFabricatorMenu) be.createMenu(7, tony.getInventory(), tony);
		tony.containerMenu = menu;
		int index = IronManItems.MARK_ORDER.indexOf(suitId);
		helper.assertTrue(menu.clickMenuButton(tony, StarkWorkbench.buildButtonId(index, 1)), "the Build button is handled");
		helper.assertTrue(be.getItem(StarkFabricatorBlockEntity.BLUEPRINT_SLOT).is(IronManItems.blueprintFor(suitId)),
				"Build stamped the Blank Blueprint and loaded it");
		helper.assertTrue(be.selectedPiece() == 1, "the helmet is the job");
		for (FabricationRecipe.Input in : recipe.inputs()) {
			helper.assertTrue(StarkWorkbench.countInInventory(tony.getInventory(), in.item()) == 0, "every component moved into the grid");
		}
		helper.assertTrue(recipe.matches(be), "the grid now satisfies the helmet recipe");
		helper.assertTrue(be.jobSuit().equals(suitId), "the hologram job is the suit");
		for (int t = 0; t < recipe.timeTicks() + 2; t++) {
			StarkFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
		}
		ItemStack out = be.getItem(StarkFabricatorBlockEntity.OUTPUT_SLOT);
		helper.assertTrue(out.getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId),
				"the machine fabricated the helmet, got " + out);
		helper.assertTrue(StarkWorkbench.pieceBuilt(StarkWorkbench.state(tony), suitId, 1), "the piece counts as built (bright hologram)");
		// the old per-piece toggles still drive the same machine
		helper.assertTrue(menu.clickMenuButton(tony, 2) && be.selectedPiece() == 2, "button 2 still picks the chestplate");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void buildRefusesALockedSuitAndListsFollowTheProgression(GameTestHelper helper) {
		floor(helper);
		ServerPlayer tony = player(helper, 180f);
		BlockPos rel = new BlockPos(2, 1, 2);
		place(helper, tony, rel);
		TonyStark.grant(tony);
		List<String> era1 = StarkWorkbench.eraSuits(1);
		helper.assertTrue(era1.size() >= 5, "Era I lists its five suits, got " + era1);
		int last = -1;
		for (String id : era1) {
			int i = IronManItems.MARK_ORDER.indexOf(id);
			helper.assertTrue(i > last, "the list follows MARK_ORDER");
			last = i;
		}
		helper.assertTrue(StarkWorkbench.eraSuits(2).equals(List.of("mark_2", "mark_6", "mark_vii", "mark_9", "mark_8")),
				"Era II lists the Mk VI .. Mk IX and the Hulkbuster in progression order, got " + StarkWorkbench.eraSuits(2));
		helper.assertTrue(StarkWorkbench.eraSuits(3).isEmpty(), "Era III is not live yet");
		String locked = era1.get(era1.size() - 1);
		helper.assertTrue(StarkWorkbench.status(StarkWorkbench.state(tony), locked) == StarkWorkbench.Status.LOCKED,
				"a fresh Tony Stark has the last Era I suit locked");
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		tony.getInventory().add(new ItemStack(IronManItems.BLANK_BLUEPRINT));
		int blanks = StarkWorkbench.countInInventory(tony.getInventory(), IronManItems.BLANK_BLUEPRINT);
		StarkFabricatorMenu menu = (StarkFabricatorMenu) be.createMenu(8, tony.getInventory(), tony);
		tony.containerMenu = menu;
		menu.clickMenuButton(tony, StarkWorkbench.buildButtonId(IronManItems.MARK_ORDER.indexOf(locked), 1));
		helper.assertTrue(be.getItem(StarkFabricatorBlockEntity.BLUEPRINT_SLOT).isEmpty(), "a locked suit loads nothing");
		helper.assertTrue(StarkWorkbench.countInInventory(tony.getInventory(), IronManItems.BLANK_BLUEPRINT) == blanks,
				"and no Blank Blueprint is stamped");
		float[] bars = StarkWorkbench.statBars(IronManSuits.byId(era1.get(0)));
		for (float b : bars) {
			helper.assertTrue(b > 0f && b <= 1f, "every stat bar is in (0, 1]");
		}
		helper.succeed();
	}

	/** v0.16.7 (user): the Era II tab -- every suit has its four Workbench recipes, a blueprint, and unlocks off the one before it. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theEraTwoTabBuildsThroughTheSameFlow(GameTestHelper helper) {
		floor(helper);
		ServerPlayer tony = player(helper, 180f);
		BlockPos rel = new BlockPos(2, 1, 2);
		place(helper, tony, rel);
		TonyStark.grant(tony);
		List<String> era2 = StarkWorkbench.eraSuits(2);
		helper.assertTrue(StarkWorkbench.LIVE_ERAS >= 2 && !era2.isEmpty(), "Era II is live");
		for (String id : era2) {
			helper.assertTrue(IronManItems.blueprintFor(id) != null, id + " has a blueprint");
			for (int p = 1; p <= 4; p++) {
				helper.assertTrue(StarkWorkbench.recipe(id, p) != null, id + " piece " + p + " has a Workbench recipe");
			}
		}
		helper.assertTrue("mark_v".equals(IronManItems.prerequisiteSuit(era2.get(0))), "the Mk VI unlocks off the Mk V Stealth");
		helper.assertTrue(StarkWorkbench.status(StarkWorkbench.state(tony), era2.get(0)) == StarkWorkbench.Status.LOCKED,
				"a fresh Tony Stark has the Mk VI locked");
		// unlock it and Build its helmet: the blueprint is stamped and the components loaded, as on the Era I tab
		String suitId = era2.get(0);
		TonyStark.markBuilt(tony, IronManSuits.byId(IronManItems.prerequisiteSuit(suitId)));
		helper.assertTrue(StarkWorkbench.status(StarkWorkbench.state(tony), suitId) == StarkWorkbench.Status.BUILDABLE, "now buildable");
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		be.addEnergy(FabricatorRecipes.MAX_ENERGY);
		FabricationRecipe recipe = StarkWorkbench.recipe(suitId, 1);
		tony.getInventory().clearContent();
		tony.getInventory().add(new ItemStack(IronManItems.BLANK_BLUEPRINT));
		for (FabricationRecipe.Input in : recipe.inputs()) {
			tony.getInventory().add(new ItemStack(in.item(), in.count()));
		}
		StarkFabricatorMenu menu = (StarkFabricatorMenu) be.createMenu(9, tony.getInventory(), tony);
		tony.containerMenu = menu;
		menu.clickMenuButton(tony, StarkWorkbench.buildButtonId(IronManItems.MARK_ORDER.indexOf(suitId), 1));
		helper.assertTrue(be.getItem(StarkFabricatorBlockEntity.BLUEPRINT_SLOT).is(IronManItems.blueprintFor(suitId)),
				"Build stamped and loaded the Mk VI blueprint");
		helper.assertTrue(recipe.matches(be), "the grid satisfies the Mk VI helmet recipe");
		helper.succeed();
	}
}
