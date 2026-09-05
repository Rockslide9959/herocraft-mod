package com.herocraft.mod.event;

/** Lifecycle of any {@link EventInstance}. */
public enum EventState {
	/** Created but not yet begun (announcement / countdown). */
	PENDING,
	RUNNING,
	/** Every participant has left the area; timers and spawning are frozen but nothing is destroyed. */
	PAUSED,
	COMPLETED,
	FAILED;

	public boolean finished() {
		return this == COMPLETED || this == FAILED;
	}

	public boolean active() {
		return this == PENDING || this == RUNNING || this == PAUSED;
	}
}
