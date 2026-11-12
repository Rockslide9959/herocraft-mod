package com.projecthero.mod.beastboy;

/**
 * v0.17.3 Beast Boy: every number of the power in one place (docs/TEEN_TITANS_DESIGN.md, Beast Boy). Form health and hitboxes live
 * in {@link BeastForm}; what each form costs and moves like is in {@link BeastBoyForms}.
 */
public final class BeastBoyConfig {
	private BeastBoyConfig() {
	}

	// ---- Morph Stamina (the Meter)
	public static final float STAMINA_MAX = 100f;
	/** Refill per tick in human form (6 a second). */
	public static final float STAMINA_REGEN_PER_TICK = 6f / 20f;
	/** Entering a sustained form from the wheel. */
	public static final float MORPH_COST = 5f;
	/** A context morph / the fly. */
	public static final float CONTEXT_MORPH_COST = 2f;
	/** A sustained form needs at least this much stamina to start. */
	public static final float MORPH_MIN = 10f;

	// ---- Morph Chain
	public static final int CHAIN_MAX = 5;
	public static final float CHAIN_DAMAGE_PER = 0.08f;
	/** The chain drops back to 0 after this long without a quick-morph hit. */
	public static final int CHAIN_TIMEOUT = 100;

	// ---- form health
	/** Knocked out of a form: dazed this long. */
	public static final int DAZE_TICKS = 20;
	/** Knocked out of a form: it can't be taken again for this long. */
	public static final int FORM_LOCKOUT_TICKS = 200;
	/** Hits inside this many ticks of the last one only count for what they add on top (like vanilla's hurt window). */
	public static final int POOL_HURT_WINDOW = 10;

	// ---- the quick morphs (human form)
	public static final int QUICK_TICKS = 10;
	public static final float RAM_DAMAGE = 10f, RAM_COST = 10f, RAM_RANGE = 6f;
	public static final int RAM_COOLDOWN = 20;
	public static final float POUNCE_DAMAGE = 8f, POUNCE_COST = 10f, POUNCE_RANGE = 8f;
	public static final int POUNCE_COOLDOWN = 20, BLEED_TICKS = 60, BLEED_EVERY = 20;
	public static final float BLEED_DAMAGE = 1f;
	public static final float SMASH_DAMAGE = 12f, SMASH_COST = 12f, SMASH_REACH = 3.5f;
	public static final int SMASH_COOLDOWN = 24;
	public static final float SNAKE_DAMAGE = 5f, SNAKE_COST = 10f, SNAKE_RANGE = 5f;
	public static final int SNAKE_COOLDOWN = 20, SNAKE_EFFECT_TICKS = 80;
	public static final float ROAR_DAMAGE = 6f, ROAR_COST = 25f, ROAR_RANGE = 8f;
	public static final int ROAR_COOLDOWN = 200, FEAR_TICKS = 80;
	public static final float STOMP_DAMAGE = 14f, STOMP_COST = 20f, STOMP_RADIUS = 4f;
	public static final int STOMP_COOLDOWN = 40;
	public static final float OCTO_COST = 20f, OCTO_RADIUS = 6f;
	public static final int OCTO_COOLDOWN = 60, OCTO_HOLD_TICKS = 40, OCTO_GRABS = 4;
	public static final float SENSES_COST = 10f;
	public static final int SENSES_TICKS = 200, SENSES_COOLDOWN = 300;

	// ---- The Beast (Shift+Z)
	public static final float BEAST_COST = 100f;
	public static final int BEAST_TICKS = 600, BEAST_COOLDOWN = 3600;
	public static final float BEAST_SWIPE = 20f, BEAST_SLAM = 12f;
	public static final int BEAST_SWIPE_COOLDOWN = 14, BEAST_LEAP_COOLDOWN = 40, BEAST_ROAR_COOLDOWN = 160;

	// ---- the sustained forms' own moves (damage, cooldown in ticks)
	public static final float TREX_BITE = 18f, TREX_TAIL = 12f;
	public static final int TREX_BITE_CD = 24, TREX_TAIL_CD = 40, TREX_ROAR_CD = 160;
	public static final float GORILLA_PUNCH = 12f, GORILLA_THROW = 6f;
	public static final int GORILLA_PUNCH_CD = 16, GORILLA_THROW_CD = 40, GORILLA_BEAT_CD = 400, CHEST_BEAT_TICKS = 200;
	public static final float RHINO_CHARGE = 16f, RHINO_RANGE = 8f;
	public static final int RHINO_CHARGE_CD = 40;
	public static final float ELEPHANT_TRUNK = 8f, ELEPHANT_STOMP = 14f;
	public static final int ELEPHANT_TRUNK_CD = 30, ELEPHANT_STOMP_CD = 40, ELEPHANT_SPRAY_CD = 60;
	public static final float WOLF_BITE = 8f;
	public static final int WOLF_BITE_CD = 12, WOLF_HOWL_CD = 400, HOWL_TICKS = 300;
	public static final float BEAR_MAUL = 14f;
	public static final int BEAR_MAUL_CD = 20, BEAR_ROAR_CD = 160;
	public static final float PTERO_DIVE = 12f, PTERO_RANGE = 12f;
	public static final int PTERO_DIVE_CD = 40;
	public static final float HAWK_TALON = 5f;
	public static final int HAWK_TALON_CD = 10, HAWK_SCREECH_CD = 200, SCREECH_TICKS = 200;
	public static final int BAT_PULSE_CD = 160, BAT_PULSE_TICKS = 120;
	public static final float BAT_PULSE_RADIUS = 24f;
	public static final float SHARK_BITE = 14f;
	public static final int SHARK_BITE_CD = 20;
	public static final int OCTOPUS_INK_CD = 100, INK_TICKS = 80;
	public static final float WHALE_SLAM = 10f;
	public static final int WHALE_SLAM_CD = 40;
	public static final int WHALE_SEATS = 4;
	/** Melee reach of the forms' bites and punches. */
	public static final float REACH = 4f;

	// ---- getting it
	/** One jungle ocelot in this many is the green (Sakutia) one. */
	public static final int GREEN_OCELOT_ONE_IN = 6;
	/** A fed green ocelot bites the one who fed it if they crouch this close within this long. */
	public static final double BITE_RANGE = 2.5;
	public static final int FED_REMEMBER_TICKS = 2400;
	public static final int FEVER_TICKS = 1200;
	/** Jungle temple chests: the Sakutia Serum in one chest out of this many. */
	public static final int SERUM_ONE_IN = 3;

	// ---- passives
	public static final float HEALTH = 20f;
	public static final int NIGHT_VISION_TICKS = 260;
	public static final int SYNC_INTERVAL = 5;
}
