package com.projecthero.mod.ironman.ability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * v0.16.1 COMIC-ACCURATE IRON MAN REWORK (explicit user design): the per-Mark key layout. Every suit's R / G / X / Z / V
 * key has a <b>tap</b> move and a <b>Shift</b> move ({@code tap / Shift}), each with its own cooldown (stored under
 * {@code <suit>/<move id>} in the synced {@code TonyStarkState.abilityReadyAt}). There is no suit energy: a move runs
 * whenever its cooldown is ready and the suit still has integrity. Pure data -- shared by the server
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
	/** Kit slot ids in key order R, G, X, Z, V (ability slots 1-5). */
	public static final List<String> SLOTS = List.of(SLOT_R, SLOT_G, SLOT_X, SLOT_Z, SLOT_V);

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
		/** {@code slot} 1..5 (R, G, X, Z, V). */
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

	private static void kit(String suitId, Key r, Key g, Key x, Key z, Key v) {
		KITS.put(suitId, new Kit(suitId, List.of(r, g, x, z, v)));
	}

	private static int s(double seconds) {
		return (int) Math.round(seconds * 20.0);
	}

	// ------------------------------------------------------------------ the nine kits

	static {
		// MARK 1 (grey iron): the cave-built prototype. No sustained flight -- rocket-boot hops and a glide.
		kit("mark_1",
				key(tap("mk1_iron_punch", s(1), 15f, Items.PISTON), tap("mk1_ground_pound", s(8), 12f, Items.ANVIL)),
				key(hold("mk1_flamethrower", 0, 8f, Items.FIRE_CHARGE), tap("mk1_smoke_screen", s(14), 0f, Items.CAMPFIRE)),
				key(tap("mk1_rocket_hop", s(2), 0f, Items.FEATHER), tap("mk1_glide", s(4), 0f, Items.ELYTRA)),
				key(tap("mk1_shoulder_rocket", s(8), 30f, Items.FIREWORK_ROCKET), tap("mk1_cluster_rocket", s(14), 12f, Items.FIREWORK_STAR)),
				key(tap("mk1_electromagnet", s(8), 0f, Items.LODESTONE), tap("mk1_magnetic_repel", s(8), 4f, Items.PISTON)));

		// MARK 1 V2 (gold): the rebuilt prototype -- palm repulsors, magnets, rocket skates, the chest lamp and an oil slick.
		kit("mark_1_v2",
				key(tap("mk1v2_palm_repulsor", s(1), 12f, Items.GLOWSTONE_DUST), tap("mk1v2_dash", s(7), 14f, Items.FEATHER)),
				key(tap("mk1v2_magnet_pull", s(6), 4f, Items.LODESTONE), tap("mk1v2_magnet_push", s(6), 6f, Items.PISTON)),
				key(tap("mk1v2_rocket_skates", s(10), 0f, Items.GOLDEN_BOOTS), tap("mk1v2_spin_kick", s(6), 14f, Items.GOLDEN_SWORD)),
				key(tap("mk1v2_lamp_flash", s(10), 0f, Items.LANTERN), tap("mk1v2_searchlight", s(12), 0f, Items.SPYGLASS)),
				key(tap("mk1v2_oil_slick", s(12), 0f, Items.INK_SAC), tap("mk1v2_ignite_slick", s(10), 6f, Items.FLINT_AND_STEEL)));

		// MARK 2 (classic red and gold): repulsors, the tractor beam, flares, the round Unibeam and JARVIS.
		kit("mark_2",
				// R tap = quick repulsor, hold 1 s and release = charged (20) -- the shared repulsor slot code and its cooldowns
				key(reg(new Move("mk2_repulsor", Input.HOLD, 0, 11f, Items.GLOWSTONE_DUST, IronManAbilities.REPULSOR_BLAST)),
						tap("mk2_dash", s(8), 16f, Items.FEATHER)),
				key(hold("mk2_tractor_beam", s(9), 8f, Items.LEAD), tap("mk2_pressor_blast", s(7), 10f, Items.PISTON)),
				key(tap("mk2_flares", s(10), 5f, Items.FIREWORK_STAR), tap("mk2_supersonic", s(25), 0f, Items.ELYTRA)),
				key(tap("mk2_unibeam", s(12), 26f, Items.BEACON), tap("mk2_unibeam_flash", s(10), 8f, Items.GLOWSTONE)),
				key(tap("mk2_highlight", s(1), 0f, Items.ENDER_EYE), tap("mk2_jarvis_scan", s(15), 0f, Items.SPYGLASS)));

		// MARK 3 (upgraded classic): rapid repulsors, pulse bolts, missiles, the triangular Unibeam and an energy shield.
		kit("mark_iii",
				key(tap("mk3_rapid_repulsors", s(1.5), 9f, Items.GLOWSTONE_DUST), tap("mk3_dash", s(8), 19f, Items.FEATHER)),
				key(tap("mk3_pulse_bolts", s(1.5), 8f, Items.AMETHYST_SHARD), tap("mk3_sonic_clap", s(8), 20f, Items.BELL)),
				key(tap("mk3_micro_missiles", s(10), 18f, Items.FIREWORK_ROCKET), tap("mk3_missile_barrage", s(18), 16f, Items.TARGET)),
				key(tap("mk3_tri_unibeam", s(12), 32f, Items.BEACON), tap("mk3_unibeam_sweep", s(14), 22f, Items.END_ROD)),
				key(tap("mk3_energy_shield", s(16), 0f, Items.SHIELD), tap("mk3_shield_bash", s(8), 18f, Items.IRON_DOOR)));

		// MARK 4 HYDRO: sonar, torpedoes, the propeller, lights and the ballast -- all x1.5 underwater.
		kit("mark_4",
				key(tap("mk4_sonar_pulse", s(10), 6f, Items.ECHO_SHARD), tap("mk4_sonar_mark", s(14), 0f, Items.SCULK_SENSOR)),
				key(tap("mk4_torpedo", s(6), 30f, Items.TRIDENT), tap("mk4_depth_charge", s(12), 28f, Items.TNT)),
				key(tap("mk4_propeller_dash", s(6), 16f, Items.HEART_OF_THE_SEA), tap("mk4_whirlpool", s(16), 3f, Items.NAUTILUS_SHELL)),
				key(tap("mk4_headlamp_beam", s(5), 14f, Items.SEA_LANTERN), tap("mk4_floodlight", s(14), 0f, Items.GLOWSTONE)),
				key(tap("mk4_ballast_anchor", s(12), 10f, Items.ANVIL), tap("mk4_pressure_crush", s(14), 24f, Items.HEAVY_CORE)));

		// MARK 5 STEALTH: silent shots, the cloak, jammers and decoys.
		kit("mark_v",
				key(tap("mk5_silenced_repulsor", s(1), 14f, Items.GLOWSTONE_DUST), tap("mk5_silent_takedown", s(12), 45f, Items.IRON_SWORD)),
				key(tap("mk5_chameleon_cloak", s(25), 0f, Items.GLASS_PANE), tap("mk5_light_bend_blink", s(6), 0f, Items.ENDER_PEARL)),
				key(tap("mk5_sensor_jammer", s(14), 0f, Items.SCULK_SHRIEKER), tap("mk5_emp_pulse", s(18), 8f, Items.REDSTONE_TORCH)),
				key(tap("mk5_low_glow_shot", s(4), 26f, Items.SPYGLASS), tap("mk5_marked_shot", s(14), 0f, Items.TARGET)),
				key(tap("mk5_holo_decoy", s(18), 0f, Items.ARMOR_STAND), tap("mk5_decoy_swap", s(10), 0f, Items.CHORUS_FRUIT)));

		// MARK 6 SILVER CENTURION: pulse storms, ricochets, the chameleon effect and the heavy Unibeam.
		kit("mark_6",
				key(tap("mk6_repulsor", s(0.5), 17f, Items.GLOWSTONE_DUST), tap("mk6_dash", s(7), 22f, Items.FEATHER)),
				key(tap("mk6_pulse_bolts", s(1.2), 10f, Items.AMETHYST_SHARD), tap("mk6_pulse_storm", s(12), 8f, Items.AMETHYST_CLUSTER)),
				key(tap("mk6_ricochet", s(6), 20f, Items.PRISMARINE_SHARD), tap("mk6_chameleon", s(20), 0f, Items.GLASS_PANE)),
				key(tap("mk6_heavy_unibeam", s(12), 40f, Items.BEACON), tap("mk6_unibeam_overcharge", s(25), 75f, Items.END_CRYSTAL)),
				key(tap("mk6_energy_shield", s(15), 0f, Items.SHIELD), tap("mk6_overload_burst", s(14), 30f, Items.TNT)));

		// MARK 7 SPACE: vacuum missiles, the orbital strike, boosters, the meteor dive, solar power and gravity.
		kit("mark_vii",
				key(tap("mk7_repulsor", s(0.5), 20f, Items.GLOWSTONE_DUST), tap("mk7_dash", s(7), 25f, Items.FEATHER)),
				key(tap("mk7_vacuum_missiles", s(10), 20f, Items.FIREWORK_ROCKET), tap("mk7_orbital_strike", s(22), 18f, Items.TARGET)),
				key(tap("mk7_booster_rockets", s(6), 0f, Items.FIREWORK_ROCKET), tap("mk7_meteor_dive", s(10), 15f, Items.FIRE_CHARGE)),
				key(tap("mk7_solar_beam", s(10), 30f, Items.SUNFLOWER), tap("mk7_solar_flare", s(16), 22f, Items.BLAZE_POWDER)),
				key(tap("mk7_gravity_tether", s(12), 6f, Items.LEAD), tap("mk7_zero_g_field", s(20), 0f, Items.PHANTOM_MEMBRANE)));

		// MARK 8 HULKBUSTER: giant fists, slams, grabs, the chest mega-beam and the brace / replacement pod.
		kit("mark_8",
				key(tap("mk8_punch_combo", s(2), 26f, Items.IRON_BLOCK), tap("mk8_rocket_fist", s(8), 45f, Items.FIREWORK_ROCKET)),
				key(tap("mk8_pile_driver", s(10), 40f, Items.ANVIL), tap("mk8_earthquake_stomp", s(14), 32f, Items.CRACKED_STONE_BRICKS)),
				key(tap("mk8_grab_pound", s(12), 20f, Items.CHAIN), tap("mk8_grab_throw", s(10), 18f, Items.SLIME_BALL)),
				key(tap("mk8_mega_beam", s(20), 18f, Items.BEACON), tap("mk8_missile_swarm", s(18), 18f, Items.TARGET)),
				key(tap("mk8_hydraulic_brace", s(25), 0f, Items.PISTON), tap("mk8_replacement_pod", s(180), 0f, Items.CHEST)));
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
