package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.3: one alien's show transformation, the common (server) half -- how long it runs, when the body changes, what it sounds like.
 * The client half (camera, poses, stage, flashes) is a {@code ShowSequenceClient} named {@code <Pascal>Show} in
 * {@code com.projecthero.mod.client.omnitrix.sequence}. Guide: {@code docs/OMNITRIX_SEQUENCES.md}.
 * <p>
 * A subclass is named {@code <Pascal>Sequence} after its alien id ({@code grey_matter} -> {@code GreyMatterSequence}), lives in this
 * package, has a public no-argument constructor, and is picked up by {@link ShowSequences} on its own: nothing else to edit.
 * Tick 0 is the slap (the right-click); video second {@code v} of its clip is tick {@code (v - clipStart) * 20}.
 */
public abstract class ShowSequence {
	/** Sneaking skips from here by default (a sneak held from before the click does not cut it at once). */
	public static final int SKIP_FROM = 10;

	private final String alienId;
	private final int total;
	private final int transformAt;

	/**
	 * @param alienId     the alien's id ({@code Aliens.X.id()})
	 * @param total       ticks from the slap to the end (the world is back and the green has faded)
	 * @param transformAt the tick the body actually changes (hide it under a flash)
	 */
	protected ShowSequence(String alienId, int total, int transformAt) {
		this.alienId = alienId;
		this.total = total;
		this.transformAt = transformAt;
	}

	public final String alienId() {
		return alienId;
	}

	public final int total() {
		return total;
	}

	public final int transformAt() {
		return transformAt;
	}

	/** Sneaking skips the rest from this tick on. */
	public int skipFrom() {
		return SKIP_FROM;
	}

	/** The soundtrack, played on the wearer at tick 0 (pre-registered for the show aliens: {@code <id>_sequence.ogg}). */
	public OmnitrixSfx.Cue cue() {
		return OmnitrixSounds.sequenceCue(alienId);
	}

	/** The soundtrack's sound event (stopped when the sequence is cut off). */
	public SoundEvent sound() {
		return OmnitrixSounds.sequenceSound(alienId);
	}

	/** Every server tick while it plays ({@code t} = ticks since the slap), after the root / skip / change checks. Optional. */
	public void serverTick(ServerPlayer p, OmnitrixState s, long t) {
	}
}
