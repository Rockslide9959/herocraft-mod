package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.flash.FlashFx;
import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;
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
}
