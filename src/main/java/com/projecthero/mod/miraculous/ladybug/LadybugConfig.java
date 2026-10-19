package com.projecthero.mod.miraculous.ladybug;

/**
 * v0.16.6: every Ladybug number (Tikki's Miraculous, the Yo-yo kit). Ticks unless said otherwise. The shared transformed
 * stats (armour, health, melee, speed, jump, no fall damage) belong to the core ({@code MiraculousConfig}).
 */
public final class LadybugConfig {
	private LadybugConfig() {
	}

	// ---------------------------------------------------------------- R: Yo-yo Strike / Ricochet
	/** How far the Yo-yo whips out along the look. */
	public static final double STRIKE_RANGE = 12.0;
	public static final float STRIKE_DAMAGE = 10f;
	public static final int STRIKE_COOLDOWN = 16;
	/** Ticks out, then back (the client draws the throw from these). */
	public static final int STRIKE_OUT_TICKS = 3;
	public static final int STRIKE_BACK_TICKS = 4;
	public static final int RICOCHET_TARGETS = 4;
	/** Each bounce finds the next mob within this of the last one. */
	public static final double RICOCHET_HOP = 8.0;
	public static final float RICOCHET_DAMAGE = 7f;
	public static final int RICOCHET_COOLDOWN = 60;
	/** Ticks per bounce on the client. */
	public static final int RICOCHET_HOP_TICKS = 3;

	// ---------------------------------------------------------------- G: Yo-yo Bind / Lasso Fling
	public static final double BIND_RANGE = 16.0;
	/** Rooted this long (G again inside it yanks). */
	public static final int BIND_TICKS = 60;
	/** Bosses shrug a bind off this fast and are never yanked or flung. */
	public static final int BIND_BOSS_TICKS = 20;
	public static final int BIND_COOLDOWN = 120;
	/** The yank: pulled to stop this far in front of her. */
	public static final double YANK_STOP = 1.6;
	public static final float FLING_SPEED = 2.2f;
	public static final float FLING_LIFT = 0.55f;
	/** Landing (or a wall) after a fling: this, on top of any vanilla fall damage. */
	public static final float FLING_IMPACT_DAMAGE = 8f;
	/** A fling that never lands is forgotten after this. */
	public static final int FLING_MAX_TICKS = 80;
	public static final int FLING_COOLDOWN = 140;

	// ---------------------------------------------------------------- X: Yo-yo Swing / Zip Line
	/** v0.16.7 (user): 45 blocks (was 30). */
	public static final double SWING_RANGE = 45.0;
	/** v0.16.7 (user): the Zip Line reaches 75 blocks. */
	public static final double ZIP_RANGE = 75.0;
	/** After letting go. */
	public static final int SWING_COOLDOWN = 6;
	/** The line is dropped when she ends up this much further from the hook than its length. */
	public static final double SWING_SLACK_BREAK = 14.0;
	public static final double ZIP_SPEED = 1.6;
	/** The zip ends this close to the hook. */
	public static final double ZIP_ARRIVE = 1.8;
	public static final int ZIP_MAX_TICKS = 70; // 75 blocks at 1.6 a tick fits
	public static final int ZIP_COOLDOWN = 40;

	// ---------------------------------------------------------------- Z: Spinning Shield (held) / Lucky Charm
	/** The longest one hold lasts. */
	public static final int SHIELD_MAX_TICKS = 120;
	public static final int SHIELD_COOLDOWN = 40;
	/** Projectiles this close (and in front) are stopped. */
	public static final double SHIELD_REACH = 3.0;
	/** Front = within this of the look (dot product). */
	public static final double SHIELD_FRONT_DOT = 0.2;
	/** Melee from the front: this much of the hit gets through. */
	public static final float SHIELD_MELEE_PASS = 0.25f;
	public static final double SHIELD_KNOCKBACK = 1.4;

	/** Lucky Charm choice: an apple below this share of max health. */
	public static final float CHARM_LOW_HEALTH = 0.35f;
	/** ... spotted TNT with this many hostiles ... */
	public static final int CHARM_SURROUNDED = 5;
	/** ... within this. */
	public static final double CHARM_SURROUND_RADIUS = 8.0;
	/** ... a parachute while falling faster than this (blocks a tick) or this far already. */
	public static final double CHARM_FALL_SPEED = -0.5;
	public static final float CHARM_FALL_DISTANCE = 3f;
	/** ... a bow when the nearest threat is further than this, a sword otherwise. */
	public static final double CHARM_BOW_RANGE = 10.0;
	/** How high above her the charm appears and drops. */
	public static final double CHARM_DROP_HEIGHT = 6.0;
	/** Ladybug Vision after the charm. */
	public static final int VISION_TICKS = 200;
	public static final double VISION_RADIUS = 32.0;
	/** v0.16.7 (user): the spotted sword hits like a Sharpness V netherite sword (8 + 3 = 11). */
	public static final double CHARM_SWORD_DAMAGE = 11.0;
	/** v0.16.7 (user): the spotted bow's arrows hit like Power V (base 2 x 2.5 = 5). */
	public static final double CHARM_ARROW_DAMAGE = 5.0;
	/** v0.16.7 (user): the TNT is as strong as 10 TNT blocks -- 100 at the centre, half at the edge, 12 blocks round. */
	public static final float CHARM_TNT_DAMAGE = 100f;
	public static final double CHARM_TNT_RADIUS = 12.0;
	public static final double CHARM_TNT_KNOCKBACK = 2.5;
	/** v0.16.7 (user): the apple -- Regeneration II for a minute, Absorption V. */
	public static final int CHARM_APPLE_REGEN_TICKS = 1200;
	public static final int CHARM_APPLE_ABSORPTION_AMPLIFIER = 4;
	/** The parachute's fall: no faster than this. */
	public static final double CHUTE_FALL = -0.12;

	/** Miraculous Ladybug: blocks broken within this of a transformed Ladybug in the last {@link #RESTORE_WINDOW} come back. */
	public static final double RESTORE_RADIUS = 64.0;
	public static final int RESTORE_WINDOW = 5 * 60 * 20;
	/** Remembered at most (per dimension; the oldest go first). */
	public static final int RESTORE_MAX_TRACKED = 40000;
	/** Put back per tick (a wave out from her). */
	public static final int RESTORE_PER_TICK = 48;
	/** Squadmates this close heal to full too. */
	public static final double HEAL_RADIUS = 128.0;
	/** How long the swarm flies (client). */
	public static final int SWARM_TICKS = 120;

	// ---------------------------------------------------------------- V: Pole Vault / Trip Wire
	public static final double VAULT_UP = 1.3;
	public static final double VAULT_FORWARD = 1.0;
	public static final int VAULT_COOLDOWN = 60;
	public static final double WIRE_RANGE = 10.0;
	public static final int WIRE_TICKS = 400;
	public static final int WIRE_ROOT_TICKS = 40;
	public static final int WIRE_COOLDOWN = 200;

	// ---------------------------------------------------------------- C: Yo-yo Scan / Yo-yo Phone
	public static final double SCAN_RADIUS = 40.0;
	public static final int SCAN_TICKS = 200;
	public static final int SCAN_COOLDOWN = 300;
	public static final int PHONE_GLOW_TICKS = 200;
	/** The squadmates' compass marker. */
	public static final int PHONE_MARKER_TICKS = 600;
	public static final int PHONE_COOLDOWN = 200;
	/** The compact pose. */
	public static final int COMPACT_TICKS = 30;
}
