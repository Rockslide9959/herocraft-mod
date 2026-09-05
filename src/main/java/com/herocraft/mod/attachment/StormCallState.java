package com.herocraft.mod.attachment;

/**
 * Tracks an active Storm Call: when it ends, and when its next random lightning strike fires.
 * Not persisted -- an active storm not surviving a server restart is an acceptable simplification.
 */
public final class StormCallState {
	public long activeUntilTick = 0L;
	public long nextStrikeTick = 0L;
}
