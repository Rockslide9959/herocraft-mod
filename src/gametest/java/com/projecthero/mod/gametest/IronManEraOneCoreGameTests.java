package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.era1.EraOneEject;
import com.projecthero.mod.ironman.ability.era1.EraOneGantry;
import com.projecthero.mod.ironman.ability.era1.IronManEraOne;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.gantry.StarkGantry;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitCrateItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * v0.16.7 (user) Iron Man ERA I -- THE GANTRY AGE, the CORE rules ({@code docs/IRONMAN_ERA1_PLAN.md}): the five suits'
 * order, names and stats; the reactor (move costs, per-second drains, the Mk III's flight drain, regen, the +50/s Gantry
 * charge, the brownout at 0 until 10%); Gantry-only suit-up / removal / repair; and the Shift+C emergency eject into a
 * Suit Crate, which only opens in a Gantry.
 */
public class IronManEraOneCoreGameTests implements FabricGameTest {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };
	private static final String[] ERA_ONE = { "mark_1", "mark_1_v2", "mark_iii", "mark_4", "mark_v" };
	private static final BlockPos CENTRE = new BlockPos(2, 1, 2);
	private static final BlockPos PLATFORM = new BlockPos(6, 2, 6);

	private static ServerPlayer tony(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 6));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	private static void wear(ServerPlayer p, String suit) {
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(suit, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, suit);
		IronManEnergy.setIntegrity(p, suit, IronManEnergy.maxIntegrity(suit));
		IronManEnergy.setEnergy(p, suit, IronManEnergy.capacity(suit));
	}

	private static void carry(ServerPlayer p, String suit) {
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(suit, t)));
		}
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static boolean wearingAny(ServerPlayer p) {
		return IronManArmor.wearingAnyIronMan(p);
	}

	/** v0.16.7 (user): the eject now folds the suit into a crate BLOCK; for the item paths, pack it straight into the item. */
	private static ItemStack ejectToItem(ServerPlayer p) {
		ItemStack crate = SuitCrateItem.of(EraOneEject.packSuit(p));
		p.getInventory().add(crate.copy());
		return crate;
	}

	private static ItemStack findCrate(ServerPlayer p) {
		for (ItemStack s : p.getInventory().items) {
			if (s.is(IronManItems.SUIT_CRATE)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	private static String lang(String key) {
		try (var in = IronManEraOneCoreGameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
			if (in == null) {
				return null;
			}
			var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
					.getAsJsonObject();
			return json.has(key) ? json.get(key).getAsString() : null;
		} catch (java.io.IOException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ order, names, stats

	@GameTest(template = EMPTY_STRUCTURE)
	public void eraOneOrderNamesAndStats(GameTestHelper h) {
		h.assertTrue(IronManItems.MARK_ORDER.equals(java.util.List.of("mark_1", "mark_1_v2", "mark_iii", "mark_4", "mark_v",
				"mark_2", "mark_6", "mark_vii", "mark_8")), "the Era I order, then the later suits: " + IronManItems.MARK_ORDER);
		h.assertTrue(FabricatorRecipes.ARMOR_BUILD_ORDER.equals(java.util.List.of("mark_1_v2", "mark_iii", "mark_4", "mark_v", "mark_2",
				"mark_6", "mark_vii", "mark_8")), "the Fabricator build order follows it");
		h.assertTrue("mark_v".equals(IronManItems.prerequisiteSuit("mark_2")) && "mark_4".equals(IronManItems.prerequisiteSuit("mark_v"))
				&& "mark_2".equals(IronManItems.prerequisiteSuit("mark_6")), "blueprint prerequisites follow the order");
		float[] integ = { 700, 850, 1400, 1600, 500 };
		float[] absorb = { 0.30f, 0.32f, 0.38f, 0.40f, 0.15f };
		float[] cap = { 600, 800, 1200, 1200, 1000 };
		float[] regen = { 1.0f, 1.5f, 2.0f, 2.0f, 2.5f };
		float[] melee = { 8, 7, 8, 8, 6 };
		String[] names = { "Mk I Cave-Forged", "Mk II Golden Avenger", "Mk III Classic", "Mk IV Hydro", "Mk V Stealth" };
		for (int i = 0; i < ERA_ONE.length; i++) {
			IronManSuit s = IronManSuits.byId(ERA_ONE[i]);
			h.assertTrue(s.eraOne() && IronManEraOne.isEraOne(ERA_ONE[i]), ERA_ONE[i] + " is Era I");
			h.assertTrue(s.maxIntegrity() == integ[i] && s.absorbShare() == absorb[i], ERA_ONE[i] + " integrity / absorb");
			h.assertTrue(s.energyCapacity() == cap[i] && s.energyRegenPerSecond() == regen[i], ERA_ONE[i] + " reactor " + s.energyCapacity());
			h.assertTrue(s.strengthBonus() == melee[i], ERA_ONE[i] + " melee +" + melee[i]);
			h.assertTrue(s.progressionIndex() == i, ERA_ONE[i] + " progression " + i);
			h.assertTrue(IronManComicKit.SLOT_C.equals(s.abilityInSlot(6)), ERA_ONE[i] + ": C is a kit key");
			IronManComicKit.Key c = IronManComicKit.kit(ERA_ONE[i]).key(6);
			h.assertTrue(c != null && c.tap() != null && c.shift() == null, ERA_ONE[i] + ": C tap move, Shift+C = the eject");
			// the short mark (.name, HUD / cards) + the comic subtitle (.comic) = the display name the armour items carry
			String name = lang(s.nameKey()) + " " + lang(s.displayKey());
			h.assertTrue(lang(s.nameKey()) == null || names[i].equals(name), ERA_ONE[i] + " is called " + names[i] + ", got " + name);
			String item = lang(s.armor(ArmorItem.Type.HELMET).getDescriptionId());
			h.assertTrue(item == null || (names[i] + " Helmet").equals(item), ERA_ONE[i] + " helmet item name, got " + item);
		}
		h.assertTrue(IronManSuits.MARK_1.scale() == 1.0f && IronManSuits.MARK_1.walkSpeedBonus() == -0.10 && !IronManSuits.MARK_1.manualFlight(),
				"Mk I: normal size, walks 10% slower, no flight");
		h.assertTrue(IronManSuits.MARK_III.manualFlight() && !IronManSuits.MARK_1_V2.manualFlight() && !IronManSuits.MARK_4.manualFlight()
				&& !IronManSuits.MARK_V.manualFlight(), "only the Mk III flies");
		h.assertTrue(IronManSuits.MARK_4.hydro() && IronManSuits.MARK_4.waterBreathing() && IronManSuits.MARK_V.stealth(), "Hydro / Stealth");
		for (String later : new String[] { "mark_2", "mark_6", "mark_vii", "mark_8" }) {
			h.assertTrue(!IronManEraOne.isEraOne(later) && IronManSuits.byId(later).energyCapacity() == IronManEnergy.FULL,
					later + " keeps the no-energy shim");
		}
		String[] later = { "mark_2", "mark_6", "mark_vii", "mark_8" };
		String[] laterNames = { "Mk VI Suitcase", "Mk VII Silver Centurion", "Mk VIII Space", "Hulkbuster I Bruiser" };
		for (int i = 0; i < later.length; i++) {
			String name = lang(IronManSuits.byId(later[i]).armor(ArmorItem.Type.HELMET).getDescriptionId());
			h.assertTrue(name == null || (laterNames[i] + " Helmet").equals(name), later[i] + " is renamed " + laterNames[i] + ", got " + name);
		}
		String reactor = lang("item.projecthero.arc_reactor");
		h.assertTrue(reactor == null || "Arc Reactor Mk1".equals(reactor), "the Arc Reactor Mk1, got " + reactor);
		// the Arc Reactor Mk1 is iron + copper + redstone
		var recipe = h.getLevel().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("arc_reactor"));
		h.assertTrue(recipe.isPresent(), "Arc Reactor recipe");
		java.util.Set<net.minecraft.world.item.Item> in = new java.util.HashSet<>();
		for (var ing : recipe.get().value().getIngredients()) {
			for (ItemStack s : ing.getItems()) {
				in.add(s.getItem());
			}
		}
		h.assertTrue(in.contains(Items.IRON_INGOT) && in.contains(Items.COPPER_INGOT) && in.contains(Items.REDSTONE_BLOCK)
				&& in.size() == 3, "iron, copper and redstone only: " + in);
		h.succeed();
	}

	// ------------------------------------------------------------------ the reactor

	@GameTest(template = EMPTY_STRUCTURE)
	public void movesPayTheirEnergyAndTheSuitBrownsOut(GameTestHelper h) {
		ServerPlayer p = tony(h);
		IronManSuit suit = IronManSuits.MARK_III;
		wear(p, "mark_iii");
		IronManEnergy.setEnergy(p, "mark_iii", 100f);
		IronManComicKit.Move unibeam = IronManComicKit.byId("e1mk3_unibeam"); // 150
		IronManComicKit.Move repulsor = IronManComicKit.byId("e1mk3_repulsor"); // 20
		h.assertFalse(IronManEraOne.canPay(p, suit, unibeam), "100 energy can't pay a 150 unibeam (LOW POWER)");
		h.assertTrue(IronManEraOne.canPay(p, suit, repulsor), "but pays a 20 repulsor");
		IronManEraOne.pay(p, suit, repulsor);
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_iii") - 80f) < 1e-3f, "paid 20 -> 80, got " + IronManEnergy.energy(p, "mark_iii"));
		// a held move drains its per-second cost each tick (tractor beam 15/s)
		h.assertTrue(IronManEraOne.drainTick(p, suit, "e1mk3_tractor_beam"), "drains while there is energy");
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_iii") - (80f - 0.75f)) < 1e-3f, "15/s = 0.75 a tick");
		// run it dry: brownout
		IronManEnergy.setEnergy(p, "mark_iii", 1.0f);
		h.assertFalse(IronManEraOne.drainTick(p, suit, "e1mk3_tractor_beam") && IronManEraOne.drainTick(p, suit, "e1mk3_tractor_beam"),
				"the drain stops the move once the reactor is dry");
		h.assertTrue(IronManEnergy.energy(p, "mark_iii") == 0f && IronManEnergy.brownedOut(p, "mark_iii"), "0 energy = BROWNOUT");
		h.assertFalse(IronManEraOne.canPay(p, suit, repulsor), "no moves while browned out");
		h.assertFalse(IronManEraOne.drainTick(p, suit, "e1mk3_targeting_hud"), "no toggles either");
		// armour + absorb still work while browned out
		h.assertTrue(com.projecthero.mod.ironman.IronManDamage.absorb(p, suit, h.getLevel().damageSources().generic(), 10f) < 10f,
				"a browned-out suit still absorbs");
		// back online only at 10%
		IronManEnergy.setEnergy(p, "mark_iii", 100f); // 8.3% of 1200
		h.assertTrue(IronManEnergy.brownedOut(p, "mark_iii"), "still browned out below 10%");
		IronManEnergy.setEnergy(p, "mark_iii", 121f);
		h.assertFalse(IronManEnergy.brownedOut(p, "mark_iii"), "back online at 10%");
		h.assertTrue(IronManEraOne.canPay(p, suit, repulsor), "moves again");
		// a later suit never pays anything
		h.assertTrue(IronManEraOne.canPay(p, IronManSuits.MARK_6, IronManComicKit.byId("mk6_repulsor")), "no energy on the Centurion");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMarkThreesFlightDrainsAndABrownoutLandsIt(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_iii");
		IronManEnergy.setEnergy(p, "mark_iii", 500f);
		IronManFlight.setFlying(p, true);
		p.setSprinting(false);
		IronManEnergy.flightDrainTick(p, IronManSuits.MARK_III);
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_iii") - (500f - 0.3f)) < 1e-3f, "flight 6/s = 0.3 a tick");
		p.setSprinting(true);
		IronManEnergy.flightDrainTick(p, IronManSuits.MARK_III);
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_iii") - (500f - 0.9f)) < 1e-3f, "sprint flight 12/s = 0.6 a tick");
		IronManEnergy.setEnergy(p, "mark_iii", 0.2f);
		IronManEnergy.flightDrainTick(p, IronManSuits.MARK_III);
		h.assertFalse(IronManFlight.isFlying(p), "running dry lands it");
		h.assertTrue(IronManEnergy.brownedOut(p, "mark_iii"), "browned out");
		IronManFlight.toggle(p);
		h.assertFalse(IronManFlight.isFlying(p), "no take-off while browned out");
		// the Mk II / IV / V have no sustained flight at all
		wear(p, "mark_1_v2");
		IronManFlight.toggle(p);
		h.assertFalse(IronManFlight.isFlying(p), "the Mk II only hovers with its X move");
		p.setSprinting(false);
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theReactorRegensSlowlyAndFastInAGantry(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_1");
		IronManEnergy.setEnergy(p, "mark_1", 100f);
		IronManEnergy.setIntegrity(p, "mark_1", 300f);
		IronManEnergy.rechargeStep(p, IronManSuits.MARK_1);
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_1") - 101f) < 1e-3f, "Mk I regen 1/s");
		h.assertTrue(IronManEnergy.integrity(p, "mark_1") == 300f, "no repair away from a Gantry");
		IronManGantryV0154GameTests.floor(h, CENTRE);
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		h.assertTrue(EraOneGantry.onGantryFloor(p), "standing on the Gantry floor");
		IronManEnergy.rechargeStep(p, IronManSuits.MARK_1);
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_1") - 151f) < 1e-3f, "+50/s on the Gantry floor, got " + IronManEnergy.energy(p, "mark_1"));
		h.assertTrue(IronManEnergy.integrity(p, "mark_1") == 305f, "and it repairs 5/s there");
		// stacks keep an Era I suit's energy; a dry stack loads browned out
		ItemStack chest = new ItemStack(IronManItems.armor("mark_1", ArmorItem.Type.CHESTPLATE));
		IronManEnergy.stampStack(chest, 222f, 400f);
		h.assertTrue(chest.has(ModDataComponents.SUIT_ENERGY) && IronManEnergy.stackEnergy(chest, "mark_1") == 222f, "the energy rides on the stack");
		IronManEnergy.stampStack(chest, 0f, 400f);
		IronManEnergy.loadFromStack(p, "mark_1", chest);
		h.assertTrue(IronManEnergy.energy(p, "mark_1") == 0f && IronManEnergy.brownedOut(p, "mark_1"), "a dry suit comes on browned out");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "era1_platform_no_gantry")
	public void aSuitPlatformWithoutAGantryDoesNotRepairAnEraOneSuit(GameTestHelper h) {
		ServerPlayer p = tony(h);
		h.setBlock(PLATFORM, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(PLATFORM);
		be.bindTo(p.getUUID());
		for (ArmorItem.Type t : TYPES) {
			ItemStack s = new ItemStack(IronManItems.armor("mark_iii", t));
			IronManEnergy.stampStack(s, 100f, 500f);
			h.assertTrue(be.store(s), "racked " + t.getName());
		}
		be.recheckGantry();
		h.assertFalse(be.gantryInRange(), "no Gantry here");
		be.regenTick(IronManSuits.MARK_III);
		h.assertTrue(be.suitIntegrity() == 500f, "no repair without a Gantry, got " + be.suitIntegrity());
		h.assertTrue(Math.abs(be.suitEnergy() - (100f + 2.0f / 20f)) < 1e-3f, "only the suit's own slow regen, got " + be.suitEnergy());
		// a complete Gantry within 20 blocks: repairs 5/s and charges +50/s
		IronManGantryV0154GameTests.floor(h, CENTRE);
		be.recheckGantry();
		h.assertTrue(be.gantryInRange() && EraOneGantry.platformInRange(h.getLevel(), be.getBlockPos()), "Gantry in range");
		float e0 = be.suitEnergy();
		be.regenTick(IronManSuits.MARK_III);
		h.assertTrue(Math.abs(be.suitIntegrity() - 500.25f) < 1e-3f, "5/s repair, got " + be.suitIntegrity());
		h.assertTrue(Math.abs(be.suitEnergy() - (e0 + 2.5f)) < 1e-3f, "+50/s charge, got " + be.suitEnergy());
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ the Gantry-only rule

	@GameTest(template = EMPTY_STRUCTURE)
	public void eraOneSuitsRefuseEveryWayButTheGantry(GameTestHelper h) {
		ServerPlayer p = tony(h);
		carry(p, "mark_1");
		h.assertFalse(IronManSuitUpManager.beginSuitUp(p, "mark_1", true), "no C hand build");
		h.assertFalse(IronManSuitUpManager.beginSuitUp(p, "mark_1"), "no plain suit-up");
		h.assertFalse(IronManSuitUpManager.toggleSuit(p, "mark_1"), "no toggle");
		h.assertFalse(IronManSuitCall.commandCall(p, "mark_1"), "no command call");
		h.assertFalse(IronManSuitCall.callPiece(p, "mark_1", ArmorItem.Type.HELMET), "no single-piece call");
		IronManSuitCall.execute(p, "mark_1", IronManSuitListPayload.SOURCE_INVENTORY);
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(p), "C answers (with the Gantry message)...");
		h.assertFalse(wearingAny(p) || IronManSuitUpManager.inTransition(p), "...but nothing goes on");
		for (IronManSuitListPayload.Option o : IronManSuitCall.assemblableSuits(p)) {
			h.assertFalse(IronManEraOne.isEraOne(o.suitId()), "no call / Phoenix listing offers an Era I suit: " + o.suitId());
		}
		// worn: no C store / hand removal either
		p.getInventory().clearContent();
		wear(p, "mark_iii");
		h.assertFalse(IronManSuitUpManager.beginSuitDown(p, "mark_iii"), "no hand removal");
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii"), "still worn");
		// a later suit is unaffected
		for (EquipmentSlot s : SLOTS) {
			p.setItemSlot(s, ItemStack.EMPTY);
		}
		TonyStark.setActiveSuit(p, "");
		carry(p, "mark_6");
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_6", true), "the Centurion still goes on by hand");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theGantryStillSuitsUpAnEraOneSuit(GameTestHelper h) {
		ServerPlayer p = tony(h);
		IronManGantryV0154GameTests.floor(h, CENTRE);
		h.setBlock(PLATFORM, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(PLATFORM);
		be.bindTo(p.getUUID());
		for (ArmorItem.Type t : TYPES) {
			ItemStack s = new ItemStack(IronManItems.armor("mark_4", t));
			IronManEnergy.stampStack(s, 700f, 1000f);
			be.store(s);
		}
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		h.assertTrue(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "the Gantry fits an Era I suit");
		h.runAfterDelay(GantryTimeline.FULL.total() + 6, () -> {
			h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_4"), "the Mk IV is on");
			h.assertTrue(IronManEnergy.energy(p, "mark_4") >= 700f, "with the energy it was racked with");
			leave(h, p);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ eject -> crate -> gantry

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void holdingShiftCEjectsTheSuitIntoACrate(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_iii");
		IronManEnergy.setIntegrity(p, "mark_iii", 0f); // even a wrecked suit ejects
		IronManEnergy.setEnergy(p, "mark_iii", 400f);
		p.setShiftKeyDown(true);
		IronManEraOne.ejectKey(p, IronManSuits.MARK_III, true);
		h.assertTrue(EraOneEject.holding(p), "the hold started");
		// a release before 3 s cancels
		IronManEraOne.ejectKey(p, IronManSuits.MARK_III, false);
		h.assertFalse(EraOneEject.holding(p), "released = cancelled");
		IronManEraOne.ejectKey(p, IronManSuits.MARK_III, true);
		h.runAfterDelay(EraOneEject.HOLD_TICKS / 2, () -> h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii"), "still on at 1.5 s"));
		h.succeedWhen(() -> {
			h.assertFalse(wearingAny(p), "the suit came off");
			// v0.16.7 (user): the empty suit stands, then folds into a Suit Crate BLOCK (SuitCrateBlockGameTests has the rest)
			com.projecthero.mod.ironman.crate.SuitCrateBlockEntity be = null;
			for (BlockPos q : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 0, 3)), h.absolutePos(new BlockPos(4, 3, 7)))) {
				if (h.getLevel().getBlockEntity(q) instanceof com.projecthero.mod.ironman.crate.SuitCrateBlockEntity c) {
					be = c;
				}
			}
			h.assertTrue(be != null && be.mask() == 15 && "mark_iii".equals(be.suitId()), "all four pieces in a Suit Crate");
			ItemStack chest = be.reference();
			float en = IronManEnergy.stackEnergy(chest, "mark_iii");
			// (the reactor regens 2/s while the hold runs)
			h.assertTrue(IronManEnergy.stackIntegrity(chest, "mark_iii") == 0f && en >= 400f && en <= 412f,
					"with their integrity and energy, got " + IronManEnergy.stackIntegrity(chest, "mark_iii") + " / " + en);
			p.setShiftKeyDown(false);
			leave(h, p);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theCrateOnlyOpensInAGantry(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_1_v2");
		IronManEnergy.setIntegrity(p, "mark_1_v2", 420f);
		IronManEnergy.setEnergy(p, "mark_1_v2", 300f);
		ItemStack crate = ejectToItem(p);
		h.assertTrue(!crate.isEmpty() && !wearingAny(p), "ejected");
		ItemStack carried = findCrate(p);
		SuitCrateItem.applyWeight(p);
		MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		h.assertTrue(slow != null && slow.getAmplifier() == 0, "the crate is heavy: Slowness I");
		// off the Gantry: it does not open
		p.setItemInHand(InteractionHand.MAIN_HAND, carried.copy());
		carried.shrink(1);
		IronManItems.SUIT_CRATE.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
		h.assertTrue(p.getMainHandItem().is(IronManItems.SUIT_CRATE) && !wearingAny(p), "nothing happens away from a Gantry");
		// (the refusal far from a Gantry is aCrateWillNotRackFarFromAGantry -- its own batch, no Gantry anywhere near)
		h.setBlock(PLATFORM, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(PLATFORM);
		be.bindTo(p.getUUID());
		IronManGantryV0154GameTests.floor(h, CENTRE);
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		h.assertTrue(StarkGantry.beginEquipFromCrate(p, InteractionHand.MAIN_HAND), "on the Gantry floor the arms take the crate");
		h.assertTrue(p.getMainHandItem().isEmpty(), "the crate is used up");
		h.runAfterDelay(GantryTimeline.FULL.total() + 6, () -> {
			h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_1_v2"), "the crated Mk II is back on");
			h.assertTrue(IronManEnergy.integrity(p, "mark_1_v2") >= 420f && IronManEnergy.energy(p, "mark_1_v2") >= 300f,
					"with its integrity and energy");
			// eject again and rack it on the platform in range of this Gantry
			ItemStack again = ejectToItem(p);
			ItemStack held = findCrate(p);
			h.assertTrue(!again.isEmpty() && !held.isEmpty(), "ejected again");
			be.recheckGantry();
			h.assertTrue(SuitCrateItem.rackOn(p, be, held), "a Suit Platform in the Gantry's range racks the crate");
			h.assertTrue(be.pieceMask() == 15 && "mark_1_v2".equals(be.storedSuitId()) && findCrate(p).isEmpty(), "all four racked, crate gone");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "era1_platform_no_gantry")
	public void aCrateWillNotRackFarFromAGantry(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_v");
		ejectToItem(p);
		ItemStack crate = findCrate(p);
		h.setBlock(PLATFORM, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(PLATFORM);
		be.bindTo(p.getUUID());
		h.assertFalse(SuitCrateItem.rackOn(p, be, crate), "no Gantry in range: refused");
		h.assertTrue(be.isEmptyPlatform() && !findCrate(p).isEmpty(), "nothing moved");
		for (ItemStack s : SuitCrateItem.pieces(crate)) {
			h.assertTrue(s.getItem() instanceof IronManArmorItem a && "mark_v".equals(a.suitId()), "the crate still holds the Mk V");
		}
		leave(h, p);
		h.succeed();
	}
}
