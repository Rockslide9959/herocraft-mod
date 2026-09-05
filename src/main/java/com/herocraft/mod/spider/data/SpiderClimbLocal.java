package com.herocraft.mod.spider.data;

import net.minecraft.core.Direction;

/**
 * Per-player scratch state for the adhesion engine: which surface is held, how long the hold has
 * survived without a surface in reach, and whether a face change just happened.
 *
 * <p>Deliberately its own attachment ({@code herocraft:spider_climb_local}) rather than fields on
 * {@link SpiderManState}: it is rewritten every tick, and none of it is worth persisting or putting
 * on the wire. Keeping it out of the synced state is what lets the synced state be written only on a
 * genuine transition. Because it is an attachment rather than a static map it is owned by the player
 * entity, so it cannot outlive a world the way a {@code static} cache would (see
 * {@code ServerStateReset} for why that matters here).
 *
 * <p>Lives on both sides independently: the owning client keeps its own copy to drive movement, the
 * server keeps its own to drive the synced climb state.
 */
public final class SpiderClimbLocal {
	private Direction face;
	private int grace;
	private int transitionTicks;
	/** Ticks since the player last deliberately launched off a surface (jump), during which adhesion
	 *  will not immediately re-grab the same wall. */
	private int detachLock;
	/**
	 * Whether the player has <em>asked</em> to be stuck to surfaces right now (v0.6.6). Adhesion no
	 * longer engages just because a wall is in reach -- the player double-taps jump against a surface to
	 * grab it and double-taps sneak (or reaches the ground) to let go. This is the intent flag that
	 * gates {@link com.herocraft.mod.spider.SpiderClimb#updateAttachment}. Per-side, like the rest of
	 * this object: the owning client sets its own copy the instant the gesture fires and the server
	 * sets its copy from the {@code CLIMB_GRAB}/{@code CLIMB_RELEASE} packet.
	 */
	private boolean grabIntent;

	public Direction face() {
		return face;
	}

	public boolean grabIntent() {
		return grabIntent;
	}

	/** Set the grab intent. Turning it off also drops any currently held face immediately. */
	public void setGrabIntent(boolean intent) {
		this.grabIntent = intent;
		if (!intent) {
			reset();
		}
	}

	public int grace() {
		return grace;
	}

	public void attach(Direction d) {
		this.face = d;
		this.grace = 0;
	}

	public void tickGrace() {
		this.grace++;
	}

	public void reset() {
		this.face = null;
		this.grace = 0;
		this.transitionTicks = 0;
	}

	public void markTransition() {
		this.transitionTicks = 6;
	}

	/** True for a few ticks after the held surface changed -- movement eases rather than snaps. */
	public boolean transitioning() {
		return transitionTicks > 0;
	}

	public void tickTransition() {
		if (transitionTicks > 0) {
			transitionTicks--;
		}
	}

	/** Refuse to re-attach for a moment, so a deliberate leap off a wall actually leaves it. */
	public void lockOut(int ticks) {
		this.detachLock = ticks;
		reset();
	}

	public boolean lockedOut() {
		return detachLock > 0;
	}

	public void tickLock() {
		if (detachLock > 0) {
			detachLock--;
		}
	}
}
