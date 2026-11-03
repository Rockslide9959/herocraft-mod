package com.projecthero.mod.omnitrix.alien;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * v0.17.1: the Omnitrix's alien roster. The dial always lists every {@link Alien} declared here, in this order; an alien
 * with no {@link AlienKit} registered shows as "coming soon" and cannot be picked.
 *
 * <h2>Adding an alien's kit (the other eight)</h2>
 * <pre>
 *   // main source set, from your kit's init (called from OmnitrixMod.initialize -- one line per kit):
 *   Aliens.register(new XlrKit());          // XlrKit extends AlienKit, id() == Aliens.XLR8.id
 *   // client source set (from OmnitrixClient.initialize -- one line per kit):
 *   AlienClients.register(Aliens.XLR8.id, new XlrClient());
 * </pre>
 * See {@code docs/OMNITRIX_FRAMEWORK_0171.md}.
 */
public final class Aliens {
	/**
	 * One alien on the dial: its id (lang {@code projecthero.omnitrix.alien.<id>}), its colour, its dial slot and which watch
	 * carries it (v0.17.2: {@code recalibrated} = the Alien Force roster on the Recalibrated Omnitrix).
	 */
	public record Alien(String id, int colour, int order, boolean recalibrated) {
		public String nameKey() {
			return "projecthero.omnitrix.alien." + id;
		}
	}

	private static final Map<String, Alien> ALL = new LinkedHashMap<>();
	/** v0.17.2: the Recalibrated Omnitrix's dial (user: "Alien Force ten only" -- the original ten are not on it). */
	private static final Map<String, Alien> RECAL = new LinkedHashMap<>();
	private static final Map<String, AlienKit> KITS = new LinkedHashMap<>();

	// the original ten (user: "only the original 10 for now"), in the show's dial order
	public static final Alien HEATBLAST = declare("heatblast", 0xFF7A1A);
	public static final Alien FOUR_ARMS = declare("four_arms", 0xD8323A);
	public static final Alien XLR8 = declare("xlr8", 0x3A7BD5);
	public static final Alien DIAMONDHEAD = declare("diamondhead", 0x4FE0C8);
	public static final Alien UPGRADE = declare("upgrade", 0x72D12E);
	public static final Alien GHOSTFREAK = declare("ghostfreak", 0xC8D2D2);
	public static final Alien STINKFLY = declare("stinkfly", 0x9BCB3F);
	public static final Alien WILDMUTT = declare("wildmutt", 0xF08A24);
	public static final Alien RIPJAWS = declare("ripjaws", 0x7A98A4);
	public static final Alien GREY_MATTER = declare("grey_matter", 0xA6ACB4);

	// v0.17.2: the Recalibrated Omnitrix's ten (Ben 10: Alien Force), in the show's order
	public static final Alien SWAMPFIRE = declareRecal("swampfire", 0x3FA34D);
	public static final Alien ECHO_ECHO = declareRecal("echo_echo", 0xE8EEF2);
	public static final Alien HUMUNGOUSAUR = declareRecal("humungousaur", 0xB58A5A);
	public static final Alien JETRAY = declareRecal("jetray", 0xD8304A);
	public static final Alien BIG_CHILL = declareRecal("big_chill", 0x5AA8E8);
	public static final Alien CHROMASTONE = declareRecal("chromastone", 0xB05AE0);
	public static final Alien BRAINSTORM = declareRecal("brainstorm", 0xE0A040);
	public static final Alien SPIDERMONKEY = declareRecal("spidermonkey", 0x3A5AC8);
	public static final Alien GOOP = declareRecal("goop", 0x7CE03A);
	public static final Alien ALIEN_X = declareRecal("alien_x", 0xF4F4FF);

	private Aliens() {
	}

	private static Alien declare(String id, int colour) {
		Alien a = new Alien(id, colour, ALL.size(), false);
		ALL.put(id, a);
		return a;
	}

	private static Alien declareRecal(String id, int colour) {
		Alien a = new Alien(id, colour, RECAL.size(), true);
		RECAL.put(id, a);
		return a;
	}

	/** Registers {@code kit} for its alien ({@link AlienKit#id()} must be one of the declared ids). Replaces any earlier kit. */
	public static void register(AlienKit kit) {
		if (byId(kit.id()) == null) {
			throw new IllegalArgumentException("Unknown Omnitrix alien '" + kit.id() + "' (declare it in Aliens first)");
		}
		KITS.put(kit.id(), kit);
	}

	/** Every alien on the prototype's dial, in order. */
	public static List<Alien> all() {
		return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
	}

	/** v0.17.2: every alien on the Recalibrated Omnitrix's dial, in order. */
	public static List<Alien> recalibrated() {
		return Collections.unmodifiableList(new ArrayList<>(RECAL.values()));
	}

	/** v0.17.2: the dial of the watch {@code p} wears (the prototype's for a player without the recalibrated one). */
	public static List<Alien> roster(net.minecraft.world.entity.player.Player p) {
		return com.projecthero.mod.omnitrix.Omnitrix.recalibrated(p) ? recalibrated() : all();
	}

	/** v0.17.2: the playable aliens on the dial of the watch {@code p} wears, in order. */
	public static List<Alien> playable(net.minecraft.world.entity.player.Player p) {
		return dial(com.projecthero.mod.omnitrix.Omnitrix.recalibrated(p));
	}

	/** v0.17.2: the playable aliens on the prototype's dial, or ({@code recal}) the Recalibrated Omnitrix's, in order. */
	public static List<Alien> dial(boolean recal) {
		List<Alien> out = new ArrayList<>();
		for (Alien a : recal ? RECAL.values() : ALL.values()) {
			if (KITS.containsKey(a.id())) {
				out.add(a);
			}
		}
		return out;
	}

	/** v0.17.2: {@code id} has a kit AND is on the dial of the watch {@code p} wears. */
	public static boolean available(net.minecraft.world.entity.player.Player p, String id) {
		Alien a = byId(id);
		return a != null && KITS.containsKey(id) && a.recalibrated() == com.projecthero.mod.omnitrix.Omnitrix.recalibrated(p);
	}

	@Nullable
	public static Alien byId(String id) {
		if (id == null) {
			return null;
		}
		Alien a = ALL.get(id);
		return a != null ? a : RECAL.get(id);
	}

	/** The kit, or null while the alien is "coming soon". */
	@Nullable
	public static AlienKit kit(String id) {
		return id == null ? null : KITS.get(id);
	}

	public static boolean playable(String id) {
		return kit(id) != null;
	}

	/** The playable aliens, in dial order. */
	public static List<Alien> playable() {
		List<Alien> out = new ArrayList<>();
		for (Alien a : ALL.values()) {
			if (KITS.containsKey(a.id())) {
				out.add(a);
			}
		}
		return out;
	}
}
