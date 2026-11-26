package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherAbilities;
import com.projecthero.mod.blackpanther.BlackPantherAbilityManager;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.BlackPantherUnsuited;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7 Black Panther rework (user): the crafted, worn suit switches the kit; the claws are always out (no toggle, no model);
 * melee 5 unsuited / 10 suited; Regeneration I / II; the Warrior's Combo 5 / 7 / 10 (8 / 9 / 10 suited, and it is the suited R
 * too); Takedown reaches 3 blocks for 10; Block cuts frontal hits; Shift+R Kinetic Pulse 10; the new Ground Pound, Flying Kick and
 * Rising Uppercut. Vindicators (no natural armour, they do not burn in daylight) with 200 health take the exact numbers. Mock players are not ticked by the
 * server, so moves over time are stepped with {@link BlackPanther#tick}.
 */
public class BlackPantherV0177GameTests implements FabricGameTest {
	/** A tough, armourless, motionless target at {@code pos} (the player stands at 2.5, 2, 2.5 facing south, +z). */
	private static Vindicator dummy(GameTestHelper helper, BlockPos pos) {
		Vindicator s = helper.spawn(EntityType.VINDICATOR, pos);
		s.setNoAi(true);
		s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		s.setHealth(200f);
		return s;
	}

	/** A bare fist with no power at all (whatever else the mod adds to every player). */
	private static double fist(GameTestHelper helper) {
		return helper.makeMockServerPlayerInLevel().getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	private static void aimAt(ServerPlayer p, net.minecraft.world.entity.LivingEntity e) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, e.getEyePosition().add(0, -0.4, 0));
	}

	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		BlackPantherAbilityManager.handle(p, slot, true);
		BlackPantherAbilityManager.handle(p, slot, false);
		p.setShiftKeyDown(false);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void clawsAreAlwaysOutInTheSuit(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		helper.assertTrue(!BlackPanther.clawsOut(p), "no claws out of the suit");
		BlackPantherGameTests.wearSuit(p);
		BlackPantherGameTests.finishChange(p);
		helper.assertTrue(BlackPanther.clawsOut(p), "the suit on: the claws are out with no toggle");
		press(p, AbilitySlot.SLOT_8, false);
		helper.assertTrue(BlackPanther.clawsOut(p), "plain N does not put them away (there is no claw toggle)");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (fist(helper) - 1.0 + BlackPantherConfig.SUITED_MELEE)) < 1.0e-6,
				"the claws' damage is always on: " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void meleeIsFiveUnsuitedAndTenSuited(GameTestHelper helper) {
		double fist = fist(helper); // 1, the vanilla fist
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		helper.assertTrue(BlackPantherConfig.UNSUITED_MELEE == 5.0 && BlackPantherConfig.SUITED_MELEE == 10.0, "the user's numbers");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (fist + 4.0)) < 1.0e-6,
				"unsuited fist 5, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		BlackPantherGameTests.wearSuit(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (fist + 9.0)) < 1.0e-6,
				"suited fist 10, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		BlackPantherGameTests.takeOffSuit(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (fist + 4.0)) < 1.0e-6, "back to 5 with a piece off");
		BlackPanther.revoke(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - fist) < 1.0e-6, "nothing once the power is gone");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void regenerationIsOneUnsuitedAndTwoSuited(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 0, "Regeneration I without the suit");
		BlackPantherGameTests.wearSuit(p);
		regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 1, "Regeneration II with it");
		BlackPantherGameTests.takeOffSuit(p);
		regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 0, "back to I");
		BlackPanther.revoke(p);
		helper.assertTrue(!p.hasEffect(MobEffects.REGENERATION), "gone with the power");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void warriorsComboIsFiveSevenTen(GameTestHelper helper) {
		helper.assertTrue(BlackPantherUnsuited.comboDamage(0, false) == 5f && BlackPantherUnsuited.comboDamage(1, false) == 7f
				&& BlackPantherUnsuited.comboDamage(2, false) == 10f, "unsuited 5 / 7 / 10");
		helper.assertTrue(BlackPantherUnsuited.comboDamage(0, true) == 8f && BlackPantherUnsuited.comboDamage(1, true) == 9f
				&& BlackPantherUnsuited.comboDamage(2, true) == 10f, "suited 8 / 9 / 10");
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		BlackPantherUnsuited.combo(p);
		float first = 200f - s.getHealth();
		helper.assertTrue(Math.abs(first - 5f) < 0.05f, "the jab lands on the press for 5, dealt " + first);
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.runAfterDelay(BlackPantherConfig.COMBO_STEP * 2 + 3, () -> {
			float total = 200f - s.getHealth();
			helper.assertTrue(Math.abs(total - 22f) < 0.1f, "5 + 7 + 10 = 22, dealt " + total);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void suitedRIsTheWarriorsComboToo(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		BlackPanther.setKinetic(p, 50f);
		press(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.COMBO) > 0, "suited R: the Warrior's Combo");
		helper.assertTrue(Math.abs(BlackPanther.kinetic(p) - 50f) < 0.01f, "it spends no Kinetic");
		float first = 200f - s.getHealth();
		helper.assertTrue(Math.abs(first - 8f) < 0.05f, "the suited jab is 8, dealt " + first);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kineticPulseOnShiftRDealsTen(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		BlackPanther.setKinetic(p, 50f);
		press(p, AbilitySlot.SLOT_1, true);
		float dealt = 200f - s.getHealth();
		helper.assertTrue(Math.abs(dealt - 10f) < 0.05f, "Kinetic Pulse 10, dealt " + dealt);
		helper.assertTrue(Math.abs(BlackPanther.kinetic(p) - 20f) < 0.05f, "30 spent");
		// a full bar: Shift+R is Kinetic Overload instead
		BlackPanther.setKinetic(p, BlackPantherConfig.KINETIC_MAX);
		press(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(BlackPanther.kinetic(p) == 0f && BlackPanther.cooldownRemaining(p, BlackPantherAbilities.OVERLOAD) > 0,
				"a full bar releases Kinetic Overload");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void takedownReachesThreeBlocksForTen(GameTestHelper helper) {
		helper.assertTrue(BlackPantherConfig.TAKEDOWN_RANGE == 3.0 && BlackPantherConfig.TAKEDOWN_DAMAGE == 10f, "the user's numbers");
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator far = dummy(helper, new BlockPos(2, 2, 6)); // its front face 3.7 blocks off
		aimAt(p, far);
		BlackPantherUnsuited.takedown(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.TAKEDOWN) == 0, "out of reach past 3 blocks");
		far.discard();
		Vindicator near = dummy(helper, new BlockPos(2, 2, 5)); // its front face 2.7 blocks off
		aimAt(p, near);
		BlackPantherUnsuited.takedown(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.TAKEDOWN) > 0, "grabbed at under 3 blocks");
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.succeedWhen(() -> {
			float dealt = 200f - near.getHealth();
			helper.assertTrue(Math.abs(dealt - 10f) < 0.05f, "the slam does 10, dealt " + dealt);
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blockCutsFrontalHits(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		ServerPlayer control = BlackPantherGameTests.bare(helper);
		Zombie front = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4)); // yaw 0 faces south, +z
		front.setNoAi(true);
		BlackPantherUnsuited.raiseBlock(p);
		helper.assertTrue(BlackPantherUnsuited.blocking(p) && BlackPanther.state(p).absorbing, "C raises the guard");
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(front), 10f);
		float lost = before - p.getHealth();
		float cBefore = control.getHealth();
		control.hurt(control.damageSources().mobAttack(front), 10f);
		float plain = cBefore - control.getHealth();
		helper.assertTrue(plain > 0f && Math.abs(lost / plain - (1f - BlackPantherConfig.BLOCK_REDUCTION)) < 0.05f,
				"60% less from the front: lost " + lost + " of " + plain);
		// from behind it does nothing
		p.invulnerableTime = 0;
		control.invulnerableTime = 0;
		Zombie back = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 0));
		back.setNoAi(true);
		before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(back), 4f);
		lost = before - p.getHealth();
		cBefore = control.getHealth();
		control.hurt(control.damageSources().mobAttack(back), 4f);
		plain = cBefore - control.getHealth();
		helper.assertTrue(plain > 0f && Math.abs(lost - plain) < 0.05f, "nothing from behind: lost " + lost + " of " + plain);
		BlackPantherUnsuited.lowerBlock(p);
		helper.assertTrue(!BlackPantherUnsuited.blocking(p) && !BlackPanther.state(p).absorbing, "released, it comes down");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wearingTheFullSuitSwitchesTheKit(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(BlackPantherItems.SUIT_HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(BlackPantherItems.SUIT_CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BlackPantherItems.SUIT_LEGGINGS));
		BlackPanther.tick(p);
		helper.assertTrue(!BlackPanther.suited(p) && !BlackPanther.suitedKit(p), "three pieces are not the suit");
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(BlackPantherItems.SUIT_BOOTS));
		BlackPanther.syncSuit(p);
		helper.assertTrue(BlackPanther.suited(p) && BlackPanther.suitedKit(p), "the fourth piece: the suit runs out and the suited kit is live");
		helper.assertTrue(BlackPanther.changing(p), "the suit-up plays first");
		BlackPantherGameTests.finishChange(p);
		press(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.LEAP) > 0, "suited X: Panther Leap");
		BlackPantherGameTests.takeOffSuit(p);
		helper.assertTrue(!BlackPanther.suited(p), "a piece off: the suit drains away");
		press(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.POUND) > 0, "unsuited Shift+G: Ground Pound");
		// someone without the power can wear it as armour, but gets no kit
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		BlackPantherGameTests.wearSuit(other);
		helper.assertTrue(!BlackPanther.suited(other) && BlackPanther.wearsFullSuit(other), "plain armour on anyone else");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSuitHasFourRecipesAndTheArmour(GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		Item[] pieces = { BlackPantherItems.SUIT_HELMET, BlackPantherItems.SUIT_CHESTPLATE, BlackPantherItems.SUIT_LEGGINGS, BlackPantherItems.SUIT_BOOTS };
		String[] ids = { "black_panther_helmet", "black_panther_chestplate", "black_panther_leggings", "black_panther_boots" };
		int armour = 0;
		float toughness = 0f;
		for (int i = 0; i < 4; i++) {
			var r = recipes.byKey(com.projecthero.mod.ProjectHeroMod.id(ids[i]));
			helper.assertTrue(r.isPresent(), ids[i] + " loads");
			helper.assertTrue(r.get().value().getResultItem(helper.getLevel().registryAccess()).is(pieces[i]), ids[i] + " makes the piece");
			ArmorItem a = (ArmorItem) pieces[i];
			armour += a.getDefense();
			toughness += a.getToughness();
			helper.assertTrue(!new ItemStack(pieces[i]).isDamageableItem(), ids[i] + " never wears out");
		}
		helper.assertTrue(armour == (int) BlackPantherConfig.SUIT_ARMOR, "12 armour over the set, got " + armour);
		helper.assertTrue(Math.abs(toughness - BlackPantherConfig.SUIT_TOUGHNESS) < 1.0e-4, "4 toughness over the set, got " + toughness);
		helper.assertTrue(recipes.byKey(com.projecthero.mod.ProjectHeroMod.id("panther_habit")).isEmpty(), "the old habit recipe is gone");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSuitHeldBeforeIsHandedBackOnce(GameTestHelper helper) {
		// 0.17.4-0.17.6: the habit in the necklace, suited at the time
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		var old = BlackPanther.state(p).copy();
		old.v177 = false;
		old.habit = true;
		old.suited = true;
		old.necklaceOff = true;
		p.setAttached(ModAttachments.BLACK_PANTHER_STATE, old);
		BlackPanther.onPlayerJoin(p);
		helper.assertTrue(BlackPanther.wearsFullSuit(p), "it was on: the four pieces are worn");
		helper.assertTrue(BlackPanther.suited(p), "and the suit stays on");
		helper.assertTrue(BlackPanther.state(p).v177 && !BlackPanther.state(p).habit && !BlackPanther.state(p).necklaceOff, "migrated once");
		BlackPanther.onPlayerJoin(p);
		helper.assertTrue(p.getInventory().countItem(BlackPantherItems.SUIT_HELMET) == 1, "not handed out twice (countItem counts the worn one)");
		// 0.17.3: no habit flag, not migrated at all, suit off: the pieces go into the inventory
		ServerPlayer o = BlackPantherGameTests.bare(helper);
		var older = new com.projecthero.mod.blackpanther.data.BlackPantherState();
		older.hasPower = true;
		o.setAttached(ModAttachments.BLACK_PANTHER_STATE, older);
		BlackPanther.onPlayerJoin(o);
		helper.assertTrue(o.getInventory().countItem(BlackPantherItems.SUIT_CHESTPLATE) == 1 && !BlackPanther.suited(o), "into the inventory");
		// a new Black Panther gets nothing for free
		ServerPlayer fresh = BlackPantherGameTests.bare(helper);
		BlackPanther.onPlayerJoin(fresh);
		helper.assertTrue(fresh.getInventory().countItem(BlackPantherItems.SUIT_HELMET) == 0, "a new power is not given one");
		// an old habit item unfolds into the pieces
		ItemStack habit = new ItemStack(BlackPantherItems.HABIT);
		fresh.setItemInHand(InteractionHand.MAIN_HAND, habit);
		habit.use(fresh.level(), fresh, InteractionHand.MAIN_HAND);
		helper.assertTrue(fresh.getInventory().countItem(BlackPantherItems.SUIT_BOOTS) == 1, "the habit unfolds into the suit");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void groundPoundSlamsRoundHim(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator s = dummy(helper, new BlockPos(4, 2, 3));
		BlackPantherUnsuited.groundPound(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.POUND) > 0, "hopped");
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.succeedWhen(() -> {
			float dealt = 200f - s.getHealth();
			helper.assertTrue(Math.abs(dealt - BlackPantherConfig.POUND_DAMAGE) < 0.05f, "the slam does 9, dealt " + dealt);
			helper.assertTrue(s.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slows");
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flyingKickHitsTheFirstForTen(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 3)); // right in front
		BlackPantherUnsuited.flyingKick(p);
		float dealt = 200f - s.getHealth();
		helper.assertTrue(Math.abs(dealt - 10f) < 0.05f, "the kick does 10, dealt " + dealt);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void risingUppercutIsVAndTrackingMovedToShiftV(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		aimAt(p, s);
		press(p, AbilitySlot.SLOT_5, false);
		float dealt = 200f - s.getHealth();
		helper.assertTrue(Math.abs(dealt - BlackPantherConfig.UPPERCUT_DAMAGE) < 0.05f, "V: Rising Uppercut 9, dealt " + dealt);
		helper.assertTrue(!BlackPanther.state(p).senses, "V is not the tracking any more");
		press(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(BlackPanther.state(p).senses, "Shift+V: Hunter's Tracking");
		helper.assertTrue(BlackPantherConfig.KNIFE_DAMAGE == 8f && BlackPantherConfig.SWEEP_DAMAGE == 7f, "knife 8, sweep 7");
		helper.succeed();
	}
}
