package com.projecthero.mod.symbiote;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.network.SymbioteBondGamePayload;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;
import com.projecthero.mod.symbiote.item.SymbioteVialItem;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

/**
 * The bonding minigame. A wild Symbiote (or a filled vial) never bonds on touch -- the host has to win this first.
 *
 * <h2>v0.17.7: the struggle (user: "change the bonding UI and bonding minigame, make it harder to beat")</h2>
 * Three <b>phases</b> -- Contact, Resistance, Merge. In each a marker runs along a track and the player strikes
 * (Space / Enter / click) while it is inside a target zone. Every phase has its own <b>Bond meter</b>:
 * <ul>
 *   <li>a hit pushes it up ({@link #GAIN}); a hit in the bright core of the zone pushes it up further ({@link #PERFECT});</li>
 *   <li>a miss knocks it down ({@link #PENALTY}) and costs a <b>strike</b> -- {@link #MAX_STRIKES} strikes over the whole
 *       game and it rejects you;</li>
 *   <li>the Symbiote <b>pushes back</b>: after a short {@link #GRACE} the meter drains all the time, and harder the fuller it
 *       is ({@code DRAIN * (0.6 + 0.8 * meter)} per tick); it also squeezes the zone narrower as the meter rises
 *       ({@link #SQUEEZE}). Empty = it pulls free;</li>
 *   <li>fill the meter to clear the phase. Phase 1 sweeps steadily; phase 2 lurches (its speed swings, it nearly stops)
 *       and the zone drifts; phase 3 is faster, stalls and surges harder, drifts further and has the narrowest zone;</li>
 *   <li>after every strike the marker flinches for {@link #LOCKOUT} ticks and takes no input, so mashing only buys
 *       strikes.</li>
 * </ul>
 * A skilled run takes roughly 15-25 seconds (tuned with a seeded bot: 35 ms timing error wins ~99% in ~19 s, 50 ms ~70%
 * in ~21 s, 75 ms ~11%).
 *
 * <h2>Server authority</h2>
 * The whole game is a pure function of a server-chosen seed and the press times ({@link #simulate}); the client screen
 * draws exactly that same simulation, and the server replays the reported press times ({@link #handleResult}) and only
 * bonds on a replayed win that had time to happen. Backing out costs nothing only before the first strike and within
 * the first {@link #FREE_BACK_OUT} ticks; after that, letting go is a failure.
 *
 * <p>Passing calls {@link SymbioteBonding#bondNow} and the usual 35-second bonding debuff
 * ({@link SymbioteVitalsManager#BONDING_TICKS}) follows. Failing consumes nothing: the Symbiote (or vial) is kept, the
 * host is nauseous for a few seconds and cannot retry for {@link #RETRY_TICKS}.
 */
public final class SymbioteBondGame {
	public static final int PHASES = 3;
	/** Old name for {@link #PHASES} (pre-0.17.7 the game was three single-press rounds). */
	public static final int ROUNDS = PHASES;
	/**
	 * v0.13.15: presses are reported in hundredths of a tick, so a press lands where the smoothly drawn marker actually was
	 * (the screen interpolates between ticks) rather than snapping to the last whole tick.
	 */
	public static final int SUBTICKS = 100;
	/** Ticks between clearing a phase and the next one starting. */
	public static final int PHASE_GAP = 24;
	/** Ticks at the start of each phase before the meter starts draining. */
	public static final int GRACE = 20;
	/** After any strike the marker flinches and ignores input for this many ticks. */
	public static final int LOCKOUT = 6;
	public static final int MAX_STRIKES = 3;
	/** A phase still going after this long is lost (the drain normally ends it far sooner). */
	public static final int PHASE_TIMEOUT = 20 * 45;
	/** Escape before the first strike and within this many ticks of opening = a free back-out. */
	public static final int FREE_BACK_OUT = 40;
	/** Most presses one game can report (the payload's list limit is one more, for the give-up marker). */
	public static final int MAX_PRESSES = 64;
	/** The client appends this to its press list when it lost or let go (presses are never negative). */
	public static final int GAVE_UP = -1;

	/** Marker: ticks per there-and-back sweep at its base speed. */
	public static final double[] PERIOD = {44.0, 46.0, 40.0};
	/** Marker speed wobbles (cycles of phase added) and their periods: phase 2 lurches, phase 3 stalls and surges. */
	private static final double[] WOBBLE_A = {0.0, 0.08, 0.09};
	private static final double[] WOBBLE_A_PERIOD = {1.0, 47.0, 43.0};
	private static final double[] WOBBLE_B = {0.0, 0.03, 0.04};
	private static final double[] WOBBLE_B_PERIOD = {1.0, 21.0, 23.0};
	/** Half-width of the target zone (fraction of the track) at an empty meter. */
	public static final double[] HALF_ZONE = {0.14, 0.12, 0.11};
	/** How much narrower the zone gets as the meter fills: half * (1 - SQUEEZE * meter). */
	public static final double[] SQUEEZE = {0.0, 0.15, 0.25};
	/** The perfect core: this share of the zone's half-width around its centre. */
	public static final double CORE = 0.4;
	/** Zone drift amplitude (fraction of the track) and period (ticks). */
	private static final double[] DRIFT = {0.0, 0.12, 0.18};
	private static final double[] DRIFT_PERIOD = {1.0, 90.0, 70.0};
	/** Meter at the start of each phase, base drain per tick, gain per hit / perfect hit, loss per miss. */
	public static final double[] START = {0.40, 0.35, 0.30};
	public static final double[] DRAIN = {0.0030, 0.0038, 0.0045};
	public static final double[] GAIN = {0.20, 0.16, 0.15};
	public static final double[] PERFECT = {0.28, 0.23, 0.21};
	public static final double[] PENALTY = {0.15, 0.18, 0.20};
	/** v0.13.15: a hair of forgiveness past the drawn edge of the zone (a press on the edge line counts). */
	public static final double EDGE_GRACE = 0.008;

	public static final int PLAYING = 0;
	public static final int WON = 1;
	public static final int FAILED = -1;

	public static final int REASON_NONE = 0;
	public static final int REASON_DRAINED = 1;
	public static final int REASON_STRIKES = 2;
	public static final int REASON_TIMEOUT = 3;
	public static final int REASON_INVALID = 4;

	public static final int RESULT_NONE = 0;
	public static final int RESULT_HIT = 1;
	public static final int RESULT_PERFECT = 2;
	public static final int RESULT_MISS = -1;

	private static final double TAU = Math.PI * 2.0;
	/** Drain shape: dm/dt = -DRAIN * (0.6 + 0.8 m) = -K (m + C) with K = 0.8 DRAIN, C = 0.75. */
	private static final double DRAIN_C = 0.75;

	private static final int SESSION_TICKS = 20 * 90;
	private static final int RETRY_TICKS = 20 * 8;
	/** Real-time slack (ticks) between the client's claimed duration and the server's clock. */
	private static final int SLACK_TICKS = 40;

	private record Session(int seed, long startedAt, UUID entity) {
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> RETRY_AT = new ConcurrentHashMap<>();

	private SymbioteBondGame() {
	}

	public static void clearSessionState() {
		SESSIONS.clear();
		RETRY_AT.clear();
	}

	// ---------------- the deterministic game (shared with the client screen) ----------------

	/** A seeded 0..1 value for {@code phase}, slot {@code k}. */
	static double rnd(int seed, int phase, int k) {
		int h = seed * 0x9E3779B1 + phase * 0x85EBCA77 + k * 0xC2B2AE3D;
		h ^= h >>> 16;
		h *= 0x7FEB352D;
		h ^= h >>> 15;
		h *= 0x846CA68B;
		h ^= h >>> 16;
		return (h >>> 8) / (double) (1 << 24);
	}

	/** Marker position 0..1, {@code t} ticks (fractional) into phase {@code phase}. Smooth in t (its speed swings but it never reverses mid-sweep). */
	public static double marker(int seed, int phase, double t) {
		t = Math.max(0.0, t);
		double th = t / PERIOD[phase] + rnd(seed, phase, 0)
				+ WOBBLE_A[phase] * StrictMath.sin(TAU * t / WOBBLE_A_PERIOD[phase] + TAU * rnd(seed, phase, 1))
				+ WOBBLE_B[phase] * StrictMath.sin(TAU * t / WOBBLE_B_PERIOD[phase] + TAU * rnd(seed, phase, 2));
		double x = th - Math.floor(th);
		return x < 0.5 ? x * 2.0 : (1.0 - x) * 2.0;
	}

	/** Centre of the target zone {@code t} ticks into the phase (it drifts in phases 2 and 3; always fully on the track). */
	public static double zoneCenter(int seed, int phase, double t) {
		double edge = HALF_ZONE[phase] + 0.04;
		double base = 0.3 + 0.4 * rnd(seed, phase, 3);
		double c = base + DRIFT[phase] * StrictMath.sin(TAU * Math.max(0.0, t) / DRIFT_PERIOD[phase] + TAU * rnd(seed, phase, 4));
		return Math.max(edge, Math.min(1.0 - edge, c));
	}

	/** The zone's half-width at {@code meter} (the Symbiote squeezes it as the bond nears). */
	public static double halfZone(int phase, double meter) {
		return HALF_ZONE[phase] * (1.0 - SQUEEZE[phase] * Math.max(0.0, Math.min(1.0, meter)));
	}

	/** What a strike at {@code t} ticks into the phase scores, at {@code meter}. */
	public static int judge(int seed, int phase, double t, double meter) {
		double d = Math.abs(marker(seed, phase, t) - zoneCenter(seed, phase, t));
		double half = halfZone(phase, meter);
		if (d <= half * CORE) {
			return RESULT_PERFECT;
		}
		return d <= half + EDGE_GRACE ? RESULT_HIT : RESULT_MISS;
	}

	/**
	 * The game's state at time {@code upTo} (ticks since it opened) after the presses so far (in 1/{@link #SUBTICKS} of a
	 * tick, ascending). Pure and deterministic: the screen draws it every frame and the server replays it.
	 */
	public record State(int outcome, int reason, int phase, double meter, int strikes, double phaseStart, double endAt,
			int used, int lastResult, double lastAt, double lastPos, int lastPhase, double lastCentre, double lastHalf) {
		public boolean playing() {
			return outcome == PLAYING;
		}

		/** Whether a strike at {@code now} would be taken (not over, not in the gap between phases, not flinching). */
		public boolean accepts(double now) {
			return outcome == PLAYING && now >= phaseStart && now - lastAt >= LOCKOUT;
		}
	}

	public static State simulate(int seed, List<Integer> presses, double upTo) {
		int phase = 0;
		double phaseStart = 0.0;
		double meter = START[0];
		double ref = 0.0;
		int strikes = 0;
		int used = 0;
		int lastResult = RESULT_NONE;
		double lastAt = -1.0e9;
		double lastPos = -1.0;
		int lastPhase = -1;
		double lastCentre = 0.0;
		double lastHalf = 0.0;
		for (Integer raw : presses) {
			double tp = raw / (double) SUBTICKS;
			if (raw >= 0 && tp > upTo) {
				break;
			}
			double emptyAt = drainEmptyAt(phase, meter, ref, phaseStart, raw < 0 ? upTo : tp);
			if (emptyAt >= 0.0) {
				return new State(FAILED, REASON_DRAINED, phase, 0.0, strikes, phaseStart, emptyAt, used, lastResult, lastAt,
						lastPos, lastPhase, lastCentre, lastHalf);
			}
			if (raw >= 0 && tp - phaseStart > PHASE_TIMEOUT) {
				return new State(FAILED, REASON_TIMEOUT, phase, meter, strikes, phaseStart, phaseStart + PHASE_TIMEOUT, used,
						lastResult, lastAt, lastPos, lastPhase, lastCentre, lastHalf);
			}
			if (raw < 0 || tp < phaseStart || tp - lastAt < LOCKOUT - 1.0e-9) {
				return new State(FAILED, REASON_INVALID, phase, meter, strikes, phaseStart, raw < 0 ? upTo : tp, used,
						lastResult, lastAt, lastPos, lastPhase, lastCentre, lastHalf);
			}
			meter = drained(phase, meter, ref, phaseStart, tp);
			double t = tp - phaseStart;
			lastHalf = halfZone(phase, meter);
			lastCentre = zoneCenter(seed, phase, t);
			lastPos = marker(seed, phase, t);
			lastResult = judge(seed, phase, t, meter);
			lastAt = tp;
			lastPhase = phase;
			ref = tp;
			used++;
			if (lastResult == RESULT_MISS) {
				meter -= PENALTY[phase];
				strikes++;
				if (strikes >= MAX_STRIKES || meter <= 0.0) {
					return new State(FAILED, strikes >= MAX_STRIKES ? REASON_STRIKES : REASON_DRAINED, phase, Math.max(0.0, meter),
							strikes, phaseStart, tp, used, lastResult, lastAt, lastPos, lastPhase, lastCentre, lastHalf);
				}
				continue;
			}
			meter += lastResult == RESULT_PERFECT ? PERFECT[phase] : GAIN[phase];
			if (meter >= 1.0) {
				phase++;
				if (phase >= PHASES) {
					return new State(WON, REASON_NONE, PHASES, 1.0, strikes, phaseStart, tp, used, lastResult, lastAt, lastPos,
							lastPhase, lastCentre, lastHalf);
				}
				phaseStart = tp + PHASE_GAP;
				meter = START[phase];
				ref = phaseStart;
			}
		}
		double emptyAt = drainEmptyAt(phase, meter, ref, phaseStart, upTo);
		if (emptyAt >= 0.0) {
			return new State(FAILED, REASON_DRAINED, phase, 0.0, strikes, phaseStart, emptyAt, used, lastResult, lastAt, lastPos,
					lastPhase, lastCentre, lastHalf);
		}
		if (upTo - phaseStart > PHASE_TIMEOUT) {
			return new State(FAILED, REASON_TIMEOUT, phase, meter, strikes, phaseStart, phaseStart + PHASE_TIMEOUT, used,
					lastResult, lastAt, lastPos, lastPhase, lastCentre, lastHalf);
		}
		return new State(PLAYING, REASON_NONE, phase, drained(phase, meter, ref, phaseStart, upTo), strikes, phaseStart, -1.0,
				used, lastResult, lastAt, lastPos, lastPhase, lastCentre, lastHalf);
	}

	/** The meter drained from {@code m0} at {@code ref} to time {@code to} (no drain during the phase's grace). */
	private static double drained(int phase, double m0, double ref, double phaseStart, double to) {
		double from = Math.max(ref, phaseStart + GRACE);
		if (to <= from) {
			return m0;
		}
		double k = 0.8 * DRAIN[phase];
		return Math.max(0.0, (m0 + DRAIN_C) * StrictMath.exp(-k * (to - from)) - DRAIN_C);
	}

	/** When the meter would hit zero on the way to {@code to}, or -1 if it does not. */
	private static double drainEmptyAt(int phase, double m0, double ref, double phaseStart, double to) {
		double from = Math.max(ref, phaseStart + GRACE);
		if (to <= from) {
			return -1.0;
		}
		double k = 0.8 * DRAIN[phase];
		double dt0 = StrictMath.log((m0 + DRAIN_C) / DRAIN_C) / k;
		return to - from >= dt0 ? from + dt0 : -1.0;
	}

	// ---------------- server flow ----------------

	/** Start the minigame for {@code player}; {@code entity} is the free Symbiote being touched, or null for a vial. */
	public static void begin(ServerPlayer player, SymbioteEntity entity) {
		long now = player.level().getGameTime();
		Long retry = RETRY_AT.get(player.getUUID());
		if (retry != null && now < retry) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.retry",
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil((retry - now) / 20.0)))
					.withStyle(ChatFormatting.DARK_GRAY), true);
			return;
		}
		if (entity != null && !entity.claimBond(player, SESSION_TICKS + 40)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.entity_busy"), true);
			return;
		}
		int seed = player.getRandom().nextInt();
		openSession(player, entity, seed);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.begin")
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
		ServerPlayNetworking.send(player, new SymbioteBondGamePayload(seed));
	}

	/**
	 * Record a running game for {@code player} with {@code seed}, starting now -- everything {@link #begin} does on the
	 * server except the checks and opening the client screen (the gametests drive the server side through this).
	 */
	public static void openSession(ServerPlayer player, SymbioteEntity entity, int seed) {
		SESSIONS.put(player.getUUID(), new Session(seed, player.level().getGameTime(), entity == null ? null : entity.getUUID()));
	}

	public static boolean hasSession(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	/** Whether {@code player} is locked out of a retry right now. */
	public static boolean onRetryCooldown(ServerPlayer player) {
		Long retry = RETRY_AT.get(player.getUUID());
		return retry != null && player.level().getGameTime() < retry;
	}

	/**
	 * The client's press times (empty = backed out; a trailing {@link #GAVE_UP} = it lost or let go). Every claim is
	 * replayed through {@link #simulate} here -- a claimed win is never trusted.
	 */
	public static void handleResult(ServerPlayer player, List<Integer> reported) {
		Session s = SESSIONS.remove(player.getUUID());
		if (s == null) {
			return; // unprompted: a modified client gains nothing
		}
		long now = player.level().getGameTime();
		long elapsed = now - s.startedAt;
		if (reported.isEmpty()) {
			// a free back-out only right at the start; walking away from a struggle in progress is a failure
			if (elapsed <= FREE_BACK_OUT + SLACK_TICKS) {
				release(player, s);
			} else {
				fail(player, s, now);
			}
			return;
		}
		if (!verifyWin(s.seed, reported, elapsed)) {
			fail(player, s, now);
			return;
		}
		// v0.16.1: projectheroUniquePowers -- someone else bonded during the game: nothing is used up
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, "symbiote")) {
			release(player, s);
			return;
		}
		Entity target = s.entity == null ? null : ((ServerLevel) player.level()).getEntity(s.entity);
		if (s.entity != null) {
			if (!(target instanceof SymbioteEntity blob) || blob.isRemoved() || blob.distanceTo(player) > 12.0f
					|| Symbiote.hasSymbiote(player)) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.lost")
						.withStyle(ChatFormatting.GRAY), true);
				return;
			}
			SymbioteBonding.bondNow(player, blob);
			return;
		}
		if (!SymbioteVialItem.consumeFilledVial(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.lost")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		SymbioteBonding.bondFromVialNow(player);
	}

	/**
	 * Whether {@code reported} is a genuine win of the game seeded {@code seed}, given {@code serverElapsed} ticks since it
	 * opened: every press valid, the win on the very last press, no give-up marker, and the run short enough and long
	 * enough to have really happened on the server's clock.
	 */
	public static boolean verifyWin(int seed, List<Integer> reported, long serverElapsed) {
		if (reported.isEmpty() || reported.size() > MAX_PRESSES) {
			return false;
		}
		List<Integer> presses = new ArrayList<>(reported.size());
		int prev = Integer.MIN_VALUE;
		for (Integer p : reported) {
			if (p == null || p < 0 || p < prev) {
				return false; // a give-up marker, junk, or time running backwards
			}
			presses.add(p);
			prev = p;
		}
		double last = presses.get(presses.size() - 1) / (double) SUBTICKS;
		State st = simulate(seed, presses, last);
		return st.outcome == WON && st.used == presses.size()
				&& serverElapsed <= SESSION_TICKS + SLACK_TICKS
				&& serverElapsed >= last - SLACK_TICKS;
	}

	private static void release(ServerPlayer player, Session s) {
		if (s.entity != null && ((ServerLevel) player.level()).getEntity(s.entity) instanceof SymbioteEntity blob) {
			blob.releaseBond();
		}
	}

	private static void fail(ServerPlayer player, Session s, long now) {
		release(player, s);
		RETRY_AT.put(player.getUUID(), now + RETRY_TICKS);
		player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.failed")
				.withStyle(ChatFormatting.DARK_PURPLE), false);
		if (player.level() instanceof ServerLevel level) {
			SymbioteSounds.organic(level, player.getX(), player.getY(), player.getZ(), 0.8f, 0.5f);
		}
	}
}
