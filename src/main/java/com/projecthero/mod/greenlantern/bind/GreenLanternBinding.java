package com.projecthero.mod.greenlantern.bind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

/**
 * v0.17.1 Green Lantern revamp: the Construct Menu's catalogue and key bindings. CLIENT-SAFE (pure data) -- the menu
 * screen and HUD read it, {@code GreenLanternBindings} (server) acts on it.
 *
 * <p>Every thing the ring can do is an {@link Entry}: the ring's own moves (Ring Bolt, the Beam, the Oath, the suit...),
 * the old fixed-key attacks (Fist, Hammer, Gatling, Missiles, Giant Hand, Shield, Dome) and one entry per
 * {@link ConstructType}. Twelve keys can each hold one entry: G, H, Z, X, C, V and Shift + each. R (bolt / beam / Blast
 * Wave), N (dismiss / suit screen) and double-jump flight stay where they are.
 *
 * <p>Saved per player in {@link GreenLanternState#bindings}: a key missing from the map uses {@link #defaultFor}, an
 * empty string means the player cleared it -- so a Lantern who never opens the menu keeps today's layout.
 */
public final class GreenLanternBinding {
	/** The twelve bindable keys, in menu order. "S" + key = the Shift variant. */
	public static final List<String> KEYS = List.of("G", "H", "Z", "X", "C", "V", "SG", "SH", "SZ", "SX", "SC", "SV");

	/** The menu's five sections, in display order. */
	public enum Section {
		ATTACK, DEFENCE, BUILDING, SUMMON, RING;

		public String translationKey() {
			return "screen.projecthero.gl_menu.section." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Which icon atlas an entry's icon is in. */
	public static final int ATLAS_CONSTRUCTS = 0;
	public static final int ATLAS_ENTRIES = 1;
	/** v0.17.1: the survival constructs' own icon strip (textures/gui/green_lantern/survival.png, 4 x 16 px). */
	public static final int ATLAS_SURVIVAL = 2;
	/** v0.17.1: the Hard-Light Mech has an icon file of its own (textures/gui/green_lantern/mech_icon.png, cell 0). */
	public static final int ATLAS_MECH = 3;

	/**
	 * One menu entry. {@code type} is the construct it deploys (null for the ring's own moves); {@code hold} = the key is
	 * held (charge / channel / keep up) rather than tapped.
	 */
	public record Entry(String id, Section section, boolean hold, int atlas, int cell, ConstructType type) {
		public String nameKey() {
			return type != null ? type.translationKey() : "projecthero.green_lantern.entry." + id;
		}

		public String descKey() {
			return nameKey() + ".desc";
		}
	}

	// the ring's own moves (and the old fixed-key attacks)
	public static final String RING_BOLT = "ring_bolt";
	public static final String BEAM = "continuous_beam";
	public static final String BLAST_WAVE = "blast_wave";
	public static final String OATH = "oath";
	public static final String SUIT = "suit";
	public static final String SCAN = "ring_scan";
	public static final String QUICK_DEPLOY = "quick_deploy";
	public static final String FIST = "construct_fist";
	public static final String HAMMER = "war_hammer";
	public static final String GATLING = "emerald_gatling";
	public static final String MISSILES = "missile_barrage";
	public static final String GIANT_HAND = "giant_hand";
	public static final String SHIELD = "shield";
	public static final String DOME = "dome";

	private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
	private static final List<Entry> ALL;

	static {
		List<Entry> list = new ArrayList<>();
		list.add(new Entry(FIST, Section.ATTACK, false, ATLAS_ENTRIES, 7, null));
		list.add(new Entry(HAMMER, Section.ATTACK, false, ATLAS_ENTRIES, 8, null));
		list.add(new Entry(GATLING, Section.ATTACK, true, ATLAS_ENTRIES, 9, null));
		list.add(new Entry(MISSILES, Section.ATTACK, false, ATLAS_ENTRIES, 10, null));
		list.add(new Entry(GIANT_HAND, Section.ATTACK, false, ATLAS_ENTRIES, 11, null));
		list.add(new Entry(RING_BOLT, Section.ATTACK, false, ATLAS_ENTRIES, 0, null));
		list.add(new Entry(BEAM, Section.ATTACK, true, ATLAS_ENTRIES, 1, null));
		list.add(new Entry(BLAST_WAVE, Section.ATTACK, false, ATLAS_ENTRIES, 2, null));
		list.add(new Entry(SHIELD, Section.DEFENCE, true, ATLAS_ENTRIES, 12, null));
		list.add(new Entry(DOME, Section.DEFENCE, false, ATLAS_ENTRIES, 13, null));
		for (ConstructType t : ConstructType.values()) {
			boolean arsenal = t.kind() == ConstructType.Kind.ARSENAL;
			int cell = arsenal ? 16 + (t.ordinal() - ConstructType.BOXING_GLOVE.ordinal()) : t.ordinal();
			int atlas = arsenal ? ATLAS_ENTRIES : ATLAS_CONSTRUCTS;
			if (t.kind() == ConstructType.Kind.SURVIVAL) { // v0.17.1: their own strip
				atlas = ATLAS_SURVIVAL;
				cell = t.ordinal() - ConstructType.HARD_LIGHT_CHEST.ordinal();
			} else if (t == ConstructType.HARD_LIGHT_MECH) {
				atlas = ATLAS_MECH;
				cell = 0;
			}
			list.add(new Entry("c_" + t.name().toLowerCase(Locale.ROOT), sectionOf(t), t.isHold(), atlas, cell, t));
		}
		list.add(new Entry(OATH, Section.RING, true, ATLAS_ENTRIES, 3, null));
		list.add(new Entry(SUIT, Section.RING, false, ATLAS_ENTRIES, 4, null));
		list.add(new Entry(SCAN, Section.RING, false, ATLAS_ENTRIES, 5, null));
		list.add(new Entry(QUICK_DEPLOY, Section.RING, false, ATLAS_ENTRIES, 6, null));
		for (Entry e : list) {
			BY_ID.put(e.id(), e);
		}
		ALL = Collections.unmodifiableList(list);
	}

	private GreenLanternBinding() {
	}

	public static List<Entry> all() {
		return ALL;
	}

	public static Entry byId(String id) {
		return id == null ? null : BY_ID.get(id);
	}

	public static Entry forConstruct(ConstructType t) {
		return BY_ID.get("c_" + t.name().toLowerCase(Locale.ROOT));
	}

	public static Section sectionOf(ConstructType t) {
		return switch (t) {
			case HARD_LIGHT_WALL, CONTAINMENT_CAGE, ATMOSPHERE_BUBBLE, BEAR_TRAP, MIRROR_SHIELD -> Section.DEFENCE;
			case PLATFORM, BRIDGE, STAIR_RAMP, MINING_DRILL, LANTERN_LIGHT, HARD_LIGHT_TOOLS, RESCUE_TETHER, CARRY_PLATFORM,
					LAUNCH_PAD, GRAPPLING_CLAW, HARD_LIGHT_CHEST, SCAFFOLD_TOWER, ITEM_NET, TUNNELLER -> Section.BUILDING;
			case SENTRY_TURRET, EMERALD_WARRIOR, FIGHTER_JET, EMERALD_DRAGON, MED_BAY -> Section.SUMMON;
			default -> Section.ATTACK;
		};
	}

	/** Today's layout (v0.16.x keys), so nothing changes until a player rebinds. */
	public static String defaultFor(String key) {
		return switch (key) {
			case "G" -> FIST;
			case "SG" -> HAMMER;
			case "H" -> SUIT;
			case "Z" -> SHIELD;
			case "SZ" -> DOME;
			case "X" -> OATH;
			case "SX" -> GATLING;
			case "C" -> QUICK_DEPLOY;
			case "SC" -> MISSILES;
			case "V" -> GIANT_HAND;
			case "SV" -> SCAN;
			default -> ""; // Shift+H: nothing (Shift+H keeps opening the power wheel until something is bound)
		};
	}

	/** The entry on {@code key} for this state (its own binding, else the default), or null if the key is empty. */
	public static Entry resolve(GreenLanternState s, String key) {
		String id = s == null ? defaultFor(key) : s.bindings.getOrDefault(key, defaultFor(key));
		return id.isEmpty() ? null : byId(id);
	}

	/** Every key {@code entryId} is bound to, in {@link #KEYS} order. */
	public static List<String> keysOf(GreenLanternState s, String entryId) {
		List<String> out = new ArrayList<>();
		for (String k : KEYS) {
			Entry e = resolve(s, k);
			if (e != null && e.id().equals(entryId)) {
				out.add(k);
			}
		}
		return out;
	}

	/** Plain C opens the menu when held, so only tap entries may go on it. */
	public static boolean allowed(String key, Entry e) {
		return e != null && KEYS.contains(key) && !("C".equals(key) && e.hold());
	}

	/** "SG" -> "Shift+G" (the badge / tooltip label). */
	public static String label(String key) {
		return key.startsWith("S") ? "Shift+" + key.substring(1) : key;
	}

	/** The key id for a base key letter and shift state. */
	public static String key(char base, boolean shift) {
		return (shift ? "S" : "") + base;
	}
}
