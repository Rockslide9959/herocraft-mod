package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.symbiote.SymbioteColours;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (v0.17.5): the Symbiote suit colours picked on the colour screen. Re-validated on the server
 * ({@code SymbioteSuitColours.apply}): every channel is masked to a plain RGB and the player must be bonded.
 */
public record SymbioteColourPayload(SymbioteColours colours) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SymbioteColourPayload> TYPE =
			new CustomPacketPayload.Type<>(ProjectHeroMod.id("symbiote_colours"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SymbioteColourPayload> CODEC = StreamCodec.of(
			(buf, p) -> SymbioteColours.STREAM_CODEC.encode(buf, p.colours),
			buf -> new SymbioteColourPayload(SymbioteColours.STREAM_CODEC.decode(buf)));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
