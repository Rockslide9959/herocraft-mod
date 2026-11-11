package com.projecthero.mod.client.omnitrix.sequence;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.sequence.ShowSequence;
import com.projecthero.mod.omnitrix.sequence.ShowSequences;

/**
 * v0.17.3: the client halves of the show sequences, by alien id. For every registered {@link ShowSequence}, the class
 * {@code <Pascal>Show} in this package is looked up by name ({@code four_arms} -> {@code FourArmsShow}); no shared list to edit.
 * Anything else can {@link #register} itself.
 */
public final class ShowSequencesClient {
	private static final Map<String, ShowSequenceClient> BY_ID = new HashMap<>();
	private static boolean loaded;

	private ShowSequencesClient() {
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		for (ShowSequence s : ShowSequences.all()) {
			if (BY_ID.containsKey(s.alienId())) {
				continue;
			}
			String name = ShowSequencesClient.class.getPackageName() + "." + ShowSequences.pascal(s.alienId()) + "Show";
			try {
				Object o = Class.forName(name).getDeclaredConstructor().newInstance();
				if (o instanceof ShowSequenceClient c && c.alienId().equals(s.alienId())) {
					BY_ID.put(s.alienId(), c);
				} else {
					ProjectHeroMod.LOGGER.warn("[omnitrix] {} is not a ShowSequenceClient for {}", name, s.alienId());
				}
			} catch (ClassNotFoundException e) {
				ProjectHeroMod.LOGGER.warn("[omnitrix] {} has a show sequence but no client half ({})", s.alienId(), name);
			} catch (ReflectiveOperationException | RuntimeException e) {
				ProjectHeroMod.LOGGER.error("[omnitrix] could not load {}", name, e);
			}
		}
	}

	public static synchronized void register(ShowSequenceClient c) {
		BY_ID.put(c.alienId(), c);
	}

	/** The client half for {@code alienId}, or null. */
	public static ShowSequenceClient get(String alienId) {
		load();
		return alienId == null ? null : BY_ID.get(alienId);
	}
}
