package com.projecthero.mod.quirk.meter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.1 Quirk framework: how one Quirk's meter is banded and paced. Each kit registers one from its own init
 * ({@code <Kit>Meter.register()}); see {@code docs/QUIRK_FRAMEWORK_0171.md}.
 *
 * <pre>
 *   QuirkMeterSpec.builder(Quirk.HELLFLAME)
 *       .bands(40f, 80f)                       // LOW 0-39, SWEET 40-79, RED 80-100
 *       .names("kindle", "blaze", "inferno", "overheat")
 *       .damage(0.85f, 1.0f, 1.3f, 1.5f)       // per band (LOW, SWEET, RED, BEYOND)
 *       .pushPast(100, 1.0f, 160)              // 5 s past 100 at 1 HP/s, then a crash of 8 s
 *       .pacing(0.12f, 1.0f)                   // build x0.12, recovery x1.0 (the user's "build gently" rule)
 *       .safe(40f)                             // where "recovered" is (the pacing test: red -> safe in 10-15 s)
 *       .heavyUse(h -> h.sustained(5f).move(10f, 30).move(12f, 50))
 *       .register();
 * </pre>
 */
public final class QuirkMeterSpec {
	/** Which way using the Quirk moves the meter, and so where its danger is. */
	public enum Direction {
		/** Use fills it (Heat, Dry Eyes, Nausea, Shadow): the RED band (top) is the danger. */
		BUILDS,
		/** Use empties it (Focus, Matter): the LOW band (bottom) is the danger, the top band is a bonus. */
		SPENDS
	}

	/** Reads / changes the value the pacing applies to (default: the Quirk meter; Explosion: Arm Strain). */
	public interface Channel {
		float read(ServerPlayer p);

		/** Adds a RAW change (it is paced by the channel's own {@link QuirkMeters#pace} call). */
		void add(ServerPlayer p, float rawDelta);

		/** Sets the value (tests). */
		void set(ServerPlayer p, float value);
	}

	/** The default channel: the synced Quirk meter, paced. */
	public static final Channel METER = new Channel() {
		@Override
		public float read(ServerPlayer p) {
			return Quirks.meter(p);
		}

		@Override
		public void add(ServerPlayer p, float rawDelta) {
			Quirks.addMeterPaced(p, rawDelta);
		}

		@Override
		public void set(ServerPlayer p, float value) {
			Quirks.setMeter(p, value);
		}
	};

	/**
	 * A scripted "steady heavy use" of the kit (raw numbers straight from its config): what it builds a second while its
	 * sustained move runs, plus a rotation of attacks (raw meter cost, cooldown ticks) pressed one every
	 * {@link #pressEvery} ticks, the next ready one in order. {@code regenPerSecond}: what the kit gives back DURING use (only
	 * {@link Direction#SPENDS} meters refill while in use). The pacing gametest drives this through the real channel.
	 */
	public static final class HeavyUse {
		public float sustainedPerSecond;
		/** The sustained rate ramps up to x{@code rampMax} over {@code rampTicks} (Zero Gravity's float-time bonus). */
		public float rampMax = 1f;
		public int rampTicks = 1;
		public float regenPerSecond;
		public int pressEvery = 40;
		public final List<float[]> moves = new ArrayList<>();
		/** The kit's own out-of-action recovery (raw, a second) and the delay before it starts. */
		public float recoverPerSecond;
		public int recoverDelay;

		public HeavyUse sustained(float perSecond) {
			this.sustainedPerSecond = perSecond;
			return this;
		}

		public HeavyUse ramp(float max, int ticks) {
			this.rampMax = max;
			this.rampTicks = Math.max(1, ticks);
			return this;
		}

		/** The sustained build a second at {@code tick} of the run (raw). */
		public float sustainedAt(int tick) {
			return sustainedPerSecond * (1f + (rampMax - 1f) * Math.min(1f, tick / (float) rampTicks));
		}

		public HeavyUse recovery(float perSecond, int delayTicks) {
			this.recoverPerSecond = perSecond;
			this.recoverDelay = delayTicks;
			return this;
		}

		public HeavyUse regen(float perSecond) {
			this.regenPerSecond = perSecond;
			return this;
		}

		public HeavyUse pressEvery(int ticks) {
			this.pressEvery = Math.max(1, ticks);
			return this;
		}

		/**
		 * One attack in the rotation: how far it moves the channel TOWARD the danger (a magnitude: Heat gained, Focus spent)
		 * and its cooldown (ticks). {@link #sustained} is a magnitude toward the danger too; {@link #regen} away from it.
		 */
		public HeavyUse move(float raw, int cooldown) {
			moves.add(new float[] { raw, cooldown });
			return this;
		}
	}

	public final Quirk quirk;
	public final Direction direction;
	/** LOW is below this, SWEET from here... */
	public final float sweetFrom;
	/** ...RED from here (to 100). */
	public final float redFrom;
	/** Band names for the HUD / guide: {@code projecthero.quirk.band.<quirk>.<name>}. */
	public final String[] names;
	/** The shared per-band damage multipliers (LOW, SWEET, RED, BEYOND). */
	public final float[] damage;
	public final boolean pushPast;
	public final int pushTicks;
	public final float pushHpPerSecond;
	public final int crashTicks;
	@Nullable
	public final Consumer<ServerPlayer> onCrash;
	/** Pacing: the meter's natural build from use is multiplied by this (and by {@link #suitBuildMult} in the full suit)... */
	public final float buildMult;
	/** ...and its natural recovery by this. */
	public final float recoverMult;
	public final float suitBuildMult;
	/** The pacing test's "recovered" value (BUILDS: at or below; SPENDS: at or above). */
	public final float safe;
	/** Where the pacing danger starts on its channel (BUILDS: at or above; SPENDS: at or below). */
	public final float danger;
	public final Channel channel;
	/** The channel's own "build" direction (BUILDS = up). Explosion's strain channel builds up while its Sweat is fuel. */
	public final Direction channelDirection;
	private final Consumer<HeavyUse> heavyFill;
	/** The meter's label on the HUD differs from the channel (Explosion: the Sweat bar, the Strain is the danger). */
	public final boolean channelIsMeter;

	private QuirkMeterSpec(Builder b) {
		this.quirk = b.quirk;
		this.direction = b.direction;
		this.sweetFrom = b.sweetFrom;
		this.redFrom = b.redFrom;
		this.names = b.names;
		this.damage = b.damage;
		this.pushPast = b.pushPast;
		this.pushTicks = b.pushTicks;
		this.pushHpPerSecond = b.pushHp;
		this.crashTicks = b.crashTicks;
		this.onCrash = b.onCrash;
		this.buildMult = b.buildMult;
		this.recoverMult = b.recoverMult;
		this.suitBuildMult = b.suitBuildMult;
		this.channel = b.channel;
		this.channelDirection = b.channelDirection == null ? b.direction : b.channelDirection;
		this.channelIsMeter = b.channel == METER;
		this.danger = b.danger != null ? b.danger : (this.channelDirection == Direction.BUILDS ? b.redFrom : b.sweetFrom);
		this.safe = b.safe != null ? b.safe : (this.channelDirection == Direction.BUILDS ? b.sweetFrom : b.redFrom);
		this.heavyFill = b.heavyFill;
	}

	/** A fresh heavy-use profile, built now from the kit's CURRENT config (files are read after registration). */
	public HeavyUse heavyUse() {
		HeavyUse h = new HeavyUse();
		heavyFill.accept(h);
		return h;
	}

	public static Builder builder(Quirk quirk) {
		return new Builder(quirk);
	}

	/** The band of a value (BEYOND is never returned here: see {@link QuirkMeters#band}). */
	public QuirkBand bandOf(float value) {
		if (value >= redFrom) {
			return QuirkBand.RED;
		}
		return value >= sweetFrom ? QuirkBand.SWEET : QuirkBand.LOW;
	}

	public float damageFor(QuirkBand band) {
		return damage[Math.min(damage.length - 1, band.ordinal())];
	}

	/** Is {@code v} on the channel in the danger zone? */
	public boolean dangerous(float v) {
		return channelDirection == Direction.BUILDS ? v >= danger : v <= danger;
	}

	public boolean recovered(float v) {
		return channelDirection == Direction.BUILDS ? v <= safe : v >= safe;
	}

	public static final class Builder {
		private final Quirk quirk;
		private Direction direction = Direction.BUILDS;
		private Direction channelDirection;
		private float sweetFrom = 30f;
		private float redFrom = 80f;
		private String[] names = { "low", "sweet", "red", "beyond" };
		private float[] damage = { 1f, 1f, 1f, 1f };
		private boolean pushPast;
		private int pushTicks;
		private float pushHp;
		private int crashTicks;
		private Consumer<ServerPlayer> onCrash;
		private float buildMult = 1f;
		private float recoverMult = 1f;
		private float suitBuildMult = 1f;
		private Channel channel = METER;
		private Float danger;
		private Float safe;
		private Consumer<HeavyUse> heavyFill = h -> {
		};

		private Builder(Quirk quirk) {
			this.quirk = quirk;
		}

		public Builder direction(Direction d) {
			this.direction = d;
			return this;
		}

		/** LOW below {@code sweetFrom}, SWEET up to {@code redFrom}, RED from there. */
		public Builder bands(float sweetFrom, float redFrom) {
			this.sweetFrom = sweetFrom;
			this.redFrom = redFrom;
			return this;
		}

		/** Lang suffixes of the four bands ({@code projecthero.quirk.band.<quirk>.<name>}). */
		public Builder names(String low, String sweet, String red, String beyond) {
			this.names = new String[] { low, sweet, red, beyond };
			return this;
		}

		public Builder damage(float low, float sweet, float red, float beyond) {
			this.damage = new float[] { low, sweet, red, beyond };
			return this;
		}

		/** The meter can be pushed past 100 for {@code ticks}, costing {@code hpPerSecond}, then crashes for {@code crashTicks}. */
		public Builder pushPast(int ticks, float hpPerSecond, int crashTicks) {
			this.pushPast = true;
			this.pushTicks = ticks;
			this.pushHp = hpPerSecond;
			this.crashTicks = crashTicks;
			return this;
		}

		/** What the crash does (default: {@code Quirks.overload(p, crashTicks)}). */
		public Builder onCrash(Consumer<ServerPlayer> crash) {
			this.onCrash = crash;
			return this;
		}

		/** The user's rule ("meters must build gently"): the meter's natural build x {@code build}, recovery x {@code recover}. */
		public Builder pacing(float build, float recover) {
			this.buildMult = build;
			this.recoverMult = recover;
			return this;
		}

		/** The full suit set makes the meter build this much slower again (Uravity: 0.75). */
		public Builder suitBuild(float mult) {
			this.suitBuildMult = mult;
			return this;
		}

		/** Pace another value than the meter (Explosion's Arm Strain), which builds in {@code dir}. */
		public Builder channel(Channel c, Direction dir) {
			this.channel = c;
			this.channelDirection = dir;
			return this;
		}

		/** The channel's danger threshold, when it is not the meter's RED band. */
		public Builder danger(float v) {
			this.danger = v;
			return this;
		}

		/** The channel's "recovered" threshold (default: the SWEET band's start for BUILDS). */
		public Builder safe(float v) {
			this.safe = v;
			return this;
		}

		/** The scripted "steady heavy use" (and the out-of-action recovery), filled lazily from the kit's config. */
		public Builder heavyUse(Consumer<HeavyUse> fill) {
			this.heavyFill = fill;
			return this;
		}

		public QuirkMeterSpec build() {
			return new QuirkMeterSpec(this);
		}

		public QuirkMeterSpec register() {
			QuirkMeterSpec s = build();
			QuirkMeters.register(s);
			return s;
		}
	}
}
