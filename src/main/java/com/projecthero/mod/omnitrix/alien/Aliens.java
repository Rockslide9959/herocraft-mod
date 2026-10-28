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
	/** One alien on the dial: its id (lang {@code projecthero.omnitrix.alien.<id>}), its colour and its dial slot. */
	public record Alien(String id, int colour, int order) {
		public String nameKey() {
			return "projecthero.omnitrix.alien." + id;
		}
	}

	private static final Map<String, Alien> ALL = new LinkedHashMap<>();
	private static final Map<String, AlienKit> KITS = new LinkedHashMap<>();

	// the original ten (user: "only the original 10 for now"), in the show's dial order
	public static final Alien HEATBLAST = declare("heatblast", 0xFF7A1A);
	public static final Alien FOUR_ARMS = declare("four_arms", 0xD8323A);
	public static final Alien XLR8 = declare("xlr8", 0x3A7BD5);
	public static final Alien DIAMONDHEAD = declare("diamondhead", 0x4FE0C8);
	public static final Alien UPGRADE = declare("upgrade", 0x38E05A);
	public static final Alien GHOSTFREAK = declare("ghostfreak", 0xC8D2D2);
	public static final Alien STINKFLY = declare("stinkfly", 0x9BCB3F);
	public static final Alien WILDVINE = declare("wildvine", 0x4A9A2A);
	public static final Alien RIPJAWS = declare("ripjaws", 0x7A98A4);
	public static final Alien GREY_MATTER = declare("grey_matter", 0xA6ACB4);

	private Aliens() {
	}

	private static Alien declare(String id, int colour) {
		Alien a = new Alien(id, colour, ALL.size());
		ALL.put(id, a);
		return a;
	}

	/** Registers {@code kit} for its alien ({@link AlienKit#id()} must be one of the declared ids). Replaces any earlier kit. */
	public static void register(AlienKit kit) {
		if (!ALL.containsKey(kit.id())) {
			throw new IllegalArgumentException("Unknown Omnitrix alien '" + kit.id() + "' (declare it in Aliens first)");
		}
		KITS.put(kit.id(), kit);
	}

	/** Every alien on the dial, in order. */
	public static List<Alien> all() {
		return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
	}

	@Nullable
	public static Alien byId(String id) {
		return id == null ? null : ALL.get(id);
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
