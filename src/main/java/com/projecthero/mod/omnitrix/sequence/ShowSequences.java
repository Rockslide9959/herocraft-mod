package com.projecthero.mod.omnitrix.sequence;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.OmnitrixSounds;

/**
 * v0.17.3: the show transformations, by alien id. Each show alien's {@link ShowSequence} is found by name -- {@code <Pascal>Sequence}
 * in this package ({@code four_arms} -> {@code FourArmsSequence}) -- so adding one never touches a shared file. The aliens looked for
 * are {@link OmnitrixSounds#SEQUENCE_ALIENS} (their soundtracks are pre-registered there); anything else can {@link #register} itself.
 */
public final class ShowSequences {
	private static final Map<String, ShowSequence> BY_ID = new LinkedHashMap<>();

	static {
		for (String id : OmnitrixSounds.SEQUENCE_ALIENS) {
			load(id);
		}
	}

	private ShowSequences() {
	}

	/** {@code grey_matter} -> {@code GreyMatter} (the class-name stem for an alien id). */
	public static String pascal(String alienId) {
		StringBuilder b = new StringBuilder();
		for (String part : alienId.split("_")) {
			if (!part.isEmpty()) {
				b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
			}
		}
		return b.toString();
	}

	private static void load(String id) {
		String name = ShowSequences.class.getPackageName() + "." + pascal(id) + "Sequence";
		try {
			Object o = Class.forName(name).getDeclaredConstructor().newInstance();
			if (o instanceof ShowSequence s && s.alienId().equals(id)) {
				BY_ID.put(id, s);
			} else {
				ProjectHeroMod.LOGGER.warn("[omnitrix] {} is not a ShowSequence for {}", name, id);
			}
		} catch (ClassNotFoundException e) {
			// not built yet: the quick transformation only
		} catch (ReflectiveOperationException | RuntimeException e) {
			ProjectHeroMod.LOGGER.error("[omnitrix] could not load the show sequence {}", name, e);
		}
	}

	/** Adds (or replaces) a sequence for an alien outside {@link OmnitrixSounds#SEQUENCE_ALIENS}. Call at mod init. */
	public static synchronized void register(ShowSequence s) {
		BY_ID.put(s.alienId(), s);
	}

	/** The sequence for {@code alienId}, or null. */
	public static ShowSequence get(String alienId) {
		return alienId == null ? null : BY_ID.get(alienId);
	}

	public static boolean has(String alienId) {
		return get(alienId) != null;
	}

	/** The one whose id hashes to {@code hash} (the synced timers hold the playing sequence's id as its hash), or null. */
	public static ShowSequence byHash(long hash) {
		for (ShowSequence s : BY_ID.values()) {
			if (s.alienId().hashCode() == hash) {
				return s;
			}
		}
		return null;
	}

	public static Collection<ShowSequence> all() {
		return Collections.unmodifiableCollection(BY_ID.values());
	}
}
