package com.projecthero.mod.miraculous;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * v0.16.6 (user): the transformation is SPOKEN -- the holder types the phrase in chat (it still goes out to chat: the
 * hero shouts it). Matching is lenient: case, punctuation, extra spaces and curly vs straight apostrophes don't matter,
 * and the Kwami's name is optional in both phrases (v0.16.7, user: "Spots on!" alone transforms). Only the holder's own Kwami's phrases count.
 * <pre>
 *   (Tikki,) spots on!   Spots off!    (Tikki, spots off!)
 *   Plagg, claws out!    Claws in!     (Plagg, claws in!)
 *   Trixx, let's pounce! Let's rest!   (Trixx, let's rest!)
 *   Nooroo, dark wings rise!  Dark wings fall!  (Nooroo, dark wings fall!)
 * </pre>
 */
public final class MiraculousPhrases {
	public enum Spoken {
		TRANSFORM, DETRANSFORM
	}

	private MiraculousPhrases() {
	}

	/** The hero's plain English name (command feedback; players see the lang key {@code projecthero.hero_tier.<key>}). */
	public static String heroName(Kwami k) {
		return switch (k) {
			case TIKKI -> "Ladybug";
			case PLAGG -> "Cat Noir";
			case TRIXX -> "Rena Rouge";
			case NOOROO -> "Hawk Moth";
			case WAYZZ -> "Carapace";
			case KAALKI -> "Pegasus";
			case SASS -> "Viperion";
		};
	}

	/** The transformation phrase, as shown to players (guide, HUD hint, intro). */
	public static String transformPhrase(Kwami k) {
		return switch (k) {
			case TIKKI -> "Tikki, spots on!";
			case PLAGG -> "Plagg, claws out!";
			case TRIXX -> "Trixx, let's pounce!";
			case NOOROO -> "Nooroo, dark wings rise!";
			case WAYZZ -> "Wayzz, shell on!";
			case KAALKI -> "Kaalki, full gallop!";
			case SASS -> "Sass, scales slither!";
		};
	}

	/** The detransformation phrase, as shown to players. */
	public static String detransformPhrase(Kwami k) {
		return switch (k) {
			case TIKKI -> "Spots off!";
			case PLAGG -> "Claws in!";
			case TRIXX -> "Let's rest!";
			case NOOROO -> "Dark wings fall!";
			case WAYZZ -> "Shell off!";
			case KAALKI -> "Dismount!";
			case SASS -> "Scales rest!";
		};
	}

	/** Lower case, curly quotes straightened, then only letters / digits / single spaces (apostrophes dropped too). */
	public static String normalize(String raw) {
		String s = raw.toLowerCase(Locale.ROOT).replace('\u2019', '\'').replace('\u2018', '\'').replace('\u02BC', '\'');
		StringBuilder b = new StringBuilder(s.length());
		boolean space = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				if (space && b.length() > 0) {
					b.append(' ');
				}
				space = false;
				b.append(c);
			} else if (c != '\'') {
				space = true;
			}
		}
		return b.toString();
	}

	/** What {@code message} says for this Kwami's holder, or null for anything else. */
	public static @Nullable Spoken match(Kwami k, String message) {
		String m = normalize(message);
		String on = normalize(transformPhrase(k));
		// v0.16.7 (user): the Kwami's name is optional ("Spots on!" works as well as "Tikki, spots on!")
		String prefix = k.id + " ";
		if (m.equals(on) || (on.startsWith(prefix) && m.equals(on.substring(prefix.length())))) {
			return Spoken.TRANSFORM;
		}
		String off = normalize(detransformPhrase(k));
		if (m.equals(off) || m.equals(k.id + " " + off)) {
			return Spoken.DETRANSFORM;
		}
		return null;
	}
}
