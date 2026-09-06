package com.projecthero.mod.spider;

/**
 * The rider's movement intent during a swing, packed into one byte.
 *
 * <p>Read entirely on the owning client, which is the only side that has it -- vanilla sends movement
 * <em>input</em> to the server only for vehicles. Nothing about this crosses the network: the rider's
 * keys shape their own arc, and the things a client must not be trusted with (the anchor, the web
 * reserve, the altitude limit) are all decided by the server from data it already has. That is why
 * swinging adds no per-tick packets of its own on top of ordinary player movement.
 */
public final class SpiderSwingInput {
	public static final int FORWARD = 1;
	public static final int BACK = 1 << 1;
	public static final int LEFT = 1 << 2;
	public static final int RIGHT = 1 << 3;
	public static final int JUMP = 1 << 4;
	public static final int SNEAK = 1 << 5;

	private SpiderSwingInput() {
	}

	public static int pack(boolean forward, boolean back, boolean left, boolean right,
			boolean jump, boolean sneak) {
		return (forward ? FORWARD : 0) | (back ? BACK : 0) | (left ? LEFT : 0) | (right ? RIGHT : 0)
				| (jump ? JUMP : 0) | (sneak ? SNEAK : 0);
	}

	public static boolean forward(int mask) {
		return (mask & FORWARD) != 0;
	}

	public static boolean back(int mask) {
		return (mask & BACK) != 0;
	}

	public static boolean left(int mask) {
		return (mask & LEFT) != 0;
	}

	public static boolean right(int mask) {
		return (mask & RIGHT) != 0;
	}

	public static boolean jump(int mask) {
		return (mask & JUMP) != 0;
	}

	public static boolean sneak(int mask) {
		return (mask & SNEAK) != 0;
	}
}
