package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** v0.14.30: flat flight drains, the Mark 5 Suitcase unfolding onto a Suit Platform, and no hand-equipping Iron Man armour. */
public class IronManV01430GameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void flatFlightDrainsMatchTheSpec(GameTestHelper h) {
		h.assertTrue(IronManSuits.byId("mark_2").flatFlightDrainPerSecond() == 2f, "Mark 2 flight 2/s");
		h.assertTrue(IronManSuits.byId("mark_iii").flatFlightDrainPerSecond() == 2f, "Mark 3 flight 2/s");
		h.assertTrue(IronManSuits.byId("mark_4").flatFlightDrainPerSecond() == 1f, "Mark 4 flight 1/s");
		h.assertTrue(IronManSuits.byId("mark_v").flatFlightDrainPerSecond() == 1f, "Mark 5 flight 1/s");
		h.assertTrue(IronManSuits.byId("mark_6").flatFlightDrainPerSecond() == 0f, "Mark 6 keeps the tiered drain");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markFiveSuitcaseUnfoldsOntoAPlatform(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		TonyStark.grant(p);
		BlockPos rel = new BlockPos(2, 1, 2);
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
		ItemStack caseStack = new ItemStack(IronManItems.MARK_V_SUITCASE); // a never-used case unfolds a fresh suit
		h.assertTrue(be.storeSuitcase(p, caseStack), "the case unfolds onto the empty platform");
		h.assertTrue(caseStack.isEmpty(), "the case is used up");
		for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
			h.assertTrue(be.holds("mark_v", t), "platform holds the Mark 5 " + t);
		}
		ItemStack second = new ItemStack(IronManItems.MARK_V_SUITCASE);
		h.assertFalse(be.storeSuitcase(p, second), "a full platform refuses a second case");
		h.assertTrue(second.getCount() == 1, "and the refused case is untouched");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManArmourCannotBeEquippedByHand(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		ItemStack helmet = new ItemStack(IronManItems.armor("mark_iii", ArmorItem.Type.HELMET));
		// inventory armour slots 5..8 (helmet .. boots) refuse it -- drag, shift-click and hot-key swap all check mayPlace
		h.assertFalse(p.inventoryMenu.getSlot(5).mayPlace(helmet), "the helmet slot refuses an Iron Man helmet");
		h.assertTrue(p.inventoryMenu.getSlot(5).mayPlace(new ItemStack(net.minecraft.world.item.Items.IRON_HELMET)),
				"ordinary armour still goes in");
		// right-click use does nothing
		p.setItemInHand(InteractionHand.MAIN_HAND, helmet);
		helmet.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
		h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "right-click must not strap the helmet on");
		h.assertTrue(p.getMainHandItem().getItem() == IronManItems.armor("mark_iii", ArmorItem.Type.HELMET), "the helmet stays in hand");
		h.succeed();
	}

	/** v0.14.31: Iron Man armour can't be taken off by hand in survival either. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManArmourCannotBeRemovedByHand(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IronManItems.armor("mark_iii", ArmorItem.Type.HELMET)));
		h.assertFalse(p.inventoryMenu.getSlot(5).mayPickup(p), "a worn Iron Man helmet can't be picked out of its slot");
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
		h.assertTrue(p.inventoryMenu.getSlot(5).mayPickup(p), "ordinary armour still comes off");
		h.succeed();
	}

	/** v0.14.31: gunfire does nothing to an Iron Man wearer; every mark totals 70% knockback resistance. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManArmourIsBulletproofAndResistsKnockback(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
			p.setItemSlot(com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor("mark_iii", t)));
		}
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(p, "mark_iii", 2000f);
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(p, "mark_iii", 1000f);
		p.invulnerableTime = 0;
		float hp = p.getHealth();
		ServerPlayer shooter = h.makeMockServerPlayerInLevel();
		com.projecthero.mod.firearm.Gunfire.hit(() -> p.hurt(p.damageSources().playerAttack(shooter), 12f));
		h.assertTrue(p.getHealth() == hp, "a bullet must not hurt an Iron Man wearer");
		h.assertTrue(com.projecthero.mod.ironman.IronManEnergy.integrity(p, "mark_iii") == 1000f, "nor drain integrity");
		for (var suit : IronManSuits.all()) {
			float kb = 0f;
			for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
				kb += ((ArmorItem) IronManItems.armor(suit.id(), t)).getMaterial().value().knockbackResistance();
			}
			h.assertTrue(Math.abs(kb - 0.7f) < 1.0e-4f, suit.id() + " full-suit knockback resistance must be 70%, got " + kb);
		}
		h.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilities.REPULSOR_RANGE == 50.0, "repulsors reach 50 blocks");
		h.succeed();
	}
}
