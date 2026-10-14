package com.projecthero.mod.crime;

import java.util.UUID;
import java.util.function.Function;

/** v0.16.2: the kinds of street crime. Each is its own world-event type ({@code crime_<id>}). */
public enum CrimeType {
	MUGGING("mugging", MuggingCrime::new),
	BREAK_IN("break_in", BreakInCrime::new),
	GETAWAY("getaway", GetawayCrime::new),
	ARSON("arson", ArsonCrime::new);

	private final String id;
	private final Function<UUID, CrimeEvent> factory;

	CrimeType(String id, Function<UUID, CrimeEvent> factory) {
		this.id = id;
		this.factory = factory;
	}

	/** The command / lang id: {@code mugging}, {@code break_in}, ... */
	public String id() {
		return id;
	}

	/** The world-event type id saved with the event. */
	public String typeId() {
		return "crime_" + id;
	}

	public String nameKey() {
		return "event.projecthero.crime." + id;
	}

	public CrimeEvent create(UUID uuid) {
		return factory.apply(uuid);
	}

	/** Natural-trigger weight from the config. */
	public int weight() {
		CrimeConfig.Trigger t = CrimeConfig.trigger();
		return Math.max(0, switch (this) {
			case MUGGING -> t.weightMugging;
			case BREAK_IN -> t.weightBreakIn;
			case GETAWAY -> t.weightGetaway;
			case ARSON -> t.weightArson;
		});
	}

	public static CrimeType byId(String id) {
		for (CrimeType t : values()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}
}
