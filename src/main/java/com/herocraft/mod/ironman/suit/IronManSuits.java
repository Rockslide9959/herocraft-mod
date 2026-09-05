package com.herocraft.mod.ironman.suit;

import java.util.LinkedHashMap;
import java.util.Map;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.ironman.ability.IronManAbilities;
import com.herocraft.mod.ironman.item.IronManItems;

/**
 * The registry of Iron Man suits. The first content pack ships Mark III, V, VII, XLII (42) and L (50)
 * -- the same reusable {@link IronManSuit} shape, so Mark I / II / IV / VI / VIII / Hulkbuster /
 * Silver Centurion / Stealth / War Machine / Iron Patriot / Superior / Rescue are each just a new
 * entry plus a recipe set later, never a new code path.
 *
 * <p>All five share the same six ability ids (spec section 30: "Do not duplicate entire systems") --
 * only the tuning numbers on each definition differ.
 */
public final class IronManSuits {
	private static final Map<String, IronManSuit> BY_ID = new LinkedHashMap<>();
	private static final Map<Integer, IronManSuit> BY_MARK = new LinkedHashMap<>();

	// Mark 1 / Mark 2 ("changes 12"): primitive first-generation prototypes, craftable at a normal
	// table (see IronManItems.PRIMITIVE_SUIT_IDS) rather than gated behind the Fabricator + a
	// blueprint. techLevel 0 keeps beginSuitUp's tech-level gate (`techLevel(player) < suit.techLevel()`)
	// trivially satisfied the instant a player has the Tony Stark power.
	public static final IronManSuit MARK_1 = register(IronManSuit.Builder.of("mark_1")
			.tech(0, 1)
			.energy(3_000f, 2.0f, 3.0f)
			.maxIntegrity(300f) // "changes 18": Mark 1 condition pool
			.energyRegen(0.7f) // "changes 18"; nudged up from 0.5 -- slight Mark 1 recharge buff
			.flamethrowerHeat(0.6f) // "changes 22": Mark 1 heat ceiling 300 (500 x 0.6) -- ~7.9 s of stream before it overheats
			.flightDrain(0.55f) // "changes 18"
			.targetScanRange(30.0) // "changes 14"
			.flight(0.75f, 0.055f) // "changes 18": flies 25% slower than the other marks
			.repulsor(0f, 0f) // no repulsor on this loadout -- see abilities() below
			.unibeam(0f, 0f)  // no unibeam on this loadout
			.missiles(0, 0f, 0f) // slot 3/Z is the single-shot Rocket instead, not the missile volley
			.strength(4.0f) // "changes 18": melee bonus +4
			.noFlightLean()
			.noManualFlight() // "changes 13": Mark 1 can't double-tap-jump to fly -- only its X flight ability
			.scale(1.25f) // "25% bigger than the normal player model"
			.toggleableHighlight()
			// "changes 17": the crude prototype -- no Night Vision helmet, only 80% fall-damage reduction
			// (not full immunity), can't breathe underwater, and 50% slower swimming (bulky and heavy).
			.noHelmetNightVision()
			.fallDamageFraction(0.20f)
			.waterMoveMultiplier(0.5)
			// Slot order is R(1) G(2) X(3) Z(4) V(5) C(6) -- see IronManAbilities' class javadoc table.
			.abilities(IronManAbilities.STRONG_PUNCH, IronManAbilities.FLAMETHROWER, IronManAbilities.TIMED_FLIGHT,
					IronManAbilities.ROCKET, IronManAbilities.MOB_HIGHLIGHT_TOGGLE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.build());

	public static final IronManSuit MARK_2 = register(IronManSuit.Builder.of("mark_2")
			.tech(0, 2)
			.energy(4_500f, 2.0f, 0.5f)
			.maxIntegrity(420f) // "changes 18": Mark 2 condition pool
			.energyRegen(1.0f) // "changes 18"; nudged up from 0.75 -- slight Mark 2 recharge buff
			.flightDrain(0.9f) // "changes 18"
			.targetScanRange(30.0) // "changes 14"
			.flight(1.0f, 0.08f) // "normal flight like other armours"
			.repulsor(9.0f, 120f)
			.repulsorWindup(20) // 1 s spin-up before an ordinary tap actually fires
			.unibeam(14.0f, 1_500f)
			.unibeamDamageMultiplier(0.7f) // "slightly weaker" than Mark III's
			.missiles(0, 0f, 0f) // slot 2/G is the single-shot Rocket instead
			.strength(5.0f) // "changes 18": melee bonus +5
			.altitudeCeiling(150.0)
			.ceilingFreeze() // "changes 17": hitting the ceiling gives Freeze for 4 s + a hard systems lockout
			.airTank(120) // "changes 17": 2 minutes of underwater breathing
			.toggleableHighlight()
			// Slot order is R(1) G(2) X(3) Z(4) V(5) C(6) -- see IronManAbilities' class javadoc table.
			.abilities(IronManAbilities.REPULSOR_BLAST, IronManAbilities.ROCKET, IronManAbilities.FLARE,
					IronManAbilities.UNIBEAM, IronManAbilities.MOB_HIGHLIGHT_TOGGLE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.build());

	public static final IronManSuit MARK_III = register(IronManSuit.Builder.of("mark_iii")
			.tech(1, 3)
			.energy(7_500f, 2.0f, 3.0f) // "changes 18": capacity 7500
			.maxIntegrity(600f) // "changes 18": Mark III condition pool
			.energyRegen(1.5f) // "changes 18"
			.armorRegen(0.03f) // "changes 18": slow worn self-repair
			.flightDrain(1.0f) // "changes 18"
			.targetScanRange(70.0) // "changes 14": Mark III target scan reaches 70 blocks
			.airTank(180) // "changes 17": 3 minutes of underwater breathing
			.flight(1.0f, 0.08f)
			.repulsor(10.0f, 120f)
			.unibeam(18.0f, 1_500f)
			.missiles(4, 8.0f, 250f)
			.damageReduction(0.72f)
			.strength(6.0f) // "changes 18": melee bonus +6
			.abilities(IronManAbilities.REPULSOR_BLAST, IronManAbilities.REPULSOR_BARRIER, IronManAbilities.MICRO_MISSILES,
					IronManAbilities.UNIBEAM, IronManAbilities.MOB_HIGHLIGHT_TOGGLE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_III_BLUEPRINT)
			.build());

	// "changes 15": Mark V redefined as the movie Mark 5 -- silver-and-red suitcase armour with the
	// same ability loadout as the Mark 2, a smaller condition pool, and the folding-suitcase suit-up.
	public static final IronManSuit MARK_V = register(IronManSuit.Builder.of("mark_v")
			.tech(2, 5)
			.energy(8_000f, 1.6f, 0.5f) // "changes 18": capacity 8000
			.maxIntegrity(550f) // "changes 18"
			.energyRegen(1.6f) // "changes 18"
			.armorRegen(0.03f) // "changes 18"
			.flightDrain(0.90f) // "changes 18"
			.targetScanRange(30.0)
			.flight(1.0f, 0.08f)
			.repulsor(9.0f, 120f)
			.repulsorWindup(20) // 1 s spin-up before an ordinary tap fires -- same as the Mark 2
			.unibeam(14.0f, 1_500f)
			.unibeamDamageMultiplier(0.7f)
			.missiles(0, 0f, 0f) // slot 2/G is the single-shot Rocket, slot 3/X is the Flare
			.strength(5.0f) // "changes 18": melee bonus +5
			.toggleableHighlight()
			// "changes 19": slot 3 (X) is the gauntlet Blade toggle, not the Flare.
			.abilities(IronManAbilities.REPULSOR_BLAST, IronManAbilities.ROCKET, IronManAbilities.BLADE,
					IronManAbilities.UNIBEAM, IronManAbilities.MOB_HIGHLIGHT_TOGGLE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.SUITCASE_MOVIE)
			.summon(SummonType.SUITCASE_ITEM)
			.blueprint(IronManItems.MARK_V_BLUEPRINT)
			.build());

	// "changes 15": Mark 4 -- the strongest craftable "movie early-marks" suit. Mark III's ability
	// loadout, plus a one-shot wrist laser reached by sneaking + the V slot.
	public static final IronManSuit MARK_4 = register(IronManSuit.Builder.of("mark_4")
			.tech(0, 4)
			.energy(8_500f, 2.0f, 3.0f) // "changes 18": capacity 8500
			.maxIntegrity(700f) // "changes 18"
			.energyRegen(1.8f) // "changes 18"
			.armorRegen(0.04f) // "changes 18"
			.flightDrain(1.05f) // "changes 18"
			.targetScanRange(70.0)
			.airTank(180) // "changes 17": 3 minutes of underwater breathing
			.flight(1.05f, 0.09f)
			.repulsor(10.0f, 120f)
			.unibeam(18.0f, 1_500f)
			.missiles(4, 8.0f, 250f)
			.strength(6.0f) // "changes 18": melee bonus +6
			.wristLaser()
			.abilities(IronManAbilities.REPULSOR_BLAST, IronManAbilities.REPULSOR_BARRIER, IronManAbilities.MICRO_MISSILES,
					IronManAbilities.UNIBEAM, IronManAbilities.MOB_HIGHLIGHT_TOGGLE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_4_BLUEPRINT)
			.build());

	// "changes 16": Mark 6 -- Mark 4's loadout minus the wrist laser, a 360-degree Repulsor Shield,
	// 30 m/s flight, and cheap to run.
	public static final IronManSuit MARK_6 = register(IronManSuit.Builder.of("mark_6")
			.tech(0, 6)
			.energy(10_500f, 2.0f, 3.0f) // "changes 18": capacity 10500
			.maxIntegrity(800f) // "changes 18"
			.energyRegen(2.6f) // "changes 18"
			.armorRegen(0.05f) // "changes 18"
			.flightDrain(1.1f) // "changes 18"
			.targetScanRange(70.0)
			.airTank(300) // "changes 17": 5 minutes of underwater breathing
			.flight(1.7f, 0.14f)
			.maxFlightSpeed(30.0)
			.repulsor(10.0f, 120f)
			.unibeam(18.0f, 1_500f)
			.missiles(4, 8.0f, 250f)
			.strength(7.0f) // "changes 18": melee bonus +7
			.fullBodyShield()
			.energyCost(0.6f)
			.coloredGlow() // "changes 17": V-toggle glow colours entities by type
			.abilities(IronManAbilities.REPULSOR_BLAST, IronManAbilities.REPULSOR_BARRIER, IronManAbilities.MICRO_MISSILES,
					IronManAbilities.UNIBEAM, IronManAbilities.MOB_HIGHLIGHT_TOGGLE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_6_BLUEPRINT)
			.build());

	// "changes 16": Mark VII redefined as the movie Mark 7 -- keeps its id / blueprint / Fabricator
	// tech-3 slot, but the suit is now: bigger pools, a passive 50-block entity highlight, a weapon
	// wheel on V that re-binds slot 3 (X), a full-body Repulsor Shield, 30 m/s flight, cheap to run.
	public static final IronManSuit MARK_VII = register(IronManSuit.Builder.of("mark_vii")
			.tech(3, 7)
			.energy(12_000f, 2.6f, 3.4f) // "changes 18": capacity 12000
			.maxIntegrity(950f) // "changes 18"
			.energyRegen(3.0f) // "changes 18"
			.armorRegen(0.06f) // "changes 18"
			.flightDrain(1.15f) // "changes 18"
			.flight(1.7f, 0.14f)
			.maxFlightSpeed(30.0)
			.repulsor(12.0f, 110f)
			.unibeam(24.0f, 1_400f)
			.missiles(6, 10.0f, 240f)
			.strength(7.0f) // "changes 18": melee bonus +7
			.fullBodyShield()
			.energyCost(0.6f)
			// "changes 17": entity highlight is now a weapon-wheel toggle (coloured by entity type), not
			// an always-on passive. 7 minutes of underwater breathing, a 50%-bigger flamethrower heat bar.
			.toggleableHighlight()
			.coloredGlow()
			.targetScanRange(50.0)
			.airTank(420)
			.flamethrowerHeat(1.5f)
			.weaponWheel()
			.abilities(IronManAbilities.REPULSOR_BLAST, IronManAbilities.REPULSOR_BARRIER, IronManAbilities.WEAPON_WHEEL_SLOT,
					IronManAbilities.UNIBEAM, IronManAbilities.WEAPON_WHEEL, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.REMOTE_AUTOMATED)
			.summon(SummonType.TRACKING_POD)
			.blueprint(IronManItems.MARK_VII_BLUEPRINT)
			.build());

	// "changes 17": Mark XLII (mark_42) and Mark L (mark_50) are removed from the game for now -- their
	// suit definitions, armour items, blueprints, recipes and creative-tab entries are all gone. The
	// technology tree tops out at Mark VII (tech 3).

	private IronManSuits() {
	}

	public static void initialize() {
		HeroCraftMod.LOGGER.info("[HeroCraft] registered {} Iron Man suits", BY_ID.size());
	}

	public static IronManSuit byId(String id) {
		return BY_ID.get(id);
	}

	public static IronManSuit byMark(int markNumber) {
		return BY_MARK.get(markNumber);
	}

	public static java.util.Collection<IronManSuit> all() {
		return BY_ID.values();
	}

	private static IronManSuit register(IronManSuit suit) {
		BY_ID.put(suit.id(), suit);
		BY_MARK.put(suit.markNumber(), suit);
		return suit;
	}
}
