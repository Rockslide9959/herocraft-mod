package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.combat.MeleeBonus;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorKit;
import com.projecthero.mod.power.ThorPassives;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierConfig;
import com.projecthero.mod.supersoldier.SuperSoldierSerum;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user: "Super Soldier: their melee should deal 5 damage and should be +5 on top of tools and weapons and stuff, please do
 * this for black panthers and other powers that have melee bonuses aswell"): the shared melee rule ({@link MeleeBonus}). A power's
 * melee N: a punch (empty hand or a non-weapon item) is exactly N, a weapon or tool is its own damage + N, and swapping the held item
 * follows. Mock players are not ticked, so the held item's own modifiers are swapped in by hand the way vanilla's equipment update
 * does it ({@link #hold}); one test leaves the top-up to the per-tick hook.
 */
public class MeleeBonusV0177GameTests implements FabricGameTest {
	private static final double EPS = 1.0e-6;

	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	/** A bare fist with no power (1, plus whatever the mod gives every player). */
	private static double fist(GameTestHelper helper) {
		return helper.makeMockServerPlayerInLevel().getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	/** What {@code item} hits for in an ordinary player's hand: the fist plus its own main-hand attack damage. */
	private static double weapon(double fist, Item item) {
		double[] add = { 0.0 };
		new ItemStack(item).forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
			if (attr.is(Attributes.ATTACK_DAMAGE) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
				add[0] += mod.amount();
			}
		});
		return fist + add[0];
	}

	/** Puts {@code item} in the main hand and swaps the held item's modifiers as vanilla's equipment update does, then reconciles. */
	private static void hold(ServerPlayer p, Item item) {
		p.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> p.getAttribute(attr).removeModifier(mod.id()));
		ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> p.getAttribute(attr).addOrUpdateTransientModifier(mod));
		MeleeBonus.reconcile(p);
	}

	private static double dmg(ServerPlayer p) {
		return p.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	private static void near(GameTestHelper helper, double got, double want, String what) {
		helper.assertTrue(Math.abs(got - want) < EPS, what + ": want " + want + ", got " + got);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void superSoldierPunchesFiveAndAddsFiveToWeapons(GameTestHelper helper) {
		double fist = fist(helper);
		ServerPlayer p = mock(helper);
		helper.assertTrue(SuperSoldierSerum.drink(p, true, 0.99f) == SuperSoldierSerum.Outcome.GRANTED, "the refined serum takes");
		SuperSoldier.reconcile(p);
		helper.assertTrue(SuperSoldierConfig.MELEE == 5.0, "the user's number: melee 5");
		near(helper, dmg(p), fist - 1.0 + 5.0, "bare hand: 5");
		hold(p, Items.DIAMOND_SWORD);
		near(helper, dmg(p), weapon(fist, Items.DIAMOND_SWORD) + 5.0, "diamond sword: 7 + 5 = 12");
		hold(p, Items.NETHERITE_SWORD);
		near(helper, dmg(p), weapon(fist, Items.NETHERITE_SWORD) + 5.0, "netherite sword: 8 + 5 = 13");
		hold(p, Items.IRON_PICKAXE);
		near(helper, dmg(p), weapon(fist, Items.IRON_PICKAXE) + 5.0, "a tool counts: iron pickaxe 4 + 5");
		hold(p, Items.TRIDENT);
		near(helper, dmg(p), weapon(fist, Items.TRIDENT) + 5.0, "trident 9 + 5");
		hold(p, Items.STICK);
		near(helper, dmg(p), fist - 1.0 + 5.0, "a stick is not a weapon: a plain 5 punch");
		hold(p, Items.SHIELD);
		near(helper, dmg(p), fist - 1.0 + 5.0, "nor is a shield");
		hold(p, null);
		near(helper, dmg(p), fist - 1.0 + 5.0, "empty again: 5");
		helper.assertTrue(p.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(MeleeBonus.WEAPON_ID) == null, "no top-up bare-handed");
		// the power gone: no bonus, and no top-up left on a held sword
		hold(p, Items.DIAMOND_SWORD);
		SuperSoldier.revoke(p);
		SuperSoldier.reconcile(p);
		near(helper, dmg(p), weapon(fist, Items.DIAMOND_SWORD), "power gone: the sword's own 7");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blackPantherAddsEightUnsuitedAndTenSuited(GameTestHelper helper) {
		double fist = fist(helper);
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		near(helper, dmg(p), fist - 1.0 + BlackPantherConfig.UNSUITED_MELEE, "unsuited bare hand: 8");
		hold(p, Items.IRON_SWORD);
		near(helper, dmg(p), weapon(fist, Items.IRON_SWORD) + 8.0, "unsuited iron sword: 6 + 8");
		hold(p, Items.DIAMOND_AXE);
		near(helper, dmg(p), weapon(fist, Items.DIAMOND_AXE) + 8.0, "unsuited diamond axe: 9 + 8");
		BlackPantherGameTests.wearSuit(p);
		near(helper, dmg(p), weapon(fist, Items.DIAMOND_AXE) + 10.0, "suited diamond axe: 9 + 10");
		hold(p, Items.DIAMOND_SWORD);
		near(helper, dmg(p), weapon(fist, Items.DIAMOND_SWORD) + 10.0, "suited diamond sword: 7 + 10");
		hold(p, null);
		near(helper, dmg(p), fist - 1.0 + BlackPantherConfig.SUITED_MELEE, "suited bare hand: 10");
		hold(p, Items.COBBLESTONE);
		near(helper, dmg(p), fist - 1.0 + BlackPantherConfig.SUITED_MELEE, "a block in hand: still 10");
		BlackPantherGameTests.takeOffSuit(p);
		hold(p, Items.DIAMOND_SWORD);
		near(helper, dmg(p), weapon(fist, Items.DIAMOND_SWORD) + 8.0, "suit off: 7 + 8 again");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void otherPowersFollowTheSameRule(GameTestHelper helper) {
		double fist = fist(helper);
		// Super Strength (mutation): +10, a punch 11 -- so a sword is its own damage + 11
		ServerPlayer s = mock(helper);
		SuperStrengthHandlers.updateUnarmed(s);
		helper.assertTrue(MeleeBonus.isRegistered(SuperStrengthHandlers.PASSIVE_ATK), "Super Strength's bonus uses the shared rule");
		MeleeBonus.reconcile(s);
		near(helper, dmg(s), fist + SuperStrengthHandlers.UNARMED_BONUS, "Super Strength bare hand: 11 (unchanged)");
		hold(s, Items.STONE_SWORD);
		near(helper, dmg(s), weapon(fist, Items.STONE_SWORD) + 1.0 + SuperStrengthHandlers.UNARMED_BONUS, "stone sword: 5 + 11");
		// Thor: the Power of Thor's +11 (a 12 punch) on an ordinary weapon; his own weapons keep their exact state numbers
		ServerPlayer t = mock(helper);
		Worthiness.setScore(t, Worthiness.TEST_WORTHY_SCORE);
		t.setAttached(ModAttachments.BOUND_HAMMER_ID, UUID.randomUUID());
		hold(t, Items.IRON_SWORD);
		ThorPassives.reconcile(t);
		ThorKit.reconcileMelee(t);
		MeleeBonus.reconcile(t);
		near(helper, dmg(t), weapon(fist, Items.IRON_SWORD) + 12.0, "Thor with an iron sword: 6 + 12");
		hold(t, ModItems.MJOLNIR);
		ThorKit.reconcileMelee(t);
		MeleeBonus.reconcile(t);
		near(helper, dmg(t), fist - 1.0 + 24.0, "Mjolnir stays exactly 24 (no top-up on his own tuned weapon)");
		helper.assertTrue(t.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(MeleeBonus.WEAPON_ID) == null, "no top-up with Mjolnir");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theTickHookFollowsAnItemSwap(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(SuperSoldierSerum.drink(p, true, 0.99f) == SuperSoldierSerum.Outcome.GRANTED, "the refined serum takes");
		SuperSoldier.reconcile(p);
		// a swap with no reconcile call: the end-of-tick hook adds the top-up, and takes it away after the next swap
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(p.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(MeleeBonus.WEAPON_ID) != null,
					"a sword in hand: the +1 weapon top-up is on");
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(p.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(MeleeBonus.WEAPON_ID) == null,
						"bread in hand: the top-up is gone");
				helper.succeed();
			});
		});
	}
}
