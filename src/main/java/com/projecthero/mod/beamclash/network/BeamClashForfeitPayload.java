package com.projecthero.mod.beamclash.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server (v0.17.5): the player pressed Escape (or opened the pause menu) during a beam clash and gives it up. */
public record BeamClashForfeitPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BeamClashForfeitPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "beam_clash_forfeit"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BeamClashForfeitPayload> CODEC = StreamCodec.unit(new BeamClashForfeitPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
