package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManBlade;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManCenturionBlades;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManComicMoves;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.ability.IronManComicState;
import com.projecthero.mod.ironman.ability.IronManMark9;
import com.projecthero.mod.ironman.ability.era1.IronManEraOne;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.suit.PortableDeploy;
import com.projecthero.mod.ironman.suit.PortableForms;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.7 (user) Era II energy + C utility (+ the Silver Centurion's blades): "all the armours should have energy levels",
 * "move one of the utility abilities of the suit to C while giving the suits another attack ability" and "the silver
 * centurion armour should have a blade ability". {@code docs/IRONMAN_ERA2_KITS.md}.
 */
public class IronManEraTwoEnergyGameTests implements FabricGameTest {
	private static final String[] SUITS = { "mark_2", "mark_6", "mark_vii", "mark_9", "mark_8" };
	private static final float[] CAPS = { 1400f, 1800f, 1600f, 1700f, 2500f };
	private static final String[] C_MOVES = { "mk2_highlight", "mk6_energy_shield", "mk7_zero_g_field", "mk9_thermal_siphon",
			"mk8_hydraulic_brace" };
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };
	private static final float HP = 300f;

	private static ServerPlayer tony(GameTestHelper h, String suit) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(new BlockPos(3, 2, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f);
		p.setXRot(0f);
		p.setYHeadRot(0f);
		p.setOnGround(true);
		if (suit != null) {
			wear(p, suit);
		}
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

	private static LivingEntity husk(GameTestHelper h, ServerPlayer p, double dx, double dz) {
		Mob m = EntityType.HUSK.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180f, 0f);
		m.setNoAi(true);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HP);
		m.setHealth(HP);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.05f;
	}

	// ------------------------------------------------------------------ the reactors

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyEraTwoSuitHasAnArcReactorMk2(GameTestHelper h) {
		for (int i = 0; i < SUITS.length; i++) {
			IronManSuit s = IronManSuits.byId(SUITS[i]);
			h.assertTrue(s.hasReactor() && !s.eraOne() && IronManEraOne.isEraTwo(SUITS[i]), SUITS[i] + " is an Era II reactor suit");
			h.assertTrue(s.energyCapacity() == CAPS[i], SUITS[i] + " capacity " + CAPS[i] + ", got " + s.energyCapacity());
			h.assertTrue(s.energyRegenPerSecond() == 2.5f, SUITS[i] + " regenerates 2.5/s");
			h.assertTrue(IronManEnergy.platformEnergyPerSecond(s) == IronManEnergy.ERA_TWO_PLATFORM_CHARGE_PER_SECOND,
					SUITS[i] + " charges faster on a rack");
		}
		ServerPlayer p = tony(h, "mark_vii");
		IronManEnergy.setEnergy(p, "mark_vii", 100f);
		IronManEnergy.rechargeStep(p, IronManSuits.MARK_VII);
		h.assertTrue(near(IronManEnergy.energy(p, "mark_vii"), 102.5f), "one second of regen: +2.5 (" + IronManEnergy.energy(p, "mark_vii") + ")");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyEraTwoMoveHasAnEnergyPrice(GameTestHelper h) {
		for (String id : SUITS) {
			IronManComicKit.Kit kit = IronManComicKit.kit(id);
			h.assertTrue(kit != null && kit.keys().size() == 6, id + ": six keys");
			for (int slot = 1; slot <= 6; slot++) {
				for (int s = 0; s < 2; s++) {
					IronManComicKit.Move m = kit.key(slot).get(s == 1);
					if (m == null) {
						continue; // Shift+C is the fold
					}
					// the Replacement Pod is free (the field repair); the two hold-C keys pay inside (brace / shield / burst)
					boolean free = "mk8_replacement_pod".equals(m.id()) || "mk8_hydraulic_brace".equals(m.id())
							|| "mk6_energy_shield".equals(m.id());
					float cost = IronManComicKit.energyCost(m.id());
					float perSecond = IronManComicKit.energyPerSecond(m.id());
					h.assertTrue(free || cost > 0f || perSecond > 0f, m.id() + " costs energy");
					h.assertTrue(cost <= 50f && perSecond < 10f, m.id() + " is on the Era I scale (" + cost + " / " + perSecond + "/s)");
				}
			}
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aMovePaysItsCostAndABrownoutRefusesEverything(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_vii");
		IronManSuit suit = IronManSuits.MARK_VII;
		float before = IronManEnergy.energy(p, "mark_vii");
		h.assertTrue(IronManComicRouter.fireForTests(p, suit, 3, false), "Booster Rockets fire");
		h.assertTrue(near(IronManEnergy.energy(p, "mark_vii"), before - IronManComicKit.energyCost("mk7_booster_rockets")),
				"and pay 15");
		// not enough for a move: refused, nothing paid
		IronManEnergy.setEnergy(p, "mark_vii", 10f);
		h.assertFalse(IronManComicRouter.fireForTests(p, suit, 2, true), "Orbital Strike (45) refused on 10");
		h.assertTrue(near(IronManEnergy.energy(p, "mark_vii"), 10f), "nothing taken");
		// 0 = brownout: every move refused, flight drops and will not start, until 10% is back
		IronManFlight.setFlying(p, true);
		IronManEnergy.setEnergy(p, "mark_vii", 0f);
		h.assertTrue(IronManEnergy.brownedOut(p, "mark_vii"), "browned out at 0");
		h.assertFalse(IronManFlight.isFlying(p), "flight dropped");
		IronManEnergy.setEnergy(p, "mark_vii", 100f);
		h.assertTrue(IronManEnergy.brownedOut(p, "mark_vii"), "still out under 10% (100 of 1600)");
		h.assertFalse(IronManComicRouter.fireForTests(p, suit, 1, false), "LOW POWER: even the repulsor refuses");
		IronManFlight.setFlying(p, true);
		com.projecthero.mod.ironman.IronManSuitTicker.tick(p);
		h.assertFalse(IronManFlight.isFlying(p), "no flight while browned out (the suit ticker lands it)");
		IronManEnergy.setEnergy(p, "mark_vii", 170f);
		h.assertFalse(IronManEnergy.brownedOut(p, "mark_vii"), "back online at 10%");
		h.assertTrue(IronManComicRouter.fireForTests(p, suit, 1, false), "moves work again");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightDrainsTwoASecondAndFourSprinting(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_6");
		IronManFlight.setFlying(p, true);
		p.setOnGround(false);
		float before = IronManEnergy.energy(p, "mark_6");
		for (int i = 0; i < 20; i++) {
			IronManEnergy.flightDrainTick(p, IronManSuits.MARK_6);
		}
		h.assertTrue(near(IronManEnergy.energy(p, "mark_6"), before - 2f), "2 a second hovering (" + IronManEnergy.energy(p, "mark_6") + ")");
		p.setSprinting(true);
		for (int i = 0; i < 20; i++) {
			IronManEnergy.flightDrainTick(p, IronManSuits.MARK_6);
		}
		h.assertTrue(near(IronManEnergy.energy(p, "mark_6"), before - 6f), "4 a second sprint-flying");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFoldedFormCarriesTheCharge(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_2");
		IronManEnergy.setEnergy(p, "mark_2", 700f);
		ItemStack packed = PortableDeploy.pack(p, PortableForms.byId("mark_2"));
		h.assertTrue(!packed.isEmpty() && near(IronManEnergy.stackEnergy(packed, "mark_2"), 700f),
				"the briefcase is stamped with 700 (" + IronManEnergy.stackEnergy(packed, "mark_2") + ")");
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "packed away");
		IronManEnergy.setEnergy(p, "mark_2", 1400f);
		h.assertTrue(PortableDeploy.deploy(p, "mark_2"), "deploys again");
		h.assertTrue(near(IronManEnergy.energy(p, "mark_2"), 700f), "and the reactor comes back at 700");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ C = a utility, the vacated slot = a new attack

	@GameTest(template = EMPTY_STRUCTURE)
	public void cIsAUtilityOnEveryEraTwoSuit(GameTestHelper h) {
		for (int i = 0; i < SUITS.length; i++) {
			IronManSuit s = IronManSuits.byId(SUITS[i]);
			h.assertTrue(IronManComicKit.SLOT_C.equals(s.abilityInSlot(6)), SUITS[i] + ": C is a kit key");
			IronManComicKit.Key c = IronManComicKit.kit(SUITS[i]).key(6);
			h.assertTrue(C_MOVES[i].equals(c.tap().id()) && c.shift() == null, SUITS[i] + ": C = " + C_MOVES[i] + ", Shift+C = the fold");
			h.assertTrue(PortableForms.byId(SUITS[i]) != null, SUITS[i] + " still has a portable form for Shift+C");
		}
		h.assertTrue("mk2_wrist_laser".equals(IronManComicKit.move("mark_2", 5, false).id()), "Mk VI V = Wrist Laser");
		h.assertTrue("mk6_deploy_blades".equals(IronManComicKit.move("mark_6", 5, false).id())
				&& "mk6_blade_whirl".equals(IronManComicKit.move("mark_6", 5, true).id()), "Mk VII V = Deploy Blades / Blade Whirl");
		h.assertTrue("mk7_singularity".equals(IronManComicKit.move("mark_vii", 5, true).id()), "Mk VIII Shift+V = Singularity");
		h.assertTrue("mk9_molten_lash".equals(IronManComicKit.move("mark_9", 5, true).id()), "Mk IX Shift+V = Molten Lash");
		h.assertTrue("mk8_palm_cannon".equals(IronManComicKit.move("mark_8", 5, false).id()), "Hulkbuster V = Palm Cannon");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMkSixHighlightIsOnCAndTheWristLaserCuts(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_2");
		h.assertTrue(IronManComicRouter.fireForTests(p, IronManSuits.MARK_2, 6, false), "C toggles the highlight");
		LivingEntity husk = husk(h, p, 0, 6);
		float before = IronManEnergy.energy(p, "mark_2");
		h.assertTrue(IronManComicRouter.fireForTests(p, IronManSuits.MARK_2, 5, false), "V fires the Wrist Laser");
		h.assertTrue(husk.getHealth() < HP, "the laser cut the husk (" + husk.getHealth() + ")");
		h.assertTrue(near(IronManEnergy.energy(p, "mark_2"), before - 15f), "and cost 15");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theSingularityDragsInAndImplodes(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_vii");
		LivingEntity a = husk(h, p, 0, 6); // under the crosshair: the grenade lands on it
		LivingEntity b = husk(h, p, -2.5, 8);
		h.assertTrue(IronManComicRouter.fireForTests(p, IronManSuits.MARK_VII, 5, true), "Shift+V throws the Singularity");
		h.runAfterDelay(IronManComicMoves.SINGULARITY_IMPLODE + 3, () -> {
			h.assertTrue(a.getHealth() < HP && b.getHealth() < HP, "the implosion hurt both (" + a.getHealth() + " / " + b.getHealth() + ")");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void theMoltenLashBurnsTheArcAndHeatsTheSink(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_9");
		IronManMark9.setHeat(p, 0f);
		LivingEntity husk = husk(h, p, 1.0, 4);
		h.assertTrue(IronManComicRouter.fireForTests(p, IronManSuits.MARK_9, 5, true), "Shift+V cracks the Molten Lash");
		h.assertTrue(IronManMark9.heat(p) >= IronManMark9.LASH_HEAT - 0.01f, "it puts heat in the sink");
		h.runAfterDelay(IronManMark9.LASH_HIT + 3, () -> {
			h.assertTrue(husk.getHealth() < HP && husk.getRemainingFireTicks() > 0, "the lash hit and set it burning");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theHulkbusterPalmCannonAndItsCBraceOrSentry(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_8");
		IronManSuit hb = IronManSuits.MARK_8;
		LivingEntity husk = husk(h, p, 0, 7);
		h.assertTrue(IronManComicRouter.fireForTests(p, hb, 5, false), "V fires the Palm Cannon");
		// C tapped: the Hydraulic Brace (own cooldown + 15 energy)
		float before = IronManEnergy.energy(p, "mark_8");
		h.assertTrue(IronManComicMoves.hulkbusterCForTests(p, hb, 2), "a C tap braces");
		h.assertTrue(IronManComicState.braced(p), "braced");
		h.assertTrue(near(IronManEnergy.energy(p, "mark_8"), before - IronManComicMoves.BRACE_ENERGY), "paid 15");
		h.assertFalse(TonyStark.abilityReady(p, "mark_8", "mk8_hydraulic_brace"), "the brace cools down");
		h.assertFalse(IronManComicMoves.hulkbusterCForTests(p, hb, 2), "a second tap is on cooldown");
		h.runAfterDelay(IronManComicMoves.HB_CANNON_FIRE + 4, () -> {
			h.assertTrue(husk.getHealth() < HP, "the palm cannon hit (" + husk.getHealth() + ")");
			// C held 1 s: Sentry Mode, even with the brace cooling down
			IronManComicMoves.hulkbusterCForTests(p, hb, IronManComicMoves.SENTRY_HOLD_TICKS + 1);
			h.assertFalse(IronManArmor.wearingAnyIronMan(p), "held C steps out of the suit");
			h.assertTrue(h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(4),
					s -> p.getUUID().equals(s.ownerId())).size() == 1, "and leaves it standing as a sentry");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theCenturionCIsShieldOrOverloadBurst(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_6");
		IronManSuit m6 = IronManSuits.MARK_6;
		float e0 = IronManEnergy.energy(p, "mark_6");
		h.assertTrue(IronManComicMoves.centurionCForTests(p, m6, 2), "C tap: Energy Shield");
		h.assertTrue(IronManComicState.shielded(p), "shield up");
		h.assertTrue(near(IronManEnergy.energy(p, "mark_6"), e0 - IronManComicMoves.SHIELD_ENERGY), "paid 25");
		LivingEntity husk = husk(h, p, 2, 2);
		h.assertTrue(IronManComicMoves.centurionCForTests(p, m6, IronManComicMoves.SENTRY_HOLD_TICKS + 1), "C held: Overload Burst");
		h.assertTrue(husk.getHealth() <= HP - IronManComicMoves.BURST_DAMAGE + 0.5f, "the burst hit for 30 (" + husk.getHealth() + ")");
		h.assertFalse(TonyStark.abilityReady(p, "mark_6", "mk6_overload_burst") || TonyStark.abilityReady(p, "mark_6", "mk6_energy_shield"),
				"each on its own cooldown");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ the Silver Centurion's blades

	@GameTest(template = EMPTY_STRUCTURE)
	public void centurionBladesDeployDrainSweepAndRetract(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_6");
		IronManSuit m6 = IronManSuits.MARK_6;
		float e0 = IronManEnergy.energy(p, "mark_6");
		h.assertTrue(IronManComicRouter.fireForTests(p, m6, 5, false), "V deploys the blades");
		h.assertTrue(IronManBlade.active(p) && IronManCenturionBlades.out(p), "blades out");
		h.assertTrue(near(IronManEnergy.energy(p, "mark_6"), e0 - 10f), "deploying costs 10");
		for (int i = 0; i < 20; i++) {
			IronManBlade.tick(p);
		}
		h.assertTrue(near(IronManEnergy.energy(p, "mark_6"), e0 - 11f), "then 1 a second (" + IronManEnergy.energy(p, "mark_6") + ")");
		var mod = p.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(com.projecthero.mod.ProjectHeroMod.id("iron_man_blade_strength"));
		h.assertTrue(mod != null && mod.amount() == IronManBlade.CENTURION_MELEE_BONUS, "+6 melee while out");
		// a connected swing sweeps the enemies round the target
		LivingEntity target = husk(h, p, 0, 2);
		LivingEntity beside = husk(h, p, 1.2, 2.5);
		IronManCenturionBlades.sweepForTests(p, target);
		h.assertTrue(beside.getHealth() <= HP - IronManCenturionBlades.SWEEP_DAMAGE + 0.5f, "the sweep slashed the husk beside it");
		// V again retracts, free
		var st = TonyStark.state(p).copy();
		st.abilityReadyAt.clear();
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, st);
		float e1 = IronManEnergy.energy(p, "mark_6");
		IronManComicRouter.fireForTests(p, m6, 5, false);
		h.assertFalse(IronManBlade.active(p), "V again retracts them");
		h.assertTrue(near(IronManEnergy.energy(p, "mark_6"), e1), "retracting is free");
		// Shift+V: the Blade Whirl snaps them out
		h.assertTrue(IronManComicRouter.fireForTests(p, m6, 5, true), "Shift+V: Blade Whirl");
		h.assertTrue(IronManBlade.active(p), "the whirl deploys the blades");
		// a brownout retracts them
		IronManEnergy.setEnergy(p, "mark_6", 0f);
		h.assertFalse(IronManBlade.active(p), "a brownout pulls the blades in");
		leave(h, p);
		h.succeed();
	}
}
