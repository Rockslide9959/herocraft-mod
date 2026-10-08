package com.projecthero.mod.greenlantern.data;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.14.3: what every viewer needs to animate a Green Lantern -- the last one-shot move (id + start tick), the moves
 * being channelled right now (bit flags) and when a ring removal started. Synced to everyone, never persisted: the
 * client pose code ({@code GreenLanternPose}), the hand renderer (the Gatling on the fist) and the HUD all read it.
 * v0.15.15: plus when the Power Battery charge ritual started ({@link #CH_CHARGE}).
 */
public record GreenLanternFx(int anim, long animStart, int channels, long ringRemoveStart, long chargeStart) {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_BOLT = 1;
	public static final int ANIM_FIST = 2;
	public static final int ANIM_HAMMER = 3;
	public static final int ANIM_MISSILES = 4;
	/** Shaping a construct: the ring arm reaches out, the free hand spreads. */
	public static final int ANIM_CONSTRUCT = 5;
	public static final int ANIM_DOME = 6;
	public static final int ANIM_THROW = 7;
	public static final int ANIM_GRAB = 8;
	/** The Oath completed -- the ring fist punched to the sky. */
	public static final int ANIM_OATH = 9;
	public static final int ANIM_SCAN = 10;
	/** v0.15.15: Shift+R Blast Wave -- both palms thrust forward. */
	public static final int ANIM_BLAST = 11;
	/** v0.15.15: the Power Battery charge just completed -- the ring fist thrown up in a flash. */
	public static final int ANIM_CHARGED = 12;

	/** Continuous Beam is being channelled. */
	public static final int CH_BEAM = 1;
	/** The Emerald Gatling is spinning. */
	public static final int CH_GATLING = 2;
	/** The Giant Hand is holding something. */
	public static final int CH_HAND = 4;
	/** Shift + N held: taking the ring off. */
	public static final int CH_RING_REMOVE = 8;
	/** v0.15.15: charging the ring at the Power Battery held in the off hand (since {@link #chargeStart}). */
	public static final int CH_CHARGE = 16;

	public static final GreenLanternFx EMPTY = new GreenLanternFx(ANIM_NONE, 0L, 0, 0L, 0L);

	public static final StreamCodec<ByteBuf, GreenLanternFx> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public GreenLanternFx decode(ByteBuf buf) {
			return new GreenLanternFx(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, GreenLanternFx fx) {
			ByteBufCodecs.VAR_INT.encode(buf, fx.anim());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.animStart());
			ByteBufCodecs.VAR_INT.encode(buf, fx.channels());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.ringRemoveStart());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.chargeStart());
		}
	};

	public boolean has(int channel) {
		return (channels & channel) != 0;
	}

	public GreenLanternFx withAnim(int id, long start) {
		return new GreenLanternFx(id, start, channels, ringRemoveStart, chargeStart);
	}

	public GreenLanternFx withChannel(int channel, boolean on) {
		return new GreenLanternFx(anim, animStart, on ? channels | channel : channels & ~channel, ringRemoveStart, chargeStart);
	}

	public GreenLanternFx withRingRemove(long start) {
		int ch = start != 0L ? channels | CH_RING_REMOVE : channels & ~CH_RING_REMOVE;
		return new GreenLanternFx(anim, animStart, ch, start, chargeStart);
	}

	/** v0.15.15: {@code start} != 0 = charging since then (sets {@link #CH_CHARGE}); 0 = not charging. */
	public GreenLanternFx withCharge(long start) {
		int ch = start != 0L ? channels | CH_CHARGE : channels & ~CH_CHARGE;
		return new GreenLanternFx(anim, animStart, ch, ringRemoveStart, start);
	}
}
