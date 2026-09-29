package com.projecthero.mod.maxsteel.data;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.14.2: everything other players need to <em>see</em> a Max Steel doing that is not already in
 * {@link MaxSteelState} -- synced to everyone, never persisted (a relog simply has no animation running).
 *
 * <ul>
 *   <li>{@code anim}/{@code animStart} -- a one-shot pose ({@link #ANIM_SWAP}, {@link #ANIM_BLAST},
 *       {@link #ANIM_CANNON}) on the shared game-time clock.</li>
 *   <li>{@code blastChargeStart} / {@code cannonChargeStart} -- 0 when not charging, else the tick the charge began
 *       (drives the held aim pose, the energy orb in the hand, the arm cannon and the HUD bars).</li>
 *   <li>{@code cannonTarget} -- entity id the Turbo Cannon is locked onto, or -1 (the lock-on reticle).</li>
 *   <li>{@code swapFrom}/{@code swapStart} -- the mode the suit is reconfiguring <em>from</em> and when, so the new
 *       form's armour can rematerialise over the old one pixel by pixel.</li>
 * </ul>
 */
public record MaxSteelFx(int anim, long animStart, long blastChargeStart, long cannonChargeStart, int cannonTarget,
		int swapFrom, long swapStart) {
	public static final int ANIM_NONE = 0;
	/** Mode reconfiguration flex. */
	public static final int ANIM_SWAP = 1;
	/** Turbo Blast release recoil. */
	public static final int ANIM_BLAST = 2;
	/** Turbo Cannon discharge recoil. */
	public static final int ANIM_CANNON = 3;

	public static final MaxSteelFx EMPTY = new MaxSteelFx(ANIM_NONE, 0L, 0L, 0L, -1, -1, 0L);

	public static final StreamCodec<ByteBuf, MaxSteelFx> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public MaxSteelFx decode(ByteBuf buf) {
			return new MaxSteelFx(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, MaxSteelFx fx) {
			ByteBufCodecs.VAR_INT.encode(buf, fx.anim());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.animStart());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.blastChargeStart());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.cannonChargeStart());
			ByteBufCodecs.VAR_INT.encode(buf, fx.cannonTarget());
			ByteBufCodecs.VAR_INT.encode(buf, fx.swapFrom());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.swapStart());
		}
	};

	public MaxSteelFx withAnim(int id, long start) {
		return new MaxSteelFx(id, start, blastChargeStart, cannonChargeStart, cannonTarget, swapFrom, swapStart);
	}

	public MaxSteelFx withBlastCharge(long start) {
		return new MaxSteelFx(anim, animStart, start, cannonChargeStart, cannonTarget, swapFrom, swapStart);
	}

	public MaxSteelFx withCannonCharge(long start, int target) {
		return new MaxSteelFx(anim, animStart, blastChargeStart, start, target, swapFrom, swapStart);
	}

	public MaxSteelFx withSwap(int from, long start) {
		return new MaxSteelFx(anim, animStart, blastChargeStart, cannonChargeStart, cannonTarget, from, start);
	}
}
