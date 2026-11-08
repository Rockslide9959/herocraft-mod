package com.projecthero.mod.miraculous.ryuko;

/** v0.17.3: every Ryuko number and move id in one place. Ticks unless named otherwise; damage in half hearts. */
public final class RyukoConfig {
	public static final String STRIKE = "ryuko_blade_combo";
	public static final String LUNGE = "ryuko_fencing_lunge";
	public static final String ELEMENT_STRIKE = "ryuko_element_strike";
	public static final String ELEMENT_BURST = "ryuko_element_burst";
	public static final String ELEMENT_DASH = "ryuko_element_dash";
	public static final String RISE = "ryuko_storm_rise";
	public static final String THROW = "ryuko_sword_throw";
	public static final String FORM = "ryuko_dragon_form";
	public static final String SWITCH = "ryuko_switch_element";
	public static final String GUARD = "ryuko_storm_guard";
	public static final String PARRY = "ryuko_parry";
	public static final String RIPOSTE = "ryuko_riposte";

	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// R
	public static final float STRIKE_DAMAGE = 8f;
	public static final float STRIKE_FINISHER_DAMAGE = 11f;
	public static final double STRIKE_REACH = 3.6;
	public static final int STRIKE_WINDOW = 24;
	public static final int STRIKE_STEP_COOLDOWN = 6;
	public static final int STRIKE_COOLDOWN = 24;
	public static final float LUNGE_DAMAGE = 12f;
	public static final int LUNGE_TICKS = 6;
	public static final double LUNGE_SPEED = 1.5;
	public static final int LUNGE_COOLDOWN = 100;

	// G
	public static final float WIND_STRIKE_DAMAGE = 8f;
	public static final double WIND_STRIKE_RANGE = 8.0;
	public static final float WATER_STRIKE_DAMAGE = 10f;
	public static final double WATER_STRIKE_RANGE = 6.0;
	public static final int WATER_SLOW_TICKS = 60;
	public static final float LIGHTNING_STRIKE_DAMAGE = 12f;
	public static final float LIGHTNING_CHAIN_DAMAGE = 8f;
	public static final double LIGHTNING_RANGE = 24.0;
	public static final double LIGHTNING_CHAIN_RANGE = 6.0;
	public static final int STRIKE_ELEMENT_COOLDOWN = 80;
	public static final double BURST_RADIUS = 6.0;
	public static final float WIND_BURST_DAMAGE = 10f;
	public static final int WHIRL_TICKS = 40;
	public static final float WATER_BURST_DAMAGE = 8f;
	public static final float LIGHTNING_BURST_DAMAGE = 12f;
	public static final int BURST_COOLDOWN = 200;

	// X
	public static final int DASH_COOLDOWN = 60;
	public static final int SLIDE_TICKS = 12;
	public static final float SLIDE_DAMAGE = 4f;
	public static final double BLINK_RANGE = 8.0;
	public static final float BLINK_DAMAGE = 6f;
	public static final double RISE_SPEED = 1.4;
	public static final int GLIDE_TICKS = 120;
	public static final double GLIDE_FALL_SPEED = 0.14;
	public static final int RISE_COOLDOWN = 160;

	// Z
	public static final float THROW_DAMAGE = 14f;
	public static final double THROW_SPEED = 1.6;
	public static final double THROW_RANGE = 22.0;
	public static final int THROW_COOLDOWN = 120;
	public static final int FORM_TICKS = 8 * 20;
	public static final float FORM_DAMAGE = 9f;
	public static final double FORM_RADIUS = 2.4;
	public static final int FORM_HIT_EVERY = 20;
	public static final float FORM_FLY_SPEED = 0.13f;
	public static final int FORM_COOLDOWN = 2400;

	// V
	public static final int SWITCH_COOLDOWN = 10;
	public static final int GUARD_TICKS = 6 * 20;
	public static final double GUARD_RADIUS = 3.0;
	public static final float GUARD_SHOCK_DAMAGE = 6f;
	public static final int GUARD_COOLDOWN = 300;

	// C (v0.17.3 tester: Parry cooldown 40 -> 120, the Riposte window 60 -> 30, damage 22 -> 15, cooldown 20 -> 100 -- too spammable)
	public static final int PARRY_TICKS = 12;
	public static final int RIPOSTE_WINDOW = 30;
	public static final int PARRY_COOLDOWN = 120;
	public static final float RIPOSTE_DAMAGE = 15f;
	public static final double RIPOSTE_REACH = 8.0;
	public static final int RIPOSTE_COOLDOWN = 100;

	private RyukoConfig() {
	}
}
