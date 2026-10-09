package com.projecthero.mod.flight;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.player.Player;

/**
 * v0.15.19, explicit user request ("when players touch the floor they stop flying"): the one landing rule every hero
 * flight shares -- Iron Man (every mark, the Mark 1's timed burst included) and the Repulsor Boots, Nova, the
 * Kryptonian, Thor, Green Lantern's Ring Flight, Max Steel's Turbo Flight, the Flight-power hero flight and the timed
 * rock / flame self-flight. A flier is down when
 * <ul>
 *   <li>it is on the ground ({@link Player#onGround()} -- on the server that is the client's own claim, from its
 *       movement packets),</li>
 *   <li>it is past the {@link #LIFTOFF_GRACE_TICKS} take-off grace (the tick a flight starts the player is usually still
 *       standing on the block they jumped off -- without the grace taking off from the ground would end at once), and</li>
 *   <li>it is not moving up ({@link #descending}: the vertical part of {@link Player#getKnownMovement()}, which on the
 *       server is the last movement the client reported).</li>
 * </ul>
 * Each system then ends its flight through its own toggle-off path, so the cleanup, sounds and animation are exactly
 * those of switching it off by hand. Vanilla creative flight is never touched (every caller skips {@code instabuild}).
 *
 * <p>Take-off ticks live here per player and per flight system ({@link #started} / {@link #ended}); a system that never
 * recorded one (a relog mid-flight) counts as past its grace. Static scratch state: cleared by
 * {@code diagnostics.ServerStateReset}.
 */
public final class FlightLanding {
	/** Ticks after a flight starts during which ground contact never ends it. */
	public static final int LIFTOFF_GRACE_TICKS = 10;
	/** Vertical speed (blocks / tick) at or below which a grounded flier counts as having touched down. */
	private static final double RISING_EPSILON = 1.0e-3;

	public static final String IRON_MAN = "iron_man";
	public static final String REPULSOR_BOOTS = "repulsor_boots";
	public static final String MAX_STEEL = "max_steel";
	public static final String HERO = "hero";
	public static final String TIMED = "timed";

	private static final Map<UUID, Map<String, Long>> STARTED = new ConcurrentHashMap<>();

	private FlightLanding() {
	}

	/** {@code system}'s flight just started for {@code player} (opens the take-off grace). */
	public static void started(Player player, String system) {
		STARTED.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(system, player.level().getGameTime());
	}

	/** {@code system}'s flight ended for {@code player}. */
	public static void ended(Player player, String system) {
		Map<String, Long> m = STARTED.get(player.getUUID());
		if (m != null) {
			m.remove(system);
			if (m.isEmpty()) {
				STARTED.remove(player.getUUID());
			}
		}
	}

	/** The game time {@code system}'s flight started for {@code player}, or -1 if none was recorded. */
	public static long startedAt(Player player, String system) {
		Map<String, Long> m = STARTED.get(player.getUUID());
		Long at = m == null ? null : m.get(system);
		return at == null ? -1L : at;
	}

	/** Has {@code player}, flying under {@code system}, touched down (ground, past the grace, not rising)? */
	public static boolean landed(Player player, String system) {
		return landed(player, startedAt(player, system));
	}

	/**
	 * As {@link #landed(Player, String)} for a system that keeps its own take-off tick ({@code startedAt}, game time; a
	 * negative value = unknown, past the grace).
	 */
	public static boolean landed(Player player, long startedAt) {
		if (!player.onGround()) {
			return false;
		}
		if (startedAt >= 0L && player.level().getGameTime() - startedAt < LIFTOFF_GRACE_TICKS) {
			return false;
		}
		return descending(player);
	}

	/** Not moving up: the vertical part of the last known movement is at most a hair above zero. */
	public static boolean descending(Player player) {
		return player.getKnownMovement().y <= RISING_EPSILON;
	}

	/** Server stopped: drop every recorded take-off. */
	public static void clearSessionState() {
		STARTED.clear();
	}
}
