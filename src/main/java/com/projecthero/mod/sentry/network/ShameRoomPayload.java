package com.projecthero.mod.sentry.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (v0.17.5 Sentry): a room of the Shame Rooms is over. {@code room} is the room the client played (0..2),
 * {@code won} whether the Light bar filled, {@code clicks} how many lights were caught (a loose plausibility check). The server
 * decides what happens ({@code ShameRooms.report}).
 */
public record ShameRoomPayload(int room, boolean won, int clicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShameRoomPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("sentry_shame_room"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShameRoomPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.room);
		buf.writeBoolean(p.won);
		buf.writeVarInt(p.clicks);
	}, buf -> new ShameRoomPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
