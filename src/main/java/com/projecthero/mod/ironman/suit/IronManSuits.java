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

	// =====================================================================================================================
	// v0.16.1 COMIC-ACCURATE REWORK (explicit user design). Nine marks in one linear progression:
	//   Mark 1 (grey iron) -> Mark 1 V2 (gold) -> Mark 2 (classic red and gold) -> Mark 3 (upgraded classic)
	//   -> Mark 4 HYDRO -> Mark 5 STEALTH -> Mark 6 SILVER CENTURION -> Mark 7 SPACE -> Mark 8 HULKBUSTER.
	// No suit energy any more: every move runs on its own cooldown, flight is unlimited (the Mark 1 hops and glides).
	// Integrity is the resource: the suit absorbs absorb() of every hit while it has integrity; at 0 it is disabled.
	// Slots R/G/X/Z/V are the comic kit (IronManComicKit: tap / Shift per key); C store (Mark 8: Sentry Mode).
	// The models are placeholders: each suit still points at its existing geo / texture (Mark 1 V2 = mark_1's model with
	// a gold copy of its texture). Armour points / toughness live in IronManArmorMaterials.
	// =====================================================================================================================

	// v0.16.7 (user) ERA I -- THE GANTRY AGE (docs/IRONMAN_ERA1_PLAN.md): the first five suits below (Mk I Cave-Forged,
	// Mk II Golden Avenger, Mk III Classic, Mk IV Hydro, Mk V Stealth) have the plan's integrity / absorb / melee, a real
	// reactor again (energy(cap) + energyRegen(per s), IronManEnergy) and suit up / come off only in a Stark Gantry. The
	// Mk III, Mk IV and Mk V fly (v0.16.7 (user): the Hydro and the Stealth got real flight too, same 2/s, sprint 4/s drain);
	// the Mk I hops and the Mk II's X hover is a move. The later suits keep their mechanics.
	public static final IronManSuit MARK_1 = register(comicKit(IronManSuit.Builder.of("mark_1"), com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_C)
			.tech(0, 1)
			.maxIntegrity(700f) // v0.16.7 (user): Era I table
			.absorb(0.30f)
			.energy(600f, 0f)
			.energyRegen(1.0f)
			.noAutoFeed()
			.flamethrowerTuning(100f, 5f, 5f, 60, 0f, 8f) // G: 8 dmg/s + burning, overheats at 100
			.flightCruise(8.0) // the Shift+X glide's forward speed
			.noSprintFlight()
			.flight(0.75f, 0.055f)
			.repulsorTap(10.0f, 0f, 20)
			.unibeam(0f)
			.missiles(0, 0f, 0f)
			.strength(8.0f) // v0.16.7 (user): +8
			.walkSpeed(-0.10)
			.noFlightLean()
			.noManualFlight() // no sustained flight: rocket-boot hops + glide only
			.scale(1.0f) // v0.16.7 (user): normal size (was 1.25) -- the Era I armour is thin
			.noHelmetNightVision()
			.waterMoveMultiplier(0.5) // heavy: sinks and wades slowly
			.targetScanRange(25.0)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.build());

	/** v0.16.1: the Mark 1 rebuilt in gold -- made by upgrading a full Mark 1 at the Fabricator. */
	public static final IronManSuit MARK_1_V2 = register(comicKit(IronManSuit.Builder.of("mark_1_v2"), com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_C)
			.tech(0, 1)
			.maxIntegrity(850f) // v0.16.7 (user): Era I Mk II Golden Avenger
			.absorb(0.32f)
			.energy(800f, 0f)
			.energyRegen(1.5f)
			.noAutoFeed()
			.flight(1.0f, 0.07f)
			.flightSpeeds(12.0, 18.0)
			.maxFlightSpeed(18.0)
			.noManualFlight() // v0.16.7 (user): no sustained flight -- its X move is the 5 s boot-jet hover
			.repulsorTap(10.0f, 0f, 20)
			.unibeam(0f)
			.missiles(0, 0f, 0f)
			.strength(7.0f)
			.noHelmetNightVision()
			.altitudeCeiling(320.0)
			.ceilingFreeze() // the gold suit ices up in thin air
			.targetScanRange(30.0)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_1_V2_BLUEPRINT)
			.build());

	/** v0.16.1: the classic red and gold -- the Briefcase armour (the old Mark 5 suitcase system is its delivery now). */
	public static final IronManSuit MARK_2 = register(comicKit(IronManSuit.Builder.of("mark_2"), IronManAbilities.SUIT_TOGGLE)
			.tech(0, 2)
			.maxIntegrity(1000f)
			.absorb(0.30f)
			.noAutoFeed()
			.targeting()
			.waterBreathing()
			.flight(1.0f, 0.08f)
			.flightSpeeds(18.0, 28.0)
			.maxFlightSpeed(28.0)
			// R: tap = quick repulsor, hold 1 s = charged (the shared repulsor slot code), Shift = dash
			.repulsorTap(11.0f, 0f, 16)
			.repulsorCharged(20.0f, 0f, 50, 20)
			.dash(16.0f, 0f, 160)
			.unibeam(20.0f)
			.missiles(0, 0f, 0f)
			.strength(7.0f)
			.altitudeCeiling(320.0)
			.ceilingFreeze()
			.targetScanRange(40.0)
			.suitUp(SuitUpType.SUITCASE_MOVIE)
			.summon(SummonType.SUITCASE_ITEM)
			.blueprint(IronManItems.MARK_2_BLUEPRINT)
			.build());

	public static final IronManSuit MARK_III = register(comicKit(IronManSuit.Builder.of("mark_iii"), com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_C)
			.tech(1, 3)
			.maxIntegrity(1400f)
			.absorb(0.38f) // v0.16.7 (user): Era I Mk III Classic -- the one Era I suit with full flight (6 energy/s, sprint 12/s)
			.energy(1200f, 0f)
			.energyRegen(2.0f)
			.waterBreathing()
			.targeting()
			.flight(1.0f, 0.08f)
			.flightSpeeds(20.0, 32.0)
			.maxFlightSpeed(32.0)
			.repulsorTap(14.0f, 0f, 10)
			.repulsorCharged(22.0f, 0f, 50, 20)
			.dash(19.0f, 0f, 160)
			.unibeam(20.0f)
			.missiles(4, 20.0f, 0f)
			.strength(8.0f)
			.altitudeCeiling(320.0)
			.targetScanRange(70.0)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_III_BLUEPRINT)
			.build());

	/** v0.16.1: the HYDRO armour -- slow in the air, three times as fast underwater, and its kit hits harder there. */
	public static final IronManSuit MARK_4 = register(comicKit(IronManSuit.Builder.of("mark_4"), com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_C)
			.tech(0, 4)
			.maxIntegrity(1600f) // v0.16.7 (user): Era I Mk IV Hydro -- flies like the Mk III; Slowness I only walking on land (IronManPassives)
			.absorb(0.40f)
			.energy(1200f, 0f)
			.energyRegen(2.0f)
			.waterBreathing()
			.targeting()
			.hydro()
			.flight(1.0f, 0.08f)
			.flightSpeeds(14.0, 22.0)
			.maxFlightSpeed(22.0)
			// v0.16.7 (user): real flight now (was .noManualFlight()) -- water-jet propulsors in water (IronManFlightFxClient)
			.repulsorTap(14.0f, 0f, 10)
			.unibeam(20.0f)
			.missiles(0, 0f, 0f)
			.strength(8.0f)
			.altitudeCeiling(320.0)
			.targetScanRange(70.0)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_4_BLUEPRINT)
			.build());

	/** v0.16.1: the STEALTH armour -- silent, sculk-proof, built for the unaware target. No briefcase any more. */
	public static final IronManSuit MARK_V = register(comicKit(IronManSuit.Builder.of("mark_v"), com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_C)
			.tech(2, 5)
			.maxIntegrity(500f) // v0.16.7 (user): Era I Mk V Stealth -- paper-thin; quiet air-jet flight, Shift+X Active Camo
			.absorb(0.15f)
			.energy(1000f, 0f)
			.energyRegen(2.5f)
			.waterBreathing()
			.targeting()
			.stealth()
			.flight(1.0f, 0.08f)
			.flightSpeeds(20.0, 34.0)
			.maxFlightSpeed(34.0)
			// v0.16.7 (user): real flight now (was .noManualFlight() + the Shift+X Silent Glide) -- near-silent clear air jets
			.repulsorTap(14.0f, 0f, 10)
			.unibeam(18.0f)
			.missiles(0, 0f, 0f)
			.strength(6.0f)
			.altitudeCeiling(320.0)
			.targetScanRange(60.0)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_V_BLUEPRINT)
			.build());

	/** v0.16.1: the SILVER CENTURION -- Resistance I and a slow out-of-combat self-repair. */
	public static final IronManSuit MARK_6 = register(comicKit(IronManSuit.Builder.of("mark_6"), IronManAbilities.SUIT_TOGGLE)
			.tech(0, 6)
			.maxIntegrity(2200f)
			.absorb(0.45f)
			.selfRepair(8.0f)
			.waterBreathing()
			.resistance(1)
			.targeting()
			.coloredGlow()
			.flight(1.7f, 0.14f)
			.flightSpeeds(22.0, 38.0)
			.maxFlightSpeed(38.0)
			.repulsorTap(17.0f, 0f, 10)
			.unibeam(24.0f)
			.missiles(0, 0f, 0f)
			.strength(9.0f)
			.fullBodyShield()
			.altitudeCeiling(320.0)
			.targetScanRange(70.0)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_6_BLUEPRINT)
			.build());

	/** v0.16.1: the SPACE armour -- the fastest flier, no altitude ceiling, fire / lava / freeze proof; keeps the orbital
	 *  pod and the Colantotte Bracelets. Self-repairs faster than the Centurion. */
	public static final IronManSuit MARK_VII = register(comicKit(IronManSuit.Builder.of("mark_vii"), IronManAbilities.SUIT_TOGGLE)
			.tech(3, 7)
			.maxIntegrity(2600f)
			.absorb(0.50f)
			.selfRepair(15.0f)
			.waterBreathing()
			.targeting()
			.coloredGlow()
			.hazardImmune()
			.flight(1.7f, 0.14f)
			.flightSpeeds(24.0, 44.0)
			.maxFlightSpeed(44.0)
			.repulsorTap(20.0f, 0f, 10)
			.unibeam(28.0f)
			.missiles(0, 0f, 0f)
			.strength(9.0f)
			.fullBodyShield()
			.targetScanRange(80.0)
			.suitUp(SuitUpType.REMOTE_AUTOMATED)
			.summon(SummonType.TRACKING_POD)
			.blueprint(IronManItems.MARK_VII_BLUEPRINT)
			.build());

	/**
	 * v0.16.7 (user) Era II stage 1: the Mk IX INFERNO -- the Nether suit. Charcoal plate, bronze trim, heat-sink fins;
	 * fire and lava immunity while the full suit is worn and an orange jet. Flies like the Mk VIII Space; integrity /
	 * absorb sit between the Mk VIII and the Hulkbuster. Keeps the no-energy shim. v0.16.7 (user) Era II stage 3: its kit
	 * ({@code IronManComicKit} "mark_9", {@code IronManMark9}): fire blast / stream, magma fist / eruption, afterburner /
	 * lava geyser, magma bomb / firestorm, heat dump / thermal siphon, the heat sink and lava swimming.
	 */
	public static final IronManSuit MARK_9 = register(comicKit(IronManSuit.Builder.of("mark_9"), IronManAbilities.SUIT_TOGGLE)
			.tech(4, 9)
			.maxIntegrity(3200f)
			.absorb(0.55f)
			.selfRepair(12.0f)
			.waterBreathing()
			.targeting()
			.coloredGlow()
			.fireImmune()
			.flight(1.7f, 0.14f)
			.flightSpeeds(24.0, 44.0)
			.maxFlightSpeed(44.0)
			.repulsorTap(21.0f, 0f, 10)
			.unibeam(30.0f)
			.missiles(0, 0f, 0f)
			.strength(10.0f)
			.fullBodyShield()
			.altitudeCeiling(320.0)
			.targetScanRange(80.0)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_9_BLUEPRINT)
			.build());

	/** v0.16.1: the HULKBUSTER -- about 3 blocks tall (x1.45), slow jet-hover, knockback immune, never self-repairs (its
	 *  Replacement Pod is the field fix). Keeps Sentry Mode on C and the Stark Glasses call; arrives as an orbital drop. */
	public static final IronManSuit MARK_8 = register(comicKit(IronManSuit.Builder.of("mark_8"),
			com.projecthero.mod.ironman.ability.IronManMark8.SENTRY) // C = Sentry Mode
			.tech(3, 8)
			.maxIntegrity(4500f)
			.absorb(1.0f) // v0.16.7 (user): the suit takes every hit, not its pilot (HulkbusterShield) -- was 60%
			.waterBreathing()
			.targeting()
			.coloredGlow()
			.hulkbuster()
			.flight(1.0f, 0.06f)
			.flightSpeeds(10.0, 10.0)
			.noSprintFlight()
			.noFlightLean()
			.maxFlightSpeed(10.0)
			.repulsorTap(24.0f, 0f, 10)
			.unibeam(30.0f)
			.missiles(0, 0f, 0f)
			.strength(16.0f)
			.scale(1.45f)
			.altitudeCeiling(320.0)
			.targetScanRange(80.0)
			.suitUp(SuitUpType.MECHANICAL_REMOTE)
			.summon(SummonType.FLYING_SET)
			.blueprint(IronManItems.MARK_8_BLUEPRINT)
			.build());

	/** v0.16.1: R / G / X / Z / V = the comic kit's five keys, C = {@code slotC}. */
	private static IronManSuit.Builder comicKit(IronManSuit.Builder b, String slotC) {
		return b.abilities(com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_R, com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_G,
				com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_X, com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_Z,
				com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_V, slotC);
	}

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
		BY_MARK.putIfAbsent(suit.markNumber(), suit); // v0.16.1: the Mark 1 V2 shares mark number 1 with the Mark 1
		return suit;
	}
}
