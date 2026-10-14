package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server (v0.16.2): this player's Physics Flight attitude -- Physics mode on / off and the current roll in
 * degrees -- so other players see them bank. Sanitised and shared in {@link com.projecthero.mod.flight.FlightAttitude#handle}.
 */
public record FlightAttitudePayload(boolean physics, float roll) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FlightAttitudePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "flight_attitude"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FlightAttitudePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.physics);
				buf.writeFloat(p.roll);
			},
			buf -> new FlightAttitudePayload(buf.readBoolean(), buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
