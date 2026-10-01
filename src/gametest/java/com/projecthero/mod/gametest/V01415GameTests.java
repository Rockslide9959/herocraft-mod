package com.projecthero.mod.gametest;

import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianConfig;
import com.projecthero.mod.thorarmor.ThorArmorItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/** v0.14.15: the Kryptonian body brought up to Thor's, and Thor's Armour at diamond level. */
public class V01415GameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void kryptonianMatchesThorsBody(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Kryptonian.grant(p);
		Kryptonian.reconcile(p);
		helper.assertTrue(KryptonianConfig.DAMAGE_REDUCTION == 0.80f
				&& Math.abs(Kryptonian.damageTakenFactor(p) - com.projecthero.mod.power.ThorPassives.DAMAGE_TAKEN_FACTOR) < 1.0e-6,
				"80% damage reduction, the same as Thor");
		var regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.isInfiniteDuration() && regen.getAmplifier() == 0, "permanent Regeneration I");
		float before = p.getHealth();
		p.hurt(helper.getLevel().damageSources().lightningBolt(), 5.0f);
		helper.assertTrue(p.getHealth() >= before, "lightning cannot hurt a Kryptonian");
		Kryptonian.revoke(p);
		helper.assertTrue(p.getEffect(MobEffects.REGENERATION) == null, "the regeneration goes with the power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mendingTheWornRingNeverReEquipsIt(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		com.projecthero.mod.flash.SpeedForce.grant(p);
		net.minecraft.world.item.ItemStack chest = new net.minecraft.world.item.ItemStack(com.projecthero.mod.flash.FlashSuit.CHESTPLATE);
		chest.setDamageValue(10);
		net.minecraft.world.item.ItemStack ring = new net.minecraft.world.item.ItemStack(com.projecthero.mod.flash.FlashSuit.RING);
		ring.set(net.minecraft.core.component.DataComponents.CONTAINER,
				net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(chest)));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ring);
		var worn = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		com.projecthero.mod.flash.FlashRing.repairStored(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST) == worn,
				"the same stack stays in the slot (no setItemSlot = no equip sound)");
		var stored = worn.get(net.minecraft.core.component.DataComponents.CONTAINER).nonEmptyItemsCopy().iterator().next();
		helper.assertTrue(stored.getDamageValue() == 9, "and the suit inside is mended");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thorsArmourIsAFullDiamondSet(GameTestHelper helper) {
		int defense = ThorArmorItems.CHESTPLATE.getDefense() + ThorArmorItems.LEGGINGS.getDefense() + ThorArmorItems.BOOTS.getDefense();
		float toughness = ThorArmorItems.CHESTPLATE.getToughness() + ThorArmorItems.LEGGINGS.getToughness() + ThorArmorItems.BOOTS.getToughness();
		helper.assertTrue(defense == 20, "20 armour like full diamond, got " + defense);
		helper.assertTrue(Math.abs(toughness - 8.0f) < 1.0e-3, "8 toughness like full diamond, got " + toughness);
		helper.assertTrue(ThorArmorItems.CHESTPLATE.getDefaultInstance().getMaxDamage() == net.minecraft.world.item.Items.DIAMOND_CHESTPLATE
				.getDefaultInstance().getMaxDamage(), "diamond durability");
		helper.succeed();
	}
}
