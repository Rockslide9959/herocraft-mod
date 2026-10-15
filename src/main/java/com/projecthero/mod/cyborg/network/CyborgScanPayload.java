package com.projecthero.mod.cyborg.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, sent ONLY to the Cyborg who scanned (v0.16.5): the creatures his Scan found, outlined cyan for
 * {@code ticks}. Nothing is set on the entities, so nobody else sees the outlines (the v0.15.4 privacy rule).
 */
public record CyborgScanPayload(List<Integer> ids, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CyborgScanPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "cyborg_scan"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CyborgScanPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512)), CyborgScanPayload::ids,
			ByteBufCodecs.VAR_INT, CyborgScanPayload::ticks,
			CyborgScanPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
