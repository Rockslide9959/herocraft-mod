package com.projecthero.mod.hero;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of {@link AbilityHandler}s keyed by {@code powerKey + "/" + abilityId}. Later batches call
 * {@link #register} (usually from a per-power {@code XxxHandlers.register()} bootstrap) to attach
 * mechanics. An ability with no registered handler is legal -- {@link AbilityRouter} just tells the
 * player it is not implemented yet.
 */
public final class AbilityHandlers {
	private static final Map<String, AbilityHandler> BY_KEY = new HashMap<>();

	private AbilityHandlers() {
	}

	public static void register(String powerKey, String abilityId, AbilityHandler handler) {
		BY_KEY.put(powerKey + "/" + abilityId, handler);
	}

	public static AbilityHandler get(Power power, Ability ability) {
		return BY_KEY.get(power.key() + "/" + ability.id());
	}

	public static boolean has(Power power, Ability ability) {
		return BY_KEY.containsKey(power.key() + "/" + ability.id());
	}

	public static int count() {
		return BY_KEY.size();
	}
}
