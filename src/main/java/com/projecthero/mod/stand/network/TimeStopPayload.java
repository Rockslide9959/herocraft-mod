package com.projecthero.mod.stand.network;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2 server -&gt; every client: the running time stop ({@code end} = 0: none). The client freezes entities with it --
 * a Stand user's client all but the caster (and drains the colour), anyone else's EVERYTHING with no cue
 * ({@code TimeStopClient}). {@code radius} 0 = the whole dimension.
 * <p>
 * v0.17.4: {@code mobile} lists the players the stop doesn't freeze, each as {@code entityId * 4 + TimeStop.Mobility ordinal}
 * (1 walk, 2 sprint, 3 free): their own client keeps them moving (capped), and every perceiving client shows them moving.
 */
public record TimeStopPayload(int casterId, String dimension, long end, double radius, double x, double y, double z, List<Integer> mobile)
		implements CustomPacketPayload {
	public static final TimeStopPayload NONE = new TimeStopPayload(-1, "", 0L, 0.0, 0.0, 0.0, 0.0, List.of());

	public static final CustomPacketPayload.Type<TimeStopPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("time_stop"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TimeStopPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.casterId);
		buf.writeUtf(p.dimension);
		buf.writeVarLong(p.end);
		buf.writeDouble(p.radius);
		buf.writeDouble(p.x);
		buf.writeDouble(p.y);
		buf.writeDouble(p.z);
		buf.writeVarInt(p.mobile.size());
		for (int m : p.mobile) {
			buf.writeVarInt(m);
		}
	}, buf -> {
		int casterId = buf.readVarInt();
		String dimension = buf.readUtf();
		long end = buf.readVarLong();
		double radius = buf.readDouble();
		double x = buf.readDouble();
		double y = buf.readDouble();
		double z = buf.readDouble();
		int n = Math.min(buf.readVarInt(), 4096);
		List<Integer> mobile = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			mobile.add(buf.readVarInt());
		}
		return new TimeStopPayload(casterId, dimension, end, radius, x, y, z, List.copyOf(mobile));
	});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
