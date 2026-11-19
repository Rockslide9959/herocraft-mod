package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.backpack.BackpackItem;
import com.projecthero.mod.backpack.BackpackMenu;
import com.projecthero.mod.backpack.BackpackMod;
import com.projecthero.mod.backpack.BackpackTier;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 backpacks: tier sizes, contents living on the item (and surviving a save round trip and a drop), upgrades keeping
 * contents and dye, nesting blocked, the dupe guard (the menu goes dead the moment the backpack leaves its slot, and nothing can
 * be taken out after) and the Netherite tier shrugging off lava while a Leather one spills its contents.
 */
public class BackpackGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static BackpackMenu open(ServerPlayer p, int invSlot) {
		ItemStack bp = p.getInventory().getItem(invSlot);
		return new BackpackMenu(1, p.getInventory(), invSlot, BackpackItem.tierOf(bp), bp);
	}

	private static int count(List<ItemStack> items, net.minecraft.world.item.Item item) {
		return items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tierSizes(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		int[] want = { 27, 36, 45, 54, 54 };
		for (BackpackTier t : BackpackTier.values()) {
			helper.assertTrue(t.slots() == want[t.ordinal()], t + " has " + want[t.ordinal()] + " slots, got " + t.slots());
			p.getInventory().setItem(4, new ItemStack(BackpackMod.forTier(t)));
			BackpackMenu menu = open(p, 4);
			helper.assertTrue(menu.slots.size() == t.slots() + 36, t + " menu: its slots plus the 36 inventory slots");
			helper.assertTrue(BackpackItem.readContents(p.getInventory().getItem(4)).size() == t.slots(), t + " reads as " + t.slots() + " stacks");
		}
		helper.assertTrue(new ItemStack(BackpackMod.NETHERITE).has(DataComponents.FIRE_RESISTANT), "Netherite is fire resistant");
		helper.assertFalse(new ItemStack(BackpackMod.DIAMOND).has(DataComponents.FIRE_RESISTANT), "Diamond is not");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void contentsPersistOnItem(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		p.getInventory().setItem(2, new ItemStack(BackpackMod.IRON));
		BackpackMenu menu = open(p, 2);
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 12));
		menu.getSlot(35).set(new ItemStack(Items.COBBLESTONE, 64));
		ItemStack bp = p.getInventory().getItem(2);
		List<ItemStack> in = BackpackItem.readContents(bp);
		helper.assertTrue(in.get(0).is(Items.DIAMOND) && in.get(0).getCount() == 12, "slot 0 written to the item");
		helper.assertTrue(in.get(35).is(Items.COBBLESTONE) && in.get(35).getCount() == 64, "last slot written to the item");
		helper.assertTrue(BackpackItem.usedSlots(bp) == 2, "two slots used");
		// save + load round trip (a chest, the player file, a dropped item entity all store the stack this way)
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag tag = ItemStack.CODEC.encodeStart(ops, bp).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, tag).getOrThrow();
		List<ItemStack> out = BackpackItem.readContents(loaded);
		helper.assertTrue(count(out, Items.DIAMOND) == 12 && count(out, Items.COBBLESTONE) == 64, "contents survive a save round trip");
		// a fresh menu on the same stack sees them
		BackpackMenu again = open(p, 2);
		helper.assertTrue(again.getSlot(0).getItem().getCount() == 12, "reopening shows the contents");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void upgradeKeepsContents(GameTestHelper helper) {
		var level = helper.getLevel();
		ItemStack leather = new ItemStack(BackpackMod.LEATHER);
		BackpackItem.writeContents(leather, List.of(new ItemStack(Items.GOLDEN_APPLE, 3), new ItemStack(Items.TORCH, 40)));
		leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC, true));
		ItemStack ing = new ItemStack(Items.IRON_INGOT);
		CraftingInput grid = CraftingInput.of(3, 3, List.of(ing, ing, ing, ing, leather, ing, ing, ing, ing));
		ItemStack iron = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).map(r -> r.value().assemble(grid, level.registryAccess()))
				.orElse(ItemStack.EMPTY);
		helper.assertTrue(iron.is(BackpackMod.IRON), "eight iron ingots round a Leather Backpack craft an Iron Backpack");
		List<ItemStack> c = BackpackItem.readContents(iron);
		helper.assertTrue(c.size() == 36 && count(c, Items.GOLDEN_APPLE) == 3 && count(c, Items.TORCH) == 40, "the upgrade keeps the contents");
		helper.assertTrue(BackpackItem.color(iron) == 0x3366CC, "and the dye");

		ItemStack diamond = new ItemStack(BackpackMod.DIAMOND);
		BackpackItem.writeContents(diamond, List.of(new ItemStack(Items.NETHERITE_SCRAP, 2)));
		SmithingRecipeInput smith = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), diamond,
				new ItemStack(Items.NETHERITE_INGOT));
		ItemStack neth = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, smith, level).map(r -> r.value().assemble(smith, level.registryAccess()))
				.orElse(ItemStack.EMPTY);
		helper.assertTrue(neth.is(BackpackMod.NETHERITE), "smithing makes the Netherite Backpack");
		helper.assertTrue(count(BackpackItem.readContents(neth), Items.NETHERITE_SCRAP) == 2, "smithing keeps the contents");

		ItemStack l = new ItemStack(Items.LEATHER);
		CraftingInput base = CraftingInput.of(3, 3, List.of(l, new ItemStack(Items.STRING), l, l, new ItemStack(Items.CHEST), l, l, l, l));
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, base, level).map(r -> r.value().assemble(base, level.registryAccess()))
				.filter(s -> s.is(BackpackMod.LEATHER)).isPresent(), "leather, string and a chest craft the Leather Backpack");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void nestingBlocked(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		p.getInventory().setItem(0, new ItemStack(BackpackMod.GOLD));
		p.getInventory().setItem(9, new ItemStack(BackpackMod.LEATHER));
		p.getInventory().setItem(10, new ItemStack(Items.SHULKER_BOX));
		p.getInventory().setItem(11, new ItemStack(Items.DIRT, 16));
		BackpackMenu menu = open(p, 0);
		var slot = menu.getSlot(0);
		helper.assertFalse(slot.mayPlace(new ItemStack(BackpackMod.LEATHER)), "no backpack in a backpack");
		helper.assertFalse(slot.mayPlace(new ItemStack(Items.RED_SHULKER_BOX)), "no shulker box in a backpack");
		helper.assertTrue(slot.mayPlace(new ItemStack(Items.BUNDLE)), "a bundle is fine");
		int size = BackpackTier.GOLD.slots();
		// shift-click from the inventory rows (inventory 9 is the first slot after the backpack's own)
		menu.quickMoveStack(p, size);
		menu.quickMoveStack(p, size + 1);
		menu.quickMoveStack(p, size + 2);
		List<ItemStack> c = BackpackItem.readContents(p.getInventory().getItem(0));
		helper.assertTrue(count(c, BackpackMod.LEATHER) == 0 && p.getInventory().getItem(9).is(BackpackMod.LEATHER), "shift-click refuses a backpack");
		helper.assertTrue(count(c, Items.SHULKER_BOX) == 0 && p.getInventory().getItem(10).is(Items.SHULKER_BOX), "shift-click refuses a shulker box");
		helper.assertTrue(count(c, Items.DIRT) == 16 && p.getInventory().getItem(11).isEmpty(), "shift-click moves dirt in");
		helper.assertFalse(new ItemStack(BackpackMod.IRON).getItem().canFitInsideContainerItems(), "a backpack refuses shulker boxes / bundles too");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void dupeGuard(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		p.getInventory().selected = 0;
		p.getInventory().setItem(3, new ItemStack(BackpackMod.LEATHER));
		BackpackMenu menu = open(p, 3);
		p.containerMenu = menu;
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 10));
		int size = BackpackTier.LEATHER.slots();
		int locked = size + 27 + 3; // hotbar slot 3

		// the backpack's own slot is frozen: no Q, no pick-up, no shift-click, no number-key swap
		menu.clicked(locked, 0, ClickType.THROW, p);
		menu.clicked(locked, 0, ClickType.PICKUP, p);
		menu.clicked(locked, 0, ClickType.QUICK_MOVE, p);
		menu.clicked(size + 27 + 5, 3, ClickType.SWAP, p); // hotbar key 4 over another slot
		helper.assertTrue(p.getInventory().getItem(3).is(BackpackMod.LEATHER) && menu.getCarried().isEmpty(), "the open backpack never leaves its slot");
		helper.assertTrue(menu.stillValid(p), "still open");

		// now it leaves by other means (dropped from outside the menu, a command, death...)
		ItemStack bp = p.getInventory().removeItemNoUpdate(3);
		ItemEntity dropped = p.drop(bp, false, false);
		helper.assertTrue(dropped != null, "dropped");
		helper.assertFalse(menu.stillValid(p), "the menu goes dead the moment the backpack moves (vanilla then closes it)");
		helper.assertFalse(menu.getSlot(0).mayPickup(p), "dead slots give nothing");
		menu.clicked(0, 0, ClickType.PICKUP, p);
		ItemStack moved = menu.quickMoveStack(p, 0);
		menu.clicked(0, 0, ClickType.THROW, p);
		helper.assertTrue(menu.getCarried().isEmpty() && moved.isEmpty(), "no pick-up or shift-click from a dead menu");
		int inInventory = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(Items.DIAMOND)) {
				inInventory += p.getInventory().getItem(i).getCount();
			}
		}
		int onGround = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(4), e -> e.getItem().is(Items.DIAMOND))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
		int inPack = count(BackpackItem.readContents(dropped.getItem()), Items.DIAMOND);
		helper.assertTrue(inInventory == 0 && onGround == 0 && inPack == 10, "10 diamonds in the dropped backpack and nowhere else (inv " + inInventory
				+ ", ground " + onGround + ", pack " + inPack + ")");
		// writing into the dead menu changes nothing on the dropped stack
		menu.getSlot(1).set(new ItemStack(Items.EMERALD, 5));
		menu.broadcastChanges();
		helper.assertTrue(count(BackpackItem.readContents(dropped.getItem()), Items.EMERALD) == 0, "a dead menu never writes to the moved backpack");
		p.containerMenu = p.inventoryMenu;
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void netheriteFireproofLeatherSpills(GameTestHelper helper) {
		var level = helper.getLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		ItemStack nethStack = new ItemStack(BackpackMod.NETHERITE);
		BackpackItem.writeContents(nethStack, List.of(new ItemStack(Items.DIAMOND, 7)));
		ItemEntity neth = new ItemEntity(level, at.x, at.y, at.z, nethStack);
		level.addFreshEntity(neth);
		neth.hurt(level.damageSources().lava(), 100f);
		neth.hurt(level.damageSources().inFire(), 100f);
		helper.assertTrue(neth.isAlive(), "the Netherite Backpack survives lava and fire");
		helper.assertTrue(count(BackpackItem.readContents(neth.getItem()), Items.DIAMOND) == 7, "with its contents");

		Vec3 at2 = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
		ItemStack leatherStack = new ItemStack(BackpackMod.LEATHER);
		BackpackItem.writeContents(leatherStack, List.of(new ItemStack(Items.EMERALD, 9)));
		ItemEntity leather = new ItemEntity(level, at2.x, at2.y, at2.z, leatherStack);
		level.addFreshEntity(leather);
		leather.hurt(level.damageSources().lava(), 100f);
		helper.assertFalse(leather.isAlive(), "a Leather Backpack burns");
		int spilled = level.getEntitiesOfClass(ItemEntity.class, new AABB(at2, at2).inflate(2), e -> e.isAlive() && e.getItem().is(Items.EMERALD))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(spilled == 9, "and spills what it held, got " + spilled);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wornIsBestCarriedNotHeld(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		p.getInventory().selected = 0;
		p.getInventory().setItem(0, new ItemStack(BackpackMod.DIAMOND)); // in the hand: drawn there, not on the back
		p.getInventory().setItem(12, new ItemStack(BackpackMod.LEATHER));
		ItemStack gold = new ItemStack(BackpackMod.GOLD);
		BackpackItem.writeContents(gold, List.of(new ItemStack(Items.DIRT)));
		p.getInventory().setItem(20, gold);
		BackpackMod.updateWorn(p);
		ItemStack worn = p.getAttached(BackpackMod.WORN);
		helper.assertTrue(worn != null && worn.is(BackpackMod.GOLD), "the best backpack not in a hand is worn");
		helper.assertFalse(worn.has(DataComponents.CONTAINER), "the synced copy carries no contents");
		helper.assertTrue(BackpackMod.bestSlot(p, true) == 0, "the Open Backpack fallback can use the held one");
		p.getInventory().setItem(20, ItemStack.EMPTY);
		p.getInventory().setItem(12, ItemStack.EMPTY);
		BackpackMod.updateWorn(p);
		helper.assertTrue(p.getAttached(BackpackMod.WORN) == null, "nothing worn when the only one is held");
		helper.succeed();
	}
}
