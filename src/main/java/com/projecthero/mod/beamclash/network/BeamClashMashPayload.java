package com.projecthero.mod.beamclash.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server (v0.17.3): one left click during a beam clash. The server counts and limits them. */
public record BeamClashMashPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BeamClashMashPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "beam_clash_mash"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BeamClashMashPayload> CODEC = StreamCodec.unit(new BeamClashMashPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
