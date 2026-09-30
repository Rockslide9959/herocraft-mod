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
		// v0.14.4: exactly three states
		helper.assertTrue(MoonKnightLunar.resolve(true, true, 0).power() == 1.5f, "full moon = 1.5");
		helper.assertTrue(MoonKnightLunar.resolve(true, true, 4).power() == 1.0f, "any other night = 1.0");
		helper.assertTrue(MoonKnightLunar.resolve(true, false, 0).power() == 0.7f, "day = 0.7");
		helper.assertTrue(MoonKnightLunar.cooldown(100, 1.5f) < 100 && MoonKnightLunar.cooldown(100, 0.7f) > 100,
				"cooldowns are divided by the lunar power");
		float here = MoonKnightLunar.power(helper.getLevel(), helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)));
		helper.assertTrue(here == MoonKnightConfig.LUNAR_DAY || here == MoonKnightConfig.LUNAR_NIGHT || here == MoonKnightConfig.LUNAR_FULL_MOON,
				"live power is one of the three states (" + here + ")");
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
		helper.assertTrue(Math.abs(MoonKnight.state(p).vengeance - 26.0f) < 0.01f, "killing a mob hunting a villager gives +6 (v0.14.4)");

		Husk loner = helper.spawn(EntityType.HUSK, 4, 2, 4);
		MoonKnight.onEntityKilled(loner, helper.getLevel().damageSources().playerAttack(p));
		float v = MoonKnight.state(p).vengeance;
		boolean night = MoonKnightLunar.isMoonNight(p.level());
		helper.assertTrue(Math.abs(v - (night ? 29.0f : 28.0f)) < 0.01f, "any other hostile kill gives +2 by day / +3 at night (" + v + ")");

		Villager bystander = helper.spawn(EntityType.VILLAGER, 5, 2, 5);
		MoonKnight.onEntityKilled(bystander, helper.getLevel().damageSources().playerAttack(p));
		helper.assertTrue(MoonKnight.state(p).vengeance == v, "killing a non-hostile gives nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vengeanceRegeneratesOutOfCombat(GameTestHelper helper) {
		// v0.14.4: the two-idle-days drain became a 0.5%/s regen while out of combat (5 s without dealing / taking damage)
		ServerPlayer p = knight(helper);
		MoonKnightState c = MoonKnight.state(p).copy();
		c.vengeance = 10.0f;
		c.lastHostileKill = p.level().getGameTime() - 10L * MoonKnightConfig.DAY_TICKS;
		p.setAttached(ModAttachments.MOON_KNIGHT_STATE, c);
		MoonKnight.markCombat(p);
		helper.assertTrue(MoonKnight.inCombat(p), "a hit puts him in combat");
		MoonKnight.tickSecond(p);
		helper.assertTrue(MoonKnight.state(p).vengeance == 10.0f, "no regen in combat, and no drain however long since a kill");
		MoonKnight.clearCombat(p);
		MoonKnight.tickSecond(p);
		float after = MoonKnight.state(p).vengeance;
		helper.assertTrue(Math.abs(after - 10.5f) < 1.0e-3f, "out of combat: +0.5 (0.5% of 100) a second (" + after + ")");
		MoonKnight.setVengeance(p, 100.0f);
		MoonKnight.tickSecond(p);
		helper.assertTrue(MoonKnight.state(p).vengeance == 100.0f, "never past the top");
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
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void transformationTakesAMomentAndIsInvulnerable(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		com.projecthero.mod.moonknight.MoonKnightTransform.toggle(p);
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightTransform.isTransforming(p), "the bandages start");
		helper.assertFalse(MoonKnight.isTransformed(p), "not suited yet");
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightSuit.wearing(p), "the suit is already on, materialising pixel by pixel");
		helper.onEachTick(() -> com.projecthero.mod.moonknight.MoonKnightTransform.tick(p));
		helper.runAfterDelay(com.projecthero.mod.moonknight.MoonKnightConfig.TRANSFORM_TICKS + 5, () -> {
			helper.assertTrue(MoonKnight.isTransformed(p), "suited after 1.5 s");
			helper.assertFalse(com.projecthero.mod.moonknight.MoonKnightTransform.isTransforming(p), "and the wrap has ended");
			// past the H anti-spam window
			com.projecthero.mod.moonknight.data.MoonKnightState st = MoonKnight.state(p).copy();
			st.transformStart -= 40;
			p.setAttached(com.projecthero.mod.attachment.ModAttachments.MOON_KNIGHT_STATE, st);
			com.projecthero.mod.moonknight.MoonKnightTransform.toggle(p);
			helper.assertFalse(MoonKnight.isTransformed(p), "H again: abilities off at once");
			helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightSuit.wearing(p), "while the suit dissolves away");
			helper.runAfterDelay(com.projecthero.mod.moonknight.MoonKnightConfig.UNTRANSFORM_TICKS + 3, () -> {
				helper.assertFalse(com.projecthero.mod.moonknight.MoonKnightSuit.wearing(p), "and then it is gone");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- v0.13.21: the suit's own gifts

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitHealsHitsHarderAndTakesLess(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		p.setHealth(10.0f);
		helper.assertFalse(MoonKnight.regenerate(p), "no regeneration out of the suit");
		MoonKnight.setTransformedForTesting(p, true);
		helper.assertTrue(MoonKnight.regenerate(p) && Math.abs(p.getHealth() - 11.0f) < 1.0e-4f,
				"in the suit: half a heart per regen step (" + p.getHealth() + ")");
		helper.assertTrue(MoonKnightConfig.SUIT_REGEN_INTERVAL == 5, "every 5 ticks");
		var dmg = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
				.getModifier(com.projecthero.mod.moonknight.ability.MoonKnightAlters.SUIT_STRENGTH);
		helper.assertTrue(dmg != null && dmg.amount() == 7.0, "+7 melee damage while suited");
		helper.assertTrue(Math.abs(com.projecthero.mod.moonknight.MoonKnightDamage.incomingFactor(p,
				p.damageSources().generic()) - 0.8f) < 1.0e-4f, "20% less damage taken");
		MoonKnight.setTransformedForTesting(p, false);
		helper.assertTrue(p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
				.getModifier(com.projecthero.mod.moonknight.ability.MoonKnightAlters.SUIT_STRENGTH) == null,
				"the +7 goes with the suit");
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightDamage.incomingFactor(p, p.damageSources().generic()) == 1.0f,
				"and so does the damage cut");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aHardHitCallsTheSuit(GameTestHelper helper) {
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightTransform.callsTheSuit(10.5f, 18.0f),
				"a hit of more than 10 calls the suit");
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightTransform.callsTheSuit(2.0f, 7.0f),
				"so does being left under 4 hearts");
		helper.assertFalse(com.projecthero.mod.moonknight.MoonKnightTransform.callsTheSuit(10.0f, 10.0f),
				"a 10 that leaves 5 hearts does not");
		ServerPlayer p = knight(helper);
		p.setHealth(20.0f);
		helper.assertFalse(com.projecthero.mod.moonknight.MoonKnightTransform.autoSuit(p, 4.0f), "a light hit: nothing");
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightTransform.autoSuit(p, 11.0f), "a big hit: the suit answers");
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightTransform.isTransforming(p),
				"the normal 1.5 s suit-up (invulnerable) has started");
		helper.assertFalse(com.projecthero.mod.moonknight.MoonKnightTransform.autoSuit(p, 11.0f), "not twice");

		ServerPlayer q = knight(helper);
		MoonKnight.setTransformedForTesting(q, true);
		MoonKnight.setTransformedForTesting(q, false); // just took it off by choice
		q.setHealth(5.0f);
		helper.assertFalse(com.projecthero.mod.moonknight.MoonKnightTransform.autoSuit(q, 1.0f),
				"not in the few seconds after taking it off");
		MoonKnightState st = MoonKnight.state(q).copy();
		st.transformStart -= MoonKnightConfig.AUTO_SUIT_GRACE_TICKS;
		q.setAttached(ModAttachments.MOON_KNIGHT_STATE, st);
		helper.assertTrue(com.projecthero.mod.moonknight.MoonKnightTransform.autoSuit(q, 1.0f),
				"after that, under 4 hearts calls it");
		helper.succeed();
	}
}
