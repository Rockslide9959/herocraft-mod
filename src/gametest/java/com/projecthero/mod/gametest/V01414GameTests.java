package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.flash.SpeedForce;
import com.projecthero.mod.flash.item.FlashRingItem;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.SupermanSuit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

/** v0.14.14: the Flash Ring as a chestplate-slot item, and the Superman Suit mending in the sun. */
public class V01414GameTests implements FabricGameTest {

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	private static ItemStack fullRing() {
		ItemStack ring = new ItemStack(FlashSuit.RING);
		ring.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(FlashSuit.CHESTPLATE),
				new ItemStack(FlashSuit.LEGGINGS))));
		return ring;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFlashRingIsAChestplateSlotItem(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		SpeedForce.grant(p);
		helper.assertTrue(p.getEquipmentSlotForItem(fullRing()) == EquipmentSlot.CHEST, "the ring equips into the chest slot");
		helper.assertTrue(FlashRing.putOn(p, fullRing()), "right-click puts it on");
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(FlashSuit.RING) && FlashRing.holdsSuit(p),
				"into the chestplate slot, holding the suit");
		FlashRing.toggle(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(FlashSuit.CHESTPLATE), "H: the suit chestplate takes the slot");
		helper.assertTrue(!FlashRing.worn(p).isEmpty() && FlashRingItem.pieces(FlashRing.worn(p)) == 0, "the empty ring rides on the finger");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anOldFingerRingMovesIntoTheChestSlot(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		SpeedForce.grant(p);
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.FLASH_RING, fullRing());
		FlashRing.tick(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(FlashSuit.RING), "a pre-0.14.14 full ring moves to the chest slot");
		helper.assertTrue(FlashRing.worn(p).isEmpty(), "and leaves the finger");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aDroppedFullRingNeverDespawns(GameTestHelper helper) {
		ItemEntity item = new ItemEntity(helper.getLevel(), helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)).x,
				helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)).y, helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)).z,
				fullRing());
		helper.getLevel().addFreshEntity(item);
		helper.assertTrue(item.getAge() == -32768, "a ring holding the suit has an unlimited lifetime");
		item.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSupermanSuitMendsInTheSun(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		Kryptonian.grant(p);
		ItemStack chest = new ItemStack(SupermanSuit.CHESTPLATE);
		chest.setDamageValue(30);
		p.setItemSlot(EquipmentSlot.CHEST, chest);
		for (int i = 0; i < 4; i++) {
			SupermanSuit.sunRepair(p);
		}
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 30 - 4 * SupermanSuit.SUN_REPAIR_AMOUNT,
				"one point a step, got " + p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue());
		helper.succeed();
	}
}
