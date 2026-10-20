package com.projecthero.mod.ironman.ability.era1;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7 (user): Iron Man ERA I -- THE GANTRY AGE (plan: {@code docs/IRONMAN_ERA1_PLAN.md}). The five Era I suits (Mk I
 * Cave-Forged, Mk II Golden Avenger, Mk III Classic, Mk IV Hydro, Mk V Stealth) each have twelve moves: R / G / X / Z / V /
 * C, tap + Shift ({@link IronManComicKit}; Shift+C is the emergency eject). Their move code lives in one class per suit
 * ({@link EraOneMk1Moves} .. {@link EraOneMk5Moves}), each registering a {@link Handler} per move id here, so
 * {@code IronManComicMoves} only has to ask {@link #has} / {@link #fire}.
 *
 * <p>Energy is back for Era I (the later suits keep the v0.16.1 no-energy shim for now): the kit table gives every Era I
 * move its cost; {@link #canPay} / {@link #pay} are the router's hooks (implemented with {@code IronManEnergy}).
 */
public final class IronManEraOne {
	/** The Era I suit ids, in progression order. */
	public static final Set<String> SUITS = Set.of("mark_1", "mark_1_v2", "mark_iii", "mark_4", "mark_v");

	/** One move edge: {@code pressed} = key down (a TAP move only ever gets the press). Returns true when it went off. */
	@FunctionalInterface
	public interface Handler {
		boolean fire(ServerPlayer player, IronManSuit suit, IronManComicKit.Move move, boolean pressed);
	}

	private static final Map<String, Handler> HANDLERS = new HashMap<>();
	private static boolean bootstrapped;

	private IronManEraOne() {
	}

	public static boolean isEraOne(String suitId) {
		return suitId != null && SUITS.contains(suitId);
	}

	public static boolean isEraOne(IronManSuit suit) {
		return suit != null && isEraOne(suit.id());
	}

	/** Called by each suit's moves class from its {@code init()}. */
	public static void register(String moveId, Handler handler) {
		if (HANDLERS.put(moveId, handler) != null) {
			throw new IllegalStateException("duplicate Era I move handler " + moveId);
		}
	}

	private static void bootstrap() {
		if (!bootstrapped) {
			bootstrapped = true;
			EraOneMk1Moves.init();
			EraOneMk2Moves.init();
			EraOneMk3Moves.init();
			EraOneMk4Moves.init();
			EraOneMk5Moves.init();
		}
	}

	/** Mod init. */
	public static void initialize() {
		bootstrap();
	}

	public static boolean has(String moveId) {
		bootstrap();
		return HANDLERS.containsKey(moveId);
	}

	public static boolean fire(ServerPlayer player, IronManSuit suit, IronManComicKit.Move move, boolean pressed) {
		bootstrap();
		Handler h = HANDLERS.get(move.id());
		return h != null && h.fire(player, suit, move, pressed);
	}

	// ------------------------------------------------------------------ hooks the CORE agent implements

	/** Router: may this Era I move go off (enough energy, not browned out)? Shows "LOW POWER" itself when not. */
	public static boolean canPay(ServerPlayer player, IronManSuit suit, IronManComicKit.Move move) {
		return true;
	}

	/** Router: the move went off -- pay its energy ({@link IronManComicKit#energyCost}). */
	public static void pay(ServerPlayer player, IronManSuit suit, IronManComicKit.Move move) {
	}

	/** Router: an edge of Shift+C on an Era I suit (hold 3 s = the emergency eject into a Suit Crate). */
	public static void ejectKey(ServerPlayer player, IronManSuit suit, boolean pressed) {
	}
}
