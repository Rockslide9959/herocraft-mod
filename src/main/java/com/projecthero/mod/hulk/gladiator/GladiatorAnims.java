package com.projecthero.mod.hulk.gladiator;

/**
 * v0.15.3: the Gladiator Hulk's one-shot animation ids, played through the Hulk's synced
 * {@link com.projecthero.mod.hulk.data.HulkState#animId} / {@code animStart} clock like the bare-handed kit's. Numbered
 * from 40 so they never collide with {@code HulkState.ANIM_*} (0-9). {@link #ticks} is how long each clip owns the body
 * after it starts (a touch longer than the clip); 0 = held while a condition lasts (airborne, spinning, pinning).
 */
public final class GladiatorAnims {
	public static final int AXE_CLEAVE = 40;
	public static final int HAMMER_UPPERCUT = 41;
	public static final int HAMMER_QUAKE = 42;
	public static final int EARTHSPLITTER = 43;
	public static final int CHAMPIONS_ROAR = 44;
	public static final int WEAPON_CLASH = 45;
	/** In the air after Arena Leap (held until he lands). */
	public static final int ARENA_LEAP = 46;
	/** Rising, then diving, in Meteor Dive (held until he lands). */
	public static final int METEOR_DIVE = 47;
	/** The weapons-first landing of Arena Leap / Meteor Dive and the end of the Whirlwind. */
	public static final int SLAM = 48;
	public static final int AXE_THROW = 49;
	public static final int HAMMER_HURL = 50;
	/** Arm out, calling the hammer home. */
	public static final int HAMMER_RECALL = 51;
	/** The Whirlwind spin (loops while it lasts). */
	public static final int WHIRLWIND = 52;
	/** Pinning a grappled target and pounding it (loops while it lasts). */
	public static final int GRAPPLE = 53;

	private GladiatorAnims() {
	}

	/** Ticks a one-shot clip owns the body; 0 for the held / looping ones. */
	public static int ticks(int anim) {
		return switch (anim) {
			case AXE_CLEAVE -> 14;
			case HAMMER_UPPERCUT -> 14;
			case HAMMER_QUAKE -> 22;
			case EARTHSPLITTER -> 22;
			case CHAMPIONS_ROAR -> 30;
			case WEAPON_CLASH -> 18;
			case SLAM -> 16;
			case AXE_THROW -> 12;
			case HAMMER_HURL -> 14;
			case HAMMER_RECALL -> 12;
			default -> 0;
		};
	}

	public static boolean isGladiator(int anim) {
		return anim >= AXE_CLEAVE && anim <= GRAPPLE;
	}
}
