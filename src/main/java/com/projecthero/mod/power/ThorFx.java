package com.projecthero.mod.power;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.14.4: what every viewer needs to animate Thor -- the last one-shot move (id, start tick and the world point it
 * landed on), the moves being channelled right now (bit flags, plus when the Wrath charge began) and the Thor's
 * Armour suit clock (direction + start tick). Synced to everyone, never persisted: the client pose code
 * ({@code ThorPose}), the effect renderer ({@code ThorFxRenderer}) and the piece-by-piece suit reveal
 * ({@code ThorSuitReveal}) all read it. Written only through {@link ThorVisuals}.
 */
public record ThorFx(int anim, long animStart, double x, double y, double z, int channels, long chargeStart,
		int suitDir, long suitStart) {
	public static final int ANIM_NONE = 0;
	/** Lightning Strike: Mjolnir to the sky, then brought down on the target. Point = the strike. */
	public static final int ANIM_STRIKE = 1;
	/** Thunderclap: a two-handed overhead slam into the ground. Point = the caster's feet. */
	public static final int ANIM_THUNDERCLAP = 2;
	/** Chain Lightning: the hammer thrust out along the aim, the free hand flung wide. */
	public static final int ANIM_CHAIN = 3;
	/** Throw: a wind-up and follow-through (the hammer has already left the hand). */
	public static final int ANIM_THROW = 4;
	/** Hammer Volley: Mjolnir flung up and out, the free hand directing it. */
	public static final int ANIM_VOLLEY = 5;
	/** God of Thunder's Wrath released: the charged hammer brought down. Point = the impact. */
	public static final int ANIM_WRATH = 6;
	/** Storm Call: the hammer raised to the sky. */
	public static final int ANIM_STORM = 7;

	/** Lightning Beam is being channelled. */
	public static final int CH_BEAM = 1;
	/** God of Thunder's Wrath is charging (since {@link #chargeStart}). */
	public static final int CH_WRATH = 2;

	public static final int SUIT_NONE = 0;
	public static final int SUIT_UP = 1;
	public static final int SUIT_DOWN = 2;

	public static final ThorFx EMPTY = new ThorFx(ANIM_NONE, 0L, 0.0, 0.0, 0.0, 0, 0L, SUIT_NONE, 0L);

	public static final StreamCodec<ByteBuf, ThorFx> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public ThorFx decode(ByteBuf buf) {
			return new ThorFx(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, ThorFx fx) {
			ByteBufCodecs.VAR_INT.encode(buf, fx.anim());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.animStart());
			ByteBufCodecs.DOUBLE.encode(buf, fx.x());
			ByteBufCodecs.DOUBLE.encode(buf, fx.y());
			ByteBufCodecs.DOUBLE.encode(buf, fx.z());
			ByteBufCodecs.VAR_INT.encode(buf, fx.channels());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.chargeStart());
			ByteBufCodecs.VAR_INT.encode(buf, fx.suitDir());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.suitStart());
		}
	};

	public boolean has(int channel) {
		return (channels & channel) != 0;
	}

	public ThorFx withAnim(int id, long start, double px, double py, double pz) {
		return new ThorFx(id, start, px, py, pz, channels, chargeStart, suitDir, suitStart);
	}

	public ThorFx withChannel(int channel, boolean on, long start) {
		int ch = on ? channels | channel : channels & ~channel;
		long charge = channel == CH_WRATH && on ? start : chargeStart;
		return new ThorFx(anim, animStart, x, y, z, ch, charge, suitDir, suitStart);
	}

	public ThorFx withSuit(int dir, long start) {
		return new ThorFx(anim, animStart, x, y, z, channels, chargeStart, dir, start);
	}
}
