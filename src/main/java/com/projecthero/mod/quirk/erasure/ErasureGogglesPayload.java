package com.projecthero.mod.quirk.erasure;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, sent ONLY to the Eraser wearing his goggles (v0.16.7): the powered players and special mobs around
 * him, outlined on his screen alone (the v0.15.4 highlight privacy rule). Re-sent every half second while they are down.
 */
public record ErasureGogglesPayload(List<Integer> ids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ErasureGogglesPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "erasure_goggles"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ErasureGogglesPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(1024)), ErasureGogglesPayload::ids,
			ErasureGogglesPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
