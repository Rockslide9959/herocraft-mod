package com.projecthero.mod.undead;

/**
 * v0.17.3 Undead kit rework: the numbers for Z (Ten Twists: Vortex Bullet), Shift+Z (Unluck Meteor), V (Reform, numbers in
 * {@link UndeadConfig}), Shift+V (Rib Cage Guard), hold V (the bone katana Kurikara and its sword skills) and the shoulder sword.
 * Ticks unless named otherwise. The animation ids and state flags here are this kit's own (kept well clear of the ones in
 * {@code UndeadState}).
 */
public final class UndeadKitConfig {
	private UndeadKitConfig() {
	}

	// ---------------------------------------------------------------- animation ids (UndeadState.animId) and flags

	/** Z wind-up: the arm held out and wrenched round ten times. */
	public static final int ANIM_VORTEX_TWIST = 120;
	/** Z fired: the arm springs off, untwisting. */
	public static final int ANIM_VORTEX_FIRE = 121;
	/** Shift+Z let go at full charge: the arm thrown down at the target as the meteor is called. */
	public static final int ANIM_METEOR_CALL = 122;
	/** The katana's skills (hold V). */
	public static final int ANIM_K_CRESCENT = 130;
	public static final int ANIM_K_GREAT = 131;
	public static final int ANIM_K_FLASH = 132;
	public static final int ANIM_K_WHIRL = 133;
	/** Shoulder sword stabbed in / pulled out. */
	public static final int ANIM_SHOULDER_IN = 136;
	public static final int ANIM_SHOULDER_OUT = 137;

	/** Shift+V: the Rib Cage Guard is up (UndeadState.flags; a high bit, clear of the shared ones). */
	public static final int FLAG_RIB_GUARD = 1 << 24;
	/** Shift+Z held: the Unluck Meteor is charging. */
	public static final int FLAG_METEOR_CHARGE = 1 << 25;

	// ---------------------------------------------------------------- Z: Ten Twists -- Vortex Bullet

	/** The wind-up: ten twists of the arm before it flies. */
	public static final int VORTEX_WINDUP = 12;
	public static final int VORTEX_TWISTS = 10;
	public static final float VORTEX_DAMAGE = 18f;
	/** Blocks a tick (52 b/s): it crosses its range in under a second. */
	public static final double VORTEX_SPEED = 2.6;
	public static final double VORTEX_RANGE = 30.0;
	/** How close to the drill's line a creature is hit (plus half its width). */
	public static final double VORTEX_RADIUS = 0.9;
	/** What it does to each creature it bores through: a shove out of the line and a lift. */
	public static final double VORTEX_KNOCK = 0.7;
	public static final double VORTEX_LIFT = 0.25;
	/** The burst where it stops (range, wall): everything within this radius is blown away. */
	public static final double VORTEX_BURST_RADIUS = 4.0;
	public static final float VORTEX_BURST_DAMAGE = 6f;
	public static final double VORTEX_BURST_KNOCK = 2.2;
	public static final double VORTEX_BURST_LIFT = 0.55;
	public static final int VORTEX_COOLDOWN = 160;

	// ---------------------------------------------------------------- Shift+Z: Unluck Meteor

	/** Shift+Z held this long calls it down (let go before: cancelled, no cooldown). */
	public static final int METEOR_CHARGE = 100;
	public static final double METEOR_REACH = 60.0;
	/** From the sky to the ground. */
	public static final int METEOR_FALL = 22;
	/** Where it starts: this high above the target and this far back toward the caster. */
	public static final double METEOR_HEIGHT = 70.0;
	public static final double METEOR_BACK = 26.0;
	public static final float METEOR_DAMAGE = 40f;
	/** At the edge of the blast the damage is this share of the centre's. */
	public static final float METEOR_EDGE_SHARE = 0.2f;
	public static final double METEOR_RADIUS = 10.0;
	public static final double METEOR_KNOCK = 2.6;
	public static final double METEOR_LIFT = 0.9;
	public static final int METEOR_FIRE_SECONDS = 6;
	public static final int METEOR_COOLDOWN = 1800;
	/** Camera shake for anyone this close to the impact. */
	public static final double METEOR_SHAKE_RADIUS = 48.0;
	/** The caster's speed while charging (a multiplier added: -0.6 = 40% of normal). */
	public static final double METEOR_CHARGE_SLOW = -0.6;

	// ---------------------------------------------------------------- Shift+V: Rib Cage Guard

	/** The longest it stays up (held); a quick tap keeps it up this long too. */
	public static final int RIB_TICKS = 80;
	/** Let go of Shift+V before this: it was a tap, and the guard stays up its full time. */
	public static final int RIB_TAP = 6;
	/** What a frontal melee hit still does (75% blocked). */
	public static final float RIB_MELEE_SHARE = 0.25f;
	/** Who hits the ribs (melee) takes this back. */
	public static final float RIB_THORNS = 4f;
	/** How wide "in front" is: the cosine of the half-angle. */
	public static final double RIB_FRONT_COS = 0.1;
	public static final int RIB_COOLDOWN = 240;

	// ---------------------------------------------------------------- hold V: the bone katana's skills

	/** R: Crimson Crescent Moon -- a red crescent thrown off the blade. */
	public static final float K_CRESCENT_DAMAGE = 13f;
	public static final double K_CRESCENT_RANGE = 16.0;
	public static final double K_CRESCENT_SPEED = 1.5;
	public static final double K_CRESCENT_WIDTH = 1.9;
	public static final int K_CRESCENT_COOLDOWN = 30;
	/** G: Great Deep-Crimson Crescent Moon -- a huge one, two-handed from overhead. */
	public static final float K_GREAT_DAMAGE = 24f;
	public static final double K_GREAT_RANGE = 26.0;
	public static final double K_GREAT_SPEED = 1.3;
	public static final double K_GREAT_WIDTH = 4.2;
	public static final double K_GREAT_HEIGHT = 4.0;
	public static final int K_GREAT_WINDUP = 7;
	public static final int K_GREAT_COOLDOWN = 300;
	/** Z: Crimson Flash -- an iaido draw: he blurs forward, cutting everything he passes, and drinks the blood he draws. */
	public static final float K_FLASH_DAMAGE = 16f;
	public static final double K_FLASH_DISTANCE = 8.0;
	public static final int K_FLASH_TICKS = 3;
	public static final double K_FLASH_WIDTH = 1.4;
	/** He heals this much for each creature the draw cuts. */
	public static final float K_FLASH_HEAL = 2f;
	/** The cuts open (blood bursts out of the line) this long after he passes. */
	public static final int K_FLASH_DELAY = 6;
	public static final int K_FLASH_COOLDOWN = 120;
	/** C: Blood Whirl -- two full turns with a blood-coated blade, all round him. */
	public static final float K_WHIRL_DAMAGE = 10f;
	public static final double K_WHIRL_RADIUS = 4.5;
	/** The two hits land on these ticks of the spin. */
	public static final int K_WHIRL_HIT1 = 2, K_WHIRL_HIT2 = 7;
	public static final int K_WHIRL_TICKS = 11;
	public static final int K_WHIRL_COOLDOWN = 160;

	// ---------------------------------------------------------------- the shoulder sword

	/** A sword in his shoulder slows his regeneration: the wait before a part starts and its regrowth both take this much longer. */
	public static final float SHOULDER_REGEN_MULT = 1.1f;
}
