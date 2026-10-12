package com.projecthero.mod.event.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.16.1: <b>the Supervillain bosses' model contract</b> -- every animation clip of the Chimera, the Arsenal and the Omega
 * Mage (one shared table: all three play the same moves, with their own art), how long the server holds it, and the server
 * tick its strike / release lands on. The model generator ({@code scratchpad/gen_supervillain_models.js} +
 * {@code supervillain_clips.js}) parses the {@code clip(...)} / {@code hold(...)} / {@code loop(...)} lines below and pins
 * every strike keyframe to them, like {@code FrostGiantAnims}.
 *
 * <h2>Timing (the Titan / Frost Giant convention)</h2>
 * The server syncs a clip ({@link EmpoweredZombie#playSupervillainAnim}); the client's GeckoLib controller blends
 * {@link #TRANSITION} ticks into it before the clip's clock starts, so a clip with {@code hit = H} keys its strike at clip
 * tick {@code H - TRANSITION}, a play-once clip is {@code length - TRANSITION} ticks long, held clips hold their last frame
 * and loops are {@code length} ticks. Moves whose damage lands the moment they start (melee, repel, the instant power
 * abilities) use {@code hit = TRANSITION}: the clip opens on its strike pose. Telegraphed casts ({@code cast_1..4}) wind
 * up for {@code cycles x 10} ticks -- exactly {@code BossPowerController#beginCast}'s wind-up -- and release on it.
 *
 * <p>{@code neck} (head look) and {@code flinch} (hurt flinch) are posed by code every frame and must never be keyed.
 * Edit only the numbers in the clip lines -- the generator parses them with a regex.
 */
public final class SupervillainAnims {
	public static final int TRANSITION = 4;
	public static final String[] CODE_DRIVEN_BONES = { "neck", "flinch" };

	/** Asset base names ({@code geo/supervillain/<model>.geo.json} etc.), one per {@link SupervillainVariant}. */
	public static String model(SupervillainVariant v) {
		return v.id();
	}

	/**
	 * One clip.
	 *
	 * @param name     {@code animation.<model>.<name>}
	 * @param length   ticks the server plays it for (a loop's cycle length)
	 * @param hit      server tick of the strike / release, or -1
	 * @param kind     0 = play once, 1 = hold the last frame, 2 = loop
	 * @param priority a running clip is only replaced by a higher priority (locomotion and idle are 0)
	 */
	public record Clip(String name, int length, int hit, int kind, int priority) {
		public String id(SupervillainVariant v) {
			return "animation." + model(v) + "." + name;
		}

		public boolean loops() {
			return kind == 2;
		}

		public boolean holds() {
			return kind == 1;
		}

		public double strikeSeconds() {
			return hit < TRANSITION ? -1.0 : (hit - TRANSITION) / 20.0;
		}

		public double clipSeconds() {
			return loops() ? length / 20.0 : Math.max(1, length - TRANSITION) / 20.0;
		}
	}

	private static final List<Clip> ALL_MUTABLE = new ArrayList<>();
	public static final List<Clip> ALL = Collections.unmodifiableList(ALL_MUTABLE);

	// ---------------------------------------------------------------- locomotion (client-chosen)
	public static final Clip IDLE = loop("idle", 80);
	/** An idle variation the client plays now and then while standing (sniff / weapon check / rune juggling). */
	public static final Clip FIDGET = clip("fidget", 64, -1, 0);
	public static final Clip WALK = loop("walk", 28);
	public static final Clip RUN = loop("run", 16);
	/** Airborne (Flight, leaps, knock-ups). */
	public static final Clip AIR = loop("air", 24);

	// ---------------------------------------------------------------- server-synced moves
	/** The boss intro: rises / roars on arrival. The roar peaks on {@code hit}. */
	public static final Clip INTRO = clip("intro", 56, 24, 8);
	/** A heavy hit (8+ damage) staggers him; ordinary hits only flinch the {@code flinch} bone. */
	public static final Clip HURT = clip("hurt", 12, -1, 1);
	public static final Clip DEATH = hold("death", 40, -1, 9);
	/** Melee swings, alternating. Vanilla zombie melee lands on the call, so the clip opens on its strike. */
	public static final Clip MELEE = clip("melee", 16, 4, 4);
	public static final Clip MELEE_ALT = clip("melee_alt", 16, 4, 4);
	/** The power-independent close-range shove ({@code EmpoweredZombie#closeRangeRepel}). */
	public static final Clip REPEL = clip("repel", 18, 4, 4);
	/** An instant ability with no better match (buffs, auras, summons). */
	public static final Clip BURST = clip("burst", 20, 4, 3);
	/** An area strike ({@code BossPowerController#strikeArea}). */
	public static final Clip SLAM = clip("slam", 20, 4, 3);
	/** A projectile / beam / line strike. */
	public static final Clip SHOOT = clip("shoot", 16, 4, 3);
	/** A dash ({@code BossPowerController#dash}). */
	public static final Clip LUNGE = clip("lunge", 16, 4, 3);
	/** A teleport ({@code BossPowerController#blinkTo}): reappearing flourish. */
	public static final Clip BLINK = clip("blink", 14, 4, 3);
	/** Telegraphed casts: wind-up of 1..4 ability cycles (10 ticks each), released on {@code hit}. */
	public static final Clip CAST_1 = clip("cast_1", 24, 10, 6);
	public static final Clip CAST_2 = clip("cast_2", 34, 20, 6);
	public static final Clip CAST_3 = clip("cast_3", 46, 30, 6);
	public static final Clip CAST_4 = clip("cast_4", 58, 40, 7);

	private SupervillainAnims() {
	}

	/** The cast clip for a wind-up of {@code cycles} ability cycles. */
	public static Clip cast(int cycles) {
		return switch (Math.max(1, Math.min(4, cycles))) {
			case 1 -> CAST_1;
			case 2 -> CAST_2;
			case 3 -> CAST_3;
			default -> CAST_4;
		};
	}

	public static int indexOf(Clip clip) {
		return clip == null ? -1 : ALL.indexOf(clip);
	}

	public static Clip byIndex(int index) {
		return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
	}

	public static Clip byName(String name) {
		for (Clip c : ALL) {
			if (c.name().equals(name)) {
				return c;
			}
		}
		return null;
	}

	private static Clip clip(String name, int length, int hit, int priority) {
		return add(new Clip(name, length, hit, 0, priority));
	}

	private static Clip hold(String name, int length, int hit, int priority) {
		return add(new Clip(name, length, hit, 1, priority));
	}

	private static Clip loop(String name, int length) {
		return add(new Clip(name, length, -1, 2, 0));
	}

	private static Clip add(Clip c) {
		ALL_MUTABLE.add(c);
		return c;
	}
}
