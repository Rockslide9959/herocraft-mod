package com.projecthero.mod.spider.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.16.1: Spider-Man's live move state ({@code projecthero:spider_move_state}) -- the move being animated, the held
 * stances (Weave Mode, the counter stance, an Impact Web charge, a swing-throw, hanging under a tightrope), the
 * tightrope's first anchor and the web being woven. NOT persistent (a relog or death starts clean) but synced to every
 * client: other players must see the poses, and the owner's HUD reads the anchor / weave status from here.
 *
 * <p>{@link SpiderManState} is at the 16-field codec ceiling, so everything that only matters while playing lives here.
 * Copy-on-write like every other hero state: {@link #copy()}, change, save.
 */
public final class SpiderMoveState {
	/** Weave Mode is open (C held): left-clicks shoot radial strands from the centre. */
	public static final int FLAG_WEAVING = 1;
	/** The Spider-Sense counter stance (hold X) is waiting for a hit. */
	public static final int FLAG_COUNTER = 1 << 1;
	/** An Impact Web is charging (hold R). */
	public static final int FLAG_CHARGING_IMPACT = 1 << 2;
	/** A Web Swing-Throw (hold Z) is whirling something round. */
	public static final int FLAG_SWING_THROW = 1 << 3;
	/** Hanging under a tightrope (Sneak while standing on it). */
	public static final int FLAG_HANGING = 1 << 4;
	/** The Tightrope's anchor A is set (see {@link #ax}). */
	public static final int FLAG_ANCHOR_A = 1 << 5;
	/** Web Blossom is charging (hold V). */
	public static final int FLAG_CHARGING_BLOSSOM = 1 << 6;
	/** The uppercut's air-combo window is open. */
	public static final int FLAG_AIR_COMBO = 1 << 7;
	/** Ledge perch: sneaking at a drop (or on a thin post) -- the comic-book crouch. */
	public static final int FLAG_PERCH = 1 << 8;

	public int flags;
	/** {@code SpiderAnim} id and the game time it started. */
	public int animId;
	public long animStart;
	/** Tightrope anchor A. */
	public double ax;
	public double ay;
	public double az;
	/** Weave Mode: the centre of the web being woven, and how many radial strands it has so far. */
	public double cx;
	public double cy;
	public double cz;
	public int strands;
	/** The Spider-Sense Pulse highlight (owner's client only draws it) runs until this game time. */
	public long pulseUntil;
	/** When the current charge (Impact Web / Web Blossom) or stance started. */
	public long chargeStart;
	/** v0.16.1: the Spider-Sense tingle lines show round his head (for everyone) until this game time. */
	public long tingleUntil;

	public SpiderMoveState() {
	}

	public SpiderMoveState(int flags, int animId, long animStart, double ax, double ay, double az,
			double cx, double cy, double cz, int strands, long pulseUntil, long chargeStart, long tingleUntil) {
		this.flags = flags;
		this.animId = animId;
		this.animStart = animStart;
		this.ax = ax;
		this.ay = ay;
		this.az = az;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.strands = strands;
		this.pulseUntil = pulseUntil;
		this.chargeStart = chargeStart;
		this.tingleUntil = tingleUntil;
	}

	public SpiderMoveState copy() {
		return new SpiderMoveState(flags, animId, animStart, ax, ay, az, cx, cy, cz, strands, pulseUntil, chargeStart, tingleUntil);
	}

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public void set(int flag, boolean on) {
		flags = on ? flags | flag : flags & ~flag;
	}

	public static final Codec<SpiderMoveState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("flags", 0).forGetter(s -> s.flags),
			Codec.INT.optionalFieldOf("anim", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.DOUBLE.optionalFieldOf("ax", 0.0).forGetter(s -> s.ax),
			Codec.DOUBLE.optionalFieldOf("ay", 0.0).forGetter(s -> s.ay),
			Codec.DOUBLE.optionalFieldOf("az", 0.0).forGetter(s -> s.az),
			Codec.DOUBLE.optionalFieldOf("cx", 0.0).forGetter(s -> s.cx),
			Codec.DOUBLE.optionalFieldOf("cy", 0.0).forGetter(s -> s.cy),
			Codec.DOUBLE.optionalFieldOf("cz", 0.0).forGetter(s -> s.cz),
			Codec.INT.optionalFieldOf("strands", 0).forGetter(s -> s.strands),
			Codec.LONG.optionalFieldOf("pulse_until", 0L).forGetter(s -> s.pulseUntil),
			Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart),
			Codec.LONG.optionalFieldOf("tingle_until", 0L).forGetter(s -> s.tingleUntil)
	).apply(i, SpiderMoveState::new));
}
