package com.projecthero.mod.quirk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.fx.QuirkCuePayload;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkSounds;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirk framework: <b>combo routes</b> (the {@code GreenLanternCombos} pattern, generalised). A route is "move A,
 * then move B within the window": B lands with a bonus. A kit registers its routes once from its init and reports its
 * moves as they fire:
 * <pre>
 *   public static final QuirkCombos.Route STUN_SHOT = QuirkCombos.route(Quirk.EXPLOSION, "stun_shot",
 *           ExplosionConfig.STUN, ExplosionConfig.AP_SHOT).window(20).bonus(1.5f).register();
 *
 *   // where a move fires:
 *   QuirkCombos.Route r = QuirkCombos.used(p, ExplosionConfig.AP_SHOT);   // null: no route finished
 *   float dmg = base * QuirkCombos.mult(r);                              // 1.5 when STUN_SHOT landed
 *
 *   // a route that needs both moves on the SAME creature ({@code sameTarget()}): report the hit instead
 *   QuirkCombos.Route r = QuirkCombos.hit(p, target, ErasureConfig.SPIN);
 * </pre>
 * Every move id is both a possible opener and a possible finisher. A landed route shows the combo popup on the user's HUD
 * (the route's name and its one-line effect: {@code projecthero.quirk.combo.<quirk>.<id>} / {@code .desc}), plays
 * {@link QuirkSounds#COMBO}, adds {@link PlusUltra#COMBO} to the gauge and bursts a ring at the user. While an opener's
 * window is open the finisher's key box flashes on the HUD (synced {@link #V_WINDOW_ROUTE} / {@link #V_WINDOW_UNTIL}).
 *
 * <p>State is server-side per player (the last time each move fired, and on what), dropped on revoke / logout / death
 * ({@code Quirks.forget}) and server stop.
 */
public final class QuirkCombos {
	/** Synced: 1 + the index of the route whose window is open (0: none) and when it closes. */
	public static final String V_WINDOW_ROUTE = "qf_cw_route";
	public static final String V_WINDOW_UNTIL = "qf_cw_until";
	/** The default window: about a second. */
	public static final int DEFAULT_WINDOW = 24;

	/** A registered route. {@link #index} is stable (registration order, the same on both sides). */
	public static final class Route {
		public final Quirk quirk;
		public final String id;
		public final String opener;
		public final String finisher;
		public final int window;
		public final float bonus;
		public final boolean sameTarget;
		public final int index;

		Route(Quirk quirk, String id, String opener, String finisher, int window, float bonus, boolean sameTarget, int index) {
			this.quirk = quirk;
			this.id = id;
			this.opener = opener;
			this.finisher = finisher;
			this.window = window;
			this.bonus = bonus;
			this.sameTarget = sameTarget;
			this.index = index;
		}

		public String nameKey() {
			return "projecthero.quirk.combo." + quirk.id + "." + id;
		}

		public String descKey() {
			return nameKey() + ".desc";
		}
	}

	public static final class Builder {
		private final Quirk quirk;
		private final String id;
		private final String opener;
		private final String finisher;
		private int window = DEFAULT_WINDOW;
		private float bonus = 1.25f;
		private boolean sameTarget;

		private Builder(Quirk quirk, String id, String opener, String finisher) {
			this.quirk = quirk;
			this.id = id;
			this.opener = opener;
			this.finisher = finisher;
		}

		public Builder window(int ticks) {
			this.window = ticks;
			return this;
		}

		/** The finisher's damage multiplier (read with {@link #mult}). */
		public Builder bonus(float mult) {
			this.bonus = mult;
			return this;
		}

		public Builder sameTarget() {
			this.sameTarget = true;
			return this;
		}

		public Route register() {
			synchronized (ROUTES) {
				Route r = new Route(quirk, id, opener, finisher, window, bonus, sameTarget, ROUTES.size());
				ROUTES.add(r);
				return r;
			}
		}
	}

	private record Mark(long tick, @Nullable UUID target) {
	}

	private static final List<Route> ROUTES = new ArrayList<>();
	/** player -> move id -> when it last fired / hit. */
	private static final Map<UUID, Map<String, Mark>> MARKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Route> LAST = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> COUNT = new ConcurrentHashMap<>();

	private QuirkCombos() {
	}

	public static Builder route(Quirk quirk, String id, String opener, String finisher) {
		return new Builder(quirk, id, opener, finisher);
	}

	public static List<Route> routes() {
		return Collections.unmodifiableList(ROUTES);
	}

	/** The routes of one Quirk (the guide lists them). */
	public static List<Route> routes(Quirk q) {
		List<Route> out = new ArrayList<>();
		for (Route r : ROUTES) {
			if (r.quirk == q) {
				out.add(r);
			}
		}
		return out;
	}

	public static @Nullable Route byIndex(int index) {
		return index >= 0 && index < ROUTES.size() ? ROUTES.get(index) : null;
	}

	static void clearSessionState() {
		MARKS.clear();
		LAST.clear();
		COUNT.clear();
	}

	static void forget(UUID id) {
		MARKS.remove(id);
	}

	/** The finisher's damage multiplier: the route's bonus, or 1 for none. */
	public static float mult(@Nullable Route r) {
		return r == null ? 1f : r.bonus;
	}

	/** A move fired (no particular target). Returns the route it just finished, or null. */
	public static @Nullable Route used(ServerPlayer p, String moveId) {
		return record(p, moveId, null);
	}

	/** A move hit {@code target}. Same-target routes only finish on the creature the opener hit. */
	public static @Nullable Route hit(ServerPlayer p, Entity target, String moveId) {
		return record(p, moveId, target == null ? null : target.getUUID());
	}

	private static @Nullable Route record(ServerPlayer p, String moveId, @Nullable UUID target) {
		Quirk q = Quirks.quirk(p);
		if (q == null) {
			return null;
		}
		long now = p.level().getGameTime();
		Map<String, Mark> marks = MARKS.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
		Route landed = null;
		for (Route r : ROUTES) {
			if (r.quirk != q || !r.finisher.equals(moveId)) {
				continue;
			}
			Mark m = marks.get(r.opener);
			if (m == null || now - m.tick > r.window || now < m.tick) {
				continue;
			}
			if (r.sameTarget && (target == null || !target.equals(m.target))) {
				continue;
			}
			landed = r;
			break;
		}
		if (landed != null) {
			marks.remove(landed.opener); // one opener, one finish
			land(p, landed);
		}
		marks.put(moveId, new Mark(now, target));
		// open the window flash for whatever this move opens
		Route opens = null;
		for (Route r : ROUTES) {
			if (r.quirk == q && r.opener.equals(moveId)) {
				opens = r;
				break;
			}
		}
		if (opens != null) {
			Route o = opens;
			Quirks.edit(p, n -> {
				n.vals.put(V_WINDOW_ROUTE, (long) o.index + 1L);
				n.vals.put(V_WINDOW_UNTIL, now + o.window);
			});
		} else if (landed != null) {
			Quirks.edit(p, n -> {
				n.vals.remove(V_WINDOW_ROUTE);
				n.vals.remove(V_WINDOW_UNTIL);
			});
		}
		return landed;
	}

	private static void land(ServerPlayer p, Route r) {
		LAST.put(p.getUUID(), r);
		COUNT.merge(p.getUUID(), 1, Integer::sum);
		if (ServerPlayNetworking.canSend(p, QuirkCuePayload.TYPE)) {
			ServerPlayNetworking.send(p, new QuirkCuePayload(QuirkCuePayload.COMBO, r.nameKey(), r.quirk.meterColour & 0xFFFFFF));
		}
		QuirkSounds.play(p.serverLevel(), p.position(), QuirkSounds.COMBO, 0.9f, 1.0f);
		Vec3 at = p.position().add(0, 0.1, 0);
		QuirkFx.ring(p.serverLevel(), at).colours(0xFFFFFF, r.quirk.meterColour & 0xFFFFFF).size(3.5f).thickness(0.35f).life(10).spawn();
		PlusUltra.add(p, PlusUltra.COMBO);
	}

	/** Client-safe: the route whose window is open for {@code p} right now, or null. */
	public static @Nullable Route openWindow(Player p) {
		var s = Quirks.peek(p);
		if (s == null || s.val(V_WINDOW_UNTIL) <= p.level().getGameTime()) {
			return null;
		}
		return byIndex((int) s.val(V_WINDOW_ROUTE) - 1);
	}

	/** Tests: the last route {@code p} landed (null: none) and how many in all. */
	public static @Nullable Route last(ServerPlayer p) {
		return LAST.get(p.getUUID());
	}

	public static int count(ServerPlayer p) {
		return COUNT.getOrDefault(p.getUUID(), 0);
	}
}
