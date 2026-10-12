package com.projecthero.mod.event.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.16.1: <b>the model contract</b> for the Zombie Raid's own mobs, the Cursed Zombie and the Pillager Spy -- every
 * GeckoLib clip, how long the server holds it and the tick its strike lands on. {@code scratchpad/gen_raid_undead_models.js}
 * parses the {@code clip(...)} / {@code hold(...)} / {@code loop(...)} lines below and pins each strike keyframe to them
 * (it fails if one is off), and {@code RaidModelGameTests} checks the written animation files against this table.
 *
 * <h2>Timing</h2>
 * The client's controllers blend {@link #TRANSITION} ticks into a new clip before its clock starts, so a clip with
 * {@code hit = H} keys its strike at clip time {@code (H - TRANSITION) / 20} s, and a play-once clip is
 * {@code (length - TRANSITION) / 20} s long (it ends before the server drops it). Loops are {@code length} ticks.
 * <ul>
 *   <li><b>Predictive</b> strikes ({@code attack}, {@code slash_a/b}, {@code spit}) are started by the server exactly
 *       {@code hit} ticks before the AI's own attack fires (the melee goal's 20-tick interval / the ranged goal's 50-tick
 *       interval are tracked from the last hit, {@link RaidAnimator}), so the claw / blade / glob lands on the tick the
 *       damage does. The AI itself is untouched.</li>
 *   <li><b>Snap</b> clips ({@code *_snap}, {@code crossbow_fire}, the boss's ability clips, {@code charge_impact},
 *       {@code shove}) answer an action the server has just performed (a melee hit with no wind-up in flight, a bolt
 *       fired, an instant power). Their {@code hit} is {@link #TRANSITION}: the strike pose is the clip's first keyframe,
 *       reached as the blend ends.</li>
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
	public static final String ZOMBIE = "raid_zombie";
	public static final String BABY = "raid_zombie_baby";
	public static final String ACID = "acid_zombie";
	public static final String JUGGERNAUT = "juggernaut_zombie";
	public static final String SKELETON = "sword_skeleton";
	public static final String CURSED = "cursed_zombie";
	public static final String SPY = "pillager_spy";
	public static final String BOSS = "powered_zombie";

	public static final String[] MODELS = { ZOMBIE, BABY, ACID, JUGGERNAUT, SKELETON, CURSED, SPY, BOSS };

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

	// ---------------------------------------------------------------- Risen (Basic / Armoured)
	public static final Clip ZOMBIE_IDLE = loop(ZOMBIE, "idle", 60);
	public static final Clip ZOMBIE_WALK = loop(ZOMBIE, "walk", 32);
	public static final Clip ZOMBIE_CHASE = loop(ZOMBIE, "chase", 20);
	public static final Clip ZOMBIE_ATTACK = clip(ZOMBIE, "attack", 20, 10, UPPER);
	public static final Clip ZOMBIE_READY = hold(ZOMBIE, "attack_ready", 6, UPPER);
	public static final Clip ZOMBIE_SNAP = clip(ZOMBIE, "attack_snap", 12, 3, UPPER);
	public static final Clip ZOMBIE_HURT = clip(ZOMBIE, "hurt", 10, -1, UPPER);
	public static final Clip ZOMBIE_DEATH = hold(ZOMBIE, "death", 20, FULL);

	// ---------------------------------------------------------------- the grave-child (Baby)
	public static final Clip BABY_IDLE = loop(BABY, "idle", 50);
	public static final Clip BABY_WALK = loop(BABY, "walk", 18);
	public static final Clip BABY_CHASE = loop(BABY, "chase", 12);
	public static final Clip BABY_ATTACK = clip(BABY, "attack", 20, 10, UPPER);
	public static final Clip BABY_READY = hold(BABY, "attack_ready", 6, UPPER);
	public static final Clip BABY_SNAP = clip(BABY, "attack_snap", 12, 3, UPPER);
	public static final Clip BABY_HURT = clip(BABY, "hurt", 10, -1, UPPER);
	public static final Clip BABY_DEATH = hold(BABY, "death", 20, FULL);

	// ---------------------------------------------------------------- Acid Zombie
	public static final Clip ACID_IDLE = loop(ACID, "idle", 60);
	public static final Clip ACID_WALK = loop(ACID, "walk", 36);
	/** Started 14 ticks before the ranged goal fires: rear back, belly heaves, throat swells, the glob leaves on hit. */
	public static final Clip ACID_SPIT = clip(ACID, "spit", 24, 14, FULL);
	public static final Clip ACID_SPIT_SNAP = clip(ACID, "spit_snap", 12, 3, FULL);
	public static final Clip ACID_HURT = clip(ACID, "hurt", 10, -1, UPPER);
	public static final Clip ACID_DEATH = hold(ACID, "death", 20, FULL);

	// ---------------------------------------------------------------- Juggernaut
	public static final Clip JUGG_IDLE = loop(JUGGERNAUT, "idle", 70);
	public static final Clip JUGG_WALK = loop(JUGGERNAUT, "walk", 40);
	public static final Clip JUGG_CHASE = loop(JUGGERNAUT, "chase", 30);
	public static final Clip JUGG_ATTACK = clip(JUGGERNAUT, "attack", 24, 12, UPPER);
	public static final Clip JUGG_READY = hold(JUGGERNAUT, "attack_ready", 6, UPPER);
	public static final Clip JUGG_SNAP = clip(JUGGERNAUT, "attack_snap", 14, 3, UPPER);
	/** The 25-tick rooted tell (JuggernautZombie.WINDUP_TICKS); the rush starts as it ends. */
	public static final Clip JUGG_CHARGE_WINDUP = hold(JUGGERNAUT, "charge_windup", 25, FULL);
	public static final Clip JUGG_CHARGE = loop(JUGGERNAUT, "charge", 12);
	public static final Clip JUGG_CHARGE_IMPACT = clip(JUGGERNAUT, "charge_impact", 16, 3, FULL);
	public static final Clip JUGG_HURT = clip(JUGGERNAUT, "hurt", 10, -1, UPPER);
	public static final Clip JUGG_DEATH = hold(JUGGERNAUT, "death", 20, FULL);

	// ---------------------------------------------------------------- Sword Skeleton
	public static final Clip SKEL_IDLE = loop(SKELETON, "idle", 60);
	public static final Clip SKEL_WALK = loop(SKELETON, "walk", 24);
	public static final Clip SKEL_CHASE = loop(SKELETON, "chase", 16);
	/** The combo alternates: a rising diagonal, then a flat backhand. */
	public static final Clip SKEL_SLASH_A = clip(SKELETON, "slash_a", 20, 10, UPPER);
	public static final Clip SKEL_SLASH_B = clip(SKELETON, "slash_b", 20, 10, UPPER);
	public static final Clip SKEL_READY = hold(SKELETON, "attack_ready", 6, UPPER);
	public static final Clip SKEL_SNAP = clip(SKELETON, "slash_snap", 12, 3, UPPER);
	public static final Clip SKEL_BLOCK = hold(SKELETON, "block", 6, UPPER);
	public static final Clip SKEL_HURT = clip(SKELETON, "hurt", 10, -1, UPPER);
	public static final Clip SKEL_DEATH = hold(SKELETON, "death", 20, FULL);

	// ---------------------------------------------------------------- Cursed Zombie
	public static final Clip CURSED_IDLE = loop(CURSED, "idle", 70);
	public static final Clip CURSED_WALK = loop(CURSED, "walk", 30);
	public static final Clip CURSED_CHASE = loop(CURSED, "chase", 18);
	public static final Clip CURSED_ATTACK = clip(CURSED, "attack", 20, 10, UPPER);
	public static final Clip CURSED_READY = hold(CURSED, "attack_ready", 6, UPPER);
	public static final Clip CURSED_SNAP = clip(CURSED, "attack_snap", 12, 3, UPPER);
	public static final Clip CURSED_HURT = clip(CURSED, "hurt", 10, -1, UPPER);
	public static final Clip CURSED_DEATH = hold(CURSED, "death", 20, FULL);

	// ---------------------------------------------------------------- Pillager Spy
	/** Includes a long look through the spyglass. */
	public static final Clip SPY_IDLE = loop(SPY, "idle", 100);
	public static final Clip SPY_WALK = loop(SPY, "walk", 30);
	public static final Clip SPY_RUN = loop(SPY, "run", 16);
	/** Vanilla's crossbow charge is 25 ticks (CrossbowItem); played while the spy is charging. */
	public static final Clip SPY_CHARGE = hold(SPY, "crossbow_charge", 25, UPPER);
	public static final Clip SPY_AIM = hold(SPY, "crossbow_aim", 6, UPPER);
	public static final Clip SPY_FIRE = clip(SPY, "crossbow_fire", 14, 3, UPPER);
	public static final Clip SPY_HURT = clip(SPY, "hurt", 10, -1, UPPER);
	public static final Clip SPY_DEATH = hold(SPY, "death", 20, FULL);

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
