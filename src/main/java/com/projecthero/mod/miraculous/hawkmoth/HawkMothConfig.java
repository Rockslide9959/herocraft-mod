package com.projecthero.mod.miraculous.hawkmoth;

/**
 * v0.16.6: every Hawk Moth number (Nooroo, the Butterfly Miraculous). Ticks unless the name says otherwise. The core's
 * shared transformed stats ({@code MiraculousStats}) come on top and are not repeated here.
 */
public final class HawkMothConfig {
	private HawkMothConfig() {
	}

	// ---------------------------------------------------------------- R: Cane Strike / Cane Sword
	/** Damage of each of the three cane blows. */
	public static final float STRIKE_DAMAGE = 9.0f;
	/** Reach of a cane blow, blocks. */
	public static final double STRIKE_REACH = 3.6;
	/** Gap between two blows of the combo. */
	public static final int STRIKE_GAP = 8;
	/** The combo resets when the next press comes later than this after the last blow. */
	public static final int STRIKE_WINDOW = 30;
	/** Rest after the third blow. */
	public static final int STRIKE_FINISH_COOLDOWN = 20;
	/** Knock-back of the third blow. */
	public static final double STRIKE_FINISH_KNOCKBACK = 0.9;
	/** Cane Sword: the blade draws out of the shaft over this many ticks before the lunge. */
	public static final int SWORD_DRAW_TICKS = 6;
	/** ... the lunge lasts this many ticks, moving {@link #SWORD_LUNGE_SPEED} blocks each. */
	public static final int SWORD_LUNGE_TICKS = 4;
	public static final double SWORD_LUNGE_SPEED = 1.5;
	/** Lunge distance, blocks (= ticks x speed). */
	public static final double SWORD_LUNGE_BLOCKS = SWORD_LUNGE_TICKS * SWORD_LUNGE_SPEED;
	public static final float SWORD_DAMAGE = 14.0f;
	/** Width of the lunge's cut (blocks either side of its path). */
	public static final double SWORD_WIDTH = 0.9;
	/** The blade stays out this long after the lunge, then slides back into the cane. */
	public static final int SWORD_SHOW_TICKS = 30;
	public static final int SWORD_COOLDOWN = 6 * 20;

	// ---------------------------------------------------------------- G: Akuma / Recall
	/** How far an akuma can be sent, blocks. */
	public static final double AKUMA_RANGE = 30.0;
	/** How fast an akuma flies, blocks a tick. */
	public static final double AKUMA_SPEED = 0.7;
	/** An akuma that has not reached its target by then gives up. */
	public static final int AKUMA_FLIGHT_LIMIT = 5 * 20;
	/** How long an akumatized mob fights for Hawk Moth. */
	public static final int AKUMA_TICKS = 30 * 20;
	/** At most this many akumas out at once (flying or bonded). */
	public static final int AKUMA_MAX = 3;
	/** +50% health and +50% damage (multiplied total). */
	public static final double AKUMA_HEALTH_BONUS = 0.5;
	public static final double AKUMA_DAMAGE_BONUS = 0.5;
	/** An akumatized mob picks fights within this radius of Hawk Moth, blocks. */
	public static final double AKUMA_GUARD_RADIUS = 16.0;
	/** An akumatized mob drifting further than this from Hawk Moth (with nothing to fight) walks back to him. */
	public static final double AKUMA_LEASH = 12.0;
	/** Melee damage of a mob with no attack of its own (a cow, a villager), before the +50%. */
	public static final float AKUMA_BARE_HIT = 3.0f;
	public static final int AKUMA_COOLDOWN = 4 * 20;
	public static final int RECALL_COOLDOWN = 3 * 20;
	/** At or above this max health a creature counts as a boss (immune to akumas). */
	public static final float BOSS_HEALTH = 150.0f;

	// ---------------------------------------------------------------- X: Butterfly Swarm Flight / Swarm Dash
	public static final int FLIGHT_TICKS = 10 * 20;
	/** Vanilla flying speed while the swarm carries you (vanilla creative 0.05). */
	public static final float FLIGHT_SPEED = 0.07f;
	/** From activation: 10 s of flight then 10 s rest. */
	public static final int FLIGHT_COOLDOWN = 20 * 20;
	/** Swarm Dash distance, blocks. */
	public static final double DASH_BLOCKS = 12.0;
	/** The cloud covers the distance in this many ticks. */
	public static final int DASH_TICKS = 6;
	/** ... then pulls back together into Hawk Moth over this many. */
	public static final int DASH_REFORM_TICKS = 6;
	public static final int DASH_COOLDOWN = 8 * 20;

	// ---------------------------------------------------------------- Z: Butterfly Shield / RISE, AKUMA!
	/** The longest the shield can be held. */
	public static final int SHIELD_MAX_TICKS = 10 * 20;
	/** Rest after the shield drops. */
	public static final int SHIELD_COOLDOWN = 4 * 20;
	/** The butterfly wall stands this far in front of him, blocks, and is this wide / tall. */
	public static final double SHIELD_DISTANCE = 1.6;
	public static final double SHIELD_WIDTH = 3.0;
	public static final double SHIELD_HEIGHT = 3.0;
	/** Rise, Akuma!: target range, blocks. */
	public static final double RISE_RANGE = 40.0;
	/** The cupped-hands moment: the white butterfly darkens for this long before it flies. */
	public static final int RISE_CUP_TICKS = 30;
	/** A Champion lasts this long. */
	public static final int CHAMPION_TICKS = 60 * 20;
	/** 1.5x size, 3x health, 2x damage (added scale / multiplied totals). */
	public static final double CHAMPION_SCALE_BONUS = 0.5;
	public static final double CHAMPION_HEALTH_BONUS = 2.0;
	public static final double CHAMPION_DAMAGE_BONUS = 1.0;
	/** A player has this long to answer the offer before it counts as refused. */
	public static final int OFFER_TICKS = 30 * 20;
	/** Rise, Akuma! cooldown from the release (the 3-minute countdown starts too). */
	public static final int RISE_COOLDOWN = 60 * 20;
	/** ... cut to this when the offer is refused, runs out, or the akuma never arrives. */
	public static final int RISE_REFUSED_COOLDOWN = 10 * 20;
	/** A Champion mob's themed power fires this often while it has a target. */
	public static final int CHAMPION_POWER_INTERVAL = 50;
	/** A Champion player's themed power on the attack key: this often at most. */
	public static final int CHAMPION_PLAYER_POWER_COOLDOWN = 25;
	/** Themed-power range, blocks. */
	public static final double CHAMPION_POWER_RANGE = 24.0;
	/** Ice shot: damage and Slowness II for this long. */
	public static final float ICE_DAMAGE = 5.0f;
	public static final int ICE_SLOW_TICKS = 60;
	/** Storm: the lightning's damage and splash radius, blocks. */
	public static final float STORM_DAMAGE = 7.0f;
	public static final double STORM_RADIUS = 2.0;

	// ---------------------------------------------------------------- V: Swarm Blind / Hawk Moth's Window
	public static final double BLIND_RADIUS = 8.0;
	public static final int BLIND_TICKS = 6 * 20;
	/** Slowness amplifier (1 = Slowness II). */
	public static final int BLIND_SLOW_AMP = 1;
	public static final int BLIND_COOLDOWN = 15 * 20;
	/** The longest look through an akuma's eyes. */
	public static final int WINDOW_TICKS = 15 * 20;
	/** Rest after the window closes. */
	public static final int WINDOW_COOLDOWN = 10 * 20;

	// ---------------------------------------------------------------- C: Emotion Sense / Akuma Link
	public static final double SENSE_RANGE = 50.0;
	public static final int SENSE_TICKS = 15 * 20;
	/** The sense is refreshed this often while it runs (moods change). */
	public static final int SENSE_REFRESH = 20;
	public static final int SENSE_COOLDOWN = 25 * 20;
	/** A player at or under this fraction of max health reads blue (low), above {@link #SENSE_PLAYER_MID} white. */
	public static final float SENSE_PLAYER_LOW = 0.35f;
	public static final float SENSE_PLAYER_MID = 0.7f;
	/** Akuma Link: how far he can point his akumas at a target, blocks. */
	public static final double LINK_RANGE = 40.0;
	public static final int LINK_COOLDOWN = 20;

	// ---------------------------------------------------------------- outline colours (RGB)
	public static final int AKUMA_OUTLINE = 0x9B30FF;
	public static final int CHAMPION_OUTLINE = 0x6A0DAD;
	public static final int SENSE_HOSTILE = 0xFF2A2A;
	public static final int SENSE_CALM = 0xFFFFFF;
	public static final int SENSE_SCARED = 0x3C78FF;
	public static final int SENSE_PLAYER_HURT = 0x8FB8FF;
}
