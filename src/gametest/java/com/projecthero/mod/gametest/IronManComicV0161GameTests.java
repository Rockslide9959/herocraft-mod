package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManIntegrity;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManComicMoves;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.suit.SummonType;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * v0.16.1 COMIC-ACCURATE IRON MAN REWORK (v0.16.7 (user): every suit has a reactor again -- Era I, then Era II), the integrity absorption share per Mark, the 0-integrity
 * disable, the Mark 6 / Mark 7 out-of-combat self-repair, the Mark 1 V2 Fabricator upgrade, the Mark 2 Briefcase remap and,
 * for every Mark, that tap and Shift on one key are different moves with separate cooldowns.
 */
public class IronManComicV0161GameTests implements FabricGameTest {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };
	// v0.16.7 (user) Era I: the Gantry Age order and its integrity / absorb table (the Mk VI Suitcase follows the Mk V)
	private static final String[] ORDER = { "mark_1", "mark_1_v2", "mark_iii", "mark_4", "mark_v", "mark_2", "mark_6", "mark_vii", "mark_9", "mark_8" }; // v0.16.7: + the Era II Mk IX Inferno
	private static final float[] SHARES = { 0.30f, 0.32f, 0.38f, 0.40f, 0.15f, 0.30f, 0.45f, 0.50f, 0.55f, 0.60f };
	private static final float[] INTEGRITY = { 700, 850, 1400, 1600, 500, 1000, 2200, 2600, 3200, 4500 };

	private static ServerPlayer tony(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	private static void wear(ServerPlayer p, String suit) {
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(suit, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, suit);
		IronManEnergy.setIntegrity(p, suit, IronManEnergy.maxIntegrity(suit));
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static DamageSource generic(GameTestHelper h) {
		return h.getLevel().damageSources().generic();
	}

	// ------------------------------------------------------------------ the roster

	@GameTest(template = EMPTY_STRUCTURE)
	public void nineMarksInOrderWithTheComicStats(GameTestHelper h) {
		h.assertTrue(IronManItems.MARK_ORDER.equals(java.util.List.of(ORDER)), "nine marks in order: " + IronManItems.MARK_ORDER);
		for (int i = 0; i < ORDER.length; i++) {
			IronManSuit s = IronManSuits.byId(ORDER[i]);
			h.assertTrue(s != null, ORDER[i] + " registered");
			h.assertTrue(s.absorbShare() == SHARES[i], ORDER[i] + " absorbs " + SHARES[i] + ", got " + s.absorbShare());
			h.assertTrue(s.maxIntegrity() == INTEGRITY[i], ORDER[i] + " integrity " + INTEGRITY[i] + ", got " + s.maxIntegrity());
			h.assertTrue(s.progressionIndex() == i, ORDER[i] + " progression index " + i);
			IronManComicKit.Kit kit = IronManComicKit.kit(ORDER[i]);
			// v0.16.7: the Era I kits have a sixth key (C); v0.16.7 (user): the Era II kits too (a utility moved to C)
			int keys = 6;
			// v0.16.7 (user) Era II stage 3: the Mk IX Inferno has its own five-key kit now (stage 1 left it empty)
			h.assertTrue(kit != null && kit.keys().size() == keys, ORDER[i] + " has a " + keys + "-key comic kit");
			for (int slot = 1; slot <= 5; slot++) {
				h.assertTrue(IronManComicKit.isKitSlot(s.abilityInSlot(slot)), ORDER[i] + " slot " + slot + " is a comic key");
			}
			// every suit at least diamond: chestplate 8+, helmet 3+
			h.assertTrue(s.armor(ArmorItem.Type.CHESTPLATE).getDefense() >= 8 && s.armor(ArmorItem.Type.HELMET).getDefense() >= 3,
					ORDER[i] + " is at least diamond-grade");
		}
		h.assertTrue(IronManSuits.MARK_8.armor(ArmorItem.Type.CHESTPLATE).getDefense() == 10
				&& IronManSuits.MARK_8.armor(ArmorItem.Type.CHESTPLATE).getToughness() == 5f, "Hulkbuster 10 chest, toughness 5");
		h.assertTrue(IronManSuits.MARK_8.scale() == 1.45f, "Hulkbuster is about 3 blocks tall");
		h.assertTrue(!IronManSuits.MARK_1.manualFlight(), "the Mark 1 has no sustained flight");
		h.assertTrue(IronManSuits.MARK_III.manualFlight() && !IronManSuits.MARK_1_V2.manualFlight() && IronManSuits.MARK_4.manualFlight()
				&& IronManSuits.MARK_V.manualFlight(), "v0.16.7 (user): the Mk III, the Hydro and the Stealth are the Era I fliers");
		h.assertTrue(IronManSuits.MARK_VII.hazardImmune() && IronManSuits.MARK_VII.altitudeCeiling() <= 0.0,
				"the Space armour ignores fire / lava / freezing and has no ceiling");
		h.assertTrue(IronManSuits.MARK_V.stealth() && IronManSuits.MARK_4.hydro() && IronManSuits.MARK_8.hulkbuster(), "special flags");
		h.succeed();
	}

	// ------------------------------------------------------------------ energy (v0.16.7 (user): every suit has a reactor)

	@GameTest(template = EMPTY_STRUCTURE)
	public void everySuitHasAReactorNow(GameTestHelper h) {
		for (String id : ORDER) {
			h.assertTrue(IronManSuits.byId(id).hasReactor() && IronManEnergy.real(id),
					id + " runs on a real reactor (" + IronManEnergy.capacity(id) + ")");
		}
		ServerPlayer p = tony(h);
		wear(p, "mark_6");
		// the pre-v0.16.1 helpers' own costs stay free (the kit table is the one price list)
		h.assertTrue(IronManEnergy.spend(p, "mark_6", 1.0e6f) && IronManEnergy.has(p, "mark_6", 1.0e6f), "spend / has never refuse");
		float before = IronManEnergy.energy(p, "mark_6");
		h.assertTrue(IronManComicRouter.fireForTests(p, IronManSuits.MARK_6, 4, false), "Heavy Unibeam fires");
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_6") - (before - IronManComicKit.energyCost("mk6_heavy_unibeam"))) < 0.01f,
				"and pays its kit cost");
		// the stamp carries the charge
		ItemStack chest = new ItemStack(IronManItems.armor("mark_6", ArmorItem.Type.CHESTPLATE));
		IronManEnergy.stampStack(chest, 500f, 700f);
		h.assertTrue(IronManEnergy.stackEnergy(chest, "mark_6") == 500f, "energy stamped on the piece");
		h.assertTrue(IronManEnergy.stackIntegrity(chest, "mark_6") == 700f, "integrity still stamped");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ integrity absorption

	@GameTest(template = EMPTY_STRUCTURE)
	public void eachMarkAbsorbsItsShareFromIntegrity(GameTestHelper h) {
		ServerPlayer p = tony(h);
		for (int i = 0; i < ORDER.length; i++) {
			IronManSuit suit = IronManSuits.byId(ORDER[i]);
			wear(p, ORDER[i]);
			float landed = IronManDamage.absorb(p, suit, generic(h), 20f);
			float expectSoak = 20f * SHARES[i];
			h.assertTrue(Math.abs(landed - (20f - expectSoak)) < 1.0e-3f, ORDER[i] + ": the wearer takes " + (20f - expectSoak) + ", got " + landed);
			float integ = IronManEnergy.integrity(p, ORDER[i]);
			h.assertTrue(Math.abs(integ - (INTEGRITY[i] - expectSoak)) < 1.0e-3f, ORDER[i] + ": integrity loses " + expectSoak + " 1:1");
		}
		// a hit bigger than what is left: the suit soaks what it has, the rest lands
		wear(p, "mark_8");
		IronManEnergy.setIntegrity(p, "mark_8", 2f);
		float landed = IronManDamage.absorb(p, IronManSuits.MARK_8, generic(h), 20f);
		h.assertTrue(Math.abs(landed - 18f) < 1.0e-3f && IronManEnergy.integrity(p, "mark_8") == 0f, "capped at the remaining integrity");
		// falls are never absorbed
		IronManEnergy.setIntegrity(p, "mark_8", 4500f);
		h.assertTrue(IronManDamage.absorb(p, IronManSuits.MARK_8, h.getLevel().damageSources().fall(), 10f) == 10f, "falls bypass");
		// onDamageTaken no longer bleeds integrity
		IronManDamage.onDamageTaken(p, generic(h), 10f);
		h.assertTrue(IronManEnergy.integrity(p, "mark_8") == 4500f, "a landed hit does not wear integrity a second time");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSpaceArmourIgnoresFireAndTheHulkbusterHitsBossesHarder(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_vii");
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().lava(), 6f), "lava cancelled");
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().freeze(), 2f), "freezing cancelled");
		h.assertTrue(IronManEnergy.integrity(p, "mark_vii") == 2600f, "and the suit loses nothing to it");
		wear(p, "mark_8");
		net.minecraft.world.entity.monster.Zombie boss = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(3, 1, 3));
		boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400.0);
		net.minecraft.world.entity.monster.Zombie grunt = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(5, 1, 3));
		h.assertTrue(IronManDamage.outgoingMultiplier(p, boss) == IronManDamage.HULKBUSTER_BOSS_MULT, "x1.5 on a boss");
		h.assertTrue(IronManDamage.outgoingMultiplier(p, grunt) == 1f, "x1 on an ordinary mob");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ 0 integrity

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroIntegrityDisablesTheSuit(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_6");
		IronManEnergy.setIntegrity(p, "mark_6", 0f);
		h.assertTrue(IronManIntegrity.disabled(p, "mark_6"), "disabled at 0");
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_6, 4, false), "no abilities");
		h.assertTrue(IronManDamage.absorb(p, IronManSuits.MARK_6, generic(h), 10f) == 10f, "a disabled suit absorbs nothing");
		com.projecthero.mod.ironman.IronManFlight.setFlying(p, true);
		IronManSuitTicker.tick(p);
		h.assertFalse(com.projecthero.mod.ironman.IronManFlight.isFlying(p), "no flight");
		MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		h.assertTrue(slow != null && slow.getAmplifier() >= 3, "heavy Slowness");
		// the Hulkbuster's Replacement Pod is the one move that still works disabled
		wear(p, "mark_8");
		IronManEnergy.setIntegrity(p, "mark_8", 0f);
		h.assertTrue(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 5, true), "Replacement Pod fires on a disabled Hulkbuster");
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 5, false), "but the Palm Cannon does not");
		h.runAfterDelay(IronManComicMoves.POD_DELAY_TICKS + 5, () -> {
			float integ = IronManEnergy.integrity(p, "mark_8");
			h.assertTrue(Math.abs(integ - 4500f * IronManComicMoves.POD_REPAIR_FRACTION) < 1f, "the pod restored 30%, got " + integ);
			leave(h, p);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ self-repair

	@GameTest(template = EMPTY_STRUCTURE)
	public void marksSixAndSevenSelfRepairOutOfCombat(GameTestHelper h) {
		ServerPlayer p = tony(h);
		for (String id : new String[] { "mark_6", "mark_vii", "mark_8", "mark_iii" }) {
			IronManSuit suit = IronManSuits.byId(id);
			wear(p, id);
			IronManEnergy.setIntegrity(p, id, 100f);
			IronManIntegrity.markCombat(p);
			h.assertFalse(IronManIntegrity.repairStep(p, suit), id + ": no repair in combat");
			IronManIntegrity.setLastCombat(p, IronManIntegrity.OUT_OF_COMBAT_TICKS + 1);
			boolean repaired = IronManIntegrity.repairStep(p, suit);
			float integ = IronManEnergy.integrity(p, id);
			switch (id) {
				case "mark_6" -> h.assertTrue(repaired && integ == 108f, "Centurion repairs 8/s, got " + integ);
				case "mark_vii" -> h.assertTrue(repaired && integ == 115f, "Space armour repairs 15/s, got " + integ);
				default -> h.assertTrue(!repaired && integ == 100f, id + " never self-repairs");
			}
		}
		h.assertTrue(IronManSuits.MARK_VII.selfRepairPerSecond() > IronManSuits.MARK_6.selfRepairPerSecond(), "Mark 7 faster");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ Mark 1 V2 upgrade

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMarkOneVTwoIsAnUpgradedMarkOne(GameTestHelper h) {
		h.assertTrue("mark_1".equals(IronManItems.prerequisiteSuit("mark_1_v2")), "unlocks after the Mark 1");
		h.assertTrue("mark_1_v2".equals(IronManItems.prerequisiteSuit("mark_iii")), "v0.16.7: the Mk III follows the Mk II Golden Avenger");
		h.assertTrue("mark_v".equals(IronManItems.prerequisiteSuit("mark_2")), "v0.16.7: the Mk VI Suitcase follows the Mk V Stealth");
		h.assertTrue(IronManItems.blueprintFor("mark_1_v2") == IronManItems.MARK_1_V2_BLUEPRINT, "its own blueprint");
		for (int i = 0; i < 4; i++) {
			FabricationRecipe r = FabricatorRecipes.byId("iron_man_mark_1_v2_" + TYPES[i].getName());
			h.assertTrue(r != null, "recipe for the " + TYPES[i].getName());
			h.assertTrue(r.result().is(IronManItems.armor("mark_1_v2", TYPES[i])), "makes the gold piece");
			boolean mk1 = false;
			boolean gold = false;
			boolean gilded = false;
			for (FabricationRecipe.Input in : r.inputs()) {
				mk1 |= in.item() == IronManItems.armor("mark_1", TYPES[i]);
				gold |= in.item() == Items.GOLD_INGOT;
				gilded |= in.item() == IronManItems.GILDED_PLATING;
			}
			h.assertTrue(mk1 && gilded && (gold || i != 1), TYPES[i].getName() + ": the Mark 1 piece + Gilded Plating (+ gold in the chest)");
		}
		// a real tray: the Mark 1 helmet + 4 gold + 1 plating with the V2 blueprint, helmet picked
		SimpleContainer tray = new SimpleContainer(11);
		tray.setItem(0, new ItemStack(IronManItems.armor("mark_1", ArmorItem.Type.HELMET)));
		tray.setItem(1, new ItemStack(Items.GOLD_INGOT, 4));
		tray.setItem(2, new ItemStack(IronManItems.GILDED_PLATING));
		tray.setItem(10, new ItemStack(IronManItems.MARK_1_V2_BLUEPRINT));
		FabricationRecipe found = FabricatorRecipes.find(tray, 3, 1);
		h.assertTrue(found != null && found.id().equals("iron_man_mark_1_v2_helmet"), "the Fabricator matches the upgrade");
		// and the new signature parts are in the Mark 4 / 5 / 7 / 8 recipes
		h.assertTrue(has("iron_man_mark_4_helmet", IronManItems.PRESSURE_HULL) && has("iron_man_mark_v_helmet", IronManItems.STEALTH_COATING)
				&& has("iron_man_mark_vii_helmet", IronManItems.THERMAL_SHIELDING) && has("iron_man_mark_8_helmet", IronManItems.HYDRAULIC_FRAME),
				"Pressure Hull / Stealth Coating / Thermal Shielding / Hydraulic Frame");
		h.succeed();
	}

	private static boolean has(String recipeId, net.minecraft.world.item.Item item) {
		FabricationRecipe r = FabricatorRecipes.byId(recipeId);
		if (r == null) {
			return false;
		}
		for (FabricationRecipe.Input in : r.inputs()) {
			if (in.item() == item) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ delivery remap

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theBriefcaseCarriesTheMarkTwo(GameTestHelper h) {
		h.assertTrue("mark_2".equals(IronManSuitUpManager.BRIEFCASE_SUIT), "the briefcase is the Mark 2's");
		h.assertTrue(IronManSuits.MARK_2.summonType() == SummonType.SUITCASE_ITEM, "the Mark 2 travels in the case");
		h.assertTrue(IronManSuits.MARK_V.summonType() == SummonType.FLYING_SET, "the Mark 5 Stealth is an ordinary suit now");
		h.assertTrue(IronManSuits.MARK_VII.summonType() == SummonType.TRACKING_POD, "the Mark 7 keeps its orbital pod");
		ServerPlayer p = tony(h);
		h.assertFalse(IronManSuitUpManager.beginSuitUp(p, "mark_2", true), "the Mark 2 never goes on by hand");
		h.assertFalse(IronManSuitUpManager.beginSuitUpFromCase(p, "mark_v"), "the Mark 5 has no case");
		p.getInventory().setItem(0, new ItemStack(IronManItems.MARK_V_SUITCASE));
		h.assertTrue(IronManSuitUpManager.beginSuitUpFromCase(p, "mark_2"), "right-clicking the briefcase builds the Mark 2");
		h.succeedWhen(() -> h.assertTrue("mark_2".equals(com.projecthero.mod.ironman.IronManArmor.wornSuitId(p))
				&& com.projecthero.mod.ironman.IronManArmor.wearingFullSuit(p, "mark_2"), "the full Mark 2 is on"));
	}

	// ------------------------------------------------------------------ tap vs Shift, one per Mark

	/** {@code slot} on {@code suitId}: tap and Shift are different moves, and each has its own cooldown. */
	private static void tapAndShift(GameTestHelper h, String suitId, int slot) {
		ServerPlayer p = tony(h);
		IronManSuit suit = IronManSuits.byId(suitId);
		wear(p, suitId);
		IronManComicKit.Move tap = IronManComicKit.move(suitId, slot, false);
		IronManComicKit.Move shift = IronManComicKit.move(suitId, slot, true);
		h.assertTrue(tap != null && shift != null && !tap.id().equals(shift.id()), suitId + " slot " + slot + ": two different moves");
		h.assertTrue(!tap.cooldownKey().equals(shift.cooldownKey()), "separate cooldown keys");

		p.setShiftKeyDown(false);
		IronManComicRouter.trigger(p, suit, IronManComicKit.SLOTS.get(slot - 1), true);
		IronManComicRouter.trigger(p, suit, IronManComicKit.SLOTS.get(slot - 1), false);
		h.assertFalse(TonyStark.abilityReady(p, suitId, tap.cooldownKey()), suitId + ": the tap move " + tap.id() + " went off (on cooldown)");
		h.assertTrue(TonyStark.abilityReady(p, suitId, shift.cooldownKey()), suitId + ": the Shift move is still ready");

		p.setShiftKeyDown(true);
		IronManComicRouter.trigger(p, suit, IronManComicKit.SLOTS.get(slot - 1), true);
		IronManComicRouter.trigger(p, suit, IronManComicKit.SLOTS.get(slot - 1), false);
		h.assertFalse(TonyStark.abilityReady(p, suitId, shift.cooldownKey()), suitId + ": Shift fired " + shift.id());
		int tapLeft = TonyStark.abilityCooldownRemaining(p, suitId, tap.cooldownKey());
		int shiftLeft = TonyStark.abilityCooldownRemaining(p, suitId, shift.cooldownKey());
		h.assertTrue(Math.abs(tapLeft - tap.cooldownTicks()) <= 1 && Math.abs(shiftLeft - shift.cooldownTicks()) <= 1,
				suitId + ": each runs its own cooldown (" + tapLeft + " / " + shiftLeft + ")");
		p.setShiftKeyDown(false);
		leave(h, p);
		h.succeed();
	}

	// v0.16.7: the five Era I kits (Mk I .. Mk V) are new moves, tested by their own Era I move test classes; the later
	// suits keep these.

	@GameTest(template = EMPTY_STRUCTURE)
	public void markTwoTapAndShiftAreSeparate(GameTestHelper h) {
		tapAndShift(h, "mark_2", 4); // Unibeam / Unibeam Flash
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSixTapAndShiftAreSeparate(GameTestHelper h) {
		tapAndShift(h, "mark_6", 2); // Pulse Bolts / Pulse Storm (v0.16.7 (user): V is Deploy Blades / Blade Whirl now)
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSevenTapAndShiftAreSeparate(GameTestHelper h) {
		tapAndShift(h, "mark_vii", 4); // Solar Beam / Solar Flare
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markEightTapAndShiftAreSeparate(GameTestHelper h) {
		tapAndShift(h, "mark_8", 4); // Chest Mega-Beam / Shoulder Missile Swarm
	}
}
