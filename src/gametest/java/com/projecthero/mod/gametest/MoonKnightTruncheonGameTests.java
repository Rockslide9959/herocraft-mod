package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.ability.MoonKnightAbilities;
import com.projecthero.mod.moonknight.ability.MoonKnightAbilityManager;
import com.projecthero.mod.moonknight.ability.MoonKnightAlters;
import com.projecthero.mod.moonknight.ability.MoonKnightTruncheon;
import com.projecthero.mod.moonknight.ability.MoonKnightTruncheonCombo;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.item.MoonKnightItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4 Moon Knight truncheon: C summons it on the press, the damage numbers (truncheon 7, Staff Spin 15), and the
 * 3-hit combo's steps, bonuses, anti-spam gap and reset window. Mock players spawn at world spawn, so they are moved
 * into the test area; mobs are NoAI husks with no armour and 100 health so the numbers come out exact.
 */
public class MoonKnightTruncheonGameTests implements FabricGameTest {
	private static ServerPlayer knight(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		MoonKnight.grant(p, false);
		MoonKnight.setTransformedForTesting(p, true);
		return p;
	}

	private static Husk dummy(GameTestHelper helper, Vec3 absolute) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		h.moveTo(absolute.x, absolute.y, absolute.z, 0.0f, 0.0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		h.setHealth(100.0f);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	/** What one of Moon Knight's ability hits of {@code base} (already x power) comes to on {@code h}. */
	private static float expected(ServerPlayer p, Husk h, float base) {
		return base * MoonKnightAlters.outgoingFactor(p, h, p.damageSources().playerAttack(p));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_truncheon_press")
	public void pressingCSummonsTheTruncheonIntoTheHand(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5));
		p.getInventory().selected = 2;
		p.getInventory().items.set(2, new ItemStack(Items.IRON_SWORD));

		MoonKnightAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "C going down puts the truncheon in the main hand");
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_TRUNCHEON), "FLAG_TRUNCHEON is on (every viewer)");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.TRUNCHEON_DRAW, "with the draw animation");
		helper.assertTrue(p.getInventory().countItem(Items.IRON_SWORD) == 1, "the sword moved into the inventory, not deleted");
		MoonKnightAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "releasing that same press doesn't put it away again");

		MoonKnightAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		MoonKnightAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
		helper.assertFalse(p.getInventory().hasAnyMatching(MoonKnightTruncheon::isTruncheon), "the next tap stows it (gone, not kept)");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.TRUNCHEON_STOW, "with the stow animation");
		helper.assertTrue(p.getMainHandItem().is(Items.IRON_SWORD), "and the sword is back in the hand");

		// scrolled away while it was out: the sword still goes back to the slot the truncheon was in
		MoonKnightAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		MoonKnightAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
		p.getInventory().selected = 5;
		MoonKnightTruncheon.stow(p, true);
		helper.assertTrue(p.getInventory().items.get(2).is(Items.IRON_SWORD), "the sword returns to slot 2, where it was");
		helper.assertTrue(p.getInventory().countItem(Items.IRON_SWORD) == 1, "exactly one sword");

		// only ever one, and never after the power is gone
		p.getInventory().selected = 2;
		MoonKnightAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		p.getInventory().items.set(20, new ItemStack(MoonKnightItems.TRUNCHEON));
		MoonKnightTruncheon.INSTANCE.tick(p);
		int copies = 0;
		for (ItemStack s : p.getInventory().items) {
			copies += MoonKnightTruncheon.isTruncheon(s) ? 1 : 0;
		}
		helper.assertTrue(copies == 1, "a second copy is deleted (" + copies + ")");
		MoonKnight.revoke(p);
		helper.assertFalse(p.getInventory().hasAnyMatching(MoonKnightTruncheon::isTruncheon), "revoking the power takes it");
		helper.assertTrue(p.getInventory().countItem(Items.IRON_SWORD) == 1, "and leaves the sword");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void truncheonAndStaffDamageNumbers(GameTestHelper helper) {
		helper.assertTrue(MoonKnightConfig.TRUNCHEON_DAMAGE == 7.0f, "truncheon damage 7");
		helper.assertTrue(MoonKnightConfig.STAFF_SPIN_DAMAGE == 15.0f, "staff spin damage 15");
		ItemAttributeModifiers mods = new ItemStack(MoonKnightItems.TRUNCHEON).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
				ItemAttributeModifiers.EMPTY);
		double[] attack = {0.0};
		mods.forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
			if (attr.value() == Attributes.ATTACK_DAMAGE.value()) {
				attack[0] += mod.amount();
			}
		});
		// the tooltip (and a bare-handed player's 1 base damage + this modifier) reads "7 Attack Damage"
		helper.assertTrue(Math.abs(attack[0] + 1.0 - 7.0) < 1.0e-6, "the item says 7 attack damage (" + (attack[0] + 1.0) + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_truncheon_spin")
	public void staffSpinDealsFifteen(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(3.5, 2.0, 3.5));
		Husk h = dummy(helper, p.position().add(2.0, 0, 0));
		MoonKnightTruncheon.INSTANCE.holdStart(p);
		float want = expected(p, h, MoonKnightConfig.STAFF_SPIN_DAMAGE * MoonKnightAbilities.power(p));
		float took = 100.0f - h.getHealth();
		helper.assertTrue(Math.abs(took - want) < 0.05f, "the spin deals 15 x power (" + took + " vs " + want + ")");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.STAFF_SPIN, "with the spin animation");
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_STAFF), "extended into the staff");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_truncheon_combo", timeoutTicks = 200)
	public void comboStepsForehandBackhandSmashThenResets(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5));
		Husk h = dummy(helper, p.position().add(0, 0, 2.0));
		MoonKnightTruncheon.summon(p);
		int gap = MoonKnightConfig.TRUNCHEON_COMBO_MIN_GAP + 2;

		helper.assertTrue(MoonKnightTruncheon.onMeleeHit(p, h, 14.0f) == 1, "hit 1: the forehand");
		helper.assertTrue(MoonKnightTruncheonCombo.step(p) == 1, "combo at step 1");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.TRUNCHEON_HIT_1, "forehand animation");
		helper.assertTrue(h.getHealth() == 100.0f, "the forehand adds nothing on top of the swing itself");
		helper.assertTrue(MoonKnightTruncheon.onMeleeHit(p, h, 14.0f) == 0, "a hit straight after doesn't count (no spamming)");
		helper.assertTrue(MoonKnightTruncheonCombo.step(p) == 1, "still at step 1");

		helper.runAfterDelay(gap, () -> {
			float before = h.getHealth();
			helper.assertTrue(MoonKnightTruncheon.onMeleeHit(p, h, 14.0f) == 2, "hit 2: the backhand");
			helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.TRUNCHEON_HIT_2, "backhand animation");
			float want = expected(p, h, MoonKnightConfig.TRUNCHEON_BACKHAND_BONUS * MoonKnightAbilities.power(p));
			helper.assertTrue(Math.abs(before - h.getHealth() - want) < 0.05f,
					"the backhand adds 3 x power (" + (before - h.getHealth()) + " vs " + want + ")");
			helper.runAfterDelay(gap, () -> {
				float before3 = h.getHealth();
				helper.assertTrue(MoonKnightTruncheon.onMeleeHit(p, h, 14.0f) == 3, "hit 3: the overhead smash");
				helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.TRUNCHEON_SLAM, "smash animation");
				float want3 = expected(p, h, MoonKnightConfig.TRUNCHEON_SLAM_BONUS * MoonKnightAbilities.power(p));
				helper.assertTrue(Math.abs(before3 - h.getHealth() - want3) < 0.05f,
						"the smash adds 6 x power (" + (before3 - h.getHealth()) + " vs " + want3 + ")");
				helper.assertTrue(MoonKnightTruncheonCombo.nextStep(p) == 1, "after the smash it starts over");
				helper.runAfterDelay(gap, () -> {
					helper.assertTrue(MoonKnightTruncheon.onMeleeHit(p, h, 14.0f) == 1, "the next hit is a forehand again");
					helper.runAfterDelay(gap, () -> {
						helper.assertTrue(MoonKnightTruncheon.onMeleeHit(p, h, 14.0f) == 2, "(backhand)");
						helper.runAfterDelay(MoonKnightConfig.TRUNCHEON_COMBO_WINDOW + 5, () -> {
							helper.assertTrue(MoonKnightTruncheonCombo.step(p) == 0, "the window ran out: the combo is dropped");
							helper.assertTrue(MoonKnightTruncheon.onMeleeHit(p, h, 14.0f) == 1, "so a late hit is a forehand, not the smash");
							MoonKnightTruncheon.stow(p, false);
							helper.assertTrue(MoonKnightTruncheonCombo.step(p) == 0, "stowing clears it too");
							helper.succeed();
						});
					});
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_truncheon_bare")
	public void onlyTheTruncheonRunsTheCombo(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5));
		Husk h = dummy(helper, p.position().add(0, 0, 2.0));
		helper.assertTrue(MoonKnightTruncheon.onMeleeHit(p, h, 8.0f) == 0, "bare-handed hits are not combo steps");
		helper.assertTrue(MoonKnightTruncheonCombo.step(p) == 0, "no combo");
		helper.succeed();
	}
}
