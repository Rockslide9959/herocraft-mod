package com.projecthero.mod.greenlantern.data;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.17.1 Ring Auto-Protection: what every viewer needs to draw the ring's automatic saves on a Lantern -- when the
 * Death Save shell flashed up, when (and at what height) the Fall Cushion pad flashed, and which of the lasting shells
 * (the Air Bubble over the head, the Heat Shell round the body) are up right now. Synced to everyone, never persisted.
 * Written only by {@code GreenLanternAutoProtect}, and only when something changed.
 *
 * @param shellStart   game time the Death Save shell went up (0 = never)
 * @param cushionStart game time the Fall Cushion pad flashed (0 = never)
 * @param cushionY     the pad's world height (the top of the ground it caught the Lantern above)
 * @param flags        {@link #BUBBLE} / {@link #HEAT}
 */
public record GreenLanternAutoProtectFx(long shellStart, long cushionStart, float cushionY, int flags) {
	/** The Air Bubble is over the Lantern's head. */
	public static final int BUBBLE = 1;
	/** The Heat Shell is round the Lantern. */
	public static final int HEAT = 2;

	public static final GreenLanternAutoProtectFx EMPTY = new GreenLanternAutoProtectFx(0L, 0L, 0f, 0);

	public static final StreamCodec<ByteBuf, GreenLanternAutoProtectFx> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public GreenLanternAutoProtectFx decode(ByteBuf buf) {
			return new GreenLanternAutoProtectFx(ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, GreenLanternAutoProtectFx fx) {
			ByteBufCodecs.VAR_LONG.encode(buf, fx.shellStart());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.cushionStart());
			ByteBufCodecs.FLOAT.encode(buf, fx.cushionY());
			ByteBufCodecs.VAR_INT.encode(buf, fx.flags());
		}
	};

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public GreenLanternAutoProtectFx withShell(long start) {
		return new GreenLanternAutoProtectFx(start, cushionStart, cushionY, flags);
	}

	public GreenLanternAutoProtectFx withCushion(long start, float y) {
		return new GreenLanternAutoProtectFx(shellStart, start, y, flags);
	}

	public GreenLanternAutoProtectFx withFlag(int flag, boolean on) {
		return new GreenLanternAutoProtectFx(shellStart, cushionStart, cushionY, on ? flags | flag : flags & ~flag);
	}
}
