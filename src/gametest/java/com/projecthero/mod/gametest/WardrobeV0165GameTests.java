package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spider.item.SpiderItems;
import com.projecthero.mod.spider.item.SpiderManSkinSuitArmorItem;
import com.projecthero.mod.spider.item.SpiderSuitUpgradeRecipe;
import com.projecthero.mod.wardrobe.MannequinEntity;
import com.projecthero.mod.wardrobe.WardrobeBlock;
import com.projecthero.mod.wardrobe.WardrobeBlockEntity;
import com.projecthero.mod.wardrobe.WardrobeMod;
import com.projecthero.mod.wardrobe.WardrobePart;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5: the Wardrobe (placement facing the player, six parts broken as one, the side columns scrolling with wrap,
 * the full-suit swap, armour-stand put / take, Binding staying on, saving), the Mannequin (placed by its item, broken
 * into a Mannequin plus what it wore) and the recipes (Mannequin, Wardrobe and the six new Spider-Man suits, which keep
 * the original piece).
 */
public class WardrobeV0165GameTests implements FabricGameTest {
	private static final BlockPos ORIGIN = new BlockPos(3, 1, 3);

	private static ServerPlayer player(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE); // a floor to place on
			}
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	/** Places a Wardrobe with the real block item, the player standing south of it looking north. */
	private static BlockPos place(GameTestHelper helper, ServerPlayer p) {
		BlockPos at = helper.absolutePos(ORIGIN);
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5, 180.0f, 0.0f);
		ItemStack stack = new ItemStack(WardrobeMod.WARDROBE_ITEM);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at.below()).add(0, 0.5, 0), Direction.UP, at.below(), false);
		InteractionResult r = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(new UseOnContext(p, InteractionHand.MAIN_HAND, hit)));
		helper.assertTrue(r.consumesAction(), "the Wardrobe places");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		return at;
	}

	private static WardrobeBlockEntity wardrobe(GameTestHelper helper, BlockPos origin) {
		return (WardrobeBlockEntity) helper.getLevel().getBlockEntity(origin);
	}

	private static BlockPos part(BlockPos origin, WardrobePart part, DoubleBlockHalf half) {
		return WardrobeBlock.partPos(origin, Direction.SOUTH, part, half);
	}

	/** Right-click (empty-handed) a part of the Wardrobe at height {@code dy} above its floor. */
	private static void click(GameTestHelper helper, ServerPlayer p, BlockPos origin, WardrobePart which, double dy) {
		BlockPos pos = part(origin, which, dy >= 1.0 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
		BlockState state = helper.getLevel().getBlockState(pos);
		BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, origin.getY() + dy, pos.getZ() + 1.0), Direction.SOUTH, pos, false);
		if (p.getMainHandItem().isEmpty()) {
			state.useWithoutItem(helper.getLevel(), p, hit);
		} else {
			state.useItemOn(p.getMainHandItem(), helper.getLevel(), p, InteractionHand.MAIN_HAND, hit);
		}
	}

	private static void wear(ServerPlayer p, Item head, Item chest, Item legs, Item feet) {
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
	}

	// ---------------- placement and breaking ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void wardrobePlacesSixPartsFacingThePlayerAndBreaksAsOne(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		ServerLevel level = helper.getLevel();
		for (WardrobePart part : WardrobePart.values()) {
			for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
				BlockState s = level.getBlockState(part(origin, part, half));
				helper.assertTrue(s.is(WardrobeMod.WARDROBE) && s.getValue(WardrobeBlock.FACING) == Direction.SOUTH
						&& s.getValue(WardrobeBlock.PART) == part && s.getValue(WardrobeBlock.HALF) == half, part + " " + half + " is placed, facing the player");
			}
		}
		// the viewer's left (standing south, looking north) is west
		helper.assertTrue(part(origin, WardrobePart.LEFT, DoubleBlockHalf.LOWER).equals(origin.west()), "the left column is on the viewer's left");
		helper.assertTrue(wardrobe(helper, origin) != null, "the middle bottom block carries the contents");
		helper.assertTrue(level.getBlockEntity(part(origin, WardrobePart.LEFT, DoubleBlockHalf.UPPER)) == null, "and only that one");

		WardrobeBlockEntity w = wardrobe(helper, origin);
		w.setPiece(0, EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		w.setPiece(7, EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
		// break the top-right block: everything goes, the stored suits drop
		level.destroyBlock(part(origin, WardrobePart.RIGHT, DoubleBlockHalf.UPPER), true);
		for (WardrobePart part : WardrobePart.values()) {
			for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
				helper.assertTrue(level.getBlockState(part(origin, part, half)).isAir(), part + " " + half + " is gone");
			}
		}
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(4));
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND_CHESTPLATE)), "the stored chestplate drops");
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.GOLDEN_BOOTS)), "and the boots from suit 8");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aPlayerBreakingASideGetsTheWardrobeBack(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		ServerLevel level = helper.getLevel();
		BlockPos left = part(origin, WardrobePart.LEFT, DoubleBlockHalf.LOWER);
		BlockState state = level.getBlockState(left);
		state.getBlock().playerWillDestroy(level, left, state, p);
		level.destroyBlock(left, true, p);
		helper.assertTrue(level.getBlockState(origin).isAir(), "the whole Wardrobe comes down");
		long wardrobes = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(4), e -> e.getItem().is(WardrobeMod.WARDROBE_ITEM))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(wardrobes == 1, "exactly one Wardrobe drops (got " + wardrobes + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void creativeBreakingDropsNoWardrobeButKeepsTheSuits(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		p.setGameMode(GameType.CREATIVE);
		ServerLevel level = helper.getLevel();
		wardrobe(helper, origin).setPiece(3, EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
		BlockPos top = part(origin, WardrobePart.CENTER, DoubleBlockHalf.UPPER);
		BlockState state = level.getBlockState(top);
		state.getBlock().playerWillDestroy(level, top, state, p);
		level.removeBlock(top, false);
		helper.assertTrue(level.getBlockState(part(origin, WardrobePart.LEFT, DoubleBlockHalf.UPPER)).isAir(), "the whole Wardrobe comes down");
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(4));
		helper.assertFalse(drops.stream().anyMatch(e -> e.getItem().is(WardrobeMod.WARDROBE_ITEM)), "no Wardrobe drops in creative");
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.TURTLE_HELMET)), "but the stored suits still do");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wardrobeNeedsRoomForAllSixBlocks(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		helper.setBlock(ORIGIN.east().above(), Blocks.STONE); // where the right column's top would go
		BlockPos at = helper.absolutePos(ORIGIN);
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5, 180.0f, 0.0f);
		ItemStack stack = new ItemStack(WardrobeMod.WARDROBE_ITEM);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at.below()).add(0, 0.5, 0), Direction.UP, at.below(), false);
		((BlockItem) stack.getItem()).place(new BlockPlaceContext(new UseOnContext(p, InteractionHand.MAIN_HAND, hit)));
		helper.assertFalse(helper.getLevel().getBlockState(at).is(WardrobeMod.WARDROBE), "no room, no Wardrobe");
		helper.succeed();
	}

	// ---------------- using it ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSideColumnsScrollTheSelectionAndWrap(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		WardrobeBlockEntity w = wardrobe(helper, origin);
		helper.assertTrue(w.selected() == 0, "starts on suit 1");
		click(helper, p, origin, WardrobePart.LEFT, 1.5);
		helper.assertTrue(w.selected() == WardrobeBlockEntity.SUITS - 1, "left from the first wraps to the last");
		click(helper, p, origin, WardrobePart.RIGHT, 0.5);
		click(helper, p, origin, WardrobePart.RIGHT, 1.2);
		helper.assertTrue(w.selected() == 1, "right twice: suit 2");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		click(helper, p, origin, WardrobePart.RIGHT, 0.5);
		helper.assertTrue(w.selected() == 2, "holding an item scrolls too");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftClickSwapsTheWholeSuit(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		WardrobeBlockEntity w = wardrobe(helper, origin);
		wear(p, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
		p.setShiftKeyDown(true);
		// store: an empty slot takes the suit off you
		click(helper, p, origin, WardrobePart.CENTER, 1.0);
		for (EquipmentSlot slot : WardrobeBlockEntity.SLOTS) {
			helper.assertTrue(p.getItemBySlot(slot).isEmpty(), slot + " came off");
		}
		helper.assertTrue(w.piece(0, EquipmentSlot.HEAD).is(Items.IRON_HELMET) && w.piece(0, EquipmentSlot.FEET).is(Items.IRON_BOOTS),
				"the iron suit is in slot 1");
		// full swap: wear gold, swap -> iron back on, gold stored
		wear(p, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
		click(helper, p, origin, WardrobePart.CENTER, 1.0);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "the iron suit is back on");
		helper.assertTrue(w.piece(0, EquipmentSlot.CHEST).is(Items.GOLDEN_CHESTPLATE), "the gold one is stored");
		// equip: bare player, the stored suit goes on
		wear(p, Items.AIR, Items.AIR, Items.AIR, Items.AIR);
		click(helper, p, origin, WardrobePart.CENTER, 1.0);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(Items.GOLDEN_LEGGINGS), "wearing nothing puts the stored suit on");
		helper.assertTrue(w.suitEmpty(0), "and empties the slot");
		// a different slot holds its own suit
		click(helper, p, origin, WardrobePart.RIGHT, 0.5);
		click(helper, p, origin, WardrobePart.CENTER, 1.0);
		helper.assertTrue(w.piece(1, EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET) && w.suitEmpty(0), "stored in slot 2, slot 1 untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aCursedPieceStaysOnInASwap(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		WardrobeBlockEntity w = wardrobe(helper, origin);
		ItemStack cursed = new ItemStack(Items.LEATHER_HELMET);
		cursed.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
		p.setItemSlot(EquipmentSlot.HEAD, cursed);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		w.setPiece(0, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		p.setShiftKeyDown(true);
		click(helper, p, origin, WardrobePart.CENTER, 1.0);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "Binding keeps the helmet on");
		helper.assertTrue(w.piece(0, EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "so the stored helmet stays put");
		helper.assertTrue(w.piece(0, EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE), "the rest still swaps");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void plainClickHangsAndTakesSinglePieces(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		WardrobeBlockEntity w = wardrobe(helper, origin);
		helper.assertTrue(WardrobeBlockEntity.slotAtHeight(1.75) == EquipmentSlot.HEAD && WardrobeBlockEntity.slotAtHeight(1.2) == EquipmentSlot.CHEST
				&& WardrobeBlockEntity.slotAtHeight(0.75) == EquipmentSlot.LEGS && WardrobeBlockEntity.slotAtHeight(0.3) == EquipmentSlot.FEET,
				"click heights map onto the figure's pieces");
		// hang a helmet (clicked anywhere: a piece goes where it belongs)
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HELMET));
		click(helper, p, origin, WardrobePart.CENTER, 0.3);
		helper.assertTrue(w.piece(0, EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "the helmet hangs on the head");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "and leaves your hand");
		// swapping a piece: the old one comes back
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET));
		click(helper, p, origin, WardrobePart.CENTER, 1.7);
		helper.assertTrue(w.piece(0, EquipmentSlot.HEAD).is(Items.IRON_HELMET) && p.getMainHandItem().is(Items.DIAMOND_HELMET),
				"hanging over a piece hands the old one back");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		w.setPiece(0, EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
		click(helper, p, origin, WardrobePart.CENTER, 0.75);
		helper.assertTrue(p.getMainHandItem().is(Items.CHAINMAIL_LEGGINGS) && w.piece(0, EquipmentSlot.LEGS).isEmpty(), "empty-handed: take the clicked piece");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		click(helper, p, origin, WardrobePart.CENTER, 0.2);
		helper.assertTrue(p.getMainHandItem().is(Items.IRON_HELMET), "clicking an empty spot takes the highest piece left");
		// a non-armour item does nothing to it
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
		click(helper, p, origin, WardrobePart.CENTER, 1.2);
		helper.assertTrue(w.suitEmpty(0) && p.getMainHandItem().is(Items.BREAD), "bread is not a suit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theWardrobeSavesItsSuitsAndSelection(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		WardrobeBlockEntity w = wardrobe(helper, origin);
		w.setPiece(9, EquipmentSlot.CHEST, new ItemStack(SpiderItems.SKIN_SUIT_PIECES.get("tasm2")[1]));
		w.scroll(4);
		var tag = w.saveWithoutMetadata(helper.getLevel().registryAccess());
		WardrobeBlockEntity copy = new WardrobeBlockEntity(origin, w.getBlockState());
		copy.loadWithComponents(tag, helper.getLevel().registryAccess());
		helper.assertTrue(copy.selected() == 4, "the selection is saved");
		helper.assertTrue(copy.piece(9, EquipmentSlot.CHEST).is(SpiderItems.SKIN_SUIT_PIECES.get("tasm2")[1]), "and the suits");
		helper.assertTrue(copy.suitEmpty(0), "empty slots stay empty");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftClickSwapsEvenHoldingSomething(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos origin = place(helper, p);
		WardrobeBlockEntity w = wardrobe(helper, origin);
		wear(p, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 5));
		p.setShiftKeyDown(true);
		BlockPos pos = part(origin, WardrobePart.CENTER, DoubleBlockHalf.UPPER);
		BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, origin.getY() + 1.0, pos.getZ() + 1.0), Direction.SOUTH, pos, false);
		// v0.16.5: vanilla skips a block's use while sneaking with an item -- the UseBlockCallback does the swap
		InteractionResult r = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(r.consumesAction(), "the sneak-click is taken");
		helper.assertTrue(w.piece(0, EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
				"holding stone, the whole suit still goes in");
		helper.assertTrue(p.getMainHandItem().getCount() == 5, "the held stone is untouched");
		helper.succeed();
	}

	// ---------------- the Mannequin ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftClickPutsYourSuitOnTheMannequin(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos at = helper.absolutePos(ORIGIN);
		MannequinEntity m = WardrobeMod.MANNEQUIN.create(helper.getLevel());
		m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		helper.getLevel().addFreshEntity(m);
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 180.0f, 0.0f);
		wear(p, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		p.setShiftKeyDown(true);
		helper.assertTrue(m.interactAt(p, new Vec3(0, 1.0, 0), InteractionHand.MAIN_HAND).consumesAction(), "the sneak-click is taken");
		for (EquipmentSlot slot : WardrobeBlockEntity.SLOTS) {
			helper.assertTrue(p.getItemBySlot(slot).isEmpty() && !m.getItemBySlot(slot).isEmpty(), slot + " moved onto the Mannequin");
		}
		// again with gold on: the two suits change places
		wear(p, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
		m.interactAt(p, new Vec3(0, 1.0, 0), InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && m.getItemBySlot(EquipmentSlot.CHEST).is(Items.GOLDEN_CHESTPLATE),
				"a full swap");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mannequinPlacesAndBreaksIntoAMannequin(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		BlockPos at = helper.absolutePos(ORIGIN);
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5, 180.0f, 0.0f);
		ItemStack stack = new ItemStack(WardrobeMod.MANNEQUIN_ITEM);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at.below()).add(0, 0.5, 0), Direction.UP, at.below(), false);
		helper.assertTrue(stack.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit)).consumesAction(), "it places");
		ServerLevel level = helper.getLevel();
		List<MannequinEntity> found = level.getEntitiesOfClass(MannequinEntity.class, new AABB(at).inflate(2));
		helper.assertTrue(found.size() == 1, "one Mannequin");
		MannequinEntity m = found.get(0);
		helper.assertTrue(m.isShowArms(), "with arms");
		helper.assertTrue(stack.isEmpty(), "the item is used up");
		m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SpiderItems.SKIN_SUIT_PIECES.get("raimi")[1]));
		// two quick punches break an armour stand
		m.hurt(level.damageSources().playerAttack(p), 1.0f);
		m.hurt(level.damageSources().playerAttack(p), 1.0f);
		helper.assertTrue(m.isRemoved(), "it breaks");
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3));
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(WardrobeMod.MANNEQUIN_ITEM)), "it drops a Mannequin");
		helper.assertFalse(drops.stream().anyMatch(e -> e.getItem().is(Items.ARMOR_STAND)), "not an armour stand");
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(SpiderItems.SKIN_SUIT_PIECES.get("raimi")[1])), "and what it wore");
		helper.succeed();
	}

	// ---------------- recipes ----------------

	private static ItemStack craft(GameTestHelper helper, String id, NonNullList<ItemStack> leftOut, int[] keptIndex) {
		var holder = helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(id)).orElse(null);
		helper.assertTrue(holder != null && holder.value() instanceof CraftingRecipe, id + " is loaded");
		CraftingRecipe recipe = (CraftingRecipe) holder.value();
		var shaped = (net.minecraft.world.item.crafting.ShapedRecipe) recipe;
		List<ItemStack> grid = new ArrayList<>();
		NonNullList<Ingredient> ings = recipe.getIngredients();
		for (int i = 0; i < ings.size(); i++) {
			ItemStack[] options = ings.get(i).getItems();
			ItemStack s = options.length == 0 ? ItemStack.EMPTY : options[0].copy();
			if (keptIndex != null && SpiderSuitUpgradeRecipe.isOriginalSuitPiece(s)) {
				keptIndex[0] = i;
			}
			grid.add(s);
		}
		CraftingInput input = CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
		helper.assertTrue(recipe.matches(input, helper.getLevel()), id + ": the grid matches");
		if (leftOut != null) {
			leftOut.addAll(helper.getLevel().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, input, helper.getLevel()));
		}
		return recipe.assemble(input, helper.getLevel().registryAccess());
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mannequinAndWardrobeAreCraftable(GameTestHelper helper) {
		helper.assertTrue(craft(helper, "mannequin", null, null).is(WardrobeMod.MANNEQUIN_ITEM), "the Mannequin recipe");
		helper.assertTrue(craft(helper, "wardrobe", null, null).is(WardrobeMod.WARDROBE_ITEM), "the Wardrobe recipe");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSixNewSpiderSuitsAreCraftedFromTheOriginalWhichIsKept(GameTestHelper helper) {
		Item[] originals = { SpiderItems.SUIT_HELMET, SpiderItems.SUIT_CHESTPLATE, SpiderItems.SUIT_LEGGINGS, SpiderItems.SUIT_BOOTS };
		String[] pieces = { "helmet", "chestplate", "leggings", "boots" };
		helper.assertTrue(SpiderItems.SKIN_SUIT_PIECES.size() == 6, "six suits");
		for (String skin : SpiderItems.SKIN_SUITS) {
			SpiderManSkinSuitArmorItem[] made = SpiderItems.SKIN_SUIT_PIECES.get(skin);
			for (int k = 0; k < 4; k++) {
				String id = "spider_man_suit_" + skin + "_" + pieces[k];
				NonNullList<ItemStack> left = NonNullList.create();
				int[] kept = { -1 };
				ItemStack out = craft(helper, id, left, kept);
				helper.assertTrue(out.is(made[k]), id + " makes its piece");
				helper.assertTrue(kept[0] >= 0 && left.get(kept[0]).is(originals[k]), id + ": the original piece stays in the grid");
				helper.assertFalse(SpiderSuitUpgradeRecipe.isOriginalSuitPiece(out), id + " is not an 'original' piece itself");
				helper.assertTrue(made[k].texture().getPath().equals("textures/armor/spider_man_" + skin + ".png"), id + " has its own skin");
			}
		}
		helper.succeed();
	}
}
