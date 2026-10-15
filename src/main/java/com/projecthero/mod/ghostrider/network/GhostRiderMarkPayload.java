package com.projecthero.mod.ghostrider.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, sent ONLY to the Ghost Rider who cast it (v0.16.5): the creatures his Judgement Mark caught, outlined
 * red for {@code ticks}. Nothing is set on the entities themselves, so no other player sees the outlines (the v0.15.4
 * highlight privacy rule).
 */
public record GhostRiderMarkPayload(List<Integer> ids, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GhostRiderMarkPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "ghost_rider_mark"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GhostRiderMarkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512)), GhostRiderMarkPayload::ids,
			ByteBufCodecs.VAR_INT, GhostRiderMarkPayload::ticks,
			GhostRiderMarkPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
