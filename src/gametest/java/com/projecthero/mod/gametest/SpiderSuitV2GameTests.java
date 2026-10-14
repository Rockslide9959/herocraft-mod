package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMask;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderTripMines;
import com.projecthero.mod.spider.SpiderWebReserve;
import com.projecthero.mod.spider.SpiderWebWings;
import com.projecthero.mod.spider.SpiderWrap;
import com.projecthero.mod.spider.data.SpiderMoveState;
import com.projecthero.mod.spider.entity.SpiderTripMineEntity;
import com.projecthero.mod.spider.item.SpiderItems;
import com.projecthero.mod.spider.item.SpiderSuitUpgradeRecipe;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 Spider-Man Suit v2: the keep-your-old-suit recipe, v2 detection, Shift+C routing (Black Suit Slam, then the
 * Trip Mine, else the Web Wall), the Trip Mine (placement, the 6 cap, the laser trigger + cocoon, pick-up) and the Web
 * Wings glide rules and flight.
 */
public class SpiderSuitV2GameTests implements FabricGameTest {

	private static ServerPlayer spider(GameTestHelper helper, boolean v2) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY));
		SpiderMan.evolveFromAdhesion(p);
		p.removeAllEffects();
		SpiderMan.setWebReserve(p, 200.0f);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(v2 ? SpiderItems.SUIT_V2_CHESTPLATE : SpiderItems.SUIT_CHESTPLATE));
		return p;
	}

	private static void floor(GameTestHelper helper, int y) {
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, y, 0)), helper.absolutePos(new BlockPos(7, y, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
	}

	/** The live mines these players own (tests run side by side, so never count a neighbour's). */
	private static List<SpiderTripMineEntity> mines(GameTestHelper helper, ServerPlayer... owners) {
		List<java.util.UUID> ids = java.util.Arrays.stream(owners).map(ServerPlayer::getUUID).toList();
		return helper.getLevel().getEntitiesOfClass(SpiderTripMineEntity.class,
				new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24), m -> ids.contains(m.owner()));
	}

	private static void shiftC(ServerPlayer p) {
		p.setShiftKeyDown(true);
		SpiderMoves.handle(p, AbilitySlot.SLOT_6, true);
		SpiderMoves.handle(p, AbilitySlot.SLOT_6, false);
		p.setShiftKeyDown(false);
	}

	// ---------------- crafting ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void craftingAV2PieceKeepsTheOriginalPiece(GameTestHelper helper) {
		Item[] olds = {SpiderItems.SUIT_HELMET, SpiderItems.SUIT_CHESTPLATE, SpiderItems.SUIT_LEGGINGS, SpiderItems.SUIT_BOOTS};
		Item[] news = {SpiderItems.SUIT_V2_HELMET, SpiderItems.SUIT_V2_CHESTPLATE, SpiderItems.SUIT_V2_LEGGINGS, SpiderItems.SUIT_V2_BOOTS};
		String[] names = {"helmet", "chestplate", "leggings", "boots"};
		var level = helper.getLevel();
		for (int k = 0; k < 4; k++) {
			var holder = level.getRecipeManager().byKey(ProjectHeroMod.id("spider_man_suit_v2_" + names[k])).orElse(null);
			helper.assertTrue(holder != null && holder.value() instanceof SpiderSuitUpgradeRecipe, names[k] + ": the upgrade recipe is loaded");
			SpiderSuitUpgradeRecipe recipe = (SpiderSuitUpgradeRecipe) holder.value();
			// fill the grid from the recipe's own ingredients (the original piece where the recipe asks for it)
			int w = recipe.getWidth(), h = recipe.getHeight();
			List<ItemStack> grid = new ArrayList<>();
			int oldIndex = -1;
			NonNullList<net.minecraft.world.item.crafting.Ingredient> ings = recipe.getIngredients();
			for (int i = 0; i < ings.size(); i++) {
				ItemStack[] options = ings.get(i).getItems();
				ItemStack s = options.length == 0 ? ItemStack.EMPTY : options[0].copy();
				if (s.is(olds[k])) {
					s.setDamageValue(7); // a worn original -- it must come back exactly as it went in
					oldIndex = i;
				}
				grid.add(s);
			}
			helper.assertTrue(oldIndex >= 0, names[k] + ": the recipe needs the original piece");
			CraftingInput input = CraftingInput.of(w, h, grid);
			helper.assertTrue(recipe.matches(input, level), names[k] + ": the grid matches");
			ItemStack out = recipe.assemble(input, level.registryAccess());
			helper.assertTrue(out.is(news[k]), names[k] + ": it makes the v2 piece");
			// exactly what the crafting table's result slot asks for when the result is taken
			NonNullList<ItemStack> left = level.getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, input, level);
			helper.assertTrue(left.get(oldIndex).is(olds[k]) && left.get(oldIndex).getCount() == 1
					&& left.get(oldIndex).getDamageValue() == 7, names[k] + ": the original piece stays in the grid, untouched");
			for (int i = 0; i < left.size(); i++) {
				if (i != oldIndex) {
					helper.assertTrue(left.get(i).isEmpty(), names[k] + ": the materials are used up");
				}
			}
			// no original piece, no v2
			List<ItemStack> noOld = new ArrayList<>(grid);
			noOld.set(oldIndex, new ItemStack(Items.STRING));
			helper.assertFalse(recipe.matches(CraftingInput.of(w, h, noOld), level), names[k] + ": needs the original");
			List<ItemStack> withV2 = new ArrayList<>(grid);
			withV2.set(oldIndex, new ItemStack(news[k]));
			helper.assertFalse(recipe.matches(CraftingInput.of(w, h, withV2), level), names[k] + ": a v2 piece does not count");
		}
		helper.succeed();
	}

	// ---------------- detection ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theV2SuitIsDetectedOnTheChestAndKeepsTheMask(GameTestHelper helper) {
		ServerPlayer p = spider(helper, false);
		helper.assertFalse(SpiderWebWings.wearingV2(p), "the original suit is not v2");
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(SpiderItems.SUIT_V2_HELMET));
		helper.assertFalse(SpiderWebWings.wearingV2(p), "the v2 mask alone is not the suit");
		helper.assertTrue(SpiderMask.wearingHood(p), "the v2 mask works with H like the original");
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SpiderItems.SUIT_V2_CHESTPLATE));
		helper.assertTrue(SpiderWebWings.wearingV2(p), "the v2 suit on the chest is v2");
		helper.assertTrue(SpiderItems.SUIT_V2_CHESTPLATE instanceof com.projecthero.mod.spider.item.SpiderManArmorItem
				&& "spider_man".equals(SpiderItems.SUIT_V2_CHESTPLATE.armorSetId()), "v2 rides the original suit's rig");
		helper.assertTrue(SpiderSuitUpgradeRecipe.isOriginalSuitPiece(new ItemStack(SpiderItems.SUIT_CHESTPLATE))
				&& !SpiderSuitUpgradeRecipe.isOriginalSuitPiece(new ItemStack(SpiderItems.SUIT_V2_CHESTPLATE))
				&& !SpiderSuitUpgradeRecipe.isOriginalSuitPiece(new ItemStack(SpiderItems.SYMBIOTE_CHESTPLATE)), "original-piece rule");
		helper.succeed();
	}

	// ---------------- Shift+C routing ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftCIsTheWebWallInTheOriginalSuitAndTheTripMineInV2(GameTestHelper helper) {
		floor(helper, 0);
		ServerPlayer p = spider(helper, false);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0f, 90f); // looking straight down at the floor
		helper.assertTrue(SpiderMoves.WEB_WALL.equals(SpiderMoves.idFor(p, 5, 1)), "original suit: Shift+C shows Web Wall");
		shiftC(p);
		helper.assertTrue(SpiderMan.abilityCooldownRemaining(p, SpiderMoves.WEB_WALL) > 0, "original suit: Shift+C is the Web Wall");
		helper.assertTrue(mines(helper, p).isEmpty(), "and no mine");

		ServerPlayer q = spider(helper, true);
		q.moveTo(at.x, at.y, at.z, 0f, 90f);
		helper.assertTrue(SpiderTripMines.TRIP_MINE.equals(SpiderMoves.idFor(q, 5, 1)), "v2: Shift+C shows Trip Mine");
		helper.assertTrue(SpiderMoves.maxCooldown(SpiderTripMines.TRIP_MINE) == SpiderTripMines.COOLDOWN, "the HUD knows its cooldown");
		float before = SpiderWebReserve.get(q);
		shiftC(q);
		helper.assertTrue(SpiderMan.abilityCooldownRemaining(q, SpiderMoves.WEB_WALL) == 0, "v2: no Web Wall");
		helper.assertTrue(SpiderMan.abilityCooldownRemaining(q, SpiderTripMines.TRIP_MINE) > 0, "v2: the Trip Mine's cooldown");
		helper.assertTrue(Math.abs(before - SpiderWebReserve.get(q) - SpiderTripMines.COST) < 0.01f, "it costs 10 webbing");
		helper.assertTrue(mines(helper, q).size() == 1, "v2: Shift+C placed a mine");
		helper.assertTrue(mines(helper, q).get(0).face() == Direction.UP, "on the floor he was looking at");
		shiftC(q);
		helper.assertTrue(mines(helper, q).size() == 1, "the 2-second cooldown holds the next one");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBlackSuitSlamStillComesFirst(GameTestHelper helper) {
		floor(helper, 0);
		var blob = com.projecthero.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		blob.moveTo(helper.absoluteVec(new Vec3(1, 1, 1)));
		helper.getLevel().addFreshEntity(blob);
		ServerPlayer p = spider(helper, true);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0f, 90f);
		com.projecthero.mod.symbiote.SymbioteBonding.bondNow(p, blob);
		com.projecthero.mod.symbiote.Symbiote.state(p).active = true;
		helper.assertTrue(com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.SLAM.equals(SpiderMoves.idFor(p, 5, 1)),
				"Black Suit: Shift+C shows the Slam");
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SpiderItems.SUIT_V2_CHESTPLATE));
		shiftC(p);
		helper.assertTrue(mines(helper, p).isEmpty(), "Black Suit: Shift+C never places a mine");
		helper.succeed();
	}

	// ---------------- the Trip Mine ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void aMineGoesOnTheFaceHeLooksAtWithItsLaser(GameTestHelper helper) {
		floor(helper, 0);
		for (int y = 1; y <= 3; y++) {
			for (int x = 0; x <= 7; x++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 6)), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		ServerPlayer p = spider(helper, true);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f); // facing south, the wall 2.5 blocks ahead
		SpiderTripMineEntity mine = SpiderTripMines.place(p);
		helper.assertTrue(mine != null, "placed");
		helper.assertTrue(mine.face() == Direction.NORTH, "stuck on the wall's face, pointing back at him (" + mine.face() + ")");
		Vec3 a = mine.anchor();
		helper.assertTrue(Math.abs(a.z - helper.absoluteVec(new Vec3(0, 0, 6)).z) < 0.05, "flat on the wall surface");
		helper.assertTrue(mine.beamLength() > 0.5f && mine.beamLength() <= SpiderTripMines.BEAM_RANGE + 0.01f,
				"the laser runs out up to 8 blocks (" + mine.beamLength() + ")");
		helper.assertTrue(Math.abs(mine.beamEnd().x - mine.beamStart().x) < 1e-6 && mine.beamEnd().z < mine.beamStart().z,
				"straight out from the wall");
		// a block in its way cuts it short, at the block
		Vec3 start = mine.beamStart();
		helper.getLevel().setBlock(BlockPos.containing(start.add(0, 0, -2.6)), Blocks.STONE.defaultBlockState(), 2);
		float cut = mine.measureBeam();
		double expected = start.z - (Math.floor(start.z - 2.6) + 1.0);
		helper.assertTrue(Math.abs(cut - expected) < 0.05, "a block in the way stops the laser at it (" + cut + " vs " + expected + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixMinesAtOnceTheSeventhReplacesTheOldest(GameTestHelper helper) {
		floor(helper, 0);
		ServerPlayer p = spider(helper, true);
		List<SpiderTripMineEntity> placed = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			placed.add(SpiderTripMines.placeAt(p, helper.absoluteVec(new Vec3(0.5 + i, 1.0, 1.5)), Direction.UP));
		}
		helper.assertTrue(SpiderTripMines.count(p.getUUID()) == SpiderTripMines.MAX_PER_PLAYER, "six counted");
		helper.assertTrue(placed.get(0).isRemoved(), "the oldest is gone");
		for (int i = 1; i < 7; i++) {
			helper.assertFalse(placed.get(i).isRemoved(), "mine " + i + " stays");
		}
		helper.assertTrue(SpiderTripMines.LIFETIME_TICKS == 12000, "each lasts 10 minutes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theFirstEnemyAcrossTheLaserIsYankedOntoTheMineAndCocooned(GameTestHelper helper) {
		floor(helper, 0);
		for (int y = 1; y <= 3; y++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(1, y, 3)), Blocks.STONE.defaultBlockState(), 2);
		}
		ServerPlayer p = spider(helper, true);
		p.moveTo(helper.absoluteVec(new Vec3(6.5, 1.0, 6.5)));
		// on the wall's east face, at knee height: the laser runs east along x
		SpiderTripMineEntity mine = SpiderTripMines.placeAt(p, helper.absoluteVec(new Vec3(2.0, 1.5, 3.5)), Direction.EAST);
		// a boss walks through it untouched
		Zombie boss = EntityType.ZOMBIE.create(helper.getLevel());
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
		boss.setHealth(400f);
		helper.assertFalse(SpiderTripMines.catchable(p, p.getUUID(), boss), "a boss is never caught");
		helper.assertFalse(SpiderTripMines.catchable(p, p.getUUID(), p), "nor is he");
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 cross = helper.absoluteVec(new Vec3(6.0, 1.0, 3.5));
		zombie.moveTo(cross.x, cross.y, cross.z, 90f, 0f);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(SpiderWrap.isWrapped(zombie), "the zombie that crossed the laser is cocooned");
			Vec3 rest = SpiderTripMines.restingSpot(mine, zombie);
			helper.assertTrue(zombie.position().distanceTo(rest) < 0.6, "pinned against the mine's surface ("
					+ zombie.position() + " vs " + rest + ")");
			helper.assertTrue(mine.isRemoved(), "the mine is spent");
			helper.assertTrue(SpiderTripMines.count(p.getUUID()) == 0, "and no longer counted");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sneakRightClickPicksHisMineBackUp(GameTestHelper helper) {
		floor(helper, 0);
		ServerPlayer p = spider(helper, true);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		SpiderTripMineEntity mine = SpiderTripMines.placeAt(p, helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)), Direction.UP);
		stranger.setShiftKeyDown(true);
		mine.interact(stranger, InteractionHand.MAIN_HAND);
		helper.assertFalse(mine.isRemoved(), "someone else can't take it");
		mine.interact(p, InteractionHand.MAIN_HAND);
		helper.assertFalse(mine.isRemoved(), "a plain right-click does nothing");
		SpiderMan.setWebReserve(p, 100f);
		p.setShiftKeyDown(true);
		mine.interact(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(mine.isRemoved(), "sneak + right-click picks it up");
		helper.assertTrue(Math.abs(SpiderWebReserve.get(p) - 110f) < 0.01f, "its webbing comes back");
		helper.assertTrue(SpiderTripMines.count(p.getUUID()) == 0, "no longer counted");
		helper.succeed();
	}

	// ---------------- Web Wings ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void webWingsGlideOnlyOnAHeldJumpWhileFallingInTheV2Suit(GameTestHelper helper) {
		ServerPlayer p = spider(helper, true);
		Vec3 high = helper.absoluteVec(new Vec3(3.5, 6.0, 3.5));
		p.moveTo(high.x, high.y, high.z, 0f, 0f);
		p.setOnGround(false);
		helper.assertTrue(SpiderWebWings.canGlide(p), "airborne in the v2 suit he can glide");
		helper.assertTrue(SpiderWebWings.canStart(p, -0.4, SpiderWebWings.HOLD_TICKS), "Jump held 6 ticks while falling: glide");
		helper.assertFalse(SpiderWebWings.canStart(p, -0.4, SpiderWebWings.HOLD_TICKS - 1), "a tap is not a glide (the swing trick's)");
		helper.assertFalse(SpiderWebWings.canStart(p, -0.2, 20), "not falling fast enough yet");
		helper.assertFalse(SpiderWebWings.canStart(p, 0.3, 20), "rising");
		p.setOnGround(true);
		helper.assertFalse(SpiderWebWings.canGlide(p), "not on the ground");
		p.setOnGround(false);
		SpiderMoves.setFlag(p, SpiderMoveState.FLAG_HANGING, true);
		helper.assertFalse(SpiderWebWings.canGlide(p), "not while hanging");
		SpiderMoves.setFlag(p, SpiderMoveState.FLAG_HANGING, false);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SpiderItems.SUIT_CHESTPLATE));
		helper.assertFalse(SpiderWebWings.canGlide(p), "the original suit has no wings");
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		plain.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SpiderItems.SUIT_V2_CHESTPLATE));
		plain.moveTo(high.x, high.y, high.z, 0f, 0f);
		plain.setOnGround(false);
		helper.assertFalse(SpiderWebWings.canGlide(plain), "the suit alone (no Spider-Man power) has no wings");
		// the server owns the synced flag: granted while eligible, cleared once he lands
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SpiderItems.SUIT_V2_CHESTPLATE));
		helper.assertTrue(SpiderWebWings.request(p, true) && SpiderWebWings.isGliding(p), "the server grants the glide");
		p.fallDistance = 12f;
		SpiderWebWings.tickPlayer(p);
		helper.assertTrue(p.fallDistance == 0f, "no fall damage builds up while gliding");
		p.setOnGround(true);
		SpiderWebWings.tickPlayer(p);
		helper.assertFalse(SpiderWebWings.isGliding(p), "landing ends it");
		p.setOnGround(false);
		helper.assertFalse(SpiderWebWings.request(plain, true), "a client can't claim a glide it isn't allowed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void webWingsGlideForwardSinkSlowlyBankAndDive(GameTestHelper helper) {
		// level flight settles at the cruise speed, sinking slowly
		Vec3 v = SpiderWebWings.entryVelocity(new Vec3(0, -0.6, 0), 0f);
		for (int i = 0; i < 200; i++) {
			v = SpiderWebWings.step(v, 0f, 0f);
		}
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		helper.assertTrue(h > 0.7 && h < 0.9, "cruise ~0.7-0.9 b/t (" + h + ")");
		helper.assertTrue(v.y < -0.08 && v.y > -0.12, "sink ~0.08-0.12 b/t (" + v.y + ")");
		helper.assertTrue(v.z > 0.7, "forward the way he looks (yaw 0 = south)");
		// diving gains speed, pulling up loses it but he still sinks
		Vec3 dive = v;
		for (int i = 0; i < 10; i++) {
			dive = SpiderWebWings.step(dive, 0f, 50f);
		}
		helper.assertTrue(Math.sqrt(dive.x * dive.x + dive.z * dive.z) > h + 0.2, "looking down dives and gains speed");
		helper.assertTrue(dive.y < v.y - 0.2, "and drops faster");
		Vec3 up = v;
		for (int i = 0; i < 40; i++) {
			up = SpiderWebWings.step(up, 0f, -35f);
		}
		helper.assertTrue(Math.sqrt(up.x * up.x + up.z * up.z) < h, "looking up bleeds speed");
		helper.assertTrue(up.y <= -SpiderWebWings.MIN_SINK + 1.0e-6, "but he always sinks");
		// turns are banked in, not snapped
		Vec3 turn = SpiderWebWings.step(v, 90f, 0f);
		double heading = Math.toDegrees(Math.atan2(-turn.x, turn.z));
		helper.assertTrue(Math.abs(heading - SpiderWebWings.TURN_DEG) < 0.5, "a hard turn banks " + SpiderWebWings.TURN_DEG
				+ " degrees a tick (" + heading + ")");
		helper.assertTrue(SpiderWebWings.bank(30f) == 1f && SpiderWebWings.bank(-3.5f) == -0.5f, "bank amount");
		helper.succeed();
	}
}
