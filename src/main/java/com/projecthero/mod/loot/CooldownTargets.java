package com.projecthero.mod.loot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.5: what a Cooldown Stim shortens. Each cooldown system registers a {@link Shortener} that scales every
 * <em>remaining</em> cooldown of the player by a factor (0.5 for the stim) and returns how many it changed. If nothing
 * changes anywhere the stim is refused (not used up).
 *
 * <p>Almost every power stores {@code Map<String, Long> abilityReadyAt} (absolute "ready again at" game times) in a synced
 * attachment; {@link #readyAtMap} covers those in one line:
 *
 * <pre>{@code
 * CooldownTargets.register("black_panther",
 *         CooldownTargets.readyAtMap(ModAttachments.BLACK_PANTHER_STATE, BlackPantherState::copy, s -> s.abilityReadyAt));
 * }</pre>
 *
 * Built-in shorteners live in {@link SupplyTargetDefaults}.
 */
public final class CooldownTargets {
	/** Scales the player's remaining cooldowns by {@code factor}; returns how many cooldowns it shortened. */
	@FunctionalInterface
	public interface Shortener {
		int shorten(ServerPlayer player, double factor);
	}

	public record Target(String id, Shortener shortener) {
	}

	/** Anything further out than this is a sentinel / state value stored in a cooldown map, not a cooldown. */
	public static final long MAX_REAL_COOLDOWN_TICKS = 20L * 60 * 60;

	private static final List<Target> TARGETS = new CopyOnWriteArrayList<>();

	private CooldownTargets() {
	}

	public static void register(String id, Shortener shortener) {
		Target t = new Target(id, shortener);
		for (int i = 0; i < TARGETS.size(); i++) {
			if (TARGETS.get(i).id().equals(id)) {
				TARGETS.set(i, t);
				return;
			}
		}
		TARGETS.add(t);
	}

	public static List<Target> targets() {
		return List.copyOf(TARGETS);
	}

	/** Runs every shortener; returns the total number of cooldowns shortened. */
	public static int apply(ServerPlayer player, double factor) {
		int n = 0;
		for (Target t : TARGETS) {
			try {
				n += t.shortener().shorten(player, factor);
			} catch (RuntimeException e) {
				com.projecthero.mod.ProjectHeroMod.LOGGER.warn("Cooldown Stim target {} failed", t.id(), e);
			}
		}
		return n;
	}

	/** {@code now + (readyAt - now) * factor}, or {@code readyAt} unchanged when it isn't a live cooldown. */
	public static long scaled(long now, long readyAt, double factor) {
		if (!isLive(now, readyAt)) {
			return readyAt;
		}
		return now + (long) Math.ceil((readyAt - now) * factor);
	}

	/** A real, still-running cooldown (not a sentinel such as 1 or {@code Long.MAX_VALUE}). */
	public static boolean isLive(long now, long readyAt) {
		return readyAt > now && readyAt - now <= MAX_REAL_COOLDOWN_TICKS;
	}

	/**
	 * Keys in the shared maps that hold an active-effect end time or a pending delay rather than a cooldown
	 * ({@code "~cocoon"}, {@code "flight_burst_pending_at"}, {@code *_until}). Shortening those would cut a buff short.
	 */
	public static boolean isEffectKey(String key) {
		return key.startsWith("~") || key.contains("pending") || key.endsWith("_until") || key.endsWith("Until");
	}

	/** Scales every live entry of a {@code Map<String, Long>} in place; returns how many changed. */
	public static int scaleMap(Map<String, Long> map, long now, double factor) {
		int[] n = { 0 };
		map.replaceAll((k, v) -> {
			if (v == null || isEffectKey(k) || !isLive(now, v)) {
				return v;
			}
			long s = scaled(now, v, factor);
			if (s != v) {
				n[0]++;
			}
			return s;
		});
		return n[0];
	}

	/**
	 * A shortener for the common attachment shape: copy the state, scale its {@code abilityReadyAt}-style map, and write
	 * it back with {@code setAttached} (which syncs it to the client) -- only when something changed.
	 */
	public static <S> Shortener readyAtMap(AttachmentType<S> type, UnaryOperator<S> copy, Function<S, Map<String, Long>> map) {
		return (player, factor) -> {
			S state = player.getAttached(type);
			if (state == null) {
				return 0;
			}
			S c = copy.apply(state);
			int n = scaleMap(map.apply(c), player.level().getGameTime(), factor);
			if (n > 0) {
				player.setAttached(type, c);
			}
			return n;
		};
	}
}
