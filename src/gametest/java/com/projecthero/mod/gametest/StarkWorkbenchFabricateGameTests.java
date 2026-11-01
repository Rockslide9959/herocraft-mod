package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorMenu;
import com.projecthero.mod.ironman.fabricator.StarkWorkbench;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * v0.17.2 (user): the Stark Workbench's Fabricate button. Nothing fabricates until it is pressed (however long a complete
 * recipe sits in the grid); pressed with a valid job it runs for the recipe's build time and makes it; it refuses with a
 * reason when there is nothing to make, components are missing, the output slot is taken, the operator is too far or has
 * no Tony Stark power, or a job already runs; the browsed piece's button makes EXACTLY that piece (all four); Cancel and a
 * job whose inputs are taken away lose nothing (inputs are consumed only on completion); a job pauses, keeping its
 * progress, while no Tony Stark is in range; and no energy is involved anywhere (an old save's "Energy" is ignored).
 */
public class StarkWorkbenchFabricateGameTests implements FabricGameTest {

	private record Bench(StarkFabricatorBlockEntity be, BlockPos abs, ServerPlayer tony, StarkFabricatorMenu menu) {
	}

	private static Bench bench(GameTestHelper helper, int menuId) {
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, IronManBlocks.STARK_FABRICATOR.defaultBlockState());
		BlockPos abs = helper.absolutePos(rel);
		ServerPlayer tony = helper.makeMockServerPlayerInLevel();
		tony.setGameMode(GameType.SURVIVAL);
		tony.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() - 1.5);
		TonyStark.grant(tony);
		var be = (StarkFabricatorBlockEntity) helper.getBlockEntity(rel);
		StarkFabricatorMenu menu = (StarkFabricatorMenu) be.createMenu(menuId, tony.getInventory(), tony);
		tony.containerMenu = menu;
		return new Bench(be, abs, tony, menu);
	}

	private static void alloyInGrid(StarkFabricatorBlockEntity be) {
		be.setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
		be.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
		be.setItem(2, new ItemStack(IronManItems.METAL_PLATING, 1));
	}

	private static void tick(GameTestHelper helper, Bench b, int ticks) {
		for (int t = 0; t < ticks; t++) {
			StarkFabricatorBlockEntity.serverTick(helper.getLevel(), b.abs(), helper.getLevel().getBlockState(b.abs()), b.be());
		}
	}

	private static FabricationRecipe alloy() {
		return FabricatorRecipes.byId("titanium_gold_alloy");
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void nothingFabricatesUntilTheButtonIsPressed(GameTestHelper helper) {
		Bench b = bench(helper, 21);
		alloyInGrid(b.be());
		tick(helper, b, alloy().timeTicks() * 2 + 5);
		helper.assertTrue(!b.be().running() && b.be().progress() == 0, "a complete recipe in the grid does not start by itself");
		helper.assertTrue(b.be().getItem(StarkFabricatorBlockEntity.OUTPUT_SLOT).isEmpty(), "and nothing is made");
		helper.assertTrue(b.be().getItem(0).getCount() == 3, "and nothing is used up");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theButtonStartsAValidJobAndItTakesTheBuildTime(GameTestHelper helper) {
		Bench b = bench(helper, 22);
		alloyInGrid(b.be());
		helper.assertTrue(b.menu().clickMenuButton(b.tony(), StarkWorkbench.FABRICATE_BUTTON), "the Fabricate button is handled");
		helper.assertTrue(b.be().running() && b.menu().running(), "pressing it starts the job (the menu sees it too)");
		helper.assertTrue(b.be().maxProgress() == alloy().timeTicks(), "the job takes the recipe's own build time");
		tick(helper, b, alloy().timeTicks() - 1);
		helper.assertTrue(b.be().running() && b.be().getItem(StarkFabricatorBlockEntity.OUTPUT_SLOT).isEmpty(),
				"a tick before the end it is still building, inputs untouched: " + b.be().getItem(0));
		helper.assertTrue(b.be().getItem(0).getCount() == 3, "inputs are consumed only on completion");
		tick(helper, b, 1);
		ItemStack out = b.be().getItem(StarkFabricatorBlockEntity.OUTPUT_SLOT);
		helper.assertTrue(out.is(IronManItems.TITANIUM_GOLD_ALLOY) && out.getCount() == alloy().result().getCount(), "made the alloy, got " + out);
		helper.assertTrue(b.be().getItem(0).isEmpty() && b.be().getItem(1).isEmpty() && b.be().getItem(2).isEmpty(), "inputs consumed");
		helper.assertTrue(!b.be().running() && b.be().completions() == 1, "the job is over and counted (the client flourish)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theButtonRefusesWhenItCannotRun(GameTestHelper helper) {
		Bench b = bench(helper, 23);
		StarkFabricatorBlockEntity be = b.be();
		helper.assertTrue(be.tryStart(b.tony()) != null && !be.running(), "an empty grid: nothing to fabricate");
		helper.assertTrue("nothing".equals(StarkWorkbench.blocker(be, 0, be.selectedPiece(), false)), "the reason is 'nothing'");
		be.setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
		helper.assertTrue(be.tryStart(b.tony()) != null && !be.running(), "half a recipe: refused");
		alloyInGrid(be);
		be.setItem(StarkFabricatorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.DIRT));
		helper.assertTrue(be.tryStart(b.tony()) != null && !be.running(), "the output slot holds something else: refused");
		helper.assertTrue("output".equals(StarkWorkbench.blocker(be, 0, be.selectedPiece(), false)), "the reason is 'output'");
		be.setItem(StarkFabricatorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
		b.tony().moveTo(b.abs().getX() + 0.5, b.abs().getY(), b.abs().getZ() - 12);
		helper.assertTrue(be.tryStart(b.tony()) != null && !be.running(), "too far away: refused");
		b.tony().moveTo(b.abs().getX() + 0.5, b.abs().getY(), b.abs().getZ() - 1.5);
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		plain.moveTo(b.abs().getX() + 0.5, b.abs().getY(), b.abs().getZ() - 1.5);
		helper.assertTrue(be.tryStart(plain) != null && !be.running(), "no Tony Stark power: refused");
		helper.assertTrue(be.tryStart(b.tony()) == null && be.running(), "valid: starts");
		helper.assertTrue(be.tryStart(b.tony()) != null, "already running: a second press is refused");
		helper.succeed();
	}

	/** The browsed piece's Fabricate button makes exactly that piece of that suit -- all four of them in turn. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void eachPieceButtonMakesExactlyThatPiece(GameTestHelper helper) {
		Bench b = bench(helper, 24);
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
			TonyStark.markBuilt(b.tony(), IronManSuits.byId(prereq));
		}
		b.tony().getInventory().clearContent();
		b.tony().getInventory().add(new ItemStack(IronManItems.BLANK_BLUEPRINT));
		int index = IronManItems.MARK_ORDER.indexOf(suitId);
		for (int piece = 1; piece <= 4; piece++) {
			FabricationRecipe recipe = StarkWorkbench.recipe(suitId, piece);
			for (FabricationRecipe.Input in : recipe.inputs()) {
				b.tony().getInventory().add(new ItemStack(in.item(), in.count()));
			}
			b.menu().clickMenuButton(b.tony(), StarkWorkbench.buildButtonId(index, piece));
			helper.assertTrue(b.be().running() && b.be().selectedPiece() == piece, "piece " + piece + " started");
			tick(helper, b, recipe.timeTicks());
			ArmorItem.Type type = StarkWorkbench.pieceType(piece);
			ItemStack out = b.be().getItem(StarkFabricatorBlockEntity.OUTPUT_SLOT);
			helper.assertTrue(out.is(IronManItems.armor(suitId, type)) && out.getCount() == 1,
					"piece " + piece + " made the " + suitId + " " + type.getName() + ", got " + out);
			helper.assertTrue(StarkWorkbench.pieceBuilt(StarkWorkbench.state(b.tony()), suitId, piece), "piece " + piece + " counts as built");
			b.be().setItem(StarkFabricatorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY); // taken out
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cancelAndTakingAnInputAwayLoseNothing(GameTestHelper helper) {
		Bench b = bench(helper, 25);
		StarkFabricatorBlockEntity be = b.be();
		alloyInGrid(be);
		helper.assertTrue(be.tryStart(b.tony()) == null, "started");
		tick(helper, b, alloy().timeTicks() / 2);
		helper.assertTrue(b.menu().clickMenuButton(b.tony(), StarkWorkbench.CANCEL_BUTTON), "the Cancel button is handled");
		helper.assertTrue(!be.running() && be.progress() == 0, "Cancel stops the job");
		helper.assertTrue(be.getItem(0).getCount() == 3 && be.getItem(1).getCount() == 3 && be.getItem(2).getCount() == 1,
				"every input is still in the grid");
		helper.assertTrue(be.getItem(StarkFabricatorBlockEntity.OUTPUT_SLOT).isEmpty(), "and nothing was made");
		// a job whose component is taken out mid-build cancels itself, the same way
		helper.assertTrue(be.tryStart(b.tony()) == null, "restarted");
		tick(helper, b, 5);
		ItemStack taken = be.removeItem(2, 1);
		tick(helper, b, 1);
		helper.assertTrue(!taken.isEmpty() && !be.running(), "taking a component away cancels the job");
		helper.assertTrue(be.getItem(0).getCount() == 3 && be.getItem(1).getCount() == 3, "the rest is untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aJobPausesWhileNoTonyStarkIsNear(GameTestHelper helper) {
		Bench b = bench(helper, 26);
		alloyInGrid(b.be());
		helper.assertTrue(b.be().tryStart(b.tony()) == null, "started");
		tick(helper, b, 10);
		int at = b.be().progress();
		b.tony().moveTo(b.abs().getX() + 0.5, b.abs().getY(), b.abs().getZ() - 15);
		tick(helper, b, 20);
		helper.assertTrue(b.be().running() && b.be().progress() == at && !b.be().advancing(), "paused, keeping its progress");
		b.tony().moveTo(b.abs().getX() + 0.5, b.abs().getY(), b.abs().getZ() - 1.5);
		tick(helper, b, 5);
		helper.assertTrue(b.be().progress() == at + 5 && b.be().advancing(), "and carries on when Tony Stark comes back");
		helper.succeed();
	}

	/** No energy anywhere: an old save's buffer is ignored, an empty one never blocks, nothing is saved for it. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void noEnergyIsInvolved(GameTestHelper helper) {
		Bench b = bench(helper, 27);
		CompoundTag old = new CompoundTag();
		old.putInt("Energy", 0); // an old, flat Fabricator
		b.be().loadWithComponents(old, helper.getLevel().registryAccess());
		alloyInGrid(b.be());
		helper.assertTrue(b.be().tryStart(b.tony()) == null, "an old save with an empty buffer still fabricates");
		tick(helper, b, alloy().timeTicks());
		helper.assertTrue(b.be().getItem(StarkFabricatorBlockEntity.OUTPUT_SLOT).is(IronManItems.TITANIUM_GOLD_ALLOY), "made it");
		CompoundTag saved = b.be().saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertFalse(saved.contains("Energy"), "no energy is saved any more");
		helper.assertTrue(b.be().data.getCount() == 3, "the menu syncs progress / length / piece only (no energy)");
		helper.succeed();
	}
}
