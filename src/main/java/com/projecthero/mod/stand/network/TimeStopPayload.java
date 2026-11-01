package com.projecthero.mod.stand.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2 server -&gt; every client: the running time stop ({@code end} = 0: none). The client freezes entities with it --
 * a Stand user's client all but the caster (and drains the colour), anyone else's EVERYTHING with no cue
 * ({@code TimeStopClient}). {@code radius} 0 = the whole dimension.
 */
public record TimeStopPayload(int casterId, String dimension, long end, double radius, double x, double y, double z)
		implements CustomPacketPayload {
	public static final TimeStopPayload NONE = new TimeStopPayload(-1, "", 0L, 0.0, 0.0, 0.0, 0.0);

	public static final CustomPacketPayload.Type<TimeStopPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("time_stop"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TimeStopPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.casterId);
		buf.writeUtf(p.dimension);
		buf.writeVarLong(p.end);
		buf.writeDouble(p.radius);
		buf.writeDouble(p.x);
		buf.writeDouble(p.y);
		buf.writeDouble(p.z);
	}, buf -> new TimeStopPayload(buf.readVarInt(), buf.readUtf(), buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
			buf.readDouble()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
