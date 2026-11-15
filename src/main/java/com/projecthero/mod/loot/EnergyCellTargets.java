package com.projecthero.mod.loot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.5: what an Energy Cell refills. Every power keeps its own meter, so each one registers a target here:
 * a predicate ("does this player have that meter right now?") and a refill ("add {@code fraction} of the max; return
 * true if anything changed"). The Energy Cell walks the targets in registration order and refills the first one that
 * can take it; if a player has a meter but every one is already full the cell is refused (and not used up), and if no
 * target applies at all the cell says the power has no meter.
 *
 * <p>Typical registration (most powers are a getter / setter / max triple):
 *
 * <pre>{@code
 * EnergyCellTargets.register("black_panther", BlackPanther::hasPower,
 *         EnergyCellTargets.meter(BlackPanther::kinetic, BlackPanther::setKinetic, p -> BlackPantherConfig.KINETIC_MAX));
 * }</pre>
 *
 * Built-in targets live in {@link SupplyTargetDefaults}.
 */
public final class EnergyCellTargets {
	/** Refills a meter by {@code fraction} (0..1) of its max. Returns true if the meter went up. */
	@FunctionalInterface
	public interface Refill {
		boolean refill(ServerPlayer player, float fraction);
	}

	/** One registered meter. */
	public record Target(String id, Predicate<ServerPlayer> applies, Refill refill) {
	}

	/** What a cell did. */
	public enum Result { REFILLED, ALREADY_FULL, NO_METER }

	/** Registration-time data only (lambdas, no worlds or entities), so nothing to clear on server stop. */
	private static final List<Target> TARGETS = new CopyOnWriteArrayList<>();

	private EnergyCellTargets() {
	}

	/** Registers a meter. A later registration with the same id replaces the earlier one (keeping its place). */
	public static void register(String id, Predicate<ServerPlayer> applies, Refill refill) {
		Target t = new Target(id, applies, refill);
		for (int i = 0; i < TARGETS.size(); i++) {
			if (TARGETS.get(i).id().equals(id)) {
				TARGETS.set(i, t);
				return;
			}
		}
		TARGETS.add(t);
	}

	/**
	 * A HeroPack mutation reserve: refills {@code ExperimentalPowers} resource {@code <activePower>/<resource>} (max
	 * {@code max}) while the active power has it. Built in: psi, ecell, flight, phase, sparkle.
	 */
	public static void registerExperimental(String resource, float max) {
		SupplyTargetDefaults.registerExperimental(resource, max);
	}

	public static List<Target> targets() {
		return List.copyOf(TARGETS);
	}

	/** The first registered target that applies to the player (for HUD / messages), or null. */
	public static Target first(ServerPlayer player) {
		for (Target t : TARGETS) {
			if (t.applies().test(player)) {
				return t;
			}
		}
		return null;
	}

	/** Refills the first applicable meter that isn't full. */
	public static Result apply(ServerPlayer player, float fraction) {
		boolean any = false;
		for (Target t : TARGETS) {
			if (!t.applies().test(player)) {
				continue;
			}
			any = true;
			if (t.refill().refill(player, fraction)) {
				return Result.REFILLED;
			}
		}
		return any ? Result.ALREADY_FULL : Result.NO_METER;
	}

	/**
	 * The common shape: read the value, add {@code fraction * max}, clamp, write. Refuses (returns false) when the meter is
	 * already within 0.5% of full.
	 */
	public static Refill meter(ToDoubleFunction<ServerPlayer> get, BiConsumer<ServerPlayer, Float> set, ToDoubleFunction<ServerPlayer> max) {
		return (player, fraction) -> {
			float m = (float) max.applyAsDouble(player);
			float cur = (float) get.applyAsDouble(player);
			if (m <= 0f || cur >= m * 0.995f) {
				return false;
			}
			set.accept(player, Math.min(m, cur + m * fraction));
			return true;
		};
	}

	/** Like {@link #meter} for powers that only expose an "add" method (it clamps itself). */
	public static Refill adder(ToDoubleFunction<ServerPlayer> get, BiConsumer<ServerPlayer, Float> add, ToDoubleFunction<ServerPlayer> max) {
		return (player, fraction) -> {
			float m = (float) max.applyAsDouble(player);
			float cur = (float) get.applyAsDouble(player);
			if (m <= 0f || cur >= m * 0.995f) {
				return false;
			}
			add.accept(player, Math.min(m - cur, m * fraction));
			return (float) get.applyAsDouble(player) > cur;
		};
	}
}
