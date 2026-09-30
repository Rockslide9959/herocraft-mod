package com.projecthero.mod.hero.visual;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.13.22: each mutation declares its own HUD bars here instead of the HUD keeping a hard-coded list of
 * resource names. A registered meter is drawn by {@code AbilityHud} from the power's synced resource map
 * ({@code powerKey + "/" + resource}); anything not registered (and not in the HUD's legacy list) is
 * bookkeeping and never drawn.
 *
 * <p>Bar styles use the user's HUD vocabulary: {@link Style#METER} (bordered 4 px bar),
 * {@link Style#HAIRLINE} (borderless 3 px), {@link Style#SLAB} (thick 7 px), {@link Style#GAUGE}
 * (segmented into ten cells).
 */
public final class MutationMeters {
	public enum Kind {
		/** starts full, spent by use; shown while not full (or always, see {@link Spec#always}) */
		RESERVE,
		/** climbs from zero; shown while non-zero */
		BUILD,
		/** a countdown on something running; shown while non-zero */
		TIMER
	}

	public enum Style { METER, HAIRLINE, SLAB, GAUGE }

	/**
	 * @param powerKey  owning power, e.g. {@code power_08_pyrokinesis}
	 * @param resource  resource name inside that power's map
	 * @param label     literal label shown under the bar
	 * @param max       full-bar value
	 * @param color     ARGB fill colour
	 * @param always    draw even when full / empty (the power's main fuel gauge)
	 * @param showValue append the value as a percentage to the label
	 * @param above     v0.14.5: drawn as a Hairline bar ABOVE the ability-key row instead of below it
	 * @param textColor v0.14.5: label colour (0 = the HUD default)
	 */
	public record Spec(String powerKey, String resource, Kind kind, Style style, String label, float max, int color,
			boolean always, boolean showValue, boolean above, int textColor) {
		public Spec(String powerKey, String resource, Kind kind, Style style, String label, float max, int color,
				boolean always, boolean showValue) {
			this(powerKey, resource, kind, style, label, max, color, always, showValue, false, 0);
		}
	}

	private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

	private MutationMeters() {
	}

	public static void register(Spec spec) {
		SPECS.put(spec.powerKey() + "/" + spec.resource(), spec);
	}

	/** Shorthand for the common case: a bordered METER bar. */
	public static void register(String powerKey, String resource, Kind kind, String label, float max, int color,
			boolean always) {
		register(new Spec(powerKey, resource, kind, Style.METER, label, max, color, always, false));
	}

	/** The spec for {@code powerKey/resource}, or null. */
	public static Spec get(String powerKey, String resource) {
		return SPECS.get(powerKey + "/" + resource);
	}

	public static Collection<Spec> all() {
		return SPECS.values();
	}
}
