package com.projecthero.mod.flash;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.14.11: the Flash Suit's synced transition clock -- when it started ({@code start}, game time) and which way it runs
 * ({@code dir}: {@link #UP} the suit pours out of the ring onto the body, {@link #DOWN} it is pulled back into the ring).
 * Never persisted; every viewer reads it to play the same reveal, pose, spin and lightning.
 */
public record FlashFx(long start, int dir) {
	public static final int NONE = 0;
	public static final int UP = 1;
	public static final int DOWN = -1;
	public static final FlashFx EMPTY = new FlashFx(0L, NONE);

	public static final StreamCodec<RegistryFriendlyByteBuf, FlashFx> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, FlashFx::start,
			ByteBufCodecs.VAR_INT, FlashFx::dir,
			FlashFx::new);

	/** How long this transition lasts, in ticks (0 for none). */
	public int length() {
		return dir == UP ? FlashRing.SUIT_UP_TICKS : dir == DOWN ? FlashRing.SUIT_DOWN_TICKS : 0;
	}

	/** Ticks since it started (with {@code partial}). */
	public float age(long gameTime, float partial) {
		return gameTime - start + partial;
	}

	/** Whether it is still running at {@code gameTime}. */
	public boolean running(long gameTime) {
		return dir != NONE && gameTime - start < length();
	}
}
