package com.projecthero.mod.hero;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;

/**
 * Power-wide passive traits (attribute modifiers, immunities, ambient effects) that apply while a
 * power is the <em>active</em> one, independent of any single ability toggle.
 *
 * <p>Handlers are registered per power key by later batches. Everything here uses <em>transient</em>
 * attribute modifiers with fixed ids (same discipline as {@code ThorPassives}) so nothing can pile
 * up across relogs, and {@link #deactivate} is always called before {@link #activate} on a power
 * switch (see {@link ExperimentalPowers#setActive}) so stale modifiers cannot linger.
 */
public final class PowerPassives {
	@FunctionalInterface
	public interface PassiveSet {
		/** Reconcile this power's passives on the player. {@code active} = should they be on right now. */
		void reconcile(ServerPlayer player, boolean active);
	}

	private static final Map<String, PassiveSet> BY_POWER = new HashMap<>();
	// A power's own handler class may call registerTick more than once (e.g. one call for a stance
	// meter, another for unrelated entity upkeep) -- keep every one of them rather than letting a
	// later call silently replace an earlier one for the same power key.
	private static final Map<String, List<Consumer<ServerPlayer>>> TICK_BY_POWER = new HashMap<>();

	private PowerPassives() {
	}

	public static void register(String powerKey, PassiveSet passives) {
		BY_POWER.put(powerKey, passives);
	}

	public static void registerTick(String powerKey, Consumer<ServerPlayer> tick) {
		TICK_BY_POWER.computeIfAbsent(powerKey, k -> new ArrayList<>()).add(tick);
	}

	public static void activate(ServerPlayer player, Power power) {
		PassiveSet set = BY_POWER.get(power.key());
		if (set != null) {
			set.reconcile(player, true);
		}
	}

	public static void deactivate(ServerPlayer player, Power power) {
		PassiveSet set = BY_POWER.get(power.key());
		if (set != null) {
			set.reconcile(player, false);
		}
	}

	public static void tick(ServerPlayer player, Power power) {
		List<Consumer<ServerPlayer>> ticks = TICK_BY_POWER.get(power.key());
		if (ticks != null) {
			for (Consumer<ServerPlayer> tick : ticks) {
				tick.accept(player);
			}
		}
	}

	/**
	 * Safety net: called on join / respawn / command to re-establish passives on the new entity.
	 *
	 * <p>v0.9.3 -- persistent power stacking: this reconciles <b>every owned Experimental Tier power</b>,
	 * not just the selected one. An owned power's passives are on; an un-owned power's are off. (The
	 * name is kept for its many call sites.)
	 */
	public static void reconcileActive(ServerPlayer player) {
		for (Map.Entry<String, PassiveSet> e : BY_POWER.entrySet()) {
			e.getValue().reconcile(player, ExperimentalPowers.owns(player, e.getKey()));
		}
	}
}
