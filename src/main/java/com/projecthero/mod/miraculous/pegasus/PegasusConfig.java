package com.projecthero.mod.miraculous.pegasus;

/**
 * v0.16.6: every Pegasus number (the Horseshoe, the portals, the six keys). Ticks unless the name says otherwise. The
 * shared transformed stats (armour, health, melee, speed, jump, no fall damage) come from the core
 * ({@code MiraculousStats}) and are not repeated here.
 */
public final class PegasusConfig {
	private PegasusConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	public static final String THROW = "pegasus_horseshoe_throw";
	public static final String VOLLEY = "pegasus_horseshoe_volley";
	public static final String KICK = "pegasus_hoof_kick";
	public static final String TRAMPLE = "pegasus_gallop_trample";
	public static final String HOP = "pegasus_portal_hop";
	public static final String GALLOP = "pegasus_gallop";
	public static final String TRAP = "pegasus_portal_trap";
	public static final String VOYAGE = "pegasus_voyage";
	public static final String SHIELD = "pegasus_portal_shield";
	public static final String SKY = "pegasus_sky_gallop";
	public static final String WAYPOINT = "pegasus_mark_waypoint";
	public static final String LINK = "pegasus_linked_portals";

	// ---------------------------------------------------------------- shared
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most {@link #BOSS_MAX_FRACTION_PER_HIT} a hit, are never knocked about and never portal-trapped. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	/** A landing spot must have a floor within this many blocks below it (never the void, never a long drop into nothing). */
	public static final int LANDING_FLOOR_SEARCH = 48;
	/** An entity that just came out of a portal can't go back in for this long. */
	public static final int PASS_COOLDOWN = 30;

	// ---------------------------------------------------------------- R: Horseshoe Throw / Shift+R: Horseshoe Volley
	public static final float THROW_DAMAGE = 12f;
	/** How far out the Horseshoe flies before it turns back. */
	public static final double THROW_RANGE = 18.0;
	/** Ticks of the outbound flight at full range (a nearer aim is reached sooner). */
	public static final int THROW_OUT_TICKS = 13;
	/** A target this close to the crosshair (degrees) is locked: the throw ends on it, following it if it moves. */
	public static final double THROW_ASSIST_DEGREES = 6.0;
	/** Coming back: blocks a tick. */
	public static final double THROW_RETURN_SPEED = 1.4;
	/** It gives up and pops back into the hand after this long. */
	public static final int THROW_MAX_TICKS = 80;
	/** Starts when the Horseshoe is caught (it can't be thrown while out anyway). */
	public static final int THROW_COOLDOWN = 30;
	public static final int VOLLEY_COUNT = 3;
	public static final float VOLLEY_DAMAGE = 7f;
	/** The fan's spread between neighbouring horseshoes, degrees. */
	public static final double VOLLEY_SPREAD_DEGREES = 12.0;
	public static final double VOLLEY_SPEED = 1.6;
	public static final int VOLLEY_LIFE = 16;
	public static final int VOLLEY_COOLDOWN = 60;

	// ---------------------------------------------------------------- G: Hoof Kick / Shift+G: Gallop Trample
	public static final float KICK_DAMAGE = 14f;
	/** Everything this close in front of you or behind you is kicked. */
	public static final double KICK_REACH = 3.0;
	public static final double KICK_KNOCKBACK = 2.4;
	public static final double KICK_LIFT = 0.55;
	public static final int KICK_COOLDOWN = 50;
	public static final double TRAMPLE_DISTANCE = 12.0;
	public static final int TRAMPLE_TICKS = 8;
	public static final float TRAMPLE_DAMAGE = 10f;
	/** Everything this close to the path is trampled (once each). */
	public static final double TRAMPLE_WIDTH = 1.5;
	public static final double TRAMPLE_KNOCKBACK = 1.0;
	public static final double TRAMPLE_LIFT = 0.45;
	public static final int TRAMPLE_COOLDOWN = 120;

	// ---------------------------------------------------------------- X: Portal Hop / Shift+X: Gallop
	public static final double HOP_RANGE = 20.0;
	/** The two portals stay open this long after the hop (just a flash; they carry nobody else). */
	public static final int HOP_PORTAL_TICKS = 24;
	public static final int HOP_COOLDOWN = 80;
	/** Gallop: Speed of this amplifier (2 = Speed III), refreshed while it is on. */
	public static final int GALLOP_SPEED_AMPLIFIER = 2;
	/** v0.16.7 (user): Gallop step assist, added to the 0.6 base step height so he steps up full blocks (1.1). */
	public static final double GALLOP_STEP_BONUS = 0.5;
	/** Pressing Gallop again within this long is ignored (on / off debounce). */
	public static final int GALLOP_COOLDOWN = 20;

	// ---------------------------------------------------------------- Z: Portal Trap / Shift+Z: Voyage
	public static final double TRAP_RANGE = 24.0;
	/** The trapped creature drops out of a portal this high above where it stood. */
	public static final int TRAP_HEIGHT = 20;
	/** Not enough sky above (a cave roof) and the drop would be shorter than this: refused. */
	public static final int TRAP_MIN_HEIGHT = 6;
	public static final int TRAP_PORTAL_TICKS = 30;
	public static final int TRAP_COOLDOWN = 300;
	/** The Voyage portal stays open this long. */
	public static final int VOYAGE_TICKS = 30 * 20;
	/** Its hexagon's radius (blocks): a ring three and a bit blocks tall. */
	public static final float VOYAGE_RADIUS = 1.7f;
	/** It opens this far ahead of you. */
	public static final double VOYAGE_DISTANCE = 2.5;
	/** The destination menu is good for this long (pick within 30 s). */
	public static final int VOYAGE_MENU_TICKS = 30 * 20;
	/** Starts when the portal opens. */
	public static final int VOYAGE_COOLDOWN = 2400;

	// ---------------------------------------------------------------- V: Portal Shield / Shift+V: Sky Gallop
	public static final int SHIELD_TICKS = 10 * 20;
	public static final float SHIELD_RADIUS = 1.2f;
	/** The shield portal hangs this far ahead of your eyes. */
	public static final double SHIELD_DISTANCE = 1.6;
	/** Spat-out projectiles fly back at least this fast (blocks a tick). */
	public static final double SHIELD_RETURN_SPEED = 1.8;
	/** Starts when the shield closes. */
	public static final int SHIELD_COOLDOWN = 300;
	public static final int SKY_TICKS = 10 * 20;
	/** Sneaking while Sky Galloping sinks you this fast (blocks a tick). */
	public static final double SKY_SINK_SPEED = 0.22;
	/** Starts when the Sky Gallop ends. */
	public static final int SKY_COOLDOWN = 400;

	// ---------------------------------------------------------------- C: Mark Waypoint / Shift+C: Linked Portals
	public static final int WAYPOINT_SLOTS = 3;
	public static final int WAYPOINT_COOLDOWN = 20;
	public static final double LINK_RANGE = 32.0;
	public static final int LINK_TICKS = 20 * 20;
	public static final float LINK_RADIUS = 1.1f;
	/** Starts when the portals close. */
	public static final int LINK_COOLDOWN = 400;

	// ---------------------------------------------------------------- the portals
	/** A portal opens (and closes) over this long: the ring grows out of a point of light. */
	public static final int PORTAL_OPEN_TICKS = 6;
	public static final float HOP_RADIUS = 1.1f;
	public static final float TRAP_RADIUS = 1.0f;
}
