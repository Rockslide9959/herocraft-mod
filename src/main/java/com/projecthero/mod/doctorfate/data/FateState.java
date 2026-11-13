package com.projecthero.mod.doctorfate.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3 Doctor Fate: the whole power for one player in one attachment ({@code projecthero:doctor_fate_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the suit layer, the Avatar of Order, the shield and the HUD read it). Only
 * {@code DoctorFate} changes it. Dominion ticks in place on the server and is re-sent every few ticks; everything else is saved
 * through a {@link #copy()} at once. All times are game times (0 = never / none).
 */
public final class FateState {
	public boolean hasPower;
	/** The Helmet of Fate is on (stats, flight, full-strength spells, Dominion rising). */
	public boolean helmetOn;
	/** 0..100: Nabu's hold on the wearer. Saved per player. */
	public float dominion;
	/** V: the 1 s helmet animation started here (0 = none); {@link #helmetGoingOn} says which way. */
	public long helmetChangeAt;
	public boolean helmetGoingOn;
	/** After a Possession the helmet can't go back on before this time. */
	public long lockoutUntil;
	/** Possessed by Nabu until this time (0 = not possessed). */
	public long possessedUntil;
	public boolean flying;
	/** G held: the Ankh Shield is up. */
	public boolean shieldUp;
	public long shieldSince;
	/** Shift+Z: the Avatar of Order stands behind him until this time; its last slam at {@link #avatarSlamAt}. */
	public long avatarUntil;
	public long avatarSlamAt;
	/** Move id -> game time it is ready again. */
	public Map<String, Long> abilityReadyAt;
	/** Bargains: refusals today (and which day that was), the pending offer, Order's command. */
	public Bargain bargain;
	/** v0.17.3 the Tower of Fate mindscape: the Blessing, the Silence, the Debt ({@code DoctorFateMindscape}). */
	public Mind mind = Mind.NONE;

	public FateState() {
		this(false, false, 0f, 0L, false, 0L, 0L, false, false, 0L, 0L, 0L, new HashMap<>(), Bargain.NONE, Mind.NONE);
	}

	public FateState(boolean hasPower, boolean helmetOn, float dominion, long helmetChangeAt, boolean helmetGoingOn, long lockoutUntil,
			long possessedUntil, boolean flying, boolean shieldUp, long shieldSince, long avatarUntil, long avatarSlamAt,
			Map<String, Long> abilityReadyAt, Bargain bargain, Mind mind) {
		this.hasPower = hasPower;
		this.helmetOn = helmetOn;
		this.dominion = dominion;
		this.helmetChangeAt = helmetChangeAt;
		this.helmetGoingOn = helmetGoingOn;
		this.lockoutUntil = lockoutUntil;
		this.possessedUntil = possessedUntil;
		this.flying = flying;
		this.shieldUp = shieldUp;
		this.shieldSince = shieldSince;
		this.avatarUntil = avatarUntil;
		this.avatarSlamAt = avatarSlamAt;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.bargain = bargain == null ? Bargain.NONE : bargain;
		this.mind = mind == null ? Mind.NONE : mind;
	}

	public FateState copy() {
		return new FateState(hasPower, helmetOn, dominion, helmetChangeAt, helmetGoingOn, lockoutUntil, possessedUntil, flying, shieldUp,
				shieldSince, avatarUntil, avatarSlamAt, abilityReadyAt, bargain, mind);
	}

	/**
	 * v0.17.3 what was agreed with Nabu in the Tower of Fate: the Blessing of Order and the Silence run until these times; the Debt
	 * ({@code debt} Dominion) comes due at {@code debtDueAt}; {@code visits} counts trips to the Tower.
	 */
	public record Mind(long blessingUntil, long silenceUntil, float debt, long debtDueAt, int visits) {
		public static final Mind NONE = new Mind(0L, 0L, 0f, 0L, 0);

		public static final Codec<Mind> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("blessing_until", 0L).forGetter(Mind::blessingUntil),
				Codec.LONG.optionalFieldOf("silence_until", 0L).forGetter(Mind::silenceUntil),
				Codec.FLOAT.optionalFieldOf("debt", 0f).forGetter(Mind::debt),
				Codec.LONG.optionalFieldOf("debt_due_at", 0L).forGetter(Mind::debtDueAt),
				Codec.INT.optionalFieldOf("visits", 0).forGetter(Mind::visits)
		).apply(i, Mind::new));

		public Mind withBlessing(long until) {
			return new Mind(until, silenceUntil, debt, debtDueAt, visits);
		}

		public Mind withSilence(long until) {
			return new Mind(blessingUntil, until, debt, debtDueAt, visits);
		}

		public Mind withDebt(float amount, long due) {
			return new Mind(blessingUntil, silenceUntil, amount, due, visits);
		}

		public Mind withVisits(int n) {
			return new Mind(blessingUntil, silenceUntil, debt, debtDueAt, n);
		}
	}

	/**
	 * Nabu's bargains. {@code pending} is the offer on screen ({@link #NONE_KIND} = none) until {@code pendingUntil};
	 * {@code refusals} counts today's refusals ({@code refusalDay} = the day they were made); the quest is Order's command
	 * (kills so far, how many it wants, until when; 0 = none), and {@code lastOffer} paces the offers.
	 */
	public record Bargain(int pending, long pendingUntil, int refusals, long refusalDay, int questKills, int questNeed, long questUntil,
			long lastOffer, long lastNightOffer) {
		public static final int NONE_KIND = 0;
		public static final int DEATHS_DOOR = 1;
		public static final int ORDERS_COMMAND = 2;
		public static final int FORBIDDEN = 3;
		public static final Bargain NONE = new Bargain(0, 0L, 0, 0L, 0, 0, 0L, 0L, -1L);

		public static final Codec<Bargain> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("pending", 0).forGetter(Bargain::pending),
				Codec.LONG.optionalFieldOf("pending_until", 0L).forGetter(Bargain::pendingUntil),
				Codec.INT.optionalFieldOf("refusals", 0).forGetter(Bargain::refusals),
				Codec.LONG.optionalFieldOf("refusal_day", 0L).forGetter(Bargain::refusalDay),
				Codec.INT.optionalFieldOf("quest_kills", 0).forGetter(Bargain::questKills),
				Codec.INT.optionalFieldOf("quest_need", 0).forGetter(Bargain::questNeed),
				Codec.LONG.optionalFieldOf("quest_until", 0L).forGetter(Bargain::questUntil),
				Codec.LONG.optionalFieldOf("last_offer", 0L).forGetter(Bargain::lastOffer),
				Codec.LONG.optionalFieldOf("last_night_offer", -1L).forGetter(Bargain::lastNightOffer)
		).apply(i, Bargain::new));

		public Bargain withPending(int kind, long until) {
			return new Bargain(kind, until, refusals, refusalDay, questKills, questNeed, questUntil, lastOffer, lastNightOffer);
		}

		public Bargain withRefusals(int n, long day) {
			return new Bargain(pending, pendingUntil, n, day, questKills, questNeed, questUntil, lastOffer, lastNightOffer);
		}

		public Bargain withQuest(int kills, int need, long until) {
			return new Bargain(pending, pendingUntil, refusals, refusalDay, kills, need, until, lastOffer, lastNightOffer);
		}

		public Bargain withLastOffer(long at) {
			return new Bargain(pending, pendingUntil, refusals, refusalDay, questKills, questNeed, questUntil, at, lastNightOffer);
		}

		public Bargain withNightOffer(long day) {
			return new Bargain(pending, pendingUntil, refusals, refusalDay, questKills, questNeed, questUntil, lastOffer, day);
		}

		public boolean questActive() {
			return questNeed > 0;
		}
	}

	public static final Codec<FateState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("helmet_on", false).forGetter(s -> s.helmetOn),
			Codec.FLOAT.optionalFieldOf("dominion", 0f).forGetter(s -> s.dominion),
			Codec.LONG.optionalFieldOf("helmet_change_at", 0L).forGetter(s -> s.helmetChangeAt),
			Codec.BOOL.optionalFieldOf("helmet_going_on", false).forGetter(s -> s.helmetGoingOn),
			Codec.LONG.optionalFieldOf("lockout_until", 0L).forGetter(s -> s.lockoutUntil),
			Codec.LONG.optionalFieldOf("possessed_until", 0L).forGetter(s -> s.possessedUntil),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.BOOL.optionalFieldOf("shield_up", false).forGetter(s -> s.shieldUp),
			Codec.LONG.optionalFieldOf("shield_since", 0L).forGetter(s -> s.shieldSince),
			Codec.LONG.optionalFieldOf("avatar_until", 0L).forGetter(s -> s.avatarUntil),
			Codec.LONG.optionalFieldOf("avatar_slam_at", 0L).forGetter(s -> s.avatarSlamAt),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Bargain.CODEC.optionalFieldOf("bargain", Bargain.NONE).forGetter(s -> s.bargain),
			Mind.CODEC.optionalFieldOf("mind", Mind.NONE).forGetter(s -> s.mind)
	).apply(i, FateState::new));
}
