package com.projecthero.mod.greenlantern.mech;

import java.util.List;

import com.projecthero.mod.greenlantern.bind.GreenLanternBinding;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.hero.AbilitySlot;

/**
 * v0.17.1: which key fires each Hard-Light Mech construct combo. CLIENT-SAFE (pure data): the server's key handler
 * ({@code GreenLanternMech#handleKey}), the mech HUD and the Alt move list all ask this.
 *
 * <h2>The rule</h2>
 * Inside the mech a combo is <b>Shift + the key its construct is bound to</b> outside it -- "use the drill construct
 * while in the mech":
 * <ol>
 *   <li>Take the construct's Construct Menu bindings in menu order (G, H, Z, X, C, V, then the Shift keys) and use the
 *   first one that is not H (H never drives the mech). Bound to G or to Shift+G, the combo is Shift+G in the mech.</li>
 *   <li>Not bound anywhere (or only to H / Shift+H), or its key is already taken by an earlier combo in
 *   {@link Combo} order: its fixed fallback -- Shift+G for the Giga Drill, Shift+X for the Katana -- or, if that is
 *   taken too, the first free one of Shift+G, Shift+X, Shift+Z, Shift+V, Shift+C.</li>
 * </ol>
 * So every combo always has a key, and two combos never share one. Shift+R stays the Rocket Fist (the katana's iaido
 * while it is drawn); R, H and N are never combo keys (N climbs out).
 */
public final class MechComboKeys {
	/** The mech's combos, in priority order (an earlier one keeps a contested key). Appended only. */
	public enum Combo {
		/** Mining Drill -> the Giga Drill (hold to spin up, let go to charge; a full spin ends in GIGA DRILL BREAK). */
		GIGA_DRILL(ConstructType.MINING_DRILL, 'G'),
		/** Energy Blade -> the Hard-Light Katana (tap to draw / sheathe; R, Shift+R and G become sword moves). */
		KATANA(ConstructType.ENERGY_BLADE, 'X');

		public final ConstructType construct;
		public final char fallback;

		Combo(ConstructType construct, char fallback) {
			this.construct = construct;
			this.fallback = fallback;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** The keys a combo may sit on in the mech (the base letters of the mech's free Shift layer). */
	public static final List<Character> FREE = List.of('G', 'X', 'Z', 'V', 'C');

	private MechComboKeys() {
	}

	/** The base key letter ('G', 'X', ...) of each combo for this Lantern's bindings, indexed by {@link Combo#ordinal}. */
	public static char[] keys(GreenLanternState s) {
		Combo[] all = Combo.values();
		char[] out = new char[all.length];
		boolean[] taken = new boolean[128];
		for (Combo c : all) {
			char k = bound(s, c.construct);
			if (k == 0 || taken[k]) {
				k = 0;
				if (!taken[c.fallback]) {
					k = c.fallback;
				} else {
					for (char f : FREE) {
						if (!taken[f]) {
							k = f;
							break;
						}
					}
				}
			}
			out[c.ordinal()] = k;
			if (k != 0) {
				taken[k] = true;
			}
		}
		return out;
	}

	/** The key letter of {@code combo}. */
	public static char keyOf(GreenLanternState s, Combo combo) {
		return keys(s)[combo.ordinal()];
	}

	/** Whether {@code combo}'s key came from the Lantern's own binding (false: the fallback). */
	public static boolean fromBinding(GreenLanternState s, Combo combo) {
		return bound(s, combo.construct) == keyOf(s, combo);
	}

	/** The combo on Shift + {@code base}, or null. */
	public static Combo comboOn(GreenLanternState s, char base) {
		char[] k = keys(s);
		for (Combo c : Combo.values()) {
			if (k[c.ordinal()] == base) {
				return c;
			}
		}
		return null;
	}

	/** The first usable base letter {@code type} is bound to (H skipped), or 0. */
	private static char bound(GreenLanternState s, ConstructType type) {
		GreenLanternBinding.Entry e = GreenLanternBinding.forConstruct(type);
		if (e == null) {
			return 0;
		}
		for (String key : GreenLanternBinding.keysOf(s, e.id())) {
			char base = key.charAt(key.length() - 1);
			if (FREE.contains(base)) {
				return base;
			}
		}
		return 0;
	}

	/** The mech key slot a base letter arrives on (G = slot 2 ... C = slot 6), or null. */
	public static AbilitySlot slotOf(char base) {
		return switch (base) {
			case 'G' -> AbilitySlot.SLOT_2;
			case 'X' -> AbilitySlot.SLOT_3;
			case 'Z' -> AbilitySlot.SLOT_4;
			case 'V' -> AbilitySlot.SLOT_5;
			case 'C' -> AbilitySlot.SLOT_6;
			default -> null;
		};
	}

	/** The base letter of a mech key slot, or 0 (R / unknown). */
	public static char baseOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> 'G';
			case SLOT_3 -> 'X';
			case SLOT_4 -> 'Z';
			case SLOT_5 -> 'V';
			case SLOT_6 -> 'C';
			default -> 0;
		};
	}
}
