package com.projecthero.mod.quirk;

/** v0.16.7: the shared Quirk numbers (each kit has its own {@code <Kit>Config} for its moves). */
public final class QuirkConfig {
	private QuirkConfig() {
	}

	/** The server meter is copied to the synced state when it moved at least this much (or hit 0 / 100)... */
	public static final float METER_SYNC_STEP = 0.5f;
	/** ...and no more often than every this many ticks. */
	public static final int METER_SYNC_TICKS = 3;
	/** A key pressed again within this long with no release between is the OS key repeat (ignored). */
	public static final int REPEAT_WINDOW = 25;
	/** v0.16.7 (user): the first-spawn calling -- 5% a random Quirk (rolled after the ring and the Miraculous). */
	public static final double SPAWN_CHANCE = 0.05;
}
