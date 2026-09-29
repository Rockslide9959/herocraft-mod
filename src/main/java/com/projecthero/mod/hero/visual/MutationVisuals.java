package com.projecthero.mod.hero.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.22: the server half of mutation visuals -- move animations and body overlays that everyone around
 * the player sees. The client half (pose library, overlay renderers) lives in
 * {@code com.projecthero.mod.client.mutation}.
 *
 * <h2>Animations</h2>
 * {@link #play} starts a named pose (e.g. {@code "punch_right"}, {@code "p01.haymaker"}) on the shared
 * game-time clock; the client samples the keyframes from {@link MutationVisualState#animStart} so the swing
 * lines up with the hit on every viewer. One-shot poses end on their own. Looping poses (channels, stances)
 * keep going until {@link #stop} / {@link #stopIf}.
 *
 * <h2>Overlays</h2>
 * A power registers a <b>flag</b> with a predicate over the server player ({@link #registerFlag}); every few
 * ticks {@link #tick} re-evaluates all flags and syncs the set when it changed. The client registers an
 * overlay renderer for the same flag. Flags are namespaced by power ({@code p05.stone_skin}).
 * {@link #registerValue} does the same for a float a renderer scales by (heat, charge ...).
 */
public final class MutationVisuals {
	private static final Map<String, Predicate<ServerPlayer>> FLAGS = new LinkedHashMap<>();
	private static final Map<String, ToDoubleFunction<ServerPlayer>> VALUES = new LinkedHashMap<>();
	private static final int REFRESH_TICKS = 4;

	private MutationVisuals() {
	}

	// ---------------- registration ----------------

	/** Registers an overlay flag (idempotent by name -- a later registration replaces an earlier one). */
	public static void registerFlag(String flag, Predicate<ServerPlayer> active) {
		FLAGS.put(flag, active);
	}

	/** Registers a synced visual value (0 is treated as "absent" and not sent). */
	public static void registerValue(String name, ToDoubleFunction<ServerPlayer> value) {
		VALUES.put(name, value);
	}

	public static java.util.Set<String> registeredFlags() {
		return java.util.Collections.unmodifiableSet(FLAGS.keySet());
	}

	// ---------------- reading (both sides) ----------------

	public static MutationVisualState state(Player player) {
		MutationVisualState s = player.getAttachedOrElse(ModAttachments.MUTATION_VISUALS, null);
		return s == null ? MutationVisualState.EMPTY : s;
	}

	public static boolean hasFlag(Player player, String flag) {
		return state(player).has(flag);
	}

	public static String anim(Player player) {
		return state(player).anim();
	}

	// ---------------- animations (server) ----------------

	/** Starts (or restarts) {@code anim} now. */
	public static void play(ServerPlayer player, String anim) {
		MutationVisualState s = state(player);
		set(player, new MutationVisualState(anim, player.level().getGameTime(), s.flags(), s.values()));
	}

	/** Stops whatever animation is playing. */
	public static void stop(ServerPlayer player) {
		MutationVisualState s = state(player);
		if (!s.anim().isEmpty()) {
			set(player, new MutationVisualState("", 0L, s.flags(), s.values()));
		}
	}

	/** Stops the animation only if it is still {@code anim} (so ending a channel never cuts off a newer move). */
	public static void stopIf(ServerPlayer player, String anim) {
		if (anim.equals(state(player).anim())) {
			stop(player);
		}
	}

	/** Starts {@code anim} unless it is already the one playing (for channels re-asserted every tick). */
	public static void ensure(ServerPlayer player, String anim) {
		if (!anim.equals(state(player).anim())) {
			play(player, anim);
		}
	}

	// ---------------- per-tick (server) ----------------

	/** Re-evaluates every registered flag/value every few ticks and syncs only on change. */
	public static void tick(ServerPlayer player) {
		if ((player.tickCount + player.getId()) % REFRESH_TICKS != 0) {
			return;
		}
		List<String> flags = new ArrayList<>();
		for (var e : FLAGS.entrySet()) {
			boolean on;
			try {
				on = e.getValue().test(player);
			} catch (RuntimeException ex) {
				on = false; // a broken predicate must never take the whole tick down
			}
			if (on) {
				flags.add(e.getKey());
			}
		}
		java.util.Collections.sort(flags);
		Map<String, Float> values = new HashMap<>();
		for (var e : VALUES.entrySet()) {
			double v;
			try {
				v = e.getValue().applyAsDouble(player);
			} catch (RuntimeException ex) {
				v = 0;
			}
			if (Math.abs(v) > 1e-3) {
				values.put(e.getKey(), Math.round(v * 100.0) / 100.0f);
			}
		}
		MutationVisualState s = state(player);
		if (!flags.equals(s.flags()) || !values.equals(s.values())) {
			set(player, new MutationVisualState(s.anim(), s.animStart(), flags, values));
		}
	}

	/** Wipes everything (power removal, death). */
	public static void clear(ServerPlayer player) {
		if (player.getAttachedOrElse(ModAttachments.MUTATION_VISUALS, null) != null) {
			set(player, MutationVisualState.EMPTY);
		}
	}

	private static void set(ServerPlayer player, MutationVisualState s) {
		player.setAttached(ModAttachments.MUTATION_VISUALS, s);
	}
}
