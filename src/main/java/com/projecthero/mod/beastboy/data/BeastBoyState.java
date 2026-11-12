package com.projecthero.mod.beastboy.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3 Beast Boy: the whole power for one player in one attachment ({@code projecthero:beast_boy_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the body he's drawn as, the move it's animating, the HUD). Only
 * {@code BeastBoy} changes it. Stamina ticks in place on the server and is re-sent every few ticks; everything else is saved
 * through a {@link #copy()} at once. Times are game times (0 = none).
 */
public final class BeastBoyState {
	public boolean hasPower;
	/** {@code BeastForm} ordinal he is in now (0 = human). */
	public int form;
	/** What's left of the form's own health pool. */
	public float formHp;
	/** Morph Stamina, 0..100. */
	public float stamina;
	/** Morph Chain 0..5 and the animal of the last quick-morph hit (a BeastForm ordinal, -1 = none). */
	public int chain;
	public int chainForm = -1;
	/** Move id -> game time it's ready again; also "form_<id>" -> a form he was knocked out of is locked until then. */
	public Map<String, Long> readyAt;
	public Timers timers;
	public Anim anim;

	/** The timed things. */
	public record Timers(long beastUntil, long dazedUntil, long sensesUntil, long feverUntil, long chainAt, long leapAt) {
		public static final Timers NONE = new Timers(0L, 0L, 0L, 0L, 0L, 0L);
		public static final Codec<Timers> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("beast_until", 0L).forGetter(Timers::beastUntil),
				Codec.LONG.optionalFieldOf("dazed_until", 0L).forGetter(Timers::dazedUntil),
				Codec.LONG.optionalFieldOf("senses_until", 0L).forGetter(Timers::sensesUntil),
				Codec.LONG.optionalFieldOf("fever_until", 0L).forGetter(Timers::feverUntil),
				Codec.LONG.optionalFieldOf("chain_at", 0L).forGetter(Timers::chainAt),
				Codec.LONG.optionalFieldOf("leap_at", 0L).forGetter(Timers::leapAt)
		).apply(i, Timers::new));

		public Timers withBeast(long v) {
			return new Timers(v, dazedUntil, sensesUntil, feverUntil, chainAt, leapAt);
		}

		public Timers withDazed(long v) {
			return new Timers(beastUntil, v, sensesUntil, feverUntil, chainAt, leapAt);
		}

		public Timers withSenses(long v) {
			return new Timers(beastUntil, dazedUntil, v, feverUntil, chainAt, leapAt);
		}

		public Timers withFever(long v) {
			return new Timers(beastUntil, dazedUntil, sensesUntil, v, chainAt, leapAt);
		}

		public Timers withChainAt(long v) {
			return new Timers(beastUntil, dazedUntil, sensesUntil, feverUntil, v, leapAt);
		}

		public Timers withLeap(long v) {
			return new Timers(beastUntil, dazedUntil, sensesUntil, feverUntil, chainAt, v);
		}
	}

	/**
	 * What the body is animating: a human-form quick morph ({@code quickForm} flashing for {@code quickTicks} from
	 * {@code quickStart}) and the current form's move ({@code move} for {@code moveTicks} from {@code moveStart}).
	 */
	public record Anim(int quickForm, long quickStart, int quickTicks, String move, long moveStart, int moveTicks) {
		public static final Anim NONE = new Anim(0, 0L, 0, "", 0L, 0);
		public static final Codec<Anim> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("quick_form", 0).forGetter(Anim::quickForm),
				Codec.LONG.optionalFieldOf("quick_start", 0L).forGetter(Anim::quickStart),
				Codec.INT.optionalFieldOf("quick_ticks", 0).forGetter(Anim::quickTicks),
				Codec.STRING.optionalFieldOf("move", "").forGetter(Anim::move),
				Codec.LONG.optionalFieldOf("move_start", 0L).forGetter(Anim::moveStart),
				Codec.INT.optionalFieldOf("move_ticks", 0).forGetter(Anim::moveTicks)
		).apply(i, Anim::new));
	}

	public BeastBoyState() {
		this(false, 0, 0f, 0f, 0, -1, Map.of(), Timers.NONE, Anim.NONE);
	}

	public BeastBoyState(boolean hasPower, int form, float formHp, float stamina, int chain, int chainForm, Map<String, Long> readyAt,
			Timers timers, Anim anim) {
		this.hasPower = hasPower;
		this.form = form;
		this.formHp = formHp;
		this.stamina = stamina;
		this.chain = chain;
		this.chainForm = chainForm;
		this.readyAt = new HashMap<>(readyAt);
		this.timers = timers == null ? Timers.NONE : timers;
		this.anim = anim == null ? Anim.NONE : anim;
	}

	public BeastBoyState copy() {
		return new BeastBoyState(hasPower, form, formHp, stamina, chain, chainForm, readyAt, timers, anim);
	}

	public static final Codec<BeastBoyState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.INT.optionalFieldOf("form", 0).forGetter(s -> s.form),
			Codec.FLOAT.optionalFieldOf("form_hp", 0f).forGetter(s -> s.formHp),
			Codec.FLOAT.optionalFieldOf("stamina", 0f).forGetter(s -> s.stamina),
			Codec.INT.optionalFieldOf("chain", 0).forGetter(s -> s.chain),
			Codec.INT.optionalFieldOf("chain_form", -1).forGetter(s -> s.chainForm),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ready_at", Map.of()).forGetter(s -> new HashMap<>(s.readyAt)),
			Timers.CODEC.optionalFieldOf("timers", Timers.NONE).forGetter(s -> s.timers),
			Anim.CODEC.optionalFieldOf("anim", Anim.NONE).forGetter(s -> s.anim)
	).apply(i, BeastBoyState::new));
}
