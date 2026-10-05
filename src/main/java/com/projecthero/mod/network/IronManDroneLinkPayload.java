package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.29 Remote Pilot, server &rarr; client: the remote link to drone {@code entityId} opened ({@code active}) or
 * closed. The client moves its camera onto the drone / back to the player. {@code range} = the link range the server
 * enforces (blocks), for the HUD.
 */
public record IronManDroneLinkPayload(int entityId, boolean active, int range) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManDroneLinkPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_drone_link"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManDroneLinkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, IronManDroneLinkPayload::entityId,
			ByteBufCodecs.BOOL, IronManDroneLinkPayload::active,
			ByteBufCodecs.VAR_INT, IronManDroneLinkPayload::range,
			IronManDroneLinkPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
