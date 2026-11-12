package com.projecthero.mod.starfire.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server (v0.17.3 Starfire): the double-tap-Space flight toggle (the server re-checks everything). */
public record StarfireActionPayload(int action) implements CustomPacketPayload {
	public static final int TOGGLE_FLIGHT = 0;

	public static final CustomPacketPayload.Type<StarfireActionPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("starfire_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StarfireActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StarfireActionPayload::action, StarfireActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
