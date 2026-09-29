package com.projecthero.mod.moonknight.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Moon Knight's live combat state ({@code projecthero:moon_knight_action}): what the cape is doing, which weapon is
 * out, the move being animated. NOT persistent (a relog or death starts clean) but synced to every client, because
 * other players must see the cape spread for a glide, raised for a block, the truncheon, and the pose.
 *
 * <p>{@link MoonKnightState} is at the 16-field codec ceiling, so everything that only matters while playing lives
 * here instead. Copy-on-write like every other hero state: {@link #copy()}, change, save.
 */
public final class MoonKnightAction {
	/** Cape Glide (jump + hold Sneak since v0.13.21) is active. */
	public static final int FLAG_GLIDING = 1;
	/** Cape Block (hold right click, v0.13.21; the old X-hold Cape Shroud): the cape is wrapped round the player. */
	public static final int FLAG_CAPE_BLOCK = 1 << 1;
	/** The Truncheon is summoned (C tap). */
	public static final int FLAG_TRUNCHEON = 1 << 2;
	/** The Truncheon is extended into a staff (C hold spin). */
	public static final int FLAG_STAFF = 1 << 3;
	/** A HOLD is charging (R fan, Z Eye of Khonshu): see {@link #chargeKey} / {@link #chargeStart}. */
	public static final int FLAG_CHARGING = 1 << 4;
	/** Mid transformation (bandages spiralling up). */
	public static final int FLAG_TRANSFORMING = 1 << 5;
	/** Diving (SNEAK+C in the air, or the G grapple kick). */
	public static final int FLAG_DIVING = 1 << 6;
	/** Fist of Khonshu (Marc) is active. */
	public static final int FLAG_FIST = 1 << 7;
	/** Eye of Khonshu is active on this player. */
	public static final int FLAG_EYE = 1 << 8;
	/** The alter radial picker is open (V hold). */
	public static final int FLAG_ALTER_PICKER = 1 << 9;
	/** The suit is dissolving away pixel by pixel after H (it is stripped when this ends). */
	public static final int FLAG_UNTRANSFORMING = 1 << 10;

	public int flags;
	/** {@code MoonKnightAnim} id and the game time it started. */
	public int animId;
	public long animStart;
	/** Which ability slot number (1..6) is charging, and since when. */
	public int chargeKey;
	public long chargeStart;
	/** Entity id of the current grapple line / reel target (-1 none), drawn as a rope by every client. */
	public int lineTargetId;
	/** Fixed grapple anchor when the line is fastened to a block. */
	public double lineX;
	public double lineY;
	public double lineZ;
	/** Game time the line was fired; the rope draws while it is non-negative. */
	public long lineStart;
	/**
	 * v0.13.21: the alter whose suit is being replaced (-1 none) and when the swap started -- every client draws the
	 * new alter's suit materialising pixel by pixel over the old one for {@code MoonKnightConfig.ALTER_SWAP_TICKS}.
	 */
	public int swapFrom;
	public long swapStart;

	public MoonKnightAction() {
		this(0, 0, 0L, 0, 0L, -1, 0.0, 0.0, 0.0, -1L, -1, 0L);
	}

	public MoonKnightAction(int flags, int animId, long animStart, int chargeKey, long chargeStart, int lineTargetId,
			double lineX, double lineY, double lineZ, long lineStart, int swapFrom, long swapStart) {
		this.flags = flags;
		this.animId = animId;
		this.animStart = animStart;
		this.chargeKey = chargeKey;
		this.chargeStart = chargeStart;
		this.lineTargetId = lineTargetId;
		this.lineX = lineX;
		this.lineY = lineY;
		this.lineZ = lineZ;
		this.lineStart = lineStart;
		this.swapFrom = swapFrom;
		this.swapStart = swapStart;
	}

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public MoonKnightAction with(int flag, boolean on) {
		MoonKnightAction c = copy();
		c.flags = on ? (flags | flag) : (flags & ~flag);
		return c;
	}

	public MoonKnightAction copy() {
		return new MoonKnightAction(flags, animId, animStart, chargeKey, chargeStart, lineTargetId, lineX, lineY, lineZ,
				lineStart, swapFrom, swapStart);
	}

	public static final Codec<MoonKnightAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("flags", 0).forGetter(s -> s.flags),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.INT.optionalFieldOf("charge_key", 0).forGetter(s -> s.chargeKey),
			Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart),
			Codec.INT.optionalFieldOf("line_target", -1).forGetter(s -> s.lineTargetId),
			Codec.DOUBLE.optionalFieldOf("line_x", 0.0).forGetter(s -> s.lineX),
			Codec.DOUBLE.optionalFieldOf("line_y", 0.0).forGetter(s -> s.lineY),
			Codec.DOUBLE.optionalFieldOf("line_z", 0.0).forGetter(s -> s.lineZ),
			Codec.LONG.optionalFieldOf("line_start", -1L).forGetter(s -> s.lineStart),
			Codec.INT.optionalFieldOf("swap_from", -1).forGetter(s -> s.swapFrom),
			Codec.LONG.optionalFieldOf("swap_start", 0L).forGetter(s -> s.swapStart)
	).apply(i, MoonKnightAction::new));
}
