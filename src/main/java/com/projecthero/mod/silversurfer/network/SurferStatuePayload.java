package com.projecthero.mod.silversurfer.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client (v0.17.1): creature {@code entityId} has been turned to solid silver by Transmutation for {@code ticks}
 * more ticks (0 = it shattered / thawed). Sent to everyone tracking it, so every viewer sees the silver statue.
 */
public record SurferStatuePayload(int entityId, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SurferStatuePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "surfer_statue"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SurferStatuePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SurferStatuePayload::entityId,
			ByteBufCodecs.VAR_INT, SurferStatuePayload::ticks,
			SurferStatuePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
