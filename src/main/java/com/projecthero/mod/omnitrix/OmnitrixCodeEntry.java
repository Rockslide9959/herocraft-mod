package com.projecthero.mod.omnitrix;

/**
 * v0.17.5: the six-slot code being typed into the watch's function mode (Alien Evo style). Pure logic, no game state: the
 * client keeps one while the watch is in function mode and sends only the finished code ({@code OmnitrixActionPayload.CODE});
 * the gametests drive the same class.
 * <ul>
 *   <li>{@link #scroll} -- the scroll wheel changes the digit in the current slot, 0-9, wrapping (up = +1);</li>
 *   <li>{@link #advance} -- the middle mouse button moves to the next slot; on the sixth it submits (returns the code) and goes
 *       back to the first slot;</li>
 *   <li>{@link #back} -- Shift + middle mouse button goes back one slot (it stops at the first).</li>
 * </ul>
 */
public final class OmnitrixCodeEntry {
	public static final int LENGTH = OmnitrixConfig.CODE_LENGTH;

	private final int[] digits = new int[LENGTH];
	private int slot;

	public int slot() {
		return slot;
	}

	public int digit(int i) {
		return digits[i];
	}

	/** Turns the current slot's digit by {@code dir} (+1 / -1), wrapping 9 -> 0 and 0 -> 9. */
	public void scroll(int dir) {
		if (dir != 0) {
			digits[slot] = Math.floorMod(digits[slot] + Integer.signum(dir), 10);
		}
	}

	/** The next slot. On the last slot the code is confirmed: returned (and the cursor goes back to slot 1); otherwise null. */
	public String advance() {
		if (slot >= LENGTH - 1) {
			slot = 0;
			return code();
		}
		slot++;
		return null;
	}

	/** Back one slot (stays on the first). */
	public void back() {
		slot = Math.max(0, slot - 1);
	}

	/** All six digits as a string ("042907"). */
	public String code() {
		StringBuilder b = new StringBuilder(LENGTH);
		for (int d : digits) {
			b.append((char) ('0' + d));
		}
		return b.toString();
	}

	/** Sets the digits (a code string; anything else is ignored) and puts the cursor on the first slot. */
	public void set(String code) {
		if (valid(code)) {
			for (int i = 0; i < LENGTH; i++) {
				digits[i] = code.charAt(i) - '0';
			}
		}
		slot = 0;
	}

	/** Every digit back to 0, the cursor on the first slot. */
	public void reset() {
		java.util.Arrays.fill(digits, 0);
		slot = 0;
	}

	/**
	 * v0.17.5 (user: "After putting in a working code the inputted code should get reset to 000000"): what the entry does when the
	 * server answers a submitted code. An accepted code (Master Control on / off, a hidden alien) goes back to 000000 on slot 1; a
	 * wrong one keeps its digits (the Mastermind hints are read against them). True if it was reset.
	 */
	public boolean afterResult(OmnitrixFunction.Outcome o) {
		if (o == OmnitrixFunction.Outcome.MASTER || o == OmnitrixFunction.Outcome.ALIEN) {
			reset();
			return true;
		}
		return false;
	}

	/** Exactly six ASCII digits. */
	public static boolean valid(String code) {
		if (code == null || code.length() != LENGTH) {
			return false;
		}
		for (int i = 0; i < LENGTH; i++) {
			char c = code.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}
}
