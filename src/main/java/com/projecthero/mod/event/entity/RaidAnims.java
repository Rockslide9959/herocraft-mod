package com.projecthero.mod.event.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.16.1: <b>the model contract</b> for the Powered Zombie Boss's custom GeckoLib model -- every clip, how long the
 * server holds it and the tick its strike lands on. (The raid's other mobs, the Cursed Zombie and the Pillager Spy are
 * drawn on the player rig with their own skins -- {@code client.render.raid.RaidPlayerRenderers} -- and have no clips.)
 * {@code scratchpad/gen_raid_undead_models.js}
 * parses the {@code clip(...)} / {@code hold(...)} / {@code loop(...)} lines below and pins each strike keyframe to them
 * (it fails if one is off), and {@code RaidModelGameTests} checks the written animation files against this table.
 *
 * <h2>Timing</h2>
 * The client's controllers blend {@link #TRANSITION} ticks into a new clip before its clock starts, so a clip with
 * {@code hit = H} keys its strike at clip time {@code (H - TRANSITION) / 20} s, and a play-once clip is
 * {@code (length - TRANSITION) / 20} s long (it ends before the server drops it). Loops are {@code length} ticks.
 * <ul>
 *   <li><b>Predictive</b> strikes ({@code attack}) are started by the server exactly {@code hit} ticks before the AI's
 *       own attack fires (the melee goal's 20-tick interval is tracked from the last hit, {@link RaidAnimator}), so the
 *       claw lands on the tick the damage does. The AI itself is untouched.</li>
 *   <li><b>Snap</b> clips ({@code attack_snap}, {@code cast_release}, the ability clips, {@code shove}) answer an action
 *       the server has just performed (a melee hit with no wind-up in flight, an instant power). Their {@code hit} is
 *       {@link #TRANSITION}: the strike pose is the clip's first keyframe, reached as the blend ends.</li>
 * </ul>
 * {@code layer}: {@link #FULL} clips replace locomotion (main controller); {@link #UPPER} clips play on the action
 * controller over the walk/chase cycle (arms / torso / head only). {@code neck} is posed by code (head look) and is never
 * keyed. Edit only the numbers -- the generator parses these lines with a regex.
 */
public final class RaidAnims {
	/** Ticks both controllers blend into a new clip before its clock starts. */
	public static final int TRANSITION = 3;

	public static final int FULL = 0;
	public static final int UPPER = 1;

	/** Asset base names: geo/raid/<name>.geo.json, textures/entity/raid/<name>.png, animations/raid/<name>.animation.json. */
	public static final String BOSS = "powered_zombie";

	public static final String[] MODELS = { BOSS };

	/**
	 * One clip.
	 *
	 * @param kind  0 = play once, 1 = hold the last frame, 2 = loop
	 * @param layer {@link #FULL} or {@link #UPPER}
	 */
	public record Clip(String model, String name, int length, int hit, int kind, int layer) {
		public String id() {
			return "animation." + model + "." + name;
		}

		public boolean loops() {
			return kind == 2;
		}

		public boolean holds() {
			return kind == 1;
		}

		/** Clip time (seconds) of the strike keyframe, or -1 when it has none. */
		public double strikeSeconds() {
			return hit < TRANSITION ? -1.0 : (hit - TRANSITION) / 20.0;
		}

		/** The clip's own animation_length (seconds). */
		public double clipSeconds() {
			return loops() ? length / 20.0 : Math.max(1, length - TRANSITION) / 20.0;
		}
	}

	private static final List<Clip> ALL_MUTABLE = new ArrayList<>();
	public static final List<Clip> ALL = Collections.unmodifiableList(ALL_MUTABLE);

	// ---------------------------------------------------------------- Powered Zombie Boss
	public static final Clip BOSS_IDLE = loop(BOSS, "idle", 70);
	public static final Clip BOSS_WALK = loop(BOSS, "walk", 40);
	public static final Clip BOSS_CHASE = loop(BOSS, "chase", 24);
	/** Flight boss off the ground. */
	public static final Clip BOSS_HOVER = loop(BOSS, "hover", 30);
	public static final Clip BOSS_ATTACK = clip(BOSS, "attack", 20, 10, UPPER);
	public static final Clip BOSS_READY = hold(BOSS, "attack_ready", 6, UPPER);
	public static final Clip BOSS_SNAP = clip(BOSS, "attack_snap", 12, 3, UPPER);
	/** A telegraphed cast (BossPowerController.beginCast) -- held until it resolves. */
	public static final Clip BOSS_CAST_WINDUP = hold(BOSS, "cast_windup", 20, FULL);
	public static final Clip BOSS_CAST_RELEASE = clip(BOSS, "cast_release", 16, 3, FULL);
	public static final Clip BOSS_SLAM = clip(BOSS, "ability_slam", 18, 3, FULL);
	public static final Clip BOSS_PUNCH = clip(BOSS, "ability_punch", 14, 3, UPPER);
	public static final Clip BOSS_BURST = clip(BOSS, "ability_burst", 18, 3, FULL);
	public static final Clip BOSS_DASH = clip(BOSS, "ability_dash", 12, 3, FULL);
	public static final Clip BOSS_SHOVE = clip(BOSS, "shove", 14, 3, FULL);
	public static final Clip BOSS_HURT = clip(BOSS, "hurt", 10, -1, UPPER);
	public static final Clip BOSS_DEATH = hold(BOSS, "death", 20, FULL);

	private RaidAnims() {
	}

	private static Clip clip(String model, String name, int length, int hit, int layer) {
		return add(new Clip(model, name, length, hit, 0, layer));
	}

	private static Clip hold(String model, String name, int length, int layer) {
		return add(new Clip(model, name, length, -1, 1, layer));
	}

	private static Clip loop(String model, String name, int length) {
		return add(new Clip(model, name, length, -1, 2, FULL));
	}

	private static Clip add(Clip c) {
		ALL_MUTABLE.add(c);
		return c;
	}

	public static int indexOf(Clip clip) {
		return clip == null ? -1 : ALL.indexOf(clip);
	}

	public static Clip byIndex(int i) {
		return i >= 0 && i < ALL.size() ? ALL.get(i) : null;
	}

	public static List<Clip> of(String model) {
		List<Clip> out = new ArrayList<>();
		for (Clip c : ALL) {
			if (c.model().equals(model)) {
				out.add(c);
			}
		}
		return out;
	}

	public static Clip find(String model, String name) {
		for (Clip c : ALL) {
			if (c.model().equals(model) && c.name().equals(name)) {
				return c;
			}
		}
		return null;
	}

	/** The named clip of {@code model}, or null (units share clip names: attack, hurt, death ...). */
	public static Clip of(String model, String name) {
		return find(model, name);
	}
}
