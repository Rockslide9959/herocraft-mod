package com.projecthero.mod.spider;

/**
 * v0.16.2: which swing animation a web line plays. The style is picked at random for every new line, but from synced
 * data only -- the line's exact anchor point and the rope length it was fired at, both written once by the server when
 * the line goes out -- so every viewer (and the owner) picks the same one without a new synced field. Two lines almost
 * never share an anchor point to the last bit (it is wherever the aim ray met the block), so consecutive swings vary.
 *
 * <p>The poses themselves are {@code client.spider.SpiderSwingPose}.
 */
public final class SpiderSwingStyles {
	/** One arm on the line, the other out for balance. */
	public static final int CLASSIC = 0;
	/** The free hand reaches up to the line beside the web hand (fast lines). */
	public static final int TWO_HAND = 1;
	/** Knees hugged through the bottom of the arc. */
	public static final int CANNONBALL = 2;
	/** One arm; a long fast swing ends in a corkscrew. */
	public static final int SPIN = 3;
	/** v0.16.2: both hands high on the line, body hanging straight, knees tucked on the upswing (a pull-up). */
	public static final int HANG_PULL = 4;
	/** v0.16.2: hands stacked one above the other, body leaning back, legs out front like a rope swing. */
	public static final int CLIMBER = 5;
	public static final int COUNT = 6;

	private SpiderSwingStyles() {
	}

	/** The style for the line fired at ({@code ax, ay, az}) with rope length {@code rope}: uniform over all styles. */
	public static int pick(double ax, double ay, double az, double rope) {
		long h = Double.doubleToLongBits(ax) * 0x9E3779B97F4A7C15L;
		h ^= Long.rotateLeft(Double.doubleToLongBits(ay) * 0xC2B2AE3D27D4EB4FL, 21);
		h ^= Long.rotateLeft(Double.doubleToLongBits(az) * 0x165667B19E3779F9L, 42);
		h ^= Double.doubleToLongBits(rope) * 0xD6E8FEB86659FD93L;
		return (int) Long.remainderUnsigned(mix(h), COUNT);
	}

	/** Both hands grip the line. */
	public static boolean twoHanded(int style) {
		return style == TWO_HAND || style == HANG_PULL || style == CLIMBER;
	}

	/** SplitMix64's finaliser: every input bit reaches every output bit. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
