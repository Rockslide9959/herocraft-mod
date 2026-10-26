package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.flash.FlashFx;
import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.flash.FlashSuitStyle;
import com.projecthero.mod.flash.item.FlashRingItem;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** v0.14.11: the Flash Suit and the Flash Ring (H packs the suit into the ring and lets it out again). */
public class FlashSuitGameTests implements FabricGameTest {

	private static ServerPlayer player(GameTestHelper helper, boolean speedster) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		if (speedster) {
			Power power = Powers.byKey(SuperSpeedHandlers.KEY);
			ExperimentalPowers.grant(p, power);
			ExperimentalPowers.setActive(p, power);
		}
		return p;
	}

	private static void suitUp(ServerPlayer p) {
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FlashSuit.HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FlashSuit.CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FlashSuit.LEGGINGS));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FlashSuit.BOOTS));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flashSuitRecipesExist(GameTestHelper helper) {
		for (String piece : new String[] { "helmet", "chestplate", "leggings", "boots" }) {
			var holder = helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id("flash_suit_" + piece));
			helper.assertTrue(holder.isPresent(), "a recipe for the " + piece);
			helper.assertTrue(holder.get().value().getResultItem(helper.getLevel().registryAccess()).getItem()
					instanceof com.projecthero.mod.flash.item.FlashSuitItem, "it makes the " + piece);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyASpeedsterCanWearTheFlashSuit(GameTestHelper helper) {
		ServerPlayer p = player(helper, false);
		helper.assertFalse(p.inventoryMenu.getSlot(6).mayPlace(new ItemStack(FlashSuit.CHESTPLATE)), "no chest slot for a non-speedster");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlashSuit.CHESTPLATE));
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "right-click does not put it on");
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FlashSuit.BOOTS));
		FlashRing.tick(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "a piece forced on pops off");
		ServerPlayer s = player(helper, true);
		helper.assertTrue(FlashSuit.mayWear(s), "a speedster may wear it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void hPacksTheSuitIntoTheRingAndLetsItOut(GameTestHelper helper) {
		ServerPlayer p = player(helper, true);
		suitUp(p);
		ItemStack oldHelmet = new ItemStack(Items.IRON_HELMET);
		FlashRing.toggle(p);
		helper.assertTrue(FlashRing.fx(p).dir() == FlashFx.DOWN, "H starts the suit-down");
		helper.runAfterDelay(FlashRing.SUIT_DOWN_TICKS + 1, () -> {
			FlashRing.tick(p);
			helper.assertTrue(!FlashSuit.wearsAny(p), "the suit is off");
			helper.assertTrue(FlashRingItem.pieces(FlashRing.storedRing(p)) == 4, "all four pieces are in the ring");
			helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(FlashSuit.RING), "v0.14.14: worn in the chestplate slot");
			p.setItemSlot(EquipmentSlot.HEAD, oldHelmet.copy());
			FlashRing.toggle(p);
			helper.assertTrue(FlashSuit.wearsFull(p), "H again: the whole suit is back on");
			helper.assertTrue(FlashRing.fx(p).dir() == FlashFx.UP, "with the suit-up running");
			helper.assertTrue(!FlashRing.worn(p).isEmpty() && FlashRingItem.pieces(FlashRing.worn(p)) == 0,
					"the ring stays on, empty");
			helper.assertTrue(p.getInventory().contains(oldHelmet), "the helmet it replaced went to the inventory");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFullSuitOnlySpeedsUpAModeSprint(GameTestHelper helper) {
		ServerPlayer p = player(helper, true);
		double before = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		suitUp(p);
		FlashRing.tick(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - before) < 1.0e-6,
				"v0.14.17: walking in the suit is no faster");
		p.setSprinting(true);
		FlashRing.tick(p);
		helper.assertFalse(suitBonus(p), "nor is a plain sprint");
		ExperimentalPowers.serverTick(p);
		AbilityRouter.handleInput(p, 6, true); // C: Speed Mode on
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(SuperSpeedHandlers.speedMode(p), "Speed Mode is on");
		FlashRing.tick(p);
		helper.assertTrue(suitBonus(p), "sprinting in Speed Mode: +50%");
		p.setSprinting(false);
		FlashRing.tick(p);
		helper.assertFalse(suitBonus(p), "walking in Speed Mode: no suit bonus");
		p.setSprinting(true);
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		FlashRing.tick(p);
		helper.assertFalse(suitBonus(p), "only with the full suit");
		helper.succeed();
	}

	private static boolean suitBonus(ServerPlayer p) {
		var m = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ProjectHeroMod.id("flash_suit_speed"));
		return m != null && Math.abs(m.amount() - FlashSuit.SPEED_BONUS) < 1.0e-6;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aFullRingDropsOnDeath(GameTestHelper helper) {
		ServerPlayer p = player(helper, true);
		suitUp(p);
		FlashRing.toggle(p);
		helper.runAfterDelay(FlashRing.SUIT_DOWN_TICKS + 1, () -> {
			FlashRing.tick(p);
			helper.assertTrue(FlashRing.holdsSuit(p), "precondition: the suit is in the ring");
			p.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
			helper.assertFalse(FlashRing.holdsSuit(p), "the ring comes off the dead player");
			boolean dropped = !helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					p.getBoundingBox().inflate(4.0), e -> e.getItem().is(FlashSuit.RING) && FlashRingItem.pieces(e.getItem()) == 4).isEmpty();
			helper.assertTrue(dropped, "and drops with the suit in it");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- v0.17.1: suit styles

	private static boolean allWorn(ServerPlayer p, FlashSuitStyle style) {
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack s = p.getItemBySlot(slot);
			if (!FlashSuit.isSuit(s) || FlashSuitStyle.of(s) != style) {
				return false;
			}
		}
		return true;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aFlashSuitStyleIsSavedOnThePiece(GameTestHelper helper) {
		ItemStack piece = new ItemStack(FlashSuit.CHESTPLATE);
		helper.assertTrue(FlashSuitStyle.of(piece) == FlashSuitStyle.ORIGINAL, "a crafted piece is the original suit");
		FlashSuitStyle.apply(piece, FlashSuitStyle.ZOOM);
		var reg = helper.getLevel().registryAccess();
		ItemStack back = ItemStack.parseOptional(reg, (net.minecraft.nbt.CompoundTag) piece.save(reg));
		helper.assertTrue(FlashSuitStyle.of(back) == FlashSuitStyle.ZOOM, "the style survives save / load");
		FlashSuitStyle.apply(back, FlashSuitStyle.ORIGINAL);
		helper.assertTrue(ItemStack.isSameItemSameComponents(back, new ItemStack(FlashSuit.CHESTPLATE)),
				"set back to the original it stacks with a crafted piece");
		helper.assertTrue(FlashSuitStyle.byOrdinal(42) == FlashSuitStyle.ORIGINAL && FlashSuitStyle.byId("nope") == FlashSuitStyle.ORIGINAL,
				"unknown styles fall back to the original");
		for (FlashSuitStyle s : FlashSuitStyle.values()) {
			helper.assertTrue(FlashSuitStyle.byId(s.id()) == s && FlashSuitStyle.byOrdinal(s.ordinal()) == s, "round trip " + s.id());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theStyleSurvivesTheRing(GameTestHelper helper) {
		ServerPlayer p = player(helper, true);
		suitUp(p);
		helper.assertTrue(FlashSuitStyle.select(p, FlashSuitStyle.REVERSE_FLASH, ItemStack.EMPTY), "the worn suit is restyled");
		helper.assertTrue(allWorn(p, FlashSuitStyle.REVERSE_FLASH), "every worn piece");
		FlashRing.toggle(p);
		helper.runAfterDelay(FlashRing.SUIT_DOWN_TICKS + 1, () -> {
			FlashRing.tick(p);
			helper.assertTrue(FlashRingItem.pieces(FlashRing.storedRing(p)) == 4, "precondition: packed");
			helper.assertTrue(FlashSuitStyle.ofRing(FlashRing.storedRing(p)) == FlashSuitStyle.REVERSE_FLASH, "packed with its style");
			helper.assertTrue(FlashSuitStyle.current(p) == FlashSuitStyle.REVERSE_FLASH, "the player's suit style reads the ring");
			// the menu's path, while the suit is in the ring
			com.projecthero.mod.speedforce.SpeedsterPrefs.apply(p,
					new com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload(false, -1, FlashSuitStyle.CLASSIC.ordinal()));
			helper.assertTrue(FlashSuitStyle.ofRing(FlashRing.storedRing(p)) == FlashSuitStyle.CLASSIC, "restyled inside the ring");
			FlashRing.toggle(p);
			helper.assertTrue(allWorn(p, FlashSuitStyle.CLASSIC), "it comes back out in that style");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftRightClickOnTheRingCyclesTheStyle(GameTestHelper helper) {
		ServerPlayer p = player(helper, true);
		suitUp(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlashSuit.RING));
		p.setShiftKeyDown(true);
		FlashSuitStyle expect = FlashSuitStyle.ORIGINAL;
		for (int i = 0; i < FlashSuitStyle.values().length; i++) {
			expect = expect.next();
			p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
			helper.assertTrue(allWorn(p, expect), "click " + (i + 1) + ": the worn suit is " + expect.id());
		}
		helper.assertTrue(expect == FlashSuitStyle.ORIGINAL, "it wraps round to the original");
		helper.assertTrue(p.getMainHandItem().is(FlashSuit.RING), "the ring stays in the hand (it is not put on)");
		// a ring in hand holding the suit, nothing worn: its own suit cycles
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			p.setItemSlot(slot, ItemStack.EMPTY);
		}
		ItemStack held = new ItemStack(FlashSuit.RING);
		held.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(
				java.util.List.of(new ItemStack(FlashSuit.HELMET), new ItemStack(FlashSuit.BOOTS))));
		p.setItemInHand(InteractionHand.MAIN_HAND, held);
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(FlashSuitStyle.ofRing(p.getMainHandItem()) == FlashSuitStyle.ZOOM, "the held ring's suit moves on");
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "and the ring is not put on");
		helper.succeed();
	}
}
