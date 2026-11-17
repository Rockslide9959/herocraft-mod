package com.projecthero.mod.sentry.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (v0.17.5 Void takeover rework 2): a batch of blocks that just started fading to black. {@code ticks} is the base
 * fade time (each position adds its own small share, {@code VoidMatter.decayTicks}, the same on both sides); {@code positions} are
 * packed {@code BlockPos.asLong} values. The client darkens each block over that time and dissolves it when the server turns it to air.
 */
public record VoidDecayPayload(int ticks, long[] positions) implements CustomPacketPayload {
	/** At most this many positions in one packet (8 bytes each). */
	public static final int MAX_BATCH = 2048;

	public static final CustomPacketPayload.Type<VoidDecayPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("void_decay"));

	public static final StreamCodec<RegistryFriendlyByteBuf, VoidDecayPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.ticks);
		buf.writeVarInt(p.positions.length);
		for (long l : p.positions) {
			buf.writeLong(l);
		}
	}, buf -> {
		int ticks = buf.readVarInt();
		int n = Math.min(buf.readVarInt(), MAX_BATCH);
		long[] pos = new long[n];
		for (int i = 0; i < n; i++) {
			pos[i] = buf.readLong();
		}
		return new VoidDecayPayload(ticks, pos);
	});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
