package com.projecthero.mod.ironman.suit;

import java.util.LinkedHashMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.item.IronManItems;

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
			.energy(500f, 3.0f) // v0.14.27: 500, explicit user request
			.maxIntegrity(750f) // v0.14.27: 750, explicit user request
			.energyRegen(2f) // v0.14.27: 2 energy/sec (halved while the X flight burst is up)
			.arrowFireImmune() // v0.14.27: arrows + fire do nothing, not even to integrity
			.noAutoFeed()
			// v0.14.27: G flamethrower -- 8 dmg/s + burning, 6 energy/s, heat +5/s to a 100 ceiling, seeps 5/s after 3 s idle
			.flamethrowerTuning(100f, 5f, 5f, 60, 6f, 8f)
			.flightCruise(8.0) // v0.14.27: the X flight burst flies at 8 blocks/s
			.noSprintFlight()
			.hoverFloor(0.5) // v0.14.27: can't sink lower than 0.5 blocks above the ground during the burst
			.mobHighlight(10f, 20 * 20) // v0.14.27: V costs 10 energy
			.platformRegen(10f, 6f) // v0.11.12: flat 10 energy/sec + 6 integrity/sec on a Suit Platform,
			// overriding the generic 0.1%-of-pool formula every other mark still uses -- explicit user
			// request.
			.flightDrain(0.55f) // "changes 18"
			.targetScanRange(25.0) // v0.14.27: Mob Highlight reaches 25 blocks
			.flight(0.75f, 0.055f) // "changes 18": flies 25% slower than the other marks
			.repulsor(0f) // no repulsor on this loadout -- see abilities() below
			.unibeam(0f)  // no unibeam on this loadout
			.missiles(0, 0f, 0f) // slot 3/Z is the single-shot Rocket instead, not the missile volley
			.strength(6.0f) // v0.14.27: melee bonus +6
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
			.energy(1_250f, 0.5f) // v0.14.26: down from 4500, explicit user request
			.maxIntegrity(1000f) // v0.14.27: 1000, explicit user request
			.energyRegen(3f) // v0.14.27: 3 energy/sec, explicit user request
			.platformRegen(24f, 15f) // v0.11.13: 24 energy/sec + 15 integrity/sec on a Suit Platform,
			// overriding the generic 0.1%-of-pool formula -- explicit user request.
			.resistance(1) // v0.14.27: Resistance I while the chestplate is worn + powered
			.arrowFireImmune()
			.noAutoFeed()
			.targeting() // v0.14.27: lock-on / auto-aim like the Mark III
			.flightDrain(0.9f) // "changes 18"
			.targetScanRange(25.0) // v0.14.27: Mob Highlight reaches 25 blocks
			.flight(1.0f, 0.08f) // "normal flight like other armours"
			// v0.14.27 R: tap = 1 s spin-up then 10 dmg (10 energy, 1 s cd); hold 1 s = 18 dmg (50 energy, 3 s cd);
			// Shift+R = the repulsor dash (15 dmg, 50 energy, 8 s cd)
			.repulsorTap(10.0f, 10f, 20)
			.repulsorWindup(20)
			.repulsorCharged(18.0f, 50f, 60, 20)
			.dash(15.0f, 50f, 160)
			.sonicClap(15.0f, 50f, 160) // v0.14.27 G
			.unibeam(20.0f)
			.unibeamChannel(120, 20.0f, 300f, 400) // v0.14.27 Z: 6 s, 20 per damage tick, 300 energy, 20 s cd
			.missiles(0, 0f, 0f)
			.strength(6.0f) // v0.14.27: melee bonus +6
			.altitudeCeiling(150.0)
			.ceilingFreeze() // "changes 17": hitting the ceiling gives Freeze for 4 s + a hard systems lockout
			// v0.14.27: no air tank any more -- the Mark 2 can't breathe underwater
			.toggleableHighlight()
			.mobHighlight(10f, 20 * 20) // v0.14.27: same as the Mark 1
			// Slot order is R(1) G(2) X(3) Z(4) V(5) C(6) -- see IronManAbilities' class javadoc table.
			.abilities(IronManAbilities.REPULSOR_BLAST, IronManAbilities.SONIC_CLAP, IronManAbilities.FLARE,
					IronManAbilities.UNIBEAM, IronManAbilities.MOB_HIGHLIGHT_TOGGLE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.build());

	public static final IronManSuit MARK_III = register(IronManSuit.Builder.of("mark_iii")
			.tech(1, 3)
			.energy(2_000f, 3.0f) // v0.14.27: 2000, explicit user request
			.maxIntegrity(1000f) // v0.14.27: 1000, explicit user request
			.energyRegen(5f) // v0.14.27: 5 energy/sec
			.resistance(1) // v0.14.27: Resistance I while the chestplate is worn + powered
			.arrowFireImmune()
			.waterBreathing() // v0.14.27: breathes underwater (no timed air tank)
			.targeting()
			.flightDrain(1.0f) // "changes 18"
			.targetScanRange(70.0) // "changes 14": Mark III target scan reaches 70 blocks
			.flight(1.0f, 0.08f)
			// v0.14.27 R: tap 15 dmg / 10 energy / 1 s cd (no windup); hold 1 s = 20 dmg / 50 energy / 3 s cd;
			// Shift+R = the repulsor dash (20 dmg, 50 energy, 8 s cd)
			.repulsorTap(15.0f, 10f, 20)
			.repulsorCharged(20.0f, 50f, 60, 20)
			.dash(20.0f, 50f, 160)
			.unibeam(18.0f)
			.missiles(4, 8.0f, 250f)
			.strength(7.0f) // v0.14.27: melee bonus +7
			// v0.14.27 (agent D): G = weapon-wheel weapon (Sneak: Sonic Clap), X = Flares (Sneak: JARVIS scan),
			// Z = held Unibeam, V = weapon wheel (Sneak: Energy Shield) -- see IronManMark3.
			.abilities(IronManAbilities.REPULSOR_BLAST, com.projecthero.mod.ironman.ability.IronManMark3.ARSENAL,
					com.projecthero.mod.ironman.ability.IronManMark3.FLARES, com.projecthero.mod.ironman.ability.IronManMark3.UNIBEAM,
					com.projecthero.mod.ironman.ability.IronManMark3.WHEEL, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_III_BLUEPRINT)
			.build());

	// "changes 15": Mark V redefined as the movie Mark 5 -- silver-and-red suitcase armour.
	// v0.14.29 (explicit user spec): Mark 2 kit with an instant repulsor tap and the gauntlet Blades on V (in place of
	// the Mob Highlight); 2500 energy (+5/s), 800 integrity (+2/s worn), diamond-level plates, +6 melee, water
	// breathing, auto-feed, lock-on targeting. C folds it into the Mark V suitcase (4 s); only right-clicking the case
	// suits back up (6 s) -- C auto-equip / the Sneak+C picker never offer it. See IronManMk5Suitcase.
	public static final IronManSuit MARK_V = register(IronManSuit.Builder.of("mark_v")
			.tech(2, 5)
			.energy(2_500f, 0.5f) // v0.14.29: 2500
			.maxIntegrity(800f) // v0.14.29: 800
			.energyRegen(5f) // v0.14.29: 5 energy/sec
			.arrowFireImmune() // v0.14.29: arrows + fire do nothing, not even to integrity
			.waterBreathing() // v0.14.29: breathes underwater
			.targeting() // v0.14.29: lock-on / auto-aim like the Mark 2 / III
			.flightDrain(0.90f) // "changes 18"
			.targetScanRange(30.0)
			.flight(1.0f, 0.08f)
			// v0.14.29 R: the Mark 2's numbers but NO 1 s spin-up -- a tap fires the moment it is released;
			// hold 1 s = charged; Shift+R = the repulsor dash
			.repulsorTap(10.0f, 10f, 20)
			.repulsorCharged(18.0f, 50f, 60, 20)
			.dash(15.0f, 50f, 160)
			.sonicClap(15.0f, 50f, 160) // G, as the Mark 2
			.unibeam(20.0f)
			.unibeamChannel(120, 20.0f, 300f, 400) // Z, as the Mark 2
			.missiles(0, 0f, 0f)
			.strength(6.0f) // v0.14.29: melee bonus +6
			// Slot order is R(1) G(2) X(3) Z(4) V(5) C(6). v0.14.29: the Mark 2 kit with V = the gauntlet Blades.
			.abilities(IronManAbilities.REPULSOR_BLAST, IronManAbilities.SONIC_CLAP, IronManAbilities.FLARE,
					IronManAbilities.UNIBEAM, IronManAbilities.BLADE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.SUITCASE_MOVIE)
			.summon(SummonType.SUITCASE_ITEM)
			.blueprint(IronManItems.MARK_V_BLUEPRINT)
			.build());

	// "changes 15": Mark 4 -- the strongest craftable "movie early-marks" suit.
	// v0.14.29 (agent A), explicit user spec: the Mark III's whole kit (IronManMark3) with +2 damage on every
	// move and every cooldown 2 s shorter (IronManMark3.MARK_4_TUNING), same energy costs; the wrist laser is gone.
	public static final IronManSuit MARK_4 = register(IronManSuit.Builder.of("mark_4")
			.tech(0, 4)
			.energy(3_000f, 3.0f) // v0.14.29: 3000
			.maxIntegrity(1750f) // v0.14.29: 1750
			.energyRegen(5f) // v0.14.29: 5 energy/sec
			.arrowFireImmune() // v0.14.29: arrows + fire do nothing, not even to integrity
			.waterBreathing() // v0.14.29: breathes underwater (no timed air tank)
			.targeting() // v0.14.29: lock-on / auto-aim + HUD lock like the Mark III
			// auto-feed stays on (no .noAutoFeed())
			.flightDrain(1.05f) // "changes 18"
			.targetScanRange(70.0)
			.flight(1.05f, 0.09f)
			// v0.14.29 R: the Mark III's numbers +2 dmg / -2 s -- tap 17 dmg / 10 energy / no cd; hold 1 s = 22 dmg /
			// 50 energy / 1 s cd; Shift+R dash 22 dmg / 50 energy / 6 s cd
			.repulsorTap(17.0f, 10f, 0)
			.repulsorCharged(22.0f, 50f, 20, 20)
			.dash(22.0f, 50f, 120)
			.unibeam(20.0f)
			.missiles(4, 8.0f, 250f)
			.strength(7.0f) // v0.14.29: melee bonus +7
			// G = weapon-wheel weapon (Sneak: Sonic Clap), X = Flares (Sneak: JARVIS scan), Z = held Unibeam,
			// V = weapon wheel (Sneak: Energy Shield) -- the IronManMark3 kit, tuned per suit.
			.abilities(IronManAbilities.REPULSOR_BLAST, com.projecthero.mod.ironman.ability.IronManMark3.ARSENAL,
					com.projecthero.mod.ironman.ability.IronManMark3.FLARES, com.projecthero.mod.ironman.ability.IronManMark3.UNIBEAM,
					com.projecthero.mod.ironman.ability.IronManMark3.WHEEL, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_4_BLUEPRINT)
			.build());

	// v0.14.29 (agent C): Mark 6 -- its own kit, built round the film's new-element arc reactor (IronManMark6): the X
	// Arc Reactor Surge (10 s of +50% damage and speed), a held Unibeam, a homing shoulder barrage, and the 360-degree
	// Repulsor Shield it has always had. Numbers sit between the Mark III / 4 and the Mark 7.
	public static final IronManSuit MARK_6 = register(IronManSuit.Builder.of("mark_6")
			.tech(0, 6)
			.energy(10_500f, 3.0f) // "changes 18": capacity 10500
			.maxIntegrity(800f) // "changes 18"
			.energyRegen(2.6f) // "changes 18"
			.flightDrain(1.1f) // "changes 18"
			.targetScanRange(70.0)
			.waterBreathing() // v0.14.29: breathes underwater indefinitely (was a 5-minute air tank)
			.resistance(1) // v0.14.29: Resistance I while the chestplate is worn + powered
			.targeting() // v0.14.29: lock-on / auto-aim
			.flight(1.7f, 0.14f)
			.maxFlightSpeed(30.0)
			// v0.14.29 R: tap 17 dmg / 10 energy / 1 s cd; hold 1 s = 26 dmg / 50 energy / 3 s cd; Shift+R dash 22 / 50 / 8 s
			.repulsorTap(17.0f, 10f, 20)
			.repulsorCharged(26.0f, 50f, 60, 20)
			.dash(22.0f, 50f, 160)
			.unibeam(com.projecthero.mod.ironman.ability.IronManMark6.UNIBEAM_DAMAGE)
			.missiles(0, 0f, 0f) // the V barrage is the Mark 6's own volley (IronManMark6), not the shared one
			.strength(7.0f) // "changes 18": melee bonus +7
			.fullBodyShield()
			.energyCost(0.6f)
			.coloredGlow() // "changes 17": the highlight colours entities by type (Sneak+V since v0.14.29)
			// G = 360 shield (Sneak: Sonic Clap), X = Arc Reactor Surge (Sneak: Flares), Z = held Unibeam,
			// V = Shoulder Barrage (Sneak: highlight) -- see IronManMark6.
			.abilities(IronManAbilities.REPULSOR_BLAST, com.projecthero.mod.ironman.ability.IronManMark6.SHIELD,
					com.projecthero.mod.ironman.ability.IronManMark6.SURGE, com.projecthero.mod.ironman.ability.IronManMark6.UNIBEAM,
					com.projecthero.mod.ironman.ability.IronManMark6.BARRAGE, IronManAbilities.SUIT_TOGGLE)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_6_BLUEPRINT)
			.build());

	// "changes 16": Mark VII redefined as the movie Mark 7 -- keeps its id / blueprint / Fabricator tech-3 slot.
	// v0.14.29 (agent C): modern kit (IronManMark7) -- the 7-wedge weapon wheel on V still re-binds X, every wheel weapon
	// retuned to hit at least as hard as the Mark III's, a held Unibeam on Z, the 360 shield on G (Sneak: Flares), and
	// the orbital drop: called off a platform, or from the pack while airborne, it streaks in by delivery pod.
	public static final IronManSuit MARK_VII = register(IronManSuit.Builder.of("mark_vii")
			.tech(3, 7)
			.energy(12_000f, 3.4f) // "changes 18": capacity 12000
			.maxIntegrity(950f) // "changes 18"
			.energyRegen(3.0f) // "changes 18"
			.flightDrain(1.15f) // "changes 18"
			.flight(1.7f, 0.14f)
			.maxFlightSpeed(30.0)
			.resistance(1) // v0.14.29: Resistance I while the chestplate is worn + powered
			.targeting() // v0.14.29: lock-on / auto-aim
			.waterBreathing() // v0.14.29: breathes underwater indefinitely (was a 7-minute air tank)
			// v0.14.29 R: tap 20 dmg / 10 energy / 1 s cd; hold 1 s = 30 dmg / 50 energy / 3 s cd; Shift+R dash 25 / 50 / 8 s
			.repulsorTap(20.0f, 10f, 20)
			.repulsorCharged(30.0f, 50f, 60, 20)
			.dash(25.0f, 50f, 160)
			.unibeam(com.projecthero.mod.ironman.ability.IronManMark7.UNIBEAM_DAMAGE)
			.missiles(6, 26.0f, 160f) // v0.14.29: wheel Micro-Missiles 6 x 26, Homing Missiles 4 x 26, 160 energy each
			.strength(8.0f) // v0.14.29: melee bonus +8
			.fullBodyShield()
			.energyCost(0.6f)
			// "changes 17": entity highlight is a weapon-wheel toggle (coloured by entity type).
			.toggleableHighlight()
			.coloredGlow()
			.targetScanRange(80.0) // v0.14.29: 80 blocks (was 50)
			// v0.14.29: wheel flamethrower -- 12 dmg/s + burning, 5 energy/s, heat +38/s to a 750 ceiling, vents 20/s
			.flamethrowerTuning(750f, 38f, 20f, 0, 5f, 12f)
			.weaponWheel()
			.abilities(IronManAbilities.REPULSOR_BLAST, com.projecthero.mod.ironman.ability.IronManMark7.SHIELD,
					IronManAbilities.WEAPON_WHEEL_SLOT, com.projecthero.mod.ironman.ability.IronManMark7.UNIBEAM,
					IronManAbilities.WEAPON_WHEEL, IronManAbilities.SUIT_TOGGLE)
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
		ProjectHeroMod.LOGGER.info("[ProjectHero] registered {} Iron Man suits", BY_ID.size());
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
