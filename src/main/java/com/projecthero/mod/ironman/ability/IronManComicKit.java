package com.projecthero.mod.ironman.ability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * v0.16.1 COMIC-ACCURATE IRON MAN REWORK (explicit user design): the per-Mark key layout. Every suit's R / G / X / Z / V
 * key has a <b>tap</b> move and a <b>Shift</b> move ({@code tap / Shift}), each with its own cooldown (stored under
 * {@code <suit>/<move id>} in the synced {@code TonyStarkState.abilityReadyAt}). v0.16.1 had no suit energy; v0.16.7
 * (user) brought the reactor back for Era I and then Era II -- every Era I / Era II move has an {@link #energyCost} or
 * {@link #energyPerSecond} ({@code IronManEraOne.canPay / pay / drainTick}). Pure data -- shared by the server
 * ({@link IronManComicRouter} / {@link IronManComicMoves}), the HUD, the spec sheet, the guide and the gametests.
 *
 * <p>Damage figures are the move's headline number (per hit / per bolt / per missile / per pulse) and are what the move
 * code actually deals; the Hydro armour's moves deal x1.5 underwater and the Hulkbuster's x1.5 against bosses and the
 * Hulk (both applied once, centrally, in {@code IronManDamage}).
 */
public final class IronManComicKit {
	public static final String SLOT_R = "comic_r";
	public static final String SLOT_G = "comic_g";
	public static final String SLOT_X = "comic_x";
	public static final String SLOT_Z = "comic_z";
	public static final String SLOT_V = "comic_v";
	/**
	 * v0.16.7 Era I: C is a kit key too (slot 6) -- its Shift edge is the emergency eject, not a move. v0.16.7 (user) Era
	 * II: also a kit key (a utility moved off the suit's V key); its Shift edge is the hold-to-fold into the portable form.
	 */
	public static final String SLOT_C = "comic_c";
	/** Kit slot ids in key order R, G, X, Z, V, C (ability slots 1-6; the Era I and Era II suits bind C). */
	public static final List<String> SLOTS = List.of(SLOT_R, SLOT_G, SLOT_X, SLOT_Z, SLOT_V, SLOT_C);

	/** TAP moves fire on the key press; HOLD moves also get the release (charge, hold-and-throw, held stream). */
	public enum Input { TAP, HOLD }

	/**
	 * One move. {@code cooldownTicks} starts when the move goes off ({@link IronManComicMoves} decides when that is for
	 * a HOLD move); {@code cooldownKey} is what the cooldown is stored under (normally the id -- the Mark 2 repulsor
	 * reuses the shared repulsor slot code and its own keys).
	 */
	public record Move(String id, Input input, int cooldownTicks, float damage, Item icon, String cooldownKey) {
		public String nameKey() {
			return "hud.projecthero.ironman.comic." + id;
		}

		public String descKey() {
			return "projecthero.guide.iron_man.comic." + id;
		}
	}

	public record Key(Move tap, Move shift) {
		public Move get(boolean shifted) {
			return shifted ? shift : tap;
		}
	}

	public record Kit(String suitId, List<Key> keys) {
		/** {@code slot} 1..6 (R, G, X, Z, V, C); C only on the Era I kits. */
		public Key key(int slot) {
			return slot < 1 || slot > keys.size() ? null : keys.get(slot - 1);
		}
	}

	private static final Map<String, Kit> KITS = new LinkedHashMap<>();
	private static final Map<String, Move> BY_ID = new LinkedHashMap<>();

	private static Move tap(String id, int cdTicks, float damage, Item icon) {
		return reg(new Move(id, Input.TAP, cdTicks, damage, icon, id));
	}

	private static Move hold(String id, int cdTicks, float damage, Item icon) {
		return reg(new Move(id, Input.HOLD, cdTicks, damage, icon, id));
	}

	private static Move reg(Move m) {
		if (BY_ID.put(m.id(), m) != null) {
			throw new IllegalStateException("duplicate comic move " + m.id());
		}
		return m;
	}

	private static Key key(Move tap, Move shift) {
		return new Key(tap, shift);
	}

	/**
	 * v0.16.7 Era I: a six-key kit (the C key's Shift is the eject, so its {@code shift} is null). v0.16.7 (user) Era II
	 * uses it too: there the C key's Shift is the hold-to-fold.
	 */
	private static void kit6(String suitId, Key r, Key g, Key x, Key z, Key v, Move c) {
		KITS.put(suitId, new Kit(suitId, List.of(r, g, x, z, v, new Key(c, null))));
	}

	/** v0.16.7 Era I energy: what a move costs when it goes off, and (held / toggled moves) per second while it runs. */
	private static final Map<String, Float> ENERGY = new LinkedHashMap<>();
	private static final Map<String, Float> ENERGY_PER_SECOND = new LinkedHashMap<>();

	private static Move en(Move m, float cost) {
		ENERGY.put(m.id(), cost);
		return m;
	}

	private static Move enPerSecond(Move m, float perSecond) {
		ENERGY_PER_SECOND.put(m.id(), perSecond);
		return m;
	}

	/** Energy a move costs when it goes off (0 for a suit without a reactor). */
	public static float energyCost(String moveId) {
		return ENERGY.getOrDefault(moveId, 0f);
	}

	/** Energy a held / toggled move drains per second while it runs (0 if none). */
	public static float energyPerSecond(String moveId) {
		return ENERGY_PER_SECOND.getOrDefault(moveId, 0f);
	}

	private static int s(double seconds) {
		return (int) Math.round(seconds * 20.0);
	}

	// ------------------------------------------------------------------ the nine kits

	static {
		// ================================================================ v0.16.7 ERA I -- THE GANTRY AGE (user design)
		// docs/IRONMAN_ERA1_PLAN.md. Every key has a tap + Shift move; C's Shift is the emergency eject. en = energy
		// (v0.16.7, user: lowered to about a third so a reactor lasts a long mission; the Mk I's numbers are the user's).

		// MK I CAVE-FORGED: grey scrap iron, slow, no flight -- rocket jumps, a wrist flamethrower, hits hard.
		kit6("mark_1",
				key(enPerSecond(hold("e1mk1_flamethrower", 0, 8f, Items.FIRE_CHARGE), 5f), en(tap("e1mk1_fire_wall", s(14), 3f, Items.CAMPFIRE), 20f)),
				key(en(tap("e1mk1_iron_fist", s(1.5), 18f, Items.PISTON), 10f), en(hold("e1mk1_haymaker", s(8), 36f, Items.ANVIL), 20f)),
				key(en(tap("e1mk1_rocket_jump", s(3), 0f, Items.FIREWORK_ROCKET), 5f), en(tap("e1mk1_rocket_leap", s(6), 0f, Items.FEATHER), 10f)),
				key(en(tap("e1mk1_smoke_screen", s(14), 0f, Items.CAMPFIRE), 15f), en(tap("e1mk1_scrap_burst", s(6), 7f, Items.IRON_NUGGET), 10f)),
				key(en(tap("e1mk1_magnetic_repel", s(8), 4f, Items.LODESTONE), 10f), en(tap("e1mk1_brace", s(16), 0f, Items.IRON_BLOCK), 25f)),
				en(tap("e1mk1_reactor_overload", s(20), 0f, Items.REDSTONE_BLOCK), 50f));

		// MK II GOLDEN AVENGER: the Mk I rebuilt in gold -- the first repulsors and a short boot-jet hover.
		kit6("mark_1_v2",
				key(en(tap("e1mk2_repulsor", s(1), 12f, Items.GLOWSTONE_DUST), 5f), en(hold("e1mk2_charged_repulsor", s(6), 26f, Items.GLOWSTONE), 20f)),
				key(en(tap("e1mk2_gold_uppercut", s(5), 14f, Items.GOLDEN_SWORD), 10f), en(tap("e1mk2_ground_pound", s(8), 16f, Items.ANVIL), 15f)),
				key(enPerSecond(tap("e1mk2_boot_hover", s(2), 0f, Items.GOLDEN_BOOTS), 3f), en(tap("e1mk2_jet_dash", s(7), 14f, Items.FEATHER), 15f)),
				key(en(tap("e1mk2_golden_glare", s(12), 0f, Items.GLOWSTONE), 15f), en(tap("e1mk2_jet_skates", s(14), 0f, Items.GOLDEN_BOOTS), 15f)),
				key(en(tap("e1mk2_magnetic_pull", s(6), 0f, Items.LODESTONE), 10f), en(tap("e1mk2_magnetic_shield", s(15), 0f, Items.SHIELD), 20f)),
				en(tap("e1mk2_reactor_overload", s(20), 0f, Items.REDSTONE_BLOCK), 50f));

		// MK III CLASSIC: the iconic red and gold (Armor Model 4) -- real flight, the chest unibeam, good all-round.
		kit6("mark_iii",
				key(en(tap("e1mk3_repulsor", s(0.8), 14f, Items.GLOWSTONE_DUST), 5f), en(tap("e1mk3_twin_volley", s(3), 12f, Items.GLOWSTONE), 15f)),
				key(en(hold("e1mk3_unibeam", s(10), 36f, Items.BEACON), 45f), en(tap("e1mk3_unibeam_sweep", s(14), 24f, Items.END_ROD), 45f)),
				key(en(tap("e1mk3_flight_boost", s(8), 0f, Items.FIREWORK_ROCKET), 15f), en(tap("e1mk3_barrel_roll", s(5), 0f, Items.FEATHER), 10f)),
				key(en(tap("e1mk3_mini_missiles", s(10), 18f, Items.FIREWORK_ROCKET), 30f), en(tap("e1mk3_missile_barrage", s(18), 14f, Items.TARGET), 45f)),
				key(enPerSecond(hold("e1mk3_tractor_beam", s(6), 0f, Items.LEAD), 5f), en(tap("e1mk3_tractor_throw", s(8), 16f, Items.SLIME_BALL), 15f)),
				enPerSecond(tap("e1mk3_targeting_hud", s(1), 0f, Items.SPYGLASS), 1f));

		// MK IV HYDRO: the gold deep-sea suit -- torpedoes, sonar, jets and a bubble shield; heavy and slow on land.
		kit6("mark_4",
				key(en(tap("e1mk4_torpedo", s(4), 30f, Items.TRIDENT), 15f), en(tap("e1mk4_homing_torpedo", s(8), 26f, Items.HEART_OF_THE_SEA), 25f)),
				key(en(tap("e1mk4_sonar_ping", s(12), 0f, Items.ECHO_SHARD), 10f), en(tap("e1mk4_sonic_pulse", s(14), 10f, Items.BELL), 20f)),
				key(en(tap("e1mk4_hydro_surge", s(4), 0f, Items.NAUTILUS_SHELL), 10f), en(tap("e1mk4_ballast_drop", s(10), 20f, Items.ANVIL), 15f)),
				key(en(tap("e1mk4_depth_charge", s(10), 28f, Items.TNT), 25f), en(tap("e1mk4_mine_field", s(20), 22f, Items.SEA_PICKLE), 35f)),
				key(en(tap("e1mk4_whirlpool", s(16), 3f, Items.NAUTILUS_SHELL), 30f), en(tap("e1mk4_bubble_shield", s(20), 0f, Items.GLASS), 25f)),
				tap("e1mk4_headlamps", s(0.5), 0f, Items.SEA_LANTERN));

		// MK V STEALTH: matte black and silent -- quiet air-jet flight, mobs lose you, Active Camo (v0.16.7 (user)); paper-thin armour.
		kit6("mark_v",
				key(en(tap("e1mk5_silenced_dart", s(1.5), 6f, Items.ARROW), 5f), en(tap("e1mk5_sleeper_dart", s(12), 0f, Items.TIPPED_ARROW), 15f)),
				key(en(tap("e1mk5_silent_takedown", s(4), 15f, Items.IRON_SWORD), 10f), en(tap("e1mk5_shadow_strike", s(10), 15f, Items.ENDER_PEARL), 25f)),
				key(en(tap("e1mk5_light_bend_blink", s(5), 0f, Items.ENDER_PEARL), 15f), enPerSecond(tap("e1mk5_active_camo", s(1), 0f, Items.TINTED_GLASS), 3f)),
				key(en(tap("e1mk5_emp", s(18), 0f, Items.REDSTONE_TORCH), 35f), en(tap("e1mk5_decoy", s(18), 0f, Items.ARMOR_STAND), 25f)),
				key(en(tap("e1mk5_radar", s(10), 0f, Items.COMPASS), 10f), en(tap("e1mk5_thermal_vision", s(16), 0f, Items.SPIDER_EYE), 15f)),
				enPerSecond(tap("e1mk5_silent_running", s(1), 0f, Items.BLACK_WOOL), 2f));

		// ================================================================ v0.16.7 (user) ERA II (docs/IRONMAN_ERA2_KITS.md)
		// "all the armours should have energy levels": every Era II move costs energy on the Era I scale (en / en per s; an
		// Arc Reactor Mk2 per suit, IronManSuits). "Shift+C ... keep it that way but move one of the utility abilities of
		// the suit to C while giving the suits another attack ability": C (tap) = a utility moved off V, and the slot it
		// left holds a new attack. Shift+C (hold) is still the fold into the portable form (IronManAbilityManager).

		// MK VI SUITCASE (mark_2): repulsors, the tractor beam, flares, the round Unibeam and JARVIS.
		kit6("mark_2",
				// R tap = quick repulsor, hold 1 s and release = charged (20) -- the shared repulsor slot code and its cooldowns
				// (it pays its 5 itself on the press: IronManComicMoves)
				key(en(reg(new Move("mk2_repulsor", Input.HOLD, 0, 11f, Items.GLOWSTONE_DUST, IronManAbilities.REPULSOR_BLAST)), 5f),
						en(tap("mk2_dash", s(8), 16f, Items.FEATHER), 15f)),
				key(en(hold("mk2_tractor_beam", s(9), 8f, Items.LEAD), 10f), en(tap("mk2_pressor_blast", s(7), 10f, Items.PISTON), 10f)),
				key(en(tap("mk2_flares", s(10), 5f, Items.FIREWORK_STAR), 10f), en(tap("mk2_supersonic", s(25), 0f, Items.ELYTRA), 20f)),
				key(en(tap("mk2_unibeam", s(12), 26f, Items.BEACON), 35f), en(tap("mk2_unibeam_flash", s(10), 8f, Items.GLOWSTONE), 20f)),
				// v0.16.7 (user): V tap = the new Wrist Laser (Highlight moved to C)
				key(en(tap("mk2_wrist_laser", s(5), 18f, Items.REDSTONE), 15f), en(tap("mk2_jarvis_scan", s(15), 0f, Items.SPYGLASS), 10f)),
				en(tap("mk2_highlight", s(1), 0f, Items.ENDER_EYE), 2f));

		// MK VII SILVER CENTURION (mark_6): pulse storms, ricochets, the chameleon effect and the heavy Unibeam.
		kit6("mark_6",
				key(en(tap("mk6_repulsor", s(0.5), 17f, Items.GLOWSTONE_DUST), 5f), en(tap("mk6_dash", s(7), 22f, Items.FEATHER), 15f)),
				key(en(tap("mk6_pulse_bolts", s(1.2), 10f, Items.AMETHYST_SHARD), 8f), en(tap("mk6_pulse_storm", s(12), 8f, Items.AMETHYST_CLUSTER), 30f)),
				key(en(tap("mk6_ricochet", s(6), 20f, Items.PRISMARINE_SHARD), 15f), en(tap("mk6_chameleon", s(20), 0f, Items.GLASS_PANE), 20f)),
				key(en(tap("mk6_heavy_unibeam", s(12), 40f, Items.BEACON), 40f), en(tap("mk6_unibeam_overcharge", s(25), 75f, Items.END_CRYSTAL), 50f)),
				// v0.16.7 (user) "the silver centurion armour should have a blade ability": V = Deploy Blades (the mode toggle:
				// 10 to deploy + 1/s while out) / Shift+V = Blade Whirl (the new attack). IronManCenturionBlades.
				key(enPerSecond(en(tap("mk6_deploy_blades", s(1), 0f, Items.IRON_SWORD), 10f), 1f),
						en(tap("mk6_blade_whirl", s(7), 16f, Items.NETHERITE_SWORD), 25f)),
				// C: tap = Energy Shield (moved off V; 15 s, 25 energy), hold 1 s = Overload Burst (moved off Shift+V; 14 s,
				// 30 energy) -- both keep their own cooldown / energy in IronManComicMoves.shieldOrBurst
				reg(new Move("mk6_energy_shield", Input.HOLD, 0, 0f, Items.SHIELD, "mk6_energy_shield")));

		// MK VIII SPACE (mark_vii): vacuum missiles, the orbital strike, boosters, the meteor dive, solar power and gravity.
		kit6("mark_vii",
				key(en(tap("mk7_repulsor", s(0.5), 20f, Items.GLOWSTONE_DUST), 5f), en(tap("mk7_dash", s(7), 25f, Items.FEATHER), 15f)),
				key(en(tap("mk7_vacuum_missiles", s(10), 20f, Items.FIREWORK_ROCKET), 25f), en(tap("mk7_orbital_strike", s(22), 18f, Items.TARGET), 45f)),
				key(en(tap("mk7_booster_rockets", s(6), 0f, Items.FIREWORK_ROCKET), 15f), en(tap("mk7_meteor_dive", s(10), 15f, Items.FIRE_CHARGE), 20f)),
				key(en(tap("mk7_solar_beam", s(10), 30f, Items.SUNFLOWER), 30f), en(tap("mk7_solar_flare", s(16), 22f, Items.BLAZE_POWDER), 30f)),
				// v0.16.7 (user): Shift+V = the new Singularity (Zero-G Field moved to C)
				key(en(tap("mk7_gravity_tether", s(12), 6f, Items.LEAD), 15f), en(tap("mk7_singularity", s(14), 22f, Items.CRYING_OBSIDIAN), 35f)),
				en(tap("mk7_zero_g_field", s(20), 0f, Items.PHANTOM_MEMBRANE), 20f));

		// MK IX INFERNO (mark_9): the Nether suit. Every move feeds or vents the heat sink (IronManMark9) -- a second meter on
		// top of the reactor. Lava swimming is passive.
		kit6("mark_9",
				key(en(tap("mk9_fire_blast", s(0.6), 21f, Items.FIRE_CHARGE), 5f), enPerSecond(hold("mk9_inferno_stream", s(4), 9f, Items.BLAZE_POWDER), 4f)),
				key(en(tap("mk9_magma_fist", s(1.5), 26f, Items.MAGMA_BLOCK), 10f), en(tap("mk9_eruption", s(12), 30f, Items.MAGMA_CREAM), 35f)),
				key(en(tap("mk9_afterburner", s(7), 24f, Items.FIREWORK_ROCKET), 15f), en(tap("mk9_lava_geyser", s(8), 16f, Items.LAVA_BUCKET), 15f)),
				key(en(tap("mk9_magma_bomb", s(10), 24f, Items.MAGMA_CREAM), 25f), en(tap("mk9_firestorm", s(18), 16f, Items.TARGET), 40f)),
				// v0.16.7 (user): Shift+V = the new Molten Lash (Thermal Siphon moved to C)
				key(en(tap("mk9_heat_dump", s(6), 46f, Items.BLAZE_ROD), 20f), en(tap("mk9_molten_lash", s(5), 22f, Items.CHAIN), 20f)),
				en(tap("mk9_thermal_siphon", s(10), 6f, Items.SOUL_CAMPFIRE), 10f));

		// HULKBUSTER I (mark_8): giant fists, slams, grabs, the chest mega-beam, the palm cannon and the replacement pod.
		kit6("mark_8",
				key(en(tap("mk8_punch_combo", s(2), 26f, Items.IRON_BLOCK), 10f), en(tap("mk8_rocket_fist", s(8), 45f, Items.FIREWORK_ROCKET), 30f)),
				key(en(tap("mk8_pile_driver", s(10), 40f, Items.ANVIL), 30f), en(tap("mk8_earthquake_stomp", s(14), 32f, Items.CRACKED_STONE_BRICKS), 40f)),
				key(en(tap("mk8_grab_pound", s(12), 20f, Items.CHAIN), 20f), en(tap("mk8_grab_throw", s(10), 18f, Items.SLIME_BALL), 20f)),
				key(en(tap("mk8_mega_beam", s(20), 18f, Items.BEACON), 50f), en(tap("mk8_missile_swarm", s(18), 18f, Items.TARGET), 45f)),
				// v0.16.7 (user): V tap = the new Palm Cannon (Hydraulic Brace moved to C); the pod costs nothing (it works on a
				// wrecked or browned-out suit -- it is the Hulkbuster's field repair)
				key(en(tap("mk8_palm_cannon", s(9), 22f, Items.GLOWSTONE), 35f), tap("mk8_replacement_pod", s(180), 0f, Items.CHEST)),
				// C: tap = Hydraulic Brace (25 s cooldown, 15 energy, both kept by IronManComicMoves so the hold always works);
				// hold 1 s = Sentry Mode (step out of the suit, free). The HUD reads the brace cooldown off the cooldown key.
				reg(new Move("mk8_hydraulic_brace", Input.HOLD, 0, 0f, Items.PISTON, "mk8_hydraulic_brace")));
	}

	private IronManComicKit() {
	}

	/** The kit for this suit id, or null (every shipped suit has one). */
	public static Kit kit(String suitId) {
		return suitId == null ? null : KITS.get(suitId);
	}

	/** Every kit, in progression order. */
	public static java.util.Collection<Kit> all() {
		return KITS.values();
	}

	/** The move bound to {@code slot} (1..5) of {@code suitId}'s kit, tap or Shift; null if none. */
	public static Move move(String suitId, int slot, boolean shift) {
		Kit k = kit(suitId);
		Key key = k == null ? null : k.key(slot);
		return key == null ? null : key.get(shift);
	}

	public static Move byId(String moveId) {
		return BY_ID.get(moveId);
	}

	public static java.util.Collection<Move> allMoves() {
		return BY_ID.values();
	}

	/** 1..5 for a kit slot ability id, else 0. */
	public static int slotOf(String abilityId) {
		int i = SLOTS.indexOf(abilityId);
		return i < 0 ? 0 : i + 1;
	}

	public static boolean isKitSlot(String abilityId) {
		return slotOf(abilityId) > 0;
	}
}
