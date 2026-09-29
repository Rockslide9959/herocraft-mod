package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Moon Knight Phase 1 (v0.13.19): the pact, the lunar power table, Vengeance gains / drain, the Fracture and the
 * Resurrection charge. Mock players are not reliably ticked, so the once-a-second upkeep is driven directly.
 */
public class MoonKnightGameTests implements FabricGameTest {
	private static ServerPlayer knight(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		MoonKnight.grant(p, false);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pactGrantsAndRevokes(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnightState s = MoonKnight.state(p);
		helper.assertTrue(s.hasPact, "the pact is sealed");
		helper.assertTrue(HeroTiers.holdsHero(p, MoonKnight.KEY), "Moon Knight counts as a Hero-Tier power");
		helper.assertTrue(Math.abs(s.vengeance - MoonKnightConfig.VENGEANCE_START) < 0.01f, "a normal pact starts at 50 Vengeance");
		helper.assertTrue(s.resurrectionCharged, "Khonshu's Resurrection starts charged");
		helper.assertFalse(s.transformed, "nobody starts suited");
		helper.assertFalse(MoonKnight.grant(p), "a second grant is refused");
		MoonKnight.revoke(p);
		helper.assertFalse(MoonKnight.hasPower(p), "revoke releases the pact");

		ServerPlayer full = helper.makeMockServerPlayerInLevel();
		MoonKnight.grant(full, true);
		helper.assertTrue(Math.abs(MoonKnight.state(full).vengeance - MoonKnightConfig.VENGEANCE_START_FULL_MOON) < 0.01f,
				"a pact sealed under a full moon starts at 100");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lunarPowerFollowsTheMoon(GameTestHelper helper) {
		helper.assertTrue(MoonKnightLunar.byPhase(0) == 1.5f, "full moon = 1.5");
		helper.assertTrue(MoonKnightLunar.byPhase(4) == 0.8f, "new moon = 0.8");
		helper.assertTrue(MoonKnightLunar.byPhase(1) == MoonKnightLunar.byPhase(7), "waning and waxing gibbous match");
		helper.assertTrue(MoonKnightLunar.byPhase(1) > MoonKnightLunar.byPhase(2) && MoonKnightLunar.byPhase(2) > MoonKnightLunar.byPhase(3)
				&& MoonKnightLunar.byPhase(3) > MoonKnightLunar.byPhase(4), "it shrinks smoothly toward the new moon");
		helper.assertTrue(MoonKnightLunar.byPhase(1) <= 1.3f && MoonKnightLunar.byPhase(3) >= 1.0f, "other nights sit between 1.3 and 1.0");
		helper.assertTrue(MoonKnightLunar.cooldown(100, 1.5f) < 100 && MoonKnightLunar.cooldown(100, 0.7f) > 100,
				"cooldowns are divided by the lunar power");
		float here = MoonKnightLunar.power(helper.getLevel(), helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)));
		helper.assertTrue(here >= MoonKnightConfig.LUNAR_MIN && here <= MoonKnightConfig.LUNAR_FULL_MOON, "live power is in range");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void protectorKillsFeedVengeance(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnight.setVengeance(p, 20.0f);
		Husk hunter = helper.spawn(EntityType.HUSK, 3, 2, 3);
		Villager prey = helper.spawn(EntityType.VILLAGER, 1, 2, 1);
		hunter.setTarget(prey);
		MoonKnight.onEntityKilled(hunter, helper.getLevel().damageSources().playerAttack(p));
		helper.assertTrue(Math.abs(MoonKnight.state(p).vengeance - 25.0f) < 0.01f, "killing a mob hunting a villager gives +5");

		Husk loner = helper.spawn(EntityType.HUSK, 4, 2, 4);
		MoonKnight.onEntityKilled(loner, helper.getLevel().damageSources().playerAttack(p));
		float v = MoonKnight.state(p).vengeance;
		helper.assertTrue(Math.abs(v - 26.0f) < 0.01f || Math.abs(v - 27.0f) < 0.01f, "any other hostile kill gives +1 by day / +2 at night");

		Villager bystander = helper.spawn(EntityType.VILLAGER, 5, 2, 5);
		MoonKnight.onEntityKilled(bystander, helper.getLevel().damageSources().playerAttack(p));
		helper.assertTrue(MoonKnight.state(p).vengeance == v, "killing a non-hostile gives nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vengeanceDrainsAfterTwoIdleDays(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		long now = p.level().getGameTime();
		MoonKnightState c = MoonKnight.state(p).copy();
		c.vengeance = 10.0f;
		c.lastHostileKill = now - 10L;
		p.setAttached(ModAttachments.MOON_KNIGHT_STATE, c);
		MoonKnight.tickSecond(p);
		helper.assertTrue(MoonKnight.state(p).vengeance == 10.0f, "no drain while the last kill is recent");
		c = MoonKnight.state(p).copy();
		c.lastHostileKill = now - MoonKnightConfig.VENGEANCE_IDLE_BEFORE_DRAIN - 20L;
		p.setAttached(ModAttachments.MOON_KNIGHT_STATE, c);
		MoonKnight.tickSecond(p);
		float after = MoonKnight.state(p).vengeance;
		helper.assertTrue(after < 10.0f && after > 9.9f, "after two idle days it drains slowly (" + after + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void emptyVengeanceFracturesOnlyWhileTransformedAndRarely(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnight.setVengeance(p, 0.0f);
		helper.assertFalse(MoonKnight.fractured(p), "no Fracture while not transformed");

		MoonKnight.setVengeance(p, 5.0f);
		MoonKnight.setTransformedForTesting(p, true);
		int before = MoonKnight.state(p).alter;
		MoonKnight.setVengeance(p, 0.0f);
		helper.assertTrue(MoonKnight.fractured(p), "hitting 0 while transformed fractures the mind");
		helper.assertTrue(MoonKnight.state(p).alter != before, "the Fracture forces a different alter");
		helper.assertTrue(MoonKnight.state(p).fractureReturnAlter == before, "and remembers who to come back to");

		// it wears off
		MoonKnightState c = MoonKnight.state(p).copy();
		c.fractureUntil = p.level().getGameTime() - 1L;
		p.setAttached(ModAttachments.MOON_KNIGHT_STATE, c);
		MoonKnight.tickSecond(p);
		helper.assertTrue(MoonKnight.state(p).alter == before && !MoonKnight.fractured(p), "the original alter returns");

		// and it is rare: hitting 0 again straight away does nothing
		MoonKnight.setVengeance(p, 5.0f);
		MoonKnight.setVengeance(p, 0.0f);
		helper.assertFalse(MoonKnight.fractured(p), "no second Fracture within ten minutes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void resurrectionOnlyRechargesOnALaterFullMoon(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnightState c = MoonKnight.state(p).copy();
		c.resurrectionCharged = false;
		c.resurrectionCycle = MoonKnightLunar.moonCycle(p.level());
		p.setAttached(ModAttachments.MOON_KNIGHT_STATE, c);
		MoonKnight.tickSecond(p);
		helper.assertFalse(MoonKnight.state(p).resurrectionCharged, "never within the same moon cycle it was spent in");
		helper.succeed();
	}
	/** Phase 2: the suit stows the player's own armour and hands it back exactly as it was. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void suitStowsAndReturnsArmour(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		net.minecraft.world.item.ItemStack chest = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
		chest.setDamageValue(17);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
		MoonKnight.setTransformedForTesting(p, true);
		helper.assertTrue(MoonKnight.isTransformed(p), "transformed");
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
				instanceof com.projecthero.mod.moonknight.item.MoonKnightArmorItem, "the suit is on");
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()
				instanceof com.projecthero.mod.moonknight.item.MoonKnightArmorItem, "all four pieces, the cowl too");
		helper.assertFalse(p.getInventory().contains(chest), "the old chestplate is stowed, not dumped in the inventory");
		MoonKnight.setTransformedForTesting(p, false);
		net.minecraft.world.item.ItemStack back = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		helper.assertTrue(back.is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE) && back.getDamageValue() == 17,
				"the very same chestplate comes back");
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(), "and the suit is gone");
		helper.succeed();
	}

	/** Phase 2: H wraps the suit on over 1.5 s, untouchable while it forms. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void transformationTakesAMomentAndIsInvulnerable(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		com.projecthero.mod.moonknight.MoonKnightTransform.toggle(p);
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightTransform.isTransforming(p), "the bandages start");
		helper.assertFalse(MoonKnight.isTransformed(p), "not suited yet");
		helper.onEachTick(() -> com.projecthero.mod.moonknight.MoonKnightTransform.tick(p));
		helper.runAfterDelay(com.projecthero.mod.moonknight.MoonKnightConfig.TRANSFORM_TICKS + 5, () -> {
			helper.assertTrue(MoonKnight.isTransformed(p), "suited after 1.5 s");
			helper.assertFalse(com.projecthero.mod.moonknight.MoonKnightTransform.isTransforming(p), "and the wrap has ended");
			helper.succeed();
		});
	}
}
