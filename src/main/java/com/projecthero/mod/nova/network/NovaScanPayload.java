package com.projecthero.mod.nova.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, sent ONLY to the Nova who cast it (v0.15.13): the Worldmind Scan's result -- every creature it found
 * (outlined cyan for {@code ticks}) and the strongest one it marked ({@code marked}, outlined red; -1 = none). Nothing is
 * set on the entities themselves, so no other player ever sees the outlines (the v0.15.4 highlight privacy rule).
 */
public record NovaScanPayload(List<Integer> ids, int marked, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<NovaScanPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "nova_scan"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NovaScanPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512)), NovaScanPayload::ids,
			ByteBufCodecs.VAR_INT, NovaScanPayload::marked,
			ByteBufCodecs.VAR_INT, NovaScanPayload::ticks,
			NovaScanPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
