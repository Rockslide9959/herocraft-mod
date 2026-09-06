package com.projecthero.mod.spider.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Every bit of the Spider-Man Hero-Class power, in one object under one namespaced attachment key
 * ({@code projecthero:spider_man_state}). Deliberately isolated from every Thor attachment, from
 * {@link com.projecthero.mod.hero.data.ExperimentalState} and from
 * {@link com.projecthero.mod.ironman.data.TonyStarkState}, exactly like those two are isolated from
 * each other -- the four permanent-power stores can never be read as one another.
 *
 * <p>Synced to <em>everyone</em> (not target-only): other players' clients need {@link #climbState}
 * to draw a wall/ceiling-crawling Spider-Man in the right orientation, and
 * {@link #swinging}/{@link #anchorX} to draw his web line. The server stays authoritative -- every
 * gameplay check runs against the server-side attachment, so a modified client seeing this data
 * cannot fake the power, the reserve or a dodge.
 *
 * <p><b>Sync budget.</b> A Fabric attachment resyncs on every {@code setAttached}, so the mutators in
 * {@link com.projecthero.mod.spider.SpiderMan} only re-save when something a viewer can actually see
 * changed: the web reserve is written at whole-point granularity, and the swing/climb fields only on
 * a state transition. Nothing here is written every tick.
 */
public final class SpiderManState {
	// ---- climb state packing (field 6) ----
	public static final int CLIMB_NONE = 0;
	public static final int CLIMB_WALL = 1;
	public static final int CLIMB_CEILING = 2;

	/** Whether the player has the permanent Spider-Man Hero-Class power. */
	public boolean hasPower;
	/** Organic webbing, 0..{@link com.projecthero.mod.spider.SpiderWebReserve#MAX}. */
	public float webReserve = com.projecthero.mod.spider.SpiderWebReserve.MAX;
	/** Absolute game-time of the last <em>major</em> web ability; regeneration waits a moment after it. */
	public long lastWebUseTick;
	/** Absolute game-time the mid-air double jump is available again. */
	public long doubleJumpReadyAt;
	/** {@code abilityId} -> absolute game-time that ability is ready again (survives relog/death/dim). */
	public final Map<String, Long> abilityReadyAt;

	/**
	 * Packed surface attachment: {@code mode | (faceIndex << 4)}, where mode is one of
	 * {@link #CLIMB_NONE} / {@link #CLIMB_WALL} / {@link #CLIMB_CEILING} and faceIndex is the
	 * {@link net.minecraft.core.Direction#get3DDataValue()} of the surface the player is stuck to
	 * (i.e. the direction from the player <em>into</em> the surface). Packed into one int so the
	 * record codec stays comfortably inside its 16-field ceiling.
	 */
	public int climbState;

	/** True while a web line is attached to {@link #anchorX}/{@link #anchorY}/{@link #anchorZ}. */
	public boolean swinging;
	public double anchorX;
	public double anchorY;
	public double anchorZ;
	/** Current rope length in blocks. Reeled in with jump, let out with sneak. */
	public double ropeLength;
	/**
	 * The altitude an <em>artificial</em> (no-terrain) swing sequence started from. Artificial anchors
	 * stop lifting once the player is {@code SpiderSwing.AIR_SWING_CEILING} above this; real terrain
	 * anchors raise it naturally as the player climbs real geometry. This is what keeps air swinging
	 * from turning into free flight.
	 */
	public double airSwingBaselineY;
	/** True when the current anchor is a fabricated one rather than a real block. */
	public boolean artificialAnchor;

	/**
	 * v0.6.17: whether the toggleable wall-crawl mode is on (slot 6 / C, which replaced Web Cocoon).
	 * While set, touching a climbable surface in mid-air engages adhesion automatically -- no
	 * double-tap-jump needed -- and the double-tap gestures still work on top of it. Synced so the
	 * client movement code and the server agree.
	 */
	public boolean wallCrawlEnabled;
	/**
	 * v0.6.17: which hand the current swing's web left from. Flipped on every {@link
	 * com.projecthero.mod.spider.SpiderSwing#fire} so consecutive swings visibly alternate hands, and
	 * read by {@code SpiderWebLineRenderer} / {@code HumanoidModelMixin} to raise the matching arm.
	 */
	public boolean swingHandRight;

	public SpiderManState() {
		this(false, com.projecthero.mod.spider.SpiderWebReserve.MAX, 0L, 0L, new HashMap<>(), 0,
				false, 0.0, 0.0, 0.0, 0.0, 0.0, false, false, false);
	}

	public SpiderManState(boolean hasPower, float webReserve, long lastWebUseTick, long doubleJumpReadyAt,
			Map<String, Long> abilityReadyAt, int climbState, boolean swinging,
			double anchorX, double anchorY, double anchorZ, double ropeLength, double airSwingBaselineY,
			boolean artificialAnchor, boolean wallCrawlEnabled, boolean swingHandRight) {
		this.hasPower = hasPower;
		this.webReserve = webReserve;
		this.lastWebUseTick = lastWebUseTick;
		this.doubleJumpReadyAt = doubleJumpReadyAt;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.climbState = climbState;
		this.swinging = swinging;
		this.anchorX = anchorX;
		this.anchorY = anchorY;
		this.anchorZ = anchorZ;
		this.ropeLength = ropeLength;
		this.airSwingBaselineY = airSwingBaselineY;
		this.artificialAnchor = artificialAnchor;
		this.wallCrawlEnabled = wallCrawlEnabled;
		this.swingHandRight = swingHandRight;
	}

	public SpiderManState copy() {
		return new SpiderManState(hasPower, webReserve, lastWebUseTick, doubleJumpReadyAt, abilityReadyAt,
				climbState, swinging, anchorX, anchorY, anchorZ, ropeLength, airSwingBaselineY, artificialAnchor,
				wallCrawlEnabled, swingHandRight);
	}

	public int climbMode() {
		return climbState & 0xF;
	}

	/** {@link net.minecraft.core.Direction#get3DDataValue()} of the attached surface, or -1. */
	public int climbFace() {
		return climbMode() == CLIMB_NONE ? -1 : (climbState >> 4) & 0xF;
	}

	public static int packClimb(int mode, int faceIndex) {
		return mode == CLIMB_NONE ? 0 : ((mode & 0xF) | ((faceIndex & 0xF) << 4));
	}

	public static final Codec<SpiderManState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("web_reserve", com.projecthero.mod.spider.SpiderWebReserve.MAX)
					.forGetter(s -> s.webReserve),
			Codec.LONG.optionalFieldOf("last_web_use_tick", 0L).forGetter(s -> s.lastWebUseTick),
			Codec.LONG.optionalFieldOf("double_jump_ready_at", 0L).forGetter(s -> s.doubleJumpReadyAt),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.INT.optionalFieldOf("climb_state", 0).forGetter(s -> s.climbState),
			Codec.BOOL.optionalFieldOf("swinging", false).forGetter(s -> s.swinging),
			Codec.DOUBLE.optionalFieldOf("anchor_x", 0.0).forGetter(s -> s.anchorX),
			Codec.DOUBLE.optionalFieldOf("anchor_y", 0.0).forGetter(s -> s.anchorY),
			Codec.DOUBLE.optionalFieldOf("anchor_z", 0.0).forGetter(s -> s.anchorZ),
			Codec.DOUBLE.optionalFieldOf("rope_length", 0.0).forGetter(s -> s.ropeLength),
			Codec.DOUBLE.optionalFieldOf("air_swing_baseline_y", 0.0).forGetter(s -> s.airSwingBaselineY),
			Codec.BOOL.optionalFieldOf("artificial_anchor", false).forGetter(s -> s.artificialAnchor),
			Codec.BOOL.optionalFieldOf("wall_crawl_enabled", false).forGetter(s -> s.wallCrawlEnabled),
			Codec.BOOL.optionalFieldOf("swing_hand_right", false).forGetter(s -> s.swingHandRight)
	).apply(instance, SpiderManState::new));
}
