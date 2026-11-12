package com.projecthero.mod.shazam.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server (v0.17.3 Shazam): double-tap Space in the air -- take off / drop out of flight (the server re-checks). */
public record ShazamFlightPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShazamFlightPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("shazam_flight"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShazamFlightPayload> CODEC = StreamCodec.unit(new ShazamFlightPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
