package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.5: client -> server, a Normal Symbiote host double-tapped jump in the air: grow the Symbiote Wings. The
 * server re-checks everything ({@code SymbioteWings.tryStart}).
 */
public record SymbioteWingsPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SymbioteWingsPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "symbiote_wings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SymbioteWingsPayload> CODEC = StreamCodec.unit(new SymbioteWingsPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
