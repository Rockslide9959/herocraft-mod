package com.projecthero.mod.ironman.ability.era1;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.ironman.IronManEnergy;
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
 * <p>Energy is back for Era I (and, v0.16.7 (user), Era II -- {@link #hasReactor}): the kit table gives every Era I / II
 * move its cost; {@link #canPay} / {@link #pay} are the router's hooks (implemented with {@code IronManEnergy}).
 *
 * <p><b>Energy rules for the move code</b> (v0.16.7 CORE):
 * <ul>
 *   <li>The router already checks {@link #canPay} on the press and calls {@link #pay} (the kit's
 *       {@link IronManComicKit#energyCost}) on the edge the handler returns true on -- a handler never pays its own
 *       up-front cost.</li>
 *   <li>A held / toggled move with a per-second cost ({@link IronManComicKit#energyPerSecond}: flamethrower, boot
 *       hover, tractor beam, targeting HUD, active camo, silent running...) calls
 *       {@link #drainTick drainTick(player, suit, moveId)} once per server tick while it runs and stops when it returns
 *       false (reactor dry / browned out / disabled / suit off).</li>
 *   <li>{@link #brownedOut} tells a running effect the suit just browned out (the brownout also runs
 *       {@code IronManSuitTicker.shutDownAllSystems}, which ends flight and the comic-state holds).</li>
 *   <li>Reused pre-v0.16.1 helpers ({@code IronManDash}, {@code IronManGroundPound}...) charge nothing
 *       ({@code IronManEnergy.spend} stays a no-op), so there is never a double charge.</li>
 *   <li>Hover moves that use {@code IronManFlight.setFlying} are landed by the brownout; only the Mk III pays the
 *       flight drain (2 / sprint 4 per second, {@code IronManEnergy.tickRecharge}) -- the hover pays through
 *       {@link #drainTick}.</li>
 * </ul>
 */
public final class IronManEraOne {
	/** The Era I suit ids, in progression order. */
	public static final Set<String> SUITS = Set.of("mark_1", "mark_1_v2", "mark_iii", "mark_4", "mark_v");

	/**
	 * v0.16.7 (user): the five Era II suits (Mk VI Suitcase, Mk VII Silver Centurion, Mk VIII Space, Mk IX Inferno,
	 * Hulkbuster I). They are NOT Gantry Age suits (they suit up / fold anywhere), but since v0.16.7 they run on the same
	 * reactor as Era I ("all the armours should have energy levels"): capacity / regen per suit, every move pays its kit
	 * cost, flight drains, brownout at 0. See {@link #hasReactor}.
	 */
	public static final Set<String> ERA_TWO = Set.of("mark_2", "mark_6", "mark_vii", "mark_9", "mark_8");

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

	/** v0.16.7 (user): is this an Era II suit (Mk VI .. Hulkbuster I)? */
	public static boolean isEraTwo(String suitId) {
		return suitId != null && ERA_TWO.contains(suitId);
	}

	/**
	 * v0.16.7 (user): does this suit run on a real reactor (Era I or Era II)? The energy rules ({@link #canPay},
	 * {@link #pay}, {@link #drainTick}, brownout, flight drain, regen) apply to exactly these; every other suit keeps the
	 * v0.16.1 always-full shim. The Gantry-only rules (suit up / off only in a Stark Gantry, the Shift+C eject) stay Era I's.
	 */
	public static boolean hasReactor(String suitId) {
		return isEraOne(suitId) || isEraTwo(suitId);
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
		EraOneEject.initialize(); // v0.16.7 (user): no fall damage while shot out of the back of an ejected suit
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

	// ------------------------------------------------------------------ energy + eject hooks (CORE)

	/**
	 * Router: may this Era I move go off? False -- with an action-bar "LOW POWER" -- while the suit is browned out
	 * ({@link IronManEnergy#brownedOut}), or when its reactor holds less than the move's cost
	 * ({@link IronManComicKit#energyCost}; a held / toggled move with no up-front cost needs some energy left). Always
	 * true for a later suit.
	 */
	public static boolean canPay(ServerPlayer player, IronManSuit suit, IronManComicKit.Move move) {
		if (suit == null || !hasReactor(suit.id()) || move == null) {
			return true;
		}
		float cost = IronManComicKit.energyCost(move.id());
		float e = IronManEnergy.energy(player, suit.id());
		boolean ok = !IronManEnergy.brownedOut(player, suit.id()) && (cost > 0f ? e >= cost - 1.0e-4f : e > 0f);
		if (!ok) {
			lowPower(player);
		}
		return ok;
	}

	/** Router: the move went off -- pay its energy ({@link IronManComicKit#energyCost}). */
	public static void pay(ServerPlayer player, IronManSuit suit, IronManComicKit.Move move) {
		if (suit == null || !hasReactor(suit.id()) || move == null) {
			return;
		}
		payAmount(player, suit, IronManComicKit.energyCost(move.id()));
	}

	/**
	 * v0.16.7 (user) Era II: pay a flat {@code cost} from a reactor suit -- for the few moves that go off away from the
	 * router's press / release edges (the Mk VI's shared repulsor slot, the Hulkbuster's C tap / hold). No-op without one.
	 */
	public static void payAmount(ServerPlayer player, IronManSuit suit, float cost) {
		if (suit != null && hasReactor(suit.id()) && cost > 0f) {
			IronManEnergy.setEnergy(player, suit.id(), IronManEnergy.energy(player, suit.id()) - cost);
		}
	}

	/** v0.16.7 (user) Era II: could a flat {@code cost} be paid now (not browned out, enough left)? LOW POWER if not. */
	public static boolean canPayAmount(ServerPlayer player, IronManSuit suit, float cost) {
		if (suit == null || !hasReactor(suit.id())) {
			return true;
		}
		float e = IronManEnergy.energy(player, suit.id());
		boolean ok = !IronManEnergy.brownedOut(player, suit.id()) && (cost > 0f ? e >= cost - 1.0e-4f : e > 0f);
		if (!ok) {
			lowPower(player);
		}
		return ok;
	}

	/**
	 * <b>For the move agents:</b> call this once per server tick while a held / toggled Era I move runs (flamethrower,
	 * boot hover, tractor beam, targeting HUD, active camo, silent running...). It drains that move's
	 * {@link IronManComicKit#energyPerSecond} / 20 from the worn suit's reactor and returns <b>false when the move must
	 * stop</b>: the reactor ran dry (the suit browns out at 0 -- {@link IronManEnergy#brownedOut}), it was already browned
	 * out, the suit is disabled (0 integrity) or no longer worn. A move with no per-second cost only gets those checks.
	 * Always true for a later suit.
	 */
	public static boolean drainTick(ServerPlayer player, IronManSuit suit, String moveId) {
		if (suit == null || !hasReactor(suit.id())) {
			return true;
		}
		if (!suit.id().equals(com.projecthero.mod.ironman.IronManArmor.wornSuitId(player))
				|| com.projecthero.mod.ironman.IronManIntegrity.disabled(player, suit.id())) {
			return false;
		}
		float perSecond = IronManComicKit.energyPerSecond(moveId);
		if (perSecond <= 0f) {
			return !IronManEnergy.brownedOut(player, suit.id());
		}
		boolean ok = IronManEnergy.drainRunning(player, suit.id(), perSecond / 20f);
		if (!ok) {
			lowPower(player);
		}
		return ok;
	}

	/** Is the worn Era I suit browned out (no moves, no flight / hover)? False for a later suit. */
	public static boolean brownedOut(ServerPlayer player, IronManSuit suit) {
		return suit != null && IronManEnergy.brownedOut(player, suit.id());
	}

	private static void lowPower(ServerPlayer player) {
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.ironman.era1.low_power")
				.withStyle(net.minecraft.ChatFormatting.RED), true);
	}

	/** Router: an edge of Shift+C on an Era I suit (hold 3 s = the emergency eject into a Suit Crate, {@link EraOneEject}). */
	public static void ejectKey(ServerPlayer player, IronManSuit suit, boolean pressed) {
		EraOneEject.key(player, suit, pressed);
	}
}
